package com.exe.astratarot.domain.entity;

import com.exe.astratarot.domain.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "payout_requests")
public class PayoutRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false)
    private ReaderProfile reader;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "account_holder")
    private String accountHolder;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private PayoutStatus status = PayoutStatus.PENDING;

    @CreationTimestamp
    @Column(name = "requested_at", updatable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
