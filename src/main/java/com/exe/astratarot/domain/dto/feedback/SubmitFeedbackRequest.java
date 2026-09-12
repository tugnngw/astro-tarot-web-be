package com.exe.astratarot.domain.dto.feedback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitFeedbackRequest(
        @NotBlank @Size(max = 40) String source,
        @NotNull @Min(0) @Max(10) Integer nps,
        @Min(1) @Max(5) Integer rating,
        @Size(max = 2000) String comment,
        @Size(max = 100) String utmSource,
        @Size(max = 100) String utmMedium,
        @Size(max = 100) String utmCampaign
) {}
