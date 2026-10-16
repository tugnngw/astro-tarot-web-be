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
        /**
         * Số người đang ở trên web trong 30 phút qua.
         *
         * <p>Lấy từ runRealtimeReport, KHÁC hẳn {@code overview.activeUsers}
         * vốn lấy từ báo cáo thường. Báo cáo thường của GA4 trễ 24–48 giờ với
         * tài sản mới, nên mọi ô số trong trang quản trị đứng im ở 0 suốt hai
         * ngày đầu dù web vẫn có khách — nhìn vào tưởng cấu hình hỏng.
         *
         * <p>-1 nghĩa là không hỏi được (chưa cấu hình, hoặc lượt gọi hỏng);
         * 0 là hỏi được và đúng là không có ai.
         */
        long realtimeActiveUsers,
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
                -1L,
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
                -1L,
                "ERROR",
                message
        );
    }
}
