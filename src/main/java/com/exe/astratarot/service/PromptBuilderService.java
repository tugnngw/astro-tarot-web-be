package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;

/**
 * Service responsible for constructing the complete prompt for AI Tarot reading.
 *
 * Input: BuildPromptRequest (containing user question, astrology context, drawn cards)
 * Output: LLMRequest with systemInstruction + role-separated messages
 *
 * The PromptBuilderService:
 * - Accepts pre-enriched, pre-calculated data
 * - Composes the system instruction from persona, reading context, cards, astrology
 * - Maps conversation history to USER/ASSISTANT messages
 * - Appends current user question as final USER message
 * - Does NOT access repositories, databases, or external services
 * - Does NOT call LLM providers (Gemini, OpenAI)
 * - Does NOT manage persistence, SSE, or reading storage
 */
public interface PromptBuilderService {

    /**
     * Builds a structured LLMRequest for AI Tarot reading.
     *
     * <p>Preferred method — produces role-separated request that providers
     * map to their native API format (Gemini systemInstruction + contents,
     * OpenAI messages with role field).
     *
     * @param request BuildPromptRequest containing:
     *                - userQuestion: the current user question (becomes last USER message)
     *                - conversationHistory: formatted history string "USER: ...\nAI: ...\n"
     *                - astrologyContext: pre-calculated astrology data
     *                - drawnCardDetails: enriched tarot cards with metadata
     *                - originalQuestion: (optional) context anchor for continuation
     *                - spreadName: (optional) name of the spread
     * @return LLMRequest with systemInstruction + messages list
     * @throws IllegalArgumentException if request validation fails
     */
    LLMRequest buildLLMRequest(BuildPromptRequest request);

    /**
     * Builds a flat prompt string for AI Tarot reading.
     *
     * @deprecated Prefer {@link #buildLLMRequest(BuildPromptRequest)} which
     *             preserves role separation. This method remains for backward
     *             compatibility and testing.
     *
     * @param request BuildPromptRequest containing:
     *                - userQuestion: the user's question
     *                - astrologyContext: pre-calculated astrology data
     *                - drawnCardDetails: enriched tarot cards with metadata
     *                - spreadName: (optional) name of the spread
     * @return A formatted String prompt ready for LLM consumption
     * @throws IllegalArgumentException if request validation fails
     */
    @Deprecated
    String buildPrompt(BuildPromptRequest request);
}