package com.exe.astratarot.service.impl;

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
 */
@Service
@RequiredArgsConstructor
public class AITarotServiceImpl implements AITarotService {

    private final PromptBuilderService promptBuilderService;
    private final LLMProvider llmProvider;

    @Override
    public LLMResponse generateInterpretation(BuildPromptRequest request) {
        String prompt = promptBuilderService.buildPrompt(request);
        return llmProvider.generate(prompt);
    }

    @Override
    public void generateInterpretationStream(BuildPromptRequest request,
                                             Consumer<String> onChunk,
                                             Consumer<Throwable> onError,
                                             Consumer<StreamCompletion> onComplete) {
        String prompt = promptBuilderService.buildPrompt(request);
        llmProvider.generateStream(prompt, onChunk, onError, onComplete);
    }
}
