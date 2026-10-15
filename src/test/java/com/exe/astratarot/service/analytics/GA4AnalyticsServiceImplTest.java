package com.exe.astratarot.service.analytics;

import com.exe.astratarot.domain.dto.analytics.GA4ReportResponse;
import com.google.analytics.data.v1beta.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GA4AnalyticsServiceImplTest {

    @Mock
    private Ga4ClientProvider clientProvider;

    @Mock
    private BetaAnalyticsDataClient mockDataClient;

    private GA4AnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GA4AnalyticsServiceImpl(clientProvider);
    }

    @Test
    @DisplayName("getReport: trả về NOT_CONFIGURED nếu clientProvider chưa cấu hình")
    void getReport_whenNotConfigured() {
        when(clientProvider.isConfigured()).thenReturn(false);
        when(clientProvider.getPropertyId()).thenReturn("559041870");

        GA4ReportResponse response = service.getReport("30daysAgo", "today");

        assertFalse(response.configured());
        assertEquals("559041870", response.propertyId());
        assertEquals("NOT_CONFIGURED", response.status());
        assertEquals(0, response.overview().activeUsers());
        verify(clientProvider, never()).getClient();
    }

    @Test
    @DisplayName("getReport: ném IllegalArgumentException nếu tham số ngày không hợp lệ")
    void getReport_invalidDate() {
        assertThrows(IllegalArgumentException.class, () ->
                service.getReport("malicious;DROP TABLE", "today"));
        assertThrows(IllegalArgumentException.class, () ->
                service.getReport("30daysAgo", "2026/10/15"));
    }

    @Test
    @DisplayName("getReport: ánh xạ thành công overview, timeSeries và trafficSources khi có dữ liệu")
    void getReport_success() {
        when(clientProvider.isConfigured()).thenReturn(true);
        when(clientProvider.getClient()).thenReturn(mockDataClient);
        when(clientProvider.getPropertyId()).thenReturn("559041870");

        // 1. Mock Overview
        RunReportResponse overviewResponse = RunReportResponse.newBuilder()
                .addRows(Row.newBuilder()
                        .addMetricValues(MetricValue.newBuilder().setValue("150"))
                        .addMetricValues(MetricValue.newBuilder().setValue("110"))
                        .addMetricValues(MetricValue.newBuilder().setValue("220"))
                        .addMetricValues(MetricValue.newBuilder().setValue("640"))
                        .addMetricValues(MetricValue.newBuilder().setValue("0.35"))
                        .addMetricValues(MetricValue.newBuilder().setValue("125.4")))
                .build();

        // 2. Mock Time Series
        RunReportResponse timeSeriesResponse = RunReportResponse.newBuilder()
                .addRows(Row.newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("20261014"))
                        .addMetricValues(MetricValue.newBuilder().setValue("45"))
                        .addMetricValues(MetricValue.newBuilder().setValue("60"))
                        .addMetricValues(MetricValue.newBuilder().setValue("180")))
                .build();

        // 3. Mock Traffic Sources
        RunReportResponse trafficResponse = RunReportResponse.newBuilder()
                .addRows(Row.newBuilder()
                        .addDimensionValues(DimensionValue.newBuilder().setValue("google"))
                        .addDimensionValues(DimensionValue.newBuilder().setValue("organic"))
                        .addMetricValues(MetricValue.newBuilder().setValue("140"))
                        .addMetricValues(MetricValue.newBuilder().setValue("95")))
                .build();

        when(mockDataClient.runReport(any(RunReportRequest.class)))
                .thenReturn(overviewResponse)
                .thenReturn(timeSeriesResponse)
                .thenReturn(trafficResponse);

        GA4ReportResponse response = service.getReport("30daysAgo", "today");

        assertTrue(response.configured());
        assertEquals("SUCCESS", response.status());
        assertEquals(150L, response.overview().activeUsers());
        assertEquals(110L, response.overview().newUsers());
        assertEquals(220L, response.overview().sessions());
        assertEquals(640L, response.overview().screenPageViews());
        assertEquals(0.35, response.overview().bounceRate(), 0.001);
        assertEquals(125.4, response.overview().averageSessionDuration(), 0.001);

        assertEquals(1, response.timeSeries().size());
        assertEquals("2026-10-14", response.timeSeries().getFirst().date());
        assertEquals(45L, response.timeSeries().getFirst().activeUsers());

        assertEquals(1, response.trafficSources().size());
        assertEquals("google", response.trafficSources().getFirst().source());
        assertEquals("organic", response.trafficSources().getFirst().medium());
        assertEquals(140L, response.trafficSources().getFirst().sessions());
    }

    @Test
    @DisplayName("getReport: bắt ngoại lệ Google API an toàn và trả về trạng thái ERROR mà không làm sập ứng dụng")
    void getReport_handlesApiExceptionGracefully() {
        when(clientProvider.isConfigured()).thenReturn(true);
        when(clientProvider.getClient()).thenReturn(mockDataClient);
        when(clientProvider.getPropertyId()).thenReturn("559041870");

        when(mockDataClient.runReport(any(RunReportRequest.class)))
                .thenThrow(new RuntimeException("Google Cloud Permission Denied for C:\\secrets\\sa-key.json"));

        GA4ReportResponse response = service.getReport("7daysAgo", "today");

        assertTrue(response.configured());
        assertEquals("ERROR", response.status());
        assertTrue(response.message().contains("Không thể truy vấn Google Analytics Data API"));
        assertFalse(response.message().contains("C:\\secrets"), "Không được để lộ đường dẫn tệp nhạy cảm");
    }
}
