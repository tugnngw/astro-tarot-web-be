package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.auth.AuthResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserSession;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.security.JwtService;
import com.exe.astratarot.service.TokenIssuerService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenIssuerServiceImpl implements TokenIssuerService {

    private final JwtService jwtService;
    private final UserSessionRepository userSessionRepository;

    @Override
    public AuthResponse issueTokens(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);

        String accessToken = jwtService.generateToken(userDetails, Map.of(
                "role", user.getRole().name(),
                "userId", user.getId().toString()
        ));

        String refreshToken = UUID.randomUUID() + "." + UUID.randomUUID();

        userSessionRepository.save(UserSession.builder()
                .user(user)
                .refreshTokenHash(DigestUtils.sha256Hex(refreshToken))
                .expiredAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

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
