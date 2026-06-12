package com.exe.astratarot.domain.dto.reader;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class AddUnavailableDateRequest {
    @NotNull
    private LocalDate unavailableDate;
    private String reason;
}