package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.LLMMessage;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.exception.LLMProviderException;
import com.exe.astratarot.service.LLMProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gemini provider implementation for LLM interactions.
 * Uses RestTemplate to call the Gemini API.
 *
 * <p>Supports structured requests with proper role separation:
 * <ul>
 *   <li>System instruction → Gemini {@code systemInstruction} field</li>
 *   <li>User messages → {@code contents} with {@code role: "user"}</li>
 *   <li>Assistant messages → {@code contents} with {@code role: "model"}</li>
 * </ul>
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiProvider implements LLMProvider {

    private final RestTemplate restTemplate;
    private final RestTemplate streamingRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.endpoint}")
    private String apiEndpoint;

    /**
     * Endpoint đã bỏ hậu tố phương thức, để hai lối gọi tự ghép đúng cái của
     * mình.
     *
     * Trước đây gọi thường ghép "?key=" (ngầm cần endpoint kết thúc bằng
     * ":generateContent") còn gọi luồng ghép ":streamGenerateContent" (ngầm cần
     * endpoint KHÔNG có phương thức). Một giá trị cấu hình không thể vừa lòng cả
     * hai: để ":generateContent" thì luồng thành
     * "...:generateContent:streamGenerateContent"; để trống thì gọi thường mất
     * hẳn phương thức. Chuẩn hoá về gốc ở đây nên cấu hình để kiểu nào cũng
     * chạy — cả URL có sẵn ":generateContent" lẫn URL gốc tới model.
     */
    private String baseEndpoint() {
        return apiEndpoint == null ? ""
                : apiEndpoint.replaceAll(":(generateContent|streamGenerateContent)$", "");
    }

    public GeminiProvider(RestTemplate restTemplate,
                          @Qualifier("streamingRestTemplate") RestTemplate streamingRestTemplate,
                          ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.streamingRestTemplate = streamingRestTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public LLMResponse generate(LLMRequest request) {
        int retries = 3;
        long delayMs = 1000;

        for (int attempt = 0; attempt < retries; attempt++) {
            try {
                return callGeminiApi(request);
            } catch (HttpClientErrorException e) {
                HttpStatusCode statusCode = e.getStatusCode();
                if (statusCode.value() == 429) { // Too Many Requests
                    handleRetryableError(attempt, retries, delayMs, "Rate limit");
                    delayMs *= 2;
                } else if (statusCode.value() >= 500 && statusCode.value() < 600) {
                    handleRetryableError(attempt, retries, delayMs, "Server error");
                    delayMs *= 2;
                } else if (statusCode.value() == 400) { // Bad Request
                    throw new LLMProviderException("Invalid request to Gemini API: " + e.getMessage(), e);
                } else if (statusCode.value() == 401) { // Unauthorized
                    throw new LLMProviderException("Invalid Gemini API key: " + e.getMessage(), e);
                } else {
                    throw new LLMProviderException("Gemini API error: " + statusCode.value() + " - " + e.getMessage(), e);
                }
            } catch (ResourceAccessException e) {
                // ResourceAccessException is retryable
                handleRetryableError(attempt, retries, delayMs, "Network error");
                delayMs *= 2;
            }
        }

        // If loop finishes without returning, it means all retries failed
        throw new LLMProviderException("Failed to generate response from Gemini after retries");
    }

    /**
     * Synchronous call to Gemini API (non-streaming).
     */
    private LLMResponse callGeminiApi(LLMRequest request) {
        try {
            Map<String, Object> requestBody = buildRequestBody(request);

            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            // Add API key to URL parameter as per Gemini API documentation
            String url = baseEndpoint() + ":generateContent?key=" + apiKey;

            // Create request entity
            HttpEntity<String> req = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            // Call Gemini API
            String response = restTemplate.postForObject(url, req, String.class);

            // Parse response
            JsonNode responseNode = objectMapper.readTree(response);
            String content = extractContent(responseNode);
            LLMTokenUsage tokenUsage = extractTokenUsage(responseNode);

            return LLMResponse.builder()
                    .content(content)
                    .modelInfo("gemini-pro") // Hardcoded for now, could be dynamic
                    .tokenUsage(tokenUsage)
                    .build();

        } catch (HttpClientErrorException e) {
            // Re-throw as LLMProviderException for consistency
            // Specific status codes handled in the main generate method's catch block
            throw e; // Re-throw to be caught by the main method's retry logic or specific handling
        } catch (ResourceAccessException e) {
            // Re-throw ResourceAccessException directly to be caught by the retry logic
            throw e;
        } catch (Exception e) {
            // Catch-all for JSON parsing errors or other unexpected issues
            throw new LLMProviderException("Error processing Gemini API response: " + e.getMessage(), e);
        }
    }

    /**
     * Handles retry logic for specific error types.
     * @param attempt Current attempt number.
     * @param retries Total allowed retries.
     * @param delayMs Current delay before retry.
     * @param errorType Type of error encountered (e.g., "Rate limit", "Network error").
     */
    private void handleRetryableError(int attempt, int retries, long delayMs, String errorType) {
        if (attempt < retries - 1) {
            log.warn("{} by Gemini API. Retrying in {}ms...", errorType, delayMs);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new LLMProviderException("Interrupted during retry backoff", ie);
            }
        } else {
            throw new LLMProviderException(errorType + " by Gemini API after retries.");
        }
    }

    @Override
    public void generateStream(LLMRequest request,
                               Consumer<String> onChunk,
                               Consumer<Throwable> onError,
                               Consumer<StreamCompletion> onComplete) {
        try {
            String url = baseEndpoint() + ":streamGenerateContent?alt=sse&key=" + apiKey;

            Map<String, Object> requestBody = buildRequestBody(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.TEXT_EVENT_STREAM));

            HttpEntity<String> req = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);

            streamingRestTemplate.execute(url, HttpMethod.POST, (RequestCallback) r -> {
                r.getHeaders().putAll(req.getHeaders());
                r.getBody().write(objectMapper.writeValueAsBytes(requestBody));
            }, (ClientHttpResponse response) -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {

                    String line;
                    String modelVersion = null;
                    LLMTokenUsage tokenUsage = null;

                    while ((line = reader.readLine()) != null) {
                        // Skip blank lines and non-data lines
                        if (!line.startsWith("data: ")) continue;

                        String jsonData = line.substring(6).trim();
                        if (jsonData.isEmpty()) continue;

                        JsonNode node = objectMapper.readTree(jsonData);

                        // Extract modelVersion from any chunk (typically first or last)
                        if (node.has("modelVersion") && !node.get("modelVersion").isNull()) {
                            modelVersion = node.get("modelVersion").asText();
                        }

                        // Extract text chunk
                        String text = extractChunkText(node);
                        if (text != null && !text.isEmpty()) {
                            onChunk.accept(text);
                        }

                        // Capture usageMetadata — appears in final chunk
                        if (node.has("usageMetadata") && !node.get("usageMetadata").isMissingNode()) {
                            tokenUsage = extractTokenUsage(node);
                        }
                    }

                    // Stream ended normally
                    String model = modelVersion != null ? modelVersion : "gemini-2.5-flash";
                    onComplete.accept(StreamCompletion.builder()
                            .modelInfo(model)
                            .tokenUsage(tokenUsage)
                            .build());

                } catch (Exception e) {
                    onError.accept(new LLMProviderException(
                            "Error reading Gemini streaming response: " + e.getMessage(), e));
                }
                return null;
            });

        } catch (Exception e) {
            onError.accept(new LLMProviderException(
                    "Gemini streaming request failed: " + e.getMessage(), e));
        }
    }

    /**
     * Builds Gemini API request body from structured LLMRequest.
     *
     * <p>Maps:
     * <ul>
     *   <li>{@code systemInstruction} → Gemini {@code systemInstruction} field</li>
     *   <li>{@code USER} messages → {@code contents} with {@code role: "user"}</li>
     *   <li>{@code ASSISTANT} messages → {@code contents} with {@code role: "model"}</li>
     * </ul>
     *
     * <p>Generation config (temperature, maxTokens) is included only when set.
     */
    private Map<String, Object> buildRequestBody(LLMRequest request) {
        Map<String, Object> requestBody = new HashMap<>();

        // System instruction → systemInstruction field (not mixed into contents)
        String systemInstruction = request.getSystemInstruction();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            Map<String, Object> systemPart = new HashMap<>();
            systemPart.put("text", systemInstruction);
            requestBody.put("systemInstruction", Map.of("parts", new Object[]{systemPart}));
        }

        // Conversation messages → contents array
        List<Map<String, Object>> contents = new ArrayList<>();
        List<LLMMessage> messages = request.getMessages();
        if (messages != null) {
            for (LLMMessage msg : messages) {
                if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) {
                    continue;
                }

                Map<String, Object> content = new HashMap<>();
                // Map role: USER → "user", ASSISTANT → "model" (Gemini convention)
                if (msg.getRole() == LLMMessage.Role.USER) {
                    content.put("role", "user");
                } else if (msg.getRole() == LLMMessage.Role.ASSISTANT) {
                    content.put("role", "model");
                } else {
                    // SYSTEM messages should not be here (handled via systemInstruction)
                    // Skip or treat as user
                    content.put("role", "user");
                }

                Map<String, Object> part = new HashMap<>();
                part.put("text", msg.getContent());
                content.put("parts", new Object[]{part});

                contents.add(content);
            }
        }

        // Fallback: if no contents, add empty array (Gemini requires at least one)
        requestBody.put("contents", contents.toArray());

        // Generation config (optional — only include when set)
        Map<String, Object> generationConfig = new HashMap<>();
        boolean hasGenerationConfig = false;

        if (request.getTemperature() != null) {
            generationConfig.put("temperature", request.getTemperature());
            hasGenerationConfig = true;
        }
        if (request.getMaxTokens() != null) {
            generationConfig.put("maxOutputTokens", request.getMaxTokens());
            hasGenerationConfig = true;
        }

        if (hasGenerationConfig) {
            requestBody.put("generationConfig", generationConfig);
        }

        return requestBody;
    }

    /**
     * Extracts the text fragment from a Gemini streaming SSE chunk.
     * Navigates candidates[0].content.parts[0].text.
     */
    private String extractChunkText(JsonNode node) {
        try {
            JsonNode candidates = node.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content = candidates.get(0).path("content");
                if (content.has("parts") && content.get("parts").isArray()
                        && content.get("parts").size() > 0) {
                    JsonNode text = content.get("parts").get(0).get("text");
                    if (text != null && !text.isNull()) {
                        return text.asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract text from SSE chunk: {}", e.getMessage());
        }
        return null;
    }

    private String extractContent(JsonNode responseNode) {
        try {
            JsonNode candidatesNode = responseNode.path("candidates");
            if (candidatesNode.isArray() && candidatesNode.size() > 0) {
                JsonNode firstCandidate = candidatesNode.get(0);
                JsonNode contentNode = firstCandidate.path("content");
                if (contentNode.has("parts") && contentNode.get("parts").isArray() && contentNode.get("parts").size() > 0) {
                    JsonNode textNode = contentNode.get("parts").get(0).get("text");
                    if (textNode != null) {
                        return textNode.asText();
                    }
                }
            }
            // Fallback for unexpected structure or missing content
            log.warn("Could not extract text content from Gemini response. Raw response snippet: {}", responseNode.toString().substring(0, Math.min(responseNode.toString().length(), 200)));
            throw new LLMProviderException("Unable to extract text content from Gemini API response.");
        } catch (Exception e) {
            throw new LLMProviderException("Error extracting content from Gemini response: " + e.getMessage(), e);
        }
    }

    private LLMTokenUsage extractTokenUsage(JsonNode responseNode) {
        try {
            JsonNode usageMetadataNode = responseNode.path("usageMetadata");
            if (!usageMetadataNode.isMissingNode()) {
                Integer promptTokens = usageMetadataNode.has("promptTokenCount") ? usageMetadataNode.get("promptTokenCount").asInt() : null;
                Integer completionTokens = usageMetadataNode.has("candidatesTokenCount") ? usageMetadataNode.get("candidatesTokenCount").asInt() : null;
                Integer totalTokens = usageMetadataNode.has("totalTokenCount") ? usageMetadataNode.get("totalTokenCount").asInt() : null;

                // Only return token usage if at least one count is available
                if (promptTokens != null || completionTokens != null || totalTokens != null) {
                    return LLMTokenUsage.builder()
                            .promptTokens(promptTokens)
                            .completionTokens(completionTokens)
                            .totalTokens(totalTokens)
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract token usage from Gemini response: {}", e.getMessage());
        }
        return null; // Return null if usage metadata is not found or parsing fails
    }
}
