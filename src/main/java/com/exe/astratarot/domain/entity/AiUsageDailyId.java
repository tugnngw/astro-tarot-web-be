package com.exe.astratarot.domain.entity;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AiUsageDailyId implements Serializable {

    private UUID userId;
    private LocalDate usageDate;

    @Override
    public String toString() {
        return "AiUsageDailyId{" +
                "userId=" + userId +
                ", usageDate=" + usageDate +
                '}';
    }
}
