package com.exe.astratarot.domain.dto.astrology;

import com.exe.astratarot.domain.enums.ProfileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Response DTO for user astrology profile.
 *
 * Carries user birth data (decrypted for presentation) and profile metadata.
 * Sensitive fields (birthTime, birthPlace, latitude, longitude) are decrypted
 * from the stored encrypted payload before being returned.
 *
 * Fields that remain in plain text:
 * - id, title, targetName, birthDate (queryable), timezone (needed for queries)
 * - profileType, isPrimary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AstrologyProfileDTO {

    private UUID id;

    /** User-provided label for this profile (e.g., "My Birth Chart", "Partner Profile"). */
    private String title;

    /** Name of the person this profile represents (null for SELF profiles). */
    private String targetName;

    /** Birth date (plain text, queryable). */
    private LocalDate birthDate;

    /** Birth time (decrypted from encrypted payload). Optional. */
    private LocalTime birthTime;

    /** Birth place (decrypted from encrypted payload). */
    private String birthPlace;

    /** Latitude of birth location (decrypted from encrypted payload). */
    private BigDecimal latitude;

    /** Longitude of birth location (decrypted from encrypted payload). */
    private BigDecimal longitude;

    /** Timezone of birth location (plain text, needed for ephemeris calculations). */
    private String timezone;

    /** Profile type: SELF, OTHER, or COUPLE. */
    private ProfileType profileType;

    /** Whether this profile is the user's primary/default chart. */
    private Boolean isPrimary;
}
