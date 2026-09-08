package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.admin.ManagedUserResponse;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Quản lý tài khoản cho MANAGER (trong phạm vi nhân sự) và ADMIN (toàn quyền). */
public interface UserAdminService {

    Page<ManagedUserResponse> list(UUID actorId, String role, String status, String keyword, Pageable pageable);

    ManagedUserResponse changeRole(UUID actorId, UUID targetId, UserRole newRole);

    ManagedUserResponse changeStatus(UUID actorId, UUID targetId, UserStatus newStatus);
}
