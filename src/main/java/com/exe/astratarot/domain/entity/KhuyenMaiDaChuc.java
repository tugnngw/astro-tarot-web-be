package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Ai đã xem lời chúc của đợt nào.
 *
 * <p>Khoá chính ghép CHÍNH LÀ thứ bảo đảm "chỉ một lần". Không dựa vào việc
 * ứng dụng kiểm trước rồi mới ghi: mở app trên điện thoại và web gần như
 * cùng lúc thì cả hai đều đọc thấy "chưa xem" và cả hai cùng hiện lời chúc.
 * Để cơ sở dữ liệu từ chối hàng thứ hai thì không có khe nào lọt.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "khuyen_mai_da_chuc")
@IdClass(KhuyenMaiDaChuc.Khoa.class)
public class KhuyenMaiDaChuc {

    @Id
    @Column(name = "khuyen_mai_id", nullable = false)
    private UUID khuyenMaiId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    @Column(name = "xem_luc", nullable = false)
    private Instant xemLuc = Instant.now();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Khoa implements Serializable {
        private UUID khuyenMaiId;
        private UUID userId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Khoa khoa)) return false;
            return Objects.equals(khuyenMaiId, khoa.khuyenMaiId)
                    && Objects.equals(userId, khoa.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(khuyenMaiId, userId);
        }
    }
}
