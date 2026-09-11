package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

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

    long countByReaderProfileIdAndStatus(UUID readerProfileId, BookingStatus status);

    // Dem tat ca booking theo trang thai — cho bang thong ke quan tri.
    long countByStatus(BookingStatus status);

    boolean existsByUserIdAndReaderProfileIdAndStatus(UUID userId, UUID readerProfileId, BookingStatus status);
}
