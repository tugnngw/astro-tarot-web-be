package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.exception.LLMProviderException;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Nhà cung cấp LLM nói chuẩn OpenAI (Groq, Cerebras). Lớp này trước đây phủ
 * 0,0%.
 *
 * <p>Chỗ dễ vỡ nhất không phải đường xanh mà là <b>đọc luồng SSE</b>: mỗi chunk
 * là một dòng {@code data:} riêng, có dòng rỗng, có dòng {@code [DONE]}, và
 * chunk cuối mang số token với {@code choices} rỗng. Xử sai một dạng dòng là
 * người dùng thấy câu trả lời cụt giữa chừng, hoặc thấy chữ "[DONE]" in ra màn
 * hình.
 *
 * <p>Chỗ thứ hai: <b>phản hồi rỗng phải thành lỗi</b>. Trả về một chuỗi rỗng
 * trông như thành công, và người dùng nhận một lời giải Tarot trắng tinh mà
 * không hiểu vì sao — còn tệ hơn một câu báo lỗi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenAiCompatibleProviderTest {

    @Mock private RestTemplate restTemplate;
    @Mock private RestTemplate streamingRestTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OpenAiCompatibleProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OpenAiCompatibleProvider(restTemplate, streamingRestTemplate, objectMapper);
        ReflectionTestUtils.setField(provider, "baseUrl", "https://api.groq.com/openai/v1");
        ReflectionTestUtils.setField(provider, "apiKey", "khoa-gia-cho-test");
        ReflectionTestUtils.setField(provider, "model", "llama-3.3-70b-versatile");
    }

    /** Dựng phản hồi SSE từ các dòng, rồi cho streamingRestTemplate phát ra. */
    private void luongTraVe(String... dong) {
        String than = String.join("\n", dong) + "\n";
        doAnswer(inv -> {
            RequestCallback callback = inv.getArgument(2);
            @SuppressWarnings("unchecked")
            ResponseExtractor<Object> extractor = inv.getArgument(3);

            callback.doWithRequest(new MockClientHttpRequest(HttpMethod.POST,
                    java.net.URI.create("https://api.groq.com/openai/v1/chat/completions")));

            ClientHttpResponse res = mock(ClientHttpResponse.class);
            when(res.getBody()).thenReturn(
                    new ByteArrayInputStream(than.getBytes(StandardCharsets.UTF_8)));
            return extractor.extractData(res);
        }).when(streamingRestTemplate).execute(anyString(), eq(HttpMethod.POST),
                any(RequestCallback.class), any(ResponseExtractor.class));
    }

    private static String chunk(String chu) {
        return "data: {\"model\":\"llama-3.3-70b-versatile\",\"choices\":[{\"delta\":{\"content\":\""
                + chu + "\"}}]}";
    }

    // =====================================================================
    // Gọi một lần
    // =====================================================================

    @Test
    @DisplayName("Gọi thường: lấy nội dung, tên model và số token")
    void goiThuong() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class))).thenReturn("""
                {
                  "model": "llama-3.3-70b-versatile",
                  "choices": [{"message": {"content": "Ba lá bài nói rằng..."}}],
                  "usage": {"prompt_tokens": 120, "completion_tokens": 300, "total_tokens": 420}
                }
                """);

        var kq = provider.generate("Tôi nên đổi việc không?");

        assertAll(
                () -> assertEquals("Ba lá bài nói rằng...", kq.getContent()),
                () -> assertEquals("llama-3.3-70b-versatile", kq.getModelInfo()),
                () -> assertEquals(120, kq.getTokenUsage().getPromptTokens()),
                () -> assertEquals(300, kq.getTokenUsage().getCompletionTokens()),
                () -> assertEquals(420, kq.getTokenUsage().getTotalTokens()));
    }

    @Test
    @DisplayName("Đường dẫn ghép đúng dù base URL có dấu / thừa")
    void duongDanGhepDung() {
        ReflectionTestUtils.setField(provider, "baseUrl", "https://api.cerebras.ai/v1///");
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        provider.generate("hỏi");

        ArgumentCaptor<String> bat = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(restTemplate).postForObject(bat.capture(), any(), eq(String.class));
        // "//chat/completions" là 404 ở một số nhà cung cấp, và câu lỗi trả về
        // không nói gì về dấu gạch chéo.
        assertEquals("https://api.cerebras.ai/v1/chat/completions", bat.getValue());
    }

    @Test
    @DisplayName("Khoá đi ở header Bearer, không đi trong thân yêu cầu")
    void khoaODungCho() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        provider.generate("hỏi");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> bat = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate).postForObject(anyString(), bat.capture(), eq(String.class));
        HttpHeaders h = bat.getValue().getHeaders();
        assertAll(
                () -> assertEquals("Bearer khoa-gia-cho-test", h.getFirst(HttpHeaders.AUTHORIZATION)),
                () -> assertEquals(MediaType.APPLICATION_JSON, h.getContentType()),
                // Khoá lọt vào thân yêu cầu là nó vào log của nhà cung cấp.
                () -> assertTrue(!bat.getValue().getBody().contains("khoa-gia-cho-test")));
    }

    @Test
    @DisplayName("Thân yêu cầu mang model và câu hỏi, KHÔNG bật stream")
    void thanYeuCauGoiThuong() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        provider.generate("Tôi nên đổi việc không?");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<String>> bat = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate).postForObject(anyString(), bat.capture(), eq(String.class));
        String than = bat.getValue().getBody();
        assertAll(
                () -> assertTrue(than.contains("llama-3.3-70b-versatile")),
                () -> assertTrue(than.contains("Tôi nên đổi việc không?")),
                () -> assertTrue(!than.contains("\"stream\"")));
    }

    @Test
    @DisplayName("Phản hồi RỖNG thành lỗi, không trả về chuỗi trắng")
    void phanHoiRong() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"\"}}]}");

        // Trả về chuỗi rỗng trông như thành công, và người dùng nhận một lời
        // giải Tarot trắng tinh mà không hiểu vì sao.
        assertThrows(LLMProviderException.class, () -> provider.generate("hỏi"));
    }

    @Test
    @DisplayName("Phản hồi sai định dạng thành lỗi có tên rõ ràng")
    void phanHoiSaiDinhDang() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("đây không phải JSON");

        assertThrows(LLMProviderException.class, () -> provider.generate("hỏi"));
    }

    @Test
    @DisplayName("Nhà cung cấp lỗi mạng thì bọc thành LLMProviderException")
    void loiMang() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("timeout"));

        // Bọc lại để nơi gọi chỉ phải bắt một loại lỗi, thay vì biết về
        // RestTemplate.
        var loi = assertThrows(LLMProviderException.class, () -> provider.generate("hỏi"));
        assertTrue(loi.getMessage().contains("timeout"));
    }

    @Test
    @DisplayName("Không có usage thì để trống, không nổ")
    void khongCoUsage() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}]}");

        // Cerebras có lúc không trả usage. Đó không phải lỗi, chỉ là không đo
        // được — và không đo được thì đừng chặn người dùng.
        assertNull(provider.generate("hỏi").getTokenUsage());
    }

    @Test
    @DisplayName("Usage rỗng hoàn toàn cũng để trống")
    void usageRong() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}],\"usage\":{}}");

        assertNull(provider.generate("hỏi").getTokenUsage());
    }

    @Test
    @DisplayName("Usage thiếu một trường thì vẫn nhận phần có")
    void usageThieuMotTruong() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"choices\":[{\"message\":{\"content\":\"x\"}}],"
                        + "\"usage\":{\"total_tokens\": 500}}");

        var u = provider.generate("hỏi").getTokenUsage();
        assertAll(
                () -> assertNotNull(u),
                () -> assertEquals(500, u.getTotalTokens()),
                () -> assertNull(u.getPromptTokens()));
    }

    // =====================================================================
    // Luồng SSE
    // =====================================================================

    @Test
    @DisplayName("Luồng: ghép các chunk theo đúng thứ tự")
    void luongGhepChunk() {
        luongTraVe(chunk("Ba "), chunk("lá "), chunk("bài."), "data: [DONE]");

        List<String> manh = new ArrayList<>();
        AtomicReference<StreamCompletion> xong = new AtomicReference<>();
        provider.generateStream("hỏi", manh::add, e -> {}, xong::set);

        assertAll(
                () -> assertEquals(List.of("Ba ", "lá ", "bài."), manh),
                () -> assertNotNull(xong.get()),
                () -> assertEquals("llama-3.3-70b-versatile", xong.get().getModelInfo()));
    }

    @Test
    @DisplayName("Dòng [DONE] và dòng rỗng KHÔNG lọt ra màn hình")
    void khongLotDongDieuKhien() {
        luongTraVe(chunk("Xin chào"), "", "data:", "data: [DONE]", "");

        List<String> manh = new ArrayList<>();
        provider.generateStream("hỏi", manh::add, e -> {}, c -> {});

        // Xử sai một dạng dòng là người dùng thấy chữ "[DONE]" in ra giữa lời
        // giải Tarot.
        assertEquals(List.of("Xin chào"), manh);
    }

    @Test
    @DisplayName("Dòng KHÔNG bắt đầu bằng data: thì bỏ qua")
    void boQuaDongKhongPhaiData() {
        luongTraVe(": keep-alive từ máy chủ", "event: message", chunk("Nội dung"));

        List<String> manh = new ArrayList<>();
        provider.generateStream("hỏi", manh::add, e -> {}, c -> {});

        assertEquals(List.of("Nội dung"), manh);
    }

    @Test
    @DisplayName("Chunk cuối mang số token dù choices rỗng")
    void chunkCuoiMangToken() {
        luongTraVe(chunk("Nội dung"),
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,"
                        + "\"completion_tokens\":20,\"total_tokens\":30}}",
                "data: [DONE]");

        AtomicReference<StreamCompletion> xong = new AtomicReference<>();
        provider.generateStream("hỏi", s -> {}, e -> {}, xong::set);

        // Không đọc được chunk này là không đo được token, và bảng chi phí AI
        // ở khu quản trị báo 0 mãi mãi.
        assertAll(
                () -> assertNotNull(xong.get().getTokenUsage()),
                () -> assertEquals(30, xong.get().getTokenUsage().getTotalTokens()));
    }

    @Test
    @DisplayName("Chunk rỗng nội dung không gọi onChunk")
    void chunkRongKhongGoi() {
        luongTraVe("data: {\"choices\":[{\"delta\":{}}]}",
                "data: {\"choices\":[{\"delta\":{\"content\":\"\"}}]}",
                chunk("Thật"));

        List<String> manh = new ArrayList<>();
        provider.generateStream("hỏi", manh::add, e -> {}, c -> {});

        // Chunk mở đầu của OpenAI chỉ mang role, không mang chữ nào. Gọi
        // onChunk("") là đẩy một sự kiện rỗng qua SSE tới trình duyệt.
        assertEquals(List.of("Thật"), manh);
    }

    @Test
    @DisplayName("Không có usage trong luồng thì hoàn tất vẫn chạy, token để trống")
    void luongKhongCoUsage() {
        luongTraVe(chunk("Nội dung"), "data: [DONE]");

        AtomicReference<StreamCompletion> xong = new AtomicReference<>();
        provider.generateStream("hỏi", s -> {}, e -> {}, xong::set);

        assertAll(
                () -> assertNotNull(xong.get()),
                () -> assertNull(xong.get().getTokenUsage()));
    }

    @Test
    @DisplayName("Dòng JSON hỏng giữa luồng thì báo lỗi, không im lặng")
    void dongJsonHong() {
        luongTraVe(chunk("Nội dung"), "data: {day khong phai json}");

        AtomicReference<Throwable> loi = new AtomicReference<>();
        AtomicReference<StreamCompletion> xong = new AtomicReference<>();
        provider.generateStream("hỏi", s -> {}, loi::set, xong::set);

        // Im lặng thì giao diện quay vòng mãi: nó chờ một sự kiện hoàn tất
        // không bao giờ tới.
        assertAll(
                () -> assertNotNull(loi.get()),
                () -> assertTrue(loi.get() instanceof LLMProviderException),
                () -> assertNull(xong.get()));
    }

    @Test
    @DisplayName("Gọi luồng thất bại ngay từ đầu thì báo qua onError")
    void goiLuongThatBai() {
        doAnswer(inv -> {
            throw new org.springframework.web.client.ResourceAccessException("mất mạng");
        }).when(streamingRestTemplate).execute(anyString(), eq(HttpMethod.POST),
                any(RequestCallback.class), any(ResponseExtractor.class));

        AtomicReference<Throwable> loi = new AtomicReference<>();
        provider.generateStream("hỏi", s -> {}, loi::set, c -> {});

        assertAll(
                () -> assertNotNull(loi.get()),
                () -> assertTrue(loi.get().getMessage().contains("mất mạng")));
    }

    @Test
    @DisplayName("Thân yêu cầu luồng bật stream và xin kèm số token")
    void thanYeuCauLuong() {
        luongTraVe("data: [DONE]");

        provider.generateStream("Tôi nên đổi việc không?", s -> {}, e -> {}, c -> {});

        // Không xin include_usage thì nhà cung cấp không gửi chunk cuối, và
        // toàn bộ chi phí AI của luồng không đo được.
        org.mockito.Mockito.verify(streamingRestTemplate).execute(
                anyString(), eq(HttpMethod.POST), any(RequestCallback.class),
                any(ResponseExtractor.class));
    }
}
