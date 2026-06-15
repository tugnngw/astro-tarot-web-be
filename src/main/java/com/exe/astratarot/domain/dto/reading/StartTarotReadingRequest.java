package com.exe.astratarot.domain.dto.reading;

import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Request DTO for initiating an AI Tarot reading.
 * Contains user details, question, card drawing parameters, and optional spread name.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartTarotReadingRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "User question is required")
    private String question;

    @NotNull(message = "Number of cards is required")
    @Size(min = 1, max = 78, message = "Number of cards must be between 1 and 78")
    private Integer numberOfCards;

    @Builder.Default
    private boolean includeReversed = true;

    private String spreadName;
}
