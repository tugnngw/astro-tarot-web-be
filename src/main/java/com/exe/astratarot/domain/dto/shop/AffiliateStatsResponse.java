package com.exe.astratarot.domain.dto.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Số liệu tiếp thị liên kết.
 *
 * estimatedCommission là ƯỚC LƯỢNG (giá × tỉ lệ × lượt bấm), KHÔNG phải doanh
 * thu. Mình không biết ai bấm rồi có mua hay không — số thật nằm ở báo cáo của
 * sàn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AffiliateStatsResponse {
    private long totalClicks;
    private long clicksInPeriod;
    private int periodDays;
    private long productsWithLink;
    private long productsWithoutLink;
    private long estimatedCommission;
    private List<ProductResponse> topProducts;
}
