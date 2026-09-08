package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.entity.ChatMessage;

import java.util.List;

/**
 * Service for estimating token counts without external tokenizer libraries.
 *
 * Uses a simple approximation: tokens ≈ characters / 4
 *
 * This is suitable for MVP token budgeting and conversation history trimming.
 * For production use with strict token limits, consider integrating a proper tokenizer.
 *
 * Motivation: Avoid external tokenizer dependencies while maintaining reasonable token budgeting.
 */
public interface TokenEstimatorService {

    /**
     * Estimates token count for a plain text string.
     *
     * Approximation: tokens = chars / 4
     * This provides a reasonable estimate for most English text.
     *
     * @param text the text to estimate (may be null)
     * @return estimated token count (0 if text is null or empty)
     */
    int estimateTokens(String text);

    /**
     * Estimates token count for a list of chat messages.
     *
     * Sums the token estimates for all messages, including formatting overhead.
     *
     * @param messages list of chat messages (may be null or empty)
     * @return total estimated token count for all messages
     */
    int estimateConversationTokens(List<ChatMessage> messages);

    /**
     * Estimates token count for a complete prompt request.
     *
     * Combines estimates for:
     * - User question
     * - Astrology context (if present)
     * - Drawn card details
     * - Conversation history (if present)
     * - System instructions and formatting
     *
     * @param request the BuildPromptRequest to estimate
     * @return estimated total token count for the complete prompt
     * @throws IllegalArgumentException if request is null
     */
    int estimatePromptTokens(BuildPromptRequest request);
}
