package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reader.ReviewReaderRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderProfileService;
import com.exe.astratarot.service.ReaderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReaderProfileService readerProfileService;
    private final ReaderService readerService;

    @GetMapping("/readers/applications")
    @PreAuthorize("hasAuthority('ADMIN_READERS_VIEW')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllPendingApplications(
            @AuthenticationPrincipal CustomUserDetails adminDetails) {
        UUID adminId = adminDetails.getUser().getId();
        return ResponseEntity.ok(readerProfileService.getAllPendingApplications(adminId));
    }

    @PatchMapping("/readers/{applicationId}/review")
    @PreAuthorize("hasAuthority('ADMIN_READERS_REVIEW')")
    public ResponseEntity<ApiResponse<Void>> reviewApplication(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            @PathVariable UUID applicationId,
            @RequestBody ReviewReaderRequest request) {
        UUID adminId = adminDetails.getUser().getId();
        readerService.review(adminId, applicationId, request);
        return ResponseEntity.ok(ApiResponse.success("Application reviewed", null));
    }
}