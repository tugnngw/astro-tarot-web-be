package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lượt khách bấm sang sàn liên kết.
 *
 * <p>Đây là số liệu duy nhất mình tự đo được. Đơn hàng và hoa hồng thật nằm ở
 * báo cáo của sàn, không lấy về được nếu chưa đăng ký chương trình đối tác.
 *
 * <p>Cố ý không lưu địa chỉ IP hay dấu vết nhận dạng người chưa đăng nhập: mục
 * đích là biết sản phẩm nào được quan tâm, không phải theo dõi người dùng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product_clicks")
public class ProductClick {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** NULL khi khách chưa đăng nhập — vẫn đếm, vì phần lớn lượt bấm là khách vãng lai. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 255)
    private String referrer;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
