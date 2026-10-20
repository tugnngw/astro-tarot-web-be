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
    private final com.exe.astratarot.service.TaiKhoanGoogleService taiKhoanGoogleService;
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

    /**
     * Nối tài khoản Google — đẩy sang dịch vụ dùng chung với lối của app.
     *
     * <p>Trước đây quy tắc nối nằm ngay trong lớp này. Khi app có lối đăng
     * nhập Google riêng, để mỗi bên tự viết là có hai bản cài đặt của cùng
     * một quy tắc; sai lệch ở đó không báo lỗi, nó sinh ra TÀI KHOẢN TRÙNG.
     */
    private User findOrCreateGoogleUser(OAuth2User principal) {
        return taiKhoanGoogleService.timHoacTao(
                requireAttribute(principal, "sub"),
                principal.getAttribute("email"),
                Boolean.TRUE.equals(principal.getAttribute("email_verified")),
                principal.getAttribute("name"),
                principal.getAttribute("picture"));
    }



    private String requireAttribute(OAuth2User principal, String name) {
        Object value = principal.getAttribute(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Google " + name + " is required");
        }
        return value.toString();
    }
}
