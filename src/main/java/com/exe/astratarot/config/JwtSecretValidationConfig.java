package com.exe.astratarot.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class JwtSecretValidationConfig {
    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostConstruct
    public void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        if ("change-this-dev-secret-key-32-bytes-minimum".equals(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET must not use the development default");
        }
    }
}
