package com.exe.astratarot.domain.dto.astrology;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Encapsulates astrological context from an external astrology calculation engine.
 *
 * This DTO is produced by the Astrology Module (via Swiss Ephemeris or similar)
 * and consumed by the AI Tarot Module during reading generation.
 *
 * The AI NEVER calculates astrology. This object carries only the results
 * of external calculations, structured as data facts rather than prompt-ready text.
 *
 * Data is separated into two categories:
 * - Natal Chart: The person's birth chart (unchanging)
 * - Transit Data: Current planetary influences (changes daily)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AstrologyContextDTO {

    // ============= NATAL CHART DATA (Birth Chart - Unchanging) =============

    /**
     * Birth date of the subject.
     * Required for all astrological calculations.
     * Example: 1990-03-21
     */
    @NotNull(message = "Birth date is required")
    private LocalDate birthDate;

    /**
     * Birth time of the subject (optional).
     * Exact time required for precise moon sign, rising sign, and house calculations.
     * May be null if birth time is unknown (00:00 assumed in such cases).
     * Example: 14:30
     */
    private LocalTime birthTime;

    /**
     * Birth place (city and/or region).
     * Used to determine timezone and location context.
     * Example: "New York, NY" or "London, UK"
     */
    @NotBlank(message = "Birth place is required")
    private String birthPlace;

    /**
     * Latitude of birth location (optional).
     * Used for precise geocoding if available.
     * Range: -90.0 to 90.0
     */
    private BigDecimal latitude;

    /**
     * Longitude of birth location (optional).
     * Used for precise geocoding if available.
     * Range: -180.0 to 180.0
     */
    private BigDecimal longitude;

    /**
     * Timezone of birth location (optional).
     * Example: "America/New_York", "Europe/London", "UTC"
     */
    private String timezone;

    // ============= NATAL CHART: PRIMARY SIGNS =============

    /**
     * Sun sign at birth (e.g., "Aries", "Taurus", "Gemini").
     * Fundamental to core identity and life purpose.
     * Always calculated from birth date.
     */
    @NotBlank(message = "Sun sign is required")
    private String sunSign;

    /**
     * Moon sign at birth (e.g., "Libra", "Scorpio", "Sagittarius").
     * Represents emotional nature and inner self.
     * Requires exact birth time for accuracy.
     * May be null if birth time unknown.
     */
    private String moonSign;

    /**
     * Rising sign / Ascendant at birth (e.g., "Cancer", "Leo", "Virgo").
     * Represents personality mask and how one is perceived.
     * Requires exact birth time for accuracy.
     * May be null if birth time unknown.
     */
    private String risingSign;

    // ============= NATAL CHART: PLANETARY POSITIONS =============

    /**
     * Natal planet positions at birth.
     * Includes Sun, Moon, Mercury, Venus, Mars, Jupiter, Saturn, and other relevant bodies.
     * Each planet's position is represented as structured data (name, sign, degree).
     * Required field.
     */
    @NotNull(message = "Natal planet positions are required")
    @Valid
    private List<PlanetPositionDTO> natalPlanetPositions;

    /**
     * Natal aspects between planets (calculated angles).
     * Describes how planets energetically interact in the birth chart.
     * Each aspect includes planets, type, exact degree, and orb.
     * Optional field; may be empty if not calculated.
     */
    @Valid
    private List<AspectDTO> natalAspects;

    /**
     * Ascendant degree position (first house cusp).
     * Example: 14.5 for 14°30' Scorpio
     */
    private BigDecimal ascendantDegree;

    /**
     * Midheaven degree position (tenth house cusp).
     * Represents career, public image, and life direction.
     * Example: 20.75 for 20°45' Cancer
     */
    private BigDecimal midheavenDegree;

    // ============= TRANSIT DATA (Current Influences - Changes Daily) =============

    /**
     * Current astrological transit or cycle.
     * Examples: "Full Moon in Libra", "Mercury Retrograde", "New Moon in Gemini"
     * Provides temporal context for the reading.
     * Optional field.
     */
    private String currentTransit;

    /**
     * Current planet positions (transits).
     * Represents where planets are today (not at birth).
     * Used to determine current influences on the natal chart.
     * Optional field; may be populated when contextual transits are relevant.
     */
    @Valid
    private List<PlanetPositionDTO> transitPlanetPositions;

    /**
     * Current aspects between transiting planets and natal planets.
     * Describes active influences affecting the person today.
     * Example: "Transiting Venus trine natal Mars" (orb 1.2°)
     * Optional field; may be empty if not calculated.
     */
    @Valid
    private List<AspectDTO> transitAspects;
}