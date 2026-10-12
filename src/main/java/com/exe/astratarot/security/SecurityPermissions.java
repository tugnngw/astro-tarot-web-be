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
    /**
     * Những việc BẤT KỲ người đã đăng nhập nào cũng làm, bất kể vai trò.
     *
     * <p>Gồm: thông báo, hồ sơ của chính mình, phiếu hỗ trợ, báo cáo vi phạm,
     * và — quan trọng — trò chuyện cùng gọi video trong một buổi xem mà mình
     * là một bên. Reader cần đúng những thứ này để hành nghề, nên chúng KHÔNG
     * phải "chức năng của khách".
     */
    public static final String USER_BASIC = "USER_BASIC";

    /**
     * Những việc chỉ KHÁCH làm: đặt lịch, trả tiền buổi xem, đánh giá Reader,
     * mua hàng, hỏi Tarot AI.
     *
     * <p>Tách khỏi {@link #USER_BASIC} ngày 12/10/2026. Trước đó mọi vai trò
     * đều mang USER_BASIC, nên một tài khoản Nhân viên đặt được lịch với chính
     * đồng nghiệp mình và đánh giá họ. Ranh giới cũ gộp hai thứ khác hẳn nhau:
     * "đã đăng nhập" với "là khách hàng".
     *
     * <p>Cố ý KHÔNG trao cho STAFF, MANAGER lẫn ADMIN. Ba vai trò ấy vận hành
     * nền tảng; để họ vừa bán vừa mua là mở đường cho những chuyện khó giải
     * thích về sau — Reader tự đặt lịch với mình để tự đánh giá năm sao, Quản
     * trị viên tự cấp gói AI cho tài khoản của mình.
     *
     * <p>Cần một tài khoản vừa làm Reader vừa đi xem bài thì lập tài khoản
     * riêng, đó mới là cách phân tách đúng.
     */
    public static final String CUSTOMER_ACTIONS = "CUSTOMER_ACTIONS";

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

    // ----- Quản lý bài viết (Blog) -----
    /** Tạo và chỉnh sửa bài viết nháp. */
    public static final String BLOG_CREATE = "BLOG_CREATE";
    /** Duyệt, từ chối, xuất bản và xóa bài viết. */
    public static final String BLOG_REVIEW = "BLOG_REVIEW";

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
