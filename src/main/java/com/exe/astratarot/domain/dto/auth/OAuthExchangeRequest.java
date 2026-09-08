package com.exe.astratarot.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthExchangeRequest(
        @NotBlank(message = "OAuth exchange code is required")
        @Size(max = 256, message = "OAuth exchange code must not exceed 256 characters")
        String code
) {
}
