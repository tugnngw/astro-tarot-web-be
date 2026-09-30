package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.LLMProvider;
import com.exe.astratarot.service.PromptBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Implementation of AITarotService.
 *
 * <p>Orchestrates the AI interpretation pipeline for both synchronous and
 * streaming generation.  No persistence, no entity management.
 *
 * <p>Builds a structured {@link LLMRequest} via PromptBuilder, then passes
 * it directly to the provider — preserving role separation between system
 * instruction, conversation history, and current user question.
 */
@Service
@RequiredArgsConstructor
public class AITarotServiceImpl implements AITarotService {

    private final PromptBuilderService promptBuilderService;
    private final LLMProvider llmProvider;

    @Override
    public LLMResponse generateInterpretation(BuildPromptRequest request) {
        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);
        return llmProvider.generate(llmRequest);
    }

    @Override
    public void generateInterpretationStream(BuildPromptRequest request,
                                             Consumer<String> onChunk,
                                             Consumer<Throwable> onError,
                                             Consumer<StreamCompletion> onComplete) {
        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);
        llmProvider.generateStream(llmRequest, onChunk, onError, onComplete);
    }
}
