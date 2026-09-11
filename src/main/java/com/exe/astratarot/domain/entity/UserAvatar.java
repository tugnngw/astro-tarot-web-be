package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Ảnh đại diện lưu trong CSDL.
 *
 * Để riêng khỏi {@link User} vì hai lý do: đọc hồ sơ không phải kéo theo vài
 * trăm KB nhị phân, và ảnh xoá được mà không đụng tới hàng người dùng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_avatars")
public class UserAvatar {

    /** Chính là id của người dùng — mỗi người một ảnh. */
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    /**
     * KHÔNG dùng @Lob ở đây.
     *
     * Với Postgres, @Lob trên byte[] khiến Hibernate ánh xạ sang kiểu `oid`
     * (large object nằm ngoài bảng), trong khi migration tạo cột `bytea`. Hai
     * bên lệch nhau thì schema-validation chặn ngay lúc khởi động và cả ứng
     * dụng không lên nổi — đã xảy ra thật trên production.
     *
     * byte[] trần ánh xạ thẳng sang `bytea`, đúng thứ migration tạo ra. Ảnh
     * giới hạn 2MB nên không cần tới large object.
     */
    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
