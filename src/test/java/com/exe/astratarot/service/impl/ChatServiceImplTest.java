package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.chat.ChatHistoryResponse;
import com.exe.astratarot.domain.dto.chat.ChatMessageResponse;
import com.exe.astratarot.domain.dto.chat.ChatResponse;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.domain.entity.ChatMessage;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.ReadingCard;
import com.exe.astratarot.domain.entity.TarotCard;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.ChatStatus;
import com.exe.astratarot.domain.enums.SenderType;
import com.exe.astratarot.exception.LLMProviderException;
import com.exe.astratarot.repository.ChatMessageRepository;
import com.exe.astratarot.repository.ChatSessionRepository;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.AstrologyContextService;
import com.exe.astratarot.service.ChatService;
import com.exe.astratarot.service.TokenEstimatorService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatServiceImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private TarotReadingRepository tarotReadingRepository;
    @Mock
    private ReadingCardRepository readingCardRepository;
    @Mock
    private AITarotService aiTarotService;
    @Mock
    private AstrologyContextService astrologyContextService;

    @Mock
    private TokenEstimatorService tokenEstimatorService;

    @InjectMocks
    private ChatServiceImpl chatService;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_OTHER_USER_ID = UUID.randomUUID();
    private static final UUID TEST_READING_ID = UUID.randomUUID();
    private static final UUID TEST_SESSION_ID = UUID.randomUUID();

    private User user;
    private User otherUser;
    private TarotReading reading;
    private ChatSession session;
    private LLMResponse llmResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock TokenEstimatorService to estimate tokens as chars / 4
        when(tokenEstimatorService.estimateTokens(any(String.class)))
                .thenAnswer(inv -> {
                    String text = inv.getArgument(0);
                    return text == null ? 0 : Math.max(1, text.length() / 4);
                });

        user = User.builder().id(TEST_USER_ID).build();
        otherUser = User.builder().id(TEST_OTHER_USER_ID).build();
        reading = TarotReading.builder()
                .id(TEST_READING_ID)
                .user(user)
                .mainQuestion("What is my destiny?")
                .totalTokensUsed(0)
                .build();
        session = ChatSession.builder()
                .id(TEST_SESSION_ID)
                .user(user)
                .tarotReading(reading)
                .status(ChatStatus.ACTIVE)
                .build();

        llmResponse = LLMResponse.builder()
                .content("AI response content")
                .modelInfo("gemini-2.0-flash-lite")
                .tokenUsage(LLMTokenUsage.builder()
                        .promptTokens(10)
                        .completionTokens(20)
                        .totalTokens(30)
                        .build())
                .build();

        // Default mocks
        lenient().when(astrologyContextService.getAstrologyContext(any(UUID.class)))
                .thenReturn(Optional.empty());
        lenient().when(readingCardRepository.findByReading(any(TarotReading.class)))
                .thenReturn(List.of());
        lenient().when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                        any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage msg = invocation.getArgument(0);
                    if (msg.getId() == null) {
                        ReflectionTestUtils.setField(msg, "id", UUID.randomUUID());
                    }
                    return msg;
                });
        lenient().when(chatSessionRepository.save(any(ChatSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(tarotReadingRepository.save(any(TarotReading.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ==========================================================
    // Batch 1: sendMessage Foundation
    // ==========================================================

    @Test
    void sendMessage_happyPath_shouldReturnChatResponse() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        ChatResponse result = chatService.sendMessage(TEST_READING_ID, user, "Follow up");

        assertNotNull(result);
        assertEquals(TEST_SESSION_ID, result.getSessionId());
        assertNotNull(result.getMessageId());
        assertEquals("AI response content", result.getReply());
        assertEquals("gemini-2.0-flash-lite", result.getModelUsed());
        assertEquals(10, result.getPromptTokens());
        assertEquals(20, result.getCompletionTokens());
        assertEquals(30, result.getTotalTokens());

        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
        verify(chatSessionRepository).save(session);
        verify(tarotReadingRepository).save(reading);
        assertEquals(30, reading.getTotalTokensUsed());
    }

    @Test
    void sendMessage_readingNotFound_shouldThrowEntityNotFoundException() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> chatService.sendMessage(TEST_READING_ID, user, "message"));
        assertTrue(ex.getMessage().contains("Tarot reading not found"));

        verify(chatMessageRepository, never()).save(any());
        verify(aiTarotService, never()).generateInterpretation(any());
    }

    @Test
    void sendMessage_ownershipMismatch_shouldThrowIllegalArgumentException() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> chatService.sendMessage(TEST_READING_ID, otherUser, "message"));
        assertTrue(ex.getMessage().contains("does not belong to user"));

        verify(chatMessageRepository, never()).save(any());
        verify(aiTarotService, never()).generateInterpretation(any());
    }

    @Test
    void sendMessage_sessionNotFound_shouldThrowEntityNotFoundException() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> chatService.sendMessage(TEST_READING_ID, user, "message"));
        assertTrue(ex.getMessage().contains("No active chat session"));

        verify(chatMessageRepository, never()).save(any());
        verify(aiTarotService, never()).generateInterpretation(any());
    }

    @Test
    void sendMessage_sessionClosed_shouldThrowIllegalStateException() {
        session.setStatus(ChatStatus.CLOSED);
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> chatService.sendMessage(TEST_READING_ID, user, "message"));
        assertTrue(ex.getMessage().contains("closed"));

        verify(chatMessageRepository, never()).save(any());
        verify(aiTarotService, never()).generateInterpretation(any());
    }

    // ==========================================================
    // Batch 1: getMessages Foundation
    // ==========================================================

    @Test
    void getMessages_happyPath_shouldReturnChatHistoryResponse() {
        ChatMessage msg1 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(session)
                .senderType(SenderType.USER)
                .content("Hello")
                .createdAt(Instant.now())
                .build();
        ChatMessage msg2 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(session)
                .senderType(SenderType.AI)
                .content("Hi there")
                .createdAt(Instant.now())
                .build();
        Page<ChatMessage> messagePage = new PageImpl<>(
                List.of(msg1, msg2), PageRequest.of(0, 20), 2);

        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                eq(TEST_SESSION_ID), any(Pageable.class)))
                .thenReturn(messagePage);

        ChatHistoryResponse result = chatService.getMessages(
                TEST_READING_ID, user, 0, 20);

        assertNotNull(result);
        assertEquals(TEST_SESSION_ID, result.getSessionId());
        assertEquals(TEST_READING_ID, result.getReadingId());
        assertEquals(ChatStatus.ACTIVE, result.getStatus());
        assertEquals(2, result.getMessages().size());
        assertEquals(2, result.getTotalMessages());
        assertFalse(result.isHasMore());
        assertEquals(1, result.getTotalPages());
        assertEquals(0, result.getCurrentPage());

        ChatMessageResponse firstMsg = result.getMessages().get(0);
        assertEquals(SenderType.USER, firstMsg.getSenderType());
        assertEquals("Hello", firstMsg.getContent());
        assertEquals(msg1.getId(), firstMsg.getId());
    }

    @Test
    void getMessages_readingNotFound_shouldThrowEntityNotFoundException() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> chatService.getMessages(TEST_READING_ID, user, 0, 20));
    }

    @Test
    void getMessages_ownershipMismatch_shouldThrowIllegalArgumentException() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> chatService.getMessages(TEST_READING_ID, otherUser, 0, 20));
        assertTrue(ex.getMessage().contains("does not belong to user"));
    }

    @Test
    void getMessages_sessionNotFound_shouldThrowEntityNotFoundException() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                () -> chatService.getMessages(TEST_READING_ID, user, 0, 20));
        assertTrue(ex.getMessage().contains("No chat session found"));
    }

    // ==========================================================
    // Batch 2: Edge Cases
    // ==========================================================

    @Test
    void sendMessage_emptyMessage_shouldSucceed() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        ChatResponse result = chatService.sendMessage(
                TEST_READING_ID, user, "");

        assertNotNull(result);
        assertEquals("AI response content", result.getReply());
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(msgCaptor.capture());
        ChatMessage userMsg = msgCaptor.getAllValues().get(0);
        assertEquals("", userMsg.getContent());
        assertEquals(SenderType.USER, userMsg.getSenderType());
    }

    @Test
    void sendMessage_nullMessageContent_shouldSucceed() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        // The service does not validate message content for null.
        // In production the DB constraint (@Column(nullable = false) on content)
        // would reject null, but at the service layer with mocked repos it passes.
        ChatResponse result = chatService.sendMessage(
                TEST_READING_ID, user, null);

        assertNotNull(result);
        assertEquals("AI response content", result.getReply());
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(msgCaptor.capture());
        assertNull(msgCaptor.getAllValues().get(0).getContent());
    }

    @Test
    void getMessages_emptyHistory_shouldReturnEmptyResults() {
        Page<ChatMessage> emptyPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0);

        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                eq(TEST_SESSION_ID), any(Pageable.class)))
                .thenReturn(emptyPage);

        ChatHistoryResponse result = chatService.getMessages(
                TEST_READING_ID, user, 0, 20);

        assertTrue(result.getMessages().isEmpty());
        assertEquals(0, result.getTotalMessages());
        assertFalse(result.isHasMore());
        assertEquals(0, result.getTotalPages());
    }

    @Test
    void getMessages_secondPage_shouldReturnCorrectPaginationMetadata() {
        List<ChatMessage> pageMessages = List.of(
                ChatMessage.builder()
                        .id(UUID.randomUUID())
                        .session(session)
                        .senderType(SenderType.USER)
                        .content("msg 1")
                        .createdAt(Instant.now())
                        .build(),
                ChatMessage.builder()
                        .id(UUID.randomUUID())
                        .session(session)
                        .senderType(SenderType.AI)
                        .content("msg 2")
                        .createdAt(Instant.now())
                        .build()
        );
        Page<ChatMessage> secondPage = new PageImpl<>(
                pageMessages, PageRequest.of(1, 20), 45);

        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                eq(TEST_SESSION_ID), any(Pageable.class)))
                .thenReturn(secondPage);

        ChatHistoryResponse result = chatService.getMessages(
                TEST_READING_ID, user, 1, 20);

        assertEquals(2, result.getMessages().size());
        assertEquals(45, result.getTotalMessages());
        assertTrue(result.isHasMore());
        assertEquals(3, result.getTotalPages());
        assertEquals(1, result.getCurrentPage());
    }

    // ==========================================================
    // Batch 3: sendMessageStream Validation
    // ==========================================================

    @Test
    void sendMessageStream_happyPath_shouldDeliverChunksAndComplete() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));

        StreamCompletion completion = new StreamCompletion(
                "gemini-2.0-flash-lite",
                LLMTokenUsage.builder()
                        .promptTokens(10)
                        .completionTokens(20)
                        .totalTokens(30)
                        .build());

        doAnswer(invocation -> {
            Consumer<String> onChunkArg = invocation.getArgument(1);
            Consumer<StreamCompletion> onCompleteArg = invocation.getArgument(3);
            onChunkArg.accept("partial ");
            onChunkArg.accept("response");
            onCompleteArg.accept(completion);
            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        Consumer<String> onChunk = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<ChatService.StreamResult> onComplete = mock(Consumer.class);

        chatService.sendMessageStream(
                TEST_READING_ID, user, "message", onChunk, onError, onComplete);

        verify(onChunk).accept("partial ");
        verify(onChunk).accept("response");
        verify(onComplete).accept(any());
        verify(onError, never()).accept(any());

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(msgCaptor.capture());
        assertEquals(SenderType.USER, msgCaptor.getAllValues().get(0).getSenderType());
        assertEquals(SenderType.AI, msgCaptor.getAllValues().get(1).getSenderType());
        assertEquals("partial response",
                msgCaptor.getAllValues().get(1).getContent());

        verify(chatSessionRepository).save(session);
        verify(tarotReadingRepository).save(reading);
        assertEquals(30, reading.getTotalTokensUsed());
    }

    @Test
    void sendMessageStream_readingNotFound_shouldCallOnError() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        Consumer<String> onChunk = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<ChatService.StreamResult> onComplete = mock(Consumer.class);

        chatService.sendMessageStream(
                TEST_READING_ID, user, "message", onChunk, onError, onComplete);

        verify(onError).accept(any(EntityNotFoundException.class));
        verify(onChunk, never()).accept(any());
        verify(onComplete, never()).accept(any());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessageStream_ownershipMismatch_shouldCallOnError() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));

        @SuppressWarnings("unchecked")
        Consumer<String> onChunk = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<ChatService.StreamResult> onComplete = mock(Consumer.class);

        chatService.sendMessageStream(
                TEST_READING_ID, otherUser, "message", onChunk, onError, onComplete);

        verify(onError).accept(any(IllegalArgumentException.class));
        verify(onChunk, never()).accept(any());
        verify(onComplete, never()).accept(any());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessageStream_sessionNotFound_shouldCallOnError() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        Consumer<String> onChunk = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<ChatService.StreamResult> onComplete = mock(Consumer.class);

        chatService.sendMessageStream(
                TEST_READING_ID, user, "message", onChunk, onError, onComplete);

        verify(onError).accept(any(EntityNotFoundException.class));
        verify(onChunk, never()).accept(any());
        verify(onComplete, never()).accept(any());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void sendMessageStream_sessionClosed_shouldCallOnError() {
        session.setStatus(ChatStatus.CLOSED);
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));

        @SuppressWarnings("unchecked")
        Consumer<String> onChunk = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<ChatService.StreamResult> onComplete = mock(Consumer.class);

        chatService.sendMessageStream(
                TEST_READING_ID, user, "message", onChunk, onError, onComplete);

        verify(onError).accept(any(IllegalStateException.class));
        verify(onChunk, never()).accept(any());
        verify(onComplete, never()).accept(any());
        verify(chatMessageRepository, never()).save(any());
    }

    // ==========================================================
    // Batch 4: sendMessageStream Callback Wiring
    // ==========================================================

    @Test
    void sendMessageStream_onChunkInvoked_shouldDeliverEachChunk() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));

        doAnswer(invocation -> {
            Consumer<String> onChunkArg = invocation.getArgument(1);
            onChunkArg.accept("token1");
            onChunkArg.accept("token2");
            onChunkArg.accept("token3");
            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        Consumer<String> onChunk = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<ChatService.StreamResult> onComplete = mock(Consumer.class);

        chatService.sendMessageStream(
                TEST_READING_ID, user, "message", onChunk, onError, onComplete);

        verify(onChunk, times(3)).accept(anyString());
        verify(onChunk).accept("token1");
        verify(onChunk).accept("token2");
        verify(onChunk).accept("token3");
        verify(onError, never()).accept(any());
        // onComplete not called in this test (no completion fired)
        verify(onComplete, never()).accept(any());
    }

    @Test
    void sendMessageStream_onCompleteInvoked_shouldPersistAndComplete() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));

        StreamCompletion completion = new StreamCompletion(
                "gemini-2.0-flash-lite",
                LLMTokenUsage.builder()
                        .promptTokens(5)
                        .completionTokens(15)
                        .totalTokens(20)
                        .build());

        doAnswer(invocation -> {
            Consumer<String> onChunkArg = invocation.getArgument(1);
            Consumer<StreamCompletion> onCompleteArg = invocation.getArgument(3);
            onChunkArg.accept("full response text");
            onCompleteArg.accept(completion);
            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        Consumer<String> onChunk = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<ChatService.StreamResult> onComplete = mock(Consumer.class);

        chatService.sendMessageStream(
                TEST_READING_ID, user, "message", onChunk, onError, onComplete);

        // Verify message persisted
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(msgCaptor.capture());
        assertEquals("full response text",
                msgCaptor.getAllValues().get(1).getContent());

        // Verify token count updated
        verify(tarotReadingRepository).save(reading);
        assertEquals(20, reading.getTotalTokensUsed());

        // Verify session timestamp updated
        verify(chatSessionRepository).save(session);
        assertNotNull(session.getLastMessageAt());

        // Verify StreamResult delivered
        ArgumentCaptor<ChatService.StreamResult> resultCaptor =
                ArgumentCaptor.forClass(ChatService.StreamResult.class);
        verify(onComplete).accept(resultCaptor.capture());
        assertEquals(TEST_SESSION_ID, resultCaptor.getValue().sessionId());
        assertNotNull(resultCaptor.getValue().messageId());
        assertEquals("gemini-2.0-flash-lite", resultCaptor.getValue().modelUsed());
        assertEquals(20, resultCaptor.getValue().totalTokens());
        assertEquals(5, resultCaptor.getValue().promptTokens());
        assertEquals(15, resultCaptor.getValue().completionTokens());

        verify(onError, never()).accept(any());
    }

    @Test
    void sendMessageStream_onErrorInvoked_shouldHandleProviderError() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(session));

        doAnswer(invocation -> {
            Consumer<Throwable> onErrorArg = invocation.getArgument(2);
            onErrorArg.accept(new LLMProviderException("Gemini API error"));
            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        Consumer<String> onChunk = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<Throwable> onError = mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<ChatService.StreamResult> onComplete = mock(Consumer.class);

        chatService.sendMessageStream(
                TEST_READING_ID, user, "message", onChunk, onError, onComplete);

        // Error delivered to caller
        verify(onError).accept(any(LLMProviderException.class));

        // USER message saved, AI message NOT saved
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));

        // No AI persistence — error path skips completion callback entirely
        verify(tarotReadingRepository, never()).save(any(TarotReading.class));
        verify(chatSessionRepository, never()).save(any(ChatSession.class));

        verify(onChunk, never()).accept(any());
        verify(onComplete, never()).accept(any());
    }
}
