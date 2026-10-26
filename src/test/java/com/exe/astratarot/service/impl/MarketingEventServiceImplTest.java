package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.marketing.HanhTrinhKhach;
import com.exe.astratarot.domain.dto.marketing.HanhTrinhTheoNguon;
import com.exe.astratarot.repository.MarketingEventRepository;
import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Bảng "khách đến từ đâu, rồi làm gì".
 *
 * <p>Việc đáng kiểm ở đây là phép GỘP, không phải câu SQL: dịch vụ nhận về hai
 * tập kết quả rời nhau — một đếm lượt theo (nguồn, hoạt động), một đếm người
 * theo nguồn — rồi phải khớp chúng lại. Khớp sai thì bảng vẫn hiện ra đầy đủ,
 * chỉ là số người treo nhầm nguồn, và không có gì báo lỗi.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarketingEventServiceImpl — hành trình theo nguồn")
class MarketingEventServiceImplTest {

    @Mock
    private MarketingEventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MarketingEventServiceImpl service;

    @Test
    @DisplayName("gộp lượt theo nguồn và gắn đúng số người của từng nguồn")
    void gopTheoNguon() {
        when(eventRepository.countBySourceAndEventSince(any(Instant.class))).thenReturn(List.of(
                new Object[]{"facebook", "bat_dau_chat_ai", 12L},
                new Object[]{"facebook", "rut_bai_tarot", 7L},
                new Object[]{"facebook", "bam_dat_lich", 2L},
                new Object[]{"tiktok", "bat_dau_chat_ai", 5L}
        ));
        when(eventRepository.countUsersBySourceSince(any(Instant.class))).thenReturn(List.of(
                new Object[]{"facebook", 4L},
                new Object[]{"tiktok", 3L}
        ));

        List<HanhTrinhTheoNguon> ketQua = service.hanhTrinhTheoNguon30Ngay();

        assertThat(ketQua).hasSize(2);

        HanhTrinhTheoNguon fb = ketQua.get(0);
        assertThat(fb.nguon()).isEqualTo("facebook");
        assertThat(fb.tongLuot()).isEqualTo(21);
        // 21 lượt nhưng chỉ 4 người — đúng cái bẫy mà cột này sinh ra để tránh.
        assertThat(fb.soNguoiDangNhap()).isEqualTo(4);
        assertThat(fb.theoHoatDong())
                .containsEntry("bat_dau_chat_ai", 12L)
                .containsEntry("rut_bai_tarot", 7L)
                .containsEntry("bam_dat_lich", 2L);

        assertThat(ketQua.get(1).nguon()).isEqualTo("tiktok");
        assertThat(ketQua.get(1).soNguoiDangNhap()).isEqualTo(3);
    }

    @Test
    @DisplayName("nguồn mang về nhiều lượt nhất nằm trên cùng")
    void sapXepTheoLuotGiamDan() {
        when(eventRepository.countBySourceAndEventSince(any(Instant.class))).thenReturn(List.of(
                new Object[]{"zalo", "gui_gop_y", 3L},
                new Object[]{"tiktok", "bat_dau_chat_ai", 30L},
                new Object[]{"facebook", "bat_dau_chat_ai", 10L}
        ));
        when(eventRepository.countUsersBySourceSince(any(Instant.class))).thenReturn(List.of());

        assertThat(service.hanhTrinhTheoNguon30Ngay())
                .extracting(HanhTrinhTheoNguon::nguon)
                .containsExactly("tiktok", "facebook", "zalo");
    }

    @Test
    @DisplayName("nguồn rỗng gom thành một nhóm có tên, không để trống")
    void nguonRong_coTen() {
        // Hàng utm_source NULL là phần lớn lưu lượng khi chưa dán link UTM.
        // Để trống thì bảng hiện một dòng không nhãn, người đọc tưởng lỗi.
        when(eventRepository.countBySourceAndEventSince(any(Instant.class))).thenReturn(List.of(
                new Object[]{null, "bat_dau_chat_ai", 9L},
                new Object[]{"   ", "rut_bai_tarot", 1L}
        ));
        when(eventRepository.countUsersBySourceSince(any(Instant.class))).thenReturn(List.<Object[]>of(
                new Object[]{null, 6L}
        ));

        List<HanhTrinhTheoNguon> ketQua = service.hanhTrinhTheoNguon30Ngay();

        assertThat(ketQua).hasSize(1);
        assertThat(ketQua.get(0).nguon()).isEqualTo(MarketingEventServiceImpl.KHONG_RO_NGUON);
        assertThat(ketQua.get(0).tongLuot()).isEqualTo(10);
        assertThat(ketQua.get(0).soNguoiDangNhap()).isEqualTo(6);
    }

    @Test
    @DisplayName("nguồn chưa ai đăng nhập thì số người là 0, không phải lỗi")
    void nguonChuaAiDangNhap() {
        when(eventRepository.countBySourceAndEventSince(any(Instant.class))).thenReturn(List.<Object[]>of(
                new Object[]{"qr", "xem_trang_chu", 4L}
        ));
        when(eventRepository.countUsersBySourceSince(any(Instant.class))).thenReturn(List.of());

        assertThat(service.hanhTrinhTheoNguon30Ngay().get(0).soNguoiDangNhap()).isZero();
    }

    @Test
    @DisplayName("chưa có sự kiện nào thì trả danh sách rỗng")
    void chuaCoSuKien() {
        when(eventRepository.countBySourceAndEventSince(any(Instant.class))).thenReturn(List.of());
        when(eventRepository.countUsersBySourceSince(any(Instant.class))).thenReturn(List.of());

        assertThat(service.hanhTrinhTheoNguon30Ngay()).isEmpty();
    }

    // ================= hành trình từng khách =================

    /**
     * Một hàng như truy vấn {@code hanhDongCuaKhach} trả về.
     *
     * <p>Chín cột, đúng thứ tự trong câu {@code SELECT}. Viết hàm dựng hàng
     * thay vì rải {@code new Object[]{...}} khắp nơi là vì tầng dịch vụ đọc
     * các cột ấy BẰNG CHỈ SỐ: thêm một cột vào giữa câu truy vấn mà quên sửa
     * chỉ số thì không có gì báo lỗi lúc biên dịch, chỉ có bảng quản trị hiện
     * sai — hoặc ném {@code ArrayIndexOutOfBounds} ngay khi ai đó mở tab.
     */
    private static Object[] hang(String maKhach, String hanhDong, String duongDan,
                                 Instant luc, String nguon, String ten, String metadata) {
        return new Object[]{maKhach, hanhDong, duongDan, luc, nguon, null, null, ten, metadata};
    }

    @Test
    @DisplayName("dựng lại hành trình theo đúng thứ tự, kèm metadata của từng bước")
    void dungLaiHanhTrinh() {
        Instant t0 = Instant.parse("2026-10-22T05:00:00Z");
        when(eventRepository.maKhachGanDay(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of("abcdef0123456789"));
        when(eventRepository.hanhDongCuaKhach(anyList())).thenReturn(List.of(
                hang("abcdef0123456789", "xem_trang", "/", t0, "facebook", null, null),
                hang("abcdef0123456789", "cta_shopee_click", "/shop", t0.plusSeconds(60),
                        "facebook", null, "{\"slug\":\"bo-bai-rider-waite\"}")
        ));

        List<HanhTrinhKhach> ds = service.hanhTrinhGanDay(20);

        assertThat(ds).hasSize(1);
        HanhTrinhKhach k = ds.get(0);
        assertThat(k.maKhach()).isEqualTo("abcdef");
        assertThat(k.nguon()).isEqualTo("facebook");
        assertThat(k.soHanhDong()).isEqualTo(2);
        assertThat(k.cacBuoc()).extracting(HanhTrinhKhach.Buoc::hanhDong)
                .containsExactly("xem_trang", "cta_shopee_click");
        // Chính cột thứ 9 — thứ dễ gãy nhất khi câu truy vấn đổi.
        assertThat(k.cacBuoc().get(1).metadata()).contains("bo-bai-rider-waite");
        assertThat(k.cacBuoc().get(0).metadata()).isNull();
    }

    @Test
    @DisplayName("tên hiện là tên đầu tiên tìm được, vì khách đăng nhập giữa chừng")
    void tenLayOHangSau() {
        Instant t0 = Instant.parse("2026-10-22T05:00:00Z");
        when(eventRepository.maKhachGanDay(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of("zzz111"));
        when(eventRepository.hanhDongCuaKhach(anyList())).thenReturn(List.of(
                hang("zzz111", "xem_trang", "/", t0, null, null, null),
                hang("zzz111", "dang_ky_tai_khoan", "/", t0.plusSeconds(30), null, "Trần Duy Đạt", null)
        ));

        List<HanhTrinhKhach> ds = service.hanhTrinhGanDay(20);

        assertThat(ds.get(0).tenNguoiDung()).isEqualTo("Trần Duy Đạt");
        assertThat(ds.get(0).nguon()).isEqualTo(MarketingEventServiceImpl.KHONG_RO_NGUON);
    }
}
