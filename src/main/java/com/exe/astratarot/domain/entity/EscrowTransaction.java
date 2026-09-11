package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng sổ cái ký quỹ.
 *
 * <p>Trước đây {@link EscrowAccount} chỉ giữ bốn con số cộng dồn, nên Reader
 * nhìn thấy số dư mà không biết nó đến từ đâu, và khi số liệu lệch thì không có
 * gì để đối chiếu. Mỗi lần tiền nhúc nhích, {@code EscrowServiceImpl} ghi một
 * dòng ở đây — kèm số dư SAU nghiệp vụ, để chỗ đứt mạch lộ ra ngay.
 *
 * <p>Số tiền luôn dương; hướng nằm ở {@link Kind}. Nhét dấu âm vào số tiền là
 * cách tạo ra loại lỗi im lặng nhất trong kế toán.
 */
@Entity
@Table(name = "escrow_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EscrowTransaction {

    public enum Kind {
        HOLD,
        RELEASE,
        REFUND,
        PENALTY,
        PENALTY_DEBT,
        DEBT_COLLECTED,
        PAYOUT_RESERVE,
        PAYOUT_RETURN,
        PAYOUT_SETTLE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escrow_account_id", nullable = false)
    private EscrowAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Kind kind;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "pending_after", nullable = false)
    private Long pendingAfter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id")
    private PayoutRequest payout;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
