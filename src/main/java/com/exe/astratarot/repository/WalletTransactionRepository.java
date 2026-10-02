package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    @Query("SELECT t FROM WalletTransaction t WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    Page<WalletTransaction> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    Optional<WalletTransaction> findByReferenceId(String referenceId);
}
