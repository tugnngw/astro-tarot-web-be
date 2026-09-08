package com.exe.astratarot.domain.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Đăng ký bằng email.
 *
 * Trước đây định danh là username. Username vẫn tồn tại trong CSDL (NOT NULL
 * UNIQUE, và luồng OAuth đang dùng) nhưng giờ được sinh tự động từ email nên
 * người dùng không phải nghĩ ra và nhớ thêm một cái tên nữa.
 */
public record RegisterRequest(
        @NotBlank(message = "Email là bắt buộc")
        @Email(message = "Email không hợp lệ")
        @Size(max = 255, message = "Email không được quá 255 ký tự")
        String email,

        @NotBlank(message = "Mật khẩu là bắt buộc")
        @Size(min = 8, max = 128, message = "Mật khẩu phải từ 8 đến 128 ký tự")
        String password,

        @NotBlank(message = "Họ tên là bắt buộc")
        @Size(max = 255, message = "Họ tên không được quá 255 ký tự")
        String fullName
) {
}
