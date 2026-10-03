package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "user_plan_purchase")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPlanPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "plan_name_snapshot", nullable = false)
    private String planNameSnapshot;

    @Column(name = "daily_quota_snapshot", nullable = false)
    private Integer dailyQuotaSnapshot;

    @Column(name = "price_snapshot", nullable = false)
    private Long priceSnapshot;

    @Column(name = "start_at", nullable = false)
    private Timestamp startAt;

    @Column(name = "end_at", nullable = false)
    private Timestamp endAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PurchaseStatus status;

    @Column(name = "purchase_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private PurchaseType purchaseType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", insertable = false, updatable = false)
    private SubscriptionPlan plan;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Timestamp.from(Instant.now());
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Timestamp.from(Instant.now());
    }

    public boolean isActive() {
        return status == PurchaseStatus.ACTIVE &&
               Instant.now().isBefore(endAt.toInstant()) &&
               Instant.now().isAfter(startAt.toInstant());
    }

    public int getRemainingDays() {
        Instant now = Instant.now();
        if (!isActive()) return 0;
        return (int) ChronoUnit.DAYS.between(now, endAt.toInstant());
    }

    public enum PurchaseStatus {
        ACTIVE, EXPIRED, SUPERSEDED, CANCELLED
    }

    public enum PurchaseType {
        STRIPE, PAYOS, WALLET, MANUAL
    }
}