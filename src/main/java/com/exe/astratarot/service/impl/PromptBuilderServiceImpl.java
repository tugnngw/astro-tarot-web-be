package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.astrology.AspectDTO;
import com.exe.astratarot.domain.dto.astrology.PlanetPositionDTO;
import com.exe.astratarot.domain.dto.llm.LLMMessage;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.service.PromptBuilderService;
import com.exe.astratarot.util.ZodiacCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Implementation of PromptBuilderService.
 *
 * <p>Builds structured {@link LLMRequest} with proper role separation:
 * <ul>
 *   <li><b>systemInstruction</b>: persona, tone, reading context, astrology data,
 *       tarot cards, response quality rules</li>
 *   <li><b>messages</b>: conversation history (USER/ASSISTANT pairs) + current
 *       user question as final USER message</li>
 * </ul>
 *
 * <p>No external dependencies. Pure composition.
 */
@Service
@RequiredArgsConstructor
public class PromptBuilderServiceImpl implements PromptBuilderService {

    @Override
    public LLMRequest buildLLMRequest(BuildPromptRequest request) {
        boolean hasCards = request.getDrawnCardDetails() != null && !request.getDrawnCardDetails().isEmpty();
        boolean isContinuation = request.getConversationHistory() != null && !request.getConversationHistory().isBlank();

        // Build system instruction
        StringBuilder sys = new StringBuilder();
        sys.append(hasCards ? buildTarotSystemInstructions() : buildAstrologySystemInstructions());
        sys.append(buildReadingContext(request.getSpreadName()));

        if (request.getAstrologyContext() != null) {
            sys.append(buildAstrologyContext(request.getAstrologyContext(), hasCards));
        }

        if (hasCards) {
            sys.append(buildTarotCardsSection(request.getDrawnCardDetails()));
        }

        // Original question as context anchor for continuation
        if (request.getOriginalQuestion() != null && !request.getOriginalQuestion().isBlank()) {
            sys.append("ORIGINAL QUESTION (context):\n")
                    .append(request.getOriginalQuestion())
                    .append("\n\n---\n\n");
        }

        // Few-shot: Gemini/docs khuyến nghị ví dụ cụ thể hơn mô tả dài
        sys.append(buildFewShotExamples(hasCards));
        sys.append(buildResponseInstructions(hasCards));

        // Build messages: history + current
        List<LLMMessage> messages = new ArrayList<>();
        if (isContinuation) {
            messages.addAll(parseConversationHistory(request.getConversationHistory()));
        }
        messages.add(LLMMessage.user(request.getUserQuestion()));

        return LLMRequest.builder()
                .systemInstruction(sys.toString())
                .messages(messages)
                .maxTokens(GIOI_HAN_TOKEN_TRA_LOI)
                .build();
    }

    /**
     * Trần token cho một lời giải.
     *
     * <p>Trước đây KHÔNG đặt trần nào: {@code LLMRequest.maxTokens} để null ở
     * mọi nơi, nên {@code GeminiProvider} bỏ hẳn {@code maxOutputTokens} khỏi
     * generationConfig và mô hình viết tới khi nào nó muốn dừng. Hỏi "hôm nay
     * tôi có may mắn không" mà nhận về năm đoạn văn.
     *
     * <p>450 token xấp xỉ 250–300 từ tiếng Việt — đủ cho câu trả lời thẳng
     * + tối đa hai ý. Trước đây 1200 vẫn để mô hình viết ba đoạn dài cho câu
     * hỏi có/không. Đây là trần cứng; hướng dẫn độ dài trong lời nhắc định
     * mức thường ngày.
     */
    private static final int GIOI_HAN_TOKEN_TRA_LOI = 450;

    @Override
    @Deprecated
    public String buildPrompt(BuildPromptRequest request) {
        LLMRequest req = buildLLMRequest(request);
        StringBuilder sb = new StringBuilder();
        sb.append(req.getSystemInstruction());
        if (req.getMessages() != null) {
            for (LLMMessage msg : req.getMessages()) {
                if (msg.getRole() == LLMMessage.Role.USER) {
                    sb.append("USER: ").append(msg.getContent()).append("\n");
                } else if (msg.getRole() == LLMMessage.Role.ASSISTANT) {
                    sb.append("AI: ").append(msg.getContent()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    // ==================== HISTORY PARSING ====================

    /**
     * Parses conversation history string into structured LLMMessages.
     *
     * <p>Format produced by ChatServiceImpl.formatConversationHistory:
     * <pre>
     * USER: content
     *
     * AI: content
     *
     * USER: content
     * </pre>
     *
     * <p>Content may contain newlines. Lines without a role prefix are appended
     * to the current message.
     */
    private List<LLMMessage> parseConversationHistory(String history) {
        List<LLMMessage> messages = new ArrayList<>();
        if (history == null || history.isBlank()) {
            return messages;
        }

        LLMMessage current = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : history.split("\n", -1)) {
            if (line.startsWith("USER: ")) {
                if (current != null) {
                    current.setContent(currentContent.toString().strip());
                    messages.add(current);
                }
                current = LLMMessage.user("");
                currentContent = new StringBuilder(line.substring(6));
            } else if (line.startsWith("AI: ")) {
                if (current != null) {
                    current.setContent(currentContent.toString().strip());
                    messages.add(current);
                }
                current = LLMMessage.assistant("");
                currentContent = new StringBuilder(line.substring(4));
            } else if (current != null && !line.isBlank()) {
                currentContent.append("\n").append(line);
            }
        }

        if (current != null && !currentContent.toString().isBlank()) {
            current.setContent(currentContent.toString().strip());
            messages.add(current);
        }

        return messages;
    }

    // ==================== SYSTEM INSTRUCTIONS ====================

    private String buildTarotSystemInstructions() {
        return """
                Bạn là một người đọc tarot — có óc quan sát, tinh tế, nói chuyện như đang trò với bạn.
                Dùng "mình" — "bạn". Không giảng bài. Không định nghĩa lá bài theo sách.

                TƯ DUY CỦA NGƯỜI ĐỌC TAROT:
                - Nhận xét về con người và tình huống, không giải thích lá bài
                - Mỗi lá bài chỉ nhắc tên MỘT lần, sau đó dùng năng lượng của nó
                - Chỉ tập trung 1-2 lá bài quan trọng nhất với câu hỏi
                - Các lá còn lại chỉ dùng để hỗ trợ, không phân tích riêng

                VIẾT NHƯ ĐANG NÓI CHUYỆN:
                - Nói thẳng, gần gũi — như đang chat, không như đang viết luận.
                - Không dùng câu mở đầu khuôn mẫu. Mỗi lần trả lời mở khác nhau.

                KHÔNG: Giảng nghĩa lá bài — Lặp tên lá bài — Mở đầu/kết luận dài
                KHÔNG: "Ultimately", "In conclusion", "Bringing it all together"
                KHÔNG: "The cards are telling you", "Remember that"
                KHÔNG: Giọng tự lực sáo rỗng, giọng diễn thuyết tạo động lực

                TRÁNH GIỌNG HUYỀN BÍ:
                KHÔNG dùng: "Mình nhìn thấy...", "Mình cảm nhận được năng lượng..."
                "Các lá bài đang muốn nhắn nhủ...", "Vũ trụ đang nói với bạn..."

                ---

                """;
    }

    private String buildAstrologySystemInstructions() {
        return """
                Bạn là nhà chiêm tinh Western (tâm lý–thực dụng) của sản phẩm trả phí.
                Người dùng bỏ tiền để nhận lời giải cụ thể, áp dụng được — không phải
                bài giảng, disclaimer, hay xin lỗi vì thiếu dữ liệu.

                Dùng "mình" — "bạn". Nói như đang chat với khách vừa trả tiền.

                GIÁ TRỊ PHẢI MANG LẠI (PAID CLIENT CONTRACT):
                - Mỗi câu trả lời phải dùng ÍT NHẤT hai dữ kiện có trong context
                  (Sun, Moon approx, ngày/nơi sinh, năm tuổi, lá bài nếu có) và nối
                  thẳng vào câu hỏi — không viết horoscope chung chung.
                - Câu hỏi may mắn / hôm nay / xu hướng: câu ĐẦU = có / không / vừa phải,
                  rồi 1 câu Sun+Moon, rồi 1 việc cụ thể làm hôm nay.
                - Không disclaimer. Không "khó khẳng định". Không "chưa đủ dữ liệu".
                - Không dùng ngoặc kép kiểu "Cancer (Gemini)" — tên Anh và Việt đã
                  ghi sẵn dạng "Cancer / Cự Giải"; Gemini = Song Tử, khác Cancer.

                TƯ DUY:
                - Chọn 1–2 yếu tố mạnh nhất liên quan câu hỏi, bỏ phần còn lại.
                - Chiêm tinh = xu hướng, không phải lời tiên tri tuyệt đối.
                - Câu khẳng định ngắn > câu hedging dài ("có thể là…", "có lẽ…").

                ---

                """;
    }

    // ==================== READING CONTEXT ====================

    private String buildReadingContext(String spreadName) {
        StringBuilder section = new StringBuilder();
        section.append("READING CONTEXT\n");

        if (spreadName != null && !spreadName.isBlank()) {
            section.append("Spread: ").append(spreadName).append("\n");
        } else {
            section.append("Spread: Tarot Reading\n");
        }

        // "Hôm nay" cần mốc lịch — không có thì model bịa hoặc nói chung chung
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String thu = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("vi"));
        section.append("Session date (Asia/Ho_Chi_Minh): ")
                .append(today.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .append(" (").append(thu).append(")\n");
        section.append("Khi họ hỏi \"hôm nay\" / \"tuần này\": bám Session date ở trên.\n");

        section.append("\n---\n\n");
        return section.toString();
    }

    /**
     * Few-shot theo Gemini prompt strategies + Esotier (paid client, no disclaimer).
     * Ví dụ cụ thể hiệu quả hơn liệt kê cấm đoán dài.
     */
    private String buildFewShotExamples(boolean hasCards) {
        if (hasCards) {
            return """
                    FEW-SHOT (bám đúng giọng & độ dài — KHÔNG chép nguyên văn):

                    Q: Mối quan hệ này đang đi về đâu?
                    Cards: The Fool, The Star, Ace of Cups | Sun: Cancer / Cự Giải
                    SAI: "Mình thấy ngay từ đầu… Eight of Swords là lá bài của…" + 5 đoạn.
                    ĐÚNG: "Đang nghiêng về mở lại, nhưng còn lửng.
                    The Fool + Ace of Cups: có cửa bắt đầu thật nếu bạn nói rõ nhu cầu thay vì đoán ý.
                    Việc cụ thể: trong 48h tới, hỏi một câu thẳng về định hướng chung."

                    ---

                    """;
        }
        return """
                FEW-SHOT (bám đúng giọng & độ dài — KHÔNG chép nguyên văn):

                Q: Hôm nay tôi có may mắn không?
                Context: Sun Cancer / Cự Giải, Moon Scorpio / Thiên Yết, sinh Hà Nội 2005-06-25
                SAI: "Mình chưa có Moon nên khó khẳng định… Sun Cancer (Gemini)…"
                → lỗi: xin lỗi thiếu data, lẫn tên cung, không trả lời có/không trước.
                ĐÚNG: "Hôm nay nghiêng may mắn vừa phải — không phải ngày bùng nổ.
                Sun Cự Giải + Moon Thiên Yết: hợp việc nhỏ, kỹ, gần người quen hơn mạo hiểm lớn.
                Một việc: chọn một việc dang dở và chốt trước 18h."

                Q: Tuần này công việc thế nào?
                ĐÚNG: "Tuần này thiên về chỉnh sửa hơn bung phá.
                Sun + nơi sinh / tuổi trong context → nói 1 điểm áp vào deadline hoặc đồng nghiệp.
                Kết bằng 1 hành động trong tuần."

                ---

                """;
    }

    // ==================== ASTROLOGY CONTEXT ====================

    private String buildAstrologyContext(AstrologyContextDTO astrology, boolean hasCards) {
        if (astrology == null) {
            return "";
        }

        StringBuilder section = new StringBuilder();

        if (hasCards) {
            section.append("=== ASTROLOGY CONTEXT (Supporting Context) ===\n\n");
        } else {
            section.append("=== ASTROLOGY CONTEXT (Primary Source of Insight) ===\n\n");
        }

        // Natal Chart Foundation
        section.append("Natal Chart:\n");
        section.append("  • Birth Date: ").append(astrology.getBirthDate());
        if (astrology.getBirthTime() != null) {
            section.append(" at ").append(astrology.getBirthTime());
        }
        if (astrology.getBirthPlace() != null && !astrology.getBirthPlace().isBlank()) {
            section.append(" in ").append(astrology.getBirthPlace()).append("\n");
        } else {
            section.append("\n");
        }

        // Primary Zodiac Data — kèm tên tiếng Việt để model khỏi lẫn Cancer/Gemini
        section.append("  • Sun: ").append(astrology.getSunSign());
        try {
            section.append(" / ").append(ZodiacCalculator.vietnameseSignName(astrology.getSunSign()));
        } catch (IllegalArgumentException ignored) {
            // unknown sign — skip VI label
        }
        if (astrology.getElement() != null && !astrology.getElement().isBlank()) {
            section.append(" (").append(astrology.getElement()).append(")");
        }
        if (astrology.getModality() != null && !astrology.getModality().isBlank()) {
            section.append(" — ").append(astrology.getModality()).append(" modality");
        }
        section.append("\n");
        if (astrology.getMoonSign() != null) {
            section.append("  • Moon: ").append(astrology.getMoonSign());
            try {
                section.append(" / ").append(ZodiacCalculator.vietnameseSignName(astrology.getMoonSign()));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
            section.append("\n");
        }
        if (astrology.getRisingSign() != null) {
            section.append("  • Rising: ").append(astrology.getRisingSign());
            try {
                section.append(" / ").append(ZodiacCalculator.vietnameseSignName(astrology.getRisingSign()));
            } catch (IllegalArgumentException ignored) {
                // skip
            }
            section.append("\n");
        }
        if (astrology.getBirthDate() != null) {
            try {
                String animal = ZodiacCalculator.approximateChineseZodiac(astrology.getBirthDate());
                section.append("  • Birth year animal (approx): ").append(animal)
                        .append(" — dùng như màu tuổi, không thay Sun/Moon.\n");
            } catch (IllegalArgumentException ignored) {
                // skip
            }
        }

        // Natal Planet Positions
        if (astrology.getNatalPlanetPositions() != null && !astrology.getNatalPlanetPositions().isEmpty()) {
            section.append("\nPlanetary Positions:\n");
            for (PlanetPositionDTO planet : astrology.getNatalPlanetPositions()) {
                section.append("- ").append(planet.getPlanetName())
                        .append(" in ").append(planet.getSign())
                        .append(" at ").append(planet.getDegree()).append("°");
                if (planet.getHouse() != null) {
                    section.append(" (House ").append(planet.getHouse()).append(")");
                }
                if (planet.getRetrograde() != null && planet.getRetrograde()) {
                    section.append(" [Retrograde]");
                }
                section.append("\n");
            }
        }

        // Natal Aspects
        if (astrology.getNatalAspects() != null && !astrology.getNatalAspects().isEmpty()) {
            section.append("\nNatal Aspects:\n");
            for (AspectDTO aspect : astrology.getNatalAspects()) {
                section.append("- ").append(aspect.getPlanet1())
                        .append(" ").append(aspect.getAspectType())
                        .append(" ").append(aspect.getPlanet2())
                        .append(" (").append(aspect.getExactDegree()).append("° exact, ")
                        .append(aspect.getOrb()).append("° orb)\n");
            }
        }

        // Current Transits
        if (astrology.getCurrentTransit() != null && !astrology.getCurrentTransit().isBlank()) {
            section.append("\nCurrent Influences:\n");
            section.append("- Transit: ").append(astrology.getCurrentTransit()).append("\n");
        }

        // Transit Aspects
        if (astrology.getTransitAspects() != null && !astrology.getTransitAspects().isEmpty()) {
            section.append("\nActive Aspects:\n");
            for (AspectDTO aspect : astrology.getTransitAspects()) {
                section.append("- ").append(aspect.getPlanet1())
                        .append(" ").append(aspect.getAspectType())
                        .append(" ").append(aspect.getPlanet2())
                        .append(" (orb: ").append(aspect.getOrb()).append("°)\n");
            }
        }

        // Personalization Guidance — only mention fields that exist
        section.append("\nHOW TO USE THIS ASTROLOGY DATA:\n");
        section.append("• Sun sign → core identity and ego drives.\n");
        if (astrology.getMoonSign() != null) {
            section.append("• Moon sign → cảm xúc, cách đón nhận năng lượng ngày; dùng khi hỏi may mắn/hôm nay/tâm trạng.\n");
            section.append("  (Moon có thể là ước lượng từ ngày/giờ sinh — vẫn dùng để tư vấn, đừng xin lỗi vì \"chưa có\".)\n");
        }
        if (astrology.getElement() != null && !astrology.getElement().isBlank()) {
            section.append("• Element (").append(astrology.getElement()).append(") → emotional and behavioral style.\n");
        }
        if (astrology.getModality() != null && !astrology.getModality().isBlank()) {
            section.append("• Modality (").append(astrology.getModality()).append(") → approach to action and change.\n");
        }
        if (hasCards) {
            section.append("Let these traits subtly colour the card interpretation.\n");
            section.append("Do NOT make deterministic predictions based solely on astrology.\n");
            section.append("Reminder: Tarot cards are the primary source of insight. Astrology enriches, it does not override.\n");
        } else {
            if (astrology.getNatalPlanetPositions() != null && !astrology.getNatalPlanetPositions().isEmpty()) {
                section.append("• Planetary positions → specific areas of life (House) and how you express energy.\n");
            }
            if (astrology.getNatalAspects() != null && !astrology.getNatalAspects().isEmpty()) {
                section.append("• Aspects → how different parts of your personality interact.\n");
            }
            if (astrology.getCurrentTransit() != null && !astrology.getCurrentTransit().isBlank()) {
                section.append("• Transits → current energies affecting you now.\n");
            }
            section.append("Use this as the PRIMARY source of insight. Connect it directly to the user's question.\n");
        }

        // Data safety: trả lời bằng dữ liệu có — không lấy thiếu data làm chủ đề
        section.append("\nDATA SAFETY:\n");
        section.append("• Dùng TỐI ĐA dữ liệu có trong context (Sun, Moon approx, ngày/nơi sinh, năm tuổi, lá bài).\n");
        section.append("• KHÔNG được tự bịa Rising / nhà / độ hành tinh nếu không có trong context.\n");
        section.append("• CẤM mở bài hoặc chiếm nửa câu trả lời bằng kiểu: \"mình chưa có Moon/Rising nên khó khẳng định\".\n");
        section.append("  Người dùng trả tiền để được xem/bói — hãy trả lời thẳng bằng Sun + Moon (approx) + câu hỏi.\n");
        section.append("• Chỉ nhắc giới hạn dữ liệu KHI họ hỏi rõ về Rising/nhà/độ chính xác ephemeris.\n");
        section.append("• Không viết nhầm tên cung (Cancer = Cự Giải, Gemini = Song Tử — đừng ghi lẫn).\n");
        section.append("• CẤM format \"Cancer (Gemini)\" / \"Cự Giải (Gemini)\" — đó là lỗi lẫn tên cung.\n");

        section.append("\n---\n\n");
        return section.toString();
    }

    // ==================== TAROT CARDS SECTION ====================

    private String buildTarotCardsSection(List<DrawnCardDetailDTO> cards) {
        if (cards == null || cards.isEmpty()) {
            return "";
        }

        StringBuilder section = new StringBuilder();
        section.append("TAROT CARDS\n\n");

        List<DrawnCardDetailDTO> sortedCards = cards.stream()
                .sorted((a, b) -> Short.compare(a.getPosition(), b.getPosition()))
                .collect(Collectors.toList());

        for (DrawnCardDetailDTO card : sortedCards) {
            section.append("Card ").append(card.getPosition() + 1).append(": ")
                    .append(card.getCardName())
                    .append(" — ").append(card.getReversed() ? "Reversed" : "Upright")
                    .append(" (").append(card.getArcanaType()).append(")\n");
        }

        section.append("\n---\n\n");
        return section.toString();
    }

    // ==================== RESPONSE INSTRUCTIONS ====================

    private String buildResponseInstructions(boolean hasCards) {
        if (hasCards) {
            return buildTarotResponseInstructions();
        } else {
            return buildAstrologyResponseInstructions();
        }
    }

    private String buildTarotResponseInstructions() {
        return """
                ĐỘ DÀI — ĐÂY LÀ RÀNG BUỘC CỨNG:
                - Câu hỏi ngắn, hỏi có/không, hỏi về một ngày: 60-100 từ. Hai đoạn là nhiều.
                - Câu hỏi về một tình huống cụ thể: 150-250 từ.
                - Chỉ vượt 250 từ khi người dùng hỏi nhiều ý rõ rệt trong cùng một câu.
                - KHÔNG BAO GIỜ quá 400 từ.
                - Người đọc trên điện thoại. Năm đoạn văn cho một câu hỏi đơn giản là thất bại,
                  dù từng đoạn viết hay đến đâu.

                CÁCH TRẢ LỜI:
                - CÂU ĐẦU TIÊN phải là câu trả lời thẳng cho đúng câu họ hỏi.
                  Họ hỏi "hôm nay có may mắn không" thì câu đầu phải nói có hoặc không,
                  rồi mới giải thích. Đừng mở bài, đừng dẫn dắt, đừng mô tả lá bài trước.
                - Sau đó tối đa hai ý giải thích. Chọn ý mạnh nhất, bỏ phần còn lại.
                - Thà bỏ sót một ý hay còn hơn chôn câu trả lời dưới bốn đoạn văn.
                - Kết bằng một câu gợi mở hoặc một việc cụ thể họ làm được hôm nay.

                TÍNH CHÍNH XÁC:
                - Dùng lá bài + chiêm tinh có trong context để trả lời cụ thể.
                - Không bịa dữ liệu còn thiếu (Rising/nhà) để làm câu trả lời có vẻ hoàn chỉnh.
                - CẤM lấy việc thiếu Moon/Rising làm chủ đề chính của câu trả lời.
                - Không biến diễn giải tarot thành fact khách quan hoặc certainty tuyệt đối.
                - Tránh: "Bạn chắc chắn sẽ...", "Người đó chắc chắn...", "Tháng sau chắc chắn..."

                GIỌNG NÓI:
                - Nói chuyện tự nhiên như đang trò chuyện, không viết luận.
                - Câu ngắn. Tránh câu ghép ba bốn mệnh đề nối bằng dấu phẩy.
                - Không triết lý. Nói thẳng điều người ta cần biết.
                - Không ép phải có cấu trúc giống nhau cho mọi câu trả lời.

                CẤM TUYỆT ĐỐI:
                - Mở đầu bằng lời chào + tên ("Chào Hải", "Chào bạn") rồi mới vào bài.
                - Mở đầu bằng "Mình thấy...", "Mình thấy ngay từ đầu...",
                  "Mình thấy ở đây có một điểm khá rõ..." hoặc bất kỳ biến thể nào.
                  Các câu ấy từng nằm trong hướng dẫn làm ví dụ, mô hình chép nguyên văn.
                - Mở đầu bằng cách gọi tên người dùng rồi xuống dòng. Vào thẳng nội dung.
                - Viết quá hai đoạn cho câu hỏi có/không hoặc hỏi về một ngày.
                - Xin lỗi / né vì "chưa có Moon" khi đã có Sun/lá bài để trả lời.
                - Giải nghĩa lá bài từ A-Z. KHÔNG viết kiểu: "Eight of Swords là lá bài của..."
                - Lặp tên lá bài
                - "Ultimately" / "In conclusion" / "Bringing it all together"
                - "The cards are telling you" / "Remember that"
                - Giọng huyền bí: "Mình nhìn thấy...", "Cảm nhận năng lượng...", "Vũ trụ nói..."
                - Giọng diễn thuyết tạo động lực
                - Thêm card không có trong reading
                - Đảo ngược card orientation
                - Tự tạo spread position
                """;
    }

    private String buildAstrologyResponseInstructions() {
        return """
                ĐỘ DÀI — ĐÂY LÀ RÀNG BUỘC CỨNG:
                - Câu hỏi ngắn, hỏi có/không, hỏi về một ngày: 60-100 từ. Hai đoạn là nhiều.
                - Câu hỏi về một tình huống cụ thể: 150-250 từ.
                - KHÔNG BAO GIỜ quá 400 từ.
                - Người đọc trên điện thoại. Năm đoạn văn cho câu hỏi đơn giản là thất bại.

                KHUÔN TRẢ LỜI CÓ/KHÔNG & "HÔM NAY":
                1) Câu 1: phán đoán thẳng (có / không / vừa phải + mức độ).
                2) Câu 2: Sun + Moon (tên Việt hoặc Anh/Việt đúng) gắn vào câu hỏi.
                3) Câu 3: một việc cụ thể làm được hôm nay (thời điểm / hành động).
                Không thêm đoạn thứ tư.

                TÍNH CHÍNH XÁC:
                - Dùng dữ liệu trong context — đó là lý do người dùng trả tiền.
                - Không bịa Rising / nhà / độ hành tinh nếu không có.
                - CẤM lấy việc thiếu dữ liệu làm chủ đề chính ("chưa có Moon nên khó nói").
                - Không biến chiêm tinh thành certainty tuyệt đối; nói xu hướng.
                - Không lẫn tên cung Việt–Anh. CẤM viết "Cancer (Gemini)" / "Cự Giải (Gemini)".

                GIỌNG NÓI:
                - Nói chuyện tự nhiên như đang trò chuyện, không viết luận.
                - Câu ngắn. Tránh câu ghép ba bốn mệnh đề nối bằng dấu phẩy.
                - Không triết lý. Nói thẳng điều người ta cần biết.
                - Declarative: "Hôm nay nghiêng…" thay vì "Có thể là hôm nay…".

                CẤM TUYỆT ĐỐI:
                - Mở đầu bằng lời chào + tên ("Chào Hải", "Chào bạn") rồi mới vào bài.
                - Mở đầu bằng "Mình thấy...", "Mình thấy ngay từ đầu...",
                  "Mình thấy ở đây có một điểm khá rõ..." hoặc bất kỳ biến thể nào.
                - Mở đầu bằng cách gọi tên người dùng rồi xuống dòng. Vào thẳng nội dung.
                - Viết quá hai đoạn cho câu hỏi có/không hoặc hỏi về một ngày.
                - Xin lỗi / né tránh vì thiếu Rising hoặc "chưa đủ dữ liệu Moon".
                - Giải nghĩa từng vị trí hành tinh một cách máy móc
                - "Ultimately" / "In conclusion" / "Bringing it all together"
                - Giọng huyền bí: "Mình nhìn thấy...", "Cảm nhận năng lượng..."
                - Dự đoán chính xác, khẳng định tuyệt đối (chỉ nói xu hướng)
                - Giọng diễn thuyết tạo động lực
                - Liệt kê tất cả các hành tinh — chỉ chọn những cái liên quan
                """;
    }
}
