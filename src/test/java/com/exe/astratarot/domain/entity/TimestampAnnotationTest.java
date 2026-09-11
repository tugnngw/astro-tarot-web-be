package com.exe.astratarot.domain.entity;

import jakarta.persistence.Entity;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @CreationTimestamp} và {@code @UpdateTimestamp} phải nằm trên một
 * trường kiểu thời gian.
 *
 * <p>Nghe hiển nhiên tới mức không đáng viết test. Nhưng tôi vừa làm sai ở BỐN
 * entity cùng một lúc: thêm trường mới bằng cách chèn vào ngay phía trên
 * {@code @Column(name = "created_at")}, mà annotation thời gian lại nằm ở dòng
 * trên nữa — nên nó rơi sang trường mới, còn trường ngày tạo thì mất.
 *
 * <p>Hậu quả không hề nhẹ: Hibernate coi trường mới là cột tự sinh, chèn
 * {@code localtimestamp} vào một cột {@code bigint}, và mọi lệnh tạo báo cáo vi
 * phạm trả 500. Nó còn âm thầm VỨT BỎ giá trị mà tầng trên gửi xuống — tiền
 * phạt gửi 10.000 mà lưu xuống 0, không một dòng lỗi nào.
 *
 * <p>Bộ test cũ không bắt được vì không có bài nào ghi xuống những entity đó.
 * Bài này thì không cần database, chạy trong mili giây, và soi TẤT CẢ entity —
 * kể cả những cái viết sau này.
 */
class TimestampAnnotationTest {

    /** Những kiểu mà một mốc thời gian có thể mang. */
    private static final Set<Class<?>> KIEU_THOI_GIAN =
            Set.of(Instant.class, LocalDateTime.class, LocalDate.class, Date.class,
                    java.sql.Timestamp.class, java.time.OffsetDateTime.class,
                    java.time.ZonedDateTime.class);

    @Test
    @DisplayName("Mọi @CreationTimestamp/@UpdateTimestamp đều nằm trên trường thời gian")
    void annotationThoiGianPhaiNamDungCho() {
        List<String> sai = new ArrayList<>();

        for (Class<?> entity : timEntity()) {
            for (Field f : entity.getDeclaredFields()) {
                boolean coAnnotation = f.isAnnotationPresent(CreationTimestamp.class)
                        || f.isAnnotationPresent(UpdateTimestamp.class);
                if (coAnnotation && !KIEU_THOI_GIAN.contains(f.getType())) {
                    sai.add(entity.getSimpleName() + "." + f.getName()
                            + " (kiểu " + f.getType().getSimpleName() + ")");
                }
            }
        }

        assertThat(sai)
                .as("Annotation thời gian đặt nhầm trường — Hibernate sẽ chèn "
                        + "localtimestamp vào cột này và vứt bỏ giá trị thật")
                .isEmpty();
    }

    @Test
    @DisplayName("Mỗi entity có cột ngày tạo thì cột đó phải được sinh tự động")
    void congNgayTaoPhaiTuSinh() {
        List<String> thieu = new ArrayList<>();

        for (Class<?> entity : timEntity()) {
            for (Field f : entity.getDeclaredFields()) {
                // Chỉ xét đúng những trường tên là createdAt: đó là chỗ mà mất
                // annotation sẽ lặng lẽ ghi null xuống một cột NOT NULL.
                if (!"createdAt".equals(f.getName())) continue;
                if (!KIEU_THOI_GIAN.contains(f.getType())) continue;

                boolean tuSinh = f.isAnnotationPresent(CreationTimestamp.class);
                // Một số entity tự đặt trong @PrePersist thay vì dùng annotation.
                boolean coPrePersist = java.util.Arrays.stream(entity.getDeclaredMethods())
                        .anyMatch(m -> m.isAnnotationPresent(jakarta.persistence.PrePersist.class));

                if (!tuSinh && !coPrePersist) {
                    thieu.add(entity.getSimpleName() + ".createdAt");
                }
            }
        }

        assertThat(thieu)
                .as("createdAt không có @CreationTimestamp và cũng không có "
                        + "@PrePersist — sẽ ghi null xuống cột NOT NULL")
                .isEmpty();
    }

    private static List<Class<?>> timEntity() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<Class<?>> ds = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.exe.astratarot.domain.entity")) {
            try {
                ds.add(Class.forName(bd.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Không nạp được entity " + bd.getBeanClassName(), e);
            }
        }
        assertThat(ds).as("Không quét thấy entity nào — bài test này đang vô dụng").isNotEmpty();
        return ds;
    }
}
