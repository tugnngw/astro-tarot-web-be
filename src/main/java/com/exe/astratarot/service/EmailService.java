package com.exe.astratarot.service;

public interface EmailService {
    void sendEmailVerification(String to, String verificationLink);
}
