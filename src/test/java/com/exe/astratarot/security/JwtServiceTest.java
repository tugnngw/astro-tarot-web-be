package com.exe.astratarot.security;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.exception.InvalidTokenException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cấp và đọc JWT. Lớp này trước đây phủ 2,0% — chưa ai kiểm, mà nó là thứ duy
 * nhất đứng giữa một chuỗi ký tự và quyền truy cập toàn bộ tài khoản.
 *
 * <p>Hai bất biến quan trọng hơn cả:
 *
 * <ol>
 *   <li><b>Token ký bằng khoá khác phải bị từ chối.</b> Nếu không thì bất kỳ ai
 *       cũng tự cấp được token cho mình, và cả hệ thống phân quyền chỉ còn là
 *       trang trí. Đây là kiểu lỗi mà bộ test "đường xanh" không bao giờ bắt
 *       được, vì nó chỉ hiện ra khi có người cố tình.
 *   <li><b>Chủ thể của token là ID, không phải email.</b> Đổi email không được
 *       làm mất phiên, và trùng email không được lẫn tài khoản. Bộ kiểm chốt
 *       chuyện này lại vì nó là quy ước ngầm dễ bị viết sai khi thêm luồng mới.
 * </ol>
 */
class JwtServiceTest {

    /** Khoá phải đủ 32 byte cho HS256. */
    private static final String KHOA = "khoa-bi-mat-chi-dung-trong-test-32b!!";
    private static final String KHOA_KHAC = "mot-khoa-hoan-toan-khac-cung-32-byte!!";

    private JwtService service;
    private User nguoiDung;

    @BeforeEach
    void setUp() {
        service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", KHOA);
        ReflectionTestUtils.setField(service, "expiration", 3_600_000L);

        nguoiDung = new User();
        nguoiDung.setId(UUID.randomUUID());
        nguoiDung.setEmail("khach@example.com");
        nguoiDung.setPasswordHash("khong-dung-toi");
        nguoiDung.setRole(UserRole.USER);
    }

    private CustomUserDetails chiTiet() {
        return new CustomUserDetails(nguoiDung);
    }

    /** Một UserDetails thường, không phải CustomUserDetails. */
    private UserDetails chiTietThuong(String tenDangNhap) {
        return org.springframework.security.core.userdetails.User
                .withUsername(tenDangNhap)
                .password("x")
                .authorities("ROLE_USER")
                .build();
    }

    private String tokenKyBangKhoa(String khoa, String chuThe, long hetHanSauMs) {
        return Jwts.builder()
                .subject(chuThe)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + hetHanSauMs))
                .signWith(Keys.hmacShaKeyFor(khoa.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    // =====================================================================

    @Test
    @DisplayName("Chủ thể của token là ID tài khoản, không phải email")
    void chuTheLaId() {
        String token = service.generateToken(chiTiet());

        // Nếu chủ thể là email thì đổi email làm mất phiên, và hai tài khoản
        // trùng email lẫn vào nhau. ID không đổi suốt đời tài khoản.
        assertEquals(nguoiDung.getId().toString(), service.extractSubject(token));
    }

    @Test
    @DisplayName("UserDetails thường thì chủ thể là tên đăng nhập")
    void userDetailsThuong() {
        String token = service.generateToken(chiTietThuong("ai-do@example.com"));

        assertEquals("ai-do@example.com", service.extractSubject(token));
    }

    @Test
    @DisplayName("Claim thêm vào được giữ trong token")
    void claimThemVao() {
        String token = service.generateToken(chiTiet(), Map.of("type", "refresh"));

        assertAll(
                () -> assertEquals(nguoiDung.getId().toString(), service.extractSubject(token)),
                () -> assertEquals("refresh", Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(KHOA.getBytes(StandardCharsets.UTF_8)))
                        .build().parseSignedClaims(token).getPayload().get("type")));
    }

    @Test
    @DisplayName("Token từ email: chủ thể là chính email đó")
    void tokenTuEmail() {
        String token = service.generateTokenFromEmail("xacthuc@example.com");

        assertAll(
                () -> assertEquals("xacthuc@example.com", service.extractSubject(token)),
                () -> assertEquals("xacthuc@example.com", service.extractEmail(token)));
    }

    @Test
    @DisplayName("Token ký bằng KHOÁ KHÁC bị từ chối")
    void kyBangKhoaKhac() {
        String token = tokenKyBangKhoa(KHOA_KHAC, nguoiDung.getId().toString(), 3_600_000L);

        // Không kiểm chữ ký thì bất kỳ ai cũng tự cấp token cho mình, và cả hệ
        // thống phân quyền chỉ còn là trang trí.
        assertAll(
                () -> assertThrows(Exception.class, () -> service.extractSubject(token)),
                () -> assertThrows(InvalidTokenException.class,
                        () -> service.extractSubjectSafely(token)));
    }

    @Test
    @DisplayName("Token HẾT HẠN bị từ chối, và báo đúng là hết hạn")
    void tokenHetHan() {
        String token = tokenKyBangKhoa(KHOA, nguoiDung.getId().toString(), -1_000L);

        var loi = assertThrows(InvalidTokenException.class,
                () -> service.extractSubjectSafely(token));
        // Phân biệt "hết hạn" với "hỏng" là việc thật: hết hạn thì app làm mới
        // token rồi đi tiếp, còn hỏng thì phải đăng nhập lại.
        assertTrue(loi.getMessage().contains("expired"));
    }

    @Test
    @DisplayName("Token méo mó bị từ chối chứ không nổ ra ngoại lệ lạ")
    void tokenMeoMo() {
        assertAll(
                () -> assertThrows(InvalidTokenException.class,
                        () -> service.extractSubjectSafely("day-khong-phai-jwt")),
                () -> assertThrows(InvalidTokenException.class,
                        () -> service.extractSubjectSafely("a.b.c")),
                () -> assertThrows(InvalidTokenException.class,
                        () -> service.extractEmailSafely("")));
    }

    @Test
    @DisplayName("Token KHÔNG ký bị từ chối")
    void tokenKhongKy() {
        String token = Jwts.builder()
                .subject(nguoiDung.getId().toString())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .compact();

        // Bỏ chữ ký đi rồi vẫn được nhận là lỗ hổng cổ điển nhất của JWT.
        assertThrows(InvalidTokenException.class, () -> service.extractSubjectSafely(token));
    }

    @Test
    @DisplayName("Xác thực token khớp đúng tài khoản")
    void xacThucKhop() {
        String token = service.generateToken(chiTiet());

        assertTrue(service.validateToken(token, chiTiet()));
    }

    @Test
    @DisplayName("Token của tài khoản KHÁC không dùng cho tài khoản này được")
    void tokenTaiKhoanKhac() {
        String token = service.generateToken(chiTiet());

        User nguoiKhac = new User();
        nguoiKhac.setId(UUID.randomUUID());
        nguoiKhac.setEmail("khac@example.com");
        nguoiKhac.setPasswordHash("x");
        nguoiKhac.setRole(UserRole.USER);

        // Đây là hàng rào chặn việc dùng token hợp lệ của mình để đóng vai người
        // khác — chữ ký đúng nhưng chủ thể không khớp.
        assertFalse(service.validateToken(token, new CustomUserDetails(nguoiKhac)));
    }

    @Test
    @DisplayName("UserDetails thường: so chủ thể với tên đăng nhập")
    void xacThucUserDetailsThuong() {
        String token = service.generateTokenFromEmail("ai-do@example.com");

        assertAll(
                () -> assertTrue(service.validateToken(token, chiTietThuong("ai-do@example.com"))),
                () -> assertFalse(service.validateToken(token, chiTietThuong("khac@example.com"))));
    }

    @Test
    @DisplayName("Hạn dùng được công bố để app biết khi nào cần làm mới")
    void hanDung() {
        assertEquals(3_600_000L, service.getExpiration());
    }
}
