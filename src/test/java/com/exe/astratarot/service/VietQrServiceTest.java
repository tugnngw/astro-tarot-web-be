package com.exe.astratarot.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Không dùng Spring context: lớp này là hàm thuần, và một bài test chạy trong
 * mili giây thì người ta mới chịu chạy nó.
 */
class VietQrServiceTest {

    private final VietQrService service = new VietQrService();

    @Test
    @DisplayName("CRC khớp chuỗi mẫu của EMVCo")
    void crcKhopChuoiMau() {
        // Chuỗi mẫu trong đặc tả EMVCo QR, phần checksum đã biết trước là A13A.
        // Đây là chốt chặn quan trọng nhất của cả lớp: dùng nhầm biến thể CRC
        // (ví dụ CRC-16/ARC) vẫn sinh ra bốn chữ số trông hợp lệ, và triệu
        // chứng duy nhất là mọi ứng dụng ngân hàng đều từ chối quét.
        String mau = "00020101021229300012D156000000000510A93FO3230Q31280012D1560000000103081234"
                + "5678520441115802CN5914BEST TRANSPORT6007BEIJING64200002ZH0104最佳运输0202北京"
                + "540523.7253031565502016233030412340603***0708A60086670902ME91320016A0112233"
                + "44998877070812345678";
        assertThat(VietQrService.crc16(mau + "6304")).isEqualTo("A13A");
    }

    @Test
    @DisplayName("Mã có số tiền là mã một lần và chứa đúng BIN, số tài khoản, số tiền")
    void dungChuoiCoSoTien() {
        String qr = service.dungChuoi("970436", "0071000123456", 250_000L, "Rut tien ASTROTAROT");

        assertThat(qr).startsWith("000201");
        // 01 02 12 — mã một lần, vì đã ghim sẵn số tiền.
        assertThat(qr).contains("010212");
        // 00 06 970436 = BIN ngân hàng; 01 13 <số tài khoản> = tài khoản nhận.
        assertThat(qr).contains("0006970436");
        assertThat(qr).contains("01130071000123456");
        assertThat(qr).contains("QRIBFTTA");
        // 53 03 704 (VND) rồi 54 06 250000.
        assertThat(qr).contains("5303704");
        assertThat(qr).contains("5406250000");
        assertThat(qr).contains("5802VN");

        // CRC ở bốn ký tự cuối phải khớp với phần còn lại.
        String than = qr.substring(0, qr.length() - 4);
        assertThat(VietQrService.crc16(than)).isEqualTo(qr.substring(qr.length() - 4));
    }

    @Test
    @DisplayName("Không có số tiền thì là mã dùng nhiều lần, không có trường 54")
    void dungChuoiKhongSoTien() {
        String qr = service.dungChuoi("970422", "0011002233445", null, null);
        assertThat(qr).contains("010211");
        assertThat(qr).doesNotContain("5406");
    }

    @Test
    @DisplayName("Nội dung bị lược dấu vì liên ngân hàng chỉ nhận ASCII")
    void noiDungBiLuocDau() {
        String qr = service.dungChuoi("970436", "0071000123456", 1000L, "Rút tiền tháng 9 — Đạt");
        assertThat(qr).contains("Rut tien thang 9 Dat");
        assertThat(qr).doesNotContain("ú");
    }

    @Test
    @DisplayName("Thiếu ngân hàng hoặc số tài khoản thì từ chối, không dựng mã sai")
    void thieuThongTinThiTuChoi() {
        assertThatThrownBy(() -> service.dungChuoi(null, "0071000123456", 1000L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.dungChuoi("970436", "  ", 1000L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
