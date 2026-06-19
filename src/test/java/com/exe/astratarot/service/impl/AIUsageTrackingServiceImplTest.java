package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.entity.AIUsageLog;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.repository.AIUsageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AIUsageTrackingServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIUsageTrackingService")
public class AIUsageTrackingServiceImplTest {

    @Mock
    private AIUsageLogRepository aiUsageLogRepository;

    private AIUsageTrackingServiceImpl aiUsageTrackingService;

    @BeforeEach
    void setUp() {
        aiUsageTrackingService = new AIUsageTrackingServiceImpl(aiUsageLogRepository);
    }

    @Test
    @DisplayName("calculateGeminiFlashCost with valid tokens returns correct cost")
    void testCalculateGeminiFlashCostValid() {
        // 1M prompt tokens: 1000000 * $0.10 / 1M = $0.10
        // 1M completion tokens: 1000000 * $0.40 / 1M = $0.40
        // Total: $0.50
        BigDecimal cost = aiUsageTrackingService.calculateGeminiFlashCost(1000000, 1000000);

        assertNotNull(cost);
        assertEquals(0, cost.compareTo(new BigDecimal("0.50")));
    }

    @Test
    @DisplayName("calculateGeminiFlashCost with null prompt tokens returns null")
    void testCalculateGeminiFlashCostNullPromptTokens() {
        BigDecimal cost = aiUsageTrackingService.calculateGeminiFlashCost(null, 1000000);
        assertNull(cost);
    }

    @Test
    @DisplayName("calculateGeminiFlashCost with null completion tokens returns null")
    void testCalculateGeminiFlashCostNullCompletionTokens() {
        BigDecimal cost = aiUsageTrackingService.calculateGeminiFlashCost(1000000, null);
        assertNull(cost);
    }

    @Test
    @DisplayName("calculateGeminiFlashCost with small token counts")
    void testCalculateGeminiFlashCostSmallTokens() {
        // 100 prompt tokens: 100 * $0.10 / 1M = $0.00001
        // 200 completion tokens: 200 * $0.40 / 1M = $0.00008
        // Total: $0.00009
        BigDecimal cost = aiUsageTrackingService.calculateGeminiFlashCost(100, 200);

        assertNotNull(cost);
        assertTrue(cost.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(cost.compareTo(new BigDecimal("0.0001")) < 0);
    }

    @Test
    @DisplayName("logReadingGeneration persists usage with all fields")
    void testLogReadingGenerationSuccess() {
        User user = User.builder().id(UUID.randomUUID()).build();
        TarotReading reading = TarotReading.builder().id(UUID.randomUUID()).build();
        LLMTokenUsage tokenUsage = LLMTokenUsage.builder()
                .promptTokens(100)
                .completionTokens(200)
                .totalTokens(300)
                .build();

        AIUsageLog mockLog = AIUsageLog.builder()
                .id(UUID.randomUUID())
                .user(user)
                .reading(reading)
                .provider("Gemini")
                .model("gemini-1.5-flash")
                .promptTokens(100)
                .completionTokens(200)
                .totalTokens(300)
                .estimatedCostUsd(new BigDecimal("0.00012"))
                .latencyMs(150)
                .build();

        when(aiUsageLogRepository.save(any(AIUsageLog.class))).thenReturn(mockLog);

        AIUsageLog result = aiUsageTrackingService.logReadingGeneration(
                user, reading, "Gemini", "gemini-1.5-flash", tokenUsage, 150L);

        assertNotNull(result);
        assertEquals(reading.getId(), result.getReading().getId());
        assertEquals("Gemini", result.getProvider());
        assertEquals(300, result.getTotalTokens());
    }

    @Test
    @DisplayName("logReadingGeneration with null token usage")
    void testLogReadingGenerationNullTokenUsage() {
        User user = User.builder().id(UUID.randomUUID()).build();
        TarotReading reading = TarotReading.builder().id(UUID.randomUUID()).build();

        AIUsageLog mockLog = AIUsageLog.builder()
                .id(UUID.randomUUID())
                .user(user)
                .reading(reading)
                .provider("Gemini")
                .model("gemini-1.5-flash")
                .promptTokens(null)
                .completionTokens(null)
                .totalTokens(null)
                .estimatedCostUsd(null)
                .latencyMs(null)
                .build();

        when(aiUsageLogRepository.save(any(AIUsageLog.class))).thenReturn(mockLog);

        AIUsageLog result = aiUsageTrackingService.logReadingGeneration(
                user, reading, "Gemini", "gemini-1.5-flash", null, null);

        assertNotNull(result);
        assertNull(result.getTotalTokens());
        assertNull(result.getEstimatedCostUsd());
    }

    @Test
    @DisplayName("logChatContinuation persists usage with chat session")
    void testLogChatContinuationSuccess() {
        User user = User.builder().id(UUID.randomUUID()).build();
        ChatSession chatSession = ChatSession.builder().id(UUID.randomUUID()).build();
        LLMTokenUsage tokenUsage = LLMTokenUsage.builder()
                .promptTokens(50)
                .completionTokens(150)
                .totalTokens(200)
                .build();

        AIUsageLog mockLog = AIUsageLog.builder()
                .id(UUID.randomUUID())
                .user(user)
                .chatSession(chatSession)
                .provider("Gemini")
                .model("gemini-1.5-flash")
                .promptTokens(50)
                .completionTokens(150)
                .totalTokens(200)
                .estimatedCostUsd(new BigDecimal("0.00008"))
                .latencyMs(120)
                .build();

        when(aiUsageLogRepository.save(any(AIUsageLog.class))).thenReturn(mockLog);

        AIUsageLog result = aiUsageTrackingService.logChatContinuation(
                user, chatSession, "Gemini", "gemini-1.5-flash", tokenUsage, 120L);

        assertNotNull(result);
        assertEquals(chatSession.getId(), result.getChatSession().getId());
        assertNull(result.getReading());
        assertEquals(200, result.getTotalTokens());
    }
}
