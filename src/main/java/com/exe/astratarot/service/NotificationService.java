package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.notification.NotificationResponse;
import com.exe.astratarot.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    /**
     * Tạo một thông báo. Không bao giờ ném lỗi ra ngoài: gửi thông báo hỏng thì
     * không được kéo theo việc chính (đặt lịch, duyệt hồ sơ) bị rollback.
     */
    void push(User recipient, String type, String title, String message, Map<String, ?> metadata);

    Page<NotificationResponse> list(UUID userId, Pageable pageable);

    long unreadCount(UUID userId);

    void markRead(UUID userId, UUID notificationId);

    int markAllRead(UUID userId);
}
