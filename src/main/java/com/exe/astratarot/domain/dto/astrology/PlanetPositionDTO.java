package com.exe.astratarot.domain.dto.astrology;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents a planet's position in the zodiac at a specific point in time.
 *
 * This DTO is used to convey structured planetary data from the external
 * astrology calculation module to the AI Tarot Module.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanetPositionDTO {

    /**
     * Name of the planet (e.g., "Sun", "Moon", "Mercury").
     * Required field.
     */
    @NotBlank(message = "Planet name is required")
    private String planetName;

    /**
     * Zodiac sign the planet is currently in (e.g., "Aries", "Taurus").
     * Required field.
     */
    @NotBlank(message = "Zodiac sign is required")
    private String sign;

    /**
     * Degree position within the zodiac sign (0-30).
     * Example: 15.5 for 15°30'.
     * Required field for precise positioning.
     */
    @NotNull(message = "Degree position is required")
    private BigDecimal degree;

    /**
     * The astrological house the planet occupies (1-12).
     * Optional field; may not be calculated in all contexts.
     */
    private Integer house;

    /**
     * Whether the planet is in retrograde motion.
     * Optional field; defaults to false if not specified.
     */
    private Boolean retrograde;
}