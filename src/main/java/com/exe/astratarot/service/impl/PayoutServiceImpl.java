package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.payout.CreatePayoutRequest;
import com.exe.astratarot.domain.dto.payout.EscrowSummaryResponse;
import com.exe.astratarot.domain.dto.payout.PayoutResponse;
import com.exe.astratarot.domain.entity.EscrowAccount;
import com.exe.astratarot.domain.entity.PayoutRequest;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.enums.PayoutStatus;
import com.exe.astratarot.exception.ReaderNotVerifiedException;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.PayoutRequestRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.security.AdminActions;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.NotificationTypes;
import com.exe.astratarot.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Rút tiền khỏi ký quỹ.
 *
 * <p>Quy trình ba bước cố ý: Reader tạo lệnh, quản trị viên duyệt, rồi đánh dấu
 * đã chi sau khi thực sự chuyển khoản. Gộp "duyệt" và "đã chi" làm một sẽ khiến
 * hệ thống nói rằng tiền đã ra khỏi tài khoản trong khi kế toán chưa bấm nút
 * chuyển — và đó là loại sai lệch chỉ phát hiện được lúc đối soát cuối tháng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutServiceImpl implements PayoutService {

    private final PayoutRequestRepository payoutRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final EscrowService escrowService;
    private final com.exe.astratarot.repository.EscrowTransactionRepository escrowTransactionRepository;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final com.exe.astratarot.service.VietQrService vietQrService;

    /** Dưới mức này thì phí chuyển khoản ăn gần hết số tiền rút. */
    private static final long MIN_PAYOUT = 50_000L;

    // =========================================================
    // Reader
    // =========================================================

    @Override
    @Transactional
    public EscrowSummaryResponse mySummary(UUID readerUserId) {
        EscrowAccount escrow = escrowService.getOrCreate(readerUserId);
        return EscrowSummaryResponse.builder()
                .balance(escrow.getBalance())
                .pendingBalance(escrow.getPendingBalance())
                .totalEarned(escrow.getTotalEarned())
                .totalWithdrawn(escrow.getTotalWithdrawn())
                .minimumPayout(MIN_PAYOUT)
                .penaltyOwed(escrow.getPenaltyOwed())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<com.exe.astratarot.domain.dto.payout.EscrowTransactionResponse> myLedger(
            UUID readerUserId, Pageable pageable) {
        return escrowTransactionRepository
                .findByAccountUserIdOrderByCreatedAtDesc(readerUserId, pageable)
                .map(t -> com.exe.astratarot.domain.dto.payout.EscrowTransactionResponse.builder()
                        .id(t.getId())
                        .kind(t.getKind().name())
                        .amount(t.getAmount())
                        .balanceAfter(t.getBalanceAfter())
                        .pendingAfter(t.getPendingAfter())
                        .bookingId(t.getBooking() == null ? null : t.getBooking().getId())
                        .note(t.getNote())
                        .createdAt(t.getCreatedAt())
                        .build());
    }

    @Override
    @Transactional
    public PayoutResponse create(UUID readerUserId, CreatePayoutRequest request) {
        ReaderProfile reader = readerProfileRepository.findByUserId(readerUserId)
                .orElseThrow(ReaderNotVerifiedException::new);

        if (request.getAmount() < MIN_PAYOUT) {
            throw new IllegalArgumentException("Số tiền rút tối thiểu là " + MIN_PAYOUT + " ₫");
        }

        // Trừ số dư NGAY tại đây. Nếu chỉ kiểm tra mà chưa trừ, Reader tạo mười
        // lệnh rút cùng một khoản và quản trị viên duyệt lần lượt là chi thừa
        // chín lần.
        escrowService.reserveForPayout(readerUserId, request.getAmount());

        PayoutRequest payout = payoutRepository.save(PayoutRequest.builder()
                .reader(reader)
                .amount(request.getAmount())
                .bankName(request.getBankName().trim())
                .bankAccount(request.getBankAccount().trim())
                .accountHolder(request.getAccountHolder().trim())
                .bankBin(emptyToNull(request.getBankBin()))
                .status(PayoutStatus.PENDING)
                .build());

        log.info("Reader {} tạo lệnh rút {}", readerUserId, request.getAmount());
        return toResponse(payout);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PayoutResponse> listMine(UUID readerUserId, Pageable pageable) {
        return payoutRepository.findForReader(readerUserId, pageable).map(this::toResponse);
    }

    // =========================================================
    // Quản trị viên
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Page<PayoutResponse> list(String status, Pageable pageable) {
        return payoutRepository.search(parseStatus(status), pageable)
                .map(this::toResponseChoNguoiDuyet);
    }

    @Override
    @Transactional
    public PayoutResponse approve(UUID actorId, UUID payoutId) {
        PayoutRequest payout = findPayout(payoutId);
        requireStatus(payout, PayoutStatus.PENDING, "Chỉ duyệt được lệnh đang chờ");

        payout.setStatus(PayoutStatus.APPROVED);
        activityLogService.record(actorId, AdminActions.PAYOUT_APPROVE, AdminActions.ENTITY_PAYOUT,
                payoutId, Map.of("amount", payout.getAmount()));

        notificationService.push(payout.getReader().getUser(), NotificationTypes.PAYOUT_APPROVED,
                "Lệnh rút đã được duyệt",
                "Chúng tôi sẽ chuyển khoản trong 1-3 ngày làm việc.",
                Map.of("payoutId", payoutId.toString()));
        return toResponse(payout);
    }

    @Override
    @Transactional
    public PayoutResponse reject(UUID actorId, UUID payoutId, String reason) {
        PayoutRequest payout = findPayout(payoutId);
        requireStatus(payout, PayoutStatus.PENDING, "Chỉ từ chối được lệnh đang chờ");

        payout.setStatus(PayoutStatus.REJECTED);
        payout.setRejectReason(reason == null || reason.isBlank() ? null : reason.trim());
        payout.setProcessedAt(Instant.now());

        // Trả tiền về số dư — nó đã bị trừ từ lúc tạo lệnh.
        escrowService.returnRejectedPayout(payout.getReader().getUser().getId(), payout.getAmount());

        activityLogService.record(actorId, AdminActions.PAYOUT_REJECT, AdminActions.ENTITY_PAYOUT,
                payoutId, Map.of("amount", payout.getAmount(), "reason", String.valueOf(reason)));

        notificationService.push(payout.getReader().getUser(), NotificationTypes.PAYOUT_REJECTED,
                "Lệnh rút bị từ chối",
                payout.getRejectReason() == null
                        ? "Tiền đã được trả lại vào số dư của bạn."
                        : payout.getRejectReason() + " Tiền đã được trả lại vào số dư của bạn.",
                Map.of("payoutId", payoutId.toString()));
        return toResponse(payout);
    }

    @Override
    @Transactional
    public PayoutResponse markPaid(UUID actorId, UUID payoutId) {
        PayoutRequest payout = findPayout(payoutId);
        // Chỉ đánh dấu đã chi sau khi đã duyệt: bỏ qua bước duyệt nghĩa là tiền
        // ra khỏi tài khoản mà không ai ký.
        requireStatus(payout, PayoutStatus.APPROVED, "Phải duyệt lệnh trước khi đánh dấu đã chi");

        payout.setStatus(PayoutStatus.PAID);
        payout.setProcessedAt(Instant.now());
        escrowService.settlePayout(payout.getReader().getUser().getId(), payout.getAmount());

        activityLogService.record(actorId, AdminActions.PAYOUT_PAID, AdminActions.ENTITY_PAYOUT,
                payoutId, Map.of("amount", payout.getAmount()));

        notificationService.push(payout.getReader().getUser(), NotificationTypes.PAYOUT_PAID,
                "Đã chuyển tiền",
                payout.getAmount() + " ₫ đã được chuyển tới " + payout.getBankName() + ".",
                Map.of("payoutId", payoutId.toString()));
        return toResponse(payout);
    }

    // =========================================================
    // Tiện ích
    // =========================================================

    private PayoutRequest findPayout(UUID id) {
        return payoutRepository.findByIdWithReader(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lệnh rút"));
    }

    private static void requireStatus(PayoutRequest p, PayoutStatus expected, String message) {
        if (p.getStatus() != expected) {
            throw new IllegalArgumentException(message);
        }
    }

    private static PayoutStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PayoutStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái lệnh rút không hợp lệ: " + status);
        }
    }

    private PayoutResponse toResponse(PayoutRequest p) {
        return PayoutResponse.builder()
                .id(p.getId())
                .readerName(p.getReader().getUser().getFullName())
                .readerEmail(p.getReader().getUser().getEmail())
                .amount(p.getAmount())
                .bankName(p.getBankName())
                // Che bớt số tài khoản ở danh sách: màn quản trị mở trên máy
                // dùng chung, không cần phơi đủ số cho mọi người đi ngang nhìn.
                .bankAccountMasked(mask(p.getBankAccount()))
                .accountHolder(p.getAccountHolder())
                .bankBin(p.getBankBin())
                .status(p.getStatus().name())
                .rejectReason(p.getRejectReason())
                .requestedAt(p.getRequestedAt())
                .processedAt(p.getProcessedAt())
                .build();
    }

    /**
     * Bản dành cho người duyệt chi: kèm chuỗi QR để quét là chuyển được ngay.
     *
     * <p>Tách hẳn khỏi {@link #toResponse} chứ không thêm một tham số boolean:
     * chuỗi này quét một cái là tiền đi, nên chỗ nào trả nó ra phải nhìn thấy
     * được từ tên hàm, không nằm ẩn sau một cờ true/false ở đầu gọi.
     */
    private PayoutResponse toResponseChoNguoiDuyet(PayoutRequest p) {
        PayoutResponse res = toResponse(p);
        // Chỉ lệnh còn chờ hoặc đã duyệt mới cần QR. Lệnh đã chi hay bị từ chối
        // mà vẫn kèm mã quét được là một cái bẫy chuyển nhầm tiền lần hai.
        boolean conPhaiChi = p.getStatus() == PayoutStatus.PENDING
                || p.getStatus() == PayoutStatus.APPROVED;
        if (conPhaiChi && p.getBankBin() != null && p.getBankAccount() != null) {
            try {
                res.setQrPayload(vietQrService.dungChuoi(
                        p.getBankBin(), p.getBankAccount(), p.getAmount(),
                        "ASTROTAROT tra thu nhap " + p.getReader().getUser().getFullName()));
            } catch (RuntimeException e) {
                // Dữ liệu ngân hàng hỏng thì bỏ mã QR, KHÔNG làm hỏng cả danh
                // sách: người duyệt vẫn phải xem được lệnh để từ chối nó.
                log.warn("Không dựng được QR cho lệnh rút {}: {}", p.getId(), e.getMessage());
            }
        }
        return res;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String mask(String account) {
        if (account == null || account.length() <= 4) {
            return account;
        }
        return "•".repeat(account.length() - 4) + account.substring(account.length() - 4);
    }
}
