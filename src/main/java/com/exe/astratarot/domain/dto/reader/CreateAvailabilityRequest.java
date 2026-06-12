package com.exe.astratarot.domain.dto.reader;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalTime;

@Data
public class CreateAvailabilityRequest {
    @NotNull
    private Short dayOfWeek;
    @NotNull
    private LocalTime startTime;
    @NotNull
    private LocalTime endTime;
}