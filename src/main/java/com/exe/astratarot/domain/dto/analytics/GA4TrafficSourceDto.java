package com.exe.astratarot.domain.dto.analytics;

/**
 * Nguồn lưu lượng truy cập từ GA4.
 */
public record GA4TrafficSourceDto(
        String source,
        String medium,
        long sessions,
        long activeUsers
) {}
