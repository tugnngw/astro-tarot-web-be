package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.LLMProvider;
import com.exe.astratarot.service.PromptBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Implementation of AITarotService.
 *
 * Orchestrates the AI interpretation pipeline:
 * 1. Builds a prompt from BuildPromptRequest using PromptBuilderService
 * 2. Sends the prompt to LLMProvider for AI generation
 * 3. Returns the raw LLMResponse
 *
 * This is a minimal orchestration layer with no persistence, no entity management,
 * and no business logic beyond connecting existing components.
 */
@Service
@RequiredArgsConstructor
public class AITarotServiceImpl implements AITarotService {

    private final PromptBuilderService promptBuilderService;
    private final LLMProvider llmProvider;

    @Override
    public LLMResponse generateInterpretation(BuildPromptRequest request) {
        // Step 1: Build the prompt from the request
        String prompt = promptBuilderService.buildPrompt(request);

        // Step 2: Send the prompt to the LLM provider and return the response
        return llmProvider.generate(prompt);
    }
}
