package com.exe.astratarot.domain.dto.admin;

import com.exe.astratarot.domain.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Họ tên là bắt buộc")
    @Size(max = 120, message = "Họ tên tối đa 120 ký tự")
    private String fullName;

    @NotNull(message = "Phải chọn vai trò")
    private UserRole role;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phone;

    /**
     * Mật khẩu tạm. Bỏ trống thì hệ thống sinh ngẫu nhiên và người dùng bắt buộc
     * phải đi qua luồng "quên mật khẩu" — an toàn hơn là để quản trị viên đặt
     * một mật khẩu rồi nhắn cho nhau qua chat.
     */
    @Size(min = 8, message = "Mật khẩu tạm phải từ 8 ký tự")
    private String temporaryPassword;

    /**
     * TRUE: đánh dấu email đã xác minh, đăng nhập được ngay — dùng khi tạo tài
     * khoản cho đồng nghiệp ngồi cùng phòng.
     * FALSE (mặc định): gửi mail xác minh như người tự đăng ký.
     */
    private Boolean markEmailVerified = false;
}
