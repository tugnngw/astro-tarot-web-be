package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.Report;
import com.exe.astratarot.domain.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
    /**
     * Buổi xem này có tố cáo nào chưa kết luận không?
     *
     * <p>PENDING và REVIEWED đều tính là "đang mở": REVIEWED mới chỉ là đã xem
     * qua, chưa ai kết luận đúng sai, nên tiền vẫn phải nằm yên.
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM Report r
            WHERE r.booking.id = :bookingId
              AND r.status IN (com.exe.astratarot.domain.enums.ReportStatus.PENDING,
                               com.exe.astratarot.domain.enums.ReportStatus.REVIEWED)
            """)
    boolean existsOpenForBooking(@Param("bookingId") UUID bookingId);


    @Query("""
            SELECT r FROM Report r
            JOIN FETCH r.reporterUser
            JOIN FETCH r.reportedUser
            LEFT JOIN FETCH r.handledBy
            WHERE (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    Page<Report> search(@Param("status") ReportStatus status, Pageable pageable);

    @Query("""
            SELECT r FROM Report r
            JOIN FETCH r.reporterUser
            JOIN FETCH r.reportedUser
            LEFT JOIN FETCH r.handledBy
            WHERE r.id = :id
            """)
    Optional<Report> findByIdWithParties(@Param("id") UUID id);

    long countByStatus(ReportStatus status);

    /** Chặn một người tố cáo cùng một người nhiều lần khi việc cũ chưa xử lý xong. */
    boolean existsByReporterUserIdAndReportedUserIdAndStatus(
            UUID reporterUserId, UUID reportedUserId, ReportStatus status);
}
