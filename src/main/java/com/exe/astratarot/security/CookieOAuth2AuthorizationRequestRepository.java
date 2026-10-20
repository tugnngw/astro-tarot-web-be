package com.exe.astratarot.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * Giữ yêu cầu uỷ quyền OAuth2 trong một cookie, không phải trong phiên.
 *
 * <h2>Vì sao phải có lớp này</h2>
 *
 * Luồng đăng nhập Google đi hai chặng: trình duyệt rời khỏi máy chủ sang
 * Google, rồi quay lại {@code /login/oauth2/code/google}. Giữa hai chặng ấy
 * Spring phải nhớ mình vừa gửi đi những gì — nhất là {@code state}, mảnh dữ
 * liệu chống giả mạo yêu cầu.
 *
 * <p>Bản mặc định ({@code HttpSessionOAuth2AuthorizationRequestRepository})
 * nhớ bằng {@code HttpSession}. Máy chủ này chạy
 * {@code SessionCreationPolicy.STATELESS} — không có phiên nào cả — nên khi
 * Google trả người dùng về, Spring không tìm thấy yêu cầu đã lưu và chặn
 * ngay với {@code authorization_request_not_found}.
 *
 * <p>Đó chính là lý do {@code .oauth2Login(...)} bị chú thích tắt trong
 * {@code SecurityConfig} từ trước. Bật nó lên mà không thay kho lưu thì nút
 * "Đăng nhập với Google" bấm được, Google hỏi quyền xong xuôi, rồi người dùng
 * rơi vào trang lỗi — hỏng ở đúng bước cuối, sau khi mọi thứ trông như đang
 * chạy.
 *
 * <h2>Vì sao cookie an toàn ở đây</h2>
 *
 * Cookie này chỉ sống vài phút giữa hai chặng và KHÔNG chứa thông tin đăng
 * nhập — nó chứa yêu cầu uỷ quyền, thứ chính Google sẽ đối chiếu. Đặt
 * {@code HttpOnly} nên JavaScript không đọc được, và {@code SameSite=Lax} để
 * trình duyệt vẫn gửi kèm khi Google chuyển hướng người dùng quay lại bằng
 * GET — đặt {@code Strict} là cookie không được gửi, và ta quay lại đúng lỗi
 * đang muốn tránh.
 */
@Slf4j
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String TEN_COOKIE = "oauth2_auth_request";

    /**
     * Ba phút. Đây là quãng từ lúc bấm nút tới lúc Google trả về — đủ rộng cho
     * một lần đăng nhập thật kể cả phải gõ mật khẩu và mã hai lớp, mà vẫn đủ
     * hẹp để một cookie bị bỏ quên không nằm lại trong máy cả ngày.
     */
    private static final int SONG_GIAY = 180;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return docCookie(request, TEN_COOKIE)
                .map(CookieOAuth2AuthorizationRequestRepository::giaiMa)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            xoaCookie(request, response);
            return;
        }
        datCookie(response, TEN_COOKIE, maHoa(authorizationRequest), SONG_GIAY);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest daLuu = loadAuthorizationRequest(request);
        // Xoá ngay cả khi không đọc được: cookie hỏng để lại thì mọi lần thử
        // đăng nhập sau đều đọc trúng nó và hỏng theo, cho tới khi người dùng
        // tự xoá dữ liệu trình duyệt.
        xoaCookie(request, response);
        return daLuu;
    }

    // ------------------------------------------------------------
    // Cookie
    // ------------------------------------------------------------

    private static Optional<Cookie> docCookie(HttpServletRequest request, String ten) {
        Cookie[] ds = request.getCookies();
        if (ds == null) return Optional.empty();
        return Arrays.stream(ds).filter(c -> ten.equals(c.getName())).findFirst();
    }

    private static void datCookie(HttpServletResponse response, String ten, String giaTri, int songGiay) {
        Cookie c = new Cookie(ten, giaTri);
        c.setPath("/");
        c.setHttpOnly(true);
        c.setMaxAge(songGiay);
        // Web và API nằm khác tên miền (astrotarot.date / api.astrotarot.date),
        // nên chặng quay về là một yêu cầu xuyên site. SameSite=Lax cho phép
        // gửi kèm ở điều hướng GET cấp cao nhất — đúng kiểu Google dùng để
        // trả người dùng về. Strict thì cookie không được gửi.
        c.setAttribute("SameSite", "Lax");
        c.setSecure(true);
        response.addCookie(c);
    }

    private static void xoaCookie(HttpServletRequest request, HttpServletResponse response) {
        docCookie(request, TEN_COOKIE).ifPresent(cu -> datCookie(response, TEN_COOKIE, "", 0));
    }

    // ------------------------------------------------------------
    // Chuyển đổi
    // ------------------------------------------------------------

    private static String maHoa(OAuth2AuthorizationRequest yeuCau) {
        return Base64.getUrlEncoder().encodeToString(SerializationUtils.serialize(yeuCau));
    }

    /**
     * Giải mã, và trả {@code null} khi cookie hỏng thay vì ném.
     *
     * <p>Cookie đến từ trình duyệt nên không được tin. Ném ở đây là trả về
     * trang lỗi 500 cho một người chỉ đang cố đăng nhập; trả {@code null}
     * thì Spring báo đúng việc đã xảy ra — không tìm thấy yêu cầu — và người
     * dùng bấm lại một lần là xong.
     */
    @SuppressWarnings("deprecation")
    private static OAuth2AuthorizationRequest giaiMa(Cookie cookie) {
        try {
            byte[] byt = Base64.getUrlDecoder().decode(cookie.getValue());
            Object doiTuong = SerializationUtils.deserialize(byt);
            return doiTuong instanceof OAuth2AuthorizationRequest y ? y : null;
        } catch (Exception e) {
            log.debug("Cookie OAuth2 không đọc được, coi như chưa có: {}", e.getMessage());
            return null;
        }
    }
}
