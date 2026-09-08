package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.astrology.AspectDTO;
import com.exe.astratarot.domain.dto.astrology.PlanetPositionDTO;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderServiceImplTest {

    private PromptBuilderServiceImpl promptBuilderService;

    @BeforeEach
    void setUp() {
        promptBuilderService = new PromptBuilderServiceImpl();
    }

    @Test
    void buildPrompt_withNullAstrologyContext_shouldSucceed() {
        // Arrange
        List<DrawnCardDetailDTO> cards = List.of(
                DrawnCardDetailDTO.builder()
                        .cardName("The Fool")
                        .arcanaType("Major Arcana")
                        .cardNumber(0)
                        .position((short) 0)
                        .reversed(false)
                        .build(),
                DrawnCardDetailDTO.builder()
                        .cardName("The Magician")
                        .arcanaType("Major Arcana")
                        .cardNumber(1)
                        .position((short) 1)
                        .reversed(true)
                        .build()
        );

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("What is my future?")
                .astrologyContext(null) // Null astrology context
                .drawnCardDetails(cards)
                .spreadName("Three-Card Spread")
                .build();

        // Act
        String prompt = promptBuilderService.buildPrompt(request);

        // Assert
        assertNotNull(prompt, "Prompt should not be null");
        assertFalse(prompt.isBlank(), "Prompt should not be blank");
        assertTrue(prompt.contains("TAROT CARDS"), "Prompt should contain TAROT CARDS section");
        assertTrue(prompt.contains("What is my future?"), "Prompt should contain user question");
        assertTrue(prompt.contains("The Fool"), "Prompt should contain first card");
        assertTrue(prompt.contains("The Magician"), "Prompt should contain second card");
        assertTrue(prompt.contains("TAROT CARDS"), "Prompt should contain TAROT CARDS section");
    }

    @Test
    void buildPrompt_withAstrologyContext_shouldSucceed() {
        // Arrange
        List<DrawnCardDetailDTO> cards = List.of(
                DrawnCardDetailDTO.builder()
                        .cardName("The Fool")
                        .arcanaType("Major Arcana")
                        .cardNumber(0)
                        .position((short) 0)
                        .reversed(false)
                        .build()
        );

        List<PlanetPositionDTO> natalPlanets = List.of(
                PlanetPositionDTO.builder()
                        .planetName("Sun")
                        .sign("Aries")
                        .degree(BigDecimal.valueOf(15.5))
                        .house(1)
                        .retrograde(false)
                        .build()
        );

        AstrologyContextDTO astrology = AstrologyContextDTO.builder()
                .birthDate(LocalDate.of(1990, 3, 21))
                .birthTime(LocalTime.of(14, 30))
                .birthPlace("New York, NY")
                .sunSign("Aries")
                .element("Fire")
                .modality("Cardinal")
                .moonSign("Libra")
                .risingSign("Cancer")
                .natalPlanetPositions(natalPlanets)
                .natalAspects(List.of())
                .transitAspects(List.of())
                .build();

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("What guidance do the cards have?")
                .astrologyContext(astrology)
                .drawnCardDetails(cards)
                .spreadName("Single Card")
                .build();

        // Act
        String prompt = promptBuilderService.buildPrompt(request);

        // Assert
        assertNotNull(prompt, "Prompt should not be null");
        assertFalse(prompt.isBlank(), "Prompt should not be blank");
        assertTrue(prompt.contains("ASTROLOGY CONTEXT"), "Prompt should contain ASTROLOGY CONTEXT section");
        assertTrue(prompt.contains("1990-03-21"), "Prompt should contain birth date");
        assertTrue(prompt.contains("New York, NY"), "Prompt should contain birth place");
        assertTrue(prompt.contains("Aries"), "Prompt should contain sun sign");
        assertTrue(prompt.contains("Fire"), "Prompt should contain element");
        assertTrue(prompt.contains("Cardinal"), "Prompt should contain modality");
        assertTrue(prompt.contains("Libra"), "Prompt should contain moon sign");
        assertTrue(prompt.contains("Cancer"), "Prompt should contain rising sign");
        assertTrue(prompt.contains("TAROT CARDS"), "Prompt should contain TAROT CARDS section");
        assertTrue(prompt.contains("The Fool"), "Prompt should contain card name");
        assertTrue(prompt.contains("What guidance do the cards have?"), "Prompt should contain user question");
    }

    @Test
    void buildPrompt_withNullAstrologyContext_includesAllOtherSections() {
        // Arrange
        List<DrawnCardDetailDTO> cards = List.of(
                DrawnCardDetailDTO.builder()
                        .cardName("Card 1")
                        .arcanaType("Minor Arcana")
                        .cardNumber(1)
                        .position((short) 0)
                        .reversed(false)
                        .build()
        );

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Test question")
                .astrologyContext(null)
                .drawnCardDetails(cards)
                .spreadName("Test Spread")
                .build();

        // Act
        String prompt = promptBuilderService.buildPrompt(request);

        // Assert
        assertTrue(prompt.contains("READING CONTEXT"), "Should contain reading context");
        assertTrue(prompt.contains("Test Spread"), "Should contain spread name");
        assertTrue(prompt.contains("TAROT CARDS"), "Should contain tarot cards section");
        assertTrue(prompt.contains("Card 1"), "Should contain card details");
        assertTrue(prompt.contains("USER QUESTION"), "Should contain user question section");
        assertTrue(prompt.contains("Test question"), "Should contain the actual question");
        assertTrue(prompt.contains("CẤU TRÚC BÀI ĐỌC LẦN ĐẦU"), "Should contain response format instructions");
    }
}
