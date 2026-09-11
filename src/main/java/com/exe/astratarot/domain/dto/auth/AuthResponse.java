package com.exe.astratarot.domain.dto.auth;

import com.exe.astratarot.domain.enums.UserRole;

import java.util.List;
import java.util.UUID;

/**
 * @param permissions Danh sách quyền của vai trò, gửi kèm để giao diện biết
 *                    hiện menu nào mà không phải tự đoán từ tên role. Đây chỉ
 *                    là dữ liệu cho UI — mọi endpoint vẫn tự kiểm tra quyền,
 *                    sửa danh sách này ở trình duyệt không mở thêm được gì.
 * @param avatar      Ảnh đại diện. Thiếu trường này thì giao diện dựng đối
 *                    tượng người dùng từ phản hồi đăng nhập mà không có ảnh,
 *                    nên MỖI phiên đăng nhập mới đều hiện hình người xám cho
 *                    tới khi có lời gọi /api/v1/me nào đó. Người dùng thấy là
 *                    "đổi ảnh xong đăng nhập lại lại mất".
 */
public record AuthResponse(UUID userId, String username, String email, String fullName, UserRole role,
                           String avatar, List<String> permissions, String accessToken, String refreshToken,
                           Long expiresIn) {
}
