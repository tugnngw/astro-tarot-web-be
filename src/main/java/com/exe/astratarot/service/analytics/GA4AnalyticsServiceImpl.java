package com.exe.astratarot.service.analytics;

import com.exe.astratarot.domain.dto.analytics.GA4OverviewDto;
import com.exe.astratarot.domain.dto.analytics.GA4ReportResponse;
import com.exe.astratarot.domain.dto.analytics.GA4TimeSeriesDto;
import com.exe.astratarot.domain.dto.analytics.GA4TrafficSourceDto;
import com.google.analytics.data.v1beta.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Triển khai truy vấn số liệu từ Google Analytics 4 qua BetaAnalyticsDataClient.
 * Xử lý lỗi an toàn, không để lộ đường dẫn file hay API credentials ra ngoài.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GA4AnalyticsServiceImpl implements GA4AnalyticsService {

    private static final Pattern VALID_DATE_PATTERN = Pattern.compile("^(today|yesterday|\\d+daysAgo|\\d{4}-\\d{2}-\\d{2})$");

    private final Ga4ClientProvider clientProvider;

    @Override
    public GA4ReportResponse getReport(String startDate, String endDate) {
        String cleanStart = sanitizeDate(startDate, "30daysAgo");
        String cleanEnd = sanitizeDate(endDate, "today");

        if (!clientProvider.isConfigured()) {
            return GA4ReportResponse.unconfigured(clientProvider.getPropertyId(), cleanStart, cleanEnd);
        }

        BetaAnalyticsDataClient client = clientProvider.getClient();
        if (client == null) {
            return GA4ReportResponse.unconfigured(clientProvider.getPropertyId(), cleanStart, cleanEnd);
        }

        String property = "properties/" + clientProvider.getPropertyId();
        DateRange dateRange = DateRange.newBuilder()
                .setStartDate(cleanStart)
                .setEndDate(cleanEnd)
                .build();

        try {
            // 1. Overview metrics
            RunReportRequest overviewRequest = RunReportRequest.newBuilder()
                    .setProperty(property)
                    .addDateRanges(dateRange)
                    .addMetrics(Metric.newBuilder().setName("activeUsers"))
                    .addMetrics(Metric.newBuilder().setName("newUsers"))
                    .addMetrics(Metric.newBuilder().setName("sessions"))
                    .addMetrics(Metric.newBuilder().setName("screenPageViews"))
                    .addMetrics(Metric.newBuilder().setName("bounceRate"))
                    .addMetrics(Metric.newBuilder().setName("averageSessionDuration"))
                    .build();

            RunReportResponse overviewResponse = client.runReport(overviewRequest);
            GA4OverviewDto overview = parseOverview(overviewResponse);

            // 2. Time series (by date)
            RunReportRequest timeSeriesRequest = RunReportRequest.newBuilder()
                    .setProperty(property)
                    .addDateRanges(dateRange)
                    .addDimensions(Dimension.newBuilder().setName("date"))
                    .addMetrics(Metric.newBuilder().setName("activeUsers"))
                    .addMetrics(Metric.newBuilder().setName("sessions"))
                    .addMetrics(Metric.newBuilder().setName("screenPageViews"))
                    .addOrderBys(OrderBy.newBuilder()
                            .setDimension(OrderBy.DimensionOrderBy.newBuilder().setDimensionName("date").build())
                            .setDesc(false)
                            .build())
                    .build();

            RunReportResponse timeSeriesResponse = client.runReport(timeSeriesRequest);
            List<GA4TimeSeriesDto> timeSeries = parseTimeSeries(timeSeriesResponse);

            // 3. Traffic sources
            RunReportRequest trafficRequest = RunReportRequest.newBuilder()
                    .setProperty(property)
                    .addDateRanges(dateRange)
                    .addDimensions(Dimension.newBuilder().setName("sessionSource"))
                    .addDimensions(Dimension.newBuilder().setName("sessionMedium"))
                    .addMetrics(Metric.newBuilder().setName("sessions"))
                    .addMetrics(Metric.newBuilder().setName("activeUsers"))
                    .addOrderBys(OrderBy.newBuilder()
                            .setMetric(OrderBy.MetricOrderBy.newBuilder().setMetricName("sessions").build())
                            .setDesc(true)
                            .build())
                    .setLimit(10)
                    .build();

            RunReportResponse trafficResponse = client.runReport(trafficRequest);
            List<GA4TrafficSourceDto> trafficSources = parseTrafficSources(trafficResponse);

            return new GA4ReportResponse(
                    true,
                    clientProvider.getPropertyId(),
                    cleanStart,
                    cleanEnd,
                    overview,
                    trafficSources,
                    timeSeries,
                    "SUCCESS",
                    "Tải báo cáo GA4 thành công"
            );
        } catch (Exception e) {
            log.error("Lỗi khi truy vấn Google Analytics Data API cho property {}: {}",
                    clientProvider.getPropertyId(), e.getMessage());
            return GA4ReportResponse.error(
                    clientProvider.getPropertyId(),
                    cleanStart,
                    cleanEnd,
                    "Không thể truy vấn Google Analytics Data API: " + sanitizeError(e.getMessage())
            );
        }
    }

    private String sanitizeDate(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String trimmed = input.trim();
        if (!VALID_DATE_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Tham số ngày không hợp lệ: '" + trimmed
                    + "'. Yêu cầu định dạng YYYY-MM-DD hoặc [N]daysAgo/today/yesterday");
        }
        return trimmed;
    }

    private GA4OverviewDto parseOverview(RunReportResponse response) {
        if (response.getRowsCount() == 0) {
            return GA4OverviewDto.empty();
        }
        Row row = response.getRows(0);
        long activeUsers = parseLongSafe(row.getMetricValues(0).getValue());
        long newUsers = parseLongSafe(row.getMetricValues(1).getValue());
        long sessions = parseLongSafe(row.getMetricValues(2).getValue());
        long screenPageViews = parseLongSafe(row.getMetricValues(3).getValue());
        double bounceRate = parseDoubleSafe(row.getMetricValues(4).getValue());
        double avgDuration = parseDoubleSafe(row.getMetricValues(5).getValue());

        return new GA4OverviewDto(activeUsers, newUsers, sessions, screenPageViews, bounceRate, avgDuration);
    }

    private List<GA4TimeSeriesDto> parseTimeSeries(RunReportResponse response) {
        List<GA4TimeSeriesDto> list = new ArrayList<>();
        for (Row row : response.getRowsList()) {
            String rawDate = row.getDimensionValues(0).getValue();
            String formattedDate = formatDateString(rawDate);
            long activeUsers = parseLongSafe(row.getMetricValues(0).getValue());
            long sessions = parseLongSafe(row.getMetricValues(1).getValue());
            long pageViews = parseLongSafe(row.getMetricValues(2).getValue());

            list.add(new GA4TimeSeriesDto(formattedDate, activeUsers, sessions, pageViews));
        }
        return list;
    }

    private List<GA4TrafficSourceDto> parseTrafficSources(RunReportResponse response) {
        List<GA4TrafficSourceDto> list = new ArrayList<>();
        for (Row row : response.getRowsList()) {
            String source = row.getDimensionValues(0).getValue();
            String medium = row.getDimensionValues(1).getValue();
            long sessions = parseLongSafe(row.getMetricValues(0).getValue());
            long activeUsers = parseLongSafe(row.getMetricValues(1).getValue());

            list.add(new GA4TrafficSourceDto(source, medium, sessions, activeUsers));
        }
        return list;
    }

    private String formatDateString(String rawDate) {
        if (rawDate != null && rawDate.length() == 8 && rawDate.matches("^\\d{8}$")) {
            return rawDate.substring(0, 4) + "-" + rawDate.substring(4, 6) + "-" + rawDate.substring(6, 8);
        }
        return rawDate != null ? rawDate : "";
    }

    private long parseLongSafe(String val) {
        try {
            return val != null ? Long.parseLong(val) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double parseDoubleSafe(String val) {
        try {
            return val != null ? Double.parseDouble(val) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String sanitizeError(String message) {
        if (message == null) return "Unknown error";
        // Strip sensitive file system paths or key details
        return message.replaceAll("[A-Za-z]:\\\\[^\\s:]+", "[path]")
                .replaceAll("/[^\\s:]+/[^\\s:]+", "[path]");
    }
}
