package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.khuyenmai.GiaSauGiam;
import com.exe.astratarot.domain.dto.khuyenmai.KhuyenMaiResponse;
import com.exe.astratarot.domain.entity.KhuyenMai;
import com.exe.astratarot.repository.KhuyenMaiDaChucRepository;
import com.exe.astratarot.repository.KhuyenMaiRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Chọn đợt khuyến mãi, và lời chúc chỉ gửi một lần.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KhuyenMaiServiceImpl")
class KhuyenMaiServiceImplTest {

    @Mock
    private KhuyenMaiRepository khuyenMaiRepository;

    @Mock
    private KhuyenMaiDaChucRepository daChucRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private KhuyenMaiServiceImpl service;

    private static KhuyenMai dot(String ten, KhuyenMai.LoaiGiam loai, long giaTri, String loiChuc) {
        return KhuyenMai.builder()
                .id(UUID.randomUUID())
                .ten(ten)
                .loiChuc(loiChuc)
                .loaiGiam(loai)
                .giaTri(giaTri)
                .batDau(Instant.now().minus(1, ChronoUnit.DAYS))
                .ketThuc(Instant.now().plus(1, ChronoUnit.DAYS))
                .dangBat(true)
                .build();
    }

    @Test
    @DisplayName("hai đợt chồng lịch: chọn đợt bớt được NHIỀU TIỀN nhất")
    void chonDotLoiNhat() {
        // Quản trị hoàn toàn có thể nhập hai đợt sát nhau (Tết dương và Tết
        // âm). "Giảm 30%" với "giảm 50.000" không so trực tiếp được — cái nào
        // lợi hơn còn tuỳ giá gói, nên phải so bằng số tiền thật.
        when(khuyenMaiRepository.dangChay(any(Instant.class))).thenReturn(List.of(
                dot("Tết dương", KhuyenMai.LoaiGiam.SO_TIEN, 50_000L, null),
                dot("Tết âm", KhuyenMai.LoaiGiam.PHAN_TRAM, 30L, null)
        ));

        // Gói 239.000: 30% = 71.700 > 50.000 → chọn phần trăm.
        assertThat(service.apDung(239_000L).tenDot()).isEqualTo("Tết âm");
        // Gói 99.000: 30% = 29.700 < 50.000 → chọn số tiền.
        assertThat(service.apDung(99_000L).tenDot()).isEqualTo("Tết dương");
    }

    @Test
    @DisplayName("không có đợt nào thì trả nguyên giá, không trả null")
    void khongCoDot() {
        when(khuyenMaiRepository.dangChay(any(Instant.class))).thenReturn(List.of());

        GiaSauGiam g = service.apDung(119_000L);
        assertThat(g.giaGoc()).isEqualTo(119_000L);
        assertThat(g.giaCuoi()).isEqualTo(119_000L);
        assertThat(g.tienGiam()).isZero();
        assertThat(g.tenDot()).isNull();
    }

    @Test
    @DisplayName("giá 0 không gọi tới cơ sở dữ liệu")
    void giaKhong() {
        assertThat(service.apDung(0L).giaCuoi()).isZero();
        verifyNoInteractions(khuyenMaiRepository);
    }

    @Test
    @DisplayName("lời chúc gửi đúng một lần, lần sau rỗng")
    void loiChucMotLan() {
        UUID ai = UUID.randomUUID();
        KhuyenMai k = dot("Tết", KhuyenMai.LoaiGiam.PHAN_TRAM, 20L, "Chúc mừng năm mới!");
        when(khuyenMaiRepository.dangChay(any(Instant.class))).thenReturn(List.of(k));
        when(daChucRepository.existsByKhuyenMaiIdAndUserId(k.getId(), ai))
                .thenReturn(false, true);

        Optional<KhuyenMaiResponse> lan1 = service.loiChucChuaXem(ai);
        assertThat(lan1).isPresent();
        assertThat(lan1.get().loiChuc()).isEqualTo("Chúc mừng năm mới!");

        assertThat(service.loiChucChuaXem(ai)).isEmpty();
    }

    @Test
    @DisplayName("hai thiết bị gọi cùng lúc: bên thua coi như đã xem")
    void haiThietBiCungLuc() {
        // Cả hai cùng đọc thấy "chưa xem" rồi cùng ghi. Khoá chính ghép chỉ
        // cho một hàng vào; bên còn lại phải im lặng bỏ qua, không được ném
        // lỗi ra màn đăng nhập.
        UUID ai = UUID.randomUUID();
        KhuyenMai k = dot("Tết", KhuyenMai.LoaiGiam.PHAN_TRAM, 20L, "Chúc mừng năm mới!");
        when(khuyenMaiRepository.dangChay(any(Instant.class))).thenReturn(List.of(k));
        when(daChucRepository.existsByKhuyenMaiIdAndUserId(k.getId(), ai)).thenReturn(false);
        when(daChucRepository.save(any())).thenThrow(new DataIntegrityViolationException("trùng"));

        assertThat(service.loiChucChuaXem(ai)).isEmpty();
    }

    @Test
    @DisplayName("đợt không có lời chúc thì bỏ qua, không hiện hộp rỗng")
    void dotKhongCoLoiChuc() {
        UUID ai = UUID.randomUUID();
        when(khuyenMaiRepository.dangChay(any(Instant.class))).thenReturn(List.of(
                dot("Giảm giá hè", KhuyenMai.LoaiGiam.PHAN_TRAM, 10L, null),
                dot("Chỉ khoảng trắng", KhuyenMai.LoaiGiam.PHAN_TRAM, 10L, "   ")
        ));

        assertThat(service.loiChucChuaXem(ai)).isEmpty();
        verify(daChucRepository, never()).save(any());
    }

    @Test
    @DisplayName("khách chưa đăng nhập không có lời chúc")
    void khachVangLai() {
        assertThat(service.loiChucChuaXem(null)).isEmpty();
        verifyNoInteractions(khuyenMaiRepository);
    }

    @Test
    @DisplayName("phát realtime hỏng không làm hỏng việc đã lưu")
    void phatHongKhongLamHongViecChinh() {
        // Đợt đã lưu vào cơ sở dữ liệu rồi. Để ngoại lệ của STOMP nổi lên là
        // transaction bị cuốn ngược, và quản trị thấy báo lỗi dù dữ liệu lẽ
        // ra đã đúng.
        when(khuyenMaiRepository.findById(any())).thenReturn(Optional.of(
                dot("Tết", KhuyenMai.LoaiGiam.PHAN_TRAM, 20L, null)));
        when(khuyenMaiRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("broker chết"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        assertThat(service.sua(UUID.randomUUID(), new com.exe.astratarot.domain.dto.khuyenmai
                .LuuKhuyenMaiRequest(
                "Tết", null, "PHAN_TRAM", 20L, null,
                Instant.now(), Instant.now().plus(2, ChronoUnit.DAYS), true)))
                .isNotNull();
    }
}
