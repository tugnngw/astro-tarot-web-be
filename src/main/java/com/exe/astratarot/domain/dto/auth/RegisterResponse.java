package com.exe.astratarot.domain.dto.auth;

/**
 * Đăng ký KHÔNG trả token. Tài khoản chỉ đăng nhập được sau khi bấm link xác
 * minh trong email, nên chỗ này chỉ báo lại đã gửi mail tới đâu để giao diện
 * hiển thị đúng địa chỉ.
 */
public record RegisterResponse(
        String email,
        boolean verificationEmailSent
) {
}
