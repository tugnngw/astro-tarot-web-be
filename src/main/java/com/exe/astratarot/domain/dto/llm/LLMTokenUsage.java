package com.exe.astratarot.domain.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token usage information from an LLM provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMTokenUsage {

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}