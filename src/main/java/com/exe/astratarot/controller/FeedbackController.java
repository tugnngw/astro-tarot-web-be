package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.feedback.SubmitFeedbackRequest;
import com.exe.astratarot.domain.dto.feedback.UserFeedbackResponse;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Khảo sát phản hồi người dùng (EXE201 OC3 — mục tiêu ≥20 phản hồi).
 * Cho phép gửi khi chưa đăng nhập (userId null).
 * Khi đã đăng nhập: mỗi tài khoản có tối đa 1 bản ghi; gửi lại sẽ cập nhật bản ghi cũ.
 */
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserFeedbackResponse>> submit(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody SubmitFeedbackRequest request) {
        UUID userId = me != null ? me.getUser().getId() : null;
        UserFeedbackResponse response = feedbackService.submit(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Cảm ơn phản hồi của bạn", response));
    }

    @GetMapping("/me/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myStatus(
            @AuthenticationPrincipal CustomUserDetails me) {
        UUID userId = me != null ? me.getUser().getId() : null;
        boolean submitted = userId != null && feedbackService.hasSubmitted(userId);
        UserFeedbackResponse myFeedback = userId != null
                ? feedbackService.getMyFeedback(userId).orElse(null)
                : null;

        Map<String, Object> data = new HashMap<>();
        data.put("submitted", submitted);
        data.put("total", feedbackService.countAll());
        data.put("goal", 20);
        data.put("goalMet", feedbackService.countAll() >= 20);
        data.put("myFeedback", myFeedback);

        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
