package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reader.ApplyReaderRequest;
import com.exe.astratarot.domain.dto.reader.ReaderApplicationResponse;
import com.exe.astratarot.domain.dto.reader.ReviewReaderRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReaderController {

    private final ReaderService readerService;

    // -------------------------------------------------
    // USER ENDPOINTS
    // -------------------------------------------------
    @PostMapping("/readers/apply")
    @PreAuthorize("hasAnyRole('USER','READER')")
    public ResponseEntity<ApiResponse<String>> apply(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                   @RequestBody ApplyReaderRequest request) {
        User user = userDetails.getUser();
        readerService.apply(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Application submitted"));
    }

    // -------------------------------------------------
    // ADMIN ENDPOINTS
    // -------------------------------------------------
    @PatchMapping("/admin/readers/{applicationId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> review(@AuthenticationPrincipal CustomUserDetails userDetails,
                                                    @PathVariable UUID applicationId,
                                                    @RequestBody ReviewReaderRequest request) {
        User admin = userDetails.getUser();
        readerService.review(admin.getId(), applicationId, request);
        return ResponseEntity.ok(ApiResponse.success("Application reviewed"));
    }
}
