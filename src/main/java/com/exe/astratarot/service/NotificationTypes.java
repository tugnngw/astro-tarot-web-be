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
    public static final String PAYMENT_FORFEITED = "PAYMENT_FORFEITED";
    public static final String REPORT_RESOLVED = "REPORT_RESOLVED";
    /** Nhân viên trả lời ticket → báo khách. */
    public static final String SUPPORT_REPLY = "SUPPORT_REPLY";
    /** Khách bổ sung tin → báo nhân viên phụ trách (hoặc hàng chờ). */
    public static final String SUPPORT_MESSAGE = "SUPPORT_MESSAGE";

    // ---------- Khoá trong metadata ----------

    /**
     * Thông báo này nói về phía nào của một buổi xem: {@link #SIDE_READER} hay
     * {@link #SIDE_CUSTOMER}.
     *
     * <p>Một buổi xem có hai người và HAI danh sách khác nhau trên giao diện —
     * "Lịch hẹn của tôi" của khách, và tab Lịch hẹn trong Bàn làm việc của
     * Reader. Loại thông báo không đủ để biết phải mở cái nào: BOOKING_CANCELLED
     * và PAYMENT_CONFIRMED đều gửi được cho cả hai bên, tuỳ tình huống.
     *
     * <p>Không có khoá này thì giao diện phải đoán theo loại, và nó đoán sai:
     * Reader nhận "Có lịch hẹn mới" rồi bấm vào, bị đưa sang danh sách phía
     * KHÁCH — nơi trống rỗng một cách hoàn toàn đúng đắn, vì chính họ không đặt
     * gì cả. Người dùng kết luận lịch hẹn không tới nơi.
     *
     * <p>Người gửi biết chắc mình đang báo cho ai, nên phía được nói ra ở đây
     * chứ không suy lại ở đầu bên kia.
     */
    public static final String SIDE = "side";

    /** Người nhận đang đứng ở vai Reader của buổi xem. */
    public static final String SIDE_READER = "reader";

    /** Người nhận đang đứng ở vai khách của buổi xem. */
    public static final String SIDE_CUSTOMER = "customer";

    private NotificationTypes() {}
}
