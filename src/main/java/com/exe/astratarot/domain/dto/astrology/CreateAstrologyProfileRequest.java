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

/**
 * Request DTO for creating a user astrology profile.
 *
 * Validates incoming profile data from the client.
 *
 * Sensitive fields (birthTime, birthPlace, latitude, longitude) will be
 * encrypted and stored in the encrypted_data column with an IV in
 * encryption_iv column.
 *
 * Non-sensitive fields are stored as plain text and may be used for queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAstrologyProfileRequest {

    /** User-provided label for this profile (e.g., "My Birth Chart"). Required. */
    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;

    /** Name of the person this profile represents. Optional for SELF profiles. */
    @Size(max = 100, message = "Target name cannot exceed 100 characters")
    private String targetName;

    /** Birth date in YYYY-MM-DD format. Required for zodiac calculations. */
    @NotNull(message = "Birth date is required")
    private LocalDate birthDate;

    /** Birth time in HH:mm:ss format. Optional (if unknown, moon sign/rising sign cannot be calculated precisely). */
    private LocalTime birthTime;

    /** Birth place (city, region, country). Required for timezone and coordinate lookup. */
    @NotBlank(message = "Birth place is required")
    @Size(max = 255, message = "Birth place cannot exceed 255 characters")
    private String birthPlace;

    /** Latitude of birth location in decimal degrees. Required for ephemeris calculations. */
    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;

    /** Longitude of birth location in decimal degrees. Required for ephemeris calculations. */
    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal longitude;

    /** Timezone of birth location (e.g., "America/New_York", "UTC"). Used for time conversion. */
    @Size(max = 50, message = "Timezone cannot exceed 50 characters")
    private String timezone;

    /** Profile type: SELF (own chart), OTHER (someone else's chart), COUPLE (relationship chart). Required. */
    @NotNull(message = "Profile type is required")
    private ProfileType profileType;

    /** Whether this profile is the user's primary/default chart. Optional, defaults to false. */
    private Boolean isPrimary;
}
