package com.exe.astratarot.service.impl;

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
import com.exe.astratarot.domain.enums.SessionType;
import com.exe.astratarot.exception.LLMProviderException;
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
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TarotReadingServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TarotCardRepository tarotCardRepository;
    @Mock
    private TarotReadingRepository tarotReadingRepository;
    @Mock
    private ReadingCardRepository readingCardRepository;
    @Mock
    private TarotDrawingService tarotDrawingService;
    @Mock
    private AITarotService aiTarotService;
    @Mock
    private AstrologyContextService astrologyContextService;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private AIUsageTrackingService aiUsageTrackingService;

    @InjectMocks
    private TarotReadingServiceImpl tarotReadingService;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_READING_ID = UUID.randomUUID();
    private static final UUID TEST_CARD_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Mock AstrologyContextService to return empty (no context) by default
        // to maintain existing test behavior
        when(astrologyContextService.getAstrologyContext(any(UUID.class)))
                .thenReturn(Optional.empty());

        // Mock ChatSessionRepository to return empty by default (no existing session)
        // This forces creation of new session in tests
        when(chatSessionRepository.findByTarotReadingIdAndSessionType(any(UUID.class), any(SessionType.class)))
                .thenReturn(Optional.empty());

        // Mock ChatSessionRepository.save to return the session passed in
        when(chatSessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Mock ChatMessageRepository.save to return the message passed in
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void initiateAiTarotReading_successfulFlow_shouldReturnResult() {
        User user = User.builder().id(TEST_USER_ID).build();
        List<CardDrawDTO> cardDraws = List.of(
                CardDrawDTO.builder().cardId(TEST_CARD_ID).position((short) 0).reversed(false).build()
        );

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("My destiny?")
                .numberOfCards(1)
                .build();

        LLMResponse llmResponse = LLMResponse.builder()
                .content("Your destiny is bright.")
                .modelInfo("gemini-pro")
                .tokenUsage(LLMTokenUsage.builder().totalTokens(50).build())
                .build();

        TarotCard tarotCard = TarotCard.builder()
                .id(TEST_CARD_ID)
                .name("The Fool")
                .arcanaType("Major Arcana")
                .cardNumber(0)
                .build();

        TarotReading savedReading = TarotReading.builder()
                .id(TEST_READING_ID)
                .user(user)
                .mainQuestion("My destiny?")
                .aiModelUsed("gemini-pro")
                .totalTokensUsed(50)
                .sessionType(SessionType.AI)
                .createdAt(Instant.now())
                .build();

        when(tarotDrawingService.drawCards(null, 1, true)).thenReturn(cardDraws);
        when(tarotCardRepository.findById(TEST_CARD_ID)).thenReturn(Optional.of(tarotCard));
        when(aiTarotService.generateInterpretation(any(BuildPromptRequest.class))).thenReturn(llmResponse);
        when(tarotReadingRepository.save(any(TarotReading.class))).thenReturn(savedReading);

        TarotReadingResultDTO result = tarotReadingService.initiateAiTarotReading(user, request);

        assertNotNull(result);
        assertEquals(TEST_READING_ID, result.getReadingId());
        assertEquals("My destiny?", result.getUserQuestion());
        assertEquals("gemini-pro", result.getModelUsed());
        assertEquals(50, result.getTotalTokensUsed().intValue());
        assertNotNull(result.getDrawnCards());
        assertEquals(1, result.getDrawnCards().size());
        assertEquals("The Fool", result.getDrawnCards().get(0).getCardName());
        assertFalse(result.getDrawnCards().get(0).getReversed());
        assertEquals("Your destiny is bright.", result.getAiInterpretation());

        verify(tarotDrawingService).drawCards(null, 1, true);
        verify(tarotCardRepository, times(2)).findById(TEST_CARD_ID);
        verify(aiTarotService).generateInterpretation(any(BuildPromptRequest.class));
        verify(tarotReadingRepository).save(any(TarotReading.class));
        verify(readingCardRepository).saveAll(anyList());
        verifyNoInteractions(userRepository);
    }

    @Test
    void initiateAiTarotReading_userNotFound_shouldThrowException() {
        // Since User is now passed directly from the authenticated context,
        // userRepository.findById is NOT called in the service.
        // The test needs to be adjusted to reflect this change.

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Does user exist?")
                .numberOfCards(1)
                .build();

        // No mock for userRepository.findById since it's not called.
        // The test primarily verifies that code paths for null user behave correctly.
        // For now, we remove this test case since the user is provided by the security context.
    }

    @Test
    void initiateAiTarotReading_tarotCardNotFound_shouldThrowException() {
        User user = User.builder().id(TEST_USER_ID).build();
        List<CardDrawDTO> cardDraws = List.of(
                CardDrawDTO.builder().cardId(TEST_CARD_ID).position((short) 0).reversed(false).build()
        );

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Card exists?")
                .numberOfCards(1)
                .build();

        when(tarotDrawingService.drawCards(null, 1, true)).thenReturn(cardDraws);
        when(tarotCardRepository.findById(TEST_CARD_ID)).thenReturn(Optional.empty());

        EntityNotFoundException thrown = assertThrows(EntityNotFoundException.class,
                () -> tarotReadingService.initiateAiTarotReading(user, request));
        assertTrue(thrown.getMessage().contains("Tarot card not found"));

        verify(tarotDrawingService).drawCards(null, 1, true);
        verify(tarotCardRepository, times(1)).findById(TEST_CARD_ID); // Only called once in enrichCardDetails before exception is thrown
        verifyNoInteractions(aiTarotService, tarotReadingRepository, readingCardRepository);
    }

    @Test
    void initiateAiTarotReading_geminiFailure_shouldThrowException() {
        User user = User.builder().id(TEST_USER_ID).build();
        List<CardDrawDTO> cardDraws = List.of(
                CardDrawDTO.builder().cardId(TEST_CARD_ID).position((short) 0).reversed(false).build()
        );

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Will Gemini work?")
                .numberOfCards(1)
                .build();

        TarotCard tarotCard = TarotCard.builder().id(TEST_CARD_ID).name("The Fool").arcanaType("Major Arcana").build();

        when(tarotDrawingService.drawCards(null, 1, true)).thenReturn(cardDraws);
        when(tarotCardRepository.findById(TEST_CARD_ID)).thenReturn(Optional.of(tarotCard));
        when(aiTarotService.generateInterpretation(any(BuildPromptRequest.class)))
                .thenThrow(new LLMProviderException("Gemini API is down"));

        LLMProviderException thrown = assertThrows(LLMProviderException.class,
                () -> tarotReadingService.initiateAiTarotReading(user, request));
        assertTrue(thrown.getMessage().contains("Gemini API is down"));

        verify(tarotDrawingService).drawCards(null, 1, true);
        verify(tarotCardRepository).findById(TEST_CARD_ID);
        verify(aiTarotService).generateInterpretation(any(BuildPromptRequest.class));
        verifyNoInteractions(tarotReadingRepository, readingCardRepository);
    }

    @Test
    void initiateAiTarotReading_emptyCardDrawResult_shouldSucceed() {
        User user = User.builder().id(TEST_USER_ID).build();
        List<CardDrawDTO> cardDraws = Collections.emptyList();

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("No cards drawn?")
                .numberOfCards(0)
                .build();

        LLMResponse expectedResponse = LLMResponse.builder()
                .content("AI interpretation for no cards.")
                .modelInfo("mock-model")
                .tokenUsage(null)
                .build();

        TarotReading savedReading = TarotReading.builder()
                .id(TEST_READING_ID)
                .user(user)
                .mainQuestion("No cards drawn?")
                .aiModelUsed("mock-model")
                .totalTokensUsed(0)
                .sessionType(SessionType.AI)
                .createdAt(Instant.now())
                .build();

        when(tarotDrawingService.drawCards(null, 0, true)).thenReturn(cardDraws);
        when(aiTarotService.generateInterpretation(any(BuildPromptRequest.class))).thenReturn(expectedResponse);
        when(tarotReadingRepository.save(any(TarotReading.class))).thenReturn(savedReading);

        TarotReadingResultDTO result = tarotReadingService.initiateAiTarotReading(user, request);

        assertNotNull(result);
        assertEquals(TEST_READING_ID, result.getReadingId());
        assertEquals("No cards drawn?", result.getUserQuestion());
        assertEquals("mock-model", result.getModelUsed());
        assertEquals(0, result.getTotalTokensUsed().intValue());
        assertTrue(result.getDrawnCards().isEmpty());
        assertEquals("AI interpretation for no cards.", result.getAiInterpretation());

        verify(tarotDrawingService).drawCards(null, 0, true);
        verify(tarotCardRepository, never()).findById(any());
        verify(aiTarotService).generateInterpretation(any(BuildPromptRequest.class));
        verify(tarotReadingRepository).save(any(TarotReading.class));
        verify(readingCardRepository).saveAll(Collections.emptyList());
        verifyNoInteractions(userRepository);
    }

    @Test
    void initiateAiTarotReading_persistenceFailure_shouldRollbackTransaction() {
        User user = User.builder().id(TEST_USER_ID).build();
        List<CardDrawDTO> cardDraws = List.of(
                CardDrawDTO.builder().cardId(TEST_CARD_ID).position((short) 0).reversed(false).build()
        );

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Persistence test?")
                .numberOfCards(1)
                .build();

        LLMResponse llmResponse = LLMResponse.builder()
                .content("Gemini succeeded.")
                .modelInfo("gemini-pro")
                .tokenUsage(LLMTokenUsage.builder().totalTokens(50).build())
                .build();

        TarotCard tarotCard = TarotCard.builder().id(TEST_CARD_ID).name("The Fool").arcanaType("Major Arcana").build();

        when(tarotDrawingService.drawCards(null, 1, true)).thenReturn(cardDraws);
        when(tarotCardRepository.findById(TEST_CARD_ID)).thenReturn(Optional.of(tarotCard)); // Mocked for enrichCardDetails
        when(aiTarotService.generateInterpretation(any(BuildPromptRequest.class))).thenReturn(llmResponse);
        when(tarotReadingRepository.save(any(TarotReading.class)))
                .thenThrow(new RuntimeException("Database error during save"));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> tarotReadingService.initiateAiTarotReading(user, request));
        assertTrue(thrown.getMessage().contains("Database error during save"));

        verify(tarotReadingRepository).save(any(TarotReading.class));
        verify(readingCardRepository, never()).saveAll(anyList()); // Ensure saveAll is not called if save fails
        verifyNoInteractions(userRepository);
    }

    @Test
    void initiateAiTarotReading_repositoryException_shouldThrowException() {
        User user = User.builder().id(TEST_USER_ID).build();
        List<CardDrawDTO> cardDraws = List.of(
                CardDrawDTO.builder().cardId(TEST_CARD_ID).position((short) 0).reversed(false).build()
        );

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Repository error test?")
                .numberOfCards(1)
                .build();

        TarotCard tarotCard = TarotCard.builder().id(TEST_CARD_ID).name("The Fool").arcanaType("Major Arcana").build();

        when(tarotDrawingService.drawCards(null, 1, true)).thenReturn(cardDraws);
        when(tarotCardRepository.findById(TEST_CARD_ID)).thenReturn(Optional.of(tarotCard)); // Mocked for enrichCardDetails AND saveReadingAndReturnResult
        when(aiTarotService.generateInterpretation(any(BuildPromptRequest.class)))
                .thenReturn(LLMResponse.builder().content("AI part succeeded").build());

        when(tarotReadingRepository.save(any(TarotReading.class)))
                .thenReturn(TarotReading.builder().id(TEST_READING_ID).user(user).build());
        when(readingCardRepository.saveAll(anyList()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Unique constraint violation"));

        RuntimeException thrown = assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> tarotReadingService.initiateAiTarotReading(user, request));
        assertTrue(thrown.getMessage().contains("Unique constraint violation"));

        verify(tarotDrawingService).drawCards(null, 1, true);
        verify(tarotCardRepository, times(2)).findById(TEST_CARD_ID); // Called in enrichCardDetails + saveReadingAndReturnResult
        verify(aiTarotService).generateInterpretation(any(BuildPromptRequest.class));
        verify(tarotReadingRepository).save(any(TarotReading.class));
        verify(readingCardRepository).saveAll(anyList()); // saveAll was called and threw exception
        verifyNoInteractions(userRepository);
    }

    @Test
    void initiateAiTarotReading_withAuthenticatedUser_ignoresRequestUserId() {
        User authenticatedUser = User.builder().id(TEST_USER_ID).build();

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Security test?")
                .numberOfCards(1)
                .build();

        List<CardDrawDTO> cardDraws = List.of(CardDrawDTO.builder().cardId(TEST_CARD_ID).position((short) 0).reversed(false).build());
        TarotCard tarotCard = TarotCard.builder().id(TEST_CARD_ID).name("The Fool").arcanaType("Major Arcana").build();
        LLMResponse llmResponse = LLMResponse.builder().content("Security test success.").build();
        TarotReading savedReading = TarotReading.builder().id(TEST_READING_ID).user(authenticatedUser).build();

        when(tarotDrawingService.drawCards(null, 1, true)).thenReturn(cardDraws);
        when(tarotCardRepository.findById(TEST_CARD_ID)).thenReturn(Optional.of(tarotCard)); // Mocked for enrichCardDetails AND saveReadingAndReturnResult
        when(aiTarotService.generateInterpretation(any(BuildPromptRequest.class))).thenReturn(llmResponse);
        when(tarotReadingRepository.save(any(TarotReading.class))).thenReturn(savedReading);

        TarotReadingResultDTO result = tarotReadingService.initiateAiTarotReading(authenticatedUser, request);

        assertNotNull(result);
        ArgumentCaptor<TarotReading> readingCaptor = ArgumentCaptor.forClass(TarotReading.class);
        verify(tarotReadingRepository).save(readingCaptor.capture());
        assertEquals(authenticatedUser.getId(), readingCaptor.getValue().getUser().getId());

        verify(tarotDrawingService).drawCards(null, 1, true);
        verify(tarotCardRepository, times(2)).findById(TEST_CARD_ID); // Called in enrichCardDetails + saveReadingAndReturnResult
        verify(aiTarotService).generateInterpretation(any(BuildPromptRequest.class));
        verify(readingCardRepository).saveAll(anyList());
        verifyNoInteractions(userRepository);
    }

    @Test
    void initiateAiTarotReading_cardEnrichmentFailure_shouldThrowException() {
        User user = User.builder().id(TEST_USER_ID).build();
        List<CardDrawDTO> cardDraws = List.of(
                CardDrawDTO.builder().cardId(TEST_CARD_ID).position((short) 0).reversed(false).build()
        );

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Enrichment test?")
                .numberOfCards(1)
                .build();

        when(tarotDrawingService.drawCards(null, 1, true)).thenReturn(cardDraws);
        when(tarotCardRepository.findById(TEST_CARD_ID)).thenThrow(new RuntimeException("DB connection failed"));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> tarotReadingService.initiateAiTarotReading(user, request));
        assertTrue(thrown.getMessage().contains("DB connection failed"));

        verify(aiTarotService, never()).generateInterpretation(any());
        verify(tarotReadingRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }
}
