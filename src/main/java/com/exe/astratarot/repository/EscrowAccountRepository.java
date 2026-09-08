package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.EscrowAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, UUID> {

    /**
     * Khoá hàng ký quỹ trong suốt giao dịch.
     *
     * Hai request cùng cộng vào một tài khoản ký quỹ mà không khoá thì cái sau
     * ghi đè cái trước và một khoản tiền biến mất. PESSIMISTIC_WRITE bắt request
     * thứ hai đợi, nên nó đọc được số dư đã cập nhật.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowAccount e WHERE e.user.id = :userId")
    Optional<EscrowAccount> findByUserId(@Param("userId") UUID userId);
}
