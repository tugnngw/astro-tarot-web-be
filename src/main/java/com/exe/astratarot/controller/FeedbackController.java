package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.feedback.SubmitFeedbackRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Khảo sát phản hồi người dùng (EXE201 OC3 — mục tiêu ≥20 phản hồi).
 * Cho phép gửi khi chưa đăng nhập (userId null).
 */
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> submit(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody SubmitFeedbackRequest request) {
        UUID userId = me != null ? me.getUser().getId() : null;
        feedbackService.submit(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Cảm ơn phản hồi của bạn", null));
    }

    @GetMapping("/me/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myStatus(
            @AuthenticationPrincipal CustomUserDetails me) {
        boolean submitted = me != null && feedbackService.hasSubmitted(me.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "submitted", submitted,
                "total", feedbackService.countAll(),
                "goal", 20,
                "goalMet", feedbackService.countAll() >= 20
        )));
    }
}
