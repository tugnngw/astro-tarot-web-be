package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.astrology.AspectDTO;
import com.exe.astratarot.domain.dto.astrology.PlanetPositionDTO;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.service.PromptBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of PromptBuilderService.
 *
 * Constructs prompts by composing six distinct sections:
 * 1. System Instructions (fixed)
 * 2. Reading Context (from request)
 * 3. Astrology Context (from AstrologyContextDTO)
 * 4. Tarot Cards (from DrawnCardDetailDTO list)
 * 5. User Question (verbatim from request)
 * 6. Response Instructions (fixed)
 *
 * No external dependencies. Pure string composition.
 */
@Service
@RequiredArgsConstructor
public class PromptBuilderServiceImpl implements PromptBuilderService {

    @Override
    public String buildPrompt(BuildPromptRequest request) {
        StringBuilder prompt = new StringBuilder();

        // Section 1: System Instructions
        prompt.append(buildSystemInstructions());

        // Section 2: Reading Context
        prompt.append(buildReadingContext(request.getSpreadName()));

        // Section 3: Astrology Context
        prompt.append(buildAstrologyContext(request.getAstrologyContext()));

        // Section 4: Tarot Cards
        prompt.append(buildTarotCardsSection(request.getDrawnCardDetails()));

        // Section 5: User Question
        prompt.append(buildUserQuestionSection(request.getUserQuestion()));

        // Section 6: Response Instructions
        prompt.append(buildResponseInstructions());

        return prompt.toString();
    }

    /**
     * Builds the system instructions section (fixed template).
     * Defines the AI's role, boundaries, and ethical guidelines.
     */
    private String buildSystemInstructions() {
        return """
                You are an empathetic Tarot and Astrology guide.

                Your role is to provide personal guidance, reflection, and insight.

                You are NOT:
                - A fortune teller
                - A medical professional
                - A legal professional
                - A financial advisor
                - Someone who makes deterministic predictions

                Core principles:
                - Never claim guaranteed future outcomes
                - Encourage personal reflection and empowerment
                - Use supportive, compassionate language
                - Respect the user's autonomy and decision-making
                - Avoid fear-based or manipulative framing

                ---

                """;
    }

    /**
     * Builds the reading context section.
     * Includes spread name and any relevant metadata.
     */
    private String buildReadingContext(String spreadName) {
        StringBuilder section = new StringBuilder();
        section.append("READING CONTEXT\n");

        if (spreadName != null && !spreadName.isBlank()) {
            section.append("Spread: ").append(spreadName).append("\n");
        } else {
            section.append("Spread: Tarot Reading\n");
        }

        section.append("\n---\n\n");
        return section.toString();
    }

    /**
     * Builds the astrology context section.
     * Formats natal chart and transit data into readable text.
     */
    private String buildAstrologyContext(AstrologyContextDTO astrology) {
        StringBuilder section = new StringBuilder();
        section.append("ASTROLOGY CONTEXT\n\n");

        // Natal Chart Foundation
        section.append("Natal Chart:\n");
        section.append("- Birth: ").append(astrology.getBirthDate());
        if (astrology.getBirthTime() != null) {
            section.append(" at ").append(astrology.getBirthTime());
        }
        section.append(" in ").append(astrology.getBirthPlace()).append("\n");

        // Primary Signs
        section.append("- Sun in ").append(astrology.getSunSign());
        if (astrology.getMoonSign() != null) {
            section.append(", Moon in ").append(astrology.getMoonSign());
        }
        if (astrology.getRisingSign() != null) {
            section.append(", Rising in ").append(astrology.getRisingSign());
        }
        section.append("\n");

        // Natal Planet Positions
        if (astrology.getNatalPlanetPositions() != null && !astrology.getNatalPlanetPositions().isEmpty()) {
            section.append("\nPlanetary Positions:\n");
            for (PlanetPositionDTO planet : astrology.getNatalPlanetPositions()) {
                section.append("- ").append(planet.getPlanetName())
                        .append(" in ").append(planet.getSign())
                        .append(" at ").append(planet.getDegree()).append("°");
                if (planet.getHouse() != null) {
                    section.append(" (House ").append(planet.getHouse()).append(")");
                }
                if (planet.getRetrograde() != null && planet.getRetrograde()) {
                    section.append(" [Retrograde]");
                }
                section.append("\n");
            }
        }

        // Natal Aspects
        if (astrology.getNatalAspects() != null && !astrology.getNatalAspects().isEmpty()) {
            section.append("\nNatal Aspects:\n");
            for (AspectDTO aspect : astrology.getNatalAspects()) {
                section.append("- ").append(aspect.getPlanet1())
                        .append(" ").append(aspect.getAspectType())
                        .append(" ").append(aspect.getPlanet2())
                        .append(" (").append(aspect.getExactDegree()).append("° exact, ")
                        .append(aspect.getOrb()).append("° orb)\n");
            }
        }

        // Current Transits
        if (astrology.getCurrentTransit() != null && !astrology.getCurrentTransit().isBlank()) {
            section.append("\nCurrent Influences:\n");
            section.append("- Transit: ").append(astrology.getCurrentTransit()).append("\n");
        }

        // Transit Aspects
        if (astrology.getTransitAspects() != null && !astrology.getTransitAspects().isEmpty()) {
            section.append("\nActive Aspects:\n");
            for (AspectDTO aspect : astrology.getTransitAspects()) {
                section.append("- ").append(aspect.getPlanet1())
                        .append(" ").append(aspect.getAspectType())
                        .append(" ").append(aspect.getPlanet2())
                        .append(" (orb: ").append(aspect.getOrb()).append("°)\n");
            }
        }

        section.append("\n---\n\n");
        return section.toString();
    }

    /**
     * Builds the tarot cards section.
     * Lists each drawn card with position and orientation.
     */
    private String buildTarotCardsSection(List<DrawnCardDetailDTO> cards) {
        StringBuilder section = new StringBuilder();
        section.append("TAROT CARDS\n\n");

        // Sort by position to ensure correct order
        List<DrawnCardDetailDTO> sortedCards = cards.stream()
                .sorted((a, b) -> Short.compare(a.getPosition(), b.getPosition()))
                .collect(Collectors.toList());

        for (DrawnCardDetailDTO card : sortedCards) {
            section.append("Card ").append(card.getPosition() + 1).append(": ")
                    .append(card.getCardName())
                    .append(" — ").append(card.getReversed() ? "Reversed" : "Upright")
                    .append(" (").append(card.getArcanaType()).append(")\n");
        }

        section.append("\n---\n\n");
        return section.toString();
    }

    /**
     * Builds the user question section.
     * Includes the user's question verbatim.
     */
    private String buildUserQuestionSection(String userQuestion) {
        return "USER QUESTION\n" +
                userQuestion + "\n\n" +
                "---\n\n";
    }

    /**
     * Builds the response instructions section (fixed template).
     * Guides the AI on how to construct its response.
     */
    private String buildResponseInstructions() {
        return """
                RESPONSE INSTRUCTIONS

                Interpret the astrology context and tarot cards together:
                - Connect planetary placements to the card symbolism
                - Consider how current transits influence the reading
                - Use the cards as a reflection tool, not a prediction tool

                Provide guidance that is:
                - Reflective and introspective
                - Supportive and empowering
                - Action-oriented (focus on what the user can do)
                - Respectful of their autonomy

                Avoid:
                - Definitive predictions or "guaranteed" outcomes
                - Medical, legal, or financial advice
                - Fear-based language or manipulative framing
                - Suggesting deterministic fate

                Format: Clear paragraphs, conversational tone, 300-500 words
                """;
    }
}