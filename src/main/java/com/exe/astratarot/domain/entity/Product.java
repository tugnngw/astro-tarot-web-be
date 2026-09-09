package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Giá bán, VND nguyên. */
    @Column(nullable = false)
    private Long price;

    /** Giá gạch ngang để hiện khuyến mãi. Null nếu không giảm giá. */
    @Column(name = "compare_at_price")
    private Long compareAtPrice;

    @Builder.Default
    @Column(nullable = false)
    private Integer stock = 0;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** TRUE khi imageUrl là ảnh minh hoạ chứ không phải ảnh chụp đúng sản phẩm. */
    @Builder.Default
    @Column(name = "image_is_illustrative", nullable = false)
    private Boolean imageIsIllustrative = false;

    /**
     * Đường dẫn tiếp thị liên kết. NULL nghĩa là chưa gắn link — giao diện ẩn
     * hẳn nút mua thay vì dẫn khách tới trang lỗi.
     */
    @Column(name = "affiliate_url", columnDefinition = "TEXT")
    private String affiliateUrl;

    @Builder.Default
    @Column(name = "affiliate_platform", nullable = false, length = 30)
    private String affiliatePlatform = "SHOPEE";

    /** Chỉ để ước lượng. Số hoa hồng thật lấy từ báo cáo của sàn. */
    @Builder.Default
    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal commissionPercent = java.math.BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "click_count", nullable = false)
    private Long clickCount = 0L;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean featured = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
