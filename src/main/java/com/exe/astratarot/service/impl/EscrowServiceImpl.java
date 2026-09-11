package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.EscrowAccount;
import com.exe.astratarot.domain.entity.EscrowTransaction;
import com.exe.astratarot.domain.entity.EscrowTransaction.Kind;
import com.exe.astratarot.domain.entity.PayoutRequest;
import com.exe.astratarot.domain.entity.Report;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.EscrowAccountRepository;
import com.exe.astratarot.repository.EscrowTransactionRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.EscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowServiceImpl implements EscrowService {

    private final EscrowAccountRepository escrowAccountRepository;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final UserRepository userRepository;

    /**
     * Phần nền tảng giữ lại trên mỗi buổi xem, tính bằng phần trăm.
     *
     * <p>Hằng số vì chưa có bảng cấu hình. Khi làm màn "Cài đặt hệ thống" thì
     * chuyển vào đó — nhưng phải nhớ: đổi phí KHÔNG được ảnh hưởng tới những
     * buổi xem đã hoàn tất, nên lúc ấy phải lưu mức phí vào từng booking chứ
     * không đọc cấu hình hiện hành.
     */
    // public để trang thống kê quản trị đọc đúng con số đang áp dụng, thay vì
    // chép lại 15 ở chỗ khác rồi hai nơi lệch nhau khi đổi phí.
    public static final int PLATFORM_FEE_PERCENT = 15;

    @Override
    @Transactional
    public EscrowAccount getOrCreate(User user) {
        return escrowAccountRepository.findByUserId(user.getId())
                .orElseGet(() -> escrowAccountRepository.save(
                        EscrowAccount.builder().user(user).build()));
    }

    @Override
    @Transactional
    public EscrowAccount getOrCreate(UUID userId) {
        return getOrCreate(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản")));
    }

    // =========================================================
    // Sổ cái
    // =========================================================

    /**
     * Lưu tài khoản và ghi một dòng sổ.
     *
     * <p>Hai việc này luôn đi cặp, và luôn theo đúng thứ tự này: số dư phải
     * đúng trước khi chép sang {@code balanceAfter}, nếu không dòng sổ nói dối.
     */
    private void ghiSo(EscrowAccount escrow, Kind kind, long amount,
                       Booking booking, Report report, PayoutRequest payout,
                       String note) {
        escrowAccountRepository.save(escrow);
        escrowTransactionRepository.save(EscrowTransaction.builder()
                .account(escrow)
                .kind(kind)
                .amount(amount)
                .balanceAfter(escrow.getBalance())
                .pendingAfter(escrow.getPendingBalance())
                .booking(booking)
                .report(report)
                .payout(payout)
                .note(note)
                .build());
    }

    // =========================================================
    // Theo lịch hẹn
    // =========================================================

    @Override
    @Transactional
    public void holdForBooking(Booking booking) {
        EscrowAccount escrow = getOrCreate(booking.getReaderProfile().getUser());
        escrow.setPendingBalance(escrow.getPendingBalance() + booking.getTotalAmount());
        ghiSo(escrow, Kind.HOLD, booking.getTotalAmount(), booking, null, null,
                "Khách đã thanh toán. Tiền được giữ tới khi buổi xem hoàn tất.");
        log.info("Ký quỹ giữ {} cho booking {}", booking.getTotalAmount(), booking.getId());
    }

    @Override
    @Transactional
    public void releaseForBooking(Booking booking) {
        EscrowAccount escrow = getOrCreate(booking.getReaderProfile().getUser());
        long gross = booking.getTotalAmount();

        // Trừ vào phần đang giữ trước khi cộng sang phần rút được. Nếu vì lý do
        // nào đó phần đang giữ không đủ (dữ liệu lệch do sự cố cũ), dừng lại
        // thay vì đẩy nó xuống âm — ràng buộc ở database cũng sẽ chặn.
        if (escrow.getPendingBalance() < gross) {
            throw new IllegalStateException(
                    "Số dư đang giữ không khớp với lịch hẹn " + booking.getId()
                            + ". Cần đối soát thủ công trước khi nhả tiền.");
        }

        long fee = gross * PLATFORM_FEE_PERCENT / 100;
        long net = gross - fee;

        escrow.setPendingBalance(escrow.getPendingBalance() - gross);
        escrow.setBalance(escrow.getBalance() + net);
        escrow.setTotalEarned(escrow.getTotalEarned() + net);
        ghiSo(escrow, Kind.RELEASE, net, booking, null, null,
                "Buổi xem hoàn tất. Đã trừ phí nền tảng " + PLATFORM_FEE_PERCENT + "%.");

        log.info("Ký quỹ nhả {} (phí {}) cho booking {}", net, fee, booking.getId());

        // Có nợ phạt thì thu ngay tại đây, trước khi Reader kịp rút. Thu sau
        // mỗi lần nhả chứ không đợi tới lúc họ xin rút: đợi thì khoản nợ chỉ
        // được thu khi chính người nợ tự nguyện đụng vào ví.
        thuNoPhat(escrow, booking);
    }

    /** Trừ dần nợ phạt vào số dư vừa nhả. Không đụng tới phần đang giữ. */
    private void thuNoPhat(EscrowAccount escrow, Booking booking) {
        long no = escrow.getPenaltyOwed();
        if (no <= 0) return;

        long thu = Math.min(no, escrow.getBalance());
        if (thu <= 0) return;

        escrow.setBalance(escrow.getBalance() - thu);
        escrow.setPenaltyOwed(no - thu);
        ghiSo(escrow, Kind.DEBT_COLLECTED, thu, booking, null, null,
                escrow.getPenaltyOwed() > 0
                        ? "Thu một phần tiền phạt còn nợ. Còn lại "
                                + escrow.getPenaltyOwed() + " đ sẽ trừ vào lần sau."
                        : "Đã thu hết tiền phạt còn nợ.");
        log.info("Thu {} tiền phạt còn nợ của tài khoản ký quỹ {}", thu, escrow.getId());
    }

    @Override
    @Transactional
    public void refundForBooking(Booking booking) {
        EscrowAccount escrow = getOrCreate(booking.getReaderProfile().getUser());
        long amount = booking.getTotalAmount();

        if (escrow.getPendingBalance() < amount) {
            throw new IllegalStateException(
                    "Số dư đang giữ không đủ để hoàn tiền cho lịch hẹn " + booking.getId()
                            + ". Cần đối soát thủ công.");
        }
        escrow.setPendingBalance(escrow.getPendingBalance() - amount);
        ghiSo(escrow, Kind.REFUND, amount, booking, null, null,
                "Lịch hẹn bị huỷ sau khi khách đã trả. Tiền hoàn lại cho khách.");
        log.info("Ký quỹ hoàn {} cho booking {}", amount, booking.getId());
    }

    // =========================================================
    // Vi phạm
    // =========================================================

    @Override
    @Transactional
    public long applyPenalty(UUID userId, long amount, Report report) {
        if (amount <= 0) return 0L;
        EscrowAccount escrow = getOrCreate(userId);

        // Trừ được bao nhiêu thì trừ ngay; phần còn thiếu ghi nợ.
        //
        // KHÔNG đụng tới pendingBalance: đó là tiền của những buổi xem chưa
        // xong, còn có thể phải hoàn lại cho khách. Lấy nó đi để trả nợ phạt là
        // lấy tiền của người thứ ba.
        long truNgay = Math.min(amount, escrow.getBalance());
        long ghiNo = amount - truNgay;

        if (truNgay > 0) {
            escrow.setBalance(escrow.getBalance() - truNgay);
            ghiSo(escrow, Kind.PENALTY, truNgay, null, report, null,
                    "Trừ tiền do vi phạm: " + moTaViPham(report));
        }
        if (ghiNo > 0) {
            escrow.setPenaltyOwed(escrow.getPenaltyOwed() + ghiNo);
            ghiSo(escrow, Kind.PENALTY_DEBT, ghiNo, null, report, null,
                    "Số dư không đủ để trừ hết. Phần còn lại sẽ trừ vào các khoản thu sau.");
        }

        log.info("Phạt {} (trừ ngay {}, ghi nợ {}) cho user {} theo báo cáo {}",
                amount, truNgay, ghiNo, userId, report == null ? null : report.getId());
        return truNgay;
    }

    private String moTaViPham(Report report) {
        if (report == null) return "không rõ";
        String loai = report.getReportType();
        return loai == null || loai.isBlank() ? "vi phạm quy định" : loai;
    }

    // =========================================================
    // Theo lệnh rút
    // =========================================================

    @Override
    @Transactional
    public void reserveForPayout(UUID readerUserId, long amount) {
        EscrowAccount escrow = getOrCreate(readerUserId);
        // Trừ NGAY lúc tạo lệnh chứ không đợi tới lúc duyệt: nếu chỉ kiểm tra
        // số dư mà chưa trừ, Reader tạo mười lệnh rút cùng một khoản tiền và
        // quản trị viên duyệt lần lượt là chi thừa chín lần.
        if (escrow.getBalance() < amount) {
            throw new IllegalArgumentException("Số dư rút được không đủ");
        }
        escrow.setBalance(escrow.getBalance() - amount);
        ghiSo(escrow, Kind.PAYOUT_RESERVE, amount, null, null, null,
                "Đã tạo lệnh rút. Tiền được giữ chỗ trong lúc chờ duyệt.");
    }

    @Override
    @Transactional
    public void returnRejectedPayout(UUID readerUserId, long amount) {
        EscrowAccount escrow = getOrCreate(readerUserId);
        escrow.setBalance(escrow.getBalance() + amount);
        ghiSo(escrow, Kind.PAYOUT_RETURN, amount, null, null, null,
                "Lệnh rút bị từ chối. Tiền trả về số dư.");
    }

    @Override
    @Transactional
    public void settlePayout(UUID readerUserId, long amount) {
        EscrowAccount escrow = getOrCreate(readerUserId);
        // Tiền đã bị trừ khỏi balance từ lúc tạo lệnh, nên ở đây chỉ ghi nhận
        // vào tổng đã rút — trừ thêm lần nữa là trừ hai lần.
        escrow.setTotalWithdrawn(escrow.getTotalWithdrawn() + amount);
        ghiSo(escrow, Kind.PAYOUT_SETTLE, amount, null, null, null,
                "Đã chuyển khoản.");
    }
}
