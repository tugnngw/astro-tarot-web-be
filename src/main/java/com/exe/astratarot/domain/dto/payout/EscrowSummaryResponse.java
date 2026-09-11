package com.exe.astratarot.domain.dto.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ba con số ký quỹ nói ba chuyện khác nhau — xem EscrowService. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscrowSummaryResponse {
    /** Đã nhả, rút được ngay. */
    private Long balance;
    /** Khách đã trả nhưng buổi xem chưa xong. Thấy được, chưa rút được. */
    private Long pendingBalance;
    private Long totalEarned;
    private Long totalWithdrawn;
    private Long minimumPayout;

    /**
     * Tiền phạt chưa thu được vì lúc xử lý vi phạm số dư không đủ.
     *
     * <p>Phải bày ra chứ không giấu: khoản này sẽ tự trừ vào các buổi xem sau,
     * nên Reader thấy thu nhập hụt mà không hiểu vì sao là chuyện chắc chắn xảy
     * ra nếu con số này chỉ nằm trong database.
     */
    private Long penaltyOwed;
}
