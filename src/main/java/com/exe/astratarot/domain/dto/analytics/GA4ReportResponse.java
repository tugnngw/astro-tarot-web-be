package com.exe.astratarot.domain.dto.analytics;

import java.util.List;

/**
 * Phản hồi báo cáo Google Analytics 4 cho trang quản trị.
 */
public record GA4ReportResponse(
        boolean configured,
        String propertyId,
        String startDate,
        String endDate,
        GA4OverviewDto overview,
        List<GA4TrafficSourceDto> trafficSources,
        List<GA4TimeSeriesDto> timeSeries,
        String status,
        String message
) {
    public static GA4ReportResponse unconfigured(String propertyId, String startDate, String endDate) {
        return new GA4ReportResponse(
                false,
                propertyId,
                startDate,
                endDate,
                GA4OverviewDto.empty(),
                List.of(),
                List.of(),
                "NOT_CONFIGURED",
                "Google Analytics 4 chưa được cấu hình credentials trên máy chủ. Đặt GA4_CREDENTIALS_PATH hoặc GA4_CREDENTIALS_JSON để kích hoạt."
        );
    }

    public static GA4ReportResponse error(String propertyId, String startDate, String endDate, String message) {
        return new GA4ReportResponse(
                true,
                propertyId,
                startDate,
                endDate,
                GA4OverviewDto.empty(),
                List.of(),
                List.of(),
                "ERROR",
                message
        );
    }
}
