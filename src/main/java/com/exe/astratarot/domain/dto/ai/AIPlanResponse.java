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
}
