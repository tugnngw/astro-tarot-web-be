package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "escrow_accounts")
public class EscrowAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    private Long balance = 0L;

    @Builder.Default
    @Column(name = "pending_balance")
    private Long pendingBalance = 0L;

    @Builder.Default
    @Column(name = "total_earned")
    private Long totalEarned = 0L;

    @Builder.Default
    @Column(name = "total_withdrawn")
    private Long totalWithdrawn = 0L;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
