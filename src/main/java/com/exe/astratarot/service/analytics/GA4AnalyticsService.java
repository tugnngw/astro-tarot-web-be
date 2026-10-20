package com.exe.astratarot.service.analytics;

import com.exe.astratarot.domain.dto.analytics.DangOnlineResponse;
import com.exe.astratarot.domain.dto.analytics.GA4ReportResponse;

/**
 * Dịch vụ báo cáo số liệu truy cập Google Analytics 4.
 */
public interface GA4AnalyticsService {
    /**
     * Lấy báo cáo tổng hợp GA4 theo khoảng ngày.
     *
     * @param startDate Ngày bắt đầu (ví dụ: "30daysAgo", "2026-09-01")
     * @param endDate   Ngày kết thúc (ví dụ: "today", "2026-10-15")
     * @return Báo cáo GA4 gồm overview, traffic sources, time series
     */
    GA4ReportResponse getReport(String startDate, String endDate);

    /**
     * Ai đang ở trên web NGAY BÂY GIỜ, ở đâu, đang xem gì.
     *
     * <p>Dùng {@code runRealtimeReport} nên trả về trong vài giây, khác hẳn
     * báo cáo thường vốn trễ 24–48 giờ.
     */
    DangOnlineResponse dangOnline();
}
