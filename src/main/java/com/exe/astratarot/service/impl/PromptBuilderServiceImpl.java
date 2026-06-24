package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.astrology.AspectDTO;
import com.exe.astratarot.domain.dto.astrology.PlanetPositionDTO;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.service.PromptBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of PromptBuilderService.
 *
 * Constructs prompts by composing six distinct sections:
 * 1. System Instructions (fixed)
 * 2. Reading Context (from request)
 * 3. Astrology Context (from AstrologyContextDTO)
 * 4. Tarot Cards (from DrawnCardDetailDTO list)
 * 5. User Question (verbatim from request)
 * 6. Response Instructions (fixed)
 *
 * No external dependencies. Pure string composition.
 */
@Service
@RequiredArgsConstructor
public class PromptBuilderServiceImpl implements PromptBuilderService {

    @Override
    public String buildPrompt(BuildPromptRequest request) {
        StringBuilder prompt = new StringBuilder();

        // Section 1: System Instructions (PHÂN BIỆT theo loại)
        boolean hasCards = request.getDrawnCardDetails() != null && !request.getDrawnCardDetails().isEmpty();
        prompt.append(buildSystemInstructions(hasCards));

        // Section 2: Reading Context
        prompt.append(buildReadingContext(request.getSpreadName()));

        // Section 3: Astrology Context
        if (request.getAstrologyContext() != null) {
            prompt.append(buildAstrologyContext(request.getAstrologyContext(), hasCards));
        } else {
            prompt.append(buildAstrologyContextUnavailable());
        }

        // Section 4: Tarot Cards - CHỈ KHI CÓ CARD
        if (hasCards) {
            prompt.append(buildTarotCardsSection(request.getDrawnCardDetails()));
        }

        // Section 5: Conversation History (follow-up only)
        if (request.getConversationHistory() != null && !request.getConversationHistory().isBlank()) {
            prompt.append(buildConversationHistorySection(request.getConversationHistory()));
        }

        // Section 5b: Original Question (follow-up only)
        if (request.getOriginalQuestion() != null && !request.getOriginalQuestion().isBlank()) {
            prompt.append(buildOriginalQuestionSection(request.getOriginalQuestion()));
        }

        // Section 6: User Question
        prompt.append(buildUserQuestionSection(request.getUserQuestion()));

        // Section 7: Response Instructions (PHÂN BIỆT theo loại)
        prompt.append(buildResponseInstructions(hasCards));

        return prompt.toString();
    }

    // ==================== SYSTEM INSTRUCTIONS ====================

    /**
     * Builds system instructions based on whether tarot cards are present.
     */
    private String buildSystemInstructions(boolean hasCards) {
        if (hasCards) {
            return buildTarotSystemInstructions();
        } else {
            return buildAstrologySystemInstructions();
        }
    }

    /**
     * Tarot persona system instructions.
     */
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
                - "Mình thấy...", "Có vẻ như...", "Điều mình để ý là..."
                - "Nếu nhìn theo góc độ này...", "Mình nghĩ điều đáng quan tâm nhất là..."

                KHÔNG: Giảng nghĩa lá bài — Lặp tên lá bài — Mở đầu/kết luận dài
                KHÔNG: "Ultimately", "In conclusion", "Bringing it all together"
                KHÔNG: "The cards are telling you", "Remember that"
                KHÔNG: Giọng tự lực sáo rỗng, giọng diễn thuyết tạo động lực

                TRÁNH GIỌNG HUYỀN BÍ:
                KHÔNG dùng: "Mình nhìn thấy...", "Mình cảm nhận được năng lượng..."
                "Các lá bài đang muốn nhắn nhủ...", "Vũ trụ đang nói với bạn..."
                THAY BẰNG: "Mình thấy...", "Có vẻ như...", "Nếu nhìn từ trải bài này..."

                ---

                """;
    }

    /**
     * Astrology-only persona system instructions.
     */
    private String buildAstrologySystemInstructions() {
        return """
                Bạn là một nhà chiêm tinh học — tinh tế, quan sát, nói chuyện như đang trò chuyện với bạn.
                Dùng "mình" — "bạn". Không giảng bài. Không định nghĩa các khái niệm chiêm tinh theo sách.

                TƯ DUY CỦA NHÀ CHIÊM TINH:
                - Phân tích bản đồ sao của người hỏi để đưa ra insight
                - Kết nối vị trí các hành tinh với câu hỏi của họ
                - Chỉ tập trung vào 2-3 yếu tố nổi bật nhất trong biểu đồ
                - Dùng chiêm tinh như một công cụ để thấu hiểu, không phải để tiên đoán

                VIẾT NHƯ ĐANG NÓI CHUYỆN:
                - "Mình thấy...", "Có vẻ như...", "Điều mình để ý là..."
                - "Với Mặt Trời của bạn ở ...", "Mặt Trăng của bạn đang..."
                - "Điều thú vị trong biểu đồ của bạn là..."

                KHÔNG: Giảng nghĩa các cung/hành tinh — Lặp tên các vị trí
                KHÔNG: "Ultimately", "In conclusion", "Bringing it all together"
                KHÔNG: Giọng huyền bí, giọng diễn thuyết tạo động lực
                KHÔNG: Đưa ra dự đoán tuyệt đối, chỉ đưa ra xu hướng và tiềm năng

                ---

                """;
    }

    // ==================== READING CONTEXT ====================

    /**
     * Builds the reading context section.
     * Includes spread name and any relevant metadata.
     */
    private String buildReadingContext(String spreadName) {
        StringBuilder section = new StringBuilder();
        section.append("READING CONTEXT\n");

        if (spreadName != null && !spreadName.isBlank()) {
            section.append("Spread: ").append(spreadName).append("\n");
        } else {
            section.append("Spread: Tarot Reading\n");
        }

        section.append("\n---\n\n");
        return section.toString();
    }

    // ==================== ASTROLOGY CONTEXT ====================

    /**
     * Builds the astrology context section.
     * Formats natal chart and transit data into readable text.
     * When hasCards=false, astrology is the PRIMARY context.
     */
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

        // ——— Natal Chart Foundation ———
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

        // ——— Primary Zodiac Data ———
        section.append("  • Sun: ").append(astrology.getSunSign());
        if (astrology.getElement() != null && !astrology.getElement().isBlank()) {
            section.append(" (").append(astrology.getElement()).append(")");
        }
        if (astrology.getModality() != null && !astrology.getModality().isBlank()) {
            section.append(" — ").append(astrology.getModality()).append(" modality");
        }
        section.append("\n");
        if (astrology.getMoonSign() != null) {
            section.append("  • Moon: ").append(astrology.getMoonSign()).append("\n");
        }
        if (astrology.getRisingSign() != null) {
            section.append("  • Rising: ").append(astrology.getRisingSign()).append("\n");
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

        // ——— Personalization Guidance ———
        if (hasCards) {
            section.append("\nHOW TO USE THIS ASTROLOGY DATA:\n");
            section.append("• Sun sign → core identity and ego drives.\n");
            section.append("• Element (Fire/Earth/Air/Water) → emotional and behavioral style.\n");
            section.append("• Modality (Cardinal/Fixed/Mutable) → approach to action and change.\n");
            section.append("Let these traits subtly colour the card interpretation.\n");
            section.append("Do NOT make deterministic predictions based solely on astrology.\n");
            section.append("Reminder: Tarot cards are the primary source of insight. Astrology enriches, it does not override.\n");
        } else {
            section.append("\nHOW TO USE THIS ASTROLOGY DATA:\n");
            section.append("• Sun sign → core identity and ego drives.\n");
            section.append("• Element (Fire/Earth/Air/Water) → emotional and behavioral style.\n");
            section.append("• Modality (Cardinal/Fixed/Mutable) → approach to action and change.\n");
            section.append("• Planetary positions → specific areas of life (House) and how you express energy.\n");
            section.append("• Aspects → how different parts of your personality interact.\n");
            section.append("• Transits → current energies affecting you now.\n");
            section.append("Use this as the PRIMARY source of insight. Connect it directly to the user's question.\n");
            section.append("Be specific: mention their Sun sign, Moon sign, or key aspects that are relevant.\n");
        }

        section.append("\n---\n\n");
        return section.toString();
    }

    /**
     * Builds an unavailable astrology context section.
     * Used when astrology context is null or not provided.
     */
    private String buildAstrologyContextUnavailable() {
        return "";
    }

    // ==================== TAROT CARDS SECTION ====================

    /**
     * Builds the tarot cards section.
     * Lists each drawn card with position and orientation.
     */
    private String buildTarotCardsSection(List<DrawnCardDetailDTO> cards) {
        if (cards == null || cards.isEmpty()) {
            return "";
        }

        StringBuilder section = new StringBuilder();
        section.append("TAROT CARDS\n\n");

        // Sort by position to ensure correct order
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

    // ==================== USER QUESTION ====================

    /**
     * Builds the user question section.
     * Includes the user's question verbatim.
     */
    private String buildUserQuestionSection(String userQuestion) {
        return "USER QUESTION\n" +
                userQuestion + "\n\n" +
                "---\n\n";
    }

    // ==================== CONVERSATION HISTORY ====================

    /**
     * Builds the conversation history section.
     * Contains previous USER/AI message pairs for follow-up context.
     * Latest messages at the bottom. Empty for initial readings.
     */
    private String buildConversationHistorySection(String conversationHistory) {
        return "CONVERSATION HISTORY\n" +
                conversationHistory + "\n" +
                "---\n\n";
    }

    /**
     * Builds the original question section.
     * Preserves the user's initial reading question for context.
     */
    private String buildOriginalQuestionSection(String originalQuestion) {
        return "ORIGINAL QUESTION\n" +
                originalQuestion + "\n\n" +
                "---\n\n";
    }

    // ==================== RESPONSE INSTRUCTIONS ====================

    /**
     * Builds response instructions based on whether tarot cards are present.
     */
    private String buildResponseInstructions(boolean hasCards) {
        if (hasCards) {
            return buildTarotResponseInstructions();
        } else {
            return buildAstrologyResponseInstructions();
        }
    }

    /**
     * Tarot response instructions.
     */
    private String buildTarotResponseInstructions() {
        return """
                HƯỚNG DẪN VIẾT BÀI ĐỌC TAROT:

                ĐỘ DÀI: Viết thoải mái, bao nhiêu cũng được, MIỄN LÀ ĐỦ Ý. Đừng viết lan man, đừng kéo dài vô nghĩa.
                Mỗi câu đều phải có giá trị. Nếu ý đã rõ, dừng lại. Không thêm thắt cho đủ chữ.

                CẤU TRÚC BÀI ĐỌC LẦN ĐẦU:
                1. Một câu ngắn — cảm nhận của bạn về câu hỏi.
                2. Lá bài nổi bật nhất — nó đang phản ánh điều gì trong con người bạn.
                3. Các lá còn lại hỗ trợ hoặc sắc thái gì thêm.
                4. Kết thúc tự nhiên — có thể là một nhận xét ngắn, một insight thực tế,
                   hoặc một câu hỏi nhẹ để bạn suy nghĩ. Đừng gượng ép đặt câu hỏi.

                CẤU TRÚC CHAT TIẾP THEO:
                1. Trả lời thẳng.
                2. Một câu kết nối với bài đã đọc.
                3. Dừng lại. Không cần kết luận.

                NGUYÊN TẮC VIẾT:
                - Nói chuyện, không viết luận
                - Trả lời câu hỏi trước
                - Chỉ nhắc tên lá bài MỘT lần
                - Dùng: "Mình thấy...", "Có vẻ...", "Điều nổi bật là..."
                - Câu ngắn. Xuống dòng tự nhiên.
                - ĐỦ Ý thì DỪNG. Không thêm thắt.

                CẤM TUYỆT ĐỐI:
                - Giải nghĩa lá bài từ A-Z. KHÔNG viết kiểu: "Eight of Swords là lá bài của..."
                - Lặp tên lá bài
                - "Ultimately" / "In conclusion" / "Bringing it all together"
                - "The cards are telling you" / "Remember that"
                - Kết luận kiểu "Hãy luôn nhớ rằng..."
                - Giọng huyền bí: "Mình nhìn thấy...", "Cảm nhận năng lượng...", "Vũ trụ nói..."
                - Giọng diễn thuyết tạo động lực
                - Viết lan man, thêm thắt vô nghĩa

                GỢI NHỚ: Observant > Explanatory. Conversation > Essay. Insight > Lecture. Đủ ý thì dừng.
                """;
    }

    /**
     * Astrology-only response instructions.
     */
    private String buildAstrologyResponseInstructions() {
        return """
                HƯỚNG DẪN VIẾT TRẢ LỜI CHO BẢN ĐỒ SAO:

                ĐỘ DÀI: Viết thoải mái, bao nhiêu cũng được, MIỄN LÀ ĐỦ Ý. Đừng viết lan man, đừng kéo dài vô nghĩa.
                Mỗi câu đều phải có giá trị. Nếu ý đã rõ, dừng lại. Không thêm thắt cho đủ chữ.

                CẤU TRÚC TRẢ LỜI:
                1. Một câu ngắn — cảm nhận chung về biểu đồ sao của bạn.
                2. Điểm nổi bật nhất trong biểu đồ (Sun, Moon, Rising, hoặc một khía cạnh đặc biệt).
                3. Kết nối trực tiếp với câu hỏi bạn đặt ra.
                4. Đưa ra insight dựa trên vị trí các hành tinh/cung.
                5. Kết thúc nhẹ nhàng, có thể là một gợi ý hoặc một câu hỏi suy ngẫm.

                NGUYÊN TẮC VIẾT:
                - Nói chuyện, không viết luận
                - Trả lời câu hỏi trước
                - Dùng cụ thể: "Với Mặt Trời ở ...", "Mặt Trăng của bạn đang..."
                - Dùng: "Mình thấy...", "Có vẻ...", "Điều nổi bật là..."
                - Câu ngắn. Xuống dòng tự nhiên.
                - Tập trung vào 2-3 yếu tố quan trọng nhất, không liệt kê tất cả
                - ĐỦ Ý thì DỪNG. Không thêm thắt.

                CẤM TUYỆT ĐỐI:
                - Giải nghĩa từng vị trí hành tinh một cách máy móc
                - "Ultimately" / "In conclusion" / "Bringing it all together"
                - Giọng huyền bí: "Mình nhìn thấy...", "Cảm nhận năng lượng..."
                - Dự đoán chính xác, khẳng định tuyệt đối (chỉ nói xu hướng)
                - Giọng diễn thuyết tạo động lực
                - Liệt kê tất cả các hành tinh — chỉ chọn những cái liên quan
                - Viết lan man, thêm thắt vô nghĩa

                GỢI NHỚ: Observant > Explanatory. Conversation > Essay. Insight > Lecture. Đủ ý thì dừng.
                """;
    }
}