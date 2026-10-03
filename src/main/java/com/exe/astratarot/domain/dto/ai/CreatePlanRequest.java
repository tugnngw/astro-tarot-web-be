package com.exe.astratarot.domain.dto.ai;

import com.exe.astratarot.domain.entity.SubscriptionPlan;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePlanRequest {

    private SubscriptionPlan.PlanType planType;
    private String name;
    private Integer dailyQuota;
    private Long price;
    private Integer durationDays;
    private String description;
}
