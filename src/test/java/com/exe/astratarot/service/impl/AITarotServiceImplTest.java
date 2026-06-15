package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.LLMProvider;
import com.exe.astratarot.service.PromptBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AITarotServiceImplTest {

    @Mock
    private PromptBuilderService promptBuilderService;

    @Mock
    private LLMProvider llmProvider;

    @InjectMocks
    private AITarotServiceImpl aiTarotService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void generateInterpretation_withNullAstrologyContext_shouldSucceed() throws Exception {
        // Arrange
        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("A simple question")
                .astrologyContext(null) // Simulate null astrology context
                .drawnCardDetails(List.of()) // Provide empty list for cards
                .build();

        String expectedPrompt = "This is a mocked prompt.";
        LLMResponse expectedResponse = LLMResponse.builder()
                .content("AI interpretation content.")
                .modelInfo("mock-model")
                .tokenUsage(null)
                .build();

        // Mock PromptBuilderService to return a specific prompt regardless of input
        when(promptBuilderService.buildPrompt(any(BuildPromptRequest.class)))
                .thenReturn(expectedPrompt);

        // Mock LLMProvider to return a successful response
        when(llmProvider.generate(expectedPrompt))
                .thenReturn(expectedResponse);

        // Act
        LLMResponse actualResponse = aiTarotService.generateInterpretation(request);

        // Assert
        assertNotNull(actualResponse, "The response should not be null.");
        assertEquals("AI interpretation content.", actualResponse.getContent(), "The AI content does not match.");
        assertEquals("mock-model", actualResponse.getModelInfo(), "The model info does not match.");
        assertNull(actualResponse.getTokenUsage(), "Token usage should be null as per mock setup.");

        // Verify that the necessary services were called
        verify(promptBuilderService).buildPrompt(request);
        verify(llmProvider).generate(expectedPrompt);
    }

    @Test
    void generateInterpretation_withValidAstrologyContext_shouldSucceed() throws Exception {
        // Arrange
        // Sample data for a valid astrology context
        AstrologyContextDTO astrologyContext = AstrologyContextDTO.builder()
                .birthDate(LocalDate.now())
                .birthPlace("Test City")
                .sunSign("Aries")
                .build();

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Guidance for my career?")
                .astrologyContext(astrologyContext)
                .drawnCardDetails(List.of())
                .spreadName("Career Spread")
                .build();

        String expectedPrompt = "Mocked prompt with astrology.";
        LLMResponse expectedResponse = LLMResponse.builder()
                .content("Career guidance from AI.")
                .modelInfo("gemini-pro")
                .tokenUsage(null)
                .build();

        when(promptBuilderService.buildPrompt(request))
                .thenReturn(expectedPrompt);
        when(llmProvider.generate(expectedPrompt))
                .thenReturn(expectedResponse);

        // Act
        LLMResponse actualResponse = aiTarotService.generateInterpretation(request);

        // Assert
        assertNotNull(actualResponse);
        assertEquals("Career guidance from AI.", actualResponse.getContent());
        assertEquals("gemini-pro", actualResponse.getModelInfo());
        assertNull(actualResponse.getTokenUsage());

        verify(promptBuilderService).buildPrompt(request);
        verify(llmProvider).generate(expectedPrompt);
    }
}
