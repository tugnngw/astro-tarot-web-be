package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.chat.ChatHistoryResponse;
import com.exe.astratarot.domain.dto.chat.ChatMessageResponse;
import com.exe.astratarot.domain.dto.chat.ChatResponse;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.domain.entity.ChatMessage;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.ReadingCard;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.ChatStatus;
import com.exe.astratarot.domain.enums.MessageType;
import com.exe.astratarot.domain.enums.SenderType;
import com.exe.astratarot.repository.ChatMessageRepository;
import com.exe.astratarot.repository.ChatSessionRepository;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.AstrologyContextService;
import com.exe.astratarot.service.ChatService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Implementation of ChatService.
 *
 * Manages AI reading continuation using existing chat_sessions and chat_messages tables.
 * Enforces ownership checks, AI session validation, and closed session prevention.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TarotReadingRepository tarotReadingRepository;
    private final ReadingCardRepository readingCardRepository;
    private final AITarotService aiTarotService;
    private final AstrologyContextService astrologyContextService;

    @Override
    @Transactional
    public ChatResponse sendMessage(UUID readingId, User user, String message) {
        log.debug("Continuation request: readingId={}, userId={}", readingId, user.getId());

        // Step 1: Verify TarotReading exists and belongs to user
        TarotReading reading = tarotReadingRepository.findById(readingId)
                .orElseThrow(() -> new EntityNotFoundException("Tarot reading not found with ID: " + readingId));

        if (!reading.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Tarot reading does not belong to user");
        }

        // Step 2: Find or create ChatSession for this reading
        ChatSession session = chatSessionRepository.findByTarotReadingId(readingId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No active chat session found for reading ID: " + readingId));

        // Step 3: Validate session state
        if (session.getStatus() == ChatStatus.CLOSED) {
            throw new IllegalStateException("Chat session is closed for reading ID: " + readingId);
        }

        // Step 4: Save user's follow-up message
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .senderType(SenderType.USER)
                .content(message)
                .messageType(MessageType.TEXT)
                .build();
        chatMessageRepository.save(userMessage);

        // Step 5: Build continuation prompt (with history, cards, astrology) and call AI
        BuildPromptRequest promptRequest = buildContinuationPromptRequest(reading, user, session, message);
        LLMResponse llmResponse = aiTarotService.generateInterpretation(promptRequest);
        log.debug("AI continuation response received for readingId={}", readingId);

        // Step 6: Save AI response
        LLMTokenUsage tokenUsage = llmResponse.getTokenUsage();
        int promptTokens = tokenUsage != null ? tokenUsage.getPromptTokens() : 0;
        int completionTokens = tokenUsage != null ? tokenUsage.getCompletionTokens() : 0;
        int totalTokens = tokenUsage != null ? tokenUsage.getTotalTokens() : 0;

        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .senderType(SenderType.AI)
                .content(llmResponse.getContent())
                .messageType(MessageType.TEXT)
                .build();
        chatMessageRepository.save(aiMessage);

        // Step 7: Update session timestamp
        session.setLastMessageAt(Instant.now());
        chatSessionRepository.save(session);

        // Step 8: Update reading total token count
        int currentTokens = reading.getTotalTokensUsed() != null ? reading.getTotalTokensUsed() : 0;
        reading.setTotalTokensUsed(currentTokens + totalTokens);
        tarotReadingRepository.save(reading);

        log.info("Continuation saved: readingId={}, messageId={}, tokens={}",
                readingId, aiMessage.getId(), totalTokens);

        // Step 9: Build response
        return ChatResponse.builder()
                .sessionId(session.getId())
                .messageId(aiMessage.getId())
                .reply(llmResponse.getContent())
                .modelUsed(llmResponse.getModelInfo())
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .createdAt(aiMessage.getCreatedAt())
                .build();
    }

    @Override
    @Async("sseTaskExecutor")
    public void sendMessageStream(UUID readingId,
                                  User user,
                                  String message,
                                  Consumer<String> onChunk,
                                  Consumer<Throwable> onError,
                                  Consumer<StreamResult> onComplete) {
        try {
            log.debug("Stream request: readingId={}, userId={}", readingId, user.getId());

            // Step 1: Verify ownership
            TarotReading reading = tarotReadingRepository.findById(readingId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Tarot reading not found with ID: " + readingId));

            if (!reading.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Tarot reading does not belong to user");
            }

            // Step 2: Find ChatSession
            ChatSession session = chatSessionRepository.findByTarotReadingId(readingId)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "No active chat session found for reading ID: " + readingId));

            // Step 3: Validate session state
            if (session.getStatus() == ChatStatus.CLOSED) {
                throw new IllegalStateException("Chat session is closed for reading ID: " + readingId);
            }

            // Step 4: Save USER message immediately
            ChatMessage userMessage = ChatMessage.builder()
                    .session(session)
                    .senderType(SenderType.USER)
                    .content(message)
                    .messageType(MessageType.TEXT)
                    .build();
            chatMessageRepository.save(userMessage);

            // Step 5: Build prompt (same as non-streaming)
            BuildPromptRequest promptRequest = buildContinuationPromptRequest(
                    reading, user, session, message);

            // Step 6: Stream from AI
            StringBuilder fullContent = new StringBuilder();
            aiTarotService.generateInterpretationStream(
                    promptRequest,
                    chunk -> {
                        fullContent.append(chunk);
                        onChunk.accept(chunk);
                    },
                    error -> {
                        log.error("Stream error for readingId={}: {}", readingId, error.getMessage());
                        onError.accept(error);
                    },
                    completion -> {
                        // Step 7: Save AI message (only on success) and get its ID
                        UUID messageId = saveAiResponseAndUpdate(reading, session,
                                fullContent.toString(), completion);
                        // Step 8: Notify caller
                        LLMTokenUsage tokenUsage = completion.getTokenUsage();
                        onComplete.accept(new StreamResult(
                                session.getId(),
                                messageId,
                                completion.getModelInfo(),
                                tokenUsage != null ? tokenUsage.getTotalTokens() : 0,
                                tokenUsage != null ? tokenUsage.getPromptTokens() : 0,
                                tokenUsage != null ? tokenUsage.getCompletionTokens() : 0
                        ));
                    }
            );
        } catch (Exception e) {
            log.error("Stream setup failed for readingId={}: {}", readingId, e.getMessage());
            onError.accept(e);
        }
    }

    /**
     * Persists the AI response after a successful stream, then updates session
     * and reading token counts.
     *
     * <p>No {@code @Transactional} here — each {@code save()} is individually
     * auto-flushed by {@code SimpleJpaRepository}.  The caller
     * ({@link #sendMessageStream}) runs without an open transaction because
     * holding one across a Gemini streaming call would exhaust the connection
     * pool under concurrent streams.
     *
     * @return the ID of the persisted AI message
     */
    protected UUID saveAiResponseAndUpdate(TarotReading reading, ChatSession session,
                                           String content, StreamCompletion completion) {
        // Persist AI message
        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .senderType(SenderType.AI)
                .content(content)
                .messageType(MessageType.TEXT)
                .build();
        chatMessageRepository.save(aiMessage);

        // Update session timestamp
        session.setLastMessageAt(Instant.now());
        chatSessionRepository.save(session);

        // Update reading token count
        int newTokens = completion.getTokenUsage() != null
                ? completion.getTokenUsage().getTotalTokens() : 0;
        int currentTokens = reading.getTotalTokensUsed() != null
                ? reading.getTotalTokensUsed() : 0;
        reading.setTotalTokensUsed(currentTokens + newTokens);
        tarotReadingRepository.save(reading);

        log.info("Stream persisted: readingId={}, tokens={}",
                reading.getId(), newTokens);

        return aiMessage.getId();
    }

    @Override
    public ChatHistoryResponse getMessages(UUID readingId, User user, int page, int size) {
        log.debug("History request: readingId={}, userId={}, page={}, size={}",
                readingId, user.getId(), page, size);

        // Step 1: Verify ownership
        TarotReading reading = tarotReadingRepository.findById(readingId)
                .orElseThrow(() -> new EntityNotFoundException("Tarot reading not found with ID: " + readingId));

        if (!reading.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Tarot reading does not belong to user");
        }

        // Step 2: Find session
        ChatSession session = chatSessionRepository.findByTarotReadingId(readingId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No chat session found for reading ID: " + readingId));

        // Step 3: Fetch paginated messages
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<ChatMessage> messagePage = chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(session.getId(), pageable);

        // Step 4: Map to DTOs
        List<ChatMessageResponse> messageResponses = messagePage.getContent().stream()
                .map(this::mapToChatMessageResponse)
                .collect(Collectors.toList());

        // Step 5: Build response
        return ChatHistoryResponse.builder()
                .sessionId(session.getId())
                .readingId(readingId)
                .status(session.getStatus())
                .messages(messageResponses)
                .totalMessages((int) messagePage.getTotalElements())
                .hasMore(messagePage.hasNext())
                .totalPages(messagePage.getTotalPages())
                .currentPage(page)
                .build();
    }

    /**
     * Builds a continuation prompt request using the reading context, conversation history,
     * original question, astrology context, and the follow-up message.
     */
    private BuildPromptRequest buildContinuationPromptRequest(TarotReading reading, User user, ChatSession session, String message) {
        // Reconstruct card details from the reading
        List<ReadingCard> readingCards = readingCardRepository.findByReading(reading);
        List<DrawnCardDetailDTO> cardDetails = new ArrayList<>();
        for (ReadingCard rc : readingCards) {
            com.exe.astratarot.domain.entity.TarotCard card = rc.getCard();
            cardDetails.add(DrawnCardDetailDTO.builder()
                    .cardId(card.getId())
                    .cardName(card.getName())
                    .arcanaType(card.getArcanaType())
                    .cardNumber(card.getCardNumber())
                    .imageUrl(card.getImageUrl())
                    .position(rc.getPosition())
                    .reversed(rc.getReversed())
                    .build());
        }

        // Load conversation history (last 20 messages, newest first, then reverse)
        Pageable historyPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ChatMessage> historyMessages = chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(session.getId(), historyPageable);
        String conversationHistory = formatConversationHistory(historyMessages.getContent());

        // Fetch astrology context (restored from null in Sprint 1)
        Optional<AstrologyContextDTO> astrologyContext =
                astrologyContextService.getAstrologyContext(user.getId());

        return BuildPromptRequest.builder()
                .userQuestion(message)
                .originalQuestion(reading.getMainQuestion())
                .conversationHistory(conversationHistory)
                .astrologyContext(astrologyContext.orElse(null))
                .drawnCardDetails(cardDetails)
                .spreadName("Continuation")
                .build();
    }

    /**
     * Formats a list of chat messages into a conversation history string.
     * Output format:
     *   USER: message
     *   AI: response
     *   USER: message
     *   AI: response
     */
    private String formatConversationHistory(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        // Messages are already ordered ASC by createdAt (from the query)
        for (ChatMessage msg : messages) {
            String senderLabel = msg.getSenderType() == SenderType.USER ? "USER" : "AI";
            sb.append(senderLabel).append(": ").append(msg.getContent()).append("\n\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Maps a ChatMessage entity to a ChatMessageResponse DTO.
     */
    private ChatMessageResponse mapToChatMessageResponse(ChatMessage chatMessage) {
        return ChatMessageResponse.builder()
                .id(chatMessage.getId())
                .senderType(chatMessage.getSenderType())
                .content(chatMessage.getContent())
                .createdAt(chatMessage.getCreatedAt())
                .build();
    }
}