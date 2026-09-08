package com.exe.astratarot.domain.dto.reader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReaderProfileResponse {
    private UUID id;
    private String username;
    private String bio;
    private String[] specialties;
    private Integer yearsExperience;
    private Long pricePer15m;
    private Long pricePer30m;
    private Long pricePer60m;
    private BigDecimal rating;
    private Integer totalReviews;
    private Boolean isAvailable;
    private List<ReaderAvailabilityResponse> weeklyAvailability;
    private List<ReaderUnavailableDateResponse> unavailableDates;
}