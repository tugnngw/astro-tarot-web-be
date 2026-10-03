package com.exe.astratarot.validation;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.llm.LLMMessage;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.service.impl.GeminiProvider;
import com.exe.astratarot.service.impl.PromptBuilderServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gọi THẬT lên Gemini để xem chất lượng lời giải, không phải test tự động.
 *
 * <p>Mặc định bị bỏ qua. Muốn chạy thì đặt hai biến môi trường:
 *
 * <pre>
 *   GEMINI_LIVE_TEST=1
 *   GEMINI_API_KEY=&lt;khoá của bạn&gt;
 * </pre>
 *
 * <p><b>Vì sao phải bật bằng tay.</b> Trước đây lớp này chạy mỗi lần đẩy code,
 * với một khoá API viết thẳng vào mã nguồn. Hai hậu quả:
 *
 * <ul>
 *   <li>Repo công khai, nên Google quét thấy khoá và vô hiệu hoá nó. Ngày
 *       03/10/2026 toàn bộ lớp này đổ với <i>"Your API key was reported as
 *       leaked"</i>, kéo CI của {@code main} đỏ — và vì CD không chờ CI nên
 *       production vẫn deploy bình thường, không ai thấy gì.
 *   <li>Mỗi lần đẩy code là một lần gọi API tính tiền, cho một phép kiểm mà
 *       kết quả phụ thuộc vào mạng và vào hạn mức.
 * </ul>
 *
 * <p>Một phép kiểm phụ thuộc dịch vụ ngoài thì không thuộc về CI: nó đỏ vì
 * những lý do chẳng liên quan gì tới đoạn mã vừa sửa, và đỏ mãi thì người ta
 * quen mắt rồi bỏ qua cả những cái đỏ thật.
 *
 * <p>Khoá giờ đọc từ môi trường. Đừng viết khoá vào đây lần nữa.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(
        named = "GEMINI_LIVE_TEST",
        matches = "1",
        disabledReason = "Gọi thật lên Gemini: đặt GEMINI_LIVE_TEST=1 và GEMINI_API_KEY để chạy")
public class Task005LiveValidationTest {

    private static PromptBuilderServiceImpl promptBuilderService;
    private static GeminiProvider geminiProvider;
    // Đọc từ môi trường. KHÔNG viết khoá vào mã nguồn — repo này công khai.
    private static final String API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash";

    // Store state across turns to simulate realistic session continuity
    private static String tarotFirstResponse = "";
    private static String tarotFollowup1Response = "";
    private static String astroFirstResponse = "";

    @BeforeAll
    static void setUp() {
        // Bật GEMINI_LIVE_TEST mà quên khoá thì dừng ngay với câu nói rõ lý do.
        // Để chạy tiếp, lỗi sẽ là 403 từ Google — đọc xong còn tưởng khoá hỏng.
        assertNotNull(API_KEY,
                "Thiếu biến môi trường GEMINI_API_KEY. Đặt khoá rồi chạy lại.");

        promptBuilderService = new PromptBuilderServiceImpl();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30000);
        requestFactory.setReadTimeout(60000);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        RestTemplate streamingRestTemplate = new RestTemplate(requestFactory);
        ObjectMapper objectMapper = new ObjectMapper();

        geminiProvider = new GeminiProvider(restTemplate, streamingRestTemplate, objectMapper);
        ReflectionTestUtils.setField(geminiProvider, "apiKey", API_KEY);
        ReflectionTestUtils.setField(geminiProvider, "apiEndpoint", ENDPOINT);
    }

    private static LLMResponse callGeminiWithPacing(LLMRequest request) {
        int maxRetries = 6;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(4000); // Respect free tier rate limits
                return geminiProvider.generate(request);
            } catch (Exception e) {
                System.out.println("Gemini call attempt " + (i + 1) + " failed: " + e.getMessage() + ". Retrying in 6s...");
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException ignored) {}
                if (i == maxRetries - 1) {
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                }
            }
        }
        throw new RuntimeException("Exhausted retries");
    }

    private List<DrawnCardDetailDTO> sampleTarotCards() {
        return List.of(
                DrawnCardDetailDTO.builder()
                        .cardId(UUID.randomUUID())
                        .cardName("The Tower")
                        .arcanaType("MAJOR")
                        .position((short) 0)
                        .reversed(false)
                        .build(),
                DrawnCardDetailDTO.builder()
                        .cardId(UUID.randomUUID())
                        .cardName("Two of Pentacles")
                        .arcanaType("MINOR")
                        .position((short) 1)
                        .reversed(true)
                        .build(),
                DrawnCardDetailDTO.builder()
                        .cardId(UUID.randomUUID())
                        .cardName("Three of Cups")
                        .arcanaType("MINOR")
                        .position((short) 2)
                        .reversed(false)
                        .build()
        );
    }

    // =========================================================================
    // PHASE 2: Verify LLMRequest Payload Structure (First Reading & Follow-up)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Phase 2.1: Verify Tarot First Reading LLMRequest Structure")
    void testTarotFirstReadingPayloadStructure() {
        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Tôi nên đổi việc không?")
                .drawnCardDetails(sampleTarotCards())
                .spreadName("Ba lá (Quá khứ - Hiện tại - Tương lai)")
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);

        assertNotNull(llmRequest.getSystemInstruction(), "systemInstruction must not be null");
        assertTrue(llmRequest.getSystemInstruction().contains("người đọc tarot"), "Must have tarot reader persona");
        assertTrue(llmRequest.getSystemInstruction().contains("The Tower"), "Must contain drawn cards");
        assertTrue(llmRequest.getSystemInstruction().contains("Two of Pentacles — Reversed"), "Must contain reversed card");
        assertTrue(llmRequest.getSystemInstruction().contains("Three of Cups"), "Must contain 3rd card");
        assertTrue(llmRequest.getSystemInstruction().contains("CÁCH TRẢ LỜI"), "Must contain unified response rules");
        assertTrue(llmRequest.getSystemInstruction().contains("TÍNH CHÍNH XÁC"), "Must contain factuality boundaries");

        // Verify messages
        assertEquals(1, llmRequest.getMessages().size(), "First reading must have exactly 1 message");
        assertEquals(LLMMessage.Role.USER, llmRequest.getMessages().get(0).getRole());
        assertEquals("Tôi nên đổi việc không?", llmRequest.getMessages().get(0).getContent());

        System.out.println("=== PHASE 2.1: Tarot First Reading LLMRequest Payload Verified ===");
        System.out.println("System Instruction Length: " + llmRequest.getSystemInstruction().length() + " chars");
        System.out.println("Messages Count: " + llmRequest.getMessages().size());
        System.out.println("Role[0]: " + llmRequest.getMessages().get(0).getRole() + " -> " + llmRequest.getMessages().get(0).getContent());
    }

    @Test
    @Order(2)
    @DisplayName("Phase 2.2: Verify Tarot Follow-up LLMRequest Structure (Role Mapping & No Duplicate)")
    void testTarotFollowupPayloadStructure() {
        String history = "USER: Tôi nên đổi việc không?\n\nAI: Mình thấy The Tower phản ánh một sự xáo trộn lớn gần đây, kết hợp Two of Pentacles ngược cho thấy bạn đang mất cân bằng...";

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Còn về tiền bạc thì sao?")
                .originalQuestion("Tôi nên đổi việc không?")
                .conversationHistory(history)
                .drawnCardDetails(sampleTarotCards())
                .spreadName("Ba lá")
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);

        // Verify systemInstruction contains original question as anchor
        assertTrue(llmRequest.getSystemInstruction().contains("ORIGINAL QUESTION"), "Must contain original question anchor");
        assertTrue(llmRequest.getSystemInstruction().contains("Tôi nên đổi việc không?"), "Must contain original question text");

        // Verify messages
        assertEquals(3, llmRequest.getMessages().size(), "History(2) + Current(1) = 3 messages");
        assertEquals(LLMMessage.Role.USER, llmRequest.getMessages().get(0).getRole());
        assertEquals("Tôi nên đổi việc không?", llmRequest.getMessages().get(0).getContent());
        assertEquals(LLMMessage.Role.ASSISTANT, llmRequest.getMessages().get(1).getRole());
        assertTrue(llmRequest.getMessages().get(1).getContent().contains("The Tower"));
        assertEquals(LLMMessage.Role.USER, llmRequest.getMessages().get(2).getRole());
        assertEquals("Còn về tiền bạc thì sao?", llmRequest.getMessages().get(2).getContent());

        // Verify current question is not duplicated
        long matchCount = llmRequest.getMessages().stream()
                .filter(m -> m.getContent() != null && m.getContent().contains("Còn về tiền bạc thì sao?"))
                .count();
        assertEquals(1, matchCount, "Current question must appear exactly once in messages");

        System.out.println("=== PHASE 2.2: Tarot Follow-up LLMRequest Payload Verified ===");
        System.out.println("Messages Count: " + llmRequest.getMessages().size());
        for (int i = 0; i < llmRequest.getMessages().size(); i++) {
            LLMMessage msg = llmRequest.getMessages().get(i);
            System.out.println("Message[" + i + "] Role: " + msg.getRole() + " Content Snippet: " + (msg.getContent().length() > 60 ? msg.getContent().substring(0, 60) + "..." : msg.getContent()));
        }
    }

    // =========================================================================
    // PHASE 3: Live Tarot Reading (First Turn)
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Phase 3: Live Tarot E2E - First Reading with Gemini 2.5 Flash")
    void testLiveTarotFirstReading() {
        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Tôi nên đổi việc không?")
                .drawnCardDetails(sampleTarotCards())
                .spreadName("Ba lá (Quá khứ - Hiện tại - Tương lai)")
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);
        LLMResponse response = callGeminiWithPacing(llmRequest);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isBlank());

        tarotFirstResponse = response.getContent();

        System.out.println("\n=======================================================");
        System.out.println("=== PHASE 3: LIVE TAROT FIRST READING RESPONSE ===");
        System.out.println("Model: " + response.getModelInfo());
        if (response.getTokenUsage() != null) {
            System.out.println("Tokens: Prompt=" + response.getTokenUsage().getPromptTokens()
                    + ", Completion=" + response.getTokenUsage().getCompletionTokens()
                    + ", Total=" + response.getTokenUsage().getTotalTokens());
        }
        System.out.println("Response Length (chars): " + tarotFirstResponse.length());
        System.out.println("Response Content:\n" + tarotFirstResponse);
        System.out.println("=======================================================\n");

        // Quality verifications
        assertTrue(tarotFirstResponse.length() > 200, "Response should have sufficient depth (>200 chars)");
        // Must reference the cards or situations
        boolean mentionsCards = tarotFirstResponse.toLowerCase().contains("tower")
                || tarotFirstResponse.toLowerCase().contains("pentacles")
                || tarotFirstResponse.toLowerCase().contains("cups")
                || tarotFirstResponse.toLowerCase().contains("đổi việc");
        assertTrue(mentionsCards, "Response must connect cards to the question");
    }

    // =========================================================================
    // PHASE 4: Live Tarot Follow-ups (Continuity & Scope Handling)
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Phase 4.1: Live Tarot Follow-up 1 - 'Còn về tiền bạc thì sao?'")
    void testLiveTarotFollowup1() {
        assertFalse(tarotFirstResponse.isBlank(), "First turn response must exist");

        String history = "USER: Tôi nên đổi việc không?\n\nAI: " + tarotFirstResponse;

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Còn về tiền bạc thì sao?")
                .originalQuestion("Tôi nên đổi việc không?")
                .conversationHistory(history)
                .drawnCardDetails(sampleTarotCards())
                .spreadName("Ba lá (Quá khứ - Hiện tại - Tương lai)")
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);
        LLMResponse response = callGeminiWithPacing(llmRequest);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isBlank());

        tarotFollowup1Response = response.getContent();

        System.out.println("\n=======================================================");
        System.out.println("=== PHASE 4.1: LIVE TAROT FOLLOW-UP 1 (TIỀN BẠC) ===");
        System.out.println("Response Content:\n" + tarotFollowup1Response);
        System.out.println("=======================================================\n");

        // Verify continuity & focus
        boolean mentionsMoney = tarotFollowup1Response.toLowerCase().contains("tiền")
                || tarotFollowup1Response.toLowerCase().contains("tài chính")
                || tarotFollowup1Response.toLowerCase().contains("thu nhập")
                || tarotFollowup1Response.toLowerCase().contains("pentacles");
        assertTrue(mentionsMoney, "Follow-up must focus specifically on money/finances");
    }

    @Test
    @Order(5)
    @DisplayName("Phase 4.2: Live Tarot Follow-up 2 - 'Tôi nên chú ý điều gì nhất?'")
    void testLiveTarotFollowup2() {
        assertFalse(tarotFollowup1Response.isBlank(), "Followup 1 response must exist");

        String history = "USER: Tôi nên đổi việc không?\n\nAI: " + tarotFirstResponse + "\n\nUSER: Còn về tiền bạc thì sao?\n\nAI: " + tarotFollowup1Response;

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Tôi nên chú ý điều gì nhất?")
                .originalQuestion("Tôi nên đổi việc không?")
                .conversationHistory(history)
                .drawnCardDetails(sampleTarotCards())
                .spreadName("Ba lá (Quá khứ - Hiện tại - Tương lai)")
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);
        LLMResponse response = callGeminiWithPacing(llmRequest);

        assertNotNull(response);
        assertNotNull(response.getContent());

        System.out.println("\n=======================================================");
        System.out.println("=== PHASE 4.2: LIVE TAROT FOLLOW-UP 2 (CHÚ Ý ĐIỀU GÌ NHẤT) ===");
        System.out.println("Response Content:\n" + response.getContent());
        System.out.println("=======================================================\n");

        assertTrue(response.getContent().length() > 100, "Response should provide concise synthesized advice");
    }

    // =========================================================================
    // PHASE 5 & 6: Live Astrology Reading & Data Audit (Missing Moon/Rising)
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Phase 5 & 6: Live Astrology Reading with Missing Moon/Rising & Data Audit")
    void testLiveAstrologyFirstReadingAndDataAudit() {
        // Context: Sun in Taurus, birth date & place known, Moon & Rising null, natal planets null
        AstrologyContextDTO astrologyContext = AstrologyContextDTO.builder()
                .birthDate(LocalDate.of(2000, 5, 17))
                .birthPlace("Hà Nội")
                .sunSign("Taurus")
                .element("Earth")
                .modality("Fixed")
                .moonSign(null)
                .risingSign(null)
                .natalPlanetPositions(null)
                .natalAspects(null)
                .transitAspects(null)
                .build();

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Tình duyên của tôi thế nào?")
                .astrologyContext(astrologyContext)
                .drawnCardDetails(List.of()) // Astrology only
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);

        // Data safety assertions in payload
        assertFalse(llmRequest.getSystemInstruction().contains("Moon: null"));
        assertFalse(llmRequest.getSystemInstruction().contains("Rising: null"));
        assertTrue(llmRequest.getSystemInstruction().contains("Taurus"));
        assertTrue(llmRequest.getSystemInstruction().contains("DATA SAFETY"));
        assertTrue(llmRequest.getSystemInstruction().contains("KHÔNG được tự đoán"));

        LLMResponse response = callGeminiWithPacing(llmRequest);

        assertNotNull(response);
        astroFirstResponse = response.getContent();

        System.out.println("\n=======================================================");
        System.out.println("=== PHASE 5 & 6: LIVE ASTROLOGY READING (MISSING MOON/RISING) ===");
        System.out.println("Response Content:\n" + astroFirstResponse);
        System.out.println("=======================================================\n");

        // Audit AI output: Ensure no hallucinated Moon / Rising sign
        String lowerResponse = astroFirstResponse.toLowerCase();
        boolean mentionsTaurusOrEarth = lowerResponse.contains("kim ngưu") || lowerResponse.contains("taurus") || lowerResponse.contains("đất");
        assertTrue(mentionsTaurusOrEarth, "Must reference user's actual Sun sign (Kim Ngưu / Taurus / Đất)");

        // Check for hallucinated claims like "Mặt trăng Bọ Cạp" or "Cung mọc Sư Tử"
        assertFalse(lowerResponse.contains("mặt trăng bọ cạp") || lowerResponse.contains("mặt trăng song ngư") || lowerResponse.contains("mặt trăng cự giải"),
                "Must NOT hallucinate unprovided Moon sign");
        assertFalse(lowerResponse.contains("cung mọc sư tử") || lowerResponse.contains("cung mọc xử nữ") || lowerResponse.contains("cung mọc thiên bình"),
                "Must NOT hallucinate unprovided Rising sign");
    }

    @Test
    @Order(7)
    @DisplayName("Phase 5.2: Live Astrology Follow-up - 'Công việc có gì nổi bật?'")
    void testLiveAstrologyFollowup() {
        assertFalse(astroFirstResponse.isBlank());

        AstrologyContextDTO astrologyContext = AstrologyContextDTO.builder()
                .birthDate(LocalDate.of(2000, 5, 17))
                .birthPlace("Hà Nội")
                .sunSign("Taurus")
                .element("Earth")
                .modality("Fixed")
                .build();

        String history = "USER: Tình duyên của tôi thế nào?\n\nAI: " + astroFirstResponse;

        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Công việc có gì nổi bật?")
                .originalQuestion("Tình duyên của tôi thế nào?")
                .conversationHistory(history)
                .astrologyContext(astrologyContext)
                .drawnCardDetails(List.of())
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);
        LLMResponse response = callGeminiWithPacing(llmRequest);

        assertNotNull(response);
        System.out.println("\n=======================================================");
        System.out.println("=== PHASE 5.2: LIVE ASTROLOGY FOLLOW-UP (CÔNG VIỆC) ===");
        System.out.println("Response Content:\n" + response.getContent());
        System.out.println("=======================================================\n");

        assertTrue(response.getContent().toLowerCase().contains("công việc")
                || response.getContent().toLowerCase().contains("sự nghiệp")
                || response.getContent().toLowerCase().contains("tài chính")
                || response.getContent().toLowerCase().contains("vững chắc"));
    }

    // =========================================================================
    // PHASE 8: Test Multiple Question Types (Open-ended, Concrete, Metaphorical)
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("Phase 8.1: Question Type A - General / Open-ended")
    void testQuestionTypeOpenEnded() {
        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Tổng quan năng lượng hiện tại của tôi thế nào?")
                .drawnCardDetails(sampleTarotCards())
                .spreadName("Tổng quan")
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);
        LLMResponse response = callGeminiWithPacing(llmRequest);

        assertNotNull(response);
        System.out.println("=== Question Type A (Open-ended) Response Sample ===");
        System.out.println(response.getContent().substring(0, Math.min(250, response.getContent().length())) + "...");
    }

    @Test
    @Order(9)
    @DisplayName("Phase 8.2: Question Type B - Concrete / Specific")
    void testQuestionTypeConcrete() {
        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Tôi có nên ký hợp đồng dự án mới trong tuần này không?")
                .drawnCardDetails(sampleTarotCards())
                .spreadName("Quyết định")
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);
        LLMResponse response = callGeminiWithPacing(llmRequest);

        assertNotNull(response);
        System.out.println("=== Question Type B (Concrete/Specific) Response Sample ===");
        System.out.println(response.getContent().substring(0, Math.min(250, response.getContent().length())) + "...");
        // Verify it doesn't give a dogmatic absolute certainty
        assertFalse(response.getContent().contains("Chắc chắn 100%"));
    }

    @Test
    @Order(10)
    @DisplayName("Phase 8.3: Question Type C - Abstract / Metaphorical")
    void testQuestionTypeAbstract() {
        BuildPromptRequest request = BuildPromptRequest.builder()
                .userQuestion("Làm sao để tôi tìm lại sự bình yên bên trong tâm hồn?")
                .drawnCardDetails(sampleTarotCards())
                .spreadName("Chữa lành")
                .build();

        LLMRequest llmRequest = promptBuilderService.buildLLMRequest(request);
        LLMResponse response = callGeminiWithPacing(llmRequest);

        assertNotNull(response);
        System.out.println("=== Question Type C (Abstract/Metaphorical) Response Sample ===");
        System.out.println(response.getContent().substring(0, Math.min(250, response.getContent().length())) + "...");
    }
}
