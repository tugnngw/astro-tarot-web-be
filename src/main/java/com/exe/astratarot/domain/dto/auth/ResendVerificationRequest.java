package com.exe.astratarot.domain.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank(message = "Email là bắt buộc")
        @Email(message = "Email không hợp lệ")
        String email
) {
}
