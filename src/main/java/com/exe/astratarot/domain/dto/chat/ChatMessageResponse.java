package com.exe.astratarot.domain.dto.chat;

import com.exe.astratarot.domain.enums.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a single chat message in the message history.
 * Contains sender info, content, and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {

    private UUID id;
    private SenderType senderType;
    private String content;
    private Integer tokenCount;
    private String modelInfo;
    private Instant createdAt;
}