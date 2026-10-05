package com.exe.astratarot.domain.dto.user;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu tự xoá tài khoản.
 *
 * <p>Đòi nhập lại mật khẩu dù người dùng đang đăng nhập. Xoá tài khoản là việc
 * không lùi được; một phiên bị đánh cắp hoặc một chiếc máy để quên không khoá
 * màn hình thì không được phép làm chuyện đó chỉ bằng vài cú chạm.
 *
 * <p>Đây cũng là cách {@code ChangePasswordRequest} đang làm, nên người dùng
 * không gặp hai lối hành xử khác nhau cho hai việc nhạy cảm như nhau.
 */
public record DeleteAccountRequest(
        @NotBlank(message = "Nhập mật khẩu để xác nhận xoá tài khoản")
        String password
) {
}
