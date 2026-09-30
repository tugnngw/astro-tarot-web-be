package com.exe.astratarot.domain.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single message in an LLM conversation, with explicit role.
 *
 * <p>Used to build structured prompts that preserve conversation history
 * and system instructions as separate messages rather than flattening
 * everything into a single user prompt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMMessage {

    /**
     * Message role in the conversation.
     *
     * <ul>
     *   <li>{@link Role#SYSTEM} — system instructions / persona</li>
     *   <li>{@link Role#USER} — user messages / current question</li>
     *   <li>{@link Role#ASSISTANT} — AI responses from history</li>
     * </ul>
     */
    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    private Role role;
    private String content;

    /**
     * Convenience factory for user messages.
     */
    public static LLMMessage user(String content) {
        return new LLMMessage(Role.USER, content);
    }

    /**
     * Convenience factory for assistant messages.
     */
    public static LLMMessage assistant(String content) {
        return new LLMMessage(Role.ASSISTANT, content);
    }

    /**
     * Convenience factory for system messages.
     */
    public static LLMMessage system(String content) {
        return new LLMMessage(Role.SYSTEM, content);
    }
}
