package com.exe.astratarot.service.impl;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Thanh toán buổi xem bằng chuyển khoản ngân hàng có mã tham chiếu.
 *
 * <p>Cố ý KHÔNG mô phỏng một cổng thanh toán tự động. Một endpoint "trả tiền"
 * bấm cái là thành công trông thì đủ, nhưng nó khiến cả luồng ký quỹ chạy trên
 * những khoản tiền chưa từng tồn tại — và đến lúc nối cổng thật thì toàn bộ số
 * liệu cũ phải bỏ đi.
 *
 * <p>Chuyển khoản kèm mã tham chiếu là cách nhiều sàn nhỏ ở Việt Nam đang chạy
 * thật: khách chuyển tiền với nội dung là mã, người trực đối chiếu sao kê rồi
 * xác nhận. Khi nối cổng tự động sau này, chỉ cần thay bước xác nhận thủ công
 * bằng webhook — phần còn lại giữ nguyên.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final BookingRepository bookingRepository;
    private final EscrowService escrowService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.bank.name:Chưa cấu hình}")
    private String bankName;

    @Value("${app.bank.account-number:Chưa cấu hình}")
    private String bankAccountNumber;

    @Value("${app.bank.account-holder:Chưa cấu hình}")
    private String bankAccountHolder;

    private static final String METHOD_BANK_TRANSFER = "BANK_TRANSFER";

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

        // Đã có lệnh chờ thì trả lại đúng lệnh đó thay vì sinh mã mới. Mỗi lần
        // bấm lại mà ra một mã khác nhau thì người trực không biết sao kê ứng
        // với lệnh nào.
        PaymentTransaction pending = transactionRepository
                .findFirstByBookingIdAndStatus(bookingId, TransactionStatus.PENDING)
                .orElseGet(() -> transactionRepository.save(PaymentTransaction.builder()
                        .booking(booking)
                        .user(booking.getUser())
                        .amount(booking.getTotalAmount())
                        .paymentMethod(METHOD_BANK_TRANSFER)
                        .externalTransactionId(generateReference())
                        .status(TransactionStatus.PENDING)
                        .build()));

        return PaymentInstructionResponse.builder()
                .transactionId(pending.getId())
                .bookingId(bookingId)
                .amount(pending.getAmount())
                .referenceCode(pending.getExternalTransactionId())
                .bankName(bankName)
                .bankAccountNumber(bankAccountNumber)
                .bankAccountHolder(bankAccountHolder)
                .transferContent(pending.getExternalTransactionId())
                .status(pending.getStatus().name())
                .build();
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
        Booking booking = tx.getBooking();
        if (booking == null) {
            throw new IllegalArgumentException("Giao dịch không gắn với lịch hẹn nào");
        }

        tx.setStatus(TransactionStatus.SUCCESS);
        booking.setPaymentStatus(PaymentStatus.PAID);

        // Tiền vào phần ĐANG GIỮ của Reader, chưa phải phần rút được. Chỉ nhả
        // khi buổi xem hoàn tất — xem EscrowService.
        escrowService.holdForBooking(booking);

        activityLogService.record(actorId, AdminActions.PAYMENT_CONFIRM, AdminActions.ENTITY_PAYMENT,
                transactionId, Map.of("amount", tx.getAmount(), "bookingId", booking.getId().toString()));

        notificationService.push(booking.getUser(), NotificationTypes.PAYMENT_CONFIRMED,
                "Đã nhận thanh toán",
                "Buổi xem với " + booking.getReaderProfile().getUser().getFullName()
                        + " đã được thanh toán.",
                Map.of("bookingId", booking.getId().toString()));

        notificationService.push(booking.getReaderProfile().getUser(), NotificationTypes.PAYMENT_CONFIRMED,
                "Khách đã thanh toán",
                "Tiền đang được giữ ở ký quỹ và sẽ vào số dư của bạn sau khi buổi xem hoàn tất.",
                Map.of("bookingId", booking.getId().toString()));

        log.info("Xác nhận thanh toán {} cho booking {}", transactionId, booking.getId());
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
        // KHÔNG đụng tới ký quỹ: tiền chưa bao giờ được giữ vì giao dịch chưa
        // từng thành công.
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

        // Hoàn tiền thật vẫn phải làm tay: hệ thống chưa nối cổng nào để tự
        // chuyển lại. Báo cho khách biết chờ, thay vì im lặng.
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

    /**
     * Mã tham chiếu người chuyển khoản gõ vào nội dung.
     *
     * Chỉ dùng chữ và số không dễ đọc nhầm (bỏ O, 0, I, 1): khách gõ tay vào
     * ứng dụng ngân hàng, một ký tự sai là người trực không tra ra.
     */
    private String generateReference() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder("ATB");
        for (int i = 0; i < 7; i++) {
            sb.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        String code = sb.toString();
        // Trùng mã là hỏng cả việc đối soát, nên thử lại cho tới khi duy nhất.
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
}
