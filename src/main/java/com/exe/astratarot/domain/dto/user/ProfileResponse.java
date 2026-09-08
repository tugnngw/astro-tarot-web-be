package com.exe.astratarot.domain.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Hồ sơ đầy đủ của người đang đăng nhập.
 * Tách khỏi entity để không lỡ tay rò passwordHash hay các token ra ngoài.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private UUID id;
    /** Sinh tự động từ email lúc đăng ký, người dùng không sửa được. */
    private String username;
    /** Chỉ đổi được qua luồng xác minh email riêng, không sửa trực tiếp. */
    private String email;
    private Boolean emailVerified;
    private String fullName;
    private String phone;
    private String avatar;
    private String gender;
    private LocalDate dateOfBirth;
    private String bio;
    private String address;
    private String city;
    private String country;
    private String role;
    private String status;
    private String authProvider;
    private Instant lastLoginAt;
    private Instant createdAt;
}
