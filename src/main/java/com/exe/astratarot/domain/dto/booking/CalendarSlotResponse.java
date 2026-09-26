package com.exe.astratarot.domain.dto.booking;

import com.exe.astratarot.domain.enums.CalendarSlotState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Một ô giờ trong lịch tháng.
 *
 * <p>Không kèm tên người đã đặt. Lịch này công khai, khách chưa đăng nhập cũng
 * xem được — lộ tên là lộ lịch làm việc của người khác.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarSlotResponse {
    private Instant startTime;
    private Instant endTime;
    private Long price;
    private CalendarSlotState state;
}
