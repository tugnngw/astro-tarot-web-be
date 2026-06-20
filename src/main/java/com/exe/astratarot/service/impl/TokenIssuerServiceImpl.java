package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.auth.AuthResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserSession;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.security.JwtService;
import com.exe.astratarot.service.TokenIssuerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenIssuerServiceImpl implements TokenIssuerService {

    private final JwtService jwtService;
    private final UserSessionRepository userSessionRepository;

    @Override
    public AuthResponse issueTokens(User user) {
        log.debug("🔑 Issuing tokens for user: {}", user.getUsername());

        CustomUserDetails userDetails = new CustomUserDetails(user);

        // Tạo claims với role và userId
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId().toString());

        // Generate access token
        String accessToken = jwtService.generateToken(userDetails, claims);
        log.debug("✅ Access token generated");

        // Generate refresh token
        String refreshToken = UUID.randomUUID() + "." + UUID.randomUUID();
        log.debug("✅ Refresh token generated");

        // Lưu refresh token vào database
        userSessionRepository.save(UserSession.builder()
                .user(user)
                .refreshTokenHash(DigestUtils.sha256Hex(refreshToken))
                .expiredAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());
        log.debug("✅ User session saved");

        // Trả về AuthResponse (record)
        return new AuthResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                accessToken,
                refreshToken,
                jwtService.getExpiration()
        );
    }
}