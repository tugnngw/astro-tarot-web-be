package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reader_profiles")
public class ReaderProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] specialties = {};

    @Builder.Default
    @Column(name = "years_experience")
    private Integer yearsExperience = 0;

    @Column(name = "price_per_15m")
    private Long pricePer15m;

    @Column(name = "price_per_30m")
    private Long pricePer30m;

    @Column(name = "price_per_60m")
    private Long pricePer60m;

    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_reviews")
    private Integer totalReviews = 0;

    @Builder.Default
    @Column(name = "is_available")
    private Boolean available = true;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
