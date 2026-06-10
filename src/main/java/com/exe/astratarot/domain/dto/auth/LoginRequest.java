package com.exe.astratarot.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Username is required")
        @Size(max = 50, message = "Username must not exceed 50 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must not exceed 128 characters")
        String password
) {
}
