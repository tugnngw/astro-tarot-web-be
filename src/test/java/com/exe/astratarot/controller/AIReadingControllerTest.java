package com.exe.astratarot.controller.test;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.domain.dto.reader.CardDrawDTO;
import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.dto.reading.TarotReadingResultDTO;
import com.exe.astratarot.domain.entity.TarotCard;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.LLMProviderException;
import com.exe.astratarot.service.TarotReadingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Slf4j
class AIReadingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TarotReadingService tarotReadingService;

    private static final UUID TEST_USER_ID = UUID.randomUUID(); // Mock User ID for service method arguments
    private static final UUID TEST_READING_ID = UUID.randomUUID();
    private static final UUID TEST_CARD_ID = UUID.randomUUID();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @Test
    void startAiTarotReading_success_returns200() throws Exception {
        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Will I succeed?")
                .numberOfCards(3)
                .spreadName("Past-Present-Future")
                .build();

        TarotReadingResultDTO resultDto = TarotReadingResultDTO.builder()
                .readingId(TEST_READING_ID)
                .userQuestion("Will I succeed?")
                .spreadName("Past-Present-Future")
                .drawnCards(List.of(DrawnCardDetailDTO.builder()
                        .cardId(TEST_CARD_ID)
                        .cardName("The Fool")
                        .arcanaType("Major Arcana")
                        .position((short)0)
                        .reversed(false)
                        .build()))
                .aiInterpretation("AI interpretation content.")
                .modelUsed("gemini-pro")
                .totalTokensUsed(50)
                .readingTimestamp(Instant.now())
                .build();

        ApiResponse<TarotReadingResultDTO> expectedResponse = ApiResponse.success("AI Tarot reading generated successfully", resultDto);

        when(tarotReadingService.initiateAiTarotReading(any(User.class), eq(request))).thenReturn(resultDto);

        mockMvc.perform(post("/api/ai-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(content().json(toJson(expectedResponse)));

        verify(tarotReadingService).initiateAiTarotReading(any(User.class), eq(request));
    }

    @Test
    void startAiTarotReading_unauthenticatedRequest_returns401() throws Exception {
        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Will I succeed?")
                .numberOfCards(3)
                .spreadName("Past-Present-Future")
                .build();

        mockMvc.perform(post("/api/ai-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tarotReadingService);
    }

    @Test
    void startAiTarotReading_invalidRequest_returns400() throws Exception {
        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .numberOfCards(1)
                .build();

        mockMvc.perform(post("/api/ai-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tarotReadingService);
    }

    @Test
    void startAiTarotReading_serviceException_returns500() throws Exception {
        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Service error?")
                .numberOfCards(1)
                .build();

        EntityNotFoundException serviceException = new EntityNotFoundException("User not found");
        when(tarotReadingService.initiateAiTarotReading(any(User.class), eq(request))).thenThrow(serviceException);

        mockMvc.perform(post("/api/ai-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isInternalServerError());

        verify(tarotReadingService).initiateAiTarotReading(any(User.class), eq(request));
    }
}
