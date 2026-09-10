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

    // --- Thống kê doanh thu ---
    // COALESCE để không có giao dịch nào thì trả 0 thay vì null, khỏi phải
    // kiểm null ở mọi nơi gọi.

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t WHERE t.status = :status")
    long sumAmountByStatus(@Param("status") TransactionStatus status);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t
            WHERE t.status = :status AND t.createdAt >= :since
            """)
    long sumAmountByStatusSince(@Param("status") TransactionStatus status, @Param("since") java.time.Instant since);

    long countByStatus(TransactionStatus status);

    /**
     * Doanh thu theo từng tháng, mới nhất trước — cho biểu đồ đường.
     * Trả [nhãn tháng "YYYY-MM", tổng tiền]. Dùng native query vì JPQL không có
     * hàm cắt chuỗi ngày theo tháng.
     */
    @Query(value = """
            SELECT to_char(created_at, 'YYYY-MM') AS thang, COALESCE(SUM(amount), 0) AS tong
            FROM payment_transactions
            WHERE status = 'SUCCESS' AND created_at >= :since
            GROUP BY thang
            ORDER BY thang
            """, nativeQuery = true)
    java.util.List<Object[]> sumAmountByMonthSince(@Param("since") java.time.Instant since);
}
