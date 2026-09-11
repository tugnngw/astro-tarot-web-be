package com.exe.astratarot.domain.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private UUID id;
    /** Chỉ người xử lý thấy tên này. Không bao giờ gửi cho người bị báo cáo. */
    private String reporterName;
    private UUID reportedUserId;
    private String reportedName;
    private String reportedRole;
    private UUID bookingId;
    private String reportType;
    private String description;
    private String status;
    private String handledByName;
    private Instant handledAt;
    private String resolutionNote;

    /** Tiền đã trừ của người bị báo cáo. 0 = chỉ nhắc nhở. */
    private Long penaltyAmount;
    private Instant createdAt;
}
