package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;

import java.util.function.Consumer;

/**
 * Service responsible for orchestrating the AI interpretation step of a Tarot reading.
 *
 * <p>Connects PromptBuilderService → LLMProvider for both synchronous and
 * streaming generation.
 */
public interface AITarotService {

    /**
     * Generates an AI interpretation synchronously.
     *
     * @param request fully prepared prompt request
     * @return response with generated content and metadata
     */
    LLMResponse generateInterpretation(BuildPromptRequest request);

    /**
     * Generates an AI interpretation via streaming.
     *
     * <p>Each text fragment is delivered to {@code onChunk} as it arrives.
     * The stream ends with either {@code onComplete} (success) or
     * {@code onError} (failure).  Only one terminal callback is invoked.
     *
     * @param request    fully prepared prompt request
     * @param onChunk    consumer for each incremental text fragment
     * @param onError    consumer for the terminal error, if any
     * @param onComplete consumer for final metadata after success
     */
    void generateInterpretationStream(BuildPromptRequest request,
                                      Consumer<String> onChunk,
                                      Consumer<Throwable> onError,
                                      Consumer<StreamCompletion> onComplete);
}
