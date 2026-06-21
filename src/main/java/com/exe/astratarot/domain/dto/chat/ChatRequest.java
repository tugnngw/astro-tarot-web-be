package com.exe.astratarot.domain.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for AI chat (astrology-only or astrology + tarot reading).
 *
 * - readingId=null → AI responds using user's astrology profile only
 * - readingId=UUID → AI responds using astrology profile + drawn cards
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message is required")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;

    private UUID readingId;

    @Builder.Default
    private boolean stream = false;
}
