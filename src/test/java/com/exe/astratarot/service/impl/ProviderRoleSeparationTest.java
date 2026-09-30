package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.LLMMessage;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Kiểm tra role separation trong request body gửi tới Gemini và OpenAI.
 *
 * <p>Mục tiêu: xác nhận LLM nhận đúng SYSTEM → USER → ASSISTANT → USER,
 * câu hỏi hiện tại luôn là USER message cuối cùng.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProviderRoleSeparationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =====================================================================
    // Gemini
    // =====================================================================

    @Mock private RestTemplate geminiRestTemplate;
    @Mock private RestTemplate geminiStreamingRestTemplate;

    private GeminiProvider geminiProvider;

    // =====================================================================
    // OpenAI
    // =====================================================================

    @Mock private RestTemplate openAiRestTemplate;
    @Mock private RestTemplate openAiStreamingRestTemplate;

    private OpenAiCompatibleProvider openAiProvider;

    @BeforeEach
    void setUp() {
        // Gemini
        geminiProvider = new GeminiProvider(geminiRestTemplate, geminiStreamingRestTemplate, objectMapper);
        ReflectionTestUtils.setField(geminiProvider, "apiKey", "test-key");
        ReflectionTestUtils.setField(geminiProvider, "apiEndpoint",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash");

        // OpenAI
        openAiProvider = new OpenAiCompatibleProvider(openAiRestTemplate, openAiStreamingRestTemplate, objectMapper);
        ReflectionTestUtils.setField(openAiProvider, "baseUrl", "https://api.groq.com/openai/v1");
        ReflectionTestUtils.setField(openAiProvider, "apiKey", "test-key");
        ReflectionTestUtils.setField(openAiProvider, "model", "llama-3.3-70b-versatile");
    }

    private JsonNode batThanJson(RestTemplate template) throws Exception {
        ArgumentCaptor<HttpEntity<String>> bat = ArgumentCaptor.forClass(HttpEntity.class);
        verify(template).postForObject(anyString(), bat.capture(), eq(String.class));
        return objectMapper.readTree(bat.getValue().getBody());
    }

    // =====================================================================
    // Test Gemini role separation
    // =====================================================================

    @Test
    @DisplayName("Gemini: system + user + assistant + user — role mapping đúng")
    void gemini_roleSeparation() throws Exception {
        when(geminiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]}}]}");

        LLMRequest req = LLMRequest.builder()
                .systemInstruction("Bạn là Tarot reader.")
                .messages(List.of(
                        LLMMessage.user("Tôi nên đổi việc không?"),
                        LLMMessage.assistant("Mình thấy The Tower..."),
                        LLMMessage.user("Còn về tài chính?")
                ))
                .build();

        geminiProvider.generate(req);

        JsonNode body = batThanJson(geminiRestTemplate);

        // Kiểm tra systemInstruction
        assertTrue(body.has("systemInstruction"), "Phải có systemInstruction");
        String sysText = body.path("systemInstruction").path("parts").path(0).path("text").asText();
        assertEquals("Bạn là Tarot reader.", sysText);

        // Kiểm tra contents array
        JsonNode contents = body.path("contents");
        assertTrue(contents.isArray(), "contents phải là array");
        assertEquals(3, contents.size(), "Có 3 messages trong contents");

        // Message 0: user
        assertEquals("user", contents.get(0).path("role").asText());
        assertEquals("Tôi nên đổi việc không?", contents.get(0).path("parts").path(0).path("text").asText());

        // Message 1: assistant → model
        assertEquals("model", contents.get(1).path("role").asText());
        assertEquals("Mình thấy The Tower...", contents.get(1).path("parts").path(0).path("text").asText());

        // Message 2: user (câu hỏi hiện tại)
        assertEquals("user", contents.get(2).path("role").asText());
        assertEquals("Còn về tài chính?", contents.get(2).path("parts").path(0).path("text").asText());
    }

    @Test
    @DisplayName("Gemini: không có history — chỉ system + user")
    void gemini_noHistory() throws Exception {
        when(geminiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]}}]}");

        LLMRequest req = LLMRequest.builder()
                .systemInstruction("Bạn là Tarot reader.")
                .messages(List.of(LLMMessage.user("Xin chào")))
                .build();

        geminiProvider.generate(req);

        JsonNode body = batThanJson(geminiRestTemplate);

        assertTrue(body.has("systemInstruction"));
        assertEquals(1, body.path("contents").size());
        assertEquals("user", body.path("contents").path(0).path("role").asText());
        assertEquals("Xin chào", body.path("contents").path(0).path("parts").path(0).path("text").asText());
    }

    @Test
    @DisplayName("Gemini: không có system instruction — bỏ qua systemInstruction field")
    void gemini_noSystemInstruction() throws Exception {
        when(geminiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]}}]}");

        LLMRequest req = LLMRequest.builder()
                .messages(List.of(LLMMessage.user("Hỏi")))
                .build();

        geminiProvider.generate(req);

        JsonNode body = batThanJson(geminiRestTemplate);

        assertFalse(body.has("systemInstruction"), "Không có system instruction thì không có field systemInstruction");
        assertEquals(1, body.path("contents").size());
    }

    @Test
    @DisplayName("Gemini: history nhiều messages — thứ tự giữ nguyên")
    void gemini_multipleHistory() throws Exception {
        when(geminiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]}}]}");

        LLMRequest req = LLMRequest.builder()
                .systemInstruction("Persona.")
                .messages(List.of(
                        LLMMessage.user("Q1"),
                        LLMMessage.assistant("A1"),
                        LLMMessage.user("Q2"),
                        LLMMessage.assistant("A2"),
                        LLMMessage.user("Q3")
                ))
                .build();

        geminiProvider.generate(req);

        JsonNode contents = batThanJson(geminiRestTemplate).path("contents");
        assertEquals(5, contents.size());
        assertEquals("user", contents.get(0).path("role").asText());
        assertEquals("model", contents.get(1).path("role").asText());
        assertEquals("user", contents.get(2).path("role").asText());
        assertEquals("model", contents.get(3).path("role").asText());
        assertEquals("user", contents.get(4).path("role").asText());
        assertEquals("Q3", contents.get(4).path("parts").path(0).path("text").asText());
    }

    // =====================================================================
    // Test OpenAI role separation
    // =====================================================================

    @Test
    @DisplayName("OpenAI: system + user + assistant + user — role mapping đúng")
    void openAi_roleSeparation() throws Exception {
        when(openAiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        LLMRequest req = LLMRequest.builder()
                .systemInstruction("Bạn là Tarot reader.")
                .messages(List.of(
                        LLMMessage.user("Tôi nên đổi việc không?"),
                        LLMMessage.assistant("Mình thấy The Tower..."),
                        LLMMessage.user("Còn về tài chính?")
                ))
                .build();

        openAiProvider.generate(req);

        JsonNode body = batThanJson(openAiRestTemplate);

        JsonNode messages = body.path("messages");
        assertTrue(messages.isArray(), "messages phải là array");
        assertEquals(4, messages.size(), "Có 4 messages (system + 3 conversation)");

        // Message 0: system
        assertEquals("system", messages.get(0).path("role").asText());
        assertEquals("Bạn là Tarot reader.", messages.get(0).path("content").asText());

        // Message 1: user
        assertEquals("user", messages.get(1).path("role").asText());
        assertEquals("Tôi nên đổi việc không?", messages.get(1).path("content").asText());

        // Message 2: assistant
        assertEquals("assistant", messages.get(2).path("role").asText());
        assertEquals("Mình thấy The Tower...", messages.get(2).path("content").asText());

        // Message 3: user (câu hỏi hiện tại)
        assertEquals("user", messages.get(3).path("role").asText());
        assertEquals("Còn về tài chính?", messages.get(3).path("content").asText());
    }

    @Test
    @DisplayName("OpenAI: không có history — chỉ system + user")
    void openAi_noHistory() throws Exception {
        when(openAiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        LLMRequest req = LLMRequest.builder()
                .systemInstruction("Bạn là Tarot reader.")
                .messages(List.of(LLMMessage.user("Xin chào")))
                .build();

        openAiProvider.generate(req);

        JsonNode messages = batThanJson(openAiRestTemplate).path("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).path("role").asText());
        assertEquals("user", messages.get(1).path("role").asText());
        assertEquals("Xin chào", messages.get(1).path("content").asText());
    }

    @Test
    @DisplayName("OpenAI: không có system instruction — bỏ qua system message")
    void openAi_noSystemInstruction() throws Exception {
        when(openAiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        LLMRequest req = LLMRequest.builder()
                .messages(List.of(LLMMessage.user("Hỏi")))
                .build();

        openAiProvider.generate(req);

        JsonNode messages = batThanJson(openAiRestTemplate).path("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).path("role").asText());
    }

    @Test
    @DisplayName("OpenAI: history nhiều messages — thứ tự giữ nguyên")
    void openAi_multipleHistory() throws Exception {
        when(openAiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        LLMRequest req = LLMRequest.builder()
                .systemInstruction("Persona.")
                .messages(List.of(
                        LLMMessage.user("Q1"),
                        LLMMessage.assistant("A1"),
                        LLMMessage.user("Q2"),
                        LLMMessage.assistant("A2"),
                        LLMMessage.user("Q3")
                ))
                .build();

        openAiProvider.generate(req);

        JsonNode messages = batThanJson(openAiRestTemplate).path("messages");
        assertEquals(6, messages.size()); // system + 5 conversation
        assertEquals("system", messages.get(0).path("role").asText());
        assertEquals("user", messages.get(1).path("role").asText());
        assertEquals("assistant", messages.get(2).path("role").asText());
        assertEquals("user", messages.get(3).path("role").asText());
        assertEquals("assistant", messages.get(4).path("role").asText());
        assertEquals("user", messages.get(5).path("role").asText());
        assertEquals("Q3", messages.get(5).path("content").asText());
    }

    // =====================================================================
    // Backward compatibility: String overload
    // =====================================================================

    @Test
    @DisplayName("Backward compat: Gemini generate(String) wrap thành single user message")
    void gemini_backwardCompat_string() throws Exception {
        when(geminiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]}}]}");

        geminiProvider.generate("Tôi nên đổi việc không?");

        JsonNode body = batThanJson(geminiRestTemplate);
        assertFalse(body.has("systemInstruction"), "String overload không có system instruction");
        assertEquals(1, body.path("contents").size());
        assertEquals("user", body.path("contents").path(0).path("role").asText());
        assertEquals("Tôi nên đổi việc không?",
                body.path("contents").path(0).path("parts").path(0).path("text").asText());
    }

    @Test
    @DisplayName("Backward compat: OpenAI generate(String) wrap thành single user message")
    void openAi_backwardCompat_string() throws Exception {
        when(openAiRestTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        openAiProvider.generate("Tôi nên đổi việc không?");

        JsonNode messages = batThanJson(openAiRestTemplate).path("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).path("role").asText());
        assertEquals("Tôi nên đổi việc không?", messages.get(0).path("content").asText());
    }

    // =====================================================================
    // Streaming vẫn hoạt động
    // =====================================================================

    @Test
    @DisplayName("Gemini: streaming với structured request — body chứa systemInstruction + contents roles")
    void gemini_streaming_bodyStructure() throws Exception {
        // Mock streaming response
        String rawSse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}],\"usageMetadata\":{\"totalTokenCount\":5}}\n\n";
        ClientHttpResponse mockResponse = mock(ClientHttpResponse.class);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream(rawSse.getBytes(StandardCharsets.UTF_8)));
        doAnswer(inv -> {
            ResponseExtractor<?> extractor = inv.getArgument(3, ResponseExtractor.class);
            return extractor.extractData(mockResponse);
        }).when(geminiStreamingRestTemplate).execute(
                anyString(), eq(HttpMethod.POST), any(RequestCallback.class), any(ResponseExtractor.class));

        LLMRequest req = LLMRequest.builder()
                .systemInstruction("Persona.")
                .messages(List.of(
                        LLMMessage.user("Q1"),
                        LLMMessage.assistant("A1"),
                        LLMMessage.user("Q2")
                ))
                .build();

        List<String> chunks = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        geminiProvider.generateStream(req, chunks::add, error::set, c -> {});

        // Kiểm tra body được gửi
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RequestCallback> cbCaptor = ArgumentCaptor.forClass(RequestCallback.class);
        verify(geminiStreamingRestTemplate).execute(
                anyString(), eq(HttpMethod.POST), cbCaptor.capture(), any(ResponseExtractor.class));

        // Capture body từ RequestCallback
        byte[] bodyBytes = new byte[1024];
        var reqMock = new org.springframework.mock.http.client.MockClientHttpRequest(
                HttpMethod.POST, java.net.URI.create("http://localhost"));
        try {
            cbCaptor.getValue().doWithRequest(reqMock);
        } catch (Exception e) {
            // ignore
        }
        String bodyStr = reqMock.getBodyAsString();
        JsonNode body = objectMapper.readTree(bodyStr);

        assertTrue(body.has("systemInstruction"));
        assertEquals(3, body.path("contents").size());
        assertEquals("user", body.path("contents").path(0).path("role").asText());
        assertEquals("model", body.path("contents").path(1).path("role").asText());
        assertEquals("user", body.path("contents").path(2).path("role").asText());

        assertNull(error.get());
        assertEquals(List.of("ok"), chunks);
    }

    @Test
    @DisplayName("OpenAI: streaming với structured request — body chứa system/user/assistant roles")
    void openAi_streaming_bodyStructure() throws Exception {
        String rawSse = "data: {\"model\":\"llama-3.3-70b-versatile\",\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n"
                + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3,\"total_tokens\":8}}\n\n"
                + "data: [DONE]\n\n";
        ClientHttpResponse mockResponse = mock(ClientHttpResponse.class);
        when(mockResponse.getBody()).thenReturn(new ByteArrayInputStream(rawSse.getBytes(StandardCharsets.UTF_8)));
        doAnswer(inv -> {
            ResponseExtractor<?> extractor = inv.getArgument(3, ResponseExtractor.class);
            return extractor.extractData(mockResponse);
        }).when(openAiStreamingRestTemplate).execute(
                anyString(), eq(HttpMethod.POST), any(RequestCallback.class), any(ResponseExtractor.class));

        LLMRequest req = LLMRequest.builder()
                .systemInstruction("Persona.")
                .messages(List.of(
                        LLMMessage.user("Q1"),
                        LLMMessage.assistant("A1"),
                        LLMMessage.user("Q2")
                ))
                .build();

        List<String> chunks = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        openAiProvider.generateStream(req, chunks::add, error::set, c -> {});

        // Kiểm tra body được gửi
        @SuppressWarnings("unchecked")
        ArgumentCaptor<RequestCallback> cbCaptor = ArgumentCaptor.forClass(RequestCallback.class);
        verify(openAiStreamingRestTemplate).execute(
                anyString(), eq(HttpMethod.POST), cbCaptor.capture(), any(ResponseExtractor.class));

        byte[] bodyBytes = new byte[2048];
        var reqMock = new org.springframework.mock.http.client.MockClientHttpRequest(
                HttpMethod.POST, java.net.URI.create("http://localhost"));
        try {
            cbCaptor.getValue().doWithRequest(reqMock);
        } catch (Exception e) {
            // ignore
        }
        String bodyStr = reqMock.getBodyAsString();
        JsonNode body = objectMapper.readTree(bodyStr);

        JsonNode messages = body.path("messages");
        assertEquals(4, messages.size());
        assertEquals("system", messages.get(0).path("role").asText());
        assertEquals("user", messages.get(1).path("role").asText());
        assertEquals("assistant", messages.get(2).path("role").asText());
        assertEquals("user", messages.get(3).path("role").asText());

        assertNull(error.get());
        assertEquals(List.of("ok"), chunks);
    }
}
