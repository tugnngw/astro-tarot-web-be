package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.domain.dto.reader.CardDrawDTO;
import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.dto.reading.TarotReadingResultDTO;
import com.exe.astratarot.domain.entity.ReadingCard;
import com.exe.astratarot.domain.entity.TarotCard;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.SessionType;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.TarotDrawingService;
import com.exe.astratarot.service.TarotReadingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of TarotReadingService.
 *
 * Orchestrates the complete AI Tarot reading flow:
 * 1. Fetch user and draw cards (no transaction)
 * 2. Enrich card data (CardDrawDTO → DrawnCardDetailDTO)
 * 3. Call Gemini via AITarotService (no transaction)
 * 4. Persist TarotReading and ReadingCard entities (inside @Transactional)
 * 5. Return TarotReadingResultDTO
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

    @Override
    public TarotReadingResultDTO initiateAiTarotReading(StartTarotReadingRequest request) {
        // Step 1: Fetch the user
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found with ID: " + request.getUserId()));

        // Step 2: Draw cards (pure logic, no reading entity needed)
        List<CardDrawDTO> drawnCardDtos = tarotDrawingService.drawCards(
                null,
                request.getNumberOfCards(),
                request.isIncludeReversed());

        // Step 3: Enrich CardDrawDTO → DrawnCardDetailDTO with full card metadata
        List<DrawnCardDetailDTO> enrichedCards = enrichCardDetails(drawnCardDtos);

        // Step 4: Build prompt and call Gemini (NO transaction)
        BuildPromptRequest promptRequest = BuildPromptRequest.builder()
                .userQuestion(request.getQuestion())
                .astrologyContext(null) // MVP: astrology context not yet available
                .drawnCardDetails(enrichedCards)
                .spreadName(request.getSpreadName())
                .build();

        LLMResponse llmResponse = aiTarotService.generateInterpretation(promptRequest);
        log.info("Gemini interpretation received. Model: {}", llmResponse.getModelInfo());

        // Step 5: Persist reading and cards (inside @Transactional)
        return saveReadingAndReturnResult(user, request, enrichedCards, llmResponse);
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
}
