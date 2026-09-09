package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.EscrowAccount;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.EscrowAccountRepository;
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
    private final UserRepository userRepository;

    /**
     * Phần nền tảng giữ lại trên mỗi buổi xem, tính bằng phần trăm.
     *
     * <p>Hằng số vì chưa có bảng cấu hình. Khi làm màn "Cài đặt hệ thống" thì
     * chuyển vào đó — nhưng phải nhớ: đổi phí KHÔNG được ảnh hưởng tới những
     * buổi xem đã hoàn tất, nên lúc ấy phải lưu mức phí vào từng booking chứ
     * không đọc cấu hình hiện hành.
     */
    private static final int PLATFORM_FEE_PERCENT = 15;

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
    // Theo lịch hẹn
    // =========================================================

    @Override
    @Transactional
    public void holdForBooking(Booking booking) {
        EscrowAccount escrow = getOrCreate(booking.getReaderProfile().getUser());
        escrow.setPendingBalance(escrow.getPendingBalance() + booking.getTotalAmount());
        escrowAccountRepository.save(escrow);
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
        escrowAccountRepository.save(escrow);

        log.info("Ký quỹ nhả {} (phí {}) cho booking {}", net, fee, booking.getId());
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
        escrowAccountRepository.save(escrow);
        log.info("Ký quỹ hoàn {} cho booking {}", amount, booking.getId());
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
        escrowAccountRepository.save(escrow);
    }

    @Override
    @Transactional
    public void returnRejectedPayout(UUID readerUserId, long amount) {
        EscrowAccount escrow = getOrCreate(readerUserId);
        escrow.setBalance(escrow.getBalance() + amount);
        escrowAccountRepository.save(escrow);
    }

    @Override
    @Transactional
    public void settlePayout(UUID readerUserId, long amount) {
        EscrowAccount escrow = getOrCreate(readerUserId);
        // Tiền đã bị trừ khỏi balance từ lúc tạo lệnh, nên ở đây chỉ ghi nhận
        // vào tổng đã rút — trừ thêm lần nữa là trừ hai lần.
        escrow.setTotalWithdrawn(escrow.getTotalWithdrawn() + amount);
        escrowAccountRepository.save(escrow);
    }
}
