package com.exe.astratarot.service.analytics;

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
}
