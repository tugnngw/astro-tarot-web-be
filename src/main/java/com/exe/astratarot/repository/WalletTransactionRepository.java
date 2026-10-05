package com.exe.astratarot.repository;

import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.domain.enums.WalletTransactionType;
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

    /**
     * Tổng tiền đã trừ ví theo một loại giao dịch.
     *
     * <p>Lưu ý {@code amount} ở bảng này LUÔN DƯƠNG, kể cả giao dịch trừ tiền —
     * chiều tiền đọc ở {@code balanceBefore}/{@code balanceAfter} chứ không đọc
     * ở dấu. Nên tổng trả về là độ lớn, không phải số âm.
     *
     * <p>Dùng để tính phần tiền của lịch hẹn được trả BẰNG VÍ: những lượt ấy
     * không sinh hàng nào trong {@code payment_transactions}, nên chỉ nhìn
     * bảng kia là bỏ sót chúng.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM WalletTransaction t
            WHERE t.type = :type AND t.status = :status
            """)
    long sumAmountByTypeAndStatus(@Param("type") WalletTransactionType type,
                                  @Param("status") TransactionStatus status);
}
