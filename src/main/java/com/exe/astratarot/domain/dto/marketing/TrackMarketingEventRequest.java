package com.exe.astratarot.domain.dto.marketing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrackMarketingEventRequest(
        @NotBlank @Size(max = 80) String eventName,
        @Size(max = 500) String path,
        @Size(max = 100) String utmSource,
        @Size(max = 100) String utmMedium,
        @Size(max = 100) String utmCampaign,
        String metadata
) {}
