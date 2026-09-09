package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.admin.ActivityLogResponse;
import com.exe.astratarot.domain.entity.ActivityLog;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.repository.ActivityLogRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.ActivityLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void record(UUID actorId, String action, String entityType, UUID entityId, Map<String, ?> changes) {
        try {
            User actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
            activityLogRepository.save(ActivityLog.builder()
                    .user(actor)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .changes(changes == null || changes.isEmpty() ? null : objectMapper.writeValueAsString(changes))
                    .build());
        } catch (Exception e) {
            // Ghi nhật ký hỏng thì KHÔNG được kéo theo thao tác chính. Đổi vai
            // trò thành công rồi mà rollback chỉ vì không ghi được log là làm
            // hỏng đúng thứ người dùng vừa yêu cầu.
            log.error("Không ghi được nhật ký [{} {} {}]", action, entityType, entityId, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActivityLogResponse> list(String action, String entityType, Pageable pageable) {
        return activityLogRepository
                .search(normalize(action), normalize(entityType), pageable)
                .map(this::toResponse);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private ActivityLogResponse toResponse(ActivityLog l) {
        User actor = l.getUser();
        return ActivityLogResponse.builder()
                .id(l.getId())
                .actorId(actor == null ? null : actor.getId())
                .actorName(actor == null ? "Hệ thống" : actor.getFullName())
                .actorRole(actor == null ? null : actor.getRole().name())
                .action(l.getAction())
                .entityType(l.getEntityType())
                .entityId(l.getEntityId())
                .changes(l.getChanges())
                .createdAt(l.getCreatedAt())
                .build();
    }
}
