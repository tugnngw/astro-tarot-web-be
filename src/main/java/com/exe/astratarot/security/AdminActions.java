package com.exe.astratarot.security;

/**
 * Tên hành động ghi vào activity_logs.
 *
 * <p>Gom vào một chỗ để nhật ký lọc được: nếu mỗi nơi tự đặt chuỗi thì sẽ có cả
 * "CHANGE_ROLE", "change_role" và "Đổi vai trò" cùng nằm trong một cột.
 */
public final class AdminActions {
    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_ROLE_CHANGE = "USER_ROLE_CHANGE";
    public static final String USER_STATUS_CHANGE = "USER_STATUS_CHANGE";
    public static final String USER_DELETE = "USER_DELETE";
    public static final String USER_SESSIONS_REVOKE = "USER_SESSIONS_REVOKE";
    public static final String USER_PASSWORD_RESET_SENT = "USER_PASSWORD_RESET_SENT";
    public static final String USER_VERIFICATION_RESENT = "USER_VERIFICATION_RESENT";

    public static final String PAYMENT_CONFIRM = "PAYMENT_CONFIRM";
    public static final String PAYMENT_REJECT = "PAYMENT_REJECT";
    public static final String PAYOUT_APPROVE = "PAYOUT_APPROVE";
    public static final String PAYOUT_REJECT = "PAYOUT_REJECT";
    public static final String PAYOUT_PAID = "PAYOUT_PAID";
    public static final String REPORT_HANDLE = "REPORT_HANDLE";

    /** Đối tượng bị tác động. */
    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_PAYMENT = "PAYMENT";
    public static final String ENTITY_PAYOUT = "PAYOUT";
    public static final String ENTITY_REPORT = "REPORT";

    private AdminActions() {}
}
