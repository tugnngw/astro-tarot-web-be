package com.exe.astratarot.domain.dto.astrology;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents an astrological aspect between two planets.
 *
 * An aspect is a specific angle between two celestial bodies that
 * indicates how their energies interact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AspectDTO {

    /**
     * The first planet in the aspect (e.g., "Sun", "Mars").
     * Required field.
     */
    @NotBlank(message = "First planet is required")
    private String planet1;

    /**
     * The second planet in the aspect (e.g., "Venus", "Saturn").
     * Required field.
     */
    @NotBlank(message = "Second planet is required")
    private String planet2;

    /**
     * Type of aspect (e.g., "Conjunction", "Square", "Trine", "Opposition", "Sextile").
     * Required field.
     */
    @NotBlank(message = "Aspect type is required")
    private String aspectType;

    /**
     * Exact degree of the aspect.
     * Example: 90.0 for a Square.
     */
    @NotNull(message = "Aspect degree is required")
    private BigDecimal exactDegree;

    /**
     * Orb of the aspect (how far from exact the aspect is).
     * Expressed in degrees. Typical orbs: 8° for Sun/Moon, 6° for planets, 2° for sensitive points.
     * Example: 2.5 means 2.5° from exact.
     */
    @NotNull(message = "Orb is required")
    private BigDecimal orb;

    /**
     * Whether this aspect is considered major (applying) or minor (separating).
     * Optional field; can be used for emphasis in readings.
     */
    private Boolean applying;
}