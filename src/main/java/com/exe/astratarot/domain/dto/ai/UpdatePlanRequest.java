package com.exe.astratarot.domain.dto.ai;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlanRequest {

    private Integer dailyQuota;
    private Long price;
    private Boolean isActive;
    private String description;
}