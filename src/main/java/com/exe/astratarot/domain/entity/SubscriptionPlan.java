package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_plan")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlanType planType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer dailyQuota;

    @Column(nullable = false)
    private Long price;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    private String description;

    /**
     * Mã sản phẩm khai trong Play Console. NULL = gói không bán trên Android.
     *
     * <p>Giá bán trên Android KHÔNG lấy từ cột {@code price}: Google bắt
     * hiển thị đúng giá thư viện thanh toán trả về (đúng tiền tệ, đúng thuế
     * của từng nước), và giá ấy chỉnh ở Play Console chứ không ở đây.
     */
    @Column(name = "google_play_product_id", length = 100)
    private String googlePlayProductId;

    /**
     * Giá niêm yết trên Play Console, tính bằng VND.
     *
     * <p>Chỉ dùng để GHI SỔ: giá khách trả do Play Console quyết, app hiện
     * đúng con số Google trả về. Cột này cao hơn {@code price} vì Google thu
     * hoa hồng mỗi lượt mua, nên giá app phải kê lên để thực nhận không thấp
     * hơn bán trên web.
     *
     * <p>NULL thì lượt mua qua Play ghi lại {@code price} — thống kê sẽ thấp
     * hơn tiền thật, nên sửa giá bên Play Console là phải sửa cả cột này.
     */
    @Column(name = "google_play_price")
    private Long googlePlayPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Timestamp.from(Instant.now());
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Timestamp.from(Instant.now());
    }

    public enum PlanType {
        MONTHLY, DAY_PASS, FREE
    }
}