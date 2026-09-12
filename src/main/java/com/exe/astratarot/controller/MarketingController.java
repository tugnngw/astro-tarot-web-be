package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.marketing.TrackMarketingEventRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.MarketingEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Ghi nhận CTA / UTM cho CAP (EXE201 OC2). */
@RestController
@RequestMapping("/api/v1/marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingEventService marketingEventService;

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<Void>> track(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody TrackMarketingEventRequest request) {
        UUID userId = me != null ? me.getUser().getId() : null;
        marketingEventService.track(userId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/events/summary")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> summary() {
        return ResponseEntity.ok(ApiResponse.success(marketingEventService.countsLast30Days()));
    }
}
