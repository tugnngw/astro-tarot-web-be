package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.dto.reading.TarotReadingResultDTO;
import com.exe.astratarot.service.TarotReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for AI Tarot Reading operations.
 *
 * Handles requests for initiating AI-powered Tarot readings.
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
     * @param request Contains user ID, question, card count, and spread details.
     * @return A response containing the Tarot reading results, or an error message.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TarotReadingResultDTO>> startAiTarotReading(
            @Valid @RequestBody StartTarotReadingRequest request) {

        TarotReadingResultDTO result =
                tarotReadingService.initiateAiTarotReading(request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "AI Tarot reading generated successfully",
                        result
                )
        );
    }
}
