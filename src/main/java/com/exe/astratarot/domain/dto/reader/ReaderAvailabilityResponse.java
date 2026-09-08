package com.exe.astratarot.domain.dto.reader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReaderAvailabilityResponse {
    private UUID id;
    private Short dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isActive;
}