package com.exe.astratarot.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Đăng ký webhook PayOS lúc khởi động. Lớp này trước đây phủ 3,8%.
 *
 * <p>Nó tồn tại vì một lỗi thật đáng nhớ: đăng ký webhook không phải một lời
 * gọi một chiều — PayOS nhận URL rồi <b>gọi ngược</b> lại chính URL đó để kiểm
 * tra. {@code @PostConstruct} chạy lúc dựng bean, cách lúc Tomcat mở cổng cả
 * phút, nên cú gọi ngược rơi vào <em>instance cũ</em> đang còn phục vụ. Hệ quả
 * là bản vá ở controller không bao giờ là bản trả lời cú gọi kiểm tra của chính
 * nó — sửa xong vẫn hỏng y như cũ.
 *
 * <p>Vậy nên logic ở đây không phải "gọi một lần rồi thôi" mà là một máy trạng
 * thái nhỏ có nhớ, và ba luật của nó đáng được chốt lại:
 *
 * <ol>
 *   <li>Thành công một lần thì <b>thôi hẳn</b> — đăng ký lại mỗi mười phút là
 *       gọi PayOS vô ích suốt vòng đời tiến trình.
 *   <li>Thất bại thì <b>thử lại, nhưng có trần</b>. URL sai thật thì thử mãi
 *       chỉ tổ rác log.
 *   <li>Chưa cấu hình PayOS thì <b>dừng ngay</b>, không đếm là một lần thử.
 * </ol>
 */
class PayOsWebhookRegistrarTest {

    private PayOsProperties properties;
    private PayOsWebhookRegistrar registrar;

    @BeforeEach
    void setUp() {
        properties = new PayOsProperties();
        properties.setClientId("client-gia");
        properties.setApiKey("khoa-gia");
        properties.setChecksumKey("checksum-gia");
        // URL không tồn tại: mọi lần gọi thật đều thất bại, đúng điều kiện cần
        // để kiểm nhánh thử lại mà không chạm mạng ngoài.
        properties.setWebhookUrl("https://khong-ton-tai.invalid/webhook");

        registrar = new PayOsWebhookRegistrar(properties);
    }

    private boolean xong() {
        return (boolean) ReflectionTestUtils.getField(registrar, "xong");
    }

    private int soLanDaThu() {
        return (int) ReflectionTestUtils.getField(registrar, "soLanDaThu");
    }

    // =====================================================================

    @Test
    @DisplayName("Chưa cấu hình PayOS thì dừng hẳn, KHÔNG tính là một lần thử")
    void chuaCauHinh() {
        properties.setClientId("  ");

        registrar.dangKy();

        // Dự án chạy được mà không bật PayOS (môi trường dev, môi trường kiểm
        // thử). Đếm đó là một lần thử thất bại thì log đầy cảnh báo vô nghĩa.
        assertAll(
                () -> assertTrue(xong()),
                () -> assertEquals(0, soLanDaThu()));
    }

    @Test
    @DisplayName("Thiếu URL webhook thì dừng hẳn và nhắc đăng ký tay")
    void thieuUrlWebhook() {
        properties.setWebhookUrl("   ");

        registrar.dangKy();

        assertAll(
                () -> assertTrue(xong()),
                () -> assertEquals(0, soLanDaThu()));
    }

    @Test
    @DisplayName("Gọi thất bại thì ĐẾM một lần thử và vẫn để ngỏ cho lần sau")
    void thatBaiThiThuLai() {
        registrar.dangKy();

        // Render cần chừng hai phút để chuyển luồng sang instance mới, nên lần
        // đầu hỏng là chuyện bình thường chứ không phải lỗi cấu hình.
        assertAll(
                () -> assertFalse(xong()),
                () -> assertEquals(1, soLanDaThu()));
    }

    @Test
    @DisplayName("Thử tối đa 6 lần rồi thôi — URL sai thật thì thử mãi chỉ tổ rác log")
    void thuToiDaSauLan() {
        for (int i = 0; i < 10; i++) {
            registrar.dangKy();
        }

        assertAll(
                () -> assertEquals(6, soLanDaThu()),
                () -> assertFalse(xong(), "hết lượt thử không phải là thành công"));
    }

    @Test
    @DisplayName("Đã thành công thì KHÔNG gọi lại nữa")
    void daThanhCongThiThoi() {
        ReflectionTestUtils.setField(registrar, "xong", true);

        registrar.dangKy();
        registrar.dangKy();

        // Lịch chạy mỗi mười phút suốt vòng đời tiến trình. Không có cờ này
        // thì mỗi instance gọi PayOS hàng trăm lần một ngày mà chẳng để làm gì.
        assertEquals(0, soLanDaThu());
    }

    @Test
    @DisplayName("Hết lượt thử rồi thì không gọi thêm dù lịch vẫn chạy")
    void hetLuotThiKhongGoiThem() {
        ReflectionTestUtils.setField(registrar, "soLanDaThu", 6);

        registrar.dangKy();

        assertEquals(6, soLanDaThu());
    }

    @Test
    @DisplayName("Cấu hình đầy đủ được nhận là đã cấu hình")
    void cauHinhDayDu() {
        assertTrue(properties.isConfigured());

        properties.setApiKey(null);
        assertFalse(properties.isConfigured());
    }
}
