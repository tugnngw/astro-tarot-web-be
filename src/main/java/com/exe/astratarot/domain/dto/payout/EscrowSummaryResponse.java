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
}
