package com.exe.astratarot.domain.dto.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {
    @NotBlank(message = "Tên người nhận là bắt buộc")
    @Size(max = 150, message = "Tên người nhận tối đa 150 ký tự")
    private String receiverName;

    @NotBlank(message = "Số điện thoại là bắt buộc")
    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0")
    private String receiverPhone;

    @NotBlank(message = "Địa chỉ giao hàng là bắt buộc")
    private String shippingAddress;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;
}
