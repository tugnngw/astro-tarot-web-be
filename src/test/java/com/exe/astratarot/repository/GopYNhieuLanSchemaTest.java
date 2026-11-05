package com.exe.astratarot.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Luật "một tài khoản góp ý được nhiều lần" phải đúng ở CẢ hai tầng.
 *
 * <h2>Chuyện đã xảy ra</h2>
 *
 * Tầng ứng dụng đổi sang giữ từng lần gửi thành một bản ghi riêng, nhưng
 * {@code V2_34} vẫn còn một partial unique index trên {@code user_feedback
 * (user_id)} từ thời luật cũ. Kết quả: mọi lần gửi thứ hai của một tài khoản
 * đổ với lỗi trùng khoá, và người dùng chỉ thấy "An unexpected error
 * occurred".
 *
 * <h2>Vì sao 1138 bài kiểm xanh mà vẫn lọt</h2>
 *
 * {@code application-test.properties} đặt {@code spring.flyway.enabled=false}
 * và để Hibernate dựng bảng từ entity. Entity không khai ràng buộc ấy, nên
 * trong bài kiểm nó không tồn tại — lỗi chỉ có thật trên Postgres của
 * production. Không bài nào trong dự án đọc tới thư mục {@code db/migration}
 * để đối chiếu.
 *
 * <h2>Bài này canh gì</h2>
 *
 * Đọc tĩnh các tệp di trú theo đúng thứ tự Flyway chạy, rồi xem thao tác CUỐI
 * CÙNG trên index duy nhất ấy là tạo hay xoá. Không dựng CSDL, không chạy SQL
 * — chỉ cần biết trạng thái sau cùng, và đó đúng là thứ đã sai.
 */
class GopYNhieuLanSchemaTest {

    private static final Path THU_MUC = Path.of("src/main/resources/db/migration");

    /** Khớp cả "CREATE UNIQUE INDEX [IF NOT EXISTS] uq_user_feedback_user_id". */
    private static final Pattern TAO = Pattern.compile(
            "CREATE\\s+UNIQUE\\s+INDEX(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+uq_user_feedback_user_id",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern XOA = Pattern.compile(
            "DROP\\s+INDEX(?:\\s+IF\\s+EXISTS)?\\s+uq_user_feedback_user_id",
            Pattern.CASE_INSENSITIVE);

    /**
     * Thứ tự Flyway: so theo số hiệu phiên bản, không so theo tên tệp.
     *
     * <p>So theo tên thì "V2_9" đứng sau "V2_34" vì '9' > '3', và bài kiểm sẽ
     * đọc ra một trạng thái sau cùng không có thật.
     */
    private static List<Path> theoThuTuChay() throws IOException {
        try (Stream<Path> s = Files.list(THU_MUC)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted((a, b) -> soSanh(soHieu(a), soHieu(b)))
                    .toList();
        }
    }

    /** "V2_34__abc.sql" → [2, 34]; dùng để so thứ tự. */
    private static List<Integer> soHieu(Path p) {
        String ten = p.getFileName().toString();
        String phan = ten.substring(1, ten.indexOf("__"));
        List<Integer> ra = new ArrayList<>();
        for (String m : phan.split("[._]")) {
            ra.add(Integer.parseInt(m));
        }
        return ra;
    }

    private static int soSanh(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            int x = i < a.size() ? a.get(i) : 0;
            int y = i < b.size() ? b.get(i) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    @Test
    @DisplayName("Sau tệp di trú cuối cùng, user_feedback(user_id) KHÔNG còn index duy nhất")
    void khongConRangBuocMotBanGhiMotNguoi() throws IOException {
        String thaoTacCuoi = null;
        String tepCuoi = null;

        for (Path tep : theoThuTuChay()) {
            String sql = Files.readString(tep, StandardCharsets.UTF_8);
            // Trong một tệp có thể vừa xoá vừa tạo lại (V2_34 làm đúng thế),
            // nên phải xét theo vị trí xuất hiện chứ không chỉ "có hay không".
            Matcher mTao = TAO.matcher(sql);
            Matcher mXoa = XOA.matcher(sql);
            int viTriTao = mTao.find() ? mTao.start() : -1;
            int viTriXoa = mXoa.find() ? mXoa.start() : -1;

            if (viTriTao >= 0 && viTriTao > viTriXoa) {
                thaoTacCuoi = "TẠO";
                tepCuoi = tep.getFileName().toString();
            } else if (viTriXoa >= 0) {
                thaoTacCuoi = "XOÁ";
                tepCuoi = tep.getFileName().toString();
            }
        }

        assertThat(thaoTacCuoi)
                .as("""
                        Tầng ứng dụng cho một tài khoản góp ý nhiều lần, nên CSDL \
                        không được giữ index duy nhất trên user_feedback(user_id). \
                        Thao tác sau cùng đang nằm ở tệp %s. Thêm một tệp di trú \
                        mới để xoá index ấy, đừng sửa tệp cũ — Flyway đã chạy nó \
                        trên production rồi.""".formatted(tepCuoi))
                .isNotEqualTo("TẠO");
    }

    @Test
    @DisplayName("So thứ tự phiên bản bằng SỐ, không bằng chữ")
    void soHieuSoSanhBangSo() {
        // Chốt lại chỗ dễ sai của chính bài kiểm này: so theo chữ thì V2_9
        // đứng sau V2_34, và kết luận về "tệp cuối cùng" sẽ sai.
        assertThat(soSanh(List.of(2, 9), List.of(2, 34))).isNegative();
        assertThat(soSanh(List.of(2, 34), List.of(2, 38))).isNegative();
        assertThat(soSanh(List.of(1), List.of(2, 11))).isNegative();
    }
}
