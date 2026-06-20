package com.exe.astratarot.domain.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending a follow-up message in an AI Tarot reading session.
 *
 * Contains the user's follow-up question and an optional stream flag for future SSE support.
 * The reading ID is derived from the URL path, not from this request body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;

    @Builder.Default
    private boolean stream = false;
}