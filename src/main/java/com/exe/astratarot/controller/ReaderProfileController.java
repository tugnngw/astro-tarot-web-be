package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reader.ReaderProfileResponse;
import com.exe.astratarot.domain.dto.reader.UpdateProfileRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderProfileService;
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
public class ReaderProfileController {

    private final ReaderProfileService readerProfileService;

    @PatchMapping("/readers/profile")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = userDetails.getUser().getId();
        readerProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", null));
    }

    @GetMapping("/readers/profile/me")
    @PreAuthorize("hasAnyRole('READER','ADMIN')")
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
}