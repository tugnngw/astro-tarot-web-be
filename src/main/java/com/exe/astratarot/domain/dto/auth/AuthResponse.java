package com.exe.astratarot.domain.dto.auth;

import com.exe.astratarot.domain.enums.UserRole;

import java.util.UUID;

public record AuthResponse(UUID userId, String username, String email, String fullName, UserRole role, String accessToken, String refreshToken, Long expiresIn) {
}
