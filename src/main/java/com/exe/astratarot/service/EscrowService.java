package com.exe.astratarot.service;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.EscrowAccount;
import com.exe.astratarot.domain.entity.User;

import java.util.UUID;

/**
 * Ký quỹ: tiền khách trả được GIỮ lại cho tới khi buổi xem thực sự diễn ra.
 *
 * <p>Vì sao phải giữ chứ không trả thẳng cho Reader: khách trả tiền trước cho
 * một dịch vụ chưa xảy ra. Nếu Reader nhận tiền ngay lúc đặt lịch thì họ không
 * còn động cơ nào để có mặt, và khách cũng không có đường đòi lại. Giữ ở ký quỹ
 * làm cả hai phía cùng an toàn.
 *
 * <p>Ba con số trong escrow_accounts nói ba chuyện khác nhau:
 * <ul>
 *   <li>{@code pendingBalance} — khách đã trả, buổi xem chưa xong. Reader thấy
 *       nhưng chưa rút được.</li>
 *   <li>{@code balance} — đã nhả, rút được.</li>
 *   <li>{@code totalEarned}, {@code totalWithdrawn} — cộng dồn, chỉ để hiển thị.</li>
 * </ul>
 */
public interface EscrowService {

    /** Tài khoản ký quỹ của một người, tạo mới nếu chưa có. */
    EscrowAccount getOrCreate(User user);

    EscrowAccount getOrCreate(UUID userId);

    /** Khách trả tiền: cộng vào phần đang giữ của Reader. */
    void holdForBooking(Booking booking);

    /**
     * Buổi xem hoàn tất: chuyển từ đang giữ sang rút được, sau khi trừ phí
     * nền tảng.
     */
    void releaseForBooking(Booking booking);

    /** Huỷ sau khi đã trả tiền: gỡ khỏi phần đang giữ để hoàn lại cho khách. */
    void refundForBooking(Booking booking);

    /**
     * Trừ tiền phạt khi một báo cáo vi phạm được kết luận là đúng.
     *
     * <p>Trừ được bao nhiêu thì trừ ngay, phần còn thiếu ghi vào
     * {@code penaltyOwed} và thu dần ở những lần nhả tiền sau. Không đụng tới
     * phần đang giữ: đó là tiền của các buổi xem chưa xong, còn có thể phải
     * hoàn lại cho khách.
     *
     * @return số tiền trừ được ngay; phần còn lại đã thành nợ.
     */
    long applyPenalty(UUID userId, long amount, com.exe.astratarot.domain.entity.Report report);

    /** Giữ chỗ số tiền Reader xin rút, để họ không xin rút hai lần cùng một khoản. */
    void reserveForPayout(UUID readerUserId, long amount);

    /** Lệnh rút bị từ chối: trả tiền về số dư rút được. */
    void returnRejectedPayout(UUID readerUserId, long amount);

    /** Lệnh rút đã chi: ghi nhận vào tổng đã rút. */
    void settlePayout(UUID readerUserId, long amount);
}
