package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.chat.ChatRequest;
import com.exe.astratarot.domain.dto.chat.ChatResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Unified REST controller for AI chat (astrology-only or astrology + reading).
 *
 * Flow:
 * 1. POST /api/chat { message, readingId: null } → Astrology profile context only
 * 2. POST /api/chat { message, readingId: UUID } → Astrology + Drawn cards context
 * 3. POST /api/chat/stream → SSE version of above
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    /**
     * Sends a message with optional readingId.
     * - If readingId == null: AI replies using user's astrology profile only
     * - If readingId != null: AI replies using user's astrology profile + reading cards
     *
     * @param userDetails the authenticated user
     * @param request contains message and optional readingId
     * @return ChatResponse with the AI reply and token usage
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChatRequest request) {

        ChatResponse response = chatService.sendMessage(
                request.getReadingId(),
                userDetails.getUser(),
                request.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent successfully", response));
    }

    /**
     * Sends a message via Server-Sent Events (streaming).
     *
     * Events:
     * - chunk: partial text fragment
     * - complete: stream finished with messageId, modelInfo, tokenUsage
     * - error: terminal error
     *
     * @param userDetails the authenticated user
     * @param request contains message and optional readingId
     * @return SSE emitter
     */
    @PostMapping("/stream")
    public SseEmitter sendMessageStream(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChatRequest request) {

        SseEmitter emitter = new SseEmitter(300_000L);

        emitter.onCompletion(() ->
                log.debug("SSE completed: readingId={}", request.getReadingId()));
        emitter.onTimeout(() ->
                log.warn("SSE timed out: readingId={}", request.getReadingId()));
        emitter.onError(ex ->
                log.warn("SSE error: readingId={}, {}", request.getReadingId(), ex.getMessage()));

        chatService.sendMessageStream(
                request.getReadingId(),
                userDetails.getUser(),
                request.getMessage(),
                /* onChunk */ chunk -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("chunk")
                                .data(Map.of("content", chunk)));
                    } catch (IOException e) {
                        log.warn("SSE send failed (client disconnected): readingId={}", request.getReadingId());
                    }
                },
                /* onError */ error -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("message", error.getMessage())));
                    } catch (IOException e) {
                        log.warn("SSE error send failed: readingId={}", request.getReadingId());
                    }
                    emitter.completeWithError(error);
},
                /* onComplete */ result -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(Map.of(
                                        "messageId", result.messageId() != null ? result.messageId().toString() : "",
                                        "modelInfo", result.modelUsed() != null ? result.modelUsed() : "",
                                        "tokenUsage", LLMTokenUsage.builder()
                                                .promptTokens(result.promptTokens())
                                                .completionTokens(result.completionTokens())
                                                .totalTokens(result.totalTokens())
                                                .build()
                                )));

} catch (IOException e) {
                        log.warn("SSE complete send failed: readingId={}", request.getReadingId());
                    }
                    emitter.complete();
                }
        );

        return emitter;
    }
}
