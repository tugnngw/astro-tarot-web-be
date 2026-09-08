package com.exe.astratarot.service;

public interface EmailService {
    void sendEmailVerification(String to, String verificationLink);

    /** Link đặt lại mật khẩu, hạn dùng ngắn (xem PasswordResetService). */
    void sendPasswordReset(String to, String resetLink);
}
