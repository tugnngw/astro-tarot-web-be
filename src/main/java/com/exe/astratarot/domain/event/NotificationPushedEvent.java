package com.exe.astratarot.domain.event;

import com.exe.astratarot.domain.dto.notification.NotificationResponse;

import java.util.UUID;

/**
 * Phát sau khi thông báo đã lưu DB. Listener AFTER_COMMIT mới đẩy STOMP —
 * tránh gửi realtime rồi transaction rollback.
 */
public record NotificationPushedEvent(
        UUID userId,
        String type,
        long unreadCount,
        NotificationResponse notification
) {}
