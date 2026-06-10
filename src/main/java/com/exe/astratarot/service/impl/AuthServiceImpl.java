package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.auth.*;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserSession;
import com.exe.astratarot.domain.enums.AuthProvider;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.exception.AccountDeactivatedException;
import com.exe.astratarot.exception.InvalidCredentialsException;
import com.exe.astratarot.exception.InvalidTokenException;
import com.exe.astratarot.exception.TokenReusedException;
import com.exe.astratarot.exception.UsernameAlreadyExistsException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.service.AuthService;
import com.exe.astratarot.service.TokenIssuerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenIssuerService tokenIssuerService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        if (userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(username)) {
            throw new UsernameAlreadyExistsException("Username already exists");
        }

        String passwordHash = passwordEncoder.encode(require(request.password(), "Password is required"));
        User user = User.builder()
                .username(username)
                .passwordHash(passwordHash)
                .fullName(require(request.fullName(), "Full name is required"))
                .authProvider(AuthProvider.LOCAL)
                .providerId(username)
                .build();

        return tokenIssuerService.issueTokens(userRepository.save(user));
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, request.password()));
        } catch (DisabledException | LockedException e) {
            throw new AccountDeactivatedException("Account is disabled or locked");
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        User user = userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));
        user.setLastLoginAt(Instant.now());
        return tokenIssuerService.issueTokens(user);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        UserSession session = userSessionRepository.findByRefreshTokenHashAndRevokedFalse(hashToken(request.refreshToken()))
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));
        if (session.getExpiredAt().isBefore(Instant.now())) {
            session.setRevoked(true);
            throw new InvalidTokenException("Refresh token expired");
        }

        User user = session.getUser();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            session.setRevoked(true);
            throw new AccountDeactivatedException("Account is no longer active");
        }

        session.setRevoked(true);
        return tokenIssuerService.issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        userSessionRepository.findByRefreshTokenHashAndRevokedFalse(hashToken(request.refreshToken()))
                .ifPresent(session -> session.setRevoked(true));
    }

    private String normalizeUsername(String username) {
        return require(username, "Username is required").trim().toLowerCase();
    }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidCredentialsException(message);
        }
        return value;
    }

    private String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenReusedException("Refresh token is required");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
