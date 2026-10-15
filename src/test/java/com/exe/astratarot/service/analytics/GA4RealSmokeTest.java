package com.exe.astratarot.service.analytics;

import com.exe.astratarot.config.Ga4Properties;
import com.exe.astratarot.domain.dto.analytics.GA4ReportResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class GA4RealSmokeTest {

    @Test
    @DisplayName("Real GA4 Smoke Test: nạp Service Account key thật và truy vấn API Google Analytics 4")
    void smokeTestRealGa4() {
        String propId = System.getProperty("GA4_PROPERTY_ID", System.getenv().getOrDefault("GA4_PROPERTY_ID", "559041870"));
        String credPath = System.getProperty("GA4_CREDENTIALS_PATH",
                System.getenv().getOrDefault("GA4_CREDENTIALS_PATH", ""));

        org.junit.jupiter.api.Assumptions.assumeTrue(credPath != null && !credPath.isBlank(),
                "Bỏ qua nếu chưa cấu hình biến môi trường GA4_CREDENTIALS_PATH");

        File credFile = new File(credPath);
        org.junit.jupiter.api.Assumptions.assumeTrue(credFile.exists() && credFile.canRead(),
                "Bỏ qua nếu không tìm thấy tệp credentials Service Account");

        Ga4Properties props = new Ga4Properties();
        props.setPropertyId(propId);
        props.setCredentialsPath(credPath);

        DefaultGa4ClientProvider provider = new DefaultGa4ClientProvider(props);
        provider.init();

        try {
            assertTrue(provider.isConfigured(), "Provider phải ở trạng thái configured=true khi nạp key thật");
            assertNotNull(provider.getClient(), "BetaAnalyticsDataClient không được null");

            GA4AnalyticsServiceImpl service = new GA4AnalyticsServiceImpl(provider);
            GA4ReportResponse response = service.getReport("7daysAgo", "today");

            assertNotNull(response, "Phản hồi báo cáo không được null");
            assertTrue(response.configured(), "Báo cáo phải xác nhận configured=true");
            assertEquals("SUCCESS", response.status(), "Trạng thái truy vấn GA4 phải là SUCCESS (Message: " + response.message() + ")");

            System.out.println("=== GA4 SMOKE TEST RESULT ===");
            System.out.println("Configured: " + response.configured());
            System.out.println("Status: " + response.status());
            System.out.println("Property ID: " + response.propertyId());
            System.out.println("Date Range: " + response.startDate() + " -> " + response.endDate());
            System.out.println("Active Users: " + response.overview().activeUsers());
            System.out.println("Sessions: " + response.overview().sessions());
            System.out.println("Screen Page Views: " + response.overview().screenPageViews());
            System.out.println("Time Series rows: " + response.timeSeries().size());
            System.out.println("Traffic Sources rows: " + response.trafficSources().size());
            System.out.println("=============================");

        } finally {
            provider.close();
        }
    }
}
