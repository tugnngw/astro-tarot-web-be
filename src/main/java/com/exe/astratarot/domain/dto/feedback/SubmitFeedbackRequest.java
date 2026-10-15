package com.exe.astratarot.domain.dto.feedback;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu gửi/sửa phản hồi hệ thống.
 *
 * <p>Số sao (rating) bắt buộc từ 1 đến 5.
 * Điểm NPS là tuỳ chọn: nếu client không truyền, hệ thống tự suy ra {@code nps = rating * 2}
 * (thang 1–5 ánh xạ sang thang 2–10) để bảo đảm ràng buộc CSDL và tương thích với bản ghi cũ.
 */
public record SubmitFeedbackRequest(
        @NotBlank @Size(max = 40) String source,
        @NotNull @Min(1) @Max(5) Integer rating,
        @Min(0) @Max(10) Integer nps,
        @Size(max = 2000) String comment,
        @Size(max = 100) String utmSource,
        @Size(max = 100) String utmMedium,
        @Size(max = 100) String utmCampaign
) {}
