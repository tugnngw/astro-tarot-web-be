package com.exe.astratarot.domain.dto.notification;

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
public class NotificationResponse {
    private UUID id;
    private String title;
    private String message;
    /** Quyết định biểu tượng và đường dẫn khi bấm vào. Xem NotificationTypes. */
    private String type;
    private Boolean read;
    /** JSON thô, thường chứa id của đối tượng liên quan để giao diện dựng link. */
    private String metadata;
    private Instant createdAt;
}
