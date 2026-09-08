package com.exe.astratarot.domain.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Một khung giờ có thể đặt. Đã trừ ngày bận và các lượt đặt đang giữ chỗ. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotResponse {
    private Instant startTime;
    private Instant endTime;
    private Long price;
}
