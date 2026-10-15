package com.exe.astratarot.service.analytics;

import com.google.analytics.data.v1beta.BetaAnalyticsDataClient;

/**
 * Cung cấp client GA4 đã xác thực và cờ trạng thái cấu hình.
 */
public interface Ga4ClientProvider {
    BetaAnalyticsDataClient getClient();
    boolean isConfigured();
    String getPropertyId();
}
