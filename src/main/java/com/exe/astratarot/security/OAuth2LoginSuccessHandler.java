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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final com.exe.astratarot.service.TaiKhoanGoogleService taiKhoanGoogleService;
    private final UserRepository userRepository;
    private final OAuthExchangeCodeService oAuthExchangeCodeService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Google đã xác thực xong và trả người dùng về.
     *
     * <h2>Vì sao bọc toàn bộ trong try/catch</h2>
     *
     * Chỗ này chạy BÊN TRONG chuỗi bộ lọc, sau khi xác thực đã thành công.
     * {@code failureHandler} của {@code oauth2Login} chỉ bắt
     * {@code AuthenticationException} ném ra TRƯỚC đó, nên bất cứ lỗi nào ở
     * đây đều chui thẳng lên servlet container: người dùng nhận một trang
     * "HTTP Status 500 – Internal Server Error" trần của Tomcat, trên tên
     * miền api, không có nút nào quay lại.
     *
     * <p>Đã xảy ra thật: một lỗi khi nối tài khoản làm mọi lần bấm nút Google
     * ra 500. Trang ấy không nói được gì, và vì nó nằm ở tầng container nên
     * cũng không vào log của ứng dụng theo cách dễ tìm.
     *
     * <p>Nên quy tắc ở đây là: KHÔNG lỗi nào được phép thoát ra. Hỏng thì ghi
     * log đầy đủ rồi đưa người dùng về trang chủ kèm mã lỗi, đúng như
     * {@code failureHandler} vẫn làm — một chỗ hỏng, một kiểu trả lời.
     */
    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String redirectUrl;
        try {
            OAuth2User principal = (OAuth2User) authentication.getPrincipal();
            User user = findOrCreateGoogleUser(principal);
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            String code = oAuthExchangeCodeService.createCode(user.getId());
            redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                    .path("/oauth-success")
                    .queryParam("code", code)
                    .build()
                    .toUriString();
        } catch (RuntimeException e) {
            log.error("Đăng nhập Google hỏng sau khi Google đã xác thực xong", e);
            redirectUrl = trimSlash(frontendUrl) + "/?dang-nhap=google-loi";
        }
        response.sendRedirect(redirectUrl);
    }

    /** `https://x.vn/` + `/?...` sẽ thành hai dấu gạch chéo. */
    private static String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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
