package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.llm.LLMMessage;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.LLMProvider;
import com.exe.astratarot.service.PromptBuilderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    void generateInterpretation_passesLLMRequestToProvider() {
        // Arrange
        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("What is my future?")
                .astrologyContext(null)
                .drawnCardDetails(List.of())
                .build();

        LLMRequest llmRequest = LLMRequest.builder()
                .systemInstruction("Persona: You are a tarot reader.")
                .messages(List.of(LLMMessage.user("What is my future?")))
                .build();

        LLMResponse expectedResponse = LLMResponse.builder()
                .content("AI interpretation content.")
                .modelInfo("mock-model")
                .tokenUsage(null)
                .build();

        when(promptBuilderService.buildLLMRequest(any(BuildPromptRequest.class)))
                .thenReturn(llmRequest);
        when(llmProvider.generate(any(LLMRequest.class)))
                .thenReturn(expectedResponse);

        // Act
        LLMResponse actualResponse = aiTarotService.generateInterpretation(request);

        // Assert
        assertNotNull(actualResponse, "The response should not be null.");
        assertEquals("AI interpretation content.", actualResponse.getContent(), "The AI content does not match.");
        assertEquals("mock-model", actualResponse.getModelInfo(), "The model info does not match.");

        // Capture the LLMRequest passed to provider
        ArgumentCaptor<LLMRequest> captor = ArgumentCaptor.forClass(LLMRequest.class);
        verify(promptBuilderService).buildLLMRequest(request);
        verify(llmProvider).generate(captor.capture());

        LLMRequest capturedRequest = captor.getValue();
        assertEquals("Persona: You are a tarot reader.", capturedRequest.getSystemInstruction());
        assertEquals(1, capturedRequest.getMessages().size());
        assertEquals(LLMMessage.Role.USER, capturedRequest.getMessages().get(0).getRole());
        assertEquals("What is my future?", capturedRequest.getMessages().get(0).getContent());
    }

    @Test
    void generateInterpretation_withAstrologyContext_passesStructuredRequest() {
        // Arrange
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

        LLMRequest llmRequest = LLMRequest.builder()
                .systemInstruction("Persona: Astrologer. Sun: Aries.")
                .messages(List.of(LLMMessage.user("Guidance for my career?")))
                .build();

        LLMResponse expectedResponse = LLMResponse.builder()
                .content("Career guidance from AI.")
                .modelInfo("gemini-pro")
                .tokenUsage(null)
                .build();

        when(promptBuilderService.buildLLMRequest(request))
                .thenReturn(llmRequest);
        when(llmProvider.generate(any(LLMRequest.class)))
                .thenReturn(expectedResponse);

        // Act
        LLMResponse actualResponse = aiTarotService.generateInterpretation(request);

        // Assert
        assertNotNull(actualResponse);
        assertEquals("Career guidance from AI.", actualResponse.getContent());
        assertEquals("gemini-pro", actualResponse.getModelInfo());

        ArgumentCaptor<LLMRequest> captor = ArgumentCaptor.forClass(LLMRequest.class);
        verify(promptBuilderService).buildLLMRequest(request);
        verify(llmProvider).generate(captor.capture());

        LLMRequest capturedRequest = captor.getValue();
        assertTrue(capturedRequest.getSystemInstruction().contains("Aries"));
        assertEquals(1, capturedRequest.getMessages().size());
    }
}
