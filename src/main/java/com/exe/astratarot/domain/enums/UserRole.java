package com.exe.astratarot.domain.enums;

/**
 * Vai trò của một tài khoản có thật trong bảng users.
 *
 * <p>Cố ý KHÔNG có GUEST: khách chưa đăng nhập không có hàng nào trong users,
 * nên "guest" là trạng thái vắng mặt của tài khoản chứ không phải một vai trò.
 * Phía giao diện có khái niệm guest để quyết định hiển thị gì, còn ở đây thêm
 * GUEST vào enum sẽ đẻ ra tài khoản khách đăng nhập được — thứ không tồn tại.
 *
 * <p>READER: chuyên gia Tarot nhận booking từ khách.
 * STAFF: nhân viên hỗ trợ khách (không phải Reader).
 * MANAGER: quản lý nhân sự.
 * ADMIN: toàn quyền.
 */
public enum UserRole {
    /** Khách đã đăng ký: mua hàng, đặt lịch, xem bài, nộp hồ sơ làm Reader. */
    USER,

    /** Chuyên gia Tarot nhận booking từ khách. */
    READER,

    /** Nhân viên hỗ trợ khách (không phải Reader). */
    STAFF,

    /** Quản lý nhân sự: xem và điều chỉnh đội ngũ, duyệt hồ sơ Reader. */
    MANAGER,

    /** Toàn quyền: thêm cả sản phẩm, đơn hàng và phân quyền tài khoản. */
    ADMIN
}
