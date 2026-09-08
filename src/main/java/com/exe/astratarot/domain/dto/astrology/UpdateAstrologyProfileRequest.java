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
 * Request DTO for updating an existing user astrology profile.
 *
 * All fields are Optional to support partial updates.
 * Only the fields provided in the request will be updated.
 *
 * Sensitive fields (birthTime, birthPlace, latitude, longitude) will be
 * re-encrypted if provided in the update request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAstrologyProfileRequest {

    /** User-provided label for this profile. */
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;

    /** Name of the person this profile represents. */
    @Size(max = 100, message = "Target name cannot exceed 100 characters")
    private String targetName;

    /** Birth date in YYYY-MM-DD format. */
    private LocalDate birthDate;

    /** Birth time in HH:mm:ss format. Optional. */
    private LocalTime birthTime;

    /** Birth place (city, region, country). */
    @Size(max = 255, message = "Birth place cannot exceed 255 characters")
    private String birthPlace;

    /** Latitude of birth location in decimal degrees. */
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private BigDecimal latitude;

    /** Longitude of birth location in decimal degrees. */
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private BigDecimal longitude;

    /** Timezone of birth location (e.g., "America/New_York", "UTC"). */
    @Size(max = 50, message = "Timezone cannot exceed 50 characters")
    private String timezone;

    /** Profile type: SELF, OTHER, or COUPLE. */
    private ProfileType profileType;

    /** Whether this profile is the user's primary/default chart. */
    private Boolean isPrimary;
}