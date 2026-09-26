package com.exe.astratarot.domain.dto.booking;

import com.exe.astratarot.domain.enums.CalendarDayKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DayCalendarResponse {
    private LocalDate date;
    private CalendarDayKind kind;
    private List<CalendarSlotResponse> slots;
}
