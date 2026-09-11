package com.exe.astratarot.domain.entity;

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
 * Trường có giá trị khởi tạo, trong một entity dùng {@code @Builder}, thì phải
 * có {@code @Builder.Default}.
 *
 * <p>Lombok <b>bỏ qua</b> giá trị khởi tạo khi dựng đối tượng qua builder. Viết
 * {@code private Long penaltyAmount = 0L;} rồi gọi
 * {@code Report.builder()...build()} thì trường đó là {@code null}, không phải
 * 0. Không có cảnh báo nào lúc biên dịch.
 *
 * <p>Với một cột NOT NULL thì hậu quả là cả lệnh insert nổ:
 * <pre>
 *   ERROR: null value in column "penalty_amount" of relation "reports"
 *          violates not-null constraint
 * </pre>
 * Tôi đã mắc đúng lỗi này ở hai entity, và nó chỉ lộ ra khi gọi API thật trên
 * production. Mọi entity có sẵn trong dự án đều đã có {@code @Builder.Default},
 * tức quy ước vốn đúng và chỉ mình tôi phá.
 *
 * <p>Bài test đọc THẲNG MÃ NGUỒN chứ không dùng reflection, vì giá trị khởi tạo
 * của trường không để lại dấu vết nào trong bytecode để soi.
 */
class BuilderDefaultTest {

    private static final Path THU_MUC_ENTITY =
            Path.of("src/main/java/com/exe/astratarot/domain/entity");

    /** Khai báo trường có gán giá trị ngay: {@code private Kiểu tên = giá trị;} */
    private static final Pattern TRUONG_CO_GIA_TRI = Pattern.compile(
            "(?m)^[ \\t]*private\\s+[\\w<>,\\[\\] .]+\\s+(\\w+)\\s*=\\s*[^;]+;");

    @Test
    @DisplayName("Entity dùng @Builder thì mọi trường có giá trị khởi tạo phải có @Builder.Default")
    void moiGiaTriKhoiTaoPhaiCoBuilderDefault() throws IOException {
        assertThat(Files.isDirectory(THU_MUC_ENTITY))
                .as("Không thấy thư mục entity ở %s — bài test này đang vô dụng", THU_MUC_ENTITY)
                .isTrue();

        List<String> thieu = new ArrayList<>();

        try (Stream<Path> files = Files.list(THU_MUC_ENTITY)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                // Bỏ chú thích TRƯỚC khi soi.
                //
                // Bản đầu của bài test này không làm vậy, và vì thế không bao
                // giờ đỏ được: chính dòng chú thích tôi viết ngay trên trường
                // có chứa chữ "Builder.Default", nên phép tìm luôn thấy nó kể
                // cả khi annotation đã bị gỡ. Một bài test không thể thất bại
                // thì tệ hơn là không có test.
                String ma = boChuThich(Files.readString(f, StandardCharsets.UTF_8));
                if (!ma.contains("@Builder")) continue;

                Matcher m = TRUONG_CO_GIA_TRI.matcher(ma);
                while (m.find()) {
                    // Nhìn lui vài dòng ngay trên khai báo để tìm annotation.
                    String truoc = ma.substring(Math.max(0, m.start() - 300), m.start());
                    if (!truoc.contains("@Builder.Default")) {
                        thieu.add(f.getFileName() + "." + m.group(1));
                    }
                }
            }
        }

        assertThat(thieu)
                .as("Thiếu @Builder.Default — Lombok sẽ bỏ qua giá trị khởi tạo và "
                        + "ghi null xuống, kể cả khi cột là NOT NULL")
                .isEmpty();
    }

    /** Bỏ chú thích khối và chú thích dòng. */
    private static String boChuThich(String ma) {
        String khongKhoi = ma.replaceAll("(?s)/\\*.*?\\*/", "");
        return khongKhoi.replaceAll("(?m)//.*$", "");
    }
}
