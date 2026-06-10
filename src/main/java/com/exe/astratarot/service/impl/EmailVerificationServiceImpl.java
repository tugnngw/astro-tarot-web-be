package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.EmailAlreadyExistsException;
import com.exe.astratarot.exception.InvalidTokenException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.EmailService;
import com.exe.astratarot.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void sendVerification(User user, String email) {

        String normalizedEmail = normalizeEmail(email);

        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> {
                    throw new EmailAlreadyExistsException("Email already exists");
                });

        String rawToken = generateRawToken();

        user.setPendingEmail(normalizedEmail);
        user.setEmailVerificationToken(hashToken(rawToken));
        user.setEmailVerificationExpiresAt(
                Instant.now().plus(24, ChronoUnit.HOURS)
        );

        userRepository.save(user);

        emailService.sendEmailVerification(
                normalizedEmail,
                buildVerificationLink(rawToken)
        );
    }

    @Override
    @Transactional
    public void verify(String token) {

        String hashedToken = hashToken(token);

        User user = userRepository
                .findByEmailVerificationToken(hashedToken)
                .orElseThrow(() ->
                        new InvalidTokenException("Invalid verification token")
                );

        if (user.getEmailVerificationExpiresAt() == null
                || user.getEmailVerificationExpiresAt().isBefore(Instant.now())) {

            throw new InvalidTokenException("Verification token expired");
        }

        if (user.getPendingEmail() == null) {
            throw new InvalidTokenException("Pending email not found");
        }

        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(
                        user.getPendingEmail()
                )
                .filter(existing ->
                        !existing.getId().equals(user.getId())
                )
                .ifPresent(existing -> {
                    throw new EmailAlreadyExistsException("Email already exists");
                });

        user.setEmail(user.getPendingEmail());
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());

        user.setPendingEmail(null);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);

        userRepository.save(user);
    }

    private String buildVerificationLink(String token) {
        return UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/verify-email")
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidTokenException("Email is required");
        }

        return email.trim().toLowerCase();
    }

    private String generateRawToken() {
        return UUID.randomUUID() + "." + UUID.randomUUID();
    }

    private String hashToken(String token) {
        return DigestUtils.sha256Hex(token);
    }
}