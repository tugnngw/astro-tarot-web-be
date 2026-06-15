package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;

/**
 * Service responsible for orchestrating the AI interpretation step of a Tarot reading.
 *
 * This service connects three existing components:
 * 1. PromptBuilderService: Constructs the prompt from BuildPromptRequest
 * 2. LLMProvider: Sends the prompt to the LLM and retrieves the response
 * 3. LLMResponse: Returns the raw AI-generated interpretation
 *
 * Responsibilities:
 * - Accept a fully prepared BuildPromptRequest
 * - Build the prompt using PromptBuilderService
 * - Call LLMProvider to generate the AI response
 * - Return the raw LLMResponse without modification or persistence
 *
 * This layer is NOT responsible for:
 * - Creating or persisting TarotReading entities
 * - Managing reading history or user data
 * - Parsing or structuring the AI response
 * - Card drawing or astrology calculations
 *
 * The AI interpretation pipeline is independent and can be tested separately.
 */
public interface AITarotService {

    /**
     * Generates an AI interpretation for a Tarot reading.
     *
     * @param request BuildPromptRequest containing:
     *                - userQuestion: The user's question for the reading
     *                - astrologyContext: Pre-calculated astrology data
     *                - drawnCardDetails: Enriched tarot cards with metadata
     *                - spreadName: (optional) Name of the tarot spread
     * @return LLMResponse containing:
     *         - content: The AI-generated interpretation text
     *         - modelInfo: The model used (e.g., "gemini-pro")
     *         - tokenUsage: Token usage statistics
     * @throws IllegalArgumentException if request validation fails
     * @throws LLMProviderException if LLM provider call fails
     */
    LLMResponse generateInterpretation(BuildPromptRequest request);
}
