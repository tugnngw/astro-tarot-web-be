package com.exe.astratarot.domain.dto.support;

import java.time.Instant;
import java.util.UUID;

public record TicketMessageResponse(
        UUID id,
        UUID senderId,
        String senderName,
        // true nếu người gửi là nhân viên hỗ trợ (không phải chủ ticket).
        boolean fromStaff,
        String body,
        Instant createdAt
) {}
