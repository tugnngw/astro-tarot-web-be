package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;

import java.util.function.Consumer;

/**
 * Interface for language model providers.
 *
 * <p>Supports both synchronous text generation and streaming
 * (server-sent events) generation.  Providers that cannot stream
 * inherit the default {@link #generateStream} which throws
 * {@link UnsupportedOperationException}.
 */
public interface LLMProvider {

    /**
     * Generate a complete text response from the provided prompt.
     *
     * @param prompt the prompt string to send to the LLM
     * @return response containing the generated content and metadata
     */
    LLMResponse generate(String prompt);

    /**
     * Stream text generation from the provided prompt.
     *
     * <p>The provider calls {@code onChunk} for each text fragment as it
     * arrives from the underlying API, then calls {@code onComplete} when
     * the stream finishes successfully, or {@code onError} on failure.
     * Only one terminal callback ({@code onComplete} or {@code onError})
     * is invoked.
     *
     * @param prompt   the prompt string to send to the LLM
     * @param onChunk  consumer for each incremental text fragment
     * @param onError  consumer for any terminal error
     * @param onComplete consumer for final metadata after the stream ends
     * @throws UnsupportedOperationException if this provider does not stream
     */
    default void generateStream(String prompt,
                                Consumer<String> onChunk,
                                Consumer<Throwable> onError,
                                Consumer<StreamCompletion> onComplete) {
        throw new UnsupportedOperationException(
                "Streaming is not supported by this provider");
    }
}