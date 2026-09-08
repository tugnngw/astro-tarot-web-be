package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.PaymentTransaction;
import com.exe.astratarot.domain.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    List<PaymentTransaction> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    Optional<PaymentTransaction> findFirstByBookingIdAndStatus(UUID bookingId, TransactionStatus status);

    Optional<PaymentTransaction> findByExternalTransactionId(String externalTransactionId);

    @Query("""
            SELECT t FROM PaymentTransaction t
            JOIN FETCH t.user
            LEFT JOIN FETCH t.booking b
            LEFT JOIN FETCH b.readerProfile rp
            LEFT JOIN FETCH rp.user
            WHERE (:status IS NULL OR t.status = :status)
            ORDER BY t.createdAt DESC
            """)
    Page<PaymentTransaction> search(@Param("status") TransactionStatus status, Pageable pageable);
}
