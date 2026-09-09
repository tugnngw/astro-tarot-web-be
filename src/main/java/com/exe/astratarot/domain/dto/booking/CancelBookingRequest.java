package com.exe.astratarot.domain.dto.booking;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CancelBookingRequest {
    /** Người bị huỷ đọc được lý do này, nên để trống thì hơn là viết cho có. */
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
