package com.exe.astratarot.domain.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email là bắt buộc")
        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email không được quá 255 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu là bắt buộc")
        @Size(max = 128, message = "Mật khẩu không được quá 128 ký tự")
        String password
) {
}
