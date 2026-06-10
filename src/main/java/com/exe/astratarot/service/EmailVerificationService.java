package com.exe.astratarot.service;

import com.exe.astratarot.domain.entity.User;

public interface EmailVerificationService {
    void sendVerification(User user, String email);

    void verify(String token);
}
