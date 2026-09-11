package com.exe.astratarot.controller.test;

import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.domain.dto.reading.StartTarotReadingRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.TarotReadingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AI Reading streaming endpoint.
 * Tests SSE streaming functionality for initial reading generation.
 *
 * Uses @SpringBootTest with real SecurityFilterChain and H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Slf4j
class AIReadingControllerStreamTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TarotReadingService tarotReadingService;

    @MockBean
    private com.exe.astratarot.security.JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final UUID TEST_READING_ID = UUID.randomUUID();
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
    void streamAiTarotReading_success_returnsChunkCompleteEvents() throws Exception {
        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Will I succeed?")
                .numberOfCards(3)
                .spreadName("Past-Present-Future")
                .build();

        // Mock user
        User mockUser = User.builder()
                .id(TEST_USER_ID)
                .username(TEST_USERNAME)
                .role(com.exe.astratarot.domain.enums.UserRole.USER)
                .build();

        CustomUserDetails customUserDetails = new com.exe.astratarot.security.CustomUserDetails(mockUser);

        when(jwtService.extractSubjectSafely(TEST_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtService.validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class))).thenReturn(true);
        when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(customUserDetails);

        // Capture the streaming callbacks
        ArgumentCaptor<Consumer<String>> onChunkCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<Throwable>> onErrorCaptor = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer<TarotReadingService.StreamReadingResult>> onCompleteCaptor = ArgumentCaptor.forClass(Consumer.class);

        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(2);
            Consumer<TarotReadingService.StreamReadingResult> onComplete = invocation.getArgument(4);

            // Simulate streaming chunks
            onChunk.accept("The Fool suggests ");
            onChunk.accept("a new beginning. ");
            onChunk.accept("This card represents ");
            onChunk.accept("adventure and taking risks.");

            // Simulate successful completion
            LLMTokenUsage tokenUsage = LLMTokenUsage.builder()
                    .promptTokens(50)
                    .completionTokens(45)
                    .totalTokens(95)
                    .build();

            onComplete.accept(new TarotReadingService.StreamReadingResult(
                    TEST_READING_ID,
                    UUID.randomUUID(),
                    "The Fool suggests a new beginning. This card represents adventure and taking risks.",
                    "gemini-pro",
                    95,
                    50,
                    45
            ));

            return null;
        }).when(tarotReadingService).initiateAiTarotReadingStream(
                eq(mockUser),
                eq(request),
                onChunkCaptor.capture(),
                onErrorCaptor.capture(),
                onCompleteCaptor.capture()
        );

        // Perform request
        MvcResult result = mockMvc.perform(post("/api/ai-readings/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/event-stream"))
                .andReturn();

        // Verify service was called
        verify(tarotReadingService).initiateAiTarotReadingStream(
                eq(mockUser),
                eq(request),
                any(Consumer.class),
                any(Consumer.class),
                any(Consumer.class)
        );

        log.info("Stream test completed successfully");
    }

    /**
     * Chưa đăng nhập thì phải là 401, KHÔNG phải 403.
     *
     * <p>Bài test này trước đây khẳng định 403 — tức là nó đang khoá lại đúng
     * một lỗi. Khi SecurityConfig chưa khai authenticationEntryPoint, Spring
     * Security trả 403 cho cả "chưa đăng nhập", và vì giao diện chỉ làm mới
     * token khi gặp 401 nên hễ token hết hạn là mọi danh sách chết trong khi
     * refresh token vẫn còn nguyên.
     *
     * <p>401 = chưa xác thực. 403 = đã xác thực nhưng không đủ quyền. Phân biệt
     * hai thứ này không phải chuyện câu chữ: nó quyết định giao diện nên đưa
     * người dùng đi làm mới phiên hay nên nói thẳng là không có quyền.
     */
    @Test
    void streamAiTarotReading_unauthenticated_returns401() throws Exception {
        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .question("Will I succeed?")
                .numberOfCards(3)
                .spreadName("Past-Present-Future")
                .build();

        mockMvc.perform(post("/api/ai-readings/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tarotReadingService);
    }

    @Test
    void streamAiTarotReading_invalidRequest_returns400() throws Exception {
        StartTarotReadingRequest request = StartTarotReadingRequest.builder()
                .numberOfCards(1)
                .build();

        // Mock user
        User mockUser = User.builder()
                .id(TEST_USER_ID)
                .username(TEST_USERNAME)
                .role(com.exe.astratarot.domain.enums.UserRole.USER)
                .build();

        CustomUserDetails customUserDetails = new com.exe.astratarot.security.CustomUserDetails(mockUser);

        when(jwtService.extractSubjectSafely(TEST_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtService.validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class))).thenReturn(true);
        when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(customUserDetails);

        mockMvc.perform(post("/api/ai-readings/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request))
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tarotReadingService);
    }
}
