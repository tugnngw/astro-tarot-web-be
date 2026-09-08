package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.user.ChangePasswordRequest;
import com.exe.astratarot.domain.dto.user.ProfileResponse;
import com.exe.astratarot.domain.dto.user.UpdateProfileRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Hồ sơ của người đang đăng nhập.
 *
 * Mọi thao tác lấy userId từ token chứ không nhận từ đường dẫn — nếu để
 * /api/v1/users/{id} thì phải tự kiểm tra quyền ở mọi endpoint, sót một chỗ
 * là người này sửa được hồ sơ người khác.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                userProfileService.getProfile(currentUserId(userDetails))));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật hồ sơ",
                userProfileService.updateProfile(currentUserId(userDetails), request)));
    }

    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse<ProfileResponse>> uploadAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật ảnh đại diện",
                userProfileService.updateAvatar(currentUserId(userDetails), file)));
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<ApiResponse<ProfileResponse>> removeAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Đã gỡ ảnh đại diện",
                userProfileService.removeAvatar(currentUserId(userDetails))));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(currentUserId(userDetails), request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đổi mật khẩu thành công. Vui lòng đăng nhập lại.", null));
    }

    private UUID currentUserId(CustomUserDetails userDetails) {
        return userDetails.getUser().getId();
    }
}
