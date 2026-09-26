package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.llm.LLMTokenUsage;
import com.exe.astratarot.domain.dto.llm.StreamCompletion;
import com.exe.astratarot.domain.dto.prompt.BuildPromptRequest;
import com.exe.astratarot.domain.entity.ChatMessage;
import com.exe.astratarot.domain.entity.ChatSession;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.ChatStatus;
import com.exe.astratarot.domain.enums.SenderType;
import com.exe.astratarot.repository.ChatMessageRepository;
import com.exe.astratarot.repository.ChatSessionRepository;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.service.AITarotService;
import com.exe.astratarot.service.AIUsageTrackingService;
import com.exe.astratarot.service.AstrologyContextService;
import com.exe.astratarot.service.ChatService;
import com.exe.astratarot.service.TokenEstimatorService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trò chuyện tiếp nối bằng AI, đường LUỒNG. Phần này chiếm phần lớn 88 dòng
 * chưa được kiểm của {@code ChatServiceImpl}.
 *
 * <p>Đường luồng khác đường thường ở một điểm quyết định: nó chạy <b>ngoài giao
 * dịch</b>. Giữ một giao dịch mở suốt một lượt gọi Gemini sẽ vét cạn pool kết
 * nối khi có vài người cùng hỏi. Hệ quả là mọi thứ phải tự lo lấy:
 *
 * <ol>
 *   <li><b>Mọi lỗi phải đi qua {@code onError}, không được ném ra ngoài.</b>
 *       Ném ra thì nó bay lên một SSE đã mở, và trình duyệt nhận một kết nối
 *       đứt thay vì một sự kiện lỗi — giao diện quay vòng mãi.
 *   <li><b>Tin nhắn của người dùng lưu NGAY</b>, trước khi gọi AI. AI lỗi thì
 *       câu hỏi vẫn còn đó; lưu sau là mất câu hỏi mỗi lần AI hỏng.
 *   <li><b>Ghi nhật ký dùng AI hỏng không được làm hỏng câu trả lời.</b> Người
 *       dùng đã đọc xong lời giải rồi; báo lỗi lúc này là vô nghĩa với họ.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceImplStreamTest {

    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private TarotReadingRepository tarotReadingRepository;
    @Mock private ReadingCardRepository readingCardRepository;
    @Mock private AITarotService aiTarotService;
    @Mock private AstrologyContextService astrologyContextService;
    @Mock private TokenEstimatorService tokenEstimatorService;
    @Mock private AIUsageTrackingService aiUsageTrackingService;

    private ChatServiceImpl service;

    private User khach;
    private TarotReading luotTrai;
    private ChatSession phien;

    @BeforeEach
    void setUp() {
        service = new ChatServiceImpl(chatSessionRepository, chatMessageRepository,
                tarotReadingRepository, readingCardRepository, aiTarotService,
                astrologyContextService, tokenEstimatorService, aiUsageTrackingService);
        ReflectionTestUtils.setField(service, "maxContextTokens", 8000);

        khach = User.builder().id(UUID.randomUUID()).build();
        luotTrai = TarotReading.builder()
                .id(UUID.randomUUID())
                .user(khach)
                .mainQuestion("Tôi nên đổi việc không?")
                .totalTokensUsed(100)
                .build();
        phien = ChatSession.builder()
                .id(UUID.randomUUID())
                .user(khach)
                .tarotReading(luotTrai)
                .status(ChatStatus.ACTIVE)
                .build();

        lenient().when(tokenEstimatorService.estimateTokens(anyString()))
                .thenAnswer(inv -> Math.max(1, inv.<String>getArgument(0).length() / 4));
        lenient().when(tarotReadingRepository.findById(luotTrai.getId()))
                .thenReturn(Optional.of(luotTrai));
        lenient().when(chatSessionRepository.findByTarotReadingId(luotTrai.getId()))
                .thenReturn(Optional.of(phien));
        lenient().when(astrologyContextService.getAstrologyContext(any(UUID.class)))
                .thenReturn(Optional.empty());
        lenient().when(readingCardRepository.findByReading(any(TarotReading.class)))
                .thenReturn(List.of());
        lenient().when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(
                        any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(inv -> {
                    ChatMessage m = inv.getArgument(0);
                    if (m.getId() == null) {
                        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
                    }
                    return m;
                });
        lenient().when(chatSessionRepository.save(any(ChatSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(tarotReadingRepository.save(any(TarotReading.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    /** Cho AI phát ra các mảnh rồi hoàn tất với số token cho trước. */
    private void aiPhatRa(List<String> manh, LLMTokenUsage token, String tenModel) {
        doAnswer(inv -> {
            Consumer<String> onChunk = inv.getArgument(1);
            Consumer<StreamCompletion> onComplete = inv.getArgument(3);
            manh.forEach(onChunk);
            onComplete.accept(StreamCompletion.builder()
                    .modelInfo(tenModel).tokenUsage(token).build());
            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(BuildPromptRequest.class), any(), any(), any());
    }

    private void aiLoi(Throwable loi) {
        doAnswer(inv -> {
            Consumer<Throwable> onError = inv.getArgument(2);
            onError.accept(loi);
            return null;
        }).when(aiTarotService).generateInterpretationStream(
                any(BuildPromptRequest.class), any(), any(), any());
    }

    private static LLMTokenUsage token(int p, int c, int t) {
        return LLMTokenUsage.builder().promptTokens(p).completionTokens(c).totalTokens(t).build();
    }

    // =====================================================================

    @Test
    @DisplayName("Luồng thành công: ghép mảnh, lưu câu trả lời, cộng dồn token")
    void luongThanhCong() {
        aiPhatRa(List.of("Ba ", "lá ", "bài."), token(50, 150, 200), "gemini-2.5-flash");

        List<String> manh = new ArrayList<>();
        AtomicReference<ChatService.StreamResult> xong = new AtomicReference<>();
        service.sendMessageStream(luotTrai.getId(), khach, "Còn về tiền bạc?",
                manh::add, e -> {}, xong::set);

        assertAll(
                () -> assertEquals(List.of("Ba ", "lá ", "bài."), manh),
                () -> assertEquals(phien.getId(), xong.get().sessionId()),
                () -> assertNotNull(xong.get().messageId()),
                () -> assertEquals("gemini-2.5-flash", xong.get().modelUsed()),
                () -> assertEquals(200, xong.get().totalTokens()),
                () -> assertEquals(50, xong.get().promptTokens()),
                () -> assertEquals(150, xong.get().completionTokens()),
                // 100 cũ + 200 mới. Ghi đè thay vì cộng là mất lịch sử chi phí
                // của cả lượt trải.
                () -> assertEquals(300, luotTrai.getTotalTokensUsed()));
    }

    @Test
    @DisplayName("Câu hỏi của người dùng lưu NGAY, trước khi gọi AI")
    void cauHoiLuuNgay() {
        aiLoi(new RuntimeException("Gemini quá tải"));

        service.sendMessageStream(luotTrai.getId(), khach, "Còn về tiền bạc?",
                s -> {}, e -> {}, c -> {});

        // AI lỗi thì câu hỏi vẫn còn đó. Lưu sau là mất câu hỏi mỗi lần AI hỏng
        // — và người dùng phải gõ lại.
        ArgumentCaptor<ChatMessage> bat = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(bat.capture());
        assertAll(
                () -> assertEquals(SenderType.USER, bat.getValue().getSenderType()),
                () -> assertEquals("Còn về tiền bạc?", bat.getValue().getContent()));
    }

    @Test
    @DisplayName("Câu trả lời AI lưu với đúng loại người gửi và nội dung đã ghép")
    void luuCauTraLoiAi() {
        aiPhatRa(List.of("Phần ", "một. ", "Phần hai."), token(10, 20, 30), "gemini");

        service.sendMessageStream(luotTrai.getId(), khach, "hỏi", s -> {}, e -> {}, c -> {});

        ArgumentCaptor<ChatMessage> bat = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2)).save(bat.capture());
        ChatMessage cuaAi = bat.getAllValues().get(1);
        // Lưu từng mảnh riêng là ba tin nhắn rời trong lịch sử chat.
        assertAll(
                () -> assertEquals(SenderType.AI, cuaAi.getSenderType()),
                () -> assertEquals("Phần một. Phần hai.", cuaAi.getContent()));
    }

    @Test
    @DisplayName("Mốc thời gian của phiên được cập nhật sau mỗi lượt")
    void capNhatMocThoiGian() {
        aiPhatRa(List.of("x"), token(1, 1, 2), "gemini");

        service.sendMessageStream(luotTrai.getId(), khach, "hỏi", s -> {}, e -> {}, c -> {});

        // Danh sách phiên chat sắp theo mốc này. Không cập nhật thì cuộc trò
        // chuyện đang diễn ra tụt xuống dưới cùng.
        assertNotNull(phien.getLastMessageAt());
    }

    @Test
    @DisplayName("AI KHÔNG trả token thì báo 0, không nổ vì null")
    void aiKhongTraToken() {
        aiPhatRa(List.of("x"), null, "gemini");

        AtomicReference<ChatService.StreamResult> xong = new AtomicReference<>();
        service.sendMessageStream(luotTrai.getId(), khach, "hỏi", s -> {}, e -> {}, xong::set);

        assertAll(
                () -> assertEquals(0, xong.get().totalTokens()),
                () -> assertEquals(0, xong.get().promptTokens()),
                () -> assertEquals(100, luotTrai.getTotalTokensUsed()));
    }

    @Test
    @DisplayName("Lượt trải chưa từng đếm token thì coi như 0, không nổ")
    void luotTraiChuaDemToken() {
        luotTrai.setTotalTokensUsed(null);
        aiPhatRa(List.of("x"), token(10, 20, 30), "gemini");

        service.sendMessageStream(luotTrai.getId(), khach, "hỏi", s -> {}, e -> {}, c -> {});

        assertEquals(30, luotTrai.getTotalTokensUsed());
    }

    @Test
    @DisplayName("Ghi nhật ký dùng AI hỏng KHÔNG làm hỏng câu trả lời")
    void nhatKyHongKhongLamHongCauTraLoi() {
        aiPhatRa(List.of("Nội dung"), token(10, 20, 30), "gemini");
        org.mockito.Mockito.doThrow(new RuntimeException("bảng usage đầy"))
                .when(aiUsageTrackingService).logChatContinuation(any(), any(), any(), any(), any(), any());

        AtomicReference<ChatService.StreamResult> xong = new AtomicReference<>();
        AtomicReference<Throwable> loi = new AtomicReference<>();
        service.sendMessageStream(luotTrai.getId(), khach, "hỏi", s -> {}, loi::set, xong::set);

        // Người dùng đã đọc xong lời giải rồi; báo lỗi lúc này là vô nghĩa với
        // họ, và còn xoá mất câu trả lời khỏi màn hình.
        assertAll(
                () -> assertNotNull(xong.get()),
                () -> assertNull(loi.get()));
    }

    @Test
    @DisplayName("Lượt trải KHÔNG phải của mình thì chặn qua onError, không ném ra ngoài")
    void luotTraiCuaNguoiKhac() {
        User nguoiLa = User.builder().id(UUID.randomUUID()).build();

        AtomicReference<Throwable> loi = new AtomicReference<>();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                service.sendMessageStream(luotTrai.getId(), nguoiLa, "hỏi", s -> {}, loi::set, c -> {}));

        // Ném ra thì nó bay lên một SSE đã mở, và trình duyệt nhận một kết nối
        // đứt thay vì một sự kiện lỗi.
        assertAll(
                () -> assertNotNull(loi.get()),
                () -> assertTrue(loi.get() instanceof IllegalArgumentException));
        verify(aiTarotService, never()).generateInterpretationStream(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lượt trải không tồn tại thì báo qua onError")
    void luotTraiKhongTonTai() {
        UUID la = UUID.randomUUID();
        when(tarotReadingRepository.findById(la)).thenReturn(Optional.empty());

        AtomicReference<Throwable> loi = new AtomicReference<>();
        service.sendMessageStream(la, khach, "hỏi", s -> {}, loi::set, c -> {});

        assertTrue(loi.get() instanceof EntityNotFoundException);
    }

    @Test
    @DisplayName("Chưa có phiên chat nào thì báo qua onError")
    void chuaCoPhienChat() {
        when(chatSessionRepository.findByTarotReadingId(luotTrai.getId()))
                .thenReturn(Optional.empty());

        AtomicReference<Throwable> loi = new AtomicReference<>();
        service.sendMessageStream(luotTrai.getId(), khach, "hỏi", s -> {}, loi::set, c -> {});

        assertTrue(loi.get() instanceof EntityNotFoundException);
    }

    @Test
    @DisplayName("Phiên ĐÃ ĐÓNG thì không nhắn thêm, và không lưu câu hỏi")
    void phienDaDong() {
        phien.setStatus(ChatStatus.CLOSED);

        AtomicReference<Throwable> loi = new AtomicReference<>();
        service.sendMessageStream(luotTrai.getId(), khach, "hỏi", s -> {}, loi::set, c -> {});

        assertAll(
                () -> assertTrue(loi.get() instanceof IllegalStateException),
                // Lưu câu hỏi vào một phiên đã đóng là để lại một tin nhắn
                // không ai trả lời được.
                () -> verify(chatMessageRepository, never()).save(any()));
    }

    @Test
    @DisplayName("Lỗi giữa luồng AI được chuyển thẳng qua onError")
    void loiGiuaLuongAi() {
        RuntimeException goc = new RuntimeException("Gemini quá tải");
        aiLoi(goc);

        AtomicReference<Throwable> loi = new AtomicReference<>();
        AtomicReference<ChatService.StreamResult> xong = new AtomicReference<>();
        service.sendMessageStream(luotTrai.getId(), khach, "hỏi", s -> {}, loi::set, xong::set);

        assertAll(
                () -> assertEquals(goc, loi.get()),
                () -> assertNull(xong.get()));
    }

    // =====================================================================
    // Đường chiêm tinh (không có lượt trải)
    // =====================================================================

    @Test
    @DisplayName("Không có lượt trải: vẫn hỏi được, nhưng KHÔNG lưu tin nhắn nào")
    void duongChiemTinh() {
        aiPhatRa(List.of("Sao Thuỷ ", "nghịch hành."), token(5, 15, 20), "gemini");

        List<String> manh = new ArrayList<>();
        AtomicReference<ChatService.StreamResult> xong = new AtomicReference<>();
        service.sendMessageStream(null, khach, "Hôm nay thế nào?", manh::add, e -> {}, xong::set);

        // Đây là lối hỏi nhanh, không gắn vào lượt trải nào, nên không có phiên
        // chat để lưu vào. Cố lưu là tạo một phiên mồ côi.
        assertAll(
                () -> assertEquals(List.of("Sao Thuỷ ", "nghịch hành."), manh),
                () -> assertNull(xong.get().sessionId()),
                () -> assertNull(xong.get().messageId()),
                () -> assertEquals(20, xong.get().totalTokens()));
        verify(chatMessageRepository, never()).save(any());
        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đường chiêm tinh không có token thì báo 0")
    void duongChiemTinhKhongCoToken() {
        aiPhatRa(List.of("x"), null, "gemini");

        AtomicReference<ChatService.StreamResult> xong = new AtomicReference<>();
        service.sendMessageStream(null, khach, "hỏi", s -> {}, e -> {}, xong::set);

        assertAll(
                () -> assertEquals(0, xong.get().totalTokens()),
                () -> assertEquals(0, xong.get().completionTokens()));
    }

    @Test
    @DisplayName("Đường chiêm tinh lỗi thì báo qua onError")
    void duongChiemTinhLoi() {
        aiLoi(new RuntimeException("mất mạng"));

        AtomicReference<Throwable> loi = new AtomicReference<>();
        service.sendMessageStream(null, khach, "hỏi", s -> {}, loi::set, c -> {});

        assertNotNull(loi.get());
    }
}
