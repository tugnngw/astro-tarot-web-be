package com.exe.astratarot.security;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.AuthProvider;
import com.exe.astratarot.exception.InvalidCredentialsException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.OAuthExchangeCodeService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final UserRepository userRepository;
    private final OAuthExchangeCodeService oAuthExchangeCodeService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        User user = findOrCreateGoogleUser(principal);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String code = oAuthExchangeCodeService.createCode(user.getId());
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/oauth-success")
                .queryParam("code", code)
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    private User findOrCreateGoogleUser(OAuth2User principal) {
        String providerId = requireAttribute(principal, "sub");
        if (!Boolean.TRUE.equals(principal.getAttribute("email_verified"))) {
            throw new IllegalArgumentException("Google email must be verified");
        }
        String email = normalizeEmail(principal.getAttribute("email"));
        String fullName = normalizeFullName(principal.getAttribute("name"), email);
        String avatar = principal.getAttribute("picture");

        return userRepository.findByAuthProviderAndProviderIdAndDeletedAtIsNull(AuthProvider.GOOGLE, providerId)
                .map(user -> updateGoogleUser(user, email, fullName, avatar, providerId))
                .orElseGet(() -> linkOrCreateGoogleUser(email, fullName, avatar, providerId));
    }

    private User linkOrCreateGoogleUser(String email, String fullName, String avatar, String providerId) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .map(user -> {
                    if (user.getAuthProvider() != AuthProvider.GOOGLE) {
                        throw new InvalidCredentialsException("Email already registered with another login method");
                    }
                    return updateGoogleUser(user, email, fullName, avatar, providerId);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(generateUniqueUsername(email))
                        .email(email)
                        .fullName(fullName)
                        .avatar(avatar)
                        .authProvider(AuthProvider.GOOGLE)
                        .providerId(providerId)
                        .emailVerified(true)
                        .emailVerifiedAt(Instant.now())
                        .build()));
    }

    private User updateGoogleUser(User user, String email, String fullName, String avatar, String providerId) {
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setProviderId(providerId);
        user.setEmail(email);
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(user.getEmailVerifiedAt() == null ? Instant.now() : user.getEmailVerifiedAt());
        user.setFullName(user.getFullName() == null || user.getFullName().isBlank() ? fullName : user.getFullName());
        user.setAvatar(user.getAvatar() == null || user.getAvatar().isBlank() ? avatar : user.getAvatar());
        user.setPendingEmail(null);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        return userRepository.save(user);
    }

    private String generateUniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@'))
                .replaceAll("[^a-zA-Z0-9_]", "_")
                .toLowerCase();
        if (base.isBlank()) {
            base = "google_user";
        }

        String username = base;
        while (userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(username)) {
            username = base + "_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return username;
    }

    private String normalizeEmail(Object email) {
        if (email == null || email.toString().isBlank()) {
            throw new IllegalArgumentException("Google email is required");
        }
        return email.toString().trim().toLowerCase();
    }

    private String normalizeFullName(Object name, String email) {
        return name == null || name.toString().isBlank() ? email : name.toString().trim();
    }

    private String requireAttribute(OAuth2User principal, String name) {
        Object value = principal.getAttribute(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Google " + name + " is required");
        }
        return value.toString();
    }
}
