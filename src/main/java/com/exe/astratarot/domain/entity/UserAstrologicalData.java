package com.exe.astratarot.domain.entity;

import com.exe.astratarot.domain.enums.ProfileType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_astrological_data")
public class UserAstrologicalData {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type", nullable = false)
    private ProfileType profileType = ProfileType.SELF;

    @Column(nullable = false)
    private String title;

    @Column(name = "target_name")
    private String targetName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "birth_time")
    private LocalTime birthTime;

    @Column(name = "birth_place")
    private String birthPlace;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String timezone;

    @Column(name = "encrypted_data", nullable = false)
    private byte[] encryptedData;

    @Column(name = "encryption_iv", nullable = false)
    private byte[] encryptionIv;

    @Builder.Default
    @Column(name = "is_primary", nullable = false)
    private Boolean primary = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
