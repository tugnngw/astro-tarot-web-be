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
