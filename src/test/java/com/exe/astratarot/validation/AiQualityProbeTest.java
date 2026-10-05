package com.exe.astratarot.validation;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.llm.LLMResponse;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.service.impl.GeminiProvider;
import com.exe.astratarot.service.impl.PromptBuilderServiceImpl;
import com.exe.astratarot.util.ZodiacCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Probe tay: gọi Gemini với prompt production + hồ sơ giả lập user 2005.
 * Chạy: GEMINI_LIVE_TEST=1 + GEMINI_API_KEY thật (không phải dummy).
 */
@EnabledIf("com.exe.astratarot.validation.LiveGeminiGate#enabled")
class AiQualityProbeTest {

    private static PromptBuilderServiceImpl promptBuilder;
    private static GeminiProvider gemini;
    private static final String API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String ENDPOINT =
            System.getenv().getOrDefault(
                    "GEMINI_API_ENDPOINT",
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash");

    @BeforeAll
    static void setUp() {
        assumeTrue(LiveGeminiGate.isUsableApiKey(API_KEY),
                "Bỏ qua probe: GEMINI_API_KEY thiếu hoặc dummy");
        promptBuilder = new PromptBuilderServiceImpl();
        RestTemplate rest = new RestTemplate(new SimpleClientHttpRequestFactory());
        gemini = new GeminiProvider(rest, rest, new ObjectMapper());
        ReflectionTestUtils.setField(gemini, "apiKey", API_KEY);
        ReflectionTestUtils.setField(gemini, "apiEndpoint", ENDPOINT);
    }

    private AstrologyContextDTO profile2005() {
        LocalDate birth = LocalDate.of(2005, 6, 25); // Cancer
        LocalTime time = LocalTime.of(9, 0);
        String sun = ZodiacCalculator.calculateSunSign(birth);
        return AstrologyContextDTO.builder()
                .birthDate(birth)
                .birthTime(time)
                .birthPlace("Hà Nội, Việt Nam")
                .sunSign(sun)
                .element(ZodiacCalculator.calculateElement(sun))
                .modality(ZodiacCalculator.calculateModality(sun))
                .moonSign(ZodiacCalculator.approximateMoonSign(birth, time))
                .build();
    }

    private String ask(String question, boolean withCards) {
        BuildPromptRequest.BuildPromptRequestBuilder b = BuildPromptRequest.builder()
                .userQuestion(question)
                .astrologyContext(profile2005())
                .spreadName(withCards ? "Past-Present-Future" : null);
        if (withCards) {
            b.drawnCardDetails(List.of(
                    DrawnCardDetailDTO.builder()
                            .cardId(UUID.randomUUID())
                            .cardName("The Fool")
                            .arcanaType("MAJOR")
                            .position((short) 0)
                            .reversed(false)
                            .build(),
                    DrawnCardDetailDTO.builder()
                            .cardId(UUID.randomUUID())
                            .cardName("The Star")
                            .arcanaType("MAJOR")
                            .position((short) 1)
                            .reversed(false)
                            .build(),
                    DrawnCardDetailDTO.builder()
                            .cardId(UUID.randomUUID())
                            .cardName("Ace of Cups")
                            .arcanaType("CUPS")
                            .position((short) 2)
                            .reversed(false)
                            .build()));
        } else {
            b.drawnCardDetails(List.of());
        }
        LLMRequest req = promptBuilder.buildLLMRequest(b.build());
        System.out.println("\n===== Q: " + question + " (cards=" + withCards + ") =====");
        System.out.println("--- sys excerpt (DATA SAFETY / Moon) ---");
        String sys = req.getSystemInstruction();
        int i = sys.indexOf("DATA SAFETY");
        if (i >= 0) {
            System.out.println(sys.substring(i, Math.min(sys.length(), i + 500)));
        }
        System.out.println("--- natal snippet ---");
        int n = sys.indexOf("Natal Chart:");
        if (n >= 0) {
            System.out.println(sys.substring(n, Math.min(sys.length(), n + 450)));
        }
        LLMResponse res = gemini.generate(req);
        String ans = res.getContent();
        System.out.println("--- ANSWER ---\n" + ans + "\n");
        return ans;
    }

    private void assertPaidQuality(String ans) {
        String lower = ans.toLowerCase(Locale.ROOT);
        assertFalse(
                lower.contains("chưa có") && (lower.contains("mặt trăng") || lower.contains("moon")),
                "Không được xin lỗi thiếu Moon:\n" + ans);
        assertFalse(
                lower.contains("khó khẳng định") && lower.contains("dữ liệu"),
                "Không được né vì thiếu dữ liệu:\n" + ans);
        assertFalse(
                lower.contains("cự giải (gemini)") || lower.contains("cancer (gemini)"),
                "Không được lẫn Cancer/Gemini:\n" + ans);
        // Phải đủ ngắn-vừa và có nội dung
        assertTrue(ans.length() > 40, "Trả lời quá ngắn");
        assertTrue(ans.length() < 2500, "Trả lời vẫn quá dài");
    }

    @Test
    @DisplayName("Probe: may mắn hôm nay (chỉ chiêm tinh)")
    void probeLuckAstroOnly() {
        String ans = ask("Hôm nay tôi có may mắn không?", false);
        assertPaidQuality(ans);
    }

    @Test
    @DisplayName("Probe: tình cảm (có lá bài)")
    void probeLoveWithCards() {
        String ans = ask("Mối quan hệ này đang đi về đâu?", true);
        assertPaidQuality(ans);
    }

    @Test
    @DisplayName("Probe: công việc tuần này")
    void probeWorkWeek() {
        String ans = ask("Tuần này công việc của mình thế nào?", false);
        assertPaidQuality(ans);
    }
}
