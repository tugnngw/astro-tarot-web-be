package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.llm.LLMResponse;

/**
 * Interface for language model providers.
 * Supports synchronous text generation.
 */
public interface LLMProvider {

    /**
     * Generate text response from the provided prompt.
     *
     * @param prompt The prompt string to send to the LLM
     * @return LLMResponse containing the generated content and metadata
     */
    LLMResponse generate(String prompt);
}