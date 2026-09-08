package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.entity.AIUsageLog;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.repository.AIUsageLogRepository;
import com.exe.astratarot.service.AIUsageTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implementation of AIUsageTrackingService.
 *
 * Tracks all AI API calls (Gemini, etc.) and calculates estimated costs.
 *
 * Pricing Model (Gemini Flash):
 * - Input: $0.10 per 1M tokens
 * - Output: $0.40 per 1M tokens
 *
 * Gracefully handles missing token data:
 * - If token counts unavailable, cost is null
 * - Logs are persisted even if cost calculation fails
 * - Failures in tracking do not interrupt the main request flow
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIUsageTrackingServiceImpl implements AIUsageTrackingService {

    private final AIUsageLogRepository aiUsageLogRepository;

    /**
     * Gemini Flash pricing (USD per 1M tokens).
     */
    private static final BigDecimal GEMINI_FLASH_INPUT_PRICE = new BigDecimal("0.10");
    private static final BigDecimal GEMINI_FLASH_OUTPUT_PRICE = new BigDecimal("0.40");
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    @Override
    public AIUsageLog logReadingGeneration(User user, TarotReading reading, String provider, String model,
                                          LLMTokenUsage tokenUsage, Long latencyMs) {
        try {
            Integer promptTokens = tokenUsage != null ? tokenUsage.getPromptTokens() : null;
            Integer completionTokens = tokenUsage != null ? tokenUsage.getCompletionTokens() : null;
            Integer totalTokens = tokenUsage != null ? tokenUsage.getTotalTokens() : null;

            BigDecimal estimatedCost = calculateCostForProvider(provider, promptTokens, completionTokens);

            AIUsageLog usageLog = AIUsageLog.builder()
                    .user(user)
                    .reading(reading)
                    .chatSession(null)
                    .provider(provider)
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .estimatedCostUsd(estimatedCost)
                    .latencyMs(latencyMs != null ? latencyMs.intValue() : null)
                    .build();

            AIUsageLog saved = aiUsageLogRepository.save(usageLog);
            log.info("Logged reading generation: readingId={}, provider={}, model={}, tokens={}, cost={}",
                    reading.getId(), provider, model, totalTokens, estimatedCost);
            return saved;

        } catch (Exception e) {
            // Log error but don't fail the request
            log.error("Failed to log reading generation usage: readingId={}, error={}", reading.getId(), e.getMessage(), e);
            return null;
        }
    }

    @Override
    public AIUsageLog logChatContinuation(User user, ChatSession chatSession, String provider, String model,
                                         LLMTokenUsage tokenUsage, Long latencyMs) {
        try {
            Integer promptTokens = tokenUsage != null ? tokenUsage.getPromptTokens() : null;
            Integer completionTokens = tokenUsage != null ? tokenUsage.getCompletionTokens() : null;
            Integer totalTokens = tokenUsage != null ? tokenUsage.getTotalTokens() : null;

            BigDecimal estimatedCost = calculateCostForProvider(provider, promptTokens, completionTokens);

            AIUsageLog usageLog = AIUsageLog.builder()
                    .user(user)
                    .reading(null)
                    .chatSession(chatSession)
                    .provider(provider)
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .estimatedCostUsd(estimatedCost)
                    .latencyMs(latencyMs != null ? latencyMs.intValue() : null)
                    .build();

            AIUsageLog saved = aiUsageLogRepository.save(usageLog);
            log.info("Logged chat continuation usage: sessionId={}, provider={}, model={}, tokens={}, cost={}",
                    chatSession != null ? chatSession.getId() : "null", provider, model, totalTokens, estimatedCost);
            return saved;

        } catch (Exception e) {
            // Log error but don't fail the request
            log.error("Failed to log chat continuation usage: sessionId={}, error={}", chatSession != null ? chatSession.getId() : "null", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public BigDecimal calculateGeminiFlashCost(Integer promptTokens, Integer completionTokens) {
        if (promptTokens == null || completionTokens == null) {
            return null;
        }

        try {
            BigDecimal inputCost = new BigDecimal(promptTokens)
                    .multiply(GEMINI_FLASH_INPUT_PRICE)
                    .divide(MILLION, 6, RoundingMode.HALF_UP);

            BigDecimal outputCost = new BigDecimal(completionTokens)
                    .multiply(GEMINI_FLASH_OUTPUT_PRICE)
                    .divide(MILLION, 6, RoundingMode.HALF_UP);

            return inputCost.add(outputCost);

        } catch (Exception e) {
            log.warn("Failed to calculate Gemini Flash cost: promptTokens={}, completionTokens={}, error={}",
                    promptTokens, completionTokens, e.getMessage());
            return null;
        }
    }

    /**
     * Calculates estimated cost based on provider.
     *
     * @param provider provider name (e.g., "Gemini")
     * @param promptTokens number of prompt tokens (may be null)
     * @param completionTokens number of completion tokens (may be null)
     * @return estimated cost in USD, or null if unavailable
     */
    private BigDecimal calculateCostForProvider(String provider, Integer promptTokens, Integer completionTokens) {
        if (provider == null || promptTokens == null || completionTokens == null) {
            return null;
        }

        switch (provider.toLowerCase()) {
            case "gemini":
            case "gemini-flash":
                return calculateGeminiFlashCost(promptTokens, completionTokens);
            default:
                log.warn("Unknown provider for cost calculation: {}", provider);
                return null;
        }
    }
}
