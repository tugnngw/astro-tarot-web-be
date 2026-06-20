package com.exe.astratarot.domain.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single AI chat continuation message.
 * Contains the session ID, message ID, the AI reply, and token usage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private UUID sessionId;
    private UUID messageId;
    private String reply;
    private String modelUsed;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Instant createdAt;
}