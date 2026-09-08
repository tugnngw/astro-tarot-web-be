package com.exe.astratarot.domain.dto.booking;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CreateBookingRequest {

    @NotNull(message = "Phải chọn Reader")
    private UUID readerProfileId;

    @NotNull(message = "Phải chọn giờ bắt đầu")
    private Instant startTime;

    /**
     * Chỉ nhận 15, 30 hoặc 60 — đúng ba mốc giá Reader khai báo. Không cho gửi
     * số phút tuỳ ý, nếu không sẽ phải bịa ra một công thức giá mà Reader chưa
     * bao giờ đồng ý.
     */
    @NotNull(message = "Phải chọn thời lượng")
    private Integer durationMinutes;
}
