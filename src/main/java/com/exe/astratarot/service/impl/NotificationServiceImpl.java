package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.notification.NotificationResponse;
import com.exe.astratarot.domain.entity.Notification;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.NotificationRepository;
import com.exe.astratarot.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void push(User recipient, String type, String title, String message, Map<String, ?> metadata) {
        if (recipient == null) {
            return;
        }
        try {
            notificationRepository.save(Notification.builder()
                    .user(recipient)
                    .type(type)
                    .title(title)
                    .message(message)
                    .metadata(metadata == null || metadata.isEmpty()
                            ? null
                            : objectMapper.writeValueAsString(metadata))
                    .build());
        } catch (Exception e) {
            // Gửi thông báo hỏng KHÔNG được làm hỏng việc chính. Đặt lịch xong
            // rồi rollback chỉ vì không ghi được thông báo là làm mất đúng thứ
            // người dùng vừa yêu cầu.
            log.error("Không tạo được thông báo [{}] cho {}", type, recipient.getId(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(UUID userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo"));
        // Thông báo của người khác thì ngay cả việc biết nó tồn tại cũng là rò
        // rỉ, nên chặn ở đây chứ không trông vào việc đoán id là khó.
        if (!n.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Thông báo này không thuộc về bạn");
        }
        n.setRead(true);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Override
    @Transactional
    public int deleteAllRead(UUID userId) {
        int soDong = notificationRepository.deleteAllRead(userId);
        log.info("Đã xoá {} thông báo đã đọc của {}", soDong, userId);
        return soDong;
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .read(n.getRead())
                .metadata(n.getMetadata())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
