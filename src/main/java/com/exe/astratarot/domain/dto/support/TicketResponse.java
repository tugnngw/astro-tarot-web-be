package com.exe.astratarot.domain.dto.support;

import com.exe.astratarot.domain.enums.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/** Một dòng trong danh sách ticket (hàng chờ nhân viên hoặc danh sách của khách). */
public record TicketResponse(
        UUID id,
        String subject,
        TicketStatus status,
        String requesterName,
        String assignedToName,
        int messageCount,
        Instant createdAt,
        Instant updatedAt
) {}
