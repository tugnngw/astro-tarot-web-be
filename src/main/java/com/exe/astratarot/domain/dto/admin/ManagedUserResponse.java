package com.exe.astratarot.domain.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Một tài khoản nhìn từ màn quản lý. Không có passwordHash, không có token. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedUserResponse {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String avatar;
    private String role;
    private String status;
    private Boolean emailVerified;
    private Instant lastLoginAt;
    private Instant createdAt;
    /**
     * TRUE khi người đang đăng nhập được phép sửa tài khoản này. Tính ở BE để
     * giao diện không phải đoán lại luật phân quyền và đoán sai.
     */
    private Boolean editable;
}
