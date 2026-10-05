package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.user.ChangePasswordRequest;
import com.exe.astratarot.domain.dto.user.DeleteAccountRequest;
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

    /**
     * Người dùng tự xoá tài khoản của mình.
     *
     * <p>CH Play và App Store đều BẮT BUỘC có chức năng này cho app cho phép
     * đăng ký (Apple 5.1.1(v), Google "Xoá dữ liệu tài khoản"). Trước đây chỉ
     * quản trị viên xoá được tài khoản người khác, chính chủ thì không có
     * đường nào.
     *
     * <p>Xoá MỀM và gỡ dữ liệu cá nhân, không xoá cứng hàng users: hoá đơn,
     * lịch hẹn và đánh giá đều tham chiếu tới nó, và lịch sử giao dịch là thứ
     * kế toán phải giữ. Chi tiết ở phần cài đặt.
     */
    void deleteOwnAccount(UUID userId, DeleteAccountRequest request);
}
