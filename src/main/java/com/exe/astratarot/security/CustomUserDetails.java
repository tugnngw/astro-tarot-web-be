package com.exe.astratarot.security;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@AllArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final User user;

    /**
     * Bảng ánh xạ vai trò sang quyền — nguồn sự thật duy nhất của toàn hệ thống.
     * Muốn đổi một vai trò được làm gì thì sửa đúng ở đây, controller không cần
     * biết role nào tồn tại.
     *
     * <p>Vai trò cấp trên KHÔNG tự động thừa kế quyền của cấp dưới. Ví dụ
     * MANAGER không có SUPPORT_RESPOND: quản lý giám sát hàng chờ hỗ trợ chứ
     * không trực tiếp trả lời khách; và ADMIN không có READER_APPLY vì admin
     * không đi nộp hồ sơ xin làm Reader. Thừa kế tự động nghe gọn nhưng sẽ gán
     * cho cấp trên những quyền vô nghĩa với họ.
     */
    private static final Map<UserRole, List<String>> ROLE_PERMISSIONS = Map.of(
            UserRole.USER, List.of(
                    SecurityPermissions.USER_BASIC,
                    SecurityPermissions.READER_APPLY),

            // STAFF gánh cả hai vai: hỗ trợ khách và Reader nhận booking.
            UserRole.STAFF, List.of(
                    SecurityPermissions.USER_BASIC,
                    SecurityPermissions.READER_MANAGE_PROFILE,
                    SecurityPermissions.SUPPORT_VIEW,
                    SecurityPermissions.SUPPORT_RESPOND),

            UserRole.MANAGER, List.of(
                    SecurityPermissions.USER_BASIC,
                    SecurityPermissions.SUPPORT_VIEW,
                    SecurityPermissions.STAFF_VIEW,
                    SecurityPermissions.STAFF_MANAGE,
                    SecurityPermissions.ADMIN_READERS_VIEW,
                    SecurityPermissions.ADMIN_READERS_REVIEW),

            UserRole.ADMIN, List.of(
                    SecurityPermissions.USER_BASIC,
                    SecurityPermissions.READER_MANAGE_PROFILE,
                    SecurityPermissions.SUPPORT_VIEW,
                    SecurityPermissions.SUPPORT_RESPOND,
                    SecurityPermissions.STAFF_VIEW,
                    SecurityPermissions.STAFF_MANAGE,
                    SecurityPermissions.ADMIN_READERS_VIEW,
                    SecurityPermissions.ADMIN_READERS_REVIEW,
                    SecurityPermissions.CATALOG_MANAGE,
                    SecurityPermissions.ORDERS_MANAGE,
                    SecurityPermissions.USERS_MANAGE,
                    SecurityPermissions.AUDIT_VIEW));

    /** Quyền của một vai trò, dùng chung cho token và cho API trả về FE. */
    public static List<String> permissionsOf(UserRole role) {
        return ROLE_PERMISSIONS.getOrDefault(role, List.of(SecurityPermissions.USER_BASIC));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        // ROLE_* giữ lại cho những chỗ còn kiểm tra theo vai trò.
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        for (String permission : permissionsOf(user.getRole())) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.BANNED && user.getDeletedAt() == null;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null;
    }
}
