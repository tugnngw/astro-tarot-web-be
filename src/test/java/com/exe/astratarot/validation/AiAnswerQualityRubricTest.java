package com.exe.astratarot.validation;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.service.impl.PromptBuilderServiceImpl;
import com.exe.astratarot.util.ZodiacCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline quality gate: đánh giá câu trả lời AI theo rubric sản phẩm trả phí,
 * và khóa contract của system prompt (không cần gọi Gemini).
 *
 * <p>Rubric dựa trên lỗi thực tế đã quan sát + best practice:
 * Gemini few-shot, Esotier paid-client (no disclaimer), answer-first.
 */
class AiAnswerQualityRubricTest {

    private PromptBuilderServiceImpl promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilderServiceImpl();
    }

    private AstrologyContextDTO profileCancer2005() {
        LocalDate birth = LocalDate.of(2005, 6, 25);
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

    /** Mẫu lỗi thực tế: xin lỗi thiếu Moon + lẫn Cancer/Gemini + không trả lời thẳng. */
    private static final String BAD_LEGACY_ANSWER = """
            Mình chưa có Moon Sign và Rising nên khó khẳng định chính xác mức độ may mắn hôm nay.
            Với Sun ở Cancer (Gemini), bạn thiên về giao tiếp, linh hoạt — nên hôm nay hãy nói chuyện
            nhiều hơn. Có thể mọi việc sẽ ổn nếu bạn mở lòng. Năng lượng vũ trụ đang nói với bạn
            rằng hãy tin vào trực giác. Ultimately, may mắn đến từ chính bạn.
            """;

    private static final String GOOD_TARGET_ANSWER = """
            Hôm nay nghiêng may mắn vừa phải — không phải ngày bùng nổ.
            Sun Cự Giải + Moon ước lượng trong hồ sơ: hợp làm việc nhỏ, kỹ, gần người quen hơn mạo hiểm lớn.
            Một việc: chọn một việc dang dở và chốt trước 18h.
            """;

    private List<String> scoreFailures(String answer) {
        String lower = answer.toLowerCase(Locale.ROOT);
        List<String> fails = new ArrayList<>();

        if ((lower.contains("chưa có") || lower.contains("thiếu"))
                && (lower.contains("moon") || lower.contains("mặt trăng") || lower.contains("rising"))) {
            fails.add("apologizes_missing_moon_or_rising");
        }
        if (lower.contains("khó khẳng định") || lower.contains("khó nói chính xác")) {
            fails.add("hedges_on_missing_data");
        }
        if (lower.contains("cự giải (gemini)") || lower.contains("cancer (gemini)")
                || lower.contains("cancer(gemini)")) {
            fails.add("confuses_cancer_with_gemini");
        }
        if (lower.contains("năng lượng vũ trụ") || lower.contains("mình nhìn thấy")
                || lower.contains("cảm nhận năng lượng")) {
            fails.add("mystical_filler");
        }
        if (lower.contains("ultimately") || lower.contains("in conclusion")
                || lower.contains("bringing it all together")) {
            fails.add("english_essay_closer");
        }
        // Có/không / vừa phải nên xuất hiện sớm với câu hỏi may mắn
        String first120 = lower.length() > 120 ? lower.substring(0, 120) : lower;
        boolean hasVerdict = first120.contains("may mắn") || first120.contains("không")
                || first120.contains("vừa phải") || first120.contains("nghiêng");
        if (!hasVerdict) {
            fails.add("no_early_verdict");
        }
        if (answer.trim().length() > 900) {
            fails.add("too_long_for_yes_no");
        }
        return fails;
    }

    @Test
    @DisplayName("Rubric: câu trả lời legacy phải FAIL (đúng là kém)")
    void legacyBadAnswerFailsRubric() {
        List<String> fails = scoreFailures(BAD_LEGACY_ANSWER);
        assertTrue(fails.contains("apologizes_missing_moon_or_rising"), fails.toString());
        assertTrue(fails.contains("confuses_cancer_with_gemini"), fails.toString());
        assertTrue(fails.size() >= 3, "Legacy answer phải trượt nhiều tiêu chí: " + fails);
        System.out.println("[RUBRIC] BAD legacy failures=" + fails);
    }

    @Test
    @DisplayName("Rubric: câu trả lời mục tiêu phải PASS")
    void goodTargetAnswerPassesRubric() {
        List<String> fails = scoreFailures(GOOD_TARGET_ANSWER);
        assertTrue(fails.isEmpty(), "Target answer vẫn fail: " + fails);
        System.out.println("[RUBRIC] GOOD target OK");
    }

    @Test
    @DisplayName("Prompt contract: Session date + FEW-SHOT + DATA SAFETY + VI labels")
    void promptContainsPaidQualityContract() {
        AstrologyContextDTO ctx = profileCancer2005();
        LLMRequest req = promptBuilder.buildLLMRequest(BuildPromptRequest.builder()
                .userQuestion("Hôm nay tôi có may mắn không?")
                .astrologyContext(ctx)
                .drawnCardDetails(List.of())
                .build());

        String sys = req.getSystemInstruction();
        String today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString();

        assertTrue(sys.contains("Session date"), "Thiếu Session date");
        assertTrue(sys.contains(today), "Session date phải là hôm nay VN: " + today);
        assertTrue(sys.contains("FEW-SHOT"), "Thiếu few-shot (Gemini best practice)");
        assertTrue(sys.contains("DATA SAFETY"), "Thiếu DATA SAFETY");
        assertTrue(sys.contains("PAID CLIENT") || sys.contains("trả phí"), "Thiếu paid-client framing");
        assertTrue(sys.contains("Cự Giải") || sys.contains("Cancer /"), "Thiếu nhãn VI cho Sun");
        assertTrue(sys.contains("Moon:"), "Phải có Moon approx trong natal");
        assertTrue(sys.contains("CẤM format \"Cancer (Gemini)\"")
                        || sys.contains("CẤM viết \"Cancer (Gemini)\""),
                "Phải cấm pattern lẫn Cancer/Gemini");
        assertTrue(sys.contains("KHUÔN TRẢ LỜI") || sys.contains("CÂU ĐẦU"),
                "Thiếu khuôn trả lời có/không");

        System.out.println("[PROMPT] moon=" + ctx.getMoonSign()
                + " sun=" + ctx.getSunSign()
                + " sysLen=" + sys.length());
        int i = sys.indexOf("FEW-SHOT");
        if (i >= 0) {
            System.out.println(sys.substring(i, Math.min(sys.length(), i + 420)));
        }
    }
}
