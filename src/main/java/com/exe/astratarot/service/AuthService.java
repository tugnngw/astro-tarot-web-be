package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.auth.ForgotPasswordRequest;
import com.exe.astratarot.domain.dto.auth.LoginRequest;
import com.exe.astratarot.domain.dto.auth.LogoutRequest;
import com.exe.astratarot.domain.dto.auth.RefreshTokenRequest;
import com.exe.astratarot.domain.dto.auth.RegisterRequest;
import com.exe.astratarot.domain.dto.auth.RegisterResponse;
import com.exe.astratarot.domain.dto.auth.ResendVerificationRequest;
import com.exe.astratarot.domain.dto.auth.ResetPasswordRequest;
import com.exe.astratarot.domain.dto.auth.AuthResponse;

public interface AuthService {

    /**
     * Tạo tài khoản và gửi mail xác minh.
     * KHÔNG trả token: tài khoản chỉ đăng nhập được sau khi bấm link trong mail.
     */
    RegisterResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);

    void logout(LogoutRequest request);

    /** Xác minh email bằng token trong link. Trả về email vừa được xác minh. */
    String verifyEmail(String token);

    /** Gửi lại mail xác minh cho tài khoản chưa kích hoạt. */
    void resendVerification(ResendVerificationRequest request);

    /** Gửi link đặt lại mật khẩu. */
    void forgotPassword(ForgotPasswordRequest request);

    /** Đặt mật khẩu mới bằng token trong link. */
    void resetPassword(ResetPasswordRequest request);
}
