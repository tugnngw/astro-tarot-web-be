package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GeminiProvider streaming.
 * Uses mocked RestTemplate to verify SSE parsing logic.
 */
class GeminiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate streamingRestTemplate;
    private RestTemplate restTemplate;
    private GeminiProvider provider;

    private void mockStreamResponse(String rawSse) throws Exception {
        InputStream inputStream = new ByteArrayInputStream(rawSse.getBytes(StandardCharsets.UTF_8));
        ClientHttpResponse mockResponse = mock(ClientHttpResponse.class);
        when(mockResponse.getBody()).thenReturn(inputStream);

        doAnswer(invocation -> {
            ResponseExtractor<?> extractor = invocation.getArgument(3, ResponseExtractor.class);
            return extractor.extractData(mockResponse);
        }).when(streamingRestTemplate).execute(
                anyString(), eq(HttpMethod.POST), any(RequestCallback.class), any(ResponseExtractor.class));
    }

    @BeforeEach
    void setUp() {
        streamingRestTemplate = mock(RestTemplate.class);
        restTemplate = mock(RestTemplate.class);
        provider = new GeminiProvider(restTemplate, streamingRestTemplate, objectMapper);

        try {
            var keyField = GeminiProvider.class.getDeclaredField("apiKey");
            keyField.setAccessible(true);
            keyField.set(provider, "test-key");

            var endpointField = GeminiProvider.class.getDeclaredField("apiEndpoint");
            endpointField.setAccessible(true);
            endpointField.set(provider,
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash");
        } catch (Exception e) {
            throw new RuntimeException("Failed to set test values via reflection", e);
        }
    }

    private String sseChunk(String text, boolean finalChunk) {
        try {
            var mapper = new ObjectMapper();
            var root = mapper.createObjectNode();

            var candidate = mapper.createObjectNode();
            var contentObj = mapper.createObjectNode();
            var parts = mapper.createArrayNode();
            var part = mapper.createObjectNode();
            part.put("text", text);
            parts.add(part);
            contentObj.set("parts", parts);
            contentObj.put("role", "model");
            candidate.set("content", contentObj);
            if (finalChunk) {
                candidate.put("finishReason", "STOP");
            }
            var candidates = mapper.createArrayNode();
            candidates.add(candidate);
            root.set("candidates", candidates);

            if (finalChunk) {
                var usage = mapper.createObjectNode();
                usage.put("promptTokenCount", 10);
                usage.put("candidatesTokenCount", 25);
                usage.put("totalTokenCount", 35);
                root.set("usageMetadata", usage);
            }

            return "data: " + mapper.writeValueAsString(root) + "\n\n";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void generateStream_multipleChunks_emitsAllAndCompletes() throws Exception {
        String raw = sseChunk("The ", false)
                + sseChunk("cards say ", false)
                + sseChunk("yes.", true);

        mockStreamResponse(raw);

        StringBuilder accumulated = new StringBuilder();
        AtomicReference<StreamCompletion> completionRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        provider.generateStream("test prompt",
                chunk -> accumulated.append(chunk),
                errorRef::set,
                completionRef::set);

        assertEquals("The cards say yes.", accumulated.toString());
        assertNull(errorRef.get(), "No error expected");

        StreamCompletion completion = completionRef.get();
        assertNotNull(completion);
        assertEquals("gemini-2.5-flash", completion.getModelInfo());
        assertNotNull(completion.getTokenUsage());
        assertEquals(10, completion.getTokenUsage().getPromptTokens());
        assertEquals(25, completion.getTokenUsage().getCompletionTokens());
        assertEquals(35, completion.getTokenUsage().getTotalTokens());
    }

    @Test
    void generateStream_singleChunk_completesWithUsage() throws Exception {
        mockStreamResponse(sseChunk("Complete response.", true));

        AtomicInteger chunkCount = new AtomicInteger(0);
        AtomicReference<String> lastChunk = new AtomicReference<>();
        AtomicReference<StreamCompletion> completionRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        provider.generateStream("test",
                chunk -> { chunkCount.incrementAndGet(); lastChunk.set(chunk); },
                errorRef::set,
                completionRef::set);

        assertEquals(1, chunkCount.get());
        assertEquals("Complete response.", lastChunk.get());
        assertNotNull(completionRef.get());
        assertEquals(35, completionRef.get().getTokenUsage().getTotalTokens());
        assertNull(errorRef.get());
    }

    @Test
    void generateStream_emptyChunks_skipsEmptyText() throws Exception {
        String raw = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}],\"role\":\"model\"}}]}\n\n"
                + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"real\"}],\"role\":\"model\"}}],\"usageMetadata\":{\"totalTokenCount\":5}}\n\n";

        mockStreamResponse(raw);

        StringBuilder accumulated = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        provider.generateStream("test",
                chunk -> accumulated.append(chunk),
                errorRef::set,
                completion -> {});

        assertEquals("real", accumulated.toString());
        assertNull(errorRef.get());
    }

    @Test
    void generateStream_blankLines_ignored() throws Exception {
        String raw = "\n"
                + "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hello\"}],\"role\":\"model\"}}],\"usageMetadata\":{\"totalTokenCount\":3}}\n\n"
                + "\n"
                + ":\n"
                + "\n";

        mockStreamResponse(raw);

        AtomicReference<String> result = new AtomicReference<>("");

        provider.generateStream("test",
                chunk -> result.set(result.get() + chunk),
                error -> {},
                completion -> {});

        assertEquals("hello", result.get());
    }
}
