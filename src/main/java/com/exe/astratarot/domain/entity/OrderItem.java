package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng trong đơn hàng.
 *
 * Tên, ảnh và đơn giá được chụp lại tại thời điểm đặt hàng thay vì đọc
 * xuyên qua Product, để đơn cũ không bị đổi theo khi sản phẩm sửa giá,
 * đổi tên hay bị gỡ khỏi shop.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Null nếu sản phẩm đã bị xoá khỏi catalog sau khi đơn được tạo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_image_url", columnDefinition = "TEXT")
    private String productImageUrl;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false)
    private Long lineTotal;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
