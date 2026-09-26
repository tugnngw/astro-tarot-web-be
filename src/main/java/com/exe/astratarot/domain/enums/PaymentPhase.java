package com.exe.astratarot.domain.enums;

/**
 * Giai đoạn thanh toán của một booking.
 *
 * <p>Máy trạng thái: {@code UNPAID} → {@code DEPOSIT_PAID} → {@code PAID}.
 * Intent có thể mang phase FULL khi đặt sát giờ (còn < 12h).
 */
public enum PaymentPhase {
    /** Đặt cọc 50% khi còn ≥ 12h tới giờ hẹn */
    DEPOSIT,
    /** Thanh toán nốt 50% còn lại */
    REMAINING,
    /** Trả 100% ngay khi đặt (còn < 12h tới giờ hẹn) */
    FULL
}
