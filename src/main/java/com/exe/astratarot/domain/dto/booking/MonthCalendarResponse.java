package com.exe.astratarot.domain.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cả tháng, một lần gọi.
 *
 * <p>Xem từng ngày thì mỗi ngày là ba truy vấn. Lật một tháng kiểu đó là khoảng
 * chín mươi truy vấn cho một cái bấm "tháng sau".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthCalendarResponse {
    private int year;
    private int month;
    private int durationMinutes;
    private List<DayCalendarResponse> days;
}
