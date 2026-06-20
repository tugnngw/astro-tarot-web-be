package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
}
