package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.chat.ChatHistoryResponse;
import com.exe.astratarot.domain.dto.chat.ChatResponse;
import com.exe.astratarot.domain.entity.User;

import java.util.UUID;

/**
 * Service responsible for AI reading chat continuation.
 *
 * Manages follow-up conversations for existing AI Tarot readings.
 * Uses existing chat_sessions and chat_messages tables.
 * No database schema changes required.
 */
public interface ChatService {

    /**
     * Sends a follow-up message in an existing AI reading session.
     *
     * @param readingId the ID of the TarotReading to continue
     * @param user the authenticated user (ownership verified)
     * @param message the follow-up question
     * @return ChatResponse with the AI reply and token usage
     * @throws IllegalArgumentException if reading not found or not an AI session
     * @throws IllegalStateException if session is closed
     */
    ChatResponse sendMessage(UUID readingId, User user, String message);

    /**
     * Retrieves paginated message history for an AI reading session.
     *
     * @param readingId the ID of the TarotReading
     * @param user the authenticated user (ownership verified)
     * @param page page number (zero-based)
     * @param size page size
     * @return ChatHistoryResponse with messages and pagination metadata
     * @throws IllegalArgumentException if reading not found or not an AI session
     */
    ChatHistoryResponse getMessages(UUID readingId, User user, int page, int size);
}