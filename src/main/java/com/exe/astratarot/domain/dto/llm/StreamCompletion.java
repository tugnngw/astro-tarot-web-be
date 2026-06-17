package com.exe.astratarot.domain.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signals the successful completion of an LLM streaming response.
 *
 * <p>Carries metadata accumulated during the stream, such as the model
 * identifier and token usage statistics.  The full generated text is
 * built externally from the {@code onChunk} callbacks and is not
 * included here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamCompletion {

    /** Model identifier (e.g. {@code "gemini-2.5-flash"}). */
    private String modelInfo;

    /** Token consumption, if reported by the provider. */
    private LLMTokenUsage tokenUsage;
}
