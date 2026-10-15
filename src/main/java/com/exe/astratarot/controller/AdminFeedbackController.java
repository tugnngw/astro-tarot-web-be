package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.feedback.AdminFeedbackItemResponse;
import com.exe.astratarot.domain.dto.feedback.FeedbackStatsResponse;
import com.exe.astratarot.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Quản trị phản hồi & đánh giá hệ thống (ADMIN only).
 * Yêu cầu quyền USERS_MANAGE hoặc AUDIT_VIEW.
 */
@RestController
@RequestMapping("/api/v1/admin/feedback")
@RequiredArgsConstructor
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    /**
     * Thống kê tổng hợp phản hồi: tổng số, điểm trung bình, phân bổ 1-5 sao, nguồn gửi.
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<FeedbackStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.success(feedbackService.getStats()));
    }

    /**
     * Danh sách phản hồi phân trang và bộ lọc theo số sao, nguồn và từ khoá.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<Page<AdminFeedbackItemResponse>>> list(
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Mặc định sắp xếp theo ngày gửi mới nhất
        int safeSize = Math.max(1, Math.min(100, size));
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(
                feedbackService.listAdminFeedback(rating, source, keyword, pageable)));
    }
}
