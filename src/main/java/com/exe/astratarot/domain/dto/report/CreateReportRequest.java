package com.exe.astratarot.domain.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateReportRequest {

    @NotNull(message = "Phải chọn người bị báo cáo")
    private UUID reportedUserId;

    @NotBlank(message = "Phải chọn loại vi phạm")
    @Size(max = 30)
    private String reportType;

    @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
    private String description;

    /** Gắn với một buổi xem cụ thể nếu có. Chỉ gắn được lịch hẹn mình liên quan. */
    private UUID bookingId;
}
