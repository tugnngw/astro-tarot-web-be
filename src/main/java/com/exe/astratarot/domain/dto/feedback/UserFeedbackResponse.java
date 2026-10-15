package com.exe.astratarot.domain.dto.feedback;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin phản hồi trả về cho người dùng (xem hoặc sau khi gửi/sửa).
 */
public record UserFeedbackResponse(
        UUID id,
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
