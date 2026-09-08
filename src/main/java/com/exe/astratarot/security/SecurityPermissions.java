package com.exe.astratarot.security;

/**
 * Tên các quyền dùng trong {@code @PreAuthorize("hasAuthority(...)")}.
 *
 * <p>Controller kiểm tra QUYỀN chứ không kiểm tra role. Nhờ vậy khi đổi việc
 * một vai trò được làm gì thì chỉ sửa bảng ánh xạ ở {@link CustomUserDetails},
 * không phải đi sửa từng annotation rải khắp controller.
 */
public final class SecurityPermissions {

    // ----- Mọi tài khoản đã đăng nhập -----
    public static final String USER_BASIC = "USER_BASIC";

    // ----- Reader (nay thuộc STAFF) -----
    /** Nộp hồ sơ xin làm Reader. Chỉ USER cần, người đã là STAFF thì không. */
    public static final String READER_APPLY = "READER_APPLY";
    /** Tự sửa hồ sơ Reader, lịch rảnh, dịch vụ của chính mình. */
    public static final String READER_MANAGE_PROFILE = "READER_MANAGE_PROFILE";

    // ----- Hỗ trợ khách -----
    /** Xem hàng chờ yêu cầu hỗ trợ. */
    public static final String SUPPORT_VIEW = "SUPPORT_VIEW";
    /** Trả lời khách. Quản lý chỉ giám sát nên không có quyền này. */
    public static final String SUPPORT_RESPOND = "SUPPORT_RESPOND";

    // ----- Quản lý nhân sự -----
    /** Xem danh sách nhân sự (STAFF, MANAGER) và thông tin liên hệ của họ. */
    public static final String STAFF_VIEW = "STAFF_VIEW";
    /**
     * Cất nhắc USER lên STAFF và hạ STAFF về USER, khoá/mở tài khoản nhân sự.
     * KHÔNG bao gồm việc tạo MANAGER hay ADMIN — chặn ở tầng service, xem
     * UserAdminServiceImpl.
     */
    public static final String STAFF_MANAGE = "STAFF_MANAGE";

    // ----- Duyệt hồ sơ Reader -----
    public static final String ADMIN_READERS_VIEW = "ADMIN_READERS_VIEW";
    public static final String ADMIN_READERS_REVIEW = "ADMIN_READERS_REVIEW";

    // ----- Chỉ ADMIN -----
    /** Thêm, sửa, ẩn sản phẩm và tồn kho. */
    public static final String CATALOG_MANAGE = "CATALOG_MANAGE";
    /** Xem mọi đơn hàng và đổi trạng thái đơn. */
    public static final String ORDERS_MANAGE = "ORDERS_MANAGE";
    /** Toàn quyền trên tài khoản, gồm cả gán MANAGER và ADMIN. */
    public static final String USERS_MANAGE = "USERS_MANAGE";
    /** Đọc nhật ký thao tác quản trị. */
    public static final String AUDIT_VIEW = "AUDIT_VIEW";

    // ----- Tiền -----
    /** Đối soát và xác nhận khoản khách chuyển. */
    public static final String PAYMENTS_MANAGE = "PAYMENTS_MANAGE";
    /** Reader xin rút tiền khỏi ký quỹ. Quản trị viên KHÔNG có: người duyệt không nên là người xin. */
    public static final String PAYOUT_REQUEST = "PAYOUT_REQUEST";
    /** Duyệt hoặc từ chối lệnh rút. */
    public static final String PAYOUT_REVIEW = "PAYOUT_REVIEW";

    /** Xử lý báo cáo vi phạm. */
    public static final String REPORT_REVIEW = "REPORT_REVIEW";

    // ----- Còn để lại vì đã có từ trước, chưa endpoint nào dùng -----
    public static final String ADMIN_MANAGE_AVAILABILITY = "ADMIN_MANAGE_AVAILABILITY";
    public static final String ADMIN_MANAGE_UNAVAILABLE = "ADMIN_MANAGE_UNAVAILABLE";
    public static final String ADMIN_VIEW_PUBLIC_READERS = "ADMIN_VIEW_PUBLIC_READERS";

    private SecurityPermissions() {}
}
