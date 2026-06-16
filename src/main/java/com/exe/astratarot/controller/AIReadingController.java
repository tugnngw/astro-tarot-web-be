package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.dto.reading.TarotReadingResultDTO;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.TarotReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for AI Tarot Reading operations.
 *
 * Handles requests for initiating AI-powered Tarot readings.
 * Ensures that readings are created only for the authenticated user.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/ai-readings")
public class AIReadingController {

    private final TarotReadingService tarotReadingService;

    /**
     * Initiates an AI Tarot reading based on the provided request.
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
}
