package com.exe.astratarot.domain.dto.realtime;

import com.exe.astratarot.domain.dto.notification.NotificationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envelope đẩy qua STOMP {@code /user/queue/events}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeEventPayload {
    /** Hiện tại chỉ NOTIFICATION; giữ field để mở rộng sau. */
    private String kind;
    /** NotificationTypes — FE map sang invalidate query. */
    private String type;
    private long unreadCount;
    private NotificationResponse notification;
}
