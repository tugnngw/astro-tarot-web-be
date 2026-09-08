package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.auth.AuthResponse;
import com.exe.astratarot.domain.dto.auth.ForgotPasswordRequest;
import com.exe.astratarot.domain.dto.auth.LoginRequest;
import com.exe.astratarot.domain.dto.auth.LogoutRequest;
import com.exe.astratarot.domain.dto.auth.RefreshTokenRequest;
import com.exe.astratarot.domain.dto.auth.RegisterRequest;
import com.exe.astratarot.domain.dto.auth.RegisterResponse;
import com.exe.astratarot.domain.dto.auth.ResendVerificationRequest;
import com.exe.astratarot.domain.dto.auth.ResetPasswordRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserSession;
import com.exe.astratarot.domain.enums.AuthProvider;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.exception.AccountDeactivatedException;
import com.exe.astratarot.exception.EmailAlreadyExistsException;
import com.exe.astratarot.exception.EmailNotVerifiedException;
import com.exe.astratarot.exception.InvalidCredentialsException;
import com.exe.astratarot.exception.InvalidTokenException;
import com.exe.astratarot.exception.TokenReusedException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.service.AuthService;
import com.exe.astratarot.service.EmailService;
import com.exe.astratarot.service.TokenIssuerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Link xác minh email sống 24 giờ. */
    private static final int VERIFY_TTL_HOURS = 24;
    /** Link đặt lại mật khẩu chỉ sống 1 giờ — nó cấp quyền đổi mật khẩu nên
     *  cửa sổ tấn công phải hẹp hơn nhiều so với link xác minh. */
    private static final int RESET_TTL_HOURS = 1;

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenIssuerService tokenIssuerService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // =========================================================
    // Đăng ký
    // =========================================================

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new EmailAlreadyExistsException("Email này đã được đăng ký");
        }

        String rawToken = generateRawToken();

        User user = User.builder()
                .username(generateUsernameFrom(email))
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .authProvider(AuthProvider.LOCAL)
                .providerId(email)
                .emailVerified(false)
                .emailVerificationToken(hashToken(rawToken))
                .emailVerificationExpiresAt(Instant.now().plus(VERIFY_TTL_HOURS, ChronoUnit.HOURS))
                .build();

        userRepository.save(user);
        emailService.sendEmailVerification(email, buildLink("/verify-email", rawToken));

        log.info("Đã tạo tài khoản {} và gửi mail xác minh", email);
        return new RegisterResponse(email, true);
    }

    /**
     * Sinh username từ phần trước @ của email.
     *
     * Cột username là NOT NULL UNIQUE từ V1_1 và luồng OAuth vẫn đang dùng, nên
     * không bỏ được; nhưng người dùng không cần biết tới nó nữa. Nối thêm 8 ký
     * tự ngẫu nhiên để hai người có email khác nhau mà trùng phần đầu (ví dụ
     * an@a.com và an@b.com) không đụng nhau.
     */
    private String generateUsernameFrom(String email) {
        String base = email.split("@")[0]
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "");
        if (base.isEmpty()) {
            base = "user";
        }
        base = base.substring(0, Math.min(base.length(), 40));

        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = base + "_" + UUID.randomUUID().toString().substring(0, 8);
            if (!userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không sinh được username duy nhất");
    }

    // =========================================================
    // Đăng nhập
    // =========================================================

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new InvalidCredentialsException("Email hoặc mật khẩu không đúng"));

        // Kiểm tra xác minh TRƯỚC khi gọi authenticationManager: nếu để sau,
        // người chưa xác minh gõ sai mật khẩu sẽ nhận thông báo sai lệch.
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new EmailNotVerifiedException(
                    "Email chưa được xác minh. Vui lòng kiểm tra hộp thư để kích hoạt tài khoản.");
        }

        try {
            // CustomUserDetailsService nạp theo email nên principal ở đây là email.
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (DisabledException | LockedException e) {
            throw new AccountDeactivatedException("Tài khoản đang bị khoá hoặc vô hiệu hoá");
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException("Email hoặc mật khẩu không đúng");
        }

        user.setLastLoginAt(Instant.now());
        return tokenIssuerService.issueTokens(user);
    }

    // =========================================================
    // Xác minh email
    // =========================================================

    @Override
    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(hashToken(token))
                .orElseThrow(() -> new InvalidTokenException("Liên kết xác minh không hợp lệ"));

        if (user.getEmailVerificationExpiresAt() == null
                || user.getEmailVerificationExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Liên kết xác minh đã hết hạn");
        }

        // Luồng đổi email của người đang đăng nhập (/user/email/send-verification)
        // dùng CHUNG cột token này và để email mới ở pending_email. Nếu chỗ này
        // chỉ đánh dấu đã xác minh mà không chuyển pending_email sang email thì
        // người dùng bấm link xong vẫn giữ email cũ. Xử lý cả hai luồng ở đây.
        String pending = user.getPendingEmail();
        if (pending != null && !pending.isBlank()) {
            userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(pending)
                    .filter(other -> !other.getId().equals(user.getId()))
                    .ifPresent(other -> {
                        throw new EmailAlreadyExistsException(
                                "Email này đã được tài khoản khác sử dụng");
                    });
            user.setEmail(pending);
            user.setPendingEmail(null);
        }

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(Instant.now());
        // Xoá token ngay để link chỉ dùng được một lần.
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        userRepository.save(user);

        log.info("Đã xác minh email {}", user.getEmail());
        return user.getEmail();
    }

    @Override
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String email = normalizeEmail(request.email());

        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getEmailVerified())) {
                return; // đã xác minh rồi thì không gửi lại
            }
            String rawToken = generateRawToken();
            user.setEmailVerificationToken(hashToken(rawToken));
            user.setEmailVerificationExpiresAt(
                    Instant.now().plus(VERIFY_TTL_HOURS, ChronoUnit.HOURS));
            userRepository.save(user);
            emailService.sendEmailVerification(email, buildLink("/verify-email", rawToken));
        });
        // Không báo email có tồn tại hay không — xem chú thích ở forgotPassword.
    }

    // =========================================================
    // Quên / đặt lại mật khẩu
    // =========================================================

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());

        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).ifPresent(user -> {
            String rawToken = generateRawToken();
            user.setPasswordResetToken(hashToken(rawToken));
            user.setPasswordResetExpiresAt(
                    Instant.now().plus(RESET_TTL_HOURS, ChronoUnit.HOURS));
            userRepository.save(user);
            emailService.sendPasswordReset(email, buildLink("/reset-password", rawToken));
            log.info("Đã gửi link đặt lại mật khẩu tới {}", email);
        });

        // Cố ý KHÔNG ném lỗi khi email không tồn tại, và controller luôn trả
        // cùng một thông điệp. Nếu phân biệt "email không tồn tại" với "đã gửi"
        // thì trang quên mật khẩu trở thành công cụ dò xem ai có tài khoản ở đây.
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByPasswordResetToken(hashToken(request.token()))
                .orElseThrow(() -> new InvalidTokenException("Liên kết đặt lại mật khẩu không hợp lệ"));

        if (user.getPasswordResetExpiresAt() == null
                || user.getPasswordResetExpiresAt().isBefore(Instant.now())) {
            throw new InvalidTokenException("Liên kết đặt lại mật khẩu đã hết hạn");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);

        // Đổi mật khẩu thường là vì nghi bị chiếm tài khoản, nên thu hồi mọi
        // phiên đang mở để thiết bị lạ bị đá ra ngay.
        userSessionRepository.findByUserIdAndRevokedFalse(user.getId())
                .forEach(session -> session.setRevoked(true));

        userRepository.save(user);
        log.info("Đã đặt lại mật khẩu cho {} và thu hồi các phiên đang mở", user.getEmail());
    }

    // =========================================================
    // Phiên đăng nhập
    // =========================================================

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        UserSession session = userSessionRepository
                .findByRefreshTokenHashAndRevokedFalse(hashToken(request.refreshToken()))
                .orElseThrow(() -> new InvalidTokenException("Refresh token không hợp lệ"));

        if (session.getExpiredAt().isBefore(Instant.now())) {
            session.setRevoked(true);
            throw new InvalidTokenException("Refresh token đã hết hạn");
        }

        User user = session.getUser();
        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            session.setRevoked(true);
            throw new AccountDeactivatedException("Tài khoản không còn hoạt động");
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

    // =========================================================
    // Tiện ích
    // =========================================================

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidCredentialsException("Email là bắt buộc");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Token gốc gửi trong link; CSDL chỉ lưu bản băm của nó. */
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String buildLink(String path, String token) {
        return UriComponentsBuilder.fromUriString(frontendUrl)
                .path(path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenReusedException("Token là bắt buộc");
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
