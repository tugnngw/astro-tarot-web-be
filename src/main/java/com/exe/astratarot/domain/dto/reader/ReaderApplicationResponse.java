package com.exe.astratarot.domain.dto.reader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReaderApplicationResponse {

    private String id;
    private String status;
    private String bio;
    private Integer experience;
    private String[] specialties;
    private Instant createdAt;

    // Kết quả duyệt. Entity đã lưu sẵn hai trường này nhưng DTO không trả ra,
    // nên người bị từ chối không có cách nào biết lý do để sửa mà nộp lại.
    private String rejectionReason;
    private Instant reviewedAt;
}
