package com.exe.astratarot.domain.dto.report;

import jakarta.validation.constraints.Min;
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

    /**
     * Tiền phạt trừ của người bị báo cáo, đơn vị đồng.
     *
     * <p>Để trống hoặc 0 là nhắc nhở suông — vẫn là một kết luận hợp lệ. Phạt
     * tiền phải do người xử lý chủ động nhập, không bao giờ tự sinh ra: mặc
     * định im lặng trừ tiền của người khác là thứ không được phép tồn tại.
     *
     * <p>Chỉ có tác dụng khi kết luận là RESOLVED (xác nhận có vi phạm).
     */
    @Min(value = 0, message = "Tiền phạt không được âm")
    private Long penaltyAmount;
}
