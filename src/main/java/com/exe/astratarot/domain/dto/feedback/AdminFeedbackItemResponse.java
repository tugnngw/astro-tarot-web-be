package com.exe.astratarot.domain.dto.feedback;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng phản hồi trong danh sách duyệt của Quản trị viên.
 *
 * @param id           định danh phản hồi
 * @param userId       định danh người dùng (null nếu gửi ẩn danh)
 * @param username     tên đăng nhập (hoặc null nếu ẩn danh)
 * @param userFullName họ tên hiển thị (hoặc null nếu ẩn danh)
 * @param userEmail    email người dùng (hoặc null nếu ẩn danh)
 * @param isAnonymous  cờ đánh dấu khách ẩn danh
 * @param rating       số sao đánh giá (1–5, có thể null với các bản ghi lịch sử)
 * @param nps          điểm NPS (0–10)
 * @param comment      nội dung góp ý
 * @param source       nguồn góp ý (TAROT_AI, BOOKING, GENERAL, LANDING)
 * @param utmSource    nguồn chiến dịch tiếp thị
 * @param utmMedium    kênh tiếp thị
 * @param utmCampaign  tên chiến dịch tiếp thị
 * @param createdAt    thời điểm gửi ban đầu
 * @param updatedAt    thời điểm cập nhật lần cuối
 */
public record AdminFeedbackItemResponse(
        UUID id,
        UUID userId,
        String username,
        String userFullName,
        String userEmail,
        boolean isAnonymous,
        Integer rating,
        Integer nps,
        String comment,
        String source,
        String utmSource,
        String utmMedium,
        String utmCampaign,
        Instant createdAt,
        Instant updatedAt
) {}
