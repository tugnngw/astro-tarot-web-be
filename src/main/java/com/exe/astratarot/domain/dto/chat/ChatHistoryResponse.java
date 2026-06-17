package com.exe.astratarot.domain.dto.chat;

import com.exe.astratarot.domain.enums.ChatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for chat message history.
 * Contains the session status and a paginated list of messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatHistoryResponse {

    private UUID sessionId;
    private UUID readingId;

    @Builder.Default
    private ChatStatus status = ChatStatus.ACTIVE;

    private List<ChatMessageResponse> messages;
    private int totalMessages;
    private boolean hasMore;
    private int totalPages;
    private int currentPage;
}