package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.notification.NotificationResponse;
import com.exe.astratarot.domain.entity.Notification;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.event.NotificationPushedEvent;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.NotificationRepository;
import com.exe.astratarot.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void push(User recipient, String type, String title, String message, Map<String, ?> metadata) {
        if (recipient == null) {
            return;
        }
        try {
            Notification saved = notificationRepository.save(Notification.builder()
                    .user(recipient)
                    .type(type)
                    .title(title)
                    .message(message)
                    .metadata(metadata == null || metadata.isEmpty()
                            ? null
                            : objectMapper.writeValueAsString(metadata))
                    .build());
            long unread = notificationRepository.countByUserIdAndReadFalse(recipient.getId());
            eventPublisher.publishEvent(new NotificationPushedEvent(
                    recipient.getId(),
                    type,
                    unread,
                    toResponse(saved)));
        } catch (Exception e) {
            log.error("Không tạo được thông báo [{}] cho {}", type, recipient.getId(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(UUID userId, Pageable pageable) {
        return notificationRepository
                .findByUserIdOrderByPinnedDescCreatedAtDesc(userId, pageable)
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
        Notification n = loadOwned(userId, notificationId);
        n.setRead(true);
    }

    @Override
    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Override
    @Transactional
    public void setPinned(UUID userId, UUID notificationId, boolean pinned) {
        Notification n = loadOwned(userId, notificationId);
        n.setPinned(pinned);
    }

    @Override
    @Transactional
    public int deleteAllRead(UUID userId) {
        int soDong = notificationRepository.deleteAllRead(userId);
        log.info("Đã xoá {} thông báo đã đọc của {}", soDong, userId);
        return soDong;
    }

    @Override
    @Transactional
    public int deleteByIds(UUID userId, Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int soDong = notificationRepository.deleteByIds(userId, ids);
        log.info("Đã xoá {} thông báo đã chọn của {}", soDong, userId);
        return soDong;
    }

    private Notification loadOwned(UUID userId, UUID notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo"));
        if (!n.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Thông báo này không thuộc về bạn");
        }
        return n;
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .read(n.getRead())
                .pinned(Boolean.TRUE.equals(n.getPinned()))
                .metadata(n.getMetadata())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
