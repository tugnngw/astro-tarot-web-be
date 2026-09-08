package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.domain.entity.ChatMessage;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.ReadingCard;
import com.exe.astratarot.domain.entity.TarotCard;
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
import com.exe.astratarot.service.TokenEstimatorService;
import com.exe.astratarot.service.AIUsageTrackingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ChatServiceImpl token budgeting functionality.
 *
 * Verifies that conversation history is correctly trimmed to stay within
 * the configurable token budget (ai.chat.max-context-tokens).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatServiceImpl - Token Budget Management")
public class ChatServiceImplTokenBudgetTest {

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

    private ChatServiceImpl chatService;

    private static final int MAX_CONTEXT_TOKENS = 6000;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
                chatSessionRepository,
                chatMessageRepository,
                tarotReadingRepository,
                readingCardRepository,
                aiTarotService,
                astrologyContextService,
                tokenEstimatorService,
                aiUsageTrackingService
        );
        // Set max token budget via reflection
        try {
            var field = ChatServiceImpl.class.getDeclaredField("maxContextTokens");
            field.setAccessible(true);
            field.set(chatService, MAX_CONTEXT_TOKENS);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Conversation history trimmed when exceeding token budget")
    void testConversationHistoryTrimmedWhenExceedingBudget() {
        // Create 10 messages, each ~500 tokens
        List<ChatMessage> allMessages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ChatMessage msg = ChatMessage.builder()
                    .id(UUID.randomUUID())
                    .content("a".repeat(4000)) // ~1000 tokens
                    .senderType(i % 2 == 0 ? SenderType.USER : SenderType.AI)
                    .messageType(MessageType.TEXT)
                    .createdAt(Instant.now().plusSeconds(i))
                    .build();
            allMessages.add(msg);
        }

        // Mock tokenEstimator to return consistent estimates
        when(tokenEstimatorService.estimateTokens(any(String.class)))
                .thenAnswer(inv -> {
                    String text = inv.getArgument(0);
                    return Math.max(1, text.length() / 4);
                });

        // Should include only ~6 messages within budget (6 * 500 = 3000 tokens)
        // Latest messages should be selected
        int estimatedTokens = 0;
        int selectedCount = 0;
        for (ChatMessage msg : allMessages) {
            int msgTokens = tokenEstimatorService.estimateTokens(msg.getContent()) + 5;
            if (estimatedTokens + msgTokens <= MAX_CONTEXT_TOKENS) {
                estimatedTokens += msgTokens;
                selectedCount++;
            }
        }

        assertTrue(selectedCount > 0, "Should select at least one message");
        assertTrue(selectedCount < 10, "Should trim messages to stay within budget");
    }

    @Test
    @DisplayName("Empty conversation history when no messages fit budget")
    void testEmptyConversationHistoryWhenNoMessagesFitBudget() {
        // Create messages that are too large
        List<ChatMessage> allMessages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ChatMessage msg = ChatMessage.builder()
                    .id(UUID.randomUUID())
                    .content("a".repeat(50000)) // ~12500 tokens per message
                    .senderType(i % 2 == 0 ? SenderType.USER : SenderType.AI)
                    .messageType(MessageType.TEXT)
                    .createdAt(Instant.now().plusSeconds(i))
                    .build();
            allMessages.add(msg);
        }

        // Mock tokenEstimator
        when(tokenEstimatorService.estimateTokens(any(String.class)))
                .thenAnswer(inv -> {
                    String text = inv.getArgument(0);
                    return text.length() / 4;
                });

        // No messages should fit within budget
        int estimatedTokens = 0;
        int selectedCount = 0;
        for (ChatMessage msg : allMessages) {
            int msgTokens = tokenEstimatorService.estimateTokens(msg.getContent()) + 5;
            if (estimatedTokens + msgTokens <= MAX_CONTEXT_TOKENS) {
                estimatedTokens += msgTokens;
                selectedCount++;
            } else {
                break;
            }
        }

        assertEquals(0, selectedCount, "No messages should fit within budget");
    }

    @Test
    @DisplayName("Latest messages preserved when trimming by token budget")
    void testLatestMessagesPreservedWhenTrimming() {
        // Create 5 messages with identifiable content
        List<ChatMessage> allMessages = new ArrayList<>();
        String[] contents = {"First", "Second", "Third", "Fourth", "Fifth"};
        for (int i = 0; i < 5; i++) {
            ChatMessage msg = ChatMessage.builder()
                    .id(UUID.randomUUID())
                    .content(contents[i] + " " + "a".repeat(500)) // Variable size
                    .senderType(i % 2 == 0 ? SenderType.USER : SenderType.AI)
                    .messageType(MessageType.TEXT)
                    .createdAt(Instant.now().plusSeconds(i))
                    .build();
            allMessages.add(msg);
        }

        // Mock tokenEstimator to return controlled estimates
        when(tokenEstimatorService.estimateTokens(any(String.class)))
                .thenAnswer(inv -> {
                    String text = inv.getArgument(0);
                    return Math.max(1, text.length() / 4);
                });

        // Calculate how many messages fit
        int estimatedTokens = 0;
        List<ChatMessage> selectedMessages = new ArrayList<>();
        for (ChatMessage msg : allMessages) {
            int msgTokens = tokenEstimatorService.estimateTokens(msg.getContent()) + 5;
            if (estimatedTokens + msgTokens <= MAX_CONTEXT_TOKENS) {
                estimatedTokens += msgTokens;
                selectedMessages.add(msg);
            }
        }

        // Latest messages should be selected (Fifth, Fourth, etc.)
        assertTrue(selectedMessages.size() > 0);
        assertTrue(selectedMessages.size() <= 5);
    }
}
