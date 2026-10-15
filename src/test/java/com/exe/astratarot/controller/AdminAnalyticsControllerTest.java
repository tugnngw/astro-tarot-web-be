package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.analytics.GA4OverviewDto;
import com.exe.astratarot.domain.dto.analytics.GA4ReportResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.service.analytics.GA4AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsControllerTest {

    @Mock
    private GA4AnalyticsService ga4AnalyticsService;

    private AdminAnalyticsController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAnalyticsController(ga4AnalyticsService);
    }

    @Test
    @DisplayName("getReport: trả về báo cáo GA4 thành công cho admin")
    void getReport_success() {
        GA4OverviewDto overview = new GA4OverviewDto(150L, 120L, 200L, 500L, 0.32, 145.5);
        GA4ReportResponse mockReport = new GA4ReportResponse(
                true,
                "559041870",
                "30daysAgo",
                "today",
                overview,
                List.of(),
                List.of(),
                "SUCCESS",
                "Tải báo cáo GA4 thành công"
        );

        when(ga4AnalyticsService.getReport("30daysAgo", "today")).thenReturn(mockReport);

        ResponseEntity<ApiResponse<GA4ReportResponse>> res = controller.getReport("30daysAgo", "today");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertTrue(res.getBody().isSuccess());
        assertEquals("559041870", res.getBody().getData().propertyId());
        assertEquals(150L, res.getBody().getData().overview().activeUsers());
        verify(ga4AnalyticsService).getReport("30daysAgo", "today");
    }

    @Test
    @DisplayName("getReport: xử lý trạng thái chưa cấu hình credentials")
    void getReport_unconfigured() {
        GA4ReportResponse unconfiguredReport = GA4ReportResponse.unconfigured("559041870", "7daysAgo", "today");
        when(ga4AnalyticsService.getReport("7daysAgo", "today")).thenReturn(unconfiguredReport);

        ResponseEntity<ApiResponse<GA4ReportResponse>> res = controller.getReport("7daysAgo", "today");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertFalse(res.getBody().getData().configured());
        assertEquals("NOT_CONFIGURED", res.getBody().getData().status());
    }
}
