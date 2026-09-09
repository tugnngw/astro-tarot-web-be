package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.domain.dto.reader.CardDrawDTO;
import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.dto.reading.TarotReadingResultDTO;
import com.exe.astratarot.domain.entity.ChatMessage;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.ReadingCard;
import com.exe.astratarot.domain.entity.TarotCard;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.ChatStatus;
import com.exe.astratarot.domain.enums.MessageType;
import com.exe.astratarot.domain.enums.SenderType;
import com.exe.astratarot.domain.enums.SessionType;
import com.exe.astratarot.repository.ChatMessageRepository;
import com.exe.astratarot.repository.ChatSessionRepository;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.AIUsageTrackingService;
import com.exe.astratarot.service.AstrologyContextService;
import com.exe.astratarot.service.TarotDrawingService;
import com.exe.astratarot.service.TarotReadingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Implementation of TarotReadingService.
 *
 * Orchestrates the complete AI Tarot reading flow:
 * 1. Fetch user and draw cards (no transaction)
 * 2. Enrich card data (CardDrawDTO → DrawnCardDetailDTO)
 * 3. Call Gemini via AITarotService (no transaction)
 * 4. Persist TarotReading and ReadingCard entities (inside @Transactional)
 * 5. Create ChatSession and initial messages (inside @Transactional)
 * 6. Return TarotReadingResultDTO
 *
 * The Gemini API call is intentionally kept OUTSIDE the database transaction
 * to prevent long-running transactions and handle API failures gracefully.
 * The reading is only persisted after Gemini succeeds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TarotReadingServiceImpl implements TarotReadingService {

    private final UserRepository userRepository;
    private final TarotCardRepository tarotCardRepository;
    private final TarotReadingRepository tarotReadingRepository;
    private final ReadingCardRepository readingCardRepository;
    private final TarotDrawingService tarotDrawingService;
    private final AITarotService aiTarotService;
    private final AstrologyContextService astrologyContextService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AIUsageTrackingService aiUsageTrackingService;

    @org.springframework.beans.factory.annotation.Value("${ai.chat.max-context-tokens:6000}")
    private int maxContextTokens;

    @Override
    public TarotReadingResultDTO initiateAiTarotReading(User user, StartTarotReadingRequest request) {
        // Step 1: Validate user (already provided from authenticated context)

        // Step 2: Draw cards (pure logic, no reading entity needed)
        List<CardDrawDTO> drawnCardDtos = tarotDrawingService.drawCards(
                null,
                request.getNumberOfCards(),
                request.isIncludeReversed());

        // Step 3: Enrich CardDrawDTO → DrawnCardDetailDTO with full card metadata
        List<DrawnCardDetailDTO> enrichedCards = enrichCardDetails(drawnCardDtos);

        // Step 4: Fetch astrology context (NEW - MVP integration)
        java.util.Optional<AstrologyContextDTO> astrologyContext =
                astrologyContextService.getAstrologyContext(user.getId());

        // Step 5: Build prompt and call Gemini (NO transaction)
        BuildPromptRequest promptRequest = BuildPromptRequest.builder()
                .userQuestion(request.getQuestion())
                .astrologyContext(astrologyContext.orElse(null))
                .drawnCardDetails(enrichedCards)
                .spreadName(request.getSpreadName())
                .build();

        LLMResponse llmResponse = aiTarotService.generateInterpretation(promptRequest);
        log.info("Gemini interpretation received. Model: {}", llmResponse.getModelInfo());

        // Step 6: Persist reading and cards (inside @Transactional)
        TarotReadingResultDTO result = saveReadingAndReturnResult(user, request, enrichedCards, llmResponse);

        // Log AI Usage (after reading is persisted)
        // Fetch the actual TarotReading entity using the ID from the result DTO
        tarotReadingRepository.findById(result.getReadingId()).ifPresent(reading ->
            aiUsageTrackingService.logReadingGeneration(
                    user,
                    reading,
                    llmResponse.getModelInfo() != null ? "Gemini" : "unknown",
                    llmResponse.getModelInfo() != null ? llmResponse.getModelInfo() : "unknown",
                    llmResponse.getTokenUsage(),
                    null // Latency not available from LLMResponse
            )
        );

        // Build and return result DTO
        return result;
    }

    @Override
    @Async("sseTaskExecutor")
    public void initiateAiTarotReadingStream(
            User user,
            StartTarotReadingRequest request,
            Consumer<String> onChunk,
            Consumer<Throwable> onError,
            Consumer<TarotReadingService.StreamReadingResult> onComplete) {
        try {
            log.debug("Stream request: userId={}, numberOfCards={}", user.getId(), request.getNumberOfCards());

            // Step 1: Draw cards (pure logic, no transaction)
            List<CardDrawDTO> drawnCardDtos = tarotDrawingService.drawCards(
                    null,
                    request.getNumberOfCards(),
                    request.isIncludeReversed());

            // Step 2: Enrich CardDrawDTO → DrawnCardDetailDTO
            List<DrawnCardDetailDTO> enrichedCards = enrichCardDetails(drawnCardDtos);

            // Step 3: Fetch astrology context
            Optional<AstrologyContextDTO> astrologyContext =
                    astrologyContextService.getAstrologyContext(user.getId());

            // Step 4: Build prompt request
            BuildPromptRequest promptRequest = BuildPromptRequest.builder()
                    .userQuestion(request.getQuestion())
                    .astrologyContext(astrologyContext.orElse(null))
                    .drawnCardDetails(enrichedCards)
                    .spreadName(request.getSpreadName())
                    .build();

            // Step 5: Stream from AI and accumulate content
            StringBuilder fullContent = new StringBuilder();
            aiTarotService.generateInterpretationStream(
                    promptRequest,
                    chunk -> {
                        fullContent.append(chunk);
                        onChunk.accept(chunk);
                    },
                    error -> {
                        try {
                            log.error("Stream error for userId={}: {}", user.getId(), error.getMessage());
                            onError.accept(error);
                        } catch (Exception onErrorEx) {
                            log.error("Error in onError callback: {}", onErrorEx.getMessage());
                        }
                    },
                    completion -> {
                        try {
                            // Step 6: Persist reading and cards (only on stream completion)
                            TarotReadingResultDTO result = saveStreamedReadingAndReturnResult(
                                    user,
                                    request,
                                    enrichedCards,
                                    fullContent.toString(),
                                    completion);

                            // Step 7: Notify completion with result
                            LLMTokenUsage tokenUsage = completion.getTokenUsage();
                            onComplete.accept(new TarotReadingService.StreamReadingResult(
                                    result.getReadingId(),
                                    null,
                                    fullContent.toString(),
                                    completion.getModelInfo(),
                                    tokenUsage != null ? tokenUsage.getTotalTokens() : 0,
                                    tokenUsage != null ? tokenUsage.getPromptTokens() : 0,
                                    tokenUsage != null ? tokenUsage.getCompletionTokens() : 0
                            ));
                        } catch (Exception persistEx) {
                            log.error("Persistence failed after stream completion: {}", persistEx.getMessage());
                            onError.accept(persistEx);
                        }
                    }
            );
        } catch (Exception e) {
            log.error("Stream setup failed for userId={}: {}", user.getId(), e.getMessage());
            onError.accept(e);
        }
    }

    /**
     * Persists a streamed TarotReading with accumulated content and completion metadata.
     * Called AFTER the Gemini stream completes successfully.
     *
     * Uses @Transactional for atomic persistence. The stream is already complete at this
     * point, so there is no risk of holding a transaction across a streaming call.
     *
     * @return TarotReadingResultDTO with persisted reading ID and metadata
     */
    @Transactional
    protected TarotReadingResultDTO saveStreamedReadingAndReturnResult(
            User user,
            StartTarotReadingRequest request,
            List<DrawnCardDetailDTO> enrichedCards,
            String streamedContent,
            StreamCompletion completion) {

        // Extract token usage from stream completion
        Integer totalTokens = (completion.getTokenUsage() != null
                ? completion.getTokenUsage().getTotalTokens()
                : 0);

        // Create and save TarotReading
        TarotReading reading = tarotReadingRepository.save(TarotReading.builder()
                .user(user)
                .mainQuestion(request.getQuestion())
                .aiModelUsed(completion.getModelInfo())
                .totalTokensUsed(totalTokens)
                .sessionType(SessionType.AI)
                .build());

        log.info("Streamed TarotReading {} created with model: {}", reading.getId(), completion.getModelInfo());

        // Create and save ReadingCard entities
        List<ReadingCard> readingCards = enrichedCards.stream()
                .map(cardDetail -> {
                    TarotCard card = tarotCardRepository.findById(cardDetail.getCardId())
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "Tarot card not found with ID: " + cardDetail.getCardId()));
                    return ReadingCard.builder()
                            .reading(reading)
                            .card(card)
                            .position(cardDetail.getPosition())
                            .reversed(cardDetail.getReversed())
                            .build();
                })
                .collect(Collectors.toList());

        readingCardRepository.saveAll(readingCards);
        log.info("Streamed TarotReading {} saved with {} cards", reading.getId(), readingCards.size());

        // Create ChatSession and persist initial messages
        ensureChatSessionWithInitialMessagesForStream(reading, user, request, streamedContent);

        // Log AI usage (reading entity is available directly here)
        try {
            aiUsageTrackingService.logReadingGeneration(
                    user,
                    reading,
                    completion.getModelInfo() != null ? "Gemini" : "unknown",
                    completion.getModelInfo() != null ? completion.getModelInfo() : "unknown",
                    completion.getTokenUsage(),
                    null
            );
        } catch (Exception usageEx) {
            log.warn("Failed to log AI usage for streaming reading", usageEx);
        }

        // Build and return result DTO
        return TarotReadingResultDTO.builder()
                .readingId(reading.getId())
                .userQuestion(request.getQuestion())
                .spreadName(request.getSpreadName())
                .drawnCards(enrichedCards)
                .aiInterpretation(streamedContent)
                .modelUsed(completion.getModelInfo())
                .totalTokensUsed(totalTokens)
                .readingTimestamp(reading.getCreatedAt())
                .build();
    }

    /**
     * Ensures a ChatSession exists for the streamed reading with initial messages.
     * Similar to ensureChatSessionWithInitialMessages but uses pre-streamed content.
     */
    private void ensureChatSessionWithInitialMessagesForStream(
            TarotReading reading,
            User user,
            StartTarotReadingRequest request,
            String streamedContent) {

        // Defensive validation
        String userQuestion = request.getQuestion();
        if (userQuestion == null || userQuestion.isBlank()) {
            throw new IllegalArgumentException("User question cannot be null or blank");
        }

        if (streamedContent == null || streamedContent.isBlank()) {
            throw new IllegalArgumentException("Streamed interpretation cannot be null or blank");
        }

        // Find existing session or create new one
        Optional<ChatSession> existingSession = chatSessionRepository
                .findByTarotReadingIdAndSessionType(reading.getId(), SessionType.AI);

        ChatSession session;
        if (existingSession.isPresent()) {
            session = existingSession.get();
            log.debug("Reusing existing ChatSession for reading {}", reading.getId());
        } else {
            // Create new ChatSession
            session = ChatSession.builder()
                    .user(user)
                    .tarotReading(reading)
                    .sessionType(SessionType.AI)
                    .status(ChatStatus.ACTIVE)
                    .lastMessageAt(Instant.now())
                    .build();
            session = chatSessionRepository.save(session);
            log.info("Created new ChatSession {} for streamed reading {}", session.getId(), reading.getId());
        }

        // Persist USER message (original question)
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .senderType(SenderType.USER)
                .content(userQuestion)
                .messageType(MessageType.TEXT)
                .build();
        chatMessageRepository.save(userMessage);
        log.debug("Saved initial USER message for session {}", session.getId());

        // Persist AI message (streamed interpretation)
        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .senderType(SenderType.AI)
                .content(streamedContent)
                .messageType(MessageType.TEXT)
                .build();
        chatMessageRepository.save(aiMessage);
        log.debug("Saved initial AI message for session {}", session.getId());

        // Update session timestamp
        session.setLastMessageAt(Instant.now());
        chatSessionRepository.save(session);
        log.info("ChatSession {} initialized with streamed messages", session.getId());
    }

    /**
     * Persists the TarotReading and associated ReadingCard entities.
     * Executed in a single transaction AFTER the Gemini API call succeeds.
     */
    @Transactional
    protected TarotReadingResultDTO saveReadingAndReturnResult(
            User user,
            StartTarotReadingRequest request,
            List<DrawnCardDetailDTO> enrichedCards,
            LLMResponse llmResponse) {

        // Extract token usage info (nullable)
        Integer totalTokens = (llmResponse.getTokenUsage() != null
                ? llmResponse.getTokenUsage().getTotalTokens()
                : 0);

        // Create and save TarotReading
        TarotReading reading = tarotReadingRepository.save(TarotReading.builder()
                .user(user)
                .mainQuestion(request.getQuestion())
                .aiModelUsed(llmResponse.getModelInfo())
                .totalTokensUsed(totalTokens)
                .sessionType(SessionType.AI)
                .build());

        // Log AI Usage (after reading is persisted)
        aiUsageTrackingService.logReadingGeneration(
                user,
                reading,
                llmResponse.getModelInfo() != null ? "Gemini" : "unknown",
                llmResponse.getModelInfo() != null ? llmResponse.getModelInfo() : "unknown",
                llmResponse.getTokenUsage(),
                null // Latency not available from LLMResponse
        );

        // Create and save ReadingCard entities
        List<ReadingCard> readingCards = enrichedCards.stream()
                .map(cardDetail -> {
                    TarotCard card = tarotCardRepository.findById(cardDetail.getCardId())
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "Tarot card not found with ID: " + cardDetail.getCardId()));
                    return ReadingCard.builder()
                            .reading(reading)
                            .card(card)
                            .position(cardDetail.getPosition())
                            .reversed(cardDetail.getReversed())
                            .build();
                })
                .collect(Collectors.toList());

        readingCardRepository.saveAll(readingCards);
        log.info("TarotReading {} saved with {} cards", reading.getId(), readingCards.size());

        // Step 7: Create ChatSession and persist initial messages (Sprint 2)
        ensureChatSessionWithInitialMessages(reading, user, request, llmResponse);

        // Build and return result DTO
        return TarotReadingResultDTO.builder()
                .readingId(reading.getId())
                .userQuestion(request.getQuestion())
                .spreadName(request.getSpreadName())
                .drawnCards(enrichedCards)
                .aiInterpretation(llmResponse.getContent())
                .modelUsed(llmResponse.getModelInfo())
                .totalTokensUsed(totalTokens)
                .readingTimestamp(reading.getCreatedAt())
                .build();
    }

    /**
     * Converts CardDrawDTO list to DrawnCardDetailDTO list by fetching full card details.
     * Each CardDrawDTO contains only cardId, position, reversed.
     * Each DrawnCardDetailDTO contains full card metadata (name, arcana, etc.).
     */
    private List<DrawnCardDetailDTO> enrichCardDetails(List<CardDrawDTO> drawnCardDtos) {
        return drawnCardDtos.stream()
                .map(cardDto -> {
                    TarotCard card = tarotCardRepository.findById(cardDto.getCardId())
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "Tarot card not found with ID: " + cardDto.getCardId()));
                    return DrawnCardDetailDTO.builder()
                            .cardId(card.getId())
                            .cardName(card.getName())
                            .arcanaType(card.getArcanaType())
                            .cardNumber(card.getCardNumber())
                            .imageUrl(card.getImageUrl())
                            .position(cardDto.getPosition())
                            .reversed(cardDto.getReversed())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Ensures a ChatSession exists for the AI reading and persists initial messages.
     *
     * Sprint 2 Feature:
     * - Creates ChatSession if it doesn't exist (prevents duplicates)
     * - Reuses existing session if found by reading ID and session type
     * - Persists initial USER message (original question)
     * - Persists initial AI message (generated interpretation)
     * - Updates session lastMessageAt timestamp
     *
     * All operations are within the @Transactional boundary of saveReadingAndReturnResult().
     *
     * @param reading the TarotReading entity (already persisted)
     * @param user the authenticated user
     * @param request the original reading request
     * @param llmResponse the AI interpretation response
     * @throws IllegalArgumentException if user question or AI content is blank
     */
    private void ensureChatSessionWithInitialMessages(
            TarotReading reading,
            User user,
            StartTarotReadingRequest request,
            LLMResponse llmResponse) {

        // Defensive validation: ensure question is not blank
        String userQuestion = request.getQuestion();
        if (userQuestion == null || userQuestion.isBlank()) {
            throw new IllegalArgumentException("User question cannot be null or blank");
        }

        // Defensive validation: ensure AI response is not blank
        String aiContent = llmResponse.getContent();
        if (aiContent == null || aiContent.isBlank()) {
            throw new IllegalArgumentException("AI interpretation cannot be null or blank");
        }

        // Find existing session or create new one
        Optional<ChatSession> existingSession = chatSessionRepository
                .findByTarotReadingIdAndSessionType(reading.getId(), SessionType.AI);

        ChatSession session;
        if (existingSession.isPresent()) {
            session = existingSession.get();
            log.debug("Reusing existing ChatSession for reading {}", reading.getId());
        } else {
            // Create new ChatSession
            session = ChatSession.builder()
                    .user(user)
                    .tarotReading(reading)
                    .sessionType(SessionType.AI)
                    .status(ChatStatus.ACTIVE)
                    .lastMessageAt(Instant.now())
                    .build();
            session = chatSessionRepository.save(session);
            log.info("Created new ChatSession {} for reading {}", session.getId(), reading.getId());
        }

        // Persist USER message (original question)
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .senderType(SenderType.USER)
                .content(userQuestion)
                .messageType(MessageType.TEXT)
                .build();
        chatMessageRepository.save(userMessage);
        log.debug("Saved initial USER message for session {}", session.getId());

        // Persist AI message (generated interpretation)
        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .senderType(SenderType.AI)
                .content(aiContent)
                .messageType(MessageType.TEXT)
                .build();
        chatMessageRepository.save(aiMessage);
        log.debug("Saved initial AI message for session {}", session.getId());

        // Update session timestamp
        session.setLastMessageAt(Instant.now());
        chatSessionRepository.save(session);
        log.info("ChatSession {} initialized with initial messages", session.getId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.exe.astratarot.domain.dto.reading.ReadingHistoryItem> listHistory(
            java.util.UUID userId, org.springframework.data.domain.Pageable pageable) {
        return tarotReadingRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(r -> new com.exe.astratarot.domain.dto.reading.ReadingHistoryItem(
                        r.getId(),
                        r.getMainQuestion(),
                        r.getSessionType() != null ? r.getSessionType().name() : null,
                        r.getAiModelUsed(),
                        r.getCreatedAt()));
    }
}
