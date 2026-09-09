package com.exe.astratarot.domain.dto.payout;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePayoutRequest {

    @NotNull(message = "Phải nhập số tiền")
    @Min(value = 1, message = "Số tiền phải lớn hơn 0")
    private Long amount;

    @NotBlank(message = "Phải nhập tên ngân hàng")
    @Size(max = 255)
    private String bankName;

    @NotBlank(message = "Phải nhập số tài khoản")
    @Size(max = 255)
    private String bankAccount;

    @NotBlank(message = "Phải nhập tên chủ tài khoản")
    @Size(max = 255)
    private String accountHolder;
}
