package com.exe.astratarot.domain.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured request to an LLM provider.
 *
 * <p>Unlike a flat prompt string, this DTO preserves role separation:
 * system instructions, conversation history (user/assistant pairs), and
 * the current user message are distinct. Providers map these roles to their
 * native API format (e.g., Gemini {@code systemInstruction} + {@code contents},
 * OpenAI {@code messages} array with {@code role} field).
 *
 * <p>Design notes:
 * <ul>
 *   <li>System instruction is optional. When null/blank, providers omit it.</li>
 *   <li>{@code messages} should contain ONLY conversation turns (user/assistant).
 *       The current user question should be the LAST user message.</li>
 *   <li>Generation config fields (temperature, maxTokens) are optional.
 *       When null, providers use their defaults — no arbitrary values are
 *       hardcoded in business logic.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMRequest {

    /**
     * System instruction / persona for the LLM.
     *
     * <p>Mapped to Gemini {@code systemInstruction} or OpenAI {@code role: "system"}.
     * Optional — when null or blank, providers omit this section.
     */
    private String systemInstruction;

    /**
     * Ordered list of conversation messages (user/assistant).
     *
     * <p>Should NOT contain system messages — use {@link #systemInstruction} instead.
     * The current user question must be the last message in this list.
     *
     * <p>Example:
     * <pre>
     *   user: "Tôi nên đổi việc không?"
     *   assistant: "Mình thấy The Tower..."
     *   user: "Còn về tài chính?"
     * </pre>
     */
    @Builder.Default
    private List<LLMMessage> messages = new ArrayList<>();

    /**
     * Optional: sampling temperature.
     *
     * <p>When null, provider uses its default. Do not hardcode arbitrary values —
     * set this only if the calling service has a legitimate configuration source.
     */
    private Double temperature;

    /**
     * Optional: maximum tokens to generate.
     *
     * <p>When null, provider uses its default. Do not hardcode arbitrary values.
     */
    private Integer maxTokens;

    /**
     * Convenience factory: wrap a single user prompt (no history, no system).
     *
     * <p>Used for backward compatibility when callers only have a flat string.
     */
    public static LLMRequest ofUserPrompt(String prompt) {
        return LLMRequest.builder()
                .messages(List.of(LLMMessage.user(prompt)))
                .build();
    }

    /**
     * Returns the last user message content, or null if no messages.
     */
    public String getCurrentUserMessage() {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            LLMMessage msg = messages.get(i);
            if (msg != null && msg.getRole() == LLMMessage.Role.USER && msg.getContent() != null) {
                return msg.getContent();
            }
        }
        return null;
    }
}
