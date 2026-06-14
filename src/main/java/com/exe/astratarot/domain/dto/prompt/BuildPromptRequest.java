package com.exe.astratarot.domain.dto.prompt;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Input DTO for the Prompt Builder Service.
 *
 * Contains all data required to construct a complete prompt for the AI Tarot Engine.
 * All data must be pre-prepared before being passed to the PromptBuilderService.
 *
 * The Prompt Builder is responsible only for composition, not data enrichment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuildPromptRequest {

    /**
     * The user's question for the reading.
     * Provided exactly as the user entered it.
     * Required field.
     */
    @NotBlank(message = "User question is required")
    private String userQuestion;

    /**
     * Pre-calculated astrological context (natal chart + transits).
     * Must be created by the Astrology Module before being passed here.
     * The AI NEVER calculates astrology; this DTO contains only external calculations.
     * Required field.
     */
    @NotNull(message = "Astrology context is required")
    @Valid
    private AstrologyContextDTO astrologyContext;

    /**
     * List of drawn tarot cards with complete enriched details.
     * Card metadata (name, arcana, number) must be resolved before creation.
     * The order in this list determines the order in the final prompt.
     * Required field; must not be empty.
     */
    @NotNull(message = "Drawn card details are required")
    @NotEmpty(message = "At least one card must be drawn")
    @Valid
    private List<DrawnCardDetailDTO> drawnCardDetails;

    /**
     * The name or description of the tarot spread used (optional).
     * Examples: "Three-Card Spread", "Celtic Cross", "Past-Present-Future"
     * If null or blank, a generic description will be used in the prompt.
     */
    private String spreadName;
}