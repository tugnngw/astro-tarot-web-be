package com.exe.astratarot.service.impl;

import com.exe.astratarot.config.PayOsConfig.PayOsClient;
import com.exe.astratarot.config.PayOsProperties;
import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.wallet.UserWalletResponse;
import com.exe.astratarot.domain.dto.wallet.WalletTransactionResponse;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.PaymentTransaction;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserWallet;
import com.exe.astratarot.domain.entity.WalletTransaction;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentPhase;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.domain.enums.WalletTransactionType;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.PaymentTransactionRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserWalletRepository;
import com.exe.astratarot.repository.WalletTransactionRepository;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.NotificationTypes;
import com.exe.astratarot.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserWalletRepository walletRepository;
    private final WalletTransactionRepository walletTxRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EscrowService escrowService;
    private final NotificationService notificationService;
    private final PayOsClient payOsClient;
    private final PayOsProperties payOsProperties;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url:http://localhost:8081}")
    private String frontendUrl;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public UserWallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
                    UserWallet newWallet = UserWallet.builder()
                            .user(user)
                            .balance(0L)
                            .build();
                    return walletRepository.save(newWallet);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public UserWalletResponse getWalletResponse(UUID userId) {
        UserWallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
                    return UserWallet.builder()
                            .user(user)
                            .balance(0L)
                            .build();
                });
        return toWalletResponse(wallet);
    }

    @Override
    @Transactional
    public WalletTransaction credit(User user, long amount, WalletTransactionType type, String referenceId, String description, String paymentMethod) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền cộng ví phải lớn hơn 0");
        }

        UserWallet wallet = walletRepository.findByUserIdWithLock(user.getId())
                .orElseGet(() -> {
                    UserWallet newWallet = UserWallet.builder()
                            .user(user)
                            .balance(0L)
                            .build();
                    return walletRepository.save(newWallet);
                });

        long balanceBefore = wallet.getBalance();
        long balanceAfter = balanceBefore + amount;
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .user(user)
                .type(type)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .status(TransactionStatus.SUCCESS)
                .referenceId(referenceId)
                .paymentMethod(paymentMethod != null ? paymentMethod : "WALLET")
                .description(description)
                .build();

        log.info("Cộng {} ₫ vào ví của user {} (type={}, before={}, after={})",
                amount, user.getId(), type, balanceBefore, balanceAfter);

        return walletTxRepository.save(tx);
    }

    @Override
    @Transactional
    public WalletTransaction debit(User user, long amount, WalletTransactionType type, String referenceId, String description) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Số tiền trừ ví phải lớn hơn 0");
        }

        UserWallet wallet = walletRepository.findByUserIdWithLock(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Người dùng chưa có ví"));

        if (wallet.getBalance() < amount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Số dư ví không đủ (Hiện có %s ₫, cần %s ₫)",
                            wallet.getBalance(), amount));
        }

        long balanceBefore = wallet.getBalance();
        long balanceAfter = balanceBefore - amount;
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .user(user)
                .type(type)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .status(TransactionStatus.SUCCESS)
                .referenceId(referenceId)
                .paymentMethod("WALLET")
                .description(description)
                .build();

        log.info("Trừ {} ₫ từ ví của user {} (type={}, before={}, after={})",
                amount, user.getId(), type, balanceBefore, balanceAfter);

        return walletTxRepository.save(tx);
    }

    @Override
    @Transactional
    public PaymentInstructionResponse createTopupIntent(UUID userId, long amount) {
        if (amount < 10000) {
            throw new IllegalArgumentException("Số tiền nạp tối thiểu là 10.000 ₫");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (!payOsClient.enabled()) {
            throw new IllegalStateException("Cổng PayOS chưa được cấu hình trên hệ thống");
        }

        long orderCode = nextOrderCode();
        String returnUrl = firstNonBlank(payOsProperties.getReturnUrl(),
                trimSlash(frontendUrl) + "/profile/wallet?status=success");
        String cancelUrl = firstNonBlank(payOsProperties.getCancelUrl(),
                trimSlash(frontendUrl) + "/profile/wallet?status=cancel");

        String description = "Astra Nap " + orderCode;
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .buyerName(user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername())
                .buyerEmail(user.getEmail())
                .build();

        CreatePaymentLinkResponse link;
        try {
            link = payOsClient.sdk().paymentRequests().create(request);
        } catch (Exception e) {
            log.error("Tạo link nạp ví PayOS thất bại cho user {}: {}", userId, e.getMessage());
            throw new IllegalStateException("Không tạo được link thanh toán PayOS: " + e.getMessage(), e);
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("checkoutUrl", link.getCheckoutUrl());
        meta.put("qrCode", link.getQrCode());
        meta.put("paymentLinkId", link.getPaymentLinkId());
        meta.put("bin", link.getBin());
        meta.put("accountNumber", link.getAccountNumber());
        meta.put("accountName", link.getAccountName());

        PaymentTransaction pending = paymentTransactionRepository.save(PaymentTransaction.builder()
                .booking(null)
                .user(user)
                .amount(amount)
                .phase(PaymentPhase.TOPUP)
                .paymentMethod("PAYOS")
                .externalTransactionId(String.valueOf(orderCode))
                .status(TransactionStatus.PENDING)
                .metadata(writeJson(meta))
                .build());

        return PaymentInstructionResponse.builder()
                .transactionId(pending.getId())
                .bookingId(null)
                .amount(amount)
                .paymentMethod("PAYOS")
                .paymentPhase(PaymentPhase.TOPUP.name())
                .referenceCode(pending.getExternalTransactionId())
                .bankName(link.getBin())
                .bankAccountNumber(link.getAccountNumber())
                .bankAccountHolder(link.getAccountName())
                .transferContent(pending.getExternalTransactionId())
                .checkoutUrl(link.getCheckoutUrl())
                .qrCode(link.getQrCode())
                .status(pending.getStatus().name())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponse> getTransactions(UUID userId, Pageable pageable) {
        return walletTxRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toTxResponse);
    }

    @Override
    @Transactional
    public void payBookingWithWallet(UUID userId, UUID bookingId) {
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
        if (booking.getPaymentStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalArgumentException("Lịch hẹn đã được hoàn tiền");
        }

        PaymentPhase phase;
        long amount;
        if (booking.getPaymentStatus() == PaymentStatus.DEPOSIT_PAID) {
            phase = PaymentPhase.REMAINING;
            amount = booking.getRemainingAmount();
        } else {
            phase = PaymentPhase.FULL;
            amount = booking.getTotalAmount();
        }

        // Trừ tiền trong ví (sẽ ném lỗi nếu không đủ số dư)
        debit(booking.getUser(), amount, WalletTransactionType.BOOKING_PAYMENT,
                booking.getId().toString(),
                "Thanh toán lịch hẹn #" + booking.getId() + " (" + phase.name() + ")");

        // Cập nhật booking status
        if (phase == PaymentPhase.DEPOSIT) {
            booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);
        } else {
            booking.setPaymentStatus(PaymentStatus.PAID);
        }
        bookingRepository.save(booking);

        // Ký quỹ
        escrowService.holdForBooking(booking, amount);

        // Ghi nhận PaymentTransaction để đối soát chung
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .booking(booking)
                .user(booking.getUser())
                .amount(amount)
                .phase(phase)
                .paymentMethod("WALLET")
                .status(TransactionStatus.SUCCESS)
                .build());

        notificationService.push(booking.getUser(), NotificationTypes.PAYMENT_CONFIRMED,
                "Thanh toán lịch hẹn thành công bằng ví",
                "Đã thanh toán " + amount + " ₫ cho lịch hẹn với "
                        + booking.getReaderProfile().getUser().getFullName() + " bằng Ví ASTROTAROT.",
                Map.of("bookingId", booking.getId().toString(), NotificationTypes.SIDE, NotificationTypes.SIDE_CUSTOMER));

        notificationService.push(booking.getReaderProfile().getUser(), NotificationTypes.PAYMENT_CONFIRMED,
                "Khách đã thanh toán qua Ví ASTROTAROT",
                "Khách đã thanh toán " + amount + " ₫. Tiền đang được giữ an toàn ở quỹ ký quỹ.",
                Map.of("bookingId", booking.getId().toString(), NotificationTypes.SIDE, NotificationTypes.SIDE_READER));

        log.info("Khách {} thanh toán thành công booking {} bằng ví: amount={}, phase={}",
                userId, bookingId, amount, phase);
    }

    private UserWalletResponse toWalletResponse(UserWallet wallet) {
        return UserWalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser() != null ? wallet.getUser().getId() : null)
                .balance(wallet.getBalance())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    private WalletTransactionResponse toTxResponse(WalletTransaction tx) {
        return WalletTransactionResponse.builder()
                .id(tx.getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .status(tx.getStatus())
                .referenceId(tx.getReferenceId())
                .paymentMethod(tx.getPaymentMethod())
                .description(tx.getDescription())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    private long nextOrderCode() {
        for (int i = 0; i < 8; i++) {
            long code = Instant.now().getEpochSecond() * 1000L + secureRandom.nextInt(1000);
            if (paymentTransactionRepository.findByExternalTransactionId(String.valueOf(code)).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Không sinh được orderCode PayOS duy nhất");
    }

    private String writeJson(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            throw new IllegalStateException("Không ghi được metadata nạp ví", e);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String trimSlash(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }
}
