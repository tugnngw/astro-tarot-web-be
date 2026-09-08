package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.admin.ManagedUserResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Quản lý tài khoản.
 *
 * <p>Controller đã chặn theo quyền, nhưng phạm vi của MANAGER không diễn tả
 * được bằng một annotation: "được sửa nhân sự, không được đụng tới quản lý và
 * admin" phụ thuộc vào vai trò của người BỊ sửa, mà annotation thì chỉ nhìn
 * thấy người đang gọi. Nên luật đó nằm ở đây.
 */
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    /** Những vai trò MANAGER được phép chạm vào, ở cả hai đầu: trước và sau khi đổi. */
    private static final Set<UserRole> MANAGER_SCOPE = EnumSet.of(UserRole.USER, UserRole.STAFF);

    @Override
    @Transactional(readOnly = true)
    public Page<ManagedUserResponse> list(UUID actorId, String role, String status, String keyword, Pageable pageable) {
        User actor = findUser(actorId);
        return userRepository
                .searchForAdmin(normalize(role), normalize(status), normalize(keyword), pageable)
                .map(u -> toResponse(u, actor));
    }

    @Override
    @Transactional
    public ManagedUserResponse changeRole(UUID actorId, UUID targetId, UserRole newRole) {
        User actor = findUser(actorId);
        User target = findUser(targetId);
        requireCanEdit(actor, target);

        if (actor.getRole() == UserRole.MANAGER && !MANAGER_SCOPE.contains(newRole)) {
            throw new AccessDeniedException("Quản lý chỉ được đặt vai trò Người dùng hoặc Nhân viên");
        }
        if (target.getRole() == newRole) {
            return toResponse(target, actor);
        }
        // Lưới an toàn cho bất biến "luôn còn ít nhất một admin".
        //
        // Hiện tại nhánh này không chạy tới được: chỉ admin mới đụng được vào
        // admin, mà không ai sửa được chính mình, nên hễ target là ADMIN thì
        // trong hệ đã có tối thiểu hai admin. Giữ lại vì luật canEdit có thể
        // được nới ra sau này, và mất admin cuối cùng là hỏng không cứu được:
        // không còn ai cấp lại quyền admin cho bất kỳ ai.
        if (target.getRole() == UserRole.ADMIN && userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN) <= 1) {
            throw new AccessDeniedException("Không thể hạ vai trò của quản trị viên cuối cùng");
        }

        target.setRole(newRole);
        userRepository.save(target);

        revokeSessions(target.getId());

        return toResponse(target, actor);
    }

    @Override
    @Transactional
    public ManagedUserResponse changeStatus(UUID actorId, UUID targetId, UserStatus newStatus) {
        User actor = findUser(actorId);
        User target = findUser(targetId);
        requireCanEdit(actor, target);

        // Cùng lưới an toàn như ở changeRole, và cũng chưa chạy tới được vì lý
        // do y hệt. Xem chú thích ở đó.
        if (newStatus != UserStatus.ACTIVE
                && target.getRole() == UserRole.ADMIN
                && userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN) <= 1) {
            throw new AccessDeniedException("Không thể khoá quản trị viên cuối cùng");
        }

        target.setStatus(newStatus);
        userRepository.save(target);

        if (newStatus != UserStatus.ACTIVE) {
            revokeSessions(target.getId());
        }

        return toResponse(target, actor);
    }

    /**
     * Thu hồi mọi phiên còn hiệu lực của một tài khoản.
     *
     * Quyền nằm trong access token đã phát, nên nếu không thu hồi thì người vừa
     * bị hạ quyền hoặc bị khoá vẫn dùng được token cũ tới khi nó hết hạn.
     */
    private void revokeSessions(UUID userId) {
        userSessionRepository.findByUserIdAndRevokedFalse(userId)
                .forEach(session -> session.setRevoked(true));
    }

    // ---------- Luật phân quyền ----------

    private static boolean canEdit(User actor, User target) {
        // Tự sửa vai trò hoặc tự khoá mình là cách nhanh nhất để mất quyền vào
        // trang quản trị mà không ai lấy lại được.
        if (actor.getId().equals(target.getId())) {
            return false;
        }
        return switch (actor.getRole()) {
            case ADMIN -> true;
            case MANAGER -> MANAGER_SCOPE.contains(target.getRole());
            default -> false;
        };
    }

    private static void requireCanEdit(User actor, User target) {
        if (!canEdit(actor, target)) {
            throw new AccessDeniedException(
                    actor.getId().equals(target.getId())
                            ? "Không thể tự thay đổi vai trò hoặc trạng thái của chính mình"
                            : "Không đủ quyền với tài khoản này");
        }
    }

    // ---------- Tiện ích ----------

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    /** Chuỗi rỗng là sentinel "không lọc" mà truy vấn đang chờ. */
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static ManagedUserResponse toResponse(User u, User actor) {
        return ManagedUserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .avatar(u.getAvatar())
                .role(u.getRole().name())
                .status(u.getStatus().name())
                .emailVerified(u.getEmailVerified())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .editable(canEdit(actor, u))
                .build();
    }
}
