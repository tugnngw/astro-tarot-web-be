package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.user.ChangePasswordRequest;
import com.exe.astratarot.domain.dto.user.DeleteAccountRequest;
import com.exe.astratarot.domain.dto.user.ProfileResponse;
import com.exe.astratarot.domain.dto.user.UpdateProfileRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.Gender;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.exception.InvalidCredentialsException;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    /** Chỉ nhận ảnh, và chỉ những định dạng trình duyệt nào cũng hiển thị được. */
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024; // 2MB

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;

    private final com.exe.astratarot.repository.UserAvatarRepository userAvatarRepository;
    private final com.exe.astratarot.repository.UserAstrologicalDataRepository
            userAstrologicalDataRepository;

    // Giu lai de doc anh cu con sot tren dia (neu co); anh moi khong dung toi.
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        return toResponse(findUser(userId));
    }

    @Override
    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUser(userId);

        // Chỉ ghi đè trường nào thực sự được gửi lên. Nếu gán tuốt thì một form
        // chỉ sửa số điện thoại sẽ xoá trắng bio, địa chỉ... của người dùng.
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(blankToNull(request.phone()));
        }
        if (request.gender() != null && !request.gender().isBlank()) {
            user.setGender(Gender.valueOf(request.gender()));
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.bio() != null) {
            user.setBio(blankToNull(request.bio()));
        }
        if (request.address() != null) {
            user.setAddress(blankToNull(request.address()));
        }
        if (request.city() != null) {
            user.setCity(blankToNull(request.city()));
        }
        if (request.country() != null) {
            user.setCountry(blankToNull(request.country()));
        }

        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public ProfileResponse updateAvatar(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Chưa chọn ảnh");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Ảnh không được lớn hơn 2MB");
        }

        // Tin content-type do client gửi là không đủ, nhưng ở đây kết hợp với
        // việc chỉ cho phép một tập đuôi cố định và luôn tự đặt lại tên file
        // nên không thể ghi đè file khác hay tạo file .jsp/.html chạy được.
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Chỉ nhận ảnh JPEG, PNG, WEBP hoặc GIF");
        }

        User user = findUser(userId);
        String extension = switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };

        // Tên file do server đặt hoàn toàn: bỏ qua tên gốc của client để tránh
        // path traversal kiểu "../../application.properties".
        String filename = userId + "_" + System.currentTimeMillis() + extension;

        try {
            // Luu thang vao CSDL. Ghi ra thu muc trong container thi moi lan
            // deploy la mat, vi dia cua Render o goi free la tam — da xay ra
            // that: users.avatar tro toi mot file tra ve 404.
            byte[] bytes = file.getBytes();
            userAvatarRepository.save(com.exe.astratarot.domain.entity.UserAvatar.builder()
                    .userId(userId)
                    .contentType(contentType.toLowerCase(Locale.ROOT))
                    .data(bytes)
                    .build());

            deleteOldAvatarFile(user.getAvatar());
            // Dinh kem moc thoi gian de trinh duyet khong dung lai anh cu trong
            // cache sau khi doi anh — duong dan khong doi thi anh cu nam lai.
            user.setAvatar(avatarUrlFor(userId, System.currentTimeMillis()));
            log.info("Đã cập nhật avatar cho user {} ({} byte)", userId, bytes.length);
        } catch (IOException e) {
            log.error("Lưu avatar cho user {} thất bại", userId, e);
            throw new IllegalStateException("Không lưu được ảnh, thử lại sau");
        }

        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public ProfileResponse removeAvatar(UUID userId) {
        User user = findUser(userId);
        deleteOldAvatarFile(user.getAvatar());
        userAvatarRepository.deleteById(userId);
        user.setAvatar(null);
        return toResponse(userRepository.save(user));
    }

    /** Duong dan cong khai cua anh, kem moc thoi gian de pha cache trinh duyet. */
    public static String avatarUrlFor(UUID userId, long version) {
        return "/api/v1/users/" + userId + "/avatar?v=" + version;
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUser(userId);

        if (user.getPasswordHash() == null) {
            // Tài khoản đăng nhập bằng Google chưa từng đặt mật khẩu.
            throw new InvalidCredentialsException(
                    "Tài khoản này đăng nhập bằng Google. Hãy dùng chức năng quên mật khẩu để đặt mật khẩu mới.");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Thu hồi các phiên khác để thiết bị lạ bị đá ra. Phiên hiện tại cũng
        // bị thu hồi — giao diện sẽ yêu cầu đăng nhập lại, đó là hành vi an
        // toàn và dễ hiểu hơn là cố giữ lại đúng một phiên.
        userSessionRepository.findByUserIdAndRevokedFalse(userId)
                .forEach(session -> session.setRevoked(true));

        log.info("User {} đã đổi mật khẩu, đã thu hồi các phiên đang mở", userId);
    }

    // =========================================================
    // Tự xoá tài khoản
    // =========================================================

    /**
     * Tên miền dành riêng cho địa chỉ không bao giờ tồn tại (RFC 2606).
     *
     * <p>Dùng nó thay vì xoá trắng ô email: cột email có ràng buộc NOT NULL và
     * UNIQUE, mà để trống thì người thứ hai xoá tài khoản sẽ đụng khoá trùng.
     * Ghép thêm id người dùng là chắc chắn không trùng.
     *
     * <p>Và chọn tên miền KHÔNG BAO GIỜ gửi được thư, phòng trường hợp một tác
     * vụ gửi thư hàng loạt nào đó về sau quên lọc tài khoản đã xoá.
     */
    private static final String MIEN_DA_XOA = "@da-xoa.invalid";

    @Override
    @Transactional
    public void deleteOwnAccount(UUID userId, DeleteAccountRequest request) {
        User user = findUser(userId);

        if (user.getPasswordHash() == null) {
            throw new InvalidCredentialsException(
                    "Tài khoản này đăng nhập bằng Google. Hãy đặt mật khẩu qua chức năng quên mật khẩu rồi xoá.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Mật khẩu không đúng");
        }

        // Cùng chốt chặn với bản xoá của quản trị viên: mất quản trị viên cuối
        // cùng là không ai vào được màn quản trị nữa, kể cả để tạo lại một
        // người khác.
        if (user.getRole() == UserRole.ADMIN
                && userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN) <= 1) {
            throw new IllegalArgumentException(
                    "Bạn là quản trị viên duy nhất. Hãy cấp quyền quản trị cho người khác trước khi xoá tài khoản.");
        }

        // ---------------------------------------------------------------
        // Ranh giới giữa xoá cứng và xoá mềm
        // ---------------------------------------------------------------
        // Một quy tắc duy nhất: GIỮ thứ mà bản ghi khác phụ thuộc vào hoặc sổ
        // sách cần, GỠ thứ chỉ thuộc về riêng người này.
        //
        // Giữ (xoá mềm):
        //   • hàng users — bookings, reviews, payment_transactions và
        //     wallet_transactions đều trỏ tới nó. Xoá cứng thì hoặc gãy khoá
        //     ngoại, hoặc xoá dây chuyền luôn lịch sử giao dịch.
        //   • lịch hẹn, đánh giá, giao dịch — chúng là lịch sử của CẢ hai
        //     phía. Xoá một đánh giá là lặng lẽ đổi điểm trung bình của một
        //     Reader không liên quan gì tới quyết định này.
        //
        // Gỡ hẳn:
        //   • hồ sơ chiêm tinh — ngày sinh, GIỜ sinh, nơi sinh kèm toạ độ. Đây
        //     là dữ liệu định danh mạnh nhất app nắm giữ, không bản ghi nào
        //     khác trỏ tới, và chẳng dính gì tới sổ sách. Giữ lại chính là giữ
        //     đúng thứ mà một yêu cầu xoá tài khoản nhắm tới.
        //   • ảnh đại diện — ảnh mặt người. Cùng lý do.
        //
        // Và gỡ thông tin cá nhân ngay trên hàng users được giữ lại. Giữ hàng
        // để khoá ngoại không gãy là một chuyện; giữ tên và email của người ta
        // trên đó lại là chuyện khác, không có lý do nào biện minh được.
        var hoSoSao = userAstrologicalDataRepository.findAllByUserId(userId);
        if (!hoSoSao.isEmpty()) {
            userAstrologicalDataRepository.deleteAll(hoSoSao);
        }
        // Xoá cả tệp trên đĩa lẫn hàng trong bảng, đúng như removeAvatar làm.
        deleteOldAvatarFile(user.getAvatar());
        userAvatarRepository.deleteById(userId);

        String dauVet = userId.toString().substring(0, 8);
        user.setEmail("da-xoa-" + userId + MIEN_DA_XOA);
        user.setUsername("da_xoa_" + dauVet);
        user.setFullName("Người dùng đã xoá");
        user.setPhone(null);
        user.setBio(null);
        user.setAddress(null);
        user.setCity(null);
        user.setCountry(null);
        user.setAvatar(null);
        user.setProviderId(null);
        user.setPendingEmail(null);
        // Xoá luôn mã xác minh và mã đặt lại mật khẩu: để lại là còn đường vào
        // một tài khoản đã xoá.
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        // Băm mật khẩu thay bằng chuỗi vô nghĩa, không phải null: null mang
        // nghĩa "tài khoản Google chưa đặt mật khẩu" ở chỗ khác trong mã.
        user.setPasswordHash("{noop}" + UUID.randomUUID());
        user.setStatus(UserStatus.INACTIVE);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);

        userSessionRepository.findByUserIdAndRevokedFalse(userId)
                .forEach(session -> session.setRevoked(true));

        log.info("User {} đã tự xoá tài khoản; đã gỡ dữ liệu cá nhân và {} hồ sơ chiêm tinh",
                userId, hoSoSao.size());
    }

    // ---------------------------------------------------------

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    /**
     * Xoá file avatar CŨ còn sót trên đĩa.
     *
     * Ảnh mới nằm trong CSDL nên hàm này chỉ còn tác dụng với những đường dẫn
     * /uploads/... từ trước. Giữ lại để dọn nốt, không phải đường đi chính.
     */
    private void deleteOldAvatarFile(String avatarPath) {
        if (avatarPath == null || !avatarPath.startsWith("/uploads/avatars/")) {
            return; // avatar từ OAuth là URL ngoài, không đụng tới
        }
        try {
            String name = Paths.get(avatarPath).getFileName().toString();
            Path dir = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
            Path old = dir.resolve(name).normalize();
            // Chốt lại: đường dẫn sau khi chuẩn hoá phải vẫn nằm trong thư mục avatars.
            if (old.startsWith(dir)) {
                Files.deleteIfExists(old);
            }
        } catch (IOException | RuntimeException e) {
            // Xoá file cũ hỏng thì cũng không được chặn việc đổi avatar.
            log.warn("Không xoá được avatar cũ {}: {}", avatarPath, e.getMessage());
        }
    }

    private String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProfileResponse toResponse(User u) {
        return ProfileResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .email(u.getEmail())
                .emailVerified(u.getEmailVerified())
                .fullName(u.getFullName())
                .phone(u.getPhone())
                .avatar(u.getAvatar())
                .gender(u.getGender() == null ? Gender.UNDISCLOSED.name() : u.getGender().name())
                .dateOfBirth(u.getDateOfBirth())
                .bio(u.getBio())
                .address(u.getAddress())
                .city(u.getCity())
                .country(u.getCountry())
                .role(u.getRole().name())
                .permissions(com.exe.astratarot.security.CustomUserDetails.permissionsOf(u.getRole()))
                .status(u.getStatus().name())
                .authProvider(u.getAuthProvider().name())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
