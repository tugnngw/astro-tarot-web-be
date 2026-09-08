package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;

/**
 * Service responsible for constructing the complete prompt for AI Tarot reading.
 *
 * Input: BuildPromptRequest (containing user question, astrology context, drawn cards)
 * Output: A single String representing the complete prompt ready for the LLM
 *
 * The PromptBuilderService:
 * - Accepts pre-enriched, pre-calculated data
 * - Composes the prompt from distinct sections (system instructions, context, cards, question, response guidance)
 * - Returns a formatted string prompt
 * - Does NOT access repositories, databases, or external services
 * - Does NOT call LLM providers (Gemini, OpenAI)
 * - Does NOT manage persistence, SSE, or reading storage
 */
public interface PromptBuilderService {

    /**
     * Builds a complete prompt for AI Tarot reading.
     *
     * @param request BuildPromptRequest containing:
     *                - userQuestion: the user's question
     *                - astrologyContext: pre-calculated astrology data
     *                - drawnCardDetails: enriched tarot cards with metadata
     *                - spreadName: (optional) name of the spread
     * @return A formatted String prompt ready for LLM consumption
     * @throws IllegalArgumentException if request validation fails
     */
    String buildPrompt(BuildPromptRequest request);
}