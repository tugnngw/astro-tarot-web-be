package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.dto.reading.TarotReadingResultDTO;

/**
 * Service responsible for orchestrating the complete AI Tarot reading flow.
 *
 * Responsibilities:
 * - Accept a reading request from the user
 * - Draw Tarot cards using TarotDrawingService
 * - Enrich card data using TarotCardRepository
 * - Build the prompt using BuildPromptRequest (with null astrologyContext)
 * - Call AITarotService for AI interpretation
 * - Persist the TarotReading and ReadingCard entities
 * - Return the full reading result as TarotReadingResultDTO
 *
 * The Gemini API call is made OUTSIDE the database transaction for reliability.
 * Database persistence happens only after Gemini succeeds.
 */
public interface TarotReadingService {

    /**
     * Initiates a complete AI Tarot reading.
     *
     * @param request Tarot reading parameters (userId, question, numberOfCards, etc.)
     * @return TarotReadingResultDTO containing the AI interpretation and card details
     */
    TarotReadingResultDTO initiateAiTarotReading(StartTarotReadingRequest request);
}
