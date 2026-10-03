package com.exe.astratarot.domain.dto.ai;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageRecord {

    private UUID userId;
    private LocalDate usageDate;
    private Integer countUsed;
}