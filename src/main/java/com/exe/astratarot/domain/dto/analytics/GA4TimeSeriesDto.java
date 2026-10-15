package com.exe.astratarot.domain.dto.analytics;

/**
 * Dữ liệu chuỗi thời gian theo ngày từ GA4.
 */
public record GA4TimeSeriesDto(
        String date,
        long activeUsers,
        long sessions,
        long screenPageViews
) {}
