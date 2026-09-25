package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AspectDTO;
import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.astrology.PlanetPositionDTO;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.dto.prompt.DrawnCardDetailDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dựng lời nhắc cho AI. Lớp này trước đây phủ 68,3%, và phần chưa kiểm là phần
 * dữ liệu chiêm tinh — đúng phần dài nhất và dễ sai lặng lẽ nhất.
 *
 * <p>Vì sao nó đáng kiểm dù chỉ là ghép chuỗi: <b>mỗi trường thiếu mà không
 * được bảo vệ sẽ in ra chữ "null" vào giữa lời nhắc</b>, và mô hình đọc chữ
 * "null" như một dữ kiện. Một người dùng chưa khai giờ sinh sẽ nhận một lời
 * giải nói về "Moon: null" — sai một cách đầy tự tin.
 *
 * <p>Chỗ thứ hai: <b>vai trò của chiêm tinh đổi theo việc có bài hay không</b>.
 * Có bài thì chiêm tinh là bối cảnh phụ và lời nhắc phải nói rõ "không được lấn
 * át lá bài"; không có bài thì chính nó là nguồn chính. Lẫn hai chế độ là AI
 * trả lời sai thể loại câu hỏi.
 */
class PromptBuilderServiceImplContextTest {

    private PromptBuilderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromptBuilderServiceImpl();
    }

    private AstrologyContextDTO chiemTinhDayDu() {
        return AstrologyContextDTO.builder()
                .birthDate(LocalDate.of(2000, 5, 17))
                .birthTime(LocalTime.of(7, 30))
                .birthPlace("Hà Nội, Việt Nam")
                .sunSign("Taurus")
                .element("Earth")
                .modality("Fixed")
                .moonSign("Pisces")
                .risingSign("Leo")
                .natalPlanetPositions(List.of(
                        PlanetPositionDTO.builder().planetName("Sun").sign("Taurus")
                                .degree(new BigDecimal("26.5")).house(10).retrograde(false).build(),
                        PlanetPositionDTO.builder().planetName("Mercury").sign("Gemini")
                                .degree(new BigDecimal("3.2")).house(11).retrograde(true).build()))
                .natalAspects(List.of(AspectDTO.builder()
                        .planet1("Sun").planet2("Moon").aspectType("Trine")
                        .exactDegree(new BigDecimal("120")).orb(new BigDecimal("2.1")).build()))
                .currentTransit("Sao Thuỷ nghịch hành")
                .transitAspects(List.of(AspectDTO.builder()
                        .planet1("Mars").planet2("Venus").aspectType("Square")
                        .orb(new BigDecimal("1.5")).build()))
                .build();
    }

    private DrawnCardDetailDTO la(String ten, short viTri, boolean nguoc) {
        return DrawnCardDetailDTO.builder()
                .cardId(UUID.randomUUID())
                .cardName(ten)
                .arcanaType("MAJOR")
                .position(viTri)
                .reversed(nguoc)
                .build();
    }

    private BuildPromptRequest yeuCau(AstrologyContextDTO chiemTinh, List<DrawnCardDetailDTO> baiRut) {
        return BuildPromptRequest.builder()
                .userQuestion("Tôi nên đổi việc không?")
                .astrologyContext(chiemTinh)
                .drawnCardDetails(baiRut)
                .spreadName("Ba lá")
                .build();
    }

    // =====================================================================

    @Nested
    @DisplayName("Dữ liệu chiêm tinh")
    class DuLieuChiemTinh {

        @Test
        @DisplayName("Đủ dữ liệu: mọi phần đều có mặt trong lời nhắc")
        void duDuLieu() {
            String nhac = service.buildPrompt(yeuCau(chiemTinhDayDu(), List.of(la("The Tower", (short) 0, false))));

            assertAll(
                    () -> assertTrue(nhac.contains("2000-05-17")),
                    () -> assertTrue(nhac.contains("07:30")),
                    () -> assertTrue(nhac.contains("Hà Nội, Việt Nam")),
                    () -> assertTrue(nhac.contains("Sun: Taurus")),
                    () -> assertTrue(nhac.contains("(Earth)")),
                    () -> assertTrue(nhac.contains("Fixed modality")),
                    () -> assertTrue(nhac.contains("Moon: Pisces")),
                    () -> assertTrue(nhac.contains("Rising: Leo")));
        }

        @Test
        @DisplayName("Thiếu giờ sinh, nơi sinh, cung Trăng: KHÔNG in chữ null")
        void thieuTruongKhongInNull() {
            AstrologyContextDTO thieu = AstrologyContextDTO.builder()
                    .birthDate(LocalDate.of(2000, 5, 17))
                    .sunSign("Taurus")
                    .build();

            String nhac = service.buildPrompt(yeuCau(thieu, List.of(la("The Tower", (short) 0, false))));

            // Mô hình đọc chữ "null" như một dữ kiện. Người chưa khai giờ sinh
            // sẽ nhận một lời giải nói về "Moon: null" — sai một cách đầy tự tin.
            assertAll(
                    () -> assertFalse(nhac.contains("null"), "lời nhắc chứa chữ null:\n" + nhac),
                    () -> assertFalse(nhac.contains("Moon:")),
                    () -> assertFalse(nhac.contains("Rising:")),
                    () -> assertTrue(nhac.contains("Sun: Taurus")));
        }

        @Test
        @DisplayName("Nơi sinh chỉ có dấu cách cũng coi như không có")
        void noiSinhChiCoDauCach() {
            AstrologyContextDTO thieu = AstrologyContextDTO.builder()
                    .birthDate(LocalDate.of(2000, 5, 17))
                    .birthPlace("   ")
                    .element("  ")
                    .modality("  ")
                    .sunSign("Taurus")
                    .build();

            String nhac = service.buildPrompt(yeuCau(thieu, List.of(la("A", (short) 0, false))));

            assertAll(
                    () -> assertFalse(nhac.contains(" in    ")),
                    () -> assertFalse(nhac.contains("(  )")),
                    () -> assertFalse(nhac.contains("modality")));
        }

        @Test
        @DisplayName("Vị trí hành tinh in kèm nhà và cờ nghịch hành")
        void viTriHanhTinh() {
            String nhac = service.buildPrompt(yeuCau(chiemTinhDayDu(), List.of(la("A", (short) 0, false))));

            assertAll(
                    () -> assertTrue(nhac.contains("Sun in Taurus at 26.5°")),
                    () -> assertTrue(nhac.contains("(House 10)")),
                    // Sao nghịch hành đổi hẳn cách đọc một vị trí; bỏ cờ này đi
                    // là đưa cho mô hình một dữ kiện ngược.
                    () -> assertTrue(nhac.contains("Mercury in Gemini at 3.2°")),
                    () -> assertTrue(nhac.contains("[Retrograde]")));
        }

        @Test
        @DisplayName("Hành tinh KHÔNG nghịch hành thì không gắn nhãn")
        void hanhTinhKhongNghichHanh() {
            AstrologyContextDTO ct = AstrologyContextDTO.builder()
                    .birthDate(LocalDate.of(2000, 5, 17)).sunSign("Taurus")
                    .natalPlanetPositions(List.of(
                            PlanetPositionDTO.builder().planetName("Venus").sign("Aries")
                                    .degree(new BigDecimal("1.0")).retrograde(false).build(),
                            PlanetPositionDTO.builder().planetName("Mars").sign("Leo")
                                    .degree(new BigDecimal("2.0")).build()))
                    .build();

            String nhac = service.buildPrompt(yeuCau(ct, List.of(la("A", (short) 0, false))));

            assertAll(
                    () -> assertFalse(nhac.contains("[Retrograde]")),
                    () -> assertFalse(nhac.contains("House"), "không khai nhà thì đừng in nhà"));
        }

        @Test
        @DisplayName("Góc chiếu bẩm sinh và góc chiếu hiện hành in khác nhau")
        void gocChieu() {
            String nhac = service.buildPrompt(yeuCau(chiemTinhDayDu(), List.of(la("A", (short) 0, false))));

            assertAll(
                    () -> assertTrue(nhac.contains("Natal Aspects:")),
                    () -> assertTrue(nhac.contains("Sun Trine Moon (120° exact, 2.1° orb)")),
                    () -> assertTrue(nhac.contains("Active Aspects:")),
                    () -> assertTrue(nhac.contains("Mars Square Venus (orb: 1.5°)")),
                    () -> assertTrue(nhac.contains("Sao Thuỷ nghịch hành")));
        }

        @Test
        @DisplayName("Danh sách rỗng thì không in tiêu đề mục rỗng")
        void danhSachRong() {
            AstrologyContextDTO ct = AstrologyContextDTO.builder()
                    .birthDate(LocalDate.of(2000, 5, 17)).sunSign("Taurus")
                    .natalPlanetPositions(List.of())
                    .natalAspects(List.of())
                    .transitAspects(List.of())
                    .currentTransit("   ")
                    .build();

            String nhac = service.buildPrompt(yeuCau(ct, List.of(la("A", (short) 0, false))));

            // Một tiêu đề "Natal Aspects:" không có dòng nào bên dưới là rác
            // chiếm chỗ trong ngân sách token.
            assertAll(
                    () -> assertFalse(nhac.contains("Planetary Positions:")),
                    () -> assertFalse(nhac.contains("Natal Aspects:")),
                    () -> assertFalse(nhac.contains("Active Aspects:")),
                    () -> assertFalse(nhac.contains("Current Influences:")));
        }

        @Test
        @DisplayName("Không có dữ liệu chiêm tinh thì bỏ hẳn mục, không ghi 'không có'")
        void khongCoDuLieuChiemTinh() {
            String nhac = service.buildPrompt(yeuCau(null, List.of(la("The Tower", (short) 0, false))));

            assertAll(
                    () -> assertFalse(nhac.contains("ASTROLOGY CONTEXT")),
                    () -> assertTrue(nhac.contains("TAROT CARDS")));
        }
    }

    // =====================================================================

    @Nested
    @DisplayName("Hai chế độ: có bài và không bài")
    class HaiCheDo {

        @Test
        @DisplayName("CÓ bài: chiêm tinh là bối cảnh PHỤ, và nói rõ không được lấn át")
        void coBai() {
            String nhac = service.buildPrompt(yeuCau(chiemTinhDayDu(),
                    List.of(la("The Tower", (short) 0, false))));

            assertAll(
                    () -> assertTrue(nhac.contains("Supporting Context")),
                    () -> assertTrue(nhac.contains("Astrology enriches, it does not override")),
                    () -> assertTrue(nhac.contains("người đọc tarot")),
                    () -> assertFalse(nhac.contains("Primary Source of Insight")));
        }

        @Test
        @DisplayName("KHÔNG bài: chiêm tinh là nguồn CHÍNH, và đổi hẳn vai người trả lời")
        void khongBai() {
            String nhac = service.buildPrompt(yeuCau(chiemTinhDayDu(), List.of()));

            // Lẫn hai chế độ là AI trả lời sai thể loại câu hỏi: người hỏi về
            // bản đồ sao lại nhận một bài giải Tarot không có lá bài nào.
            assertAll(
                    () -> assertTrue(nhac.contains("Primary Source of Insight")),
                    () -> assertTrue(nhac.contains("nhà chiêm tinh học")),
                    () -> assertFalse(nhac.contains("TAROT CARDS")),
                    () -> assertFalse(nhac.contains("Supporting Context")));
        }

        @Test
        @DisplayName("Danh sách bài null cũng là chế độ không bài")
        void danhSachBaiNull() {
            String nhac = service.buildPrompt(yeuCau(chiemTinhDayDu(), null));

            assertTrue(nhac.contains("nhà chiêm tinh học"));
        }

        @Test
        @DisplayName("Lá bài in theo đúng thứ tự vị trí dù truyền vào lộn xộn")
        void thuTuLaBai() {
            String nhac = service.buildPrompt(yeuCau(null, List.of(
                    la("The Star", (short) 2, false),
                    la("The Tower", (short) 0, true),
                    la("The Moon", (short) 1, false))));

            // Vị trí quyết định lá nào là quá khứ, hiện tại, tương lai. Đảo thứ
            // tự là gắn sai ý nghĩa cho từng lá.
            int tower = nhac.indexOf("The Tower");
            int moon = nhac.indexOf("The Moon");
            int star = nhac.indexOf("The Star");
            assertAll(
                    () -> assertTrue(tower < moon && moon < star, "thứ tự lá bài sai"),
                    // Đánh số cho người đọc bắt đầu từ 1, không phải 0.
                    () -> assertTrue(nhac.contains("Card 1: The Tower")),
                    () -> assertTrue(nhac.contains("Card 3: The Star")));
        }

        @Test
        @DisplayName("Chiều lá bài in rõ xuôi hay ngược")
        void chieuLaBai() {
            String nhac = service.buildPrompt(yeuCau(null, List.of(
                    la("The Tower", (short) 0, true),
                    la("The Star", (short) 1, false))));

            assertAll(
                    () -> assertTrue(nhac.contains("The Tower — Reversed")),
                    () -> assertTrue(nhac.contains("The Star — Upright")));
        }
    }

    // =====================================================================

    @Nested
    @DisplayName("Trò chuyện tiếp nối")
    class TroChuyenTiepNoi {

        @Test
        @DisplayName("Có lịch sử trò chuyện thì đưa vào lời nhắc")
        void coLichSu() {
            BuildPromptRequest r = BuildPromptRequest.builder()
                    .userQuestion("Còn về tiền bạc?")
                    .originalQuestion("Tôi nên đổi việc không?")
                    .conversationHistory("Người dùng: Tôi nên đổi việc không?\nAI: Ba lá bài nói rằng...")
                    .drawnCardDetails(List.of(la("The Tower", (short) 0, false)))
                    .build();

            String nhac = service.buildPrompt(r);

            // Không đưa lịch sử vào thì mỗi câu hỏi tiếp là một cuộc trò chuyện
            // mới, và AI trả lời như chưa từng nói gì.
            assertAll(
                    () -> assertTrue(nhac.contains("Ba lá bài nói rằng...")),
                    () -> assertTrue(nhac.contains("Tôi nên đổi việc không?")),
                    () -> assertTrue(nhac.contains("Còn về tiền bạc?")));
        }

        @Test
        @DisplayName("Lịch sử rỗng hoặc chỉ dấu cách thì bỏ qua mục đó")
        void lichSuRong() {
            BuildPromptRequest r = BuildPromptRequest.builder()
                    .userQuestion("Hỏi lần đầu")
                    .conversationHistory("   ")
                    .originalQuestion("  ")
                    .drawnCardDetails(List.of(la("A", (short) 0, false)))
                    .build();

            String nhac = service.buildPrompt(r);

            assertFalse(nhac.contains("null"));
        }

        @Test
        @DisplayName("Không khai tên trải bài thì dùng tên mặc định")
        void khongKhaiTenTraiBai() {
            BuildPromptRequest r = BuildPromptRequest.builder()
                    .userQuestion("hỏi")
                    .drawnCardDetails(List.of(la("A", (short) 0, false)))
                    .build();

            assertTrue(service.buildPrompt(r).contains("Spread: Tarot Reading"));
        }
    }
}
