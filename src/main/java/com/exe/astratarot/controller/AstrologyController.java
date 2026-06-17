package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.astrology.AstrologyProfileDTO;
import com.exe.astratarot.domain.dto.astrology.CreateAstrologyProfileRequest;
import com.exe.astratarot.domain.dto.astrology.UpdateAstrologyProfileRequest;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.AstrologyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for managing user astrology profiles.
 *
 * Endpoints:
 * - POST   /api/me/astrology/profiles              - Create profile
 * - GET    /api/me/astrology/profiles              - Get all profiles
 * - GET    /api/me/astrology/profiles/primary      - Get primary profile
 * - PUT    /api/me/astrology/profiles/{profileId}  - Update profile
 * - DELETE /api/me/astrology/profiles/{profileId}  - Delete profile
 *
 * Security:
 * - All endpoints require JWT authentication.
 * - User ID is extracted from @AuthenticationPrincipal CustomUserDetails.
 * - Users can only manage their own profiles (enforced via userId).
 *
 * API Design:
 * - Use /api/me/astrology/profiles (not /api/astrology/profiles/{userId})
 * - Avoids exposing userId in URL path.
 * - Follows Spring Security best practices.
 */

@RestController
@RequestMapping("/api/me/astrology/profiles")
@RequiredArgsConstructor
@Slf4j
public class AstrologyController {

    private final AstrologyService astrologyService;

    // ===== CREATE PROFILE =====

    /**
     * Create a new astrology profile for the authenticated user.
     *
     * @param userDetails Authenticated user details (from JWT token)
     * @param request Profile data (birthDate, birthTime, birthPlace, coordinates, timezone, etc.)
     * @return Created profile with HTTP 201 CREATED
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AstrologyProfileDTO>> createProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateAstrologyProfileRequest request) {

        UUID userId = userDetails.getUser().getId();
        log.info("Creating astrology profile for user: {}", userId);

        AstrologyProfileDTO createdProfile = astrologyService.createProfile(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Astrology profile created successfully", createdProfile));
    }

    // ===== GET ALL PROFILES =====

    /**
     * Get all astrology profiles for the authenticated user.
     *
     * @param userDetails Authenticated user details (from JWT token)
     * @return List of profiles (may be empty)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AstrologyProfileDTO>>> getProfiles(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        UUID userId = userDetails.getUser().getId();
        log.info("Fetching all astrology profiles for user: {}", userId);

        List<AstrologyProfileDTO> profiles = astrologyService.getProfiles(userId);

        return ResponseEntity.ok(
                ApiResponse.success("Astrology profiles retrieved successfully", profiles));
    }

    // ===== GET PRIMARY PROFILE =====

    /**
     * Get the primary astrology profile for the authenticated user.
     *
     * @param userDetails Authenticated user details (from JWT token)
     * @return Primary profile if found, otherwise 404 Not Found
     */
    @GetMapping("/primary")
    public ResponseEntity<ApiResponse<AstrologyProfileDTO>> getPrimaryProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        UUID userId = userDetails.getUser().getId();
        log.info("Fetching primary astrology profile for user: {}", userId);

        Optional<AstrologyProfileDTO> primaryProfile = astrologyService.getPrimaryProfile(userId);

        return primaryProfile
                .map(profile -> ResponseEntity.ok(
                        ApiResponse.success("Primary profile retrieved successfully", profile)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("No primary profile found")));
    }

    // ===== UPDATE PROFILE =====

    /**
     * Update an existing astrology profile.
     *
     * @param userDetails Authenticated user details (from JWT token)
     * @param profileId Profile ID to update
     * @param request Updated profile data (partial update - only provided fields are updated)
     * @return Updated profile
     */
    @PutMapping("/{profileId}")
    public ResponseEntity<ApiResponse<AstrologyProfileDTO>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID profileId,
            @Valid @RequestBody UpdateAstrologyProfileRequest request) {

        UUID userId = userDetails.getUser().getId();
        log.info("Updating astrology profile {} for user: {}", profileId, userId);

        try {
            AstrologyProfileDTO updatedProfile = astrologyService.updateProfile(userId, profileId, request);
            return ResponseEntity.ok(
                    ApiResponse.success("Astrology profile updated successfully", updatedProfile));
        } catch (IllegalArgumentException e) {
            log.warn("Profile not found or access denied: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Profile not found"));
        }
    }

    // ===== DELETE PROFILE =====

    /**
     * Delete an astrology profile.
     *
     * @param userDetails Authenticated user details (from JWT token)
     * @param profileId Profile ID to delete
     * @return Empty response with HTTP 200 OK
     */
    @DeleteMapping("/{profileId}")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID profileId) {

        UUID userId = userDetails.getUser().getId();
        log.info("Deleting astrology profile {} for user: {}", profileId, userId);

        try {
            astrologyService.deleteProfile(userId, profileId);
            return ResponseEntity.ok(ApiResponse.<Void>success("Astrology profile deleted successfully", null));
        } catch (IllegalArgumentException e) {
            log.warn("Profile not found or access denied: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Profile not found"));
        }
    }
}
