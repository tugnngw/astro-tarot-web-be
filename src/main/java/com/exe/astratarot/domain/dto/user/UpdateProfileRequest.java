package com.exe.astratarot.domain.dto.user;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Cập nhật hồ sơ. Mọi trường đều tuỳ chọn — chỉ trường nào gửi lên (khác null)
 * mới được ghi đè, nên giao diện có thể lưu từng phần mà không sợ xoá trắng
 * những ô nó không đụng tới.
 *
 * Cố ý KHÔNG cho sửa ở đây: email (phải qua luồng xác minh), username, role,
 * status. Nhận chúng từ client rồi tin tưởng ghi thẳng là lỗ hổng leo thang
 * quyền.
 */
public record UpdateProfileRequest(
        @Size(max = 255, message = "Họ tên không được quá 255 ký tự")
        String fullName,

        @Pattern(regexp = "^$|^0[0-9]{9}$",
                message = "Số điện thoại gồm 10 chữ số và bắt đầu bằng 0")
        String phone,

        @Pattern(regexp = "^$|^(MALE|FEMALE|OTHER|UNDISCLOSED)$",
                message = "Giới tính không hợp lệ")
        String gender,

        @Past(message = "Ngày sinh phải ở quá khứ")
        LocalDate dateOfBirth,

        @Size(max = 500, message = "Giới thiệu không được quá 500 ký tự")
        String bio,

        @Size(max = 500, message = "Địa chỉ không được quá 500 ký tự")
        String address,

        @Size(max = 120, message = "Tỉnh/thành không được quá 120 ký tự")
        String city,

        @Size(max = 120, message = "Quốc gia không được quá 120 ký tự")
        String country
) {
}
