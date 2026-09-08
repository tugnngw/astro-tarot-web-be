package com.exe.astratarot.domain.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Token là bắt buộc")
        String token,

        @NotBlank(message = "Mật khẩu mới là bắt buộc")
        @Size(min = 8, max = 128, message = "Mật khẩu phải từ 8 đến 128 ký tự")
        String newPassword
) {
}
