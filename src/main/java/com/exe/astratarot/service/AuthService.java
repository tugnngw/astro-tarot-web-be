package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.auth.*;

public interface AuthService {
    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(LogoutRequest request);
}
