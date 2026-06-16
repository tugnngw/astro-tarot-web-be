package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.dto.reading.TarotReadingResultDTO;
import com.exe.astratarot.domain.entity.User;

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
 *
 * User identity is provided as a parameter to ensure readings are created only
 * for the authenticated user, preventing cross-user account manipulation.
 */
public interface TarotReadingService {

    /**
     * Initiates a complete AI Tarot reading for the authenticated user.
     *
     * @param user The authenticated user who is requesting the reading.
     *             This is derived from the JWT context to ensure security.
     * @param request Tarot reading parameters (question, numberOfCards, etc.)
     * @return TarotReadingResultDTO containing the AI interpretation and card details
     */
    TarotReadingResultDTO initiateAiTarotReading(User user, StartTarotReadingRequest request);
}
