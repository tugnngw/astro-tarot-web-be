package com.exe.astratarot.domain.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Mật khẩu hiện tại là bắt buộc")
        String currentPassword,

        @NotBlank(message = "Mật khẩu mới là bắt buộc")
        @Size(min = 8, max = 128, message = "Mật khẩu phải từ 8 đến 128 ký tự")
        String newPassword
) {
}
