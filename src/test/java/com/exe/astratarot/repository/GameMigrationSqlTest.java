package com.exe.astratarot.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy thật tệp di trú của mini game.
 *
 * <h2>Vì sao cần bài này</h2>
 *
 * {@code application-test.properties} đặt {@code spring.flyway.enabled=false}
 * và để Hibernate tự dựng bảng từ entity, nên KHÔNG có bài nào trong dự án mở
 * tới tệp {@code .sql} trong {@code db/migration}. Một dấu phẩy thiếu hay một
 * tên cột viết sai ở đó đi qua toàn bộ CI màu xanh, rồi đổ ở bước Flyway lúc
 * Render khởi động — tức là sau khi đã đẩy lên production.
 *
 * <h2>Bài này KHÔNG chứng minh được gì</h2>
 *
 * H2 ở chế độ PostgreSQL vẫn là H2. Nó không bắt được những chỗ Postgres khó
 * tính hơn (kiểu dữ liệu riêng, hành vi khi ép kiểu, cách so sánh chuỗi). Nó
 * chỉ bắt lỗi cú pháp và tên sai — mà đó mới là thứ hay sai khi viết DDL bằng
 * tay, và hiện không có gì bắt.
 */
class GameMigrationSqlTest {

    private static final Path TEP = Path.of(
            "src/main/resources/db/migration/V2_37__game_giao_hoi.sql");

    /**
     * H2 không nhận từ {@code TIMESTAMPTZ}, kể cả ở chế độ PostgreSQL.
     *
     * <p>Với Postgres thì {@code TIMESTAMPTZ} chỉ là tên gọi ngắn của
     * {@code TIMESTAMP WITH TIME ZONE} — hai thứ y hệt nhau. Cả 36 tệp di trú
     * trước đều viết {@code TIMESTAMPTZ} và chạy thật trên Render, nên chỗ phải
     * nhượng bộ là bài kiểm này chứ không phải tệp di trú: đổi tệp di trú theo
     * H2 là để một chữ trong mã production bị quyết định bởi một thư viện chỉ
     * tồn tại lúc chạy test.
     *
     * <p>Phép thay chỉ đổi đúng một tên kiểu dữ liệu, nên nó không che được lỗi
     * cú pháp nào khác.
     */
    private static String choH2Hieu(String sql) {
        return sql.replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE");
    }

    @Test
    @DisplayName("tệp di trú chạy được và dựng ra đúng hai bảng")
    void tepDiTruChayDuoc() throws Exception {
        String goc = Files.readString(TEP, StandardCharsets.UTF_8);
        // Nếu một ngày tệp di trú không còn cột thời gian nào thì phép thay bên
        // dưới thành vô nghĩa; bắt ngay thay vì để nó im lặng không làm gì.
        assertThat(goc).contains("TIMESTAMPTZ");
        String sql = choH2Hieu(goc);

        try (Connection cn = DriverManager.getConnection(
                "jdbc:h2:mem:ditru_game;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
             Statement st = cn.createStatement()) {

            // Khoá ngoại trỏ tới users(id), nên phải có một bảng users để trỏ.
            st.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
            st.execute(sql);

            assertThat(demCot(cn, "GAME_PHIEN")).isPositive();
            assertThat(demCot(cn, "GAME_NGAY")).isPositive();

            // Các cột tầng dịch vụ thật sự đọc và ghi. Đặt sai tên ở đây thì
            // Hibernate đổ lúc chạy, không phải lúc dựng.
            for (String cot : new String[] {
                    "ID", "USER_ID", "NHAN_VAT", "BANG", "HAT_GIONG",
                    "DIEM", "LUOT_CON_LAI", "NANG_LUONG", "DA_CHOT"}) {
                assertThat(coCot(cn, "GAME_PHIEN", cot))
                        .as("game_phien thiếu cột " + cot).isTrue();
            }
            for (String cot : new String[] {"USER_ID", "NGAY", "DIEM_DA_TINH", "LUOT_DA_NHAN"}) {
                assertThat(coCot(cn, "GAME_NGAY", cot))
                        .as("game_ngay thiếu cột " + cot).isTrue();
            }

            // Khoá chính kép của game_ngay: thiếu nó thì một người có nhiều hàng
            // cùng ngày, và trần lượt ngày đếm sai về phía có lợi cho người chơi.
            assertThat(demKhoaChinh(cn, "GAME_NGAY")).isEqualTo(2);
        }
    }

    private static int demCot(Connection cn, String bang) throws Exception {
        try (ResultSet rs = cn.getMetaData().getColumns(null, null, bang, null)) {
            int n = 0;
            while (rs.next()) n++;
            return n;
        }
    }

    private static boolean coCot(Connection cn, String bang, String cot) throws Exception {
        try (ResultSet rs = cn.getMetaData().getColumns(null, null, bang, cot)) {
            return rs.next();
        }
    }

    private static int demKhoaChinh(Connection cn, String bang) throws Exception {
        try (ResultSet rs = cn.getMetaData().getPrimaryKeys(null, null, bang)) {
            int n = 0;
            while (rs.next()) n++;
            return n;
        }
    }
}
