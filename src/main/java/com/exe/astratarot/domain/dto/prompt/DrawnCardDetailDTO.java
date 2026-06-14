package com.exe.astratarot.domain.dto.prompt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single tarot card with all details needed for prompt construction.
 *
 * This DTO carries enriched tarot card information (name, arcana type, number)
 * combined with position and reversal status from the draw.
 *
 * Card metadata must be resolved before this DTO is created.
 * The Prompt Builder receives this fully-enriched DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawnCardDetailDTO {

    /**
     * The tarot card's name (e.g., "The Fool", "The Magician", "The High Priestess").
     * Required field.
     */
    @NotBlank(message = "Card name is required")
    private String cardName;

    /**
     * The arcana type (e.g., "Major Arcana", "Minor Arcana", "Cups", "Wands", "Swords", "Pentacles").
     * Required field.
     */
    @NotBlank(message = "Arcana type is required")
    private String arcanaType;

    /**
     * The card number within its arcana (e.g., 0 for The Fool, 1 for The Magician).
     * Optional field; may be null for some card systems.
     */
    private Integer cardNumber;

    /**
     * URL to the card's image representation.
     * Optional field; may be null if no image available.
     */
    private String imageUrl;

    /**
     * Position of this card in the spread (e.g., 0, 1, 2 for Past-Present-Future).
     * Required field; determines card ordering in the prompt.
     */
    @NotNull(message = "Card position is required")
    private Short position;

    /**
     * Whether this card is drawn in reversed orientation.
     * true = reversed, false = upright
     * Required field; must be explicitly set.
     */
    @NotNull(message = "Reversed status is required")
    private Boolean reversed;
}