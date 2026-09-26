package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Các buổi xem đã kết thúc quá hạn mà vẫn đứng ở CONFIRMED.
     *
     * <p>Trả về ID chứ không phải entity: mỗi buổi sẽ được chốt trong một giao
     * dịch riêng, nên nạp cả đối tượng ở đây rồi dùng lại ở giao dịch khác chỉ
     * tạo ra thực thể lạc khỏi phiên làm việc.
     */
    @Query("""
            SELECT b.id FROM Booking b
            WHERE b.status = :status
              AND b.paymentStatus = :paymentStatus
              AND b.endTime < :truoc
            ORDER BY b.endTime ASC
            """)
    List<UUID> findIdsDueForSettlement(@Param("status") BookingStatus status,
                                       @Param("paymentStatus") PaymentStatus paymentStatus,
                                       @Param("truoc") Instant truoc,
                                       Pageable pageable);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.readerProfile rp
            JOIN FETCH rp.user
            JOIN FETCH b.user
            WHERE b.user.id = :userId
              AND (:status IS NULL OR b.status = :status)
            ORDER BY b.startTime DESC
            """)
    Page<Booking> findForUser(@Param("userId") UUID userId,
                              @Param("status") BookingStatus status,
                              Pageable pageable);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.user
            JOIN FETCH b.readerProfile rp
            JOIN FETCH rp.user
            WHERE rp.user.id = :readerUserId
              AND (:status IS NULL OR b.status = :status)
            ORDER BY b.startTime DESC
            """)
    Page<Booking> findForReader(@Param("readerUserId") UUID readerUserId,
                                @Param("status") BookingStatus status,
                                Pageable pageable);

    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.user
            JOIN FETCH b.readerProfile rp
            JOIN FETCH rp.user
            WHERE b.id = :id
            """)
    java.util.Optional<Booking> findByIdWithParties(@Param("id") UUID id);

    /**
     * Những lượt đặt còn hiệu lực của một Reader trong một khoảng thời gian.
     *
     * <p>Điều kiện chồng lấn viết là {@code start < :end AND end > :start} chứ
     * không phải so sánh hai đầu riêng lẻ: hai khoảng chỉ KHÔNG chồng nhau khi
     * một cái kết thúc trước hoặc đúng lúc cái kia bắt đầu. Viết sai chỗ này là
     * cho đặt trùng giờ mà tới lúc gặp mới biết.
     *
     * <p>Đơn đã huỷ không tính — huỷ xong thì khung giờ đó phải mở lại cho
     * người khác.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.readerProfile.id = :readerProfileId
              AND b.status <> com.exe.astratarot.domain.enums.BookingStatus.CANCELLED
              AND b.startTime < :end
              AND b.endTime > :start
            """)
    List<Booking> findOverlapping(@Param("readerProfileId") UUID readerProfileId,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);

    /**
     * Buổi của khách trong một tháng, tính theo giờ bắt đầu.
     *
     * <p>Kèm các bên để dựng {@code BookingResponse} mà không nạp thêm từng dòng.
     * Lịch huỷ cũng nằm trong kết quả: người đặt cần thấy buổi mình đã bỏ.
     */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.user
            JOIN FETCH b.readerProfile rp
            JOIN FETCH rp.user
            WHERE b.user.id = :userId
              AND b.startTime >= :start
              AND b.startTime < :end
            ORDER BY b.startTime ASC
            """)
    List<Booking> findForUserBetween(@Param("userId") UUID userId,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end);

    /** Buổi khách đặt với Reader này trong một tháng, tính theo giờ bắt đầu. */
    @Query("""
            SELECT b FROM Booking b
            JOIN FETCH b.user
            JOIN FETCH b.readerProfile rp
            JOIN FETCH rp.user
            WHERE rp.user.id = :readerUserId
              AND b.startTime >= :start
              AND b.startTime < :end
            ORDER BY b.startTime ASC
            """)
    List<Booking> findForReaderBetween(@Param("readerUserId") UUID readerUserId,
                                       @Param("start") Instant start,
                                       @Param("end") Instant end);

    long countByReaderProfileIdAndStatus(UUID readerProfileId, BookingStatus status);

    // Dem tat ca booking theo trang thai — cho bang thong ke quan tri.
    long countByStatus(BookingStatus status);

    /**
     * Những booking DEPOSIT_PAID đang quá hạn thanh toán nốt
     * (paymentDeadline đã qua mà paymentStatus vẫn là DEPOSIT_PAID).
     */
    @Query("""
            SELECT b.id FROM Booking b
            WHERE b.paymentStatus = :status
              AND b.paymentDeadline IS NOT NULL
              AND b.paymentDeadline < :now
            """)
    List<UUID> findOverdueDepositPaid(@Param("status") PaymentStatus status,
                                       @Param("now") Instant now);

    /**
     * Những booking DEPOSIT_PAID sắp đến hạn nhắc thanh toán (T-24h).
     */
    @Query("""
            SELECT b.id FROM Booking b
            WHERE b.paymentStatus = :status
              AND b.paymentDeadline IS NOT NULL
              AND b.paymentDeadline BETWEEN :now AND :nextDeadline
            """)
    List<UUID> findDepositPaidNearDeadline(
            @Param("status") PaymentStatus status,
            @Param("now") Instant now,
            @Param("nextDeadline") Instant nextDeadline);

    boolean existsByUserIdAndReaderProfileIdAndStatus(UUID userId, UUID readerProfileId, BookingStatus status);
}
