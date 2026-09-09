package com.exe.astratarot.service.impl;

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
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Nhà cung cấp LLM theo chuẩn API của OpenAI (POST /chat/completions).
 *
 * Groq và Cerebras đều nói đúng phương ngữ này, nên cùng một lớp chạy được cả
 * hai — chỉ khác ở base URL, khoá và tên model, khai qua biến môi trường. Đổi
 * nhà cung cấp là đổi ba biến, không đụng code:
 *
 *   Groq     : base https://api.groq.com/openai/v1   model llama-3.3-70b-versatile
 *   Cerebras : base https://api.cerebras.ai/v1       model llama-3.3-70b
 *
 * Bật lớp này bằng llm.provider=openai. Khi để mặc định (gemini) thì lớp không
 * được tạo, và GeminiProvider giữ vai trò như cũ — nhờ vậy hai provider không
 * bao giờ cùng tồn tại để Spring phải phân vân chọn cái nào.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
public class OpenAiCompatibleProvider implements LLMProvider {

    private final RestTemplate restTemplate;
    private final RestTemplate streamingRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.openai.base-url}")
    private String baseUrl;

    @Value("${llm.openai.api-key}")
    private String apiKey;

    @Value("${llm.openai.model}")
    private String model;

    public OpenAiCompatibleProvider(RestTemplate restTemplate,
                                    @Qualifier("streamingRestTemplate") RestTemplate streamingRestTemplate,
                                    ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.streamingRestTemplate = streamingRestTemplate;
        this.objectMapper = objectMapper;
    }

    /** base URL bỏ dấu "/" thừa ở cuối rồi ghép đường dẫn chuẩn. */
    private String chatUrl() {
        String b = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return b + "/chat/completions";
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(apiKey);
        return h;
    }

    private Map<String, Object> body(String prompt, boolean stream) {
        Map<String, Object> b = new java.util.HashMap<>();
        b.put("model", model);
        b.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        if (stream) {
            b.put("stream", true);
            // Xin luôn số token ở chunk cuối; nhà cung cấp nào không trả thì ta
            // bỏ qua, không phải lỗi.
            b.put("stream_options", Map.of("include_usage", true));
        }
        return b;
    }

    @Override
    public LLMResponse generate(String prompt) {
        try {
            HttpEntity<String> req = new HttpEntity<>(
                    objectMapper.writeValueAsString(body(prompt, false)), headers());
            String res = restTemplate.postForObject(chatUrl(), req, String.class);
            JsonNode root = objectMapper.readTree(res);

            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isEmpty()) {
                throw new LLMProviderException("Phản hồi LLM rỗng hoặc sai định dạng");
            }
            return LLMResponse.builder()
                    .content(content)
                    .modelInfo(root.path("model").asText(model))
                    .tokenUsage(usage(root.path("usage")))
                    .build();
        } catch (LLMProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMProviderException("Gọi LLM thất bại: " + e.getMessage(), e);
        }
    }

    @Override
    public void generateStream(String prompt,
                               Consumer<String> onChunk,
                               Consumer<Throwable> onError,
                               Consumer<StreamCompletion> onComplete) {
        try {
            HttpHeaders h = headers();
            h.setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
            byte[] payload = objectMapper.writeValueAsBytes(body(prompt, true));

            streamingRestTemplate.execute(chatUrl(), HttpMethod.POST, (RequestCallback) req -> {
                req.getHeaders().putAll(h);
                req.getBody().write(payload);
            }, (ClientHttpResponse response) -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {

                    String line;
                    LLMTokenUsage tokenUsage = null;
                    String modelInfo = model;

                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) continue;
                        String data = line.substring(5).trim();
                        // Dấu chấm hết của chuẩn OpenAI.
                        if (data.isEmpty() || "[DONE]".equals(data)) continue;

                        JsonNode node = objectMapper.readTree(data);
                        if (node.has("model")) modelInfo = node.get("model").asText(modelInfo);

                        String text = node.path("choices").path(0).path("delta").path("content").asText("");
                        if (!text.isEmpty()) onChunk.accept(text);

                        // Chunk cuối (khi include_usage) mang usage và choices rỗng.
                        JsonNode u = node.path("usage");
                        if (!u.isMissingNode() && !u.isNull()) {
                            LLMTokenUsage parsed = usage(u);
                            if (parsed != null) tokenUsage = parsed;
                        }
                    }

                    onComplete.accept(StreamCompletion.builder()
                            .modelInfo(modelInfo)
                            .tokenUsage(tokenUsage)
                            .build());
                } catch (Exception e) {
                    onError.accept(new LLMProviderException(
                            "Lỗi đọc luồng phản hồi LLM: " + e.getMessage(), e));
                }
                return null;
            });
        } catch (Exception e) {
            onError.accept(new LLMProviderException(
                    "Gọi luồng LLM thất bại: " + e.getMessage(), e));
        }
    }

    /** Chuẩn OpenAI: usage.prompt_tokens / completion_tokens / total_tokens. */
    private LLMTokenUsage usage(JsonNode u) {
        if (u == null || u.isMissingNode() || u.isNull()) return null;
        Integer p = u.has("prompt_tokens") ? u.get("prompt_tokens").asInt() : null;
        Integer c = u.has("completion_tokens") ? u.get("completion_tokens").asInt() : null;
        Integer t = u.has("total_tokens") ? u.get("total_tokens").asInt() : null;
        if (p == null && c == null && t == null) return null;
        return LLMTokenUsage.builder()
                .promptTokens(p)
                .completionTokens(c)
                .totalTokens(t)
                .build();
    }
}
