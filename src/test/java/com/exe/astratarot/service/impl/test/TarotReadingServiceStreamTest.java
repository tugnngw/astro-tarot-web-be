package com.exe.astratarot.service.impl.test;

import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.TarotCard;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.ChatStatus;
import com.exe.astratarot.domain.enums.SessionType;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.repository.ChatMessageRepository;
import com.exe.astratarot.repository.ChatSessionRepository;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.AIUsageTrackingService;
import com.exe.astratarot.service.AstrologyContextService;
import com.exe.astratarot.service.TarotDrawingService;
import com.exe.astratarot.service.TarotReadingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TarotReadingService streaming functionality.
 * Tests the initiateAiTarotReadingStream method with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
@Slf4j
class TarotReadingServiceStreamTest {

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

    private com.exe.astratarot.service.impl.TarotReadingServiceImpl tarotReadingService;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_READING_ID = UUID.randomUUID();
    private static final UUID TEST_CARD_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tarotReadingService = new com.exe.astratarot.service.impl.TarotReadingServiceImpl(
                null, // userRepository (not needed for this test)
                tarotCardRepository,
                tarotReadingRepository,
                readingCardRepository,
                tarotDrawingService,
                aiTarotService,
                astrologyContextService,
                chatSessionRepository,
                chatMessageRepository,
                aiUsageTrackingService
        );
    }

    @Test
    void initiateAiTarotReadingStream_successfulStream_persistsReadingOnCompletion() {
        // Arrange
        User testUser = User.builder()
                .id(TEST_USER_ID)
                .username("test-user")
                .role(UserRole.USER)
                .build();

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Will I succeed?")
                .numberOfCards(3)
                .spreadName("Past-Present-Future")
                .build();

        // Mock card drawing
        com.exe.astratarot.domain.dto.reader.CardDrawDTO cardDraw = com.exe.astratarot.domain.dto.reader.CardDrawDTO.builder()
                .cardId(TEST_CARD_ID)
                .position((short) 0)
                .reversed(false)
                .build();
        when(tarotDrawingService.drawCards(any(), anyInt(), anyBoolean()))
                .thenReturn(List.of(cardDraw));

        // Mock card details
        TarotCard tarotCard = TarotCard.builder()
                .id(TEST_CARD_ID)
                .name("The Fool")
                .arcanaType("Major Arcana")
                .cardNumber(0)
                .imageUrl("https://example.com/fool.jpg")
                .build();
        when(tarotCardRepository.findById(TEST_CARD_ID))
                .thenReturn(Optional.of(tarotCard));

        // Mock astrology context
        when(astrologyContextService.getAstrologyContext(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // Mock reading persistence
        TarotReading savedReading = TarotReading.builder()
                .id(TEST_READING_ID)
                .user(testUser)
                .mainQuestion("Will I succeed?")
                .aiModelUsed("gemini-pro")
                .totalTokensUsed(95)
                .build();
        when(tarotReadingRepository.save(any(TarotReading.class)))
                .thenReturn(savedReading);

        // Mock chat session
        ChatSession mockChatSession = com.exe.astratarot.domain.entity.ChatSession.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tarotReading(savedReading) // Associate with the saved reading
                .sessionType(com.exe.astratarot.domain.enums.SessionType.AI)
                .status(com.exe.astratarot.domain.enums.ChatStatus.ACTIVE)
                .lastMessageAt(Instant.now())
                .build();
        when(chatSessionRepository.findByTarotReadingIdAndSessionType(any(), any()))
                .thenReturn(Optional.empty()); // Assume new session is created
        when(chatSessionRepository.save(any(com.exe.astratarot.domain.entity.ChatSession.class)))
                .thenReturn(mockChatSession);

        // Capture streaming callbacks
        List<String> chunks = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        List<TarotReadingService.StreamReadingResult> results = new ArrayList<>();

        Consumer<String> onChunk = chunks::add;
        Consumer<Throwable> onError = errors::add;
        Consumer<TarotReadingService.StreamReadingResult> onComplete = results::add;

        // Setup AI stream behavior
        doAnswer(invocation -> {
            Consumer<String> chunkCallback = invocation.getArgument(1);
            Consumer<StreamCompletion> completeCallback = invocation.getArgument(3);

            // Simulate streaming chunks
            chunkCallback.accept("The Fool ");
            chunkCallback.accept("suggests ");
            chunkCallback.accept("a new beginning.");

            // Simulate completion
            LLMTokenUsage tokenUsage = LLMTokenUsage.builder()
                    .promptTokens(50)
                    .completionTokens(45)
                    .totalTokens(95)
                    .build();
            StreamCompletion completion = StreamCompletion.builder()
                    .modelInfo("gemini-pro")
                    .tokenUsage(tokenUsage)
                    .build();
            completeCallback.accept(completion);

            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(),
                any(Consumer.class),
                any(Consumer.class),
                any(Consumer.class)
        );

        // Act
        tarotReadingService.initiateAiTarotReadingStream(
                testUser,
                request,
                onChunk,
                onError,
                onComplete
        );

        // Wait for async completion (in real tests, use CountDownLatch or similar)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        assertTrue(chunks.size() > 0, "Should have received chunks");
        assertTrue(errors.isEmpty(), "Should not have errors");
        assertTrue(results.size() > 0, "Should have completion result");

        // Verify reading was persisted
        verify(tarotReadingRepository).save(any(TarotReading.class));
        verify(readingCardRepository).saveAll(any());
        // ChatSession is saved: once on creation, once for timestamp update
        verify(chatSessionRepository, times(2)).save(any());

        // Verify AI usage tracking
        verify(aiUsageTrackingService).logReadingGeneration(
                eq(testUser),
                any(TarotReading.class),
                eq("Gemini"),
                eq("gemini-pro"),
                any(LLMTokenUsage.class),
                eq(null)
        );

        log.info("Stream test passed: {} chunks, {} results", chunks.size(), results.size());
    }

    @Test
    void initiateAiTarotReadingStream_streamError_doesNotPersistReading() {
        // Arrange
        User testUser = User.builder()
                .id(TEST_USER_ID)
                .username("test-user")
                .role(UserRole.USER)
                .build();

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Will I succeed?")
                .numberOfCards(3)
                .build();

        // Mock card drawing
        when(tarotDrawingService.drawCards(any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());

        // Mock astrology context
        when(astrologyContextService.getAstrologyContext(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // Capture callbacks
        List<String> chunks = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        Consumer<String> onChunk = chunks::add;
        Consumer<Throwable> onError = errors::add;
        Consumer<TarotReadingService.StreamReadingResult> onComplete = result -> {
        };

        // Setup AI stream to error
        doAnswer(invocation -> {
            Consumer<Throwable> errorCallback = invocation.getArgument(2);
            errorCallback.accept(new RuntimeException("Gemini API error"));
            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(),
                any(Consumer.class),
                any(Consumer.class),
                any(Consumer.class)
        );

        // Act
        tarotReadingService.initiateAiTarotReadingStream(
                testUser,
                request,
                onChunk,
                onError,
                onComplete
        );

        // Wait for async completion
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        assertTrue(chunks.isEmpty() || chunks.size() == 0, "Should not have received chunks on error");
        assertTrue(errors.size() > 0, "Should have error");
        verify(tarotReadingRepository, never()).save(any(TarotReading.class));
        verify(readingCardRepository, never()).saveAll(any());
        verify(chatSessionRepository, never()).save(any());
        verify(aiUsageTrackingService, never()).logReadingGeneration(any(), any(), any(), any(), any(), any());

        log.info("Error handling test passed");
    }

    @Test
    void initiateAiTarotReadingStream_invokesChunkCallbackCorrectly() {
        // Arrange
        User testUser = User.builder()
                .id(TEST_USER_ID)
                .username("test-user")
                .role(UserRole.USER)
                .build();

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Will I succeed?")
                .numberOfCards(2)
                .build();

        // Mock minimal dependencies
        when(tarotDrawingService.drawCards(any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());
        when(astrologyContextService.getAstrologyContext(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // Mock persistence
        TarotReading savedReading = TarotReading.builder()
                .id(TEST_READING_ID)
                .user(testUser)
                .mainQuestion("Will I succeed?")
                .aiModelUsed("gemini-pro")
                .totalTokensUsed(50)
                .build();
        when(tarotReadingRepository.save(any(TarotReading.class)))
                .thenReturn(savedReading);

        ChatSession mockChatSession = ChatSession.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tarotReading(savedReading)
                .sessionType(com.exe.astratarot.domain.enums.SessionType.AI)
                .status(ChatStatus.ACTIVE)
                .lastMessageAt(Instant.now())
                .build();
        when(chatSessionRepository.findByTarotReadingIdAndSessionType(any(), any()))
                .thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class)))
                .thenReturn(mockChatSession);

        // Capture streaming callbacks
        List<String> chunks = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        List<TarotReadingService.StreamReadingResult> results = new ArrayList<>();

        Consumer<String> onChunk = chunks::add;
        Consumer<Throwable> onError = errors::add;
        Consumer<TarotReadingService.StreamReadingResult> onComplete = results::add;

        // Setup AI stream to send specific chunks
        doAnswer(invocation -> {
            Consumer<String> chunkCallback = invocation.getArgument(1);
            Consumer<StreamCompletion> completeCallback = invocation.getArgument(3);

            // Simulate streaming chunks
            chunkCallback.accept("First interpretation chunk.");
            chunkCallback.accept("Second interpretation chunk.");
            chunkCallback.accept("Final interpretation chunk.");

            // Simulate completion
            LLMTokenUsage tokenUsage = LLMTokenUsage.builder()
                    .promptTokens(30)
                    .completionTokens(20)
                    .totalTokens(50)
                    .build();
            StreamCompletion completion = StreamCompletion.builder()
                    .modelInfo("gemini-pro")
                    .tokenUsage(tokenUsage)
                    .build();
            completeCallback.accept(completion);

            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(),
                any(Consumer.class),
                any(Consumer.class),
                any(Consumer.class)
        );

        // Act
        tarotReadingService.initiateAiTarotReadingStream(
                testUser,
                request,
                onChunk,
                onError,
                onComplete
        );

        // Wait for async completion
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        assertEquals(3, chunks.size(), "Should have received exactly 3 chunks");
        assertEquals("First interpretation chunk.", chunks.get(0));
        assertEquals("Second interpretation chunk.", chunks.get(1));
        assertEquals("Final interpretation chunk.", chunks.get(2));
        assertTrue(errors.isEmpty(), "Should not have errors");
        assertEquals(1, results.size(), "Should have exactly one completion result");

        // Verify the result
        TarotReadingService.StreamReadingResult result = results.get(0);
        assertEquals(TEST_READING_ID, result.readingId());
        assertEquals("First interpretation chunk.Second interpretation chunk.Final interpretation chunk.", result.aiInterpretation());
        assertEquals("gemini-pro", result.modelUsed());
        assertEquals(50, result.totalTokens());
        assertEquals(30, result.promptTokens());
        assertEquals(20, result.completionTokens());

        log.info("Chunk callback test passed: {} chunks", chunks.size());
    }

    @Test
    void initiateAiTarotReadingStream_invokesCompleteCallbackOnSuccess() {
        // Arrange
        User testUser = User.builder()
                .id(TEST_USER_ID)
                .username("test-user")
                .role(UserRole.USER)
                .build();

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Test question")
                .numberOfCards(1)
                .build();

        // Mock minimal dependencies
        when(tarotDrawingService.drawCards(any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());
        when(astrologyContextService.getAstrologyContext(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // Mock persistence
        TarotReading savedReading = TarotReading.builder()
                .id(TEST_READING_ID)
                .user(testUser)
                .mainQuestion("Test question")
                .aiModelUsed("gemini-pro")
                .totalTokensUsed(25)
                .build();
        when(tarotReadingRepository.save(any(TarotReading.class)))
                .thenReturn(savedReading);

        ChatSession mockChatSession = ChatSession.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .tarotReading(savedReading)
                .sessionType(com.exe.astratarot.domain.enums.SessionType.AI)
                .status(ChatStatus.ACTIVE)
                .lastMessageAt(Instant.now())
                .build();
        when(chatSessionRepository.findByTarotReadingIdAndSessionType(any(), any()))
                .thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class)))
                .thenReturn(mockChatSession);

        // Capture callbacks
        List<String> chunks = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        List<TarotReadingService.StreamReadingResult> results = new ArrayList<>();

        Consumer<String> onChunk = chunks::add;
        Consumer<Throwable> onError = errors::add;
        Consumer<TarotReadingService.StreamReadingResult> onComplete = results::add;

        // Setup AI stream
        doAnswer(invocation -> {
            Consumer<String> chunkCallback = invocation.getArgument(1);
            Consumer<StreamCompletion> completeCallback = invocation.getArgument(3);

            // Simulate streaming
            chunkCallback.accept("Test interpretation");

            // Simulate completion
            LLMTokenUsage tokenUsage = LLMTokenUsage.builder()
                    .promptTokens(15)
                    .completionTokens(10)
                    .totalTokens(25)
                    .build();
            StreamCompletion completion = StreamCompletion.builder()
                    .modelInfo("gemini-pro")
                    .tokenUsage(tokenUsage)
                    .build();
            completeCallback.accept(completion);

            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(),
                any(Consumer.class),
                any(Consumer.class),
                any(Consumer.class)
        );

        // Act
        tarotReadingService.initiateAiTarotReadingStream(
                testUser,
                request,
                onChunk,
                onError,
                onComplete
        );

        // Wait for async completion
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        assertTrue(chunks.size() > 0, "Should have received chunks");
        assertTrue(errors.isEmpty(), "Should not have errors");
        assertEquals(1, results.size(), "Should have exactly one completion result");

        // Verify the result
        TarotReadingService.StreamReadingResult result = results.get(0);
        assertEquals(TEST_READING_ID, result.readingId());
        assertEquals("Test interpretation", result.aiInterpretation());
        assertEquals("gemini-pro", result.modelUsed());
        assertEquals(25, result.totalTokens());
        assertEquals(15, result.promptTokens());
        assertEquals(10, result.completionTokens());

        log.info("Complete callback test passed");
    }

    @Test
    void initiateAiTarotReadingStream_invokesErrorCallbackOnProviderFailure() {
        // Arrange
        User testUser = User.builder()
                .id(TEST_USER_ID)
                .username("test-user")
                .role(UserRole.USER)
                .build();

        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Will I succeed?")
                .numberOfCards(3)
                .build();

        // Mock minimal dependencies
        when(tarotDrawingService.drawCards(any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());
        when(astrologyContextService.getAstrologyContext(TEST_USER_ID))
                .thenReturn(Optional.empty());

        // Capture callbacks
        List<String> chunks = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        List<TarotReadingService.StreamReadingResult> results = new ArrayList<>();

        Consumer<String> onChunk = chunks::add;
        Consumer<Throwable> onError = errors::add;
        Consumer<TarotReadingService.StreamReadingResult> onComplete = result -> {
        };

        // Setup AI stream to error
        doAnswer(invocation -> {
            Consumer<Throwable> errorCallback = invocation.getArgument(2);
            errorCallback.accept(new RuntimeException("Gemini API temporarily unavailable"));
            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(),
                any(Consumer.class),
                any(Consumer.class),
                any(Consumer.class)
        );

        // Act
        tarotReadingService.initiateAiTarotReadingStream(
                testUser,
                request,
                onChunk,
                onError,
                onComplete
        );

        // Wait for async completion
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Assert
        assertTrue(chunks.isEmpty(), "Should not receive chunks on immediate error");
        assertTrue(errors.size() > 0, "Should have received error");
        assertEquals(1, errors.size(), "Should have exactly one error");
        assertTrue(results.isEmpty(), "Should not have completion result on error");

        // Verify error type
        Throwable error = errors.get(0);
        assertTrue(error instanceof RuntimeException);
        assertEquals("Gemini API temporarily unavailable", error.getMessage());

        // Verify no persistence occurred
        verify(tarotReadingRepository, never()).save(any(TarotReading.class));
        verify(readingCardRepository, never()).saveAll(any());
        verify(chatSessionRepository, never()).save(any());
        verify(aiUsageTrackingService, never()).logReadingGeneration(any(), any(), any(), any(), any(), any());

        log.info("Error callback test passed");
    }
}
