package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.ai.BaoCaoNoiDungAiRequest;
import com.exe.astratarot.domain.entity.AiContentReport;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.AiContentReportRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Báo cáo nội dung AI.
 *
 * <p>Chính sách AI tạo sinh của CH Play buộc có chức năng này. Ba chỗ dễ hỏng
 * mà test ở đây giữ lại:
 *
 * <ol>
 *   <li><b>Dò id.</b> Cho báo lượt trải bài bất kỳ thì ai cũng thử được một
 *       dãy uuid và biết cái nào có thật.
 *   <li><b>Bấm nhầm hai lần.</b> Không được thành hai việc cho người xử lý, và
 *       cũng không được ném lỗi đỏ vào mặt người dùng.
 *   <li><b>Lý do tự chế.</b> Bảng có ràng buộc CHECK; lý do lạ lọt xuống là
 *       lỗi cơ sở dữ liệu chứ không phải lỗi 400 dễ hiểu.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaoCaoNoiDungAiServiceImplTest {

    @Mock private AiContentReportRepository aiContentReportRepository;
    @Mock private TarotReadingRepository tarotReadingRepository;

    private BaoCaoNoiDungAiServiceImpl service;
    private User nguoiBao;
    private TarotReading reading;

    @BeforeEach
    void setUp() {
        service = new BaoCaoNoiDungAiServiceImpl(
                aiContentReportRepository, tarotReadingRepository);

        nguoiBao = new User();
        nguoiBao.setId(UUID.randomUUID());

        reading = new TarotReading();
        reading.setId(UUID.randomUUID());
        reading.setUser(nguoiBao);
        reading.setMainQuestion("Mình có nên đổi việc không?");

        lenient().when(tarotReadingRepository.findById(reading.getId()))
                .thenReturn(Optional.of(reading));
        lenient().when(aiContentReportRepository
                        .existsByReporterUserIdAndReadingId(any(), any()))
                .thenReturn(false);
    }

    private BaoCaoNoiDungAiRequest yeuCau(String lyDo) {
        return new BaoCaoNoiDungAiRequest(reading.getId(), lyDo, "Lời giải nghe doạ nạt");
    }

    @Test
    @DisplayName("Ghi nhận báo cáo kèm câu hỏi gốc")
    void ghiNhan() {
        service.bao(nguoiBao, yeuCau("XUC_PHAM"));

        ArgumentCaptor<AiContentReport> bat = ArgumentCaptor.forClass(AiContentReport.class);
        verify(aiContentReportRepository).save(bat.capture());

        assertAll(
                () -> assertEquals("XUC_PHAM", bat.getValue().getLyDo()),
                () -> assertEquals(nguoiBao, bat.getValue().getReporterUser()),
                () -> assertEquals(reading, bat.getValue().getReading()),
                // Chụp lại câu hỏi ngay lúc báo: lời giải có thể bị sinh lại
                // trước khi có người xem, và lúc ấy báo cáo thành vô dụng.
                () -> assertEquals("Mình có nên đổi việc không?",
                        bat.getValue().getNoiDungBiBao()),
                () -> assertEquals(AiContentReport.CHO_XU_LY,
                        bat.getValue().getTrangThai()));
    }

    @Test
    @DisplayName("Không báo được lượt trải bài của người khác")
    void khongBaoDuocCuaNguoiKhac() {
        User nguoiKhac = new User();
        nguoiKhac.setId(UUID.randomUUID());
        reading.setUser(nguoiKhac);

        assertThrows(AccessDeniedException.class,
                () -> service.bao(nguoiBao, yeuCau("SAI_LECH")));
        verify(aiContentReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Báo lại lần hai thì im lặng bỏ qua, không ném lỗi")
    void baoLaiLanHai() {
        when(aiContentReportRepository
                .existsByReporterUserIdAndReadingId(nguoiBao.getId(), reading.getId()))
                .thenReturn(true);

        // Với người dùng thì bấm lần hai cũng là "đã báo rồi". Ném lỗi đỏ ở
        // đây chỉ gây hoang mang mà chẳng sửa được gì.
        service.bao(nguoiBao, yeuCau("KHAC"));

        verify(aiContentReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lý do lạ bị chặn trước khi chạm cơ sở dữ liệu")
    void lyDoLa() {
        var loi = assertThrows(IllegalArgumentException.class,
                () -> service.bao(nguoiBao, yeuCau("TU_CHE")));

        assertEquals("Lý do không hợp lệ", loi.getMessage());
        // Chặn ở đây chứ để lọt xuống thì bảng ném lỗi ràng buộc CHECK, và
        // người dùng nhận một lỗi 500 thay vì một câu nói rõ.
        verify(tarotReadingRepository, never()).findById(any());
        verify(aiContentReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lượt trải bài không tồn tại thì báo không tìm thấy")
    void khongCoLuotTraiBai() {
        when(tarotReadingRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.bao(nguoiBao, yeuCau("NGUY_HIEM")));
    }

    @Test
    @DisplayName("Đếm đúng số báo cáo còn chờ xử lý")
    void demChoXuLy() {
        when(aiContentReportRepository.countByTrangThai(AiContentReport.CHO_XU_LY))
                .thenReturn(4L);

        assertEquals(4L, service.demChoXuLy());
    }
}
