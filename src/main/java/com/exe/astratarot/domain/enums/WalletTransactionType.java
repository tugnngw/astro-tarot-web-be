package com.exe.astratarot.domain.enums;

/**
 * Loại giao dịch ví cá nhân ASTROTAROT.
 */
public enum WalletTransactionType {
    /** Nạp tiền vào ví qua cổng thanh toán (PayOS VietQR) */
    TOPUP,
    /** Mua gói cước AI Tarot */
    AI_SUBSCRIPTION,
    /** Thanh toán tiền cọc hoặc tiền dịch vụ đọc bài Tarot */
    BOOKING_PAYMENT,
    /** Nhận tiền hoàn khi huỷ lịch hẹn Tarot */
    BOOKING_REFUND,
    /** Quản trị viên can thiệp điều chỉnh số dư */
    ADMIN_ADJUSTMENT
}
