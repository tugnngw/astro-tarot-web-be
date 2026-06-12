package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reader.CreateAvailabilityRequest;
import com.exe.astratarot.domain.dto.reader.ReaderAvailabilityResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderAvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReaderAvailabilityController {

    private final ReaderAvailabilityService readerAvailabilityService;

    @PostMapping("/availability")
    @PreAuthorize("hasAnyRole('READER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> createAvailability(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateAvailabilityRequest request) {
        UUID userId = userDetails.getUser().getId();
        readerAvailabilityService.create(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Availability created", null));
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAnyRole('READER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReaderAvailabilityResponse>>> getWeeklySchedule(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(readerAvailabilityService.getWeeklySchedule(userId));
    }

    @DeleteMapping("/availability/{id}")
    @PreAuthorize("hasAnyRole('READER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAvailability(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = userDetails.getUser().getId();
        readerAvailabilityService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Availability deleted", null));
    }
}