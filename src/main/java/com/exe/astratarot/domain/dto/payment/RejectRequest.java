package com.exe.astratarot.domain.dto.payment;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Lý do từ chối, dùng chung cho đối soát thanh toán và duyệt lệnh rút. */
@Data
public class RejectRequest {
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
