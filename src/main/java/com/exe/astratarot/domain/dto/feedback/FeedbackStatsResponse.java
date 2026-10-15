package com.exe.astratarot.domain.dto.feedback;

import java.util.Map;

/**
 * Thống kê tổng hợp phản hồi hệ thống cho Quản trị viên.
 *
 * @param totalCount          tổng số lượt góp ý (gồm cả thành viên và khách ẩn danh)
 * @param averageRating       điểm số sao trung bình (1.00 - 5.00), làm tròn 2 chữ số thập phân
 * @param ratingDistribution  phân bổ số lượt đánh giá theo từng mức sao (1, 2, 3, 4, 5) — bảo đảm đủ 5 mức
 * @param averageNps          điểm NPS trung bình (0.00 - 10.00)
 * @param bySource            số lượt phản hồi theo nguồn gửi (TAROT_AI, BOOKING, GENERAL, LANDING)
 */
public record FeedbackStatsResponse(
        long totalCount,
        double averageRating,
        Map<Integer, Long> ratingDistribution,
        Double averageNps,
        Map<String, Long> bySource
) {}
