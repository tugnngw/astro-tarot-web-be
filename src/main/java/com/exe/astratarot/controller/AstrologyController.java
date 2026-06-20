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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/astrology/profiles")
@RequiredArgsConstructor
@Slf4j
public class AstrologyController {

    private final AstrologyService astrologyService;

    @PostMapping
    public ResponseEntity<ApiResponse<AstrologyProfileDTO>> createProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateAstrologyProfileRequest request) {

        // ✅ Lấy user từ SecurityContext
        if (userDetails == null) {
            log.error("❌ User not authenticated");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        UUID userId = userDetails.getUser().getId();
        log.info("✅ Creating astrology profile for user: {}", userId);

        AstrologyProfileDTO createdProfile = astrologyService.createProfile(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Astrology profile created successfully", createdProfile));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AstrologyProfileDTO>>> getProfiles(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        UUID userId = userDetails.getUser().getId();
        log.info("📋 Fetching all astrology profiles for user: {}", userId);

        List<AstrologyProfileDTO> profiles = astrologyService.getProfiles(userId);

        return ResponseEntity.ok(
                ApiResponse.success("Astrology profiles retrieved successfully", profiles));
    }

    @GetMapping("/primary")
    public ResponseEntity<ApiResponse<AstrologyProfileDTO>> getPrimaryProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        UUID userId = userDetails.getUser().getId();
        log.info("📌 Fetching primary astrology profile for user: {}", userId);

        Optional<AstrologyProfileDTO> primaryProfile = astrologyService.getPrimaryProfile(userId);

        return primaryProfile
                .map(profile -> ResponseEntity.ok(
                        ApiResponse.success("Primary profile retrieved successfully", profile)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("No primary profile found")));
    }

    @PutMapping("/{profileId}")
    public ResponseEntity<ApiResponse<AstrologyProfileDTO>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID profileId,
            @Valid @RequestBody UpdateAstrologyProfileRequest request) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        UUID userId = userDetails.getUser().getId();
        log.info("✏️ Updating astrology profile {} for user: {}", profileId, userId);

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

    @DeleteMapping("/{profileId}")
    public ResponseEntity<ApiResponse<Void>> deleteProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID profileId) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("User not authenticated"));
        }

        UUID userId = userDetails.getUser().getId();
        log.info("🗑️ Deleting astrology profile {} for user: {}", profileId, userId);

        try {
            astrologyService.deleteProfile(userId, profileId);
            return ResponseEntity.ok(ApiResponse.success("Astrology profile deleted successfully", null));
        } catch (IllegalArgumentException e) {
            log.warn("Profile not found or access denied: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Profile not found"));
        }
    }
}