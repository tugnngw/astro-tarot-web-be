package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.entity.AIUsageLog;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;

import java.util.UUID;

/**
 * Service for tracking AI service usage and cost estimation.
 *
 * Responsibilities:
 * - Log AI API calls (Gemini, etc.)
 * - Calculate estimated costs based on token usage
 * - Support cost analytics and usage monitoring
 *
 * Gracefully handles missing token data (nullable values) to ensure
 * failures in cost calculation don't interrupt the main request flow.
 */
public interface AIUsageTrackingService {

    /**
     * Log AI usage after initial reading generation.
     *
     * @param user the authenticated user
     * @param reading the generated TarotReading
     * @param provider AI provider name (e.g., "Gemini")
     * @param model AI model name (e.g., "gemini-1.5-flash")
     * @param tokenUsage token usage data (may be null)
     * @param latencyMs latency in milliseconds (may be null)
     * @return persisted AIUsageLog entity
     */
    AIUsageLog logReadingGeneration(User user, TarotReading reading, String provider, String model,
                                   LLMTokenUsage tokenUsage, Long latencyMs);

    /**
     * Log AI usage after chat continuation.
     *
     * @param user the authenticated user
     * @param chatSession the ChatSession for the continuation (may be null for astrology-only chat)
     * @param provider AI provider name (e.g., "Gemini")
     * @param model AI model name (e.g., "gemini-1.5-flash")
     * @param tokenUsage token usage data (may be null)
     * @param latencyMs latency in milliseconds (may be null)
     * @return persisted AIUsageLog entity
     */
    AIUsageLog logChatContinuation(User user, ChatSession chatSession, String provider, String model,
                                  LLMTokenUsage tokenUsage, Long latencyMs);

    /**
     * Calculate estimated cost for Gemini Flash model.
     *
     * Pricing (as of June 2026):
     * - Input: $0.10 per 1M tokens
     * - Output: $0.40 per 1M tokens
     *
     * @param promptTokens number of prompt tokens (may be null)
     * @param completionTokens number of completion tokens (may be null)
     * @return estimated cost in USD, or null if token counts unavailable
     */
    java.math.BigDecimal calculateGeminiFlashCost(Integer promptTokens, Integer completionTokens);
}
