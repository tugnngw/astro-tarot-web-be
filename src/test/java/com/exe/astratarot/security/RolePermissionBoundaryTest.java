package com.exe.astratarot.security;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ranh giới giữa "đã đăng nhập" và "là khách hàng".
 *
 * <p>Trước 12/10/2026 mọi vai trò đều mang {@code USER_BASIC}, nên một tài
 * khoản Nhân viên đặt được lịch với chính đồng nghiệp mình rồi đánh giá họ, và
 * Quản trị viên tự cấp gói AI cho tài khoản của mình.
 *
 * <p>Bộ test này khoá cả HAI chiều, vì sai chiều nào cũng hỏng:
 *
 * <ul>
 *   <li>Quá lỏng — vai trò vận hành lại mua bán được như khách.
 *   <li>Quá chặt — Reader mất {@code USER_BASIC} thì không trò chuyện hay gọi
 *       video với khách được nữa, tức là không hành nghề được. Chiều này nguy
 *       hiểm hơn vì nó phá đúng việc chính của sản phẩm.
 * </ul>
 */
class RolePermissionBoundaryTest {

    private Set<String> quyenCua(UserRole role) {
        User u = new User();
        u.setRole(role);
        return new CustomUserDetails(u).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Chỉ Thành viên mới có quyền của khách")
    void chiThanhVienCoQuyenKhach() {
        assertAll(
                () -> assertTrue(quyenCua(UserRole.USER)
                        .contains(SecurityPermissions.CUSTOMER_ACTIONS)),
                // Ba vai trò vận hành: để họ vừa bán vừa mua là mở đường cho
                // Reader tự đặt lịch với mình rồi tự đánh giá năm sao.
                () -> assertFalse(quyenCua(UserRole.STAFF)
                        .contains(SecurityPermissions.CUSTOMER_ACTIONS)),
                () -> assertFalse(quyenCua(UserRole.MANAGER)
                        .contains(SecurityPermissions.CUSTOMER_ACTIONS)),
                () -> assertFalse(quyenCua(UserRole.ADMIN)
                        .contains(SecurityPermissions.CUSTOMER_ACTIONS)));
    }

    @Test
    @DisplayName("MỌI vai trò vẫn giữ USER_BASIC — Reader cần nó để hành nghề")
    void moiVaiTroVanGiuUserBasic() {
        // USER_BASIC nay gác: trò chuyện trong buổi xem, cấu hình gọi video,
        // thông báo, phiếu hỗ trợ, xem một buổi mình là một bên, huỷ buổi, báo
        // cáo vi phạm. Gỡ nó khỏi STAFF là Reader ngồi nhìn khách mà không nói
        // được câu nào.
        for (UserRole r : UserRole.values()) {
            assertTrue(quyenCua(r).contains(SecurityPermissions.USER_BASIC),
                    "Vai trò " + r + " mất USER_BASIC");
        }
    }

    @Test
    @DisplayName("Reader vẫn giữ đủ quyền làm việc")
    void readerVanLamViecDuoc() {
        Set<String> staff = quyenCua(UserRole.STAFF);
        assertAll(
                () -> assertTrue(staff.contains(SecurityPermissions.READER_MANAGE_PROFILE)),
                () -> assertTrue(staff.contains(SecurityPermissions.PAYOUT_REQUEST)),
                () -> assertTrue(staff.contains(SecurityPermissions.SUPPORT_RESPOND)),
                () -> assertTrue(staff.contains(SecurityPermissions.BLOG_CREATE)));
    }

    @Test
    @DisplayName("Khách không chạm được việc vận hành")
    void khachKhongChamViecVanHanh() {
        Set<String> user = quyenCua(UserRole.USER);
        assertAll(
                () -> assertFalse(user.contains(SecurityPermissions.USERS_MANAGE)),
                () -> assertFalse(user.contains(SecurityPermissions.PAYMENTS_MANAGE)),
                () -> assertFalse(user.contains(SecurityPermissions.BLOG_REVIEW)),
                () -> assertFalse(user.contains(SecurityPermissions.READER_MANAGE_PROFILE)));
    }
}
