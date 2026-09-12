package com.exe.astratarot.service.realtime;

import com.exe.astratarot.domain.dto.realtime.RealtimeEventPayload;
import com.exe.astratarot.domain.event.NotificationPushedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Đẩy sự kiện realtime sau khi transaction tạo thông báo đã commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRealtimeListener {

    public static final String USER_EVENTS_QUEUE = "/queue/events";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationPushed(NotificationPushedEvent event) {
        try {
            RealtimeEventPayload payload = RealtimeEventPayload.builder()
                    .kind("NOTIFICATION")
                    .type(event.type())
                    .unreadCount(event.unreadCount())
                    .notification(event.notification())
                    .build();
            messagingTemplate.convertAndSendToUser(
                    event.userId().toString(),
                    USER_EVENTS_QUEUE,
                    payload);
        } catch (Exception e) {
            // Realtime hỏng không được làm hỏng việc chính đã commit.
            log.warn("Không đẩy STOMP cho user {}: {}", event.userId(), e.getMessage());
        }
    }
}
