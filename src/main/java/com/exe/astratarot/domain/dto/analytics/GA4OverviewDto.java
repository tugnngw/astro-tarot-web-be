package com.exe.astratarot.domain.dto.analytics;

/**
 * Chỉ số lưu lượng tổng quan từ GA4.
 */
public record GA4OverviewDto(
        long activeUsers,
        long newUsers,
        long sessions,
        long screenPageViews,
        double bounceRate,
        double averageSessionDuration
) {
    public static GA4OverviewDto empty() {
        return new GA4OverviewDto(0L, 0L, 0L, 0L, 0.0, 0.0);
    }
}
