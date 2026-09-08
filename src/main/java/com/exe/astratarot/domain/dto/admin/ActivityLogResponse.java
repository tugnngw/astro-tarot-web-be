package com.exe.astratarot.domain.dto.admin;

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
public class ActivityLogResponse {
    private UUID id;
    /** Người thực hiện. Null khi tài khoản đó đã bị xoá. */
    private UUID actorId;
    private String actorName;
    private String actorRole;
    private String action;
    private String entityType;
    private UUID entityId;
    /** JSON thô mô tả thay đổi, giao diện tự quyết cách hiển thị. */
    private String changes;
    private Instant createdAt;
}
