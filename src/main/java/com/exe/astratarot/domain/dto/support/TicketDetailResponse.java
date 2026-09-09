package com.exe.astratarot.domain.dto.support;

import com.exe.astratarot.domain.enums.TicketStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chi tiết một ticket kèm toàn bộ luồng trao đổi. */
public record TicketDetailResponse(
        UUID id,
        String subject,
        TicketStatus status,
        UUID requesterId,
        String requesterName,
        String assignedToName,
        Instant createdAt,
        Instant updatedAt,
        List<TicketMessageResponse> messages
) {}
