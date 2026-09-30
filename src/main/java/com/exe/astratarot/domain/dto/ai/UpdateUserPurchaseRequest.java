package com.exe.astratarot.domain.dto.ai;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserPurchaseRequest {

    private Integer dailyQuotaSnapshot;
    private java.time.Instant endAt;
}