package com.exe.astratarot.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ranh giới "thật / gieo sẵn" của màn Tổng quan.
 *
 * <p>Phần dễ sai ở đây không phải phép đếm mà là CÁCH nhận ra dữ liệu mẫu.
 * Cách đầu tiên nghĩ ra — loại tài khoản {@code @astrotarot.demo} — cho kết
 * quả sai, vì {@code V2_23} và {@code V2_25} gieo thanh toán, buổi hẹn và
 * đánh giá lên hai hộp thư Gmail có thật của nhóm.
 *
 * <p>Không dựng cơ sở dữ liệu ở đây được: {@code payment_transactions} có cột
 * {@code jsonb}, H2 không tạo nổi bảng ấy nên {@code @DataJpaTest} chết ngay ở
 * bước tạo lược đồ. Bù lại, bài kiểm dưới đây kiểm được thứ quan trọng hơn một
 * phép đếm: nó tự ĐỌC thư mục migration, nên lô seed nào gieo sai khuôn khoá
 * chính là đỏ ngay, kể cả lô viết sau này.
 */
@DisplayName("DuLieuThat — nhận ra dữ liệu gieo sẵn")
class DuLieuThatTest {

    private static final Path THU_MUC_MIGRATION =
            Path.of("src", "main", "resources", "db", "migration");

    /** Khoá chính viết tay trong tệp SQL, dạng `'xxxxxxxx-xxxx-...'`. */
    private static final Pattern UUID_TRONG_SQL = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    @Test
    @DisplayName("mọi khoá chính viết tay trong các tệp seed đều bị nhận ra")
    void moiKhoaSeed_deuBiNhanRa() throws IOException {
        List<String> saiKhuon = new ArrayList<>();
        int daKiem = 0;

        try (Stream<Path> tep = Files.list(THU_MUC_MIGRATION)) {
            for (Path p : tep.filter(f -> f.getFileName().toString().contains("seed")).toList()) {
                String noiDung = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = UUID_TRONG_SQL.matcher(noiDung);
                while (m.find()) {
                    daKiem++;
                    UUID id = UUID.fromString(m.group());
                    if (!DuLieuThat.laGieoSan(id)) {
                        saiKhuon.add(p.getFileName() + " → " + id);
                    }
                }
            }
        }

        // Đọc được tệp thật, không phải thư mục rỗng rồi tự khen mình đúng.
        assertThat(daKiem)
                .as("số khoá chính đọc được trong các tệp seed")
                .isGreaterThan(100);

        assertThat(saiKhuon)
                .as("""
                        Có lô seed gieo khoá chính không theo khuôn %s.
                        Những hàng ấy sẽ bị đếm là DỮ LIỆU THẬT ở màn Tổng quan \
                        mà không có gì báo lỗi — số vẫn hiện ra, chỉ là sai.
                        Sửa tệp seed cho đúng khuôn, đừng nới lỏng bài kiểm này.""",
                        DuLieuThat.KHUON_GIEO_SAN)
                .isEmpty();
    }

    @Test
    @DisplayName("khoá ngẫu nhiên của hàng thật không bao giờ bị nhận nhầm")
    void khoaNgauNhien_khongBiNhanNham() {
        // UUID v4 phải có đúng "0000" ở nhóm hai và "8000" ở nhóm bốn mới
        // trùng khuôn — cỡ một phần nghìn tỉ. Chạy một trăm nghìn lượt ở đây
        // không chứng minh được con số ấy, nhưng đủ bắt lỗi nếu khuôn bị nới
        // rộng thành thứ gì đó hay trùng (ví dụ chỉ còn "-4000-").
        for (int i = 0; i < 100_000; i++) {
            assertThat(DuLieuThat.laGieoSan(UUID.randomUUID())).isFalse();
        }
    }

    @Test
    @DisplayName("khuôn phải khớp cả hai nhóm, không chỉ phần phiên bản")
    void khuon_phaiKhopCaHaiNhom() {
        assertThat(DuLieuThat.laGieoSan(
                UUID.fromString("c2000000-0000-4000-8000-000000000012"))).isTrue();

        // Nhóm bốn là 9xxx chứ không phải 8000 — UUID v4 hợp lệ, hàng thật.
        assertThat(DuLieuThat.laGieoSan(
                UUID.fromString("c2000000-0000-4000-9000-000000000012"))).isFalse();
        // Nhóm hai không phải 0000.
        assertThat(DuLieuThat.laGieoSan(
                UUID.fromString("c2000000-1234-4000-8000-000000000012"))).isFalse();
    }

    @Test
    @DisplayName("điều kiện SQL và hàm Java dùng chung một khuôn")
    void dieuKienSql_dungChungKhuon() {
        // Hai thứ rời nhau là kiểu hỏng tệ nhất ở đây: trang quản trị đếm
        // theo SQL, bài kiểm xác nhận theo Java, và chúng khác nhau.
        assertThat(DuLieuThat.DIEU_KIEN).contains(DuLieuThat.KHUON_GIEO_SAN);
        assertThat(DuLieuThat.DIEU_KIEN).isEqualTo(
                "CAST(x.id AS String) NOT LIKE '%-0000-4000-8000-%'");
    }

    @Test
    @DisplayName("id rỗng không phải dữ liệu gieo sẵn")
    void idRong_khongPhaiSeed() {
        assertThat(DuLieuThat.laGieoSan(null)).isFalse();
    }
}
