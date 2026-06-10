package com.exe.astratarot.domain.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank(message = "Verification token is required")
        @Size(max = 256, message = "Verification token must not exceed 256 characters")
        String token
) {
}
