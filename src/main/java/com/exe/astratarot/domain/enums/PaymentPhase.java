package com.exe.astratarot.domain.enums;

/**
 * Giai đoạn / loại thanh toán PayOS hoặc chuyển khoản.
 *
 * <p>Booking: {@code UNPAID} → {@code DEPOSIT_PAID} → {@code PAID}.
 * Intent có thể mang phase FULL khi đặt sát giờ (còn &lt; 12h).
 * Ví và gói AI dùng TOPUP / AI_SUBSCRIPTION (không gắn booking).
 */
public enum PaymentPhase {
    /** Đặt cọc 50% khi còn ≥ 12h tới giờ hẹn */
    DEPOSIT,
    /** Thanh toán nốt 50% còn lại */
    REMAINING,
    /** Trả 100% ngay khi đặt (còn < 12h tới giờ hẹn) */
    FULL,
    /** Nạp tiền vào ví người dùng ASTROTAROT */
    TOPUP,
    /** Mua gói cước AI — kích hoạt purchase sau khi PayOS xác nhận */
    AI_SUBSCRIPTION
}
