package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.user.ChangePasswordRequest;
import com.exe.astratarot.domain.dto.user.ProfileResponse;
import com.exe.astratarot.domain.dto.user.UpdateProfileRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.Gender;
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
            Path dir = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            deleteOldAvatarFile(user.getAvatar());
            user.setAvatar("/uploads/avatars/" + filename);
            log.info("Đã cập nhật avatar cho user {}", userId);
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
        user.setAvatar(null);
        return toResponse(userRepository.save(user));
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

    // ---------------------------------------------------------

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
    }

    /** Xoá file avatar cũ để thư mục upload không phình theo mỗi lần đổi ảnh. */
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
                .status(u.getStatus().name())
                .authProvider(u.getAuthProvider().name())
                .lastLoginAt(u.getLastLoginAt())
                .createdAt(u.getCreatedAt())
                .build();
    }
}
