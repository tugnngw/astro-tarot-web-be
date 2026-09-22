package com.exe.astratarot.domain.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Một tin nhắn trong buổi tư vấn, đã đủ thông tin để vẽ lên màn hình. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingMessageResponse {
    private UUID id;
    private UUID bookingId;
    private UUID senderId;
    private String senderName;
    private String body;
    private Instant readAt;
    private Instant createdAt;
}
