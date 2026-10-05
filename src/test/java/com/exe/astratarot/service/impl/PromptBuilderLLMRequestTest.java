package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.llm.LLMMessage;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for structured LLMRequest generation from PromptBuilderServiceImpl.
 *
 * <p>These tests verify that the runtime actually uses role separation:
 * systemInstruction ≠ conversation history ≠ current user question.
 * Current user question must appear exactly once in messages.
 */
class PromptBuilderLLMRequestTest {

    private PromptBuilderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromptBuilderServiceImpl();
    }

    private List<DrawnCardDetailDTO> cards() {
        return List.of(
                DrawnCardDetailDTO.builder()
                        .cardId(UUID.randomUUID())
                        .cardName("The Tower")
                        .arcanaType("MAJOR")
                        .position((short) 0)
                        .reversed(false)
                        .build()
        );
    }

    private BuildPromptRequest yeuCau(AstrologyContextDTO chiemTinh,
                                      List<DrawnCardDetailDTO> baiRut,
                                      String lichSu, String cauHoi) {
        return BuildPromptRequest.builder()
                .userQuestion(cauHoi)
                .astrologyContext(chiemTinh)
                .drawnCardDetails(baiRut != null ? baiRut : List.of())
                .spreadName("Ba lá")
                .conversationHistory(lichSu)
                .build();
    }

    // =====================================================================

    @Test
    @DisplayName("First reading: systemInstruction non-null, messages has 1 USER message")
    void firstReading() {
        LLMRequest req = service.buildLLMRequest(
                yeuCau(null, cards(), null, "Tôi nên đổi việc không?"));

        assertNotNull(req.getSystemInstruction(), "systemInstruction should not be null");
        assertFalse(req.getSystemInstruction().isBlank());
        assertEquals(1, req.getMessages().size(), "First reading should have 1 message");
        assertEquals(LLMMessage.Role.USER, req.getMessages().get(0).getRole());
        assertEquals("Tôi nên đổi việc không?", req.getMessages().get(0).getContent());
    }

    @Test
    @DisplayName("Continuation: history USER/ASSISTANT + current USER")
    void continuation() {
        String lichSu = "USER: Tôi nên đổi việc không?\nAI: Mình thấy The Tower đang phản ánh...";
        BuildPromptRequest r = BuildPromptRequest.builder()
                .userQuestion("Còn về tiền bạc?")
                .originalQuestion("Tôi nên đổi việc không?")
                .conversationHistory(lichSu)
                .drawnCardDetails(cards())
                .spreadName("Continuation")
                .build();

        LLMRequest req = service.buildLLMRequest(r);

        assertEquals(3, req.getMessages().size(), "History(2) + current(1) = 3");
        assertEquals(LLMMessage.Role.USER, req.getMessages().get(0).getRole());
        assertEquals("Tôi nên đổi việc không?", req.getMessages().get(0).getContent());
        assertEquals(LLMMessage.Role.ASSISTANT, req.getMessages().get(1).getRole());
        assertTrue(req.getMessages().get(1).getContent().contains("The Tower"));
        assertEquals(LLMMessage.Role.USER, req.getMessages().get(2).getRole());
        assertEquals("Còn về tiền bạc?", req.getMessages().get(2).getContent());
    }

    @Test
    @DisplayName("Current question appears exactly once in messages")
    void noDuplicateCurrentMessage() {
        String lichSu = "USER: Tôi nên đổi việc không?\nAI: Mình thấy The Tower...";
        BuildPromptRequest r = BuildPromptRequest.builder()
                .userQuestion("Còn về tiền bạc?")
                .conversationHistory(lichSu)
                .drawnCardDetails(cards())
                .build();

        LLMRequest req = service.buildLLMRequest(r);

        long count = req.getMessages().stream()
                .filter(m -> m.getContent() != null && m.getContent().contains("Còn về tiền bạc?"))
                .count();
        assertEquals(1, count, "Current question should appear exactly once");
    }

    @Test
    @DisplayName("Role mapping: USER→USER, AI→ASSISTANT")
    void roleMapping() {
        String lichSu = "USER: Câu 1\nAI: Trả lời 1\nUSER: Câu 2\nAI: Trả lời 2";
        BuildPromptRequest r = BuildPromptRequest.builder()
                .userQuestion("Câu hiện tại")
                .conversationHistory(lichSu)
                .drawnCardDetails(cards())
                .build();

        LLMRequest req = service.buildLLMRequest(r);

        assertEquals(5, req.getMessages().size());
        assertEquals(LLMMessage.Role.USER, req.getMessages().get(0).getRole());
        assertEquals(LLMMessage.Role.ASSISTANT, req.getMessages().get(1).getRole());
        assertEquals(LLMMessage.Role.USER, req.getMessages().get(2).getRole());
        assertEquals(LLMMessage.Role.ASSISTANT, req.getMessages().get(3).getRole());
        assertEquals(LLMMessage.Role.USER, req.getMessages().get(4).getRole());
        assertEquals("Câu hiện tại", req.getMessages().get(4).getContent());
    }

    @Test
    @DisplayName("Astrology missing data: no null, no instruction to assume missing values")
    void astrologyMissingData() {
        AstrologyContextDTO thieu = AstrologyContextDTO.builder()
                .birthDate(LocalDate.of(2000, 5, 17))
                .sunSign("Taurus")
                .moonSign(null)
                .risingSign(null)
                .natalPlanetPositions(null)
                .natalAspects(null)
                .build();

        LLMRequest req = service.buildLLMRequest(
                yeuCau(thieu, List.of(), null, "Hôm nay thế nào?"));

        String sys = req.getSystemInstruction();
        assertFalse(sys.contains("Moon: null"), "Should not contain Moon: null");
        assertFalse(sys.contains("Rising: null"), "Should not contain Rising: null");
        assertFalse(sys.contains("Planetary Positions:"), "Should not contain empty Planetary Positions header");
        assertFalse(sys.contains("Natal Aspects:"), "Should not contain empty Natal Aspects header");
        assertTrue(sys.contains("Taurus"), "Should contain sun sign");
        // Should not instruct model to mention Moon when absent
        assertFalse(sys.toLowerCase().contains("mention their moon"),
                "Should not instruct to mention Moon when null");
        // Should have data safety rule — trả lời bằng data có, không né thiếu Moon
        assertTrue(sys.contains("DATA SAFETY"), "Should have DATA SAFETY section");
        assertTrue(sys.contains("CẤM mở bài") || sys.contains("thiếu dữ liệu"),
                "Should forbid leading with missing-data apology");
        assertTrue(sys.contains("KHÔNG được tự bịa") || sys.contains("KHÔNG được tự"),
                "Should still forbid inventing Rising/houses");
        assertTrue(sys.contains("FEW-SHOT"), "Should include few-shot examples");
        assertTrue(sys.contains("Session date"), "Should include session date for 'hôm nay'");
    }

    @Test
    @DisplayName("Astrology available data: values passed correctly")
    void astrologyAvailableData() {
        AstrologyContextDTO dayDu = AstrologyContextDTO.builder()
                .birthDate(LocalDate.of(2000, 5, 17))
                .birthPlace("Hà Nội")
                .sunSign("Taurus")
                .element("Earth")
                .modality("Fixed")
                .moonSign("Pisces")
                .risingSign("Leo")
                .natalPlanetPositions(null)
                .natalAspects(null)
                .build();

        LLMRequest req = service.buildLLMRequest(
                yeuCau(dayDu, List.of(), null, "Hôm nay thế nào?"));

        String sys = req.getSystemInstruction();
        assertTrue(sys.contains("Moon: Pisces"), "Should contain Moon sign");
        assertTrue(sys.contains("Rising: Leo"), "Should contain Rising sign");
        assertTrue(sys.contains("Taurus"), "Should contain Sun sign");
        assertTrue(sys.contains("Earth"), "Should contain element");
    }

    @Test
    @DisplayName("Continuation system instruction has persona + factuality rules")
    void firstVsContinuation() {
        String lichSu = "USER: Tôi nên đổi việc không?\nAI: Mình thấy The Tower...";
        BuildPromptRequest r = BuildPromptRequest.builder()
                .userQuestion("Còn về tiền bạc?")
                .originalQuestion("Tôi nên đổi việc không?")
                .conversationHistory(lichSu)
                .drawnCardDetails(cards())
                .spreadName("Continuation")
                .build();

        LLMRequest req = service.buildLLMRequest(r);

        String sys = req.getSystemInstruction();
        // Should have persona
        assertTrue(sys.contains("người đọc tarot"), "Should have tarot persona");
        // Should have factuality rules
        assertTrue(sys.contains("Dùng lá bài") || sys.contains("dữ liệu"), "Should have factuality rule");
        // Should have original question as context
        assertTrue(sys.contains("ORIGINAL QUESTION"), "Should have original question context");
        assertTrue(sys.contains("Tôi nên đổi việc không?"), "Should contain original question");
    }

    @Test
    @DisplayName("Hard length constraints + maxTokens trần cứng")
    void hardLengthConstraintsAndMaxTokens() {
        BuildPromptRequest r = yeuCau(null, cards(), null, "hỏi");
        LLMRequest req = service.buildLLMRequest(r);

        String sys = req.getSystemInstruction();
        assertFalse(sys.contains("ĐỦ Ý thì DỪNG"), "Should not contain ĐỦ Ý thì DỪNG");
        assertFalse(sys.contains("CẤU TRÚC BÀI ĐỌC LẦN ĐẦU"), "Should not contain first-reading-only structure");
        assertFalse(sys.contains("CẤU TRÚC CHAT TIẾP THEO"), "Should not contain continuation-only structure");
        assertTrue(sys.contains("CÁCH TRẢ LỜI"), "Should have response approach section");
        assertTrue(sys.contains("ĐỘ DÀI"), "Should have hard length section");
        assertTrue(sys.contains("60-100 từ") || sys.contains("60–100 từ"),
                "Should cap short yes/no answers");
        assertNotNull(req.getMaxTokens(), "maxTokens must be set so Gemini gets maxOutputTokens");
        assertTrue(req.getMaxTokens() > 0 && req.getMaxTokens() <= 600,
                "maxTokens should be a tight ceiling, not unlimited");
    }

    @Test
    @DisplayName("Human-like behavior guidance exists")
    void humanLikeBehavior() {
        BuildPromptRequest r = yeuCau(null, cards(), null, "hỏi");
        LLMRequest req = service.buildLLMRequest(r);

        String sys = req.getSystemInstruction();
        assertTrue(sys.contains("nói chuyện"), "Should mention talking/conversation");
        assertTrue(sys.contains("Bạn là"), "Should have persona definition");
        assertTrue(sys.contains("GIỌNG NÓI"), "Should have voice/tone section");
        assertTrue(sys.contains("Không dùng câu mở đầu khuôn mẫu")
                        || sys.contains("CẤM TUYỆT ĐỐI"),
                "Should ban canned openings");
    }

    @Test
    @DisplayName("Astrology-only mode: no cards, astrology is primary")
    void astrologyOnlyMode() {
        AstrologyContextDTO chiemTinh = AstrologyContextDTO.builder()
                .birthDate(LocalDate.of(2000, 5, 17))
                .sunSign("Taurus")
                .build();

        LLMRequest req = service.buildLLMRequest(
                yeuCau(chiemTinh, List.of(), null, "Về tình yêu của tôi?"));

        String sys = req.getSystemInstruction();
        assertTrue(sys.contains("nhà chiêm tinh"), "Should have astrologer persona");
        assertTrue(sys.contains("Primary Source of Insight"), "Astrology should be primary");
        assertFalse(sys.contains("TAROT CARDS"), "Should not have tarot cards section");
        assertFalse(sys.contains("người đọc tarot"), "Should not have tarot reader persona");
        // Should have DATA SAFETY
        assertTrue(sys.contains("DATA SAFETY"), "Should have data safety rules");
    }
}
