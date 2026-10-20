package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.analytics.DangOnlineResponse;
import com.exe.astratarot.domain.dto.analytics.GA4ReportResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.service.analytics.GA4AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Quản trị số liệu phân tích lưu lượng Google Analytics 4 (GA4).
 * Yêu cầu quyền USERS_MANAGE hoặc AUDIT_VIEW.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final GA4AnalyticsService ga4AnalyticsService;

    /**
     * Lấy báo cáo số liệu GA4 theo khoảng ngày.
     *
     * @param startDate Ngày bắt đầu (mặc định "30daysAgo")
     * @param endDate   Ngày kết thúc (mặc định "today")
     * @return Báo cáo GA4 được bọc trong ApiResponse chuẩn
     */
    @GetMapping("/report")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<GA4ReportResponse>> getReport(
            @RequestParam(name = "startDate", defaultValue = "30daysAgo") String startDate,
            @RequestParam(name = "endDate", defaultValue = "today") String endDate
    ) {
        GA4ReportResponse report = ga4AnalyticsService.getReport(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * Ai đang ở trên web ngay bây giờ, ở đâu, đang xem gì.
     *
     * <p>Tách khỏi {@code /report} vì hai nhịp khác hẳn nhau: báo cáo thường
     * trễ 24–48 giờ nên tải một lần là đủ, còn số liệu này đáng làm mới mỗi
     * nửa phút. Gộp chung thì mỗi lần muốn xem ai đang online lại kéo theo
     * ba truy vấn báo cáo nặng mà kết quả không hề đổi.
     */
    @GetMapping("/dang-online")
    @PreAuthorize("hasAnyAuthority('USERS_MANAGE','AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<DangOnlineResponse>> dangOnline() {
        return ResponseEntity.ok(ApiResponse.success(ga4AnalyticsService.dangOnline()));
    }
}
