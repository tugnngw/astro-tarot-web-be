package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.exception.LLMProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResourceAccessException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gemini: phần luồng SSE, cách ghép URL, và chính sách thử lại — những nhánh bộ
 * kiểm cũ chưa chạm tới.
 *
 * <p>Chỗ đáng kiểm nhất là <b>cách ghép endpoint</b>, vì nó đã là một lỗi thật.
 * Gọi thường cần hậu tố {@code :generateContent}, gọi luồng cần
 * {@code :streamGenerateContent}. Một giá trị cấu hình không thể vừa lòng cả
 * hai: để {@code :generateContent} thì luồng thành
 * {@code ...:generateContent:streamGenerateContent}; để trống thì gọi thường
 * mất hẳn phương thức. Lời giải là chuẩn hoá về gốc — và bộ kiểm này chốt lại
 * rằng cả hai dạng cấu hình đều chạy.
 *
 * <p>Chỗ thứ hai: <b>thử lại đúng loại lỗi</b>. 429 và 5xx là tạm thời nên thử
 * lại; 400 và 401 là sai cấu hình nên thử lại mười lần cũng vậy, chỉ tốn thời
 * gian của người đang chờ.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeminiProviderStreamTest {

    @Mock private RestTemplate restTemplate;
    @Mock private RestTemplate streamingRestTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GeminiProvider provider;

    private static final String GOC =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash";

    @BeforeEach
    void setUp() {
        provider = new GeminiProvider(restTemplate, streamingRestTemplate, objectMapper);
        ReflectionTestUtils.setField(provider, "apiKey", "khoa-gia");
        ReflectionTestUtils.setField(provider, "apiEndpoint", GOC);
    }

    private void luongTraVe(String... dong) {
        String than = String.join("\n", dong) + "\n";
        doAnswer(inv -> {
            RequestCallback callback = inv.getArgument(2);
            @SuppressWarnings("unchecked")
            ResponseExtractor<Object> extractor = inv.getArgument(3);
            callback.doWithRequest(new MockClientHttpRequest(HttpMethod.POST,
                    java.net.URI.create(GOC)));
            ClientHttpResponse res = mock(ClientHttpResponse.class);
            when(res.getBody()).thenReturn(
                    new ByteArrayInputStream(than.getBytes(StandardCharsets.UTF_8)));
            return extractor.extractData(res);
        }).when(streamingRestTemplate).execute(anyString(), eq(HttpMethod.POST),
                any(RequestCallback.class), any(ResponseExtractor.class));
    }

    private static String chunk(String chu) {
        return "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + chu + "\"}]}}]}";
    }

    private String urlDaGoi() {
        ArgumentCaptor<String> bat = ArgumentCaptor.forClass(String.class);
        verify(streamingRestTemplate).execute(bat.capture(), eq(HttpMethod.POST),
                any(RequestCallback.class), any(ResponseExtractor.class));
        return bat.getValue();
    }

    // =====================================================================
    // Ghép endpoint
    // =====================================================================

    @Nested
    @DisplayName("Ghép endpoint")
    class GhepEndpoint {

        @Test
        @DisplayName("Cấu hình là URL GỐC: cả hai lối gọi tự ghép đúng phương thức")
        void cauHinhUrlGoc() {
            luongTraVe("data: [DONE]");
            provider.generateStream("hỏi", s -> {}, e -> {}, c -> {});

            assertTrue(urlDaGoi().startsWith(GOC + ":streamGenerateContent?alt=sse&key="));
        }

        @Test
        @DisplayName("Cấu hình ĐÃ KÈM :generateContent thì không ghép chồng lên nhau")
        void cauHinhDaKemPhuongThuc() {
            ReflectionTestUtils.setField(provider, "apiEndpoint", GOC + ":generateContent");
            luongTraVe("data: [DONE]");

            provider.generateStream("hỏi", s -> {}, e -> {}, c -> {});

            // "...:generateContent:streamGenerateContent" là 404, và câu lỗi
            // Google trả về không nói gì về việc ghép hai phương thức.
            assertAll(
                    () -> assertTrue(urlDaGoi().contains(":streamGenerateContent")),
                    () -> assertTrue(!urlDaGoi().contains(":generateContent:")));
        }

        @Test
        @DisplayName("Cấu hình đã kèm :streamGenerateContent cũng chuẩn hoá được")
        void cauHinhKemStream() {
            ReflectionTestUtils.setField(provider, "apiEndpoint", GOC + ":streamGenerateContent");
            luongTraVe("data: [DONE]");

            provider.generateStream("hỏi", s -> {}, e -> {}, c -> {});

            assertAll(
                    () -> assertTrue(urlDaGoi().contains(":streamGenerateContent?alt=sse")),
                    () -> assertTrue(!urlDaGoi().contains("streamGenerateContent:")));
        }

        @Test
        @DisplayName("Gọi thường ghép :generateContent và mang khoá ở tham số")
        void goiThuongGhepDung() {
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]}}]}");

            provider.generate("hỏi");

            ArgumentCaptor<String> bat = ArgumentCaptor.forClass(String.class);
            verify(restTemplate).postForObject(bat.capture(), any(), eq(String.class));
            assertEquals(GOC + ":generateContent?key=khoa-gia", bat.getValue());
        }
    }

    // =====================================================================
    // Thử lại
    // =====================================================================

    @Nested
    @DisplayName("Chính sách thử lại")
    class ThuLai {

        @Test
        @DisplayName("Lỗi 401 KHÔNG thử lại — sai khoá thì thử mười lần cũng vậy")
        void loi401KhongThuLai() {
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                            "Unauthorized", null, null, null));

            var loi = assertThrows(LLMProviderException.class, () -> provider.generate("hỏi"));
            assertTrue(loi.getMessage().contains("Invalid Gemini API key"));
            // Thử lại ở đây chỉ làm người đang chờ đợi thêm ba giây cho một câu
            // trả lời chắc chắn không tới.
            verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
        }

        @Test
        @DisplayName("Lỗi 400 KHÔNG thử lại — yêu cầu sai thì gửi lại vẫn sai")
        void loi400KhongThuLai() {
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST,
                            "Bad Request", null, null, null));

            assertThrows(LLMProviderException.class, () -> provider.generate("hỏi"));
            verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
        }

        @Test
        @DisplayName("Lỗi 403 không nằm trong nhóm nào thì báo kèm mã trạng thái")
        void loiKhac() {
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN,
                            "Forbidden", null, null, null));

            var loi = assertThrows(LLMProviderException.class, () -> provider.generate("hỏi"));
            // Nêu mã trạng thái để đọc log là biết ngay phải sửa ở đâu.
            assertTrue(loi.getMessage().contains("403"));
        }

        @Test
        @DisplayName("Lỗi mạng: thử lại đủ ba lần rồi mới bỏ cuộc")
        void loiMangThuLaiBaLan() {
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenThrow(new ResourceAccessException("timeout"));

            assertThrows(LLMProviderException.class, () -> provider.generate("hỏi"));
            // Mất mạng chớp nhoáng là chuyện thường; bỏ cuộc ngay lần đầu là
            // làm hỏng một yêu cầu đáng lẽ thành công ở lần thứ hai.
            verify(restTemplate, times(3)).postForObject(anyString(), any(), eq(String.class));
        }

        @Test
        @DisplayName("Thử lại rồi THÀNH CÔNG thì trả kết quả, không ném lỗi")
        void thuLaiRoiThanhCong() {
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenThrow(new ResourceAccessException("timeout"))
                    .thenReturn("{\"candidates\":[{\"content\":{\"parts\":"
                            + "[{\"text\":\"Ba lá bài nói rằng...\"}]}}]}");

            assertEquals("Ba lá bài nói rằng...", provider.generate("hỏi").getContent());
        }

        @Test
        @DisplayName("Phản hồi sai cấu trúc thì báo lỗi rõ, không trả chuỗi rỗng")
        void phanHoiSaiCauTruc() {
            when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                    .thenReturn("{\"candidates\":[]}");

            assertThrows(LLMProviderException.class, () -> provider.generate("hỏi"));
        }
    }

    // =====================================================================
    // Luồng SSE
    // =====================================================================

    @Nested
    @DisplayName("Luồng SSE")
    class LuongSse {

        @Test
        @DisplayName("Ghép các chunk theo đúng thứ tự")
        void ghepChunk() {
            luongTraVe(chunk("Ba "), chunk("lá "), chunk("bài."));

            List<String> manh = new ArrayList<>();
            AtomicReference<StreamCompletion> xong = new AtomicReference<>();
            provider.generateStream("hỏi", manh::add, e -> {}, xong::set);

            assertAll(
                    () -> assertEquals(List.of("Ba ", "lá ", "bài."), manh),
                    () -> assertNotNull(xong.get()));
        }

        @Test
        @DisplayName("Lấy tên model thật từ chunk, không dùng mặc định")
        void layTenModelThat() {
            luongTraVe("data: {\"modelVersion\":\"gemini-2.5-flash-002\","
                    + "\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"x\"}]}}]}");

            AtomicReference<StreamCompletion> xong = new AtomicReference<>();
            provider.generateStream("hỏi", s -> {}, e -> {}, xong::set);

            // Bảng chi phí AI nhóm theo tên model. Ghi mặc định cho mọi lượt là
            // mọi lượt gộp vào một dòng, và không so sánh được model nào tốn hơn.
            assertEquals("gemini-2.5-flash-002", xong.get().getModelInfo());
        }

        @Test
        @DisplayName("Không có tên model thì rơi về mặc định, không để null")
        void khongCoTenModel() {
            luongTraVe(chunk("x"));

            AtomicReference<StreamCompletion> xong = new AtomicReference<>();
            provider.generateStream("hỏi", s -> {}, e -> {}, xong::set);

            assertEquals("gemini-2.5-flash", xong.get().getModelInfo());
        }

        @Test
        @DisplayName("Chunk cuối mang usageMetadata")
        void chunkCuoiMangToken() {
            luongTraVe(chunk("Nội dung"),
                    "data: {\"usageMetadata\":{\"promptTokenCount\":15,"
                            + "\"candidatesTokenCount\":40,\"totalTokenCount\":55}}");

            AtomicReference<StreamCompletion> xong = new AtomicReference<>();
            provider.generateStream("hỏi", s -> {}, e -> {}, xong::set);

            assertAll(
                    () -> assertEquals(15, xong.get().getTokenUsage().getPromptTokens()),
                    () -> assertEquals(40, xong.get().getTokenUsage().getCompletionTokens()),
                    () -> assertEquals(55, xong.get().getTokenUsage().getTotalTokens()));
        }

        @Test
        @DisplayName("Dòng rỗng và dòng không phải data: đều bị bỏ qua")
        void boQuaDongDieuKhien() {
            luongTraVe("", ": keep-alive", "event: message", "data: ", chunk("Thật"));

            List<String> manh = new ArrayList<>();
            provider.generateStream("hỏi", manh::add, e -> {}, c -> {});

            assertEquals(List.of("Thật"), manh);
        }

        @Test
        @DisplayName("Chunk thiếu parts thì bỏ qua, không nổ")
        void chunkThieuParts() {
            luongTraVe("data: {\"candidates\":[{\"content\":{}}]}",
                    "data: {\"candidates\":[]}",
                    chunk("Thật"));

            List<String> manh = new ArrayList<>();
            AtomicReference<Throwable> loi = new AtomicReference<>();
            provider.generateStream("hỏi", manh::add, loi::set, c -> {});

            // Gemini gửi chunk chỉ mang safetyRatings, không mang chữ nào. Nổ ở
            // đó là cắt đứt một luồng đang chạy tốt.
            assertAll(
                    () -> assertEquals(List.of("Thật"), manh),
                    () -> assertNull(loi.get()));
        }

        @Test
        @DisplayName("JSON hỏng giữa luồng thì báo qua onError")
        void jsonHong() {
            luongTraVe(chunk("Nội dung"), "data: {khong-phai-json}");

            AtomicReference<Throwable> loi = new AtomicReference<>();
            AtomicReference<StreamCompletion> xong = new AtomicReference<>();
            provider.generateStream("hỏi", s -> {}, loi::set, xong::set);

            // Im lặng thì giao diện quay vòng mãi, chờ một sự kiện hoàn tất
            // không bao giờ tới.
            assertAll(
                    () -> assertNotNull(loi.get()),
                    () -> assertTrue(loi.get() instanceof LLMProviderException),
                    () -> assertNull(xong.get()));
        }

        @Test
        @DisplayName("Không gọi được máy chủ thì báo qua onError, không ném ra ngoài")
        void khongGoiDuocMayChu() {
            doAnswer(inv -> {
                throw new ResourceAccessException("mất mạng");
            }).when(streamingRestTemplate).execute(anyString(), eq(HttpMethod.POST),
                    any(RequestCallback.class), any(ResponseExtractor.class));

            AtomicReference<Throwable> loi = new AtomicReference<>();
            // Ném ra ngoài thì nó bay lên controller giữa một SSE đã mở, và
            // trình duyệt nhận một kết nối đứt thay vì một sự kiện lỗi.
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> provider.generateStream("hỏi", s -> {}, loi::set, c -> {}));
            assertNotNull(loi.get());
        }
    }
}
