package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.chat.ChatHistoryResponse;
import com.exe.astratarot.domain.dto.chat.ChatRequest;
import com.exe.astratarot.domain.dto.chat.ChatResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST Controller for AI Tarot reading chat continuation.
 *
 * Enables users to continue AI-generated Tarot readings with follow-up questions.
 * Sessions use existing chat_sessions and chat_messages tables (no schema changes).
 * Non-streaming only. SSE support planned for future.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/ai-readings")
public class AIChatController {

    private final ChatService chatService;

    /**
     * Sends a follow-up message in an existing AI reading session.
     *
     * @param userDetails the authenticated user's details
     * @param readingId the ID of the TarotReading to continue
     * @param request contains the follow-up message
     * @return ChatResponse with the AI reply and token usage
     */
    @PostMapping("/{readingId}/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID readingId,
            @Valid @RequestBody ChatRequest request) {

        ChatResponse response = chatService.sendMessage(readingId, userDetails.getUser(), request.getMessage());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent successfully", response));
    }

    /**
     * Retrieves paginated message history for an AI reading session.
     *
     * @param userDetails the authenticated user's details
     * @param readingId the ID of the TarotReading
     * @param page page number (zero-based, default 0)
     * @param size page size (default 50, max 100)
     * @return ChatHistoryResponse with messages and pagination metadata
     */
    @GetMapping("/{readingId}/chat/messages")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID readingId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {

        ChatHistoryResponse history = chatService.getMessages(readingId, userDetails.getUser(), page, size);

        return ResponseEntity.ok(ApiResponse.success("Messages retrieved successfully", history));
    }
}