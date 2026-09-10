package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.PayoutRequest;
import com.exe.astratarot.domain.enums.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {

    @Query("""
            SELECT p FROM PayoutRequest p
            JOIN FETCH p.reader rp
            JOIN FETCH rp.user
            WHERE rp.user.id = :readerUserId
            ORDER BY p.requestedAt DESC
            """)
    Page<PayoutRequest> findForReader(@Param("readerUserId") UUID readerUserId, Pageable pageable);

    @Query("""
            SELECT p FROM PayoutRequest p
            JOIN FETCH p.reader rp
            JOIN FETCH rp.user
            WHERE (:status IS NULL OR p.status = :status)
            ORDER BY p.requestedAt DESC
            """)
    Page<PayoutRequest> search(@Param("status") PayoutStatus status, Pageable pageable);

    @Query("""
            SELECT p FROM PayoutRequest p
            JOIN FETCH p.reader rp
            JOIN FETCH rp.user
            WHERE p.id = :id
            """)
    java.util.Optional<PayoutRequest> findByIdWithReader(@Param("id") UUID id);

    // --- Thống kê ---

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PayoutRequest p WHERE p.status = :status")
    long sumAmountByStatus(@Param("status") PayoutStatus status);

    long countByStatus(PayoutStatus status);
}
