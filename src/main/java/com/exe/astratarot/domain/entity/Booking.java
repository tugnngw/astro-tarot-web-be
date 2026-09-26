package com.exe.astratarot.domain.entity;

import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
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
@Table(name = "bookings")
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_profile_id", nullable = false)
    private ReaderProfile readerProfile;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "deposit_amount", nullable = false)
    private Long depositAmount;

    @Column(name = "remaining_amount", nullable = false)
    private Long remainingAmount;

    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    // @Builder.Default là BẮT BUỘC ở đây, không phải tuỳ chọn. Thiếu nó thì
    // Lombok bỏ qua giá trị khởi tạo và builder ghi null xuống một cột
    // `nullable = false` — mọi lượt đặt lịch đổ ở tầng database, và đổ tại chỗ
    // ghi chứ không tại chỗ sai, nên rất khó lần ra. BuilderDefaultTest chốt
    // luật này cho toàn bộ entity.
    @Builder.Default
    @Column(name = "forfeited_amount", nullable = false)
    private Long forfeitedAmount = 0L;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    /**
     * Ghi chú Reader viết cho khách sau buổi xem.
     *
     * <p>Không phải nhật ký nội bộ: khách đọc được. Với đề tài Tarot thì đây
     * chính là sản phẩm — trước khi có cột này, khách trả tiền xong là buổi
     * xem không để lại gì ngoài một dòng trạng thái.
     */
    @Column(name = "reader_note", columnDefinition = "TEXT")
    private String readerNote;

    @Column(name = "reader_note_at")
    private Instant readerNoteAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
