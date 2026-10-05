package com.exe.astratarot.domain.dto.ai;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIPlanResponse {

    private UUID purchaseId;
    private String planName;
    private Integer dailyQuota;
    private Long price;
    private Instant startAt;
    private Instant endAt;
    private Integer remainingDays;
    private boolean isActive;

    /**
     * Khi mua qua PayOS: link checkout. Gói chưa kích hoạt cho đến webhook.
     * {@code purchaseId} lúc này có thể null; {@code isActive} = false.
     */
    private String checkoutUrl;
    private String qrCode;
    private boolean paymentPending;
}
