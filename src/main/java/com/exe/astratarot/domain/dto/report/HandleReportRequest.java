package com.exe.astratarot.domain.dto.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HandleReportRequest {

    /** REVIEWED, RESOLVED hoặc REJECTED. */
    @NotBlank(message = "Phải chọn kết luận")
    private String status;

    /** Người tố cáo đọc được, nên viết cho họ hiểu. */
    @Size(max = 2000, message = "Kết luận tối đa 2000 ký tự")
    private String resolutionNote;
}
