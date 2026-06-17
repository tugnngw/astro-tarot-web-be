package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.astrology.AstrologyProfileDTO;
import com.exe.astratarot.domain.dto.astrology.CreateAstrologyProfileRequest;
import com.exe.astratarot.domain.dto.astrology.UpdateAstrologyProfileRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user astrology profiles.
 *
 * Responsibilities:
 * - CRUD operations for user astrological data
 * - Encryption of sensitive birth data (birthTime, birthPlace, latitude, longitude)
 * - Access control (user can only manage their own profiles)
 *
 * Sensitive fields stored in encrypted payload:
 * {
 *   "version": 1,
 *   "birthTime": "HH:mm:ss",
 *   "birthPlace": "city, country",
 *   "latitude": 21.0285,
 *   "longitude": 105.8542
 * }
 *
 * Non-sensitive fields stored as plain-text (queryable):
 * - birthDate (used for zodiac calculations)
 * - timezone (used for time conversions)
 * - profileType (SELF, OTHER, COUPLE)
 *
 * Placeholder for future calculation methods:
 * - getNatalChart(UUID userId)
 * - getTransitChart(UUID userId, LocalDateTime dateTime)
 * (Out of scope for Milestone 1, planned for Phase 2: Swiss Ephemeris Integration)
 */
public interface AstrologyService {

    /**
     * Create a new astrology profile for a user.
     *
     * @param userId the authenticated user ID
     * @param request profile data (birthDate, birthTime, birthPlace, birthPlace, timezone)
     * @return the created profile DTO (with decrypted sensitive fields)
     * @throws IllegalArgumentException if validation fails
     */
    AstrologyProfileDTO createProfile(UUID userId, CreateAstrologyProfileRequest request);

    /**
     * Get all astrology profiles for a user.
     *
     * @param userId the authenticated user ID
     * @return list of profiles (all fields decrypted for return)
     */
    List<AstrologyProfileDTO> getProfiles(UUID userId);

    /**
     * Get the primary astrology profile for a user.
     *
     * @param userId the authenticated user ID
     * @return the primary profile if one exists, otherwise empty
     */
    Optional<AstrologyProfileDTO> getPrimaryProfile(UUID userId);

    /**
     * Update an existing astrology profile.
     *
     * @param userId the authenticated user ID
     * @param profileId the profile to update
     * @param request updated profile data (partial update - only provided fields are updated)
     * @return the updated profile DTO
     * @throws IllegalArgumentException if profile not found or validation fails
     */
    AstrologyProfileDTO updateProfile(UUID userId, UUID profileId, UpdateAstrologyProfileRequest request);

    /**
     * Delete an astrology profile.
     *
     * @param userId the authenticated user ID
     * @param profileId the profile to delete
     * @throws IllegalArgumentException if profile not found
     */
    void deleteProfile(UUID userId, UUID profileId);
}