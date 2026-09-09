package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.admin.CreateUserRequest;
import com.exe.astratarot.domain.dto.admin.ManagedUserDetailResponse;
import com.exe.astratarot.domain.dto.admin.ManagedUserResponse;
import com.exe.astratarot.domain.dto.admin.UpdateUserInfoRequest;
import com.exe.astratarot.domain.dto.auth.ForgotPasswordRequest;
import com.exe.astratarot.domain.dto.auth.ResendVerificationRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.AuthProvider;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.exception.EmailAlreadyExistsException;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.OrderRepository;
import com.exe.astratarot.repository.ReaderApplicationRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.security.AdminActions;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.AuthService;
import com.exe.astratarot.service.UserAdminService;
import com.exe.astratarot.service.UsernameGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Quản lý tài khoản.
 *
 * <p>Controller đã chặn theo quyền, nhưng phạm vi của MANAGER không diễn tả
 * được bằng một annotation: "được sửa nhân sự, không được đụng tới quản lý và
 * admin" phụ thuộc vào vai trò của người BỊ sửa, mà annotation thì chỉ nhìn
 * thấy người đang gọi. Nên luật đó nằm ở đây.
 *
 * <p>Mọi thao tác đổi dữ liệu đều ghi vào activity_logs. Đây là những thao tác
 * dễ gây tranh cãi nhất trong hệ thống — hạ quyền, khoá tài khoản, xoá người —
 * nên phải truy được ai làm, lúc nào, đổi từ gì sang gì.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final OrderRepository orderRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final ReaderApplicationRepository readerApplicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsernameGenerator usernameGenerator;
    private final ActivityLogService activityLogService;
    private final AuthService authService;
    private final com.exe.astratarot.service.NotificationService notificationService;

    private final SecureRandom secureRandom = new SecureRandom();

    /** Nhãn tiếng Việt cho thông báo gửi tới người bị đổi vai trò. */
    private static final java.util.Map<UserRole, String> ROLE_LABEL = java.util.Map.of(
            UserRole.USER, "Thành viên",
            UserRole.STAFF, "Nhân viên",
            UserRole.MANAGER, "Quản lý",
            UserRole.ADMIN, "Quản trị viên");

    /** Những vai trò MANAGER được phép chạm vào, ở cả hai đầu: trước và sau khi đổi. */
    private static final Set<UserRole> MANAGER_SCOPE = EnumSet.of(UserRole.USER, UserRole.STAFF);

    // =========================================================
    // Đọc
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ManagedUserResponse> list(UUID actorId, String role, String status, String keyword, Pageable pageable) {
        User actor = findUser(actorId);
        return userRepository
                .searchForAdmin(normalize(role), normalize(status), normalize(keyword), pageable)
                .map(u -> toResponse(u, actor));
    }

    @Override
    @Transactional(readOnly = true)
    public ManagedUserDetailResponse detail(UUID actorId, UUID targetId) {
        User actor = findUser(actorId);
        User u = findUser(targetId);

        return ManagedUserDetailResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phone(u.getPhone())
                .avatar(u.getAvatar())
                .gender(u.getGender() == null ? null : u.getGender().name())
                .dateOfBirth(u.getDateOfBirth())
                .bio(u.getBio())
                .address(u.getAddress())
                .city(u.getCity())
                .country(u.getCountry())
                .role(u.getRole().name())
                .status(u.getStatus().name())
                .emailVerified(u.getEmailVerified())
                .authProvider(u.getAuthProvider() == null ? null : u.getAuthProvider().name())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .permissions(CustomUserDetails.permissionsOf(u.getRole()))
                .editable(canEdit(actor, u))
                .activeSessions(userSessionRepository.countByUserIdAndRevokedFalse(u.getId()))
                .orderCount(orderRepository.countByUserId(u.getId()))
                .totalSpent(orderRepository.sumTotalAmountByUserId(u.getId()))
                .hasReaderProfile(readerProfileRepository.existsByUserId(u.getId()))
                .hasPendingReaderApplication(readerApplicationRepository.existsByUserIdAndStatus(
                        u.getId(), ReaderApplication.ApplicationStatus.PENDING))
                .build();
    }

    // =========================================================
    // Tạo và sửa
    // =========================================================

    @Override
    @Transactional
    public ManagedUserResponse create(UUID actorId, CreateUserRequest request) {
        User actor = findUser(actorId);
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        // Quản lý chỉ tạo được thành viên và nhân viên — cùng phạm vi với việc
        // đổi vai trò. Nếu không, tạo thẳng một tài khoản ADMIN mới là đường
        // vòng để tự nâng quyền.
        if (actor.getRole() == UserRole.MANAGER && !MANAGER_SCOPE.contains(request.getRole())) {
            throw new AccessDeniedException("Quản lý chỉ tạo được tài khoản Người dùng hoặc Nhân viên");
        }
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new EmailAlreadyExistsException("Email này đã được đăng ký");
        }

        boolean verified = Boolean.TRUE.equals(request.getMarkEmailVerified());
        String rawPassword = request.getTemporaryPassword() == null || request.getTemporaryPassword().isBlank()
                ? randomPassword()
                : request.getTemporaryPassword();

        User user = User.builder()
                .username(usernameGenerator.fromEmail(email))
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(request.getFullName().trim())
                .phone(request.getPhone())
                .role(request.getRole())
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .providerId(email)
                .emailVerified(verified)
                .build();
        userRepository.save(user);

        // Người mới không bao giờ nhận mật khẩu qua lời nói: hoặc họ tự xác minh
        // email, hoặc họ đi qua luồng quên mật khẩu. Cả hai đều dùng lại đúng
        // luồng đã có ở AuthService thay vì tự sinh token ở đây.
        if (verified) {
            authService.forgotPassword(new ForgotPasswordRequest(email));
        } else {
            authService.resendVerification(new ResendVerificationRequest(email));
        }

        activityLogService.record(actorId, AdminActions.USER_CREATE, AdminActions.ENTITY_USER, user.getId(),
                Map.of("email", email, "role", request.getRole().name(), "emailVerified", verified));

        log.info("{} đã tạo tài khoản {} với vai trò {}", actor.getUsername(), email, request.getRole());
        return toResponse(user, actor);
    }

    @Override
    @Transactional
    public ManagedUserResponse updateInfo(UUID actorId, UUID targetId, UpdateUserInfoRequest request) {
        User actor = findUser(actorId);
        User target = findUser(targetId);
        requireCanEdit(actor, target);

        Map<String, Object> changes = new LinkedHashMap<>();
        // Chỉ ghi đè trường nào thực sự được gửi lên: PATCH mà ghi đè cả trường
        // null sẽ xoá sạch dữ liệu người dùng chỉ vì giao diện không gửi đủ.
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            changes.put("fullName", Map.of("from", target.getFullName(), "to", request.getFullName().trim()));
            target.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            target.setPhone(request.getPhone().isBlank() ? null : request.getPhone().trim());
            changes.put("phone", target.getPhone());
        }
        if (request.getCity() != null) {
            target.setCity(request.getCity().isBlank() ? null : request.getCity().trim());
            changes.put("city", target.getCity());
        }
        if (request.getAddress() != null) {
            target.setAddress(request.getAddress().isBlank() ? null : request.getAddress().trim());
            changes.put("address", target.getAddress());
        }

        userRepository.save(target);
        if (!changes.isEmpty()) {
            activityLogService.record(actorId, AdminActions.USER_UPDATE, AdminActions.ENTITY_USER, targetId, changes);
        }
        return toResponse(target, actor);
    }

    // =========================================================
    // Vai trò và trạng thái
    // =========================================================

    @Override
    @Transactional
    public ManagedUserResponse changeRole(UUID actorId, UUID targetId, UserRole newRole) {
        User actor = findUser(actorId);
        return toResponse(applyRoleChange(actor, findUser(targetId), newRole), actor);
    }

    @Override
    @Transactional
    public List<ManagedUserResponse> changeRoleBulk(UUID actorId, List<UUID> targetIds, UserRole newRole) {
        User actor = findUser(actorId);
        // Cố ý KHÔNG bỏ qua tài khoản lỗi để chạy tiếp: một lô nửa thành công
        // nửa thất bại là trạng thái không ai kiểm lại được. Cả lô cùng nằm
        // trong một transaction nên sai một tài khoản là rollback toàn bộ.
        return targetIds.stream()
                .distinct()
                .map(id -> toResponse(applyRoleChange(actor, findUser(id), newRole), actor))
                .toList();
    }

    private User applyRoleChange(User actor, User target, UserRole newRole) {
        requireCanEdit(actor, target);

        if (actor.getRole() == UserRole.MANAGER && !MANAGER_SCOPE.contains(newRole)) {
            throw new AccessDeniedException("Quản lý chỉ được đặt vai trò Người dùng hoặc Nhân viên");
        }
        UserRole oldRole = target.getRole();
        if (oldRole == newRole) {
            return target;
        }
        // Lưới an toàn cho bất biến "luôn còn ít nhất một admin".
        //
        // Hiện tại nhánh này không chạy tới được: chỉ admin mới đụng được vào
        // admin, mà không ai sửa được chính mình, nên hễ target là ADMIN thì
        // trong hệ đã có tối thiểu hai admin. Giữ lại vì luật canEdit có thể
        // được nới ra sau này, và mất admin cuối cùng là hỏng không cứu được:
        // không còn ai cấp lại quyền admin cho bất kỳ ai.
        if (oldRole == UserRole.ADMIN && userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN) <= 1) {
            throw new AccessDeniedException("Không thể hạ vai trò của quản trị viên cuối cùng");
        }

        target.setRole(newRole);
        userRepository.save(target);
        revokeAllSessions(target.getId());

        activityLogService.record(actor.getId(), AdminActions.USER_ROLE_CHANGE, AdminActions.ENTITY_USER,
                target.getId(), Map.of("from", oldRole.name(), "to", newRole.name()));

        // Người bị đổi vai trò cũng vừa bị đăng xuất khỏi mọi thiết bị. Không
        // báo thì họ chỉ thấy mình bị đá ra mà không hiểu vì sao.
        notificationService.push(target,
                com.exe.astratarot.service.NotificationTypes.ACCOUNT_ROLE_CHANGED,
                "Vai trò tài khoản đã thay đổi",
                "Tài khoản của bạn được chuyển sang vai trò " + ROLE_LABEL.getOrDefault(newRole, newRole.name())
                        + ". Bạn cần đăng nhập lại.",
                Map.of("from", oldRole.name(), "to", newRole.name()));
        return target;
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

        UserStatus oldStatus = target.getStatus();
        target.setStatus(newStatus);
        userRepository.save(target);

        if (newStatus != UserStatus.ACTIVE) {
            revokeAllSessions(target.getId());
        }
        activityLogService.record(actorId, AdminActions.USER_STATUS_CHANGE, AdminActions.ENTITY_USER, targetId,
                Map.of("from", oldStatus.name(), "to", newStatus.name()));
        return toResponse(target, actor);
    }

    // =========================================================
    // Phiên đăng nhập, mật khẩu, xoá
    // =========================================================

    @Override
    @Transactional
    public void revokeSessions(UUID actorId, UUID targetId) {
        User actor = findUser(actorId);
        User target = findUser(targetId);
        requireCanEdit(actor, target);

        revokeAllSessions(targetId);
        activityLogService.record(actorId, AdminActions.USER_SESSIONS_REVOKE, AdminActions.ENTITY_USER, targetId, null);
    }

    @Override
    @Transactional
    public void sendPasswordReset(UUID actorId, UUID targetId) {
        User actor = findUser(actorId);
        User target = findUser(targetId);
        requireCanEdit(actor, target);

        if (target.getEmail() == null || target.getEmail().isBlank()) {
            throw new IllegalArgumentException("Tài khoản này không có email nên không gửi được liên kết");
        }
        // Gửi link chứ không đặt mật khẩu hộ: quản trị viên biết mật khẩu của
        // người khác là mất hẳn khả năng quy trách nhiệm cho thao tác đăng nhập.
        authService.forgotPassword(new ForgotPasswordRequest(target.getEmail()));
        activityLogService.record(actorId, AdminActions.USER_PASSWORD_RESET_SENT,
                AdminActions.ENTITY_USER, targetId, null);
    }

    @Override
    @Transactional
    public void resendVerification(UUID actorId, UUID targetId) {
        User actor = findUser(actorId);
        User target = findUser(targetId);
        requireCanEdit(actor, target);

        if (Boolean.TRUE.equals(target.getEmailVerified())) {
            throw new IllegalArgumentException("Tài khoản này đã xác minh email rồi");
        }
        authService.resendVerification(new ResendVerificationRequest(target.getEmail()));
        activityLogService.record(actorId, AdminActions.USER_VERIFICATION_RESENT,
                AdminActions.ENTITY_USER, targetId, null);
    }

    @Override
    @Transactional
    public void softDelete(UUID actorId, UUID targetId) {
        User actor = findUser(actorId);
        User target = findUser(targetId);
        requireCanEdit(actor, target);

        // Chỉ quản trị viên được xoá. Quản lý sửa được nhân sự nhưng xoá tài
        // khoản là chuyện khác hẳn: nó kéo theo đơn hàng, lịch hẹn, đánh giá.
        if (actor.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Chỉ quản trị viên mới xoá được tài khoản");
        }
        if (target.getRole() == UserRole.ADMIN
                && userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN) <= 1) {
            throw new AccessDeniedException("Không thể xoá quản trị viên cuối cùng");
        }

        // Xoá MỀM: đơn hàng, đánh giá và lịch sử của người này vẫn tham chiếu
        // tới hàng users. Xoá cứng sẽ làm gãy khoá ngoại hoặc mất luôn lịch sử
        // giao dịch mà kế toán cần giữ.
        target.setDeletedAt(java.time.Instant.now());
        target.setStatus(UserStatus.INACTIVE);
        userRepository.save(target);
        revokeAllSessions(targetId);

        activityLogService.record(actorId, AdminActions.USER_DELETE, AdminActions.ENTITY_USER, targetId,
                Map.of("email", String.valueOf(target.getEmail()), "role", target.getRole().name()));
        log.info("{} đã xoá mềm tài khoản {}", actor.getUsername(), target.getUsername());
    }

    // =========================================================
    // Luật phân quyền
    // =========================================================

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

    // =========================================================
    // Tiện ích
    // =========================================================

    /**
     * Thu hồi mọi phiên còn hiệu lực của một tài khoản.
     *
     * Quyền nằm trong access token đã phát, nên nếu không thu hồi thì người vừa
     * bị hạ quyền hoặc bị khoá vẫn dùng được token cũ tới khi nó hết hạn.
     */
    private void revokeAllSessions(UUID userId) {
        userSessionRepository.findByUserIdAndRevokedFalse(userId)
                .forEach(session -> session.setRevoked(true));
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

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
