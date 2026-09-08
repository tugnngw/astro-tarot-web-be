package com.exe.astratarot.domain.dto.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Chỉ thông tin liên hệ. Vai trò và trạng thái có endpoint riêng, có luật riêng. */
@Data
public class UpdateUserInfoRequest {

    @Size(max = 120, message = "Họ tên tối đa 120 ký tự")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phone;

    @Size(max = 120, message = "Thành phố tối đa 120 ký tự")
    private String city;

    private String address;
}
