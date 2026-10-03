package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserWalletRepository extends JpaRepository<UserWallet, UUID> {

    @Query("SELECT w FROM UserWallet w WHERE w.user.id = :userId")
    Optional<UserWallet> findByUserId(@Param("userId") UUID userId);

    /**
     * Khoá hàng ví trong suốt giao dịch để ngăn chặn race condition và số dư âm khi có nhiều request đồng thời.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM UserWallet w WHERE w.user.id = :userId")
    Optional<UserWallet> findByUserIdWithLock(@Param("userId") UUID userId);
}
