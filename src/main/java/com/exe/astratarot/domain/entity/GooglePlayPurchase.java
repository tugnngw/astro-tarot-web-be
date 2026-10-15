package com.exe.astratarot.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lượt mua đã đổi lấy gói — khoá chính là purchaseToken của Google.
 *
 * <p>Tồn tại để mỗi lượt mua chỉ đổi được ĐÚNG MỘT LẦN. Không có bảng này
 * thì cùng một token gửi lại nhiều lần là nhận nhiều gói, và máy khách gửi
 * lại thật mà không hề gian: mạng rớt giữa chừng, app khởi động lại và phát
 * lại luồng mua chưa xác nhận — thư viện {@code in_app_purchase} cố ý phát
 * lại cho tới khi được acknowledge.
 */
@Entity
@Table(name = "google_play_purchase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GooglePlayPurchase {

    /** purchaseToken của Google. Dài, nên cột để 512. */
    @Id
    @Column(name = "purchase_token", length = 512)
    private String purchaseToken;

    /** Mã đơn Google hiện trong email biên nhận của khách — tiện đối soát. */
    @Column(name = "order_id", length = 128)
    private String orderId;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    /** Bản ghi gói đã cấp. Null nếu cấp xong mới ghi nhận được. */
    @Column(name = "user_purchase_id")
    private UUID userPurchaseId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
