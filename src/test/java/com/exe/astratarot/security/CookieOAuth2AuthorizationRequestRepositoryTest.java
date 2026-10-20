package com.exe.astratarot.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giữ yêu cầu uỷ quyền giữa hai chặng của luồng Google.
 *
 * <p>Đây là chỗ hỏng mà không ai nhìn thấy cho tới bước cuối: nút bấm được,
 * Google hỏi quyền xong xuôi, rồi người dùng rơi vào trang lỗi
 * {@code authorization_request_not_found}. Máy chủ chạy
 * {@code SessionCreationPolicy.STATELESS} nên bản mặc định (nhớ bằng
 * {@code HttpSession}) không dùng được — và đó đúng là lý do đăng nhập Google
 * từng bị tắt.
 */
@DisplayName("CookieOAuth2AuthorizationRequestRepository")
class CookieOAuth2AuthorizationRequestRepositoryTest {

    private final CookieOAuth2AuthorizationRequestRepository kho =
            new CookieOAuth2AuthorizationRequestRepository();

    private static OAuth2AuthorizationRequest yeuCau(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id-thu")
                .redirectUri("https://api.astrotarot.date/login/oauth2/code/google")
                .scopes(java.util.Set.of("openid", "email", "profile"))
                .state(state)
                .build();
    }

    private static MockHttpServletRequest kemCookie(MockHttpServletResponse res) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(res.getCookies());
        return req;
    }

    @Test
    @DisplayName("lưu rồi đọc lại ra đúng yêu cầu, không cần phiên")
    void luuRoiDocLai() {
        MockHttpServletResponse res = new MockHttpServletResponse();
        kho.saveAuthorizationRequest(yeuCau("state-abc"), new MockHttpServletRequest(), res);

        OAuth2AuthorizationRequest doc = kho.loadAuthorizationRequest(kemCookie(res));

        assertThat(doc).isNotNull();
        // `state` là mảnh chống giả mạo yêu cầu — mất nó là cả luồng vô nghĩa.
        assertThat(doc.getState()).isEqualTo("state-abc");
        assertThat(doc.getClientId()).isEqualTo("client-id-thu");
    }

    @Test
    @DisplayName("cookie đặt HttpOnly và Secure")
    void cookieDungThuocTinh() {
        MockHttpServletResponse res = new MockHttpServletResponse();
        kho.saveAuthorizationRequest(yeuCau("s"), new MockHttpServletRequest(), res);

        Cookie c = res.getCookie(CookieOAuth2AuthorizationRequestRepository.TEN_COOKIE);
        assertThat(c).isNotNull();
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getSecure()).isTrue();
        assertThat(c.getPath()).isEqualTo("/");
        assertThat(c.getMaxAge()).isPositive();
    }

    @Test
    @DisplayName("SameSite=Lax, không phải Strict")
    void sameSiteLax() {
        // Web và API khác tên miền, nên chặng Google trả về là điều hướng
        // xuyên site. Strict thì trình duyệt KHÔNG gửi cookie kèm, và ta quay
        // lại đúng lỗi đang muốn tránh.
        MockHttpServletResponse res = new MockHttpServletResponse();
        kho.saveAuthorizationRequest(yeuCau("s"), new MockHttpServletRequest(), res);

        Cookie c = res.getCookie(CookieOAuth2AuthorizationRequestRepository.TEN_COOKIE);
        assertThat(c.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    @DisplayName("lấy ra thì trả yêu cầu VÀ xoá cookie")
    void layRaThiXoa() {
        MockHttpServletResponse resLuu = new MockHttpServletResponse();
        kho.saveAuthorizationRequest(yeuCau("state-xyz"), new MockHttpServletRequest(), resLuu);

        MockHttpServletResponse resXoa = new MockHttpServletResponse();
        OAuth2AuthorizationRequest lay = kho.removeAuthorizationRequest(kemCookie(resLuu), resXoa);

        assertThat(lay).isNotNull();
        assertThat(lay.getState()).isEqualTo("state-xyz");

        // Hết hạn ngay: để lại thì một yêu cầu cũ còn dùng lại được.
        Cookie c = resXoa.getCookie(CookieOAuth2AuthorizationRequestRepository.TEN_COOKIE);
        assertThat(c).isNotNull();
        assertThat(c.getMaxAge()).isZero();
    }

    @Test
    @DisplayName("chưa có cookie thì trả null, không ném")
    void chuaCoCookie() {
        assertThat(kho.loadAuthorizationRequest(new MockHttpServletRequest())).isNull();
        assertThat(kho.removeAuthorizationRequest(
                new MockHttpServletRequest(), new MockHttpServletResponse())).isNull();
    }

    @Test
    @DisplayName("cookie hỏng thì coi như chưa có, và vẫn bị dọn")
    void cookieHong() {
        // Cookie đến từ trình duyệt nên không được tin. Ném ở đây là trả 500
        // cho một người chỉ đang cố đăng nhập; tệ hơn, cookie hỏng để lại thì
        // MỌI lần thử sau đều đọc trúng nó và hỏng theo, cho tới khi người
        // dùng tự xoá dữ liệu trình duyệt.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie(
                CookieOAuth2AuthorizationRequestRepository.TEN_COOKIE, "khong-phai-base64!!!"));

        assertThat(kho.loadAuthorizationRequest(req)).isNull();

        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(kho.removeAuthorizationRequest(req, res)).isNull();
        assertThat(res.getCookie(CookieOAuth2AuthorizationRequestRepository.TEN_COOKIE)
                .getMaxAge()).isZero();
    }

    @Test
    @DisplayName("lưu null là lệnh xoá")
    void luuNullLaXoa() {
        MockHttpServletResponse resLuu = new MockHttpServletResponse();
        kho.saveAuthorizationRequest(yeuCau("s"), new MockHttpServletRequest(), resLuu);

        MockHttpServletResponse res = new MockHttpServletResponse();
        kho.saveAuthorizationRequest(null, kemCookie(resLuu), res);

        assertThat(res.getCookie(CookieOAuth2AuthorizationRequestRepository.TEN_COOKIE)
                .getMaxAge()).isZero();
    }

    @Test
    @DisplayName("không đụng tới cookie nào khác")
    void khongDungCookieKhac() {
        // Xoá nhầm cookie phiên hay cookie ngôn ngữ thì người dùng bị đăng
        // xuất khỏi thứ khác chỉ vì vừa thử đăng nhập Google.
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("ngon_ngu", "vi"), new Cookie("gi_do_khac", "x"));

        MockHttpServletResponse res = new MockHttpServletResponse();
        kho.removeAuthorizationRequest(req, res);

        assertThat(Arrays.stream(res.getCookies()).map(Cookie::getName))
                .doesNotContain("ngon_ngu", "gi_do_khac");
    }
}
