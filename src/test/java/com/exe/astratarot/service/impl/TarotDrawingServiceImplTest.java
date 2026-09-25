package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.reader.CardDrawDTO;
import com.exe.astratarot.domain.dto.reader.DrawCardsRequest;
import com.exe.astratarot.domain.entity.ReadingCard;
import com.exe.astratarot.domain.entity.TarotCard;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.repository.ReadingCardRepository;
import com.exe.astratarot.repository.TarotCardRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rút bài. Lớp này trước đây phủ 0,0%.
 *
 * <p>Bất biến duy nhất mà cả tính năng dựa vào: <b>không rút trùng lá</b>. Một
 * lượt trải ba lá mà ra hai lá Thần Tháp thì lời giải thành vô nghĩa, và người
 * dùng nhìn thấy ngay — đây là kiểu lỗi làm mất niềm tin vào toàn bộ sản phẩm
 * chứ không chỉ một màn hình.
 *
 * <p>Vì phép rút dùng số ngẫu nhiên, một lần chạy đúng không chứng minh được
 * gì. Nên phép kiểm "không trùng" chạy lặp lại nhiều lần với bộ bài nhỏ, nơi
 * xác suất đụng nhau là cao nhất.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TarotDrawingServiceImplTest {

    @Mock private TarotCardRepository tarotCardRepository;
    @Mock private ReadingCardRepository readingCardRepository;
    @Mock private TarotReadingRepository tarotReadingRepository;

    private TarotDrawingServiceImpl service;

    private TarotReading luotTrai;
    private List<TarotCard> boBai;

    @BeforeEach
    void setUp() {
        service = new TarotDrawingServiceImpl(tarotCardRepository, readingCardRepository,
                tarotReadingRepository);

        boBai = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            TarotCard c = TarotCard.builder()
                    .id(UUID.randomUUID())
                    .name("Lá " + i)
                    .arcanaType("MAJOR")
                    .cardNumber(i)
                    .build();
            boBai.add(c);
            lenient().when(tarotCardRepository.findById(c.getId())).thenReturn(Optional.of(c));
        }

        luotTrai = TarotReading.builder()
                .id(UUID.randomUUID())
                .mainQuestion("Tôi nên đổi việc không?")
                .build();

        lenient().when(tarotCardRepository.findAll()).thenReturn(boBai);
        lenient().when(tarotReadingRepository.findById(luotTrai.getId()))
                .thenReturn(Optional.of(luotTrai));
    }

    private DrawCardsRequest yeuCau(int soLa, boolean coNguoc) {
        DrawCardsRequest r = new DrawCardsRequest();
        r.setReadingId(luotTrai.getId());
        r.setNumberOfCards(soLa);
        r.setIncludeReversed(coNguoc);
        return r;
    }

    // =====================================================================

    @RepeatedTest(value = 30, name = "Không rút trùng lá (lần {currentRepetition}/{totalRepetitions})")
    @DisplayName("Không rút trùng lá")
    void khongRutTrungLa() {
        // Bộ bài chỉ 5 lá mà rút 5 — xác suất đụng nhau cao nhất có thể. Chạy
        // một lần rồi kết luận là tự lừa mình: phép rút dùng số ngẫu nhiên nên
        // một lần đúng không chứng minh gì.
        when(tarotCardRepository.findAll()).thenReturn(boBai.subList(0, 5));

        List<CardDrawDTO> la = service.drawCards(luotTrai.getId(), 5, true);

        Set<UUID> rieng = new HashSet<>();
        la.forEach(c -> rieng.add(c.getCardId()));
        assertAll(
                () -> assertEquals(5, la.size()),
                () -> assertEquals(5, rieng.size(), "có lá bị rút trùng: " + la));
    }

    @Test
    @DisplayName("Vị trí đánh số liên tục từ 0")
    void viTriLienTuc() {
        List<CardDrawDTO> la = service.drawCards(luotTrai.getId(), 3, false);

        // Vị trí quyết định lá nào là "quá khứ", "hiện tại", "tương lai". Nhảy
        // số hay lặp số là lời giải gắn sai chỗ.
        assertAll(
                () -> assertEquals((short) 0, la.get(0).getPosition()),
                () -> assertEquals((short) 1, la.get(1).getPosition()),
                () -> assertEquals((short) 2, la.get(2).getPosition()));
    }

    @Test
    @DisplayName("Không bật lá ngược thì MỌI lá đều xuôi")
    void khongBatLaNguoc() {
        List<CardDrawDTO> la = service.drawCards(luotTrai.getId(), 10, false);

        // Người dùng tắt lá ngược là vì họ không muốn đọc nghĩa ngược. Lọt một
        // lá ngược vào là đưa cho họ đúng thứ họ vừa từ chối.
        la.forEach(c -> assertFalse(c.getReversed(), "lá " + c.getPosition() + " không được ngược"));
    }

    @RepeatedTest(value = 5, name = "Bật lá ngược thì có cả hai chiều (lần {currentRepetition})")
    @DisplayName("Bật lá ngược thì thực sự có lá ngược")
    void batLaNguoc() {
        // Rút 22 lá với xác suất ngược 50/50: khả năng ra toàn xuôi là 1/2^22,
        // nhỏ tới mức nếu xảy ra thì đó là lỗi chứ không phải xui.
        List<CardDrawDTO> la = service.drawCards(luotTrai.getId(), 22, true);

        assertTrue(la.stream().anyMatch(CardDrawDTO::getReversed),
                "bật lá ngược mà 22 lá đều xuôi thì cờ includeReversed không có tác dụng");
    }

    @Test
    @DisplayName("Rút nhiều hơn số lá trong bộ thì báo lỗi, không lặp vô tận")
    void rutNhieuHonSoLa() {
        when(tarotCardRepository.findAll()).thenReturn(boBai.subList(0, 3));

        // Không chặn thì vòng while chạy mãi vì không bao giờ đủ lá riêng — máy
        // chủ treo một luồng cho tới khi hết thời gian chờ.
        assertThrows(IllegalArgumentException.class,
                () -> service.drawCards(luotTrai.getId(), 5, false));
    }

    @Test
    @DisplayName("Rút đúng bằng số lá trong bộ thì vẫn được")
    void rutDungBangSoLa() {
        when(tarotCardRepository.findAll()).thenReturn(boBai.subList(0, 3));

        assertEquals(3, service.drawCards(luotTrai.getId(), 3, false).size());
    }

    @Test
    @DisplayName("Rút cho một lượt trải: lưu các lá vào đúng lượt đó")
    void rutChoLuotTrai() {
        var kq = service.drawCardsForReading(yeuCau(3, true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReadingCard>> bat = ArgumentCaptor.forClass(List.class);
        verify(readingCardRepository).saveAll(bat.capture());
        assertAll(
                () -> assertEquals(luotTrai.getId(), kq.getReadingId()),
                () -> assertEquals(3, kq.getDrawnCards().size()),
                () -> assertEquals(3, bat.getValue().size()),
                () -> bat.getValue().forEach(rc ->
                        assertEquals(luotTrai, rc.getReading())),
                // Chiều lá phải đi theo xuống bản ghi, không thì lời giải đọc
                // nghĩa xuôi cho một lá hiện ngược trên màn hình.
                () -> assertEquals(
                        kq.getDrawnCards().get(0).getReversed(),
                        bat.getValue().get(0).getReversed()));
    }

    @Test
    @DisplayName("Lượt trải không tồn tại thì báo lỗi, không rút gì")
    void luotTraiKhongTonTai() {
        DrawCardsRequest r = new DrawCardsRequest();
        r.setReadingId(UUID.randomUUID());
        r.setNumberOfCards(3);
        when(tarotReadingRepository.findById(r.getReadingId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.drawCardsForReading(r));
        verify(readingCardRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Lá đã rút mà không còn trong bảng thì báo lỗi rõ")
    void laKhongConTrongBang() {
        // Xảy ra khi có người xoá một lá khỏi bộ bài giữa lúc đang rút.
        when(tarotCardRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.drawCardsForReading(yeuCau(1, false)));
    }
}
