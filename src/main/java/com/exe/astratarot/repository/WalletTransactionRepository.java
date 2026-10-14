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

    /**
     * Tổng tiền đã hoàn cho những lịch hẹn ĐƯỢC TRẢ BẰNG VÍ.
     *
     * <p>Vì sao phải lọc riêng nhóm ấy: huỷ lịch thì tiền luôn chảy về ví
     * ({@code PaymentServiceImpl.refund} gọi {@code walletService.credit} với
     * {@code BOOKING_REFUND}), nhưng hai lối trả tiền ghi sổ khác nhau.
     *
     * <ul>
     *   <li>Trả thẳng qua cổng: hàng ở {@code payment_transactions} bị lật
     *       SUCCESS → CANCELLED, nên nó TỰ RƠI khỏi tổng doanh thu rồi. Trừ
     *       thêm lần nữa là trừ hai lần.</li>
     *   <li>Trả bằng số dư ví: hàng {@code BOOKING_PAYMENT} KHÔNG bị lật, vẫn
     *       nằm nguyên trong tổng. Không trừ là tính cả những buổi đã huỷ và
     *       đã trả tiền lại cho khách.</li>
     * </ul>
     *
     * <p>Phân biệt hai nhóm ngay trong chính bảng này, không phải nối sang
     * bảng khác: lịch trả bằng ví thì có sẵn một hàng {@code BOOKING_PAYMENT}
     * cùng {@code referenceId} (referenceId là id lịch hẹn).
     *
     * <p>Hoàn một phần cũng đúng: huỷ muộn chỉ hoàn phần ngoài tiền cọc, và
     * hàm này cộng đúng số tiền đã hoàn chứ không cộng giá trị buổi xem.
     */
    @Query("""
            SELECT COALESCE(SUM(r.amount), 0) FROM WalletTransaction r
            WHERE r.type = com.exe.astratarot.domain.enums.WalletTransactionType.BOOKING_REFUND
              AND r.status = :status
              AND EXISTS (
                  SELECT 1 FROM WalletTransaction p
                  WHERE p.referenceId = r.referenceId
                    AND p.type = com.exe.astratarot.domain.enums.WalletTransactionType.BOOKING_PAYMENT
                    AND p.status = :status
              )
            """)
    long sumBookingRefundForWalletPaid(@Param("status") TransactionStatus status);
}
