package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reader.AddUnavailableDateRequest;
import com.exe.astratarot.domain.dto.reader.ReaderUnavailableDateResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderUnavailableDateService;
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
public class ReaderUnavailableDateController {

    private final ReaderUnavailableDateService readerUnavailableDateService;

    @PostMapping("/unavailable-dates")
    @PreAuthorize("hasAnyRole('READER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> addUnavailableDate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AddUnavailableDateRequest request) {
        UUID userId = userDetails.getUser().getId();
        readerUnavailableDateService.addDate(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Unavailable date added", null));
    }

    @GetMapping("/unavailable-dates")
    @PreAuthorize("hasAnyRole('READER','ADMIN')")
    public ResponseEntity<ApiResponse<List<ReaderUnavailableDateResponse>>> getUnavailableDates(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(readerUnavailableDateService.getUnavailableDates(userId));
    }

    @DeleteMapping("/unavailable-dates/{id}")
    @PreAuthorize("hasAnyRole('READER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeUnavailableDate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = userDetails.getUser().getId();
        readerUnavailableDateService.removeDate(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Unavailable date removed", null));
    }
}