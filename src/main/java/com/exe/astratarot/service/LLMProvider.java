package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.llm.LLMMessage;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;

import java.util.function.Consumer;

/**
 * Interface for language model providers.
 *
 * <p>Supports both structured requests ({@link LLMRequest}) and flat string
 * prompts. Structured requests preserve role separation — system instructions,
 * conversation history, and current user question are distinct messages that
 * providers map to their native API format.
 *
 * <p>The {@code generate(String)} and {@code generateStream(String)} methods
 * are provided for backward compatibility: they wrap the flat string as a
 * single user message via {@link LLMRequest#ofUserPrompt(String)}.
 *
 * <p>Providers that cannot stream inherit the default {@link #generateStream}
 * methods which throw {@link UnsupportedOperationException}.
 */
public interface LLMProvider {

    // =====================================================================
    // Structured request methods (preferred)
    // =====================================================================

    /**
     * Generate a complete text response from a structured request.
     *
     * @param request structured request with system instruction, conversation
     *                history, and current user message
     * @return response containing the generated content and metadata
     */
    LLMResponse generate(LLMRequest request);

    /**
     * Stream text generation from a structured request.
     *
     * <p>The provider calls {@code onChunk} for each text fragment as it
     * arrives from the underlying API, then calls {@code onComplete} when
     * the stream finishes successfully, or {@code onError} on failure.
     * Only one terminal callback ({@code onComplete} or {@code onError})
     * is invoked.
     *
     * @param request    structured request with system instruction, conversation
     *                   history, and current user message
     * @param onChunk    consumer for each incremental text fragment
     * @param onError    consumer for any terminal error
     * @param onComplete consumer for final metadata after the stream ends
     * @throws UnsupportedOperationException if this provider does not stream
     */
    default void generateStream(LLMRequest request,
                                Consumer<String> onChunk,
                                Consumer<Throwable> onError,
                                Consumer<StreamCompletion> onComplete) {
        throw new UnsupportedOperationException(
                "Streaming is not supported by this provider");
    }

    // =====================================================================
    // Flat string methods (backward compatibility)
    // =====================================================================

    /**
     * Generate a complete text response from a flat prompt string.
     *
     * <p>Wraps the prompt as a single user message via
     * {@link LLMRequest#ofUserPrompt(String)}. Prefer
     * {@link #generate(LLMRequest)} when structured data is available.
     *
     * @param prompt the prompt string to send to the LLM
     * @return response containing the generated content and metadata
     */
    default LLMResponse generate(String prompt) {
        return generate(LLMRequest.ofUserPrompt(prompt));
    }

    /**
     * Stream text generation from a flat prompt string.
     *
     * <p>Wraps the prompt as a single user message via
     * {@link LLMRequest#ofUserPrompt(String)}. Prefer
     * {@link #generateStream(LLMRequest, ...)} when structured data is available.
     *
     * @param prompt     the prompt string to send to the LLM
     * @param onChunk    consumer for each incremental text fragment
     * @param onError    consumer for any terminal error
     * @param onComplete consumer for final metadata after the stream ends
     * @throws UnsupportedOperationException if this provider does not stream
     */
    default void generateStream(String prompt,
                                Consumer<String> onChunk,
                                Consumer<Throwable> onError,
                                Consumer<StreamCompletion> onComplete) {
        generateStream(LLMRequest.ofUserPrompt(prompt), onChunk, onError, onComplete);
    }
}
