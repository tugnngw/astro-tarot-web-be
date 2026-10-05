package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.astrology.AspectDTO;
import com.exe.astratarot.domain.dto.astrology.PlanetPositionDTO;
import com.exe.astratarot.domain.dto.llm.LLMMessage;
import com.exe.astratarot.domain.dto.llm.LLMRequest;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import com.exe.astratarot.service.PromptBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
     * <p>1200 token xấp xỉ 700–800 từ tiếng Việt — đủ cho một lời giải ba lá
     * có chiều sâu, mà vẫn chặn được những bài luận lan man. Đây là trần cứng,
     * phần hướng dẫn trong lời nhắc mới là thứ định độ dài thường ngày; trần
     * chỉ để chặn trường hợp mô hình quên mất mình đang nói gì.
     */
    private static final int GIOI_HAN_TOKEN_TRA_LOI = 1200;

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
                Bạn là một nhà chiêm tinh học — tinh tế, quan sát, nói chuyện như đang trò chuyện với bạn.
                Dùng "mình" — "bạn". Không giảng bài. Không định nghĩa các khái niệm chiêm tinh theo sách.

                TƯ DUY CỦA NHÀ CHIÊM TINH:
                - Phân tích bản đồ sao của người hỏi để đưa ra insight
                - Kết nối vị trí các hành tinh với câu hỏi của họ
                - Chỉ tập trung vào 2-3 yếu tố nổi bật nhất trong biểu đồ
                - Dùng chiêm tinh như một công cụ để thấu hiểu, không phải để tiên đoán

                VIẾT NHƯ ĐANG NÓI CHUYỆN:
                - Nói thẳng, gần gũi — như đang chat, không như đang viết luận.
                - Không dùng câu mở đầu khuôn mẫu. Mỗi lần trả lời mở khác nhau.

                KHÔNG: Giảng nghĩa các cung/hành tinh — Lặp tên các vị trí
                KHÔNG: "Ultimately", "In conclusion", "Bringing it all together"
                KHÔNG: Giọng huyền bí, giọng diễn thuyết tạo động lực
                KHÔNG: Đưa ra dự đoán tuyệt đối, chỉ đưa ra xu hướng và tiềm năng

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

        section.append("\n---\n\n");
        return section.toString();
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

        // Primary Zodiac Data
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

        // Personalization Guidance — only mention fields that exist
        section.append("\nHOW TO USE THIS ASTROLOGY DATA:\n");
        section.append("• Sun sign → core identity and ego drives.\n");
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

        // Data safety: only mention what's in context
        section.append("\nDATA SAFETY:\n");
        section.append("Chỉ nhắc đến các dữ liệu có trong context trên.\n");
        section.append("Nếu Moon, Rising, hoặc vị trí hành tinh nào không có trong context, KHÔNG được tự đoán, tự tính, hay trình bày như fact.\n");
        section.append("Nếu cần đề cập dữ liệu không có, nói rõ: mình chưa có dữ liệu đó trong context hiện tại.\n");

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
                - Chỉ sử dụng dữ liệu được cung cấp trong context.
                - Không bịa dữ liệu còn thiếu để làm câu trả lời có vẻ hoàn chỉnh.
                - Nếu thông tin cần thiết không có, nói rõ giới hạn của dữ liệu.
                - Không biến diễn giải tarot thành fact khách quan hoặc certainty tuyệt đối.
                - Tránh: "Bạn chắc chắn sẽ...", "Người đó chắc chắn...", "Tháng sau chắc chắn..."

                GIỌNG NÓI:
                - Nói chuyện tự nhiên như đang trò chuyện, không viết luận.
                - Câu ngắn. Tránh câu ghép ba bốn mệnh đề nối bằng dấu phẩy.
                - Không triết lý. Nói thẳng điều người ta cần biết.
                - Không ép phải có cấu trúc giống nhau cho mọi câu trả lời.

                CẤM TUYỆT ĐỐI:
                - Mở đầu bằng "Mình thấy ở đây có một điểm khá rõ" hoặc bất kỳ biến thể nào
                  của nó. Câu ấy từng nằm trong hướng dẫn này làm ví dụ, và mô hình chép
                  nguyên văn ở gần như mọi câu trả lời.
                - Mở đầu bằng cách gọi tên người dùng rồi xuống dòng. Vào thẳng nội dung.
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
                - Người đọc trên điện thoại. Năm đoạn văn cho một câu hỏi đơn giản là thất bại.

                CÁCH TRẢ LỜI:
                - CÂU ĐẦU TIÊN phải là câu trả lời thẳng cho đúng câu họ hỏi, rồi mới giải thích.
                - Sau đó tối đa hai ý. Chọn ý mạnh nhất, bỏ phần còn lại.
                - Kết bằng một câu gợi mở hoặc một việc cụ thể họ làm được.

                TÍNH CHÍNH XÁC:
                - Chỉ sử dụng dữ liệu được cung cấp trong context.
                - Không bịa dữ liệu còn thiếu để làm câu trả lời có vẻ hoàn chỉnh.
                - Nếu thông tin cần thiết không có, nói rõ giới hạn của dữ liệu.
                - Không biến diễn giải astrology thành fact khách quan hoặc certainty tuyệt đối.
                - Tránh: "Bạn chắc chắn sẽ...", "Tháng sau chắc chắc..."
                - Chỉ nhắc đến các dữ liệu astrology có trong context. Không tự đoán Moon/Rising/planets nếu không có.

                GIỌNG NÓI:
                - Nói chuyện tự nhiên như đang trò chuyện, không viết luận.
                - Câu ngắn. Tránh câu ghép ba bốn mệnh đề nối bằng dấu phẩy.
                - Không triết lý. Nói thẳng điều người ta cần biết.
                - Không ép phải có cấu trúc giống nhau cho mọi câu trả lời.

                CẤM TUYỆT ĐỐI:
                - Mở đầu bằng "Mình thấy ở đây có một điểm khá rõ" hoặc biến thể của nó.
                  Câu ấy từng nằm trong hướng dẫn này làm ví dụ, và mô hình chép nguyên văn
                  ở gần như mọi câu trả lời.
                - Mở đầu bằng cách gọi tên người dùng rồi xuống dòng. Vào thẳng nội dung.
                - Giải nghĩa từng vị trí hành tinh một cách máy móc
                - "Ultimately" / "In conclusion" / "Bringing it all together"
                - Giọng huyền bí: "Mình nhìn thấy...", "Cảm nhận năng lượng..."
                - Dự đoán chính xác, khẳng định tuyệt đối (chỉ nói xu hướng)
                - Giọng diễn thuyết tạo động lực
                - Liệt kê tất cả các hành tinh — chỉ chọn những cái liên quan
                - Tự đoán dữ liệu chiêm tinh không có trong context
                """;
    }
}
