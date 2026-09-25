package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.SessionType;
import com.exe.astratarot.repository.ChatMessageRepository;
import com.exe.astratarot.repository.ChatSessionRepository;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.AIUsageTrackingService;
import com.exe.astratarot.service.AstrologyContextService;
import com.exe.astratarot.service.TarotDrawingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Lịch sử trải bài. Phương thức này trước đây chưa được kiểm dòng nào.
 *
 * <p>Nhỏ nhưng là màn hình người dùng mở lại nhiều nhất sau khi trải bài xong,
 * và có một cái bẫy null: {@code sessionType} có thể trống ở những bản ghi cũ,
 * và {@code .name()} trên null là một NullPointerException làm trắng cả trang
 * lịch sử — không phải một dòng hỏng, mà cả trang.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TarotReadingServiceImplHistoryTest {

    @Mock private UserRepository userRepository;
    @Mock private TarotCardRepository tarotCardRepository;
    @Mock private TarotReadingRepository tarotReadingRepository;
    @Mock private ReadingCardRepository readingCardRepository;
    @Mock private TarotDrawingService tarotDrawingService;
    @Mock private AITarotService aiTarotService;
    @Mock private AstrologyContextService astrologyContextService;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private AIUsageTrackingService aiUsageTrackingService;

    private TarotReadingServiceImpl service;

    private User khach;

    @BeforeEach
    void setUp() {
        service = new TarotReadingServiceImpl(userRepository, tarotCardRepository,
                tarotReadingRepository, readingCardRepository, tarotDrawingService,
                aiTarotService, astrologyContextService, chatSessionRepository,
                chatMessageRepository, aiUsageTrackingService);

        khach = User.builder().id(UUID.randomUUID()).build();
    }

    private TarotReading luot(String cauHoi, SessionType loai, String model) {
        return TarotReading.builder()
                .id(UUID.randomUUID())
                .user(khach)
                .mainQuestion(cauHoi)
                .sessionType(loai)
                .aiModelUsed(model)
                .build();
    }

    // =====================================================================

    @Test
    @DisplayName("Lịch sử mang đủ câu hỏi, loại phiên và model đã dùng")
    void lichSuDuTruong() {
        TarotReading r = luot("Tôi nên đổi việc không?", SessionType.AI, "gemini-2.5-flash");
        when(tarotReadingRepository.findByUserIdOrderByCreatedAtDesc(eq(khach.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(r)));

        var muc = service.listHistory(khach.getId(), PageRequest.of(0, 10)).getContent().get(0);

        // Câu hỏi là thứ duy nhất giúp người dùng nhận ra lượt trải nào là lượt
        // nào; thiếu nó thì lịch sử chỉ là một danh sách ngày tháng.
        assertAll(
                () -> assertEquals(r.getId(), muc.id()),
                () -> assertEquals("Tôi nên đổi việc không?", muc.mainQuestion()),
                () -> assertEquals("AI", muc.sessionType()),
                () -> assertEquals("gemini-2.5-flash", muc.aiModelUsed()));
    }

    @Test
    @DisplayName("Bản ghi cũ KHÔNG có loại phiên thì để trống, không nổ")
    void khongCoLoaiPhien() {
        when(tarotReadingRepository.findByUserIdOrderByCreatedAtDesc(eq(khach.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(luot("Câu hỏi", null, null))));

        // .name() trên null là NullPointerException làm TRẮNG cả trang lịch sử
        // — không phải một dòng hỏng, mà cả trang.
        var muc = service.listHistory(khach.getId(), PageRequest.of(0, 10)).getContent().get(0);
        assertAll(
                () -> assertNull(muc.sessionType()),
                () -> assertNull(muc.aiModelUsed()),
                () -> assertEquals("Câu hỏi", muc.mainQuestion()));
    }

    @Test
    @DisplayName("Chưa trải lần nào thì trang rỗng, không null")
    void chuaTraiLanNao() {
        when(tarotReadingRepository.findByUserIdOrderByCreatedAtDesc(eq(khach.getId()), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertTrue(service.listHistory(khach.getId(), PageRequest.of(0, 10)).isEmpty());
    }

    @Test
    @DisplayName("Phân trang giữ nguyên tổng số bản ghi thật")
    void phanTrang() {
        when(tarotReadingRepository.findByUserIdOrderByCreatedAtDesc(eq(khach.getId()), any()))
                .thenReturn(new PageImpl<>(
                        List.of(luot("A", SessionType.AI, "m")),
                        PageRequest.of(0, 10), 42));

        var trang = service.listHistory(khach.getId(), PageRequest.of(0, 10));

        // Tổng số quyết định có hiện nút "trang sau" hay không; sai là người
        // dùng không xem được quá mười lượt đầu.
        assertAll(
                () -> assertEquals(42L, trang.getTotalElements()),
                () -> assertEquals(5, trang.getTotalPages()));
    }

    @Test
    @DisplayName("Lịch sử chỉ của CHÍNH người đang đăng nhập")
    void chiCuaChinhMinh() {
        UUID nguoiLa = UUID.randomUUID();
        when(tarotReadingRepository.findByUserIdOrderByCreatedAtDesc(eq(nguoiLa), any()))
                .thenReturn(new PageImpl<>(List.of()));

        // Câu hỏi người ta gửi cho AI là chuyện rất riêng tư. Truy vấn đã ghép
        // userId, nên hàng rào nằm ở chính câu truy vấn.
        assertTrue(service.listHistory(nguoiLa, PageRequest.of(0, 10)).isEmpty());
        org.mockito.Mockito.verify(tarotReadingRepository)
                .findByUserIdOrderByCreatedAtDesc(eq(nguoiLa), any());
    }
}
