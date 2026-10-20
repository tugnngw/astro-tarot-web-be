package com.exe.astratarot.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phép tính giảm giá.
 *
 * <p>Đây là chỗ sai thì mất tiền thật và không đòi lại được, nên những bài
 * kiểm dưới đây nhắm vào các giá trị biên chứ không phải đường chính.
 */
@DisplayName("KhuyenMai — tính tiền giảm")
class KhuyenMaiTest {

    private static KhuyenMai dot(KhuyenMai.LoaiGiam loai, long giaTri, Long tran) {
        return KhuyenMai.builder()
                .ten("Tết")
                .loaiGiam(loai)
                .giaTri(giaTri)
                .giamToiDa(tran)
                .batDau(Instant.now().minus(1, ChronoUnit.DAYS))
                .ketThuc(Instant.now().plus(1, ChronoUnit.DAYS))
                .dangBat(true)
                .build();
    }

    @Test
    @DisplayName("giảm theo phần trăm")
    void phanTram() {
        assertThat(dot(KhuyenMai.LoaiGiam.PHAN_TRAM, 20, null).tienGiam(239_000L))
                .isEqualTo(47_800L);
        assertThat(dot(KhuyenMai.LoaiGiam.PHAN_TRAM, 20, null).giaSauGiam(239_000L))
                .isEqualTo(191_200L);
    }

    @Test
    @DisplayName("trần giảm chặn đúng loại sai sót hay gặp nhất")
    void tranGiam() {
        // "Giảm 50%" trên gói 239.000 là 119.500 — con số người nhập không
        // nhìn thấy lúc gõ số 50. Đặt trần 50.000 thì chỉ bớt 50.000.
        KhuyenMai k = dot(KhuyenMai.LoaiGiam.PHAN_TRAM, 50, 50_000L);
        assertThat(k.tienGiam(239_000L)).isEqualTo(50_000L);
        assertThat(k.giaSauGiam(239_000L)).isEqualTo(189_000L);

        // Trần không biến giảm ít thành giảm nhiều.
        assertThat(k.tienGiam(20_000L)).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("giảm số tiền lớn hơn giá gói thì về 0, KHÔNG âm")
    void khongBaoGioAm() {
        // Số âm ở đây chảy thẳng vào lệnh trừ ví và thành cộng tiền cho
        // khách — lỗi mất tiền im lặng nhất của cả tính năng.
        KhuyenMai k = dot(KhuyenMai.LoaiGiam.SO_TIEN, 500_000L, null);
        assertThat(k.giaSauGiam(119_000L)).isZero();
        assertThat(k.tienGiam(119_000L)).isEqualTo(119_000L);
    }

    @Test
    @DisplayName("giảm 100% ra 0 đồng, không ra số âm")
    void giamToanBo() {
        assertThat(dot(KhuyenMai.LoaiGiam.PHAN_TRAM, 100, null).giaSauGiam(99_000L))
                .isZero();
    }

    @Test
    @DisplayName("giá 0 hoặc âm không sinh ra khoản giảm nào")
    void giaKhongHopLe() {
        KhuyenMai k = dot(KhuyenMai.LoaiGiam.PHAN_TRAM, 30, null);
        assertThat(k.tienGiam(0L)).isZero();
        assertThat(k.tienGiam(-5_000L)).isZero();
    }

    @Test
    @DisplayName("trần chỉ áp cho phần trăm, không áp cho giảm thẳng tiền")
    void tranKhongApChoSoTien() {
        // Quản trị nhập "giảm 50.000, trần 10.000" là mâu thuẫn. Tầng dịch vụ
        // xoá trần khi kiểu là SO_TIEN; ở đây xác nhận phép tính không tự ý
        // áp trần nếu trần lỡ còn sót lại trong dữ liệu cũ.
        KhuyenMai k = dot(KhuyenMai.LoaiGiam.SO_TIEN, 50_000L, 10_000L);
        assertThat(k.tienGiam(239_000L)).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("đang chạy: trong khoảng thì có, ngoài khoảng hoặc tắt thì không")
    void dangChay() {
        Instant bayGio = Instant.parse("2026-02-17T00:00:00Z");
        KhuyenMai k = KhuyenMai.builder()
                .ten("Tết")
                .loaiGiam(KhuyenMai.LoaiGiam.PHAN_TRAM)
                .giaTri(20L)
                .batDau(Instant.parse("2026-02-16T00:00:00Z"))
                .ketThuc(Instant.parse("2026-02-20T00:00:00Z"))
                .dangBat(true)
                .build();

        assertThat(k.dangChay(bayGio)).isTrue();
        assertThat(k.dangChay(Instant.parse("2026-02-15T23:59:59Z"))).isFalse();

        // Mốc kết thúc KHÔNG tính vào: đặt ketThuc = 00:00 ngày 20 nghĩa là
        // hết ngày 19, đúng như người nhập hiểu khi chọn khoảng ngày.
        assertThat(k.dangChay(Instant.parse("2026-02-20T00:00:00Z"))).isFalse();

        k.setDangBat(false);
        assertThat(k.dangChay(bayGio)).isFalse();
    }
}
