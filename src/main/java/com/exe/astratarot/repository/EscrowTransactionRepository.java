package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.EscrowTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EscrowTransactionRepository extends JpaRepository<EscrowTransaction, UUID> {

    /** Sổ của một người, mới nhất trước — đúng thứ tự tab "Thu nhập" cần. */
    Page<EscrowTransaction> findByAccountUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
