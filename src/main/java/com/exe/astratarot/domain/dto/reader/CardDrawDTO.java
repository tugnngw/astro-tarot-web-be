package com.exe.astratarot.domain.dto.reader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardDrawDTO {
    private UUID cardId;
    private Short position;
    private Boolean reversed;
}