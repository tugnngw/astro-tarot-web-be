package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.entity.ChatMessage;
import com.exe.astratarot.service.TokenEstimatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementation of TokenEstimatorService.
 *
 * Provides token estimation without external tokenizer libraries.
 * Uses approximation: tokens ≈ characters / 4
 *
 * Rationale:
 * - Simple, fast, no external dependencies
 * - Suitable for MVP token budgeting (±10% accuracy for English text)
 * - Allows conversation history trimming to stay within token budgets
 * - Production use should integrate proper tokenizer (e.g., tiktoken equivalent)
 */
@Service
@Slf4j
public class TokenEstimatorServiceImpl implements TokenEstimatorService {

    /**
     * Approximation factor: average English word is ~4 characters, ~1.3 tokens per word.
     * Therefore: tokens ≈ chars / 4 * 1.3 ≈ chars / 3.08
     * Using 4 for simplicity (slightly conservative estimate).
     */
    private static final int CHARS_PER_TOKEN = 4;

    /**
     * Overhead tokens for formatting, section headers, etc.
     * System instructions, prompt structure adds ~500 tokens per complete prompt.
     */
    private static final int FORMATTING_OVERHEAD = 500;

    /**
     * Tokens per chat message (formatting + metadata).
     * Each message adds "USER: " or "AI: " prefix + newlines.
     */
    private static final int MESSAGE_OVERHEAD = 5;

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    @Override
    public int estimateConversationTokens(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int totalTokens = 0;
        for (ChatMessage msg : messages) {
            // Content tokens
            totalTokens += estimateTokens(msg.getContent());
            // Message formatting overhead (sender label, newlines)
            totalTokens += MESSAGE_OVERHEAD;
        }

        log.debug("Estimated {} tokens for {} messages", totalTokens, messages.size());
        return totalTokens;
    }

    @Override
    public int estimatePromptTokens(BuildPromptRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("BuildPromptRequest cannot be null");
        }

        int totalTokens = FORMATTING_OVERHEAD; // System instructions, section headers

        // User question
        totalTokens += estimateTokens(request.getUserQuestion());

        // Original question (if present, for follow-ups)
        if (request.getOriginalQuestion() != null && !request.getOriginalQuestion().isBlank()) {
            totalTokens += estimateTokens(request.getOriginalQuestion());
        }

        // Astrology context (if present)
        if (request.getAstrologyContext() != null) {
            totalTokens += estimateAstrologyContextTokens(request.getAstrologyContext());
        }

        // Drawn card details
        if (request.getDrawnCardDetails() != null && !request.getDrawnCardDetails().isEmpty()) {
            totalTokens += estimateTokens(request.getDrawnCardDetails().toString());
        }

        // Conversation history (if present)
        if (request.getConversationHistory() != null && !request.getConversationHistory().isBlank()) {
            totalTokens += estimateTokens(request.getConversationHistory());
        }

        log.debug("Estimated {} tokens for complete prompt", totalTokens);
        return totalTokens;
    }

    /**
     * Estimates tokens for AstrologyContextDTO.
     * Includes birth data, zodiac signs, planetary positions, aspects, transits.
     */
    private int estimateAstrologyContextTokens(Object astrologyContext) {
        if (astrologyContext == null) {
            return 0;
        }
        // Rough estimate: astrology context is ~1500-2000 tokens
        // Includes: birth data, zodiac signs, planets, aspects, transits
        return 1500;
    }
}
