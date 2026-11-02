package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.astrology.NguoiThuHaiDTO;
import com.exe.astratarot.domain.dto.chat.ChatHistoryResponse;
import com.exe.astratarot.domain.dto.chat.ChatResponse;
import com.exe.astratarot.domain.entity.User;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Service responsible for AI reading chat continuation.
 *
 * <p>Manages follow-up conversations for existing AI Tarot readings.
 * Uses existing chat_sessions and chat_messages tables.
 * No database schema changes required.
 */
public interface ChatService {

    /**
     * Sends a follow-up message in an existing AI reading session (blocking).
     *
     * @param readingId the ID of the TarotReading to continue
     * @param user the authenticated user (ownership verified)
     * @param message the follow-up question
     * @return ChatResponse with the AI reply and token usage
     */
    default ChatResponse sendMessage(UUID readingId, User user, String message) {
        return sendMessage(readingId, user, message, null);
    }

    /**
     * Như trên, nhưng cho lượt xem đôi.
     *
     * <p>Người thứ hai đi kèm TỪNG tin nhắn chứ không lưu ở đâu cả. Họ không
     * có tài khoản nên không có hồ sơ để tra, và nếu nhớ hộ theo userId thì
     * người của phiên trước sẽ còn nguyên trong phiên sau.
     *
     * @param nguoiThuHai người thứ hai, null ở lượt xem một người
     */
    ChatResponse sendMessage(UUID readingId, User user, String message, NguoiThuHaiDTO nguoiThuHai);

    /**
     * Sends a follow-up message via streaming.
     *
     * <p>Text fragments are delivered to {@code onChunk} as they arrive.
     * After the final chunk, the AI message is persisted and
     * {@code onComplete} receives session/message metadata.
     * On failure, {@code onError} is called and no AI message is saved.
     *
     * @param readingId the TarotReading to continue
     * @param user the authenticated user
     * @param message the follow-up question
     * @param onChunk  consumer for each incremental text fragment
     * @param onError  consumer for the terminal error, if any
     * @param onComplete consumer for session/message metadata after save
     */
    default void sendMessageStream(UUID readingId,
                                   User user,
                                   String message,
                                   Consumer<String> onChunk,
                                   Consumer<Throwable> onError,
                                   Consumer<StreamResult> onComplete) {
        sendMessageStream(readingId, user, message, null, onChunk, onError, onComplete);
    }

    /**
     * Bản streaming cho lượt xem đôi. Xem {@link #sendMessage(UUID, User, String, NguoiThuHaiDTO)}.
     */
    void sendMessageStream(UUID readingId,
                           User user,
                           String message,
                           NguoiThuHaiDTO nguoiThuHai,
                           Consumer<String> onChunk,
                           Consumer<Throwable> onError,
                           Consumer<StreamResult> onComplete);

    /**
     * Retrieves paginated message history for an AI reading session.
     *
     * @param readingId the ID of the TarotReading
     * @param user the authenticated user (ownership verified)
     * @param page page number (zero-based)
     * @param size page size
     * @return ChatHistoryResponse with messages and pagination metadata
     */
    ChatHistoryResponse getMessages(UUID readingId, User user, int page, int size);

    /**
     * Metadata returned after a streaming response is persisted.
     */
    record StreamResult(UUID sessionId, UUID messageId, String modelUsed,
                        Integer totalTokens, Integer promptTokens, Integer completionTokens) {}
}