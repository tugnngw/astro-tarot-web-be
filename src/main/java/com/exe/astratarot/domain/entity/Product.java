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
