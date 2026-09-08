package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reader.*;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderService;
import com.exe.astratarot.service.ReaderProfileService;
import com.exe.astratarot.service.ReaderAvailabilityService;
import com.exe.astratarot.service.ReaderUnavailableDateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReaderController {

    private final ReaderService readerService;
    private final ReaderProfileService readerProfileService;
    private final ReaderAvailabilityService readerAvailabilityService;
    private final ReaderUnavailableDateService readerUnavailableDateService;

    // -------------------------------------------------
    // Reader Application
    // -------------------------------------------------
    @PostMapping("/readers/apply")
    @PreAuthorize("hasAuthority('READER_APPLY')")
    public ResponseEntity<ApiResponse<String>> apply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                   @RequestBody ApplyReaderRequest request) {
        User user = userDetails.getUser();
        readerService.apply(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Application submitted"));
    }

    // -------------------------------------------------
    // Reader Profile Management
    // -------------------------------------------------
    @PatchMapping("/readers/profile")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = userDetails.getUser().getId();
        readerProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", null));
    }

    @GetMapping("/readers/profile/me")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<ReaderProfileResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(readerProfileService.getMyProfile(userId));
    }

    @GetMapping("/readers")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<List<ReaderProfileResponse>>> getAllVerifiedReaders() {
        return ResponseEntity.ok(readerProfileService.getAllVerifiedReaders());
    }

    @GetMapping("/readers/{readerId}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<ReaderProfileResponse>> getReaderById(@PathVariable UUID readerId) {
        return ResponseEntity.ok(readerProfileService.getReaderById(readerId));
    }

    // -------------------------------------------------
    // Reader Availability Management
    // -------------------------------------------------
    @PostMapping("/availability")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<Void>> createAvailability(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateAvailabilityRequest request) {
        UUID userId = userDetails.getUser().getId();
        readerAvailabilityService.create(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Availability created", null));
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<List<ReaderAvailabilityResponse>>> getWeeklySchedule(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(readerAvailabilityService.getWeeklySchedule(userId));
    }

    @DeleteMapping("/availability/{id}")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<Void>> deleteAvailability(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = userDetails.getUser().getId();
        readerAvailabilityService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Availability deleted", null));
    }

    // -------------------------------------------------
    // Reader Unavailable Dates Management
    // -------------------------------------------------
    @PostMapping("/unavailable-dates")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<Void>> addUnavailableDate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AddUnavailableDateRequest request) {
        UUID userId = userDetails.getUser().getId();
        readerUnavailableDateService.addDate(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Unavailable date added", null));
    }

    @GetMapping("/unavailable-dates")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<List<ReaderUnavailableDateResponse>>> getUnavailableDates(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(readerUnavailableDateService.getUnavailableDates(userId));
    }

    @DeleteMapping("/unavailable-dates/{id}")
    @PreAuthorize("hasAuthority('READER_MANAGE_PROFILE')")
    public ResponseEntity<ApiResponse<Void>> removeUnavailableDate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = userDetails.getUser().getId();
        readerUnavailableDateService.removeDate(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Unavailable date removed", null));
    }
}