package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.dto.reading.TarotReadingResultDTO;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.TarotReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * REST Controller for AI Tarot Reading operations.
 *
 * Handles requests for initiating AI-powered Tarot readings.
 * Ensures that readings are created only for the authenticated user.
 *
 * Supports both synchronous and streaming endpoints:
 * - POST /api/ai-readings — blocking, returns complete reading
 * - POST /api/ai-readings/stream — streaming, returns SSE emitter
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/ai-readings")
public class AIReadingController {

    private final TarotReadingService tarotReadingService;

    /**
     * Initiates an AI Tarot reading (synchronous, blocking).
     *
     * Derives the user identity from the JWT context via @AuthenticationPrincipal CustomUserDetails
     * to ensure security. Users can only create readings for their own account.
     *
     * @param userDetails The authenticated user's details.
     * @param request     Contains user question, card count, and spread details.
     * @return A response containing the Tarot reading results, or an error message.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TarotReadingResultDTO>> startAiTarotReading(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody StartTarotReadingRequest request) {

        TarotReadingResultDTO result =
                tarotReadingService.initiateAiTarotReading(userDetails.getUser(), request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "AI Tarot reading generated successfully",
                        result
                )
        );
    }

    /**
     * Initiates an AI Tarot reading via Server-Sent Events (SSE streaming).
     *
     * <p>Streams interpretation tokens progressively as they arrive from Gemini,
     * then persists the complete reading only after successful stream completion.
     *
     * <p>Events:
     * <ul>
     *   <li>{@code chunk} — partial interpretation text fragment (`{ "content": "..." }`)
     *   <li>{@code complete} — stream finished, reading persisted
     *       (`{ "readingId", "sessionId", "modelInfo", "tokenUsage" }`)
     *   <li>{@code error} — terminal error, reading NOT persisted (`{ "message": "..." }`)
     * </ul>
     *
     * @param userDetails the authenticated user's details
     * @param request     contains user question, card count, and spread details
     * @return SSE emitter
     */
    @PostMapping("/stream")
    public SseEmitter startAiTarotReadingStream(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody StartTarotReadingRequest request) {

        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout

        // Register disconnect/timeout/error handlers (logging only)
        emitter.onCompletion(() ->
                log.debug("SSE completed: userId={}", userDetails.getUser().getId()));
        emitter.onTimeout(() ->
                log.warn("SSE timed out: userId={}", userDetails.getUser().getId()));
        emitter.onError(ex ->
                log.warn("SSE error: userId={}, {}", userDetails.getUser().getId(), ex.getMessage()));

        tarotReadingService.initiateAiTarotReadingStream(
                userDetails.getUser(),
                request,
                /* onChunk */ chunk -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("chunk")
                                .data(Map.of("content", chunk)));
                        log.info("CONTROLLER CHUNK: [{}]", chunk);
                    } catch (IOException e) {
                        log.warn("SSE send failed (client disconnected): userId={}", userDetails.getUser().getId());
                    }
                },
                /* onError */ error -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(Map.of("message", error.getMessage())));
                    } catch (IOException e) {
                        log.warn("SSE error send failed: userId={}", userDetails.getUser().getId());
                    }
                    emitter.completeWithError(error);
                },
                /* onComplete */ result -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(Map.of(
                                        "readingId", result.readingId().toString(),
                                        "sessionId", result.sessionId() != null ? result.sessionId().toString() : "",
                                        "modelInfo", result.modelUsed() != null ? result.modelUsed() : "",
                                        "tokenUsage", LLMTokenUsage.builder()
                                                .promptTokens(result.promptTokens())
                                                .completionTokens(result.completionTokens())
                                                .totalTokens(result.totalTokens())
                                                .build()
                                )));
                        log.info("CONTROLLER COMPLETE");
                    } catch (IOException e) {
                        log.warn("SSE complete send failed: userId={}", userDetails.getUser().getId());
                    }
                    emitter.complete();
                }
        );

        return emitter;
    }
}
