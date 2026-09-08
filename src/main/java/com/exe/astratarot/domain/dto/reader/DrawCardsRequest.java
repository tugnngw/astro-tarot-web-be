package com.exe.astratarot.domain.dto.reader;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawCardsRequest {
    @NotNull(message = "Reading ID is required")
    private UUID readingId;

    @NotNull(message = "Number of cards is required")
    @Min(value = 1, message = "At least 1 card must be drawn")
    @Max(value = 78, message = "Cannot draw more than 78 cards")
    private Integer numberOfCards;

    @Builder.Default
    private boolean includeReversed = true;
}