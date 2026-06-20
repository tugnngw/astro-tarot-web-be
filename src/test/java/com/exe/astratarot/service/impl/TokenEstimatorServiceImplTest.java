package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.domain.entity.ChatMessage;
import com.exe.astratarot.domain.enums.SenderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TokenEstimatorServiceImpl.
 *
 * Tests token estimation accuracy using approximation: tokens ≈ chars / 4
 */
@DisplayName("TokenEstimatorService")
public class TokenEstimatorServiceImplTest {

    private TokenEstimatorServiceImpl tokenEstimator;

    @BeforeEach
    void setUp() {
        tokenEstimator = new TokenEstimatorServiceImpl();
    }

    @Test
    @DisplayName("estimateTokens with null text returns 0")
    void testEstimateTokensNull() {
        int tokens = tokenEstimator.estimateTokens(null);
        assertEquals(0, tokens);
    }

    @Test
    @DisplayName("estimateTokens with empty text returns 0")
    void testEstimateTokensEmpty() {
        int tokens = tokenEstimator.estimateTokens("");
        assertEquals(0, tokens);
    }

    @Test
    @DisplayName("estimateTokens with short text")
    void testEstimateTokensShort() {
        // 4 characters = 1 token
        int tokens = tokenEstimator.estimateTokens("test");
        assertEquals(1, tokens);
    }

    @Test
    @DisplayName("estimateTokens with medium text")
    void testEstimateTokensMedium() {
        // 100 characters ≈ 25 tokens
        String text = "a".repeat(100);
        int tokens = tokenEstimator.estimateTokens(text);
        assertEquals(25, tokens);
    }

    @Test
    @DisplayName("estimateTokens with long text")
    void testEstimateTokensLong() {
        // 1000 characters ≈ 250 tokens
        String text = "a".repeat(1000);
        int tokens = tokenEstimator.estimateTokens(text);
        assertEquals(250, tokens);
    }

    @Test
    @DisplayName("estimateConversationTokens with null list returns 0")
    void testEstimateConversationTokensNull() {
        int tokens = tokenEstimator.estimateConversationTokens(null);
        assertEquals(0, tokens);
    }

    @Test
    @DisplayName("estimateConversationTokens with empty list returns 0")
    void testEstimateConversationTokensEmpty() {
        int tokens = tokenEstimator.estimateConversationTokens(new ArrayList<>());
        assertEquals(0, tokens);
    }

    @Test
    @DisplayName("estimateConversationTokens with single message")
    void testEstimateConversationTokensSingle() {
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage msg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .content("Hello world")
                .senderType(SenderType.USER)
                .createdAt(Instant.now())
                .build();
        messages.add(msg);

        int tokens = tokenEstimator.estimateConversationTokens(messages);
        // "Hello world" = 11 chars ≈ 2 tokens + 5 overhead = 7 tokens
        assertTrue(tokens >= 7 && tokens <= 8);
    }

    @Test
    @DisplayName("estimateConversationTokens with multiple messages")
    void testEstimateConversationTokensMultiple() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ChatMessage msg = ChatMessage.builder()
                    .id(UUID.randomUUID())
                    .content("a".repeat(100))
                    .senderType(i % 2 == 0 ? SenderType.USER : SenderType.AI)
                    .createdAt(Instant.now())
                    .build();
            messages.add(msg);
        }

        int tokens = tokenEstimator.estimateConversationTokens(messages);
        // 3 messages * (100/4 + 5) = 3 * 30 = 90 tokens
        assertTrue(tokens >= 85 && tokens <= 95);
    }

    @Test
    @DisplayName("estimatePromptTokens throws IllegalArgumentException for null request")
    void testEstimatePromptTokensNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            tokenEstimator.estimatePromptTokens(null);
        });
    }

    @Test
    @DisplayName("estimatePromptTokens with minimal request")
    void testEstimatePromptTokensMinimal() {
        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("What should I do?")
                .drawnCardDetails(List.of())
                .build();

        int tokens = tokenEstimator.estimatePromptTokens(request);
        // Should include: formatting overhead (500) + question tokens
        assertTrue(tokens >= 500);
    }

    @Test
    @DisplayName("estimatePromptTokens with astrology context")
    void testEstimatePromptTokensWithAstrology() {
        AstrologyContextDTO astrology = AstrologyContextDTO.builder()
                .birthDate(java.time.LocalDate.of(1990, 1, 1))
                .sunSign("Capricorn")
                .element("Earth")
                .modality("Cardinal")
                .build();

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("What should I do?")
                .astrologyContext(astrology)
                .drawnCardDetails(List.of())
                .build();

        int tokens = tokenEstimator.estimatePromptTokens(request);
        // Should include: formatting (500) + astrology (~1500) + question
        assertTrue(tokens >= 2000);
    }

    @Test
    @DisplayName("estimatePromptTokens with conversation history")
    void testEstimatePromptTokensWithHistory() {
        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Follow-up question?")
                .conversationHistory("USER: Hello\nAI: Hi there\n")
                .drawnCardDetails(List.of())
                .build();

        int tokens = tokenEstimator.estimatePromptTokens(request);
        // Should include: formatting (500) + history + question
        assertTrue(tokens >= 500);
    }

    @Test
    @DisplayName("estimatePromptTokens with cards")
    void testEstimatePromptTokensWithCards() {
        List<DrawnCardDetailDTO> cards = List.of(
                DrawnCardDetailDTO.builder()
                        .cardId(UUID.randomUUID())
                        .cardName("The Fool")
                        .cardNumber(0)
                        .position((short) 1)
                        .reversed(false)
                        .build()
        );

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("What should I do?")
                .drawnCardDetails(cards)
                .build();

        int tokens = tokenEstimator.estimatePromptTokens(request);
        // Should include: formatting (500) + cards + question
        assertTrue(tokens >= 500);
    }
}
