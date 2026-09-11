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

    /** Vì sao bị từ chối. Reader phải biết để sửa thông tin ngân hàng. */
    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @CreationTimestamp
    /**
     * Mã BIN ngân hàng theo chuẩn VietQR (970436 = Vietcombank, …).
     *
     * <p>Tên ngân hàng dạng chữ tự do không dựng được mã QR: "Vietcombank",
     * "VCB" và "ngân hàng ngoại thương" là ba cách viết của cùng một nơi mà máy
     * không đoán được. Cho phép rỗng vì những lệnh rút tạo trước thay đổi này
     * không có mã BIN, và không được vì thế mà hỏng.
     */
    @Column(name = "bank_bin", length = 20)
    private String bankBin;

    @Column(name = "requested_at", updatable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
