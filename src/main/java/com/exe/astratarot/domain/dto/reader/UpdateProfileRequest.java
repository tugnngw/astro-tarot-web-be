package com.exe.astratarot.domain.dto.reader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String bio;
    private String[] specialties;
    private Integer yearsExperience;
    private Long pricePer15m;
    private Long pricePer30m;
    private Long pricePer60m;
}