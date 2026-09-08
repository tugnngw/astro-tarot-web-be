package com.exe.astratarot.domain.entity;

import com.exe.astratarot.domain.enums.SessionType;
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
@Table(name = "tarot_readings")
public class TarotReading {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "astro_profile_id")
    private UserAstrologicalData astroProfile;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type")
    private SessionType sessionType = SessionType.AI;

    @Column(name = "main_question", nullable = false, columnDefinition = "TEXT")
    private String mainQuestion;

    @Column(name = "ai_model_used", length = 100)
    private String aiModelUsed;

    @Builder.Default
    @Column(name = "total_tokens_used")
    private Integer totalTokensUsed = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
