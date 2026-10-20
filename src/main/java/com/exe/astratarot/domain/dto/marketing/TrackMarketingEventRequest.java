package com.exe.astratarot.domain.dto.marketing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrackMarketingEventRequest(
        @NotBlank @Size(max = 80) String eventName,
        @Size(max = 500) String path,
        @Size(max = 100) String utmSource,
        @Size(max = 100) String utmMedium,
        @Size(max = 100) String utmCampaign,
        /**
         * Mã khách sinh ở trình duyệt, để nối các hành động của cùng một người.
         *
         * <p>KHÔNG phải danh tính: không tên, không email; xoá dữ liệu trình
         * duyệt là thành khách mới. Đúng mức cần để đọc hành trình.
         */
        @Size(max = 64) String visitorId,
        String metadata
) {}
