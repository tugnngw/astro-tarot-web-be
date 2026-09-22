package com.exe.astratarot.domain.dto.booking;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Một gói tín hiệu WebRTC chuyển tiếp giữa hai máy.
 *
 * <p>Máy chủ KHÔNG hiểu và KHÔNG lưu nội dung bên trong — nó chỉ kiểm tra
 * người gửi có quyền ở trong buổi này không, rồi chuyển nguyên văn cho phía
 * bên kia. Tiếng và hình đi thẳng giữa hai trình duyệt, không qua Render.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSignalMessage {

    /** OFFER | ANSWER | ICE | HANGUP | BUSY | RINGING */
    @NotBlank
    private String type;

    /** Nội dung thô của WebRTC (SDP hoặc ICE candidate) — máy chủ không đọc. */
    private String payload;

    /** Có video hay chỉ thoại. Dùng để phía kia biết có bật camera không. */
    private boolean video;

    // Máy chủ tự điền, client gửi lên cũng bị ghi đè.
    private UUID bookingId;
    private UUID fromUserId;
    private String fromName;
}
