package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.chat.ChatResponse;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.entity.ChatMessage;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.ChatStatus;
import com.exe.astratarot.domain.enums.SenderType;
import com.exe.astratarot.domain.enums.SessionType;
import com.exe.astratarot.repository.ChatMessageRepository;
import com.exe.astratarot.repository.ChatSessionRepository;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.AstrologyContextService;
import com.exe.astratarot.service.TokenEstimatorService;
import com.exe.astratarot.service.AIUsageTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for ChatService conversation state and history behavior.
 *
 * Covers:
 * - Finding 1: Astrology chat persistence when readingId == null
 * - Finding 2: Current user message not duplicated in history
 * - Finding 3: Chronological ordering in history
 * - Finding 5: Session identity consistency
 */
class ChatServiceConversationStateTest {

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
    @Mock
    private AIUsageTrackingService aiUsageTrackingService;

    @InjectMocks
    private ChatServiceImpl chatService;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_READING_ID = UUID.randomUUID();
    private static final UUID TEST_SESSION_ID = UUID.randomUUID();
    private static final UUID ASTRO_SESSION_ID = UUID.randomUUID();

    private User user;
    private TarotReading reading;
    private ChatSession tarotSession;
    private ChatSession astroSession;
    private LLMResponse llmResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set max token budget (normally injected via @Value)
        ReflectionTestUtils.setField(chatService, "maxContextTokens", 8000);

        // Mock token estimator
        when(tokenEstimatorService.estimateTokens(any(String.class)))
                .thenAnswer(inv -> {
                    String text = inv.getArgument(0);
                    return text == null ? 0 : Math.max(1, text.length() / 4);
                });

        user = User.builder().id(TEST_USER_ID).build();
        reading = TarotReading.builder()
                .id(TEST_READING_ID)
                .user(user)
                .mainQuestion("What is my destiny?")
                .totalTokensUsed(0)
                .build();
        tarotSession = ChatSession.builder()
                .id(TEST_SESSION_ID)
                .user(user)
                .tarotReading(reading)
                .sessionType(SessionType.AI)
                .status(ChatStatus.ACTIVE)
                .build();
        astroSession = ChatSession.builder()
                .id(ASTRO_SESSION_ID)
                .user(user)
                .tarotReading(null)
                .sessionType(SessionType.AI)
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
                .thenAnswer(invocation -> {
                    ChatSession s = invocation.getArgument(0);
                    if (s.getId() == null) {
                        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
                    }
                    return s;
                });
        lenient().when(tarotReadingRepository.save(any(TarotReading.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ChatMessage createMessage(SenderType senderType, String content, Instant createdAt) {
        return ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(tarotSession)
                .senderType(senderType)
                .content(content)
                .createdAt(createdAt)
                .build();
    }

    private ChatMessage createMessage(SenderType senderType, String content, Instant createdAt, UUID id) {
        return ChatMessage.builder()
                .id(id)
                .session(tarotSession)
                .senderType(senderType)
                .content(content)
                .createdAt(createdAt)
                .build();
    }

    // ==========================================================
    // Finding 1: Astrology chat persistence when readingId == null
    // ==========================================================

    @Test
    @DisplayName("Astrology chat creates session and persists messages")
    void astrologyChat_createsSessionAndPersists() {
        // No existing session
        when(chatSessionRepository.findByUserIdAndTarotReadingIsNullAndSessionType(
                TEST_USER_ID, SessionType.AI))
                .thenReturn(Optional.empty());
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        ChatResponse result = chatService.sendMessage(null, user, "Công việc của tôi thế nào?");

        // Verify session created (saved twice: create + update lastMessageAt)
        assertNotNull(result.getSessionId());
        verify(chatSessionRepository, times(2)).save(any(ChatSession.class));

        // Verify messages persisted (USER + AI)
        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(msgCaptor.capture());
        assertEquals(SenderType.USER, msgCaptor.getAllValues().get(0).getSenderType());
        assertEquals("Công việc của tôi thế nào?", msgCaptor.getAllValues().get(0).getContent());
        assertEquals(SenderType.AI, msgCaptor.getAllValues().get(1).getSenderType());
        assertEquals("AI response content", msgCaptor.getAllValues().get(1).getContent());

        // Verify response has session/message IDs
        assertNotNull(result.getSessionId());
        assertNotNull(result.getMessageId());
        assertEquals("AI response content", result.getReply());
    }

    @Test
    @DisplayName("Astrology chat follow-up loads previous conversation history")
    void astrologyChat_followUpLoadsHistory() {
        // Existing session
        when(chatSessionRepository.findByUserIdAndTarotReadingIsNullAndSessionType(
                TEST_USER_ID, SessionType.AI))
                .thenReturn(Optional.of(astroSession));

        // Previous conversation: USER asked, AI responded
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now().minusSeconds(30);
        List<ChatMessage> history = List.of(
                createMessage(SenderType.USER, "Công việc của tôi thế nào?", t1),
                createMessage(SenderType.AI, "Tôi thấy The Tower...", t2)
        );
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                eq(ASTRO_SESSION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(history));

        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        ChatResponse result = chatService.sendMessage(null, user, "Còn tài chính?");

        // Verify BuildPromptRequest includes conversation history
        ArgumentCaptor<BuildPromptRequest> promptCaptor = ArgumentCaptor.forClass(BuildPromptRequest.class);
        verify(aiTarotService).generateInterpretation(promptCaptor.capture());
        BuildPromptRequest promptRequest = promptCaptor.getValue();

        assertNotNull(promptRequest.getConversationHistory());
        String historyStr = promptRequest.getConversationHistory();
        assertTrue(historyStr.contains("Công việc của tôi thế nào?"));
        assertTrue(historyStr.contains("Tôi thấy The Tower..."));

        // Verify current message is userQuestion, not in history
        assertEquals("Còn tài chính?", promptRequest.getUserQuestion());
        assertFalse(historyStr.contains("Còn tài chính?"));

        // Verify same session reused
        assertEquals(ASTRO_SESSION_ID, result.getSessionId());
    }

    @Test
    @DisplayName("Astrology chat with closed session throws error")
    void astrologyChat_closedSessionThrows() {
        astroSession.setStatus(ChatStatus.CLOSED);
        when(chatSessionRepository.findByUserIdAndTarotReadingIsNullAndSessionType(
                TEST_USER_ID, SessionType.AI))
                .thenReturn(Optional.of(astroSession));

        assertThrows(IllegalStateException.class,
                () -> chatService.sendMessage(null, user, "Hello"));

        verify(chatMessageRepository, never()).save(any());
        verify(aiTarotService, never()).generateInterpretation(any());
    }

    // ==========================================================
    // Finding 2: Current message not duplicated in history
    // ==========================================================

    @Test
    @DisplayName("Current user message excluded from conversation history (Tarot flow)")
    void tarotFlow_currentMessageNotDuplicated() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(tarotSession));
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        // Use a FIXED ID for the current message so we can include it in history mock
        UUID currentMsgId = UUID.randomUUID();

        // Override save mock to use this fixed ID
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage msg = invocation.getArgument(0);
                    if (msg.getSenderType() == SenderType.USER) {
                        ReflectionTestUtils.setField(msg, "id", currentMsgId);
                    } else if (msg.getId() == null) {
                        ReflectionTestUtils.setField(msg, "id", UUID.randomUUID());
                    }
                    return msg;
                });

        // History as DB would return AFTER saving current message (DESC order: newest first)
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now().minusSeconds(30);
        Instant t3 = Instant.now(); // current message (just saved, newest)

        // DESC order: current (newest) first, then previous
        List<ChatMessage> allMessagesDesc = List.of(
                createMessage(SenderType.USER, "Câu hỏi hiện tại", t3, currentMsgId), // current
                createMessage(SenderType.AI, "Trả lời trước", t2),
                createMessage(SenderType.USER, "Câu hỏi trước", t1)
        );

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                eq(TEST_SESSION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(allMessagesDesc));

        chatService.sendMessage(TEST_READING_ID, user, "Câu hỏi hiện tại");

        ArgumentCaptor<BuildPromptRequest> promptCaptor = ArgumentCaptor.forClass(BuildPromptRequest.class);
        verify(aiTarotService).generateInterpretation(promptCaptor.capture());
        BuildPromptRequest promptRequest = promptCaptor.getValue();

        // Current message should be userQuestion
        assertEquals("Câu hỏi hiện tại", promptRequest.getUserQuestion());

        // History should contain previous messages but NOT current
        String historyStr = promptRequest.getConversationHistory();
        assertTrue(historyStr.contains("Câu hỏi trước"));
        assertTrue(historyStr.contains("Trả lời trước"));
        assertFalse(historyStr.contains("Câu hỏi hiện tại"),
                "Current user message should NOT appear in conversationHistory");
    }

    @Test
    @DisplayName("Current user message excluded from conversation history (Astrology flow)")
    void astrologyFlow_currentMessageNotDuplicated() {
        when(chatSessionRepository.findByUserIdAndTarotReadingIsNullAndSessionType(
                TEST_USER_ID, SessionType.AI))
                .thenReturn(Optional.of(astroSession));

        UUID currentMsgId = UUID.randomUUID();

        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage msg = invocation.getArgument(0);
                    if (msg.getSenderType() == SenderType.USER) {
                        ReflectionTestUtils.setField(msg, "id", currentMsgId);
                    } else if (msg.getId() == null) {
                        ReflectionTestUtils.setField(msg, "id", UUID.randomUUID());
                    }
                    return msg;
                });

        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now().minusSeconds(30);
        Instant t3 = Instant.now();

        // DESC order: current first
        List<ChatMessage> allMessagesDesc = List.of(
                createMessage(SenderType.USER, "Còn tài chính?", t3, currentMsgId), // current
                createMessage(SenderType.AI, "The Tower...", t2),
                createMessage(SenderType.USER, "Công việc?", t1)
        );

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                eq(ASTRO_SESSION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(allMessagesDesc));
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        chatService.sendMessage(null, user, "Còn tài chính?");

        ArgumentCaptor<BuildPromptRequest> promptCaptor = ArgumentCaptor.forClass(BuildPromptRequest.class);
        verify(aiTarotService).generateInterpretation(promptCaptor.capture());
        BuildPromptRequest promptRequest = promptCaptor.getValue();

        assertEquals("Còn tài chính?", promptRequest.getUserQuestion());
        String historyStr = promptRequest.getConversationHistory();
        assertTrue(historyStr.contains("Công việc?"));
        assertTrue(historyStr.contains("The Tower..."));
        assertFalse(historyStr.contains("Còn tài chính?"),
                "Current user message should NOT appear in conversationHistory");
    }

    // ==========================================================
    // Finding 3: Chronological ordering
    // ==========================================================

    @Test
    @DisplayName("History is ordered chronologically (oldest first) in formatted output")
    void history_chronologicalOrdering() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(tarotSession));
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        // Messages in DESC order (newest first) as repository would return
        Instant t1 = Instant.now().minusSeconds(90);
        Instant t2 = Instant.now().minusSeconds(60);
        Instant t3 = Instant.now().minusSeconds(30);
        Instant t4 = Instant.now();

        List<ChatMessage> messagesDesc = List.of(
                createMessage(SenderType.USER, "Q4", t4),   // newest
                createMessage(SenderType.AI, "A3", t3),
                createMessage(SenderType.USER, "Q3", t2),
                createMessage(SenderType.AI, "A2", t1)     // oldest
        );

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                eq(TEST_SESSION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(messagesDesc));

        chatService.sendMessage(TEST_READING_ID, user, "Q5");

        ArgumentCaptor<BuildPromptRequest> promptCaptor = ArgumentCaptor.forClass(BuildPromptRequest.class);
        verify(aiTarotService).generateInterpretation(promptCaptor.capture());
        BuildPromptRequest promptRequest = promptCaptor.getValue();

        String historyStr = promptRequest.getConversationHistory();
        // After reversal, oldest should appear first
        int idxA2 = historyStr.indexOf("A2");
        int idxQ3 = historyStr.indexOf("Q3");
        int idxA3 = historyStr.indexOf("A3");
        int idxQ4 = historyStr.indexOf("Q4");

        assertTrue(idxA2 >= 0 && idxQ3 >= 0 && idxA3 >= 0 && idxQ4 >= 0);
        assertTrue(idxA2 < idxQ3, "A2 (oldest) should appear before Q3");
        assertTrue(idxQ3 < idxA3, "Q3 should appear before A3");
        assertTrue(idxA3 < idxQ4, "A3 should appear before Q4 (newest)");
    }

    @Test
    @DisplayName("Multiple follow-ups preserve correct sequence")
    void multipleFollowUps_preserveSequence() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(tarotSession));
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        UUID currentMsgId = UUID.randomUUID();
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> {
                    ChatMessage msg = invocation.getArgument(0);
                    if (msg.getSenderType() == SenderType.USER) {
                        ReflectionTestUtils.setField(msg, "id", currentMsgId);
                    } else if (msg.getId() == null) {
                        ReflectionTestUtils.setField(msg, "id", UUID.randomUUID());
                    }
                    return msg;
                });

        // 5 messages in DESC order: U3(current), A2, U2, A1, U1
        Instant base = Instant.now().minusSeconds(100);

        List<ChatMessage> allMessagesDesc = List.of(
                createMessage(SenderType.USER, "USER 3", base.plusSeconds(40), currentMsgId), // current
                createMessage(SenderType.AI, "ASSISTANT 2", base.plusSeconds(30)),
                createMessage(SenderType.USER, "USER 2", base.plusSeconds(20)),
                createMessage(SenderType.AI, "ASSISTANT 1", base.plusSeconds(10)),
                createMessage(SenderType.USER, "USER 1", base)                   // oldest
        );

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                eq(TEST_SESSION_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(allMessagesDesc));

        chatService.sendMessage(TEST_READING_ID, user, "USER 3");

        ArgumentCaptor<BuildPromptRequest> promptCaptor = ArgumentCaptor.forClass(BuildPromptRequest.class);
        verify(aiTarotService).generateInterpretation(promptCaptor.capture());
        BuildPromptRequest promptRequest = promptCaptor.getValue();

        String historyStr = promptRequest.getConversationHistory();
        assertEquals("USER 3", promptRequest.getUserQuestion());

        // History should contain U1, A1, U2, A2 in order (excluding current U3)
        int u1 = historyStr.indexOf("USER 1");
        int a1 = historyStr.indexOf("ASSISTANT 1");
        int u2 = historyStr.indexOf("USER 2");
        int a2 = historyStr.indexOf("ASSISTANT 2");
        int u3 = historyStr.indexOf("USER 3");

        assertTrue(u1 >= 0 && a1 >= 0 && u2 >= 0 && a2 >= 0);
        assertTrue(u1 < a1, "USER 1 before ASSISTANT 1");
        assertTrue(a1 < u2, "ASSISTANT 1 before USER 2");
        assertTrue(u2 < a2, "USER 2 before ASSISTANT 2");
        assertTrue(u3 < 0, "USER 3 (current) should NOT be in history");
    }

    // ==========================================================
    // Finding 5: Session identity consistency
    // ==========================================================

    @Test
    @DisplayName("Tarot flow uses reading-linked session")
    void tarotFlow_usesReadingLinkedSession() {
        when(tarotReadingRepository.findById(TEST_READING_ID))
                .thenReturn(Optional.of(reading));
        when(chatSessionRepository.findByTarotReadingId(TEST_READING_ID))
                .thenReturn(Optional.of(tarotSession));
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        ChatResponse result = chatService.sendMessage(TEST_READING_ID, user, "Hello");

        assertEquals(TEST_SESSION_ID, result.getSessionId());
        verify(chatSessionRepository, never())
                .findByUserIdAndTarotReadingIsNullAndSessionType(any(), any());
    }

    @Test
    @DisplayName("Astrology flow uses user-scoped session (no reading)")
    void astrologyFlow_usesUserScopedSession() {
        when(chatSessionRepository.findByUserIdAndTarotReadingIsNullAndSessionType(
                TEST_USER_ID, SessionType.AI))
                .thenReturn(Optional.of(astroSession));
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        ChatResponse result = chatService.sendMessage(null, user, "Hello");

        assertEquals(ASTRO_SESSION_ID, result.getSessionId());
        verify(chatSessionRepository, never())
                .findByTarotReadingId(any());
    }

    @Test
    @DisplayName("Astrology session creation sets correct fields")
    void astrologySession_creationFields() {
        when(chatSessionRepository.findByUserIdAndTarotReadingIsNullAndSessionType(
                TEST_USER_ID, SessionType.AI))
                .thenReturn(Optional.empty());
        when(aiTarotService.generateInterpretation(any()))
                .thenReturn(llmResponse);

        chatService.sendMessage(null, user, "Hello");

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(chatSessionRepository, times(2)).save(sessionCaptor.capture());
        // First save is session creation, second is lastMessageAt update
        ChatSession savedSession = sessionCaptor.getAllValues().get(0);

        assertEquals(TEST_USER_ID, savedSession.getUser().getId());
        assertNull(savedSession.getTarotReading(), "Astrology session should have null tarotReading");
        assertEquals(SessionType.AI, savedSession.getSessionType());
        assertEquals(ChatStatus.ACTIVE, savedSession.getStatus());
    }
}
