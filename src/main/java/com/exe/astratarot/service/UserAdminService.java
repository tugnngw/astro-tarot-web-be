package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.admin.CreateUserRequest;
import com.exe.astratarot.domain.dto.admin.ManagedUserDetailResponse;
import com.exe.astratarot.domain.dto.admin.ManagedUserResponse;
import com.exe.astratarot.domain.dto.admin.UpdateUserInfoRequest;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** Quản lý tài khoản cho MANAGER (trong phạm vi nhân sự) và ADMIN (toàn quyền). */
public interface UserAdminService {

    Page<ManagedUserResponse> list(UUID actorId, String role, String status, String keyword, Pageable pageable);

    /** Hồ sơ đầy đủ kèm số liệu hoạt động, dùng cho bảng chi tiết. */
    ManagedUserDetailResponse detail(UUID actorId, UUID targetId);

    /** Tạo tài khoản nhân sự trực tiếp, không bắt họ tự đăng ký rồi mới cất nhắc. */
    ManagedUserResponse create(UUID actorId, CreateUserRequest request);

    /** Sửa thông tin liên hệ. KHÔNG đụng tới vai trò, trạng thái hay mật khẩu. */
    ManagedUserResponse updateInfo(UUID actorId, UUID targetId, UpdateUserInfoRequest request);

    ManagedUserResponse changeRole(UUID actorId, UUID targetId, UserRole newRole);

    /** Đổi vai trò nhiều tài khoản cùng lúc. Sai một tài khoản thì hỏng cả lô. */
    List<ManagedUserResponse> changeRoleBulk(UUID actorId, List<UUID> targetIds, UserRole newRole);

    ManagedUserResponse changeStatus(UUID actorId, UUID targetId, UserStatus newStatus);

    /** Buộc đăng xuất khỏi mọi thiết bị, không đổi gì khác. */
    void revokeSessions(UUID actorId, UUID targetId);

    /** Gửi mail đặt lại mật khẩu. Quản trị viên không tự đặt mật khẩu hộ. */
    void sendPasswordReset(UUID actorId, UUID targetId);

    /** Gửi lại mail xác minh cho tài khoản chưa kích hoạt. */
    void resendVerification(UUID actorId, UUID targetId);

    /** Xoá mềm: đặt deleted_at, giữ nguyên đơn hàng và lịch sử đã phát sinh. */
    void softDelete(UUID actorId, UUID targetId);
}
