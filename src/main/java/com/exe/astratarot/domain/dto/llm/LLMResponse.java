package com.exe.astratarot.domain.dto.llm;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response from an LLM provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMResponse {

    @NotBlank(message = "Content is required")
    private String content;

    @NotBlank(message = "Model info is required")
    private String modelInfo;

    private LLMTokenUsage tokenUsage;
}