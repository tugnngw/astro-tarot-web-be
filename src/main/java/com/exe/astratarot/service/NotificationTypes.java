package com.exe.astratarot.service;

/**
 * Loại thông báo.
 *
 * <p>Gom vào một chỗ để giao diện ánh xạ được sang biểu tượng và đường dẫn. Nếu
 * mỗi nơi tự đặt chuỗi thì sẽ có cả booking_created lẫn BOOKING_NEW cùng nằm
 * trong một cột và giao diện không nhận ra cái nào.
 */
public final class NotificationTypes {
    public static final String BOOKING_CREATED = "BOOKING_CREATED";
    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";
    public static final String BOOKING_COMPLETED = "BOOKING_COMPLETED";
    public static final String REVIEW_RECEIVED = "REVIEW_RECEIVED";
    public static final String READER_APPLICATION_APPROVED = "READER_APPLICATION_APPROVED";
    public static final String READER_APPLICATION_REJECTED = "READER_APPLICATION_REJECTED";
    public static final String ACCOUNT_ROLE_CHANGED = "ACCOUNT_ROLE_CHANGED";
    public static final String PAYMENT_CONFIRMED = "PAYMENT_CONFIRMED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REFUNDED = "PAYMENT_REFUNDED";
    public static final String PAYOUT_APPROVED = "PAYOUT_APPROVED";
    public static final String PAYOUT_REJECTED = "PAYOUT_REJECTED";
    public static final String PAYOUT_PAID = "PAYOUT_PAID";
    public static final String REPORT_RESOLVED = "REPORT_RESOLVED";

    private NotificationTypes() {}
}
