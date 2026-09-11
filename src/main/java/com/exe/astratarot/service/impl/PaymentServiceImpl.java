package com.exe.astratarot.service.impl;

import com.exe.astratarot.config.PayOsConfig.PayOsClient;
import com.exe.astratarot.config.PayOsProperties;
import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.payment.PaymentTransactionResponse;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.PaymentTransaction;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.PaymentTransactionRepository;
import com.exe.astratarot.security.AdminActions;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.NotificationTypes;
import com.exe.astratarot.service.PaymentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Thanh toán buổi xem: PayOS (nếu cấu hình) hoặc chuyển khoản có mã tham chiếu.
 *
 * <p>Webhook PayOS xác nhận tự động rồi gọi cùng luồng ký quỹ như admin confirm.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String METHOD_BANK_TRANSFER = "BANK_TRANSFER";
    private static final String METHOD_PAYOS = "PAYOS";

    private final PaymentTransactionRepository transactionRepository;
    private final BookingRepository bookingRepository;
    private final EscrowService escrowService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final PayOsClient payOsClient;
    private final PayOsProperties payOsProperties;
    private final ObjectMapper objectMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.bank.name:Chưa cấu hình}")
    private String bankName;

    @Value("${app.bank.account-number:Chưa cấu hình}")
    private String bankAccountNumber;

    @Value("${app.bank.account-holder:Chưa cấu hình}")
    private String bankAccountHolder;

    @Value("${app.frontend-url:http://localhost:8081}")
    private String frontendUrl;

    // =========================================================
    // Khách trả tiền
    // =========================================================

    @Override
    @Transactional
    public PaymentInstructionResponse createPaymentIntent(UUID userId, UUID bookingId) {
        Booking booking = bookingRepository.findByIdWithParties(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));

        if (!booking.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Lịch hẹn này không phải của bạn");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Lịch hẹn đã huỷ, không thanh toán được");
        }
        if (booking.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Lịch hẹn này đã thanh toán rồi");
        }

        PaymentTransaction pending = transactionRepository
                .findFirstByBookingIdAndStatus(bookingId, TransactionStatus.PENDING)
                .orElse(null);

        if (pending != null) {
            return toInstruction(pending);
        }

        if (payOsClient.enabled()) {
            return createPayOsIntent(booking);
        }
        return createBankTransferIntent(booking);
    }

    private PaymentInstructionResponse createBankTransferIntent(Booking booking) {
        PaymentTransaction pending = transactionRepository.save(PaymentTransaction.builder()
                .booking(booking)
                .user(booking.getUser())
                .amount(booking.getTotalAmount())
                .paymentMethod(METHOD_BANK_TRANSFER)
                .externalTransactionId(generateReference())
                .status(TransactionStatus.PENDING)
                .build());
        return toInstruction(pending);
    }

    private PaymentInstructionResponse createPayOsIntent(Booking booking) {
        long orderCode = nextOrderCode();
        String returnUrl = firstNonBlank(payOsProperties.getReturnUrl(),
                trimSlash(frontendUrl) + "/bookings?payment=success");
        String cancelUrl = firstNonBlank(payOsProperties.getCancelUrl(),
                trimSlash(frontendUrl) + "/bookings?payment=cancel");

        // PayOS giới hạn mô tả ~25 ký tự.
        String description = "Astra " + orderCode;
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(booking.getTotalAmount())
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .buyerName(booking.getUser().getFullName())
                .buyerEmail(booking.getUser().getEmail())
                .build();

        CreatePaymentLinkResponse link;
        try {
            link = payOsClient.sdk().paymentRequests().create(request);
        } catch (Exception e) {
            log.error("Tạo link PayOS thất bại cho booking {}: {}", booking.getId(), e.getMessage());
            throw new IllegalStateException("Không tạo được link thanh toán PayOS: " + e.getMessage(), e);
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("checkoutUrl", link.getCheckoutUrl());
        meta.put("qrCode", link.getQrCode());
        meta.put("paymentLinkId", link.getPaymentLinkId());
        meta.put("bin", link.getBin());
        meta.put("accountNumber", link.getAccountNumber());
        meta.put("accountName", link.getAccountName());

        PaymentTransaction pending = transactionRepository.save(PaymentTransaction.builder()
                .booking(booking)
                .user(booking.getUser())
                .amount(booking.getTotalAmount())
                .paymentMethod(METHOD_PAYOS)
                .externalTransactionId(String.valueOf(orderCode))
                .status(TransactionStatus.PENDING)
                .metadata(writeJson(meta))
                .build());

        return toInstruction(pending, link);
    }

    // =========================================================
    // Webhook PayOS
    // =========================================================

    @Override
    @Transactional
    public void handlePayOsWebhook(Object rawBody) {
        if (!payOsClient.enabled()) {
            throw new IllegalStateException("PayOS chưa cấu hình");
        }
        WebhookData data = payOsClient.sdk().webhooks().verify(rawBody);
        if (data == null || data.getOrderCode() == null) {
            throw new IllegalArgumentException("Webhook PayOS thiếu orderCode");
        }
        if (!"00".equals(data.getCode())) {
            log.info("PayOS webhook không thành công code={} desc={} order={}",
                    data.getCode(), data.getDesc(), data.getOrderCode());
            return;
        }

        String orderKey = String.valueOf(data.getOrderCode());
        PaymentTransaction tx = transactionRepository.findByExternalTransactionId(orderKey)
                .orElse(null);

        // Không tìm thấy giao dịch KHÔNG phải là lỗi của ta, và tuyệt đối không
        // được trả 5xx.
        //
        // Đây chính là nguyên nhân "Webhook url invalid" mỗi lần khởi động:
        // khi đăng ký webhook, PayOS gọi thử chính URL này bằng một gói tin giả
        // mang orderCode = 123. Chữ ký của gói tin đó hợp lệ nên nó đi qua
        // verify(), rồi chết ở đây vì làm gì có giao dịch nào số 123. Ngoại lệ
        // bay lên controller và thành HTTP 500, PayOS thấy 500 thì kết luận URL
        // hỏng và từ chối đăng ký — suốt thời gian qua webhook chưa từng được
        // đăng ký thành công.
        //
        // Không bắt riêng số 123: một magic number là thứ sẽ mục đi lặng lẽ khi
        // PayOS đổi gói tin thử. Lý lẽ đúng rộng hơn thế — orderCode lạ thì
        // không có việc gì để làm, và gọi lại mười lần cũng không làm nó tồn
        // tại, nên báo nhận rồi thôi.
        if (tx == null) {
            log.info("PayOS webhook cho orderCode={} không khớp giao dịch nào. "
                    + "Thường là gói tin PayOS gửi thử lúc đăng ký webhook.", orderKey);
            return;
        }

        if (tx.getStatus() != TransactionStatus.PENDING) {
            log.info("PayOS webhook trùng — giao dịch {} đã {}", tx.getId(), tx.getStatus());
            return;
        }
        if (data.getAmount() != null && !data.getAmount().equals(tx.getAmount())) {
            log.warn("PayOS amount lệch: webhook={} tx={} id={}", data.getAmount(), tx.getAmount(), tx.getId());
            throw new IllegalArgumentException("Số tiền webhook không khớp giao dịch");
        }

        markPaid(null, tx);
        log.info("PayOS xác nhận thanh toán {} (orderCode={})", tx.getId(), orderKey);
    }

    // =========================================================
    // Quản trị viên đối soát
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentTransactionResponse> list(String status, Pageable pageable) {
        return transactionRepository.search(parseStatus(status), pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public PaymentTransactionResponse confirm(UUID actorId, UUID transactionId) {
        PaymentTransaction tx = findTransaction(transactionId);
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalArgumentException("Giao dịch này đã được xử lý rồi");
        }
        markPaid(actorId, tx);
        return toResponse(tx);
    }

    @Override
    @Transactional
    public PaymentTransactionResponse reject(UUID actorId, UUID transactionId, String reason) {
        PaymentTransaction tx = findTransaction(transactionId);
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new IllegalArgumentException("Giao dịch này đã được xử lý rồi");
        }

        tx.setStatus(TransactionStatus.FAILED);
        activityLogService.record(actorId, AdminActions.PAYMENT_REJECT, AdminActions.ENTITY_PAYMENT,
                transactionId, Map.of("reason", String.valueOf(reason)));

        notificationService.push(tx.getUser(), NotificationTypes.PAYMENT_FAILED,
                "Chưa đối soát được khoản chuyển",
                reason == null || reason.isBlank()
                        ? "Chúng tôi chưa tìm thấy khoản chuyển khớp với mã của bạn. Kiểm tra lại giúp nhé."
                        : reason,
                tx.getBooking() == null ? Map.of() : Map.of("bookingId", tx.getBooking().getId().toString()));

        return toResponse(tx);
    }

    // =========================================================
    // Hoàn tiền khi huỷ
    // =========================================================

    @Override
    @Transactional
    public void refundIfPaid(Booking booking) {
        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            return;
        }
        escrowService.refundForBooking(booking);
        booking.setPaymentStatus(PaymentStatus.REFUNDED);

        transactionRepository.findByBookingIdOrderByCreatedAtDesc(booking.getId()).stream()
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                .findFirst()
                .ifPresent(t -> t.setStatus(TransactionStatus.CANCELLED));

        notificationService.push(booking.getUser(), NotificationTypes.PAYMENT_REFUNDED,
                "Lịch hẹn đã huỷ, tiền sẽ được hoàn",
                "Khoản " + booking.getTotalAmount()
                        + " ₫ sẽ được chuyển lại trong 1-3 ngày làm việc.",
                Map.of("bookingId", booking.getId().toString()));

        log.info("Đánh dấu hoàn tiền cho booking {}", booking.getId());
    }

    // =========================================================
    // Tiện ích
    // =========================================================

    private void markPaid(UUID actorId, PaymentTransaction tx) {
        Booking booking = tx.getBooking();
        if (booking == null) {
            throw new IllegalArgumentException("Giao dịch không gắn với lịch hẹn nào");
        }

        tx.setStatus(TransactionStatus.SUCCESS);
        booking.setPaymentStatus(PaymentStatus.PAID);
        escrowService.holdForBooking(booking);

        activityLogService.record(actorId, AdminActions.PAYMENT_CONFIRM, AdminActions.ENTITY_PAYMENT,
                tx.getId(), Map.of(
                        "amount", tx.getAmount(),
                        "bookingId", booking.getId().toString(),
                        "method", String.valueOf(tx.getPaymentMethod()),
                        "source", actorId == null ? "payos_webhook" : "admin"));

        notificationService.push(booking.getUser(), NotificationTypes.PAYMENT_CONFIRMED,
                "Đã nhận thanh toán",
                "Buổi xem với " + booking.getReaderProfile().getUser().getFullName()
                        + " đã được thanh toán.",
                Map.of("bookingId", booking.getId().toString()));

        notificationService.push(booking.getReaderProfile().getUser(), NotificationTypes.PAYMENT_CONFIRMED,
                "Khách đã thanh toán",
                "Tiền đang được giữ ở ký quỹ và sẽ vào số dư của bạn sau khi buổi xem hoàn tất.",
                Map.of("bookingId", booking.getId().toString()));

        log.info("Xác nhận thanh toán {} cho booking {}", tx.getId(), booking.getId());
    }

    private PaymentInstructionResponse toInstruction(PaymentTransaction pending) {
        if (METHOD_PAYOS.equals(pending.getPaymentMethod())) {
            Map<String, Object> meta = readMeta(pending.getMetadata());
            return PaymentInstructionResponse.builder()
                    .transactionId(pending.getId())
                    .bookingId(pending.getBooking().getId())
                    .amount(pending.getAmount())
                    .paymentMethod(METHOD_PAYOS)
                    .referenceCode(pending.getExternalTransactionId())
                    .bankName(stringOr(meta.get("bin"), bankName))
                    .bankAccountNumber(stringOr(meta.get("accountNumber"), bankAccountNumber))
                    .bankAccountHolder(stringOr(meta.get("accountName"), bankAccountHolder))
                    .transferContent(pending.getExternalTransactionId())
                    .checkoutUrl(stringOr(meta.get("checkoutUrl"), null))
                    .qrCode(stringOr(meta.get("qrCode"), null))
                    .status(pending.getStatus().name())
                    .build();
        }
        return PaymentInstructionResponse.builder()
                .transactionId(pending.getId())
                .bookingId(pending.getBooking().getId())
                .amount(pending.getAmount())
                .paymentMethod(METHOD_BANK_TRANSFER)
                .referenceCode(pending.getExternalTransactionId())
                .bankName(bankName)
                .bankAccountNumber(bankAccountNumber)
                .bankAccountHolder(bankAccountHolder)
                .transferContent(pending.getExternalTransactionId())
                .status(pending.getStatus().name())
                .build();
    }

    private PaymentInstructionResponse toInstruction(PaymentTransaction pending, CreatePaymentLinkResponse link) {
        return PaymentInstructionResponse.builder()
                .transactionId(pending.getId())
                .bookingId(pending.getBooking().getId())
                .amount(pending.getAmount())
                .paymentMethod(METHOD_PAYOS)
                .referenceCode(pending.getExternalTransactionId())
                .bankName(firstNonBlank(link.getBin(), bankName))
                .bankAccountNumber(firstNonBlank(link.getAccountNumber(), bankAccountNumber))
                .bankAccountHolder(firstNonBlank(link.getAccountName(), bankAccountHolder))
                .transferContent(pending.getExternalTransactionId())
                .checkoutUrl(link.getCheckoutUrl())
                .qrCode(link.getQrCode())
                .status(pending.getStatus().name())
                .build();
    }

    private long nextOrderCode() {
        for (int i = 0; i < 8; i++) {
            long code = Instant.now().getEpochSecond() * 1000L + secureRandom.nextInt(1000);
            if (transactionRepository.findByExternalTransactionId(String.valueOf(code)).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Không sinh được orderCode PayOS duy nhất");
    }

    private String generateReference() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("ATB");
        for (int i = 0; i < 7; i++) {
            sb.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        String code = sb.toString();
        return transactionRepository.findByExternalTransactionId(code).isPresent()
                ? generateReference()
                : code;
    }

    private PaymentTransaction findTransaction(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch"));
    }

    private static TransactionStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return TransactionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái giao dịch không hợp lệ: " + status);
        }
    }

    private PaymentTransactionResponse toResponse(PaymentTransaction t) {
        Booking b = t.getBooking();
        return PaymentTransactionResponse.builder()
                .id(t.getId())
                .bookingId(b == null ? null : b.getId())
                .payerName(t.getUser().getFullName())
                .payerEmail(t.getUser().getEmail())
                .readerName(b == null ? null : b.getReaderProfile().getUser().getFullName())
                .amount(t.getAmount())
                .paymentMethod(t.getPaymentMethod())
                .referenceCode(t.getExternalTransactionId())
                .status(t.getStatus().name())
                .bookingStartTime(b == null ? null : b.getStartTime())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private String writeJson(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            throw new IllegalStateException("Không ghi được metadata thanh toán", e);
        }
    }

    private Map<String, Object> readMeta(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("metadata thanh toán không đọc được: {}", e.getMessage());
            return Map.of();
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String stringOr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
