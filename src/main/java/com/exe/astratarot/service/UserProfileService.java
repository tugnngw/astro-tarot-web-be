package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.user.ChangePasswordRequest;
import com.exe.astratarot.domain.dto.user.ProfileResponse;
import com.exe.astratarot.domain.dto.user.UpdateProfileRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserProfileService {

    ProfileResponse getProfile(UUID userId);

    /** Chỉ ghi đè những trường được gửi lên (khác null). */
    ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    /** Lưu ảnh đại diện, trả về hồ sơ đã cập nhật đường dẫn avatar. */
    ProfileResponse updateAvatar(UUID userId, MultipartFile file);

    ProfileResponse removeAvatar(UUID userId);

    /** Đổi mật khẩu khi đang đăng nhập — phải nhập đúng mật khẩu hiện tại. */
    void changePassword(UUID userId, ChangePasswordRequest request);
}
