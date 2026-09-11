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
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.exception.LLMProviderException;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.security.JwtService;
import com.exe.astratarot.service.TarotReadingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import com.exe.astratarot.config.TestSecurityConfig;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
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
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Slf4j
class AIReadingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TarotReadingService tarotReadingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private StringRedisTemplate redisTemplate; // Mock for AuthRateLimitFilter
    private static final UUID TEST_USER_ID = UUID.randomUUID(); // Mock User ID for service method arguments
    private static final UUID TEST_READING_ID = UUID.randomUUID();
    private static final UUID TEST_CARD_ID = UUID.randomUUID();
    private static final String TEST_TOKEN = "test-token";
    private static final String TEST_USERNAME = "test-user";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
    }

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

        // Mock JWTService and UserDetailsService for authentication
        User mockUser = User.builder()
                .id(TEST_USER_ID)
                .username(TEST_USERNAME)
                .role(UserRole.USER)
                .build();

        CustomUserDetails customUserDetails = new CustomUserDetails(mockUser);

        when(jwtService.extractSubjectSafely(TEST_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtService.validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class))).thenReturn(true);
        when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(customUserDetails);

        when(tarotReadingService.initiateAiTarotReading(eq(mockUser), eq(request))).thenReturn(resultDto);

        mockMvc.perform(post("/api/ai-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk());

        verify(tarotReadingService).initiateAiTarotReading(any(User.class), eq(request));
        verify(jwtService).extractSubjectSafely(TEST_TOKEN);
        verify(jwtService).validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class));
        verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
        verify(jwtService).validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class));
        verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
    }

    @Test
    /**
     * Chưa đăng nhập là 401, không phải 403 — xem chú thích cùng nội dung ở
     * AIReadingControllerStreamTest. Bài test này trước đây khoá lại đúng cái
     * lỗi khiến giao diện không bao giờ làm mới token hết hạn.
     */
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

        // Mock JWTService and UserDetailsService for authentication
        User mockUser = User.builder()
                .id(TEST_USER_ID)
                .username(TEST_USERNAME)
                .role(UserRole.USER)
                .build();

        CustomUserDetails customUserDetails = new CustomUserDetails(mockUser);

        when(jwtService.extractSubjectSafely(TEST_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtService.validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class))).thenReturn(true);
        when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(customUserDetails);

        mockMvc.perform(post("/api/ai-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tarotReadingService);
        verify(jwtService).extractSubjectSafely(TEST_TOKEN);
        verify(jwtService).validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class));
        verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
        verify(jwtService).validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class));
        verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
    }

    @Test
    void startAiTarotReading_serviceException_returns500() throws Exception {
        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Service error?")
                .numberOfCards(1)
                .build();

        // Mock JWTService and UserDetailsService for authentication
        User mockUser = User.builder()
                .id(TEST_USER_ID)
                .username(TEST_USERNAME)
                .role(UserRole.USER)
                .build();

        CustomUserDetails customUserDetails = new CustomUserDetails(mockUser);

        when(jwtService.extractSubjectSafely(TEST_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtService.validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class))).thenReturn(true);
        when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(customUserDetails);

        EntityNotFoundException serviceException = new EntityNotFoundException("User not found");
        when(tarotReadingService.initiateAiTarotReading(eq(mockUser), eq(request))).thenThrow(serviceException);

        mockMvc.perform(post("/api/ai-readings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isInternalServerError());

        verify(tarotReadingService).initiateAiTarotReading(any(User.class), eq(request));
        verify(jwtService).extractSubjectSafely(TEST_TOKEN);
        verify(jwtService).validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class));
        verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
        verify(jwtService).validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class));
        verify(userDetailsService).loadUserByUsername(TEST_USERNAME);
    }
}
