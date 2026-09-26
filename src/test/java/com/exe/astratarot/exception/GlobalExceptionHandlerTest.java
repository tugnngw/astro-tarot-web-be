package com.exe.astratarot.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bộ dịch ngoại lệ sang mã HTTP. Lớp này trước đây phủ 24,4%.
 *
 * <p>Đây là nơi quyết định người dùng nhìn thấy gì khi có chuyện. Nguyên tắc
 * xuyên suốt: <b>mã trạng thái phải nói đúng lỗi nằm ở đâu</b>.
 *
 * <ul>
 *   <li>4xx là "yêu cầu của bạn có vấn đề" — người dùng sửa được.
 *   <li>5xx là "máy chủ của chúng tôi hỏng" — người dùng không làm gì được, và
 *       nó đánh thức cảnh báo hệ thống.
 *   <li>502 là "dịch vụ bên ngoài hỏng" — mời thử lại, không phải lỗi của ta.
 * </ul>
 *
 * <p>Trả 500 cho một lỗi nghiệp vụ bình thường là hai chuyện tệ cùng lúc: người
 * dùng thấy màn hình "hệ thống hỏng" cho một việc họ tự sửa được, và mọi cảnh
 * báo lỗi hệ thống kêu oan. Cả hai đều đã xảy ra thật với bảy ngoại lệ của
 * luồng Reader.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // =====================================================================

    @Test
    @DisplayName("Ngoại lệ NGHIỆP VỤ trả 400, không phải 500")
    void nghiepVuTra400() {
        // Bảy ngoại lệ của luồng Reader trước đây không có handler nào, nên rơi
        // hết xuống handler chung và trả 500 kèm "Đã có lỗi xảy ra". Người dùng
        // nộp hồ sơ lần hai, hay khai một khung giờ trùng, đều nhận màn hình
        // lỗi hệ thống thay vì câu giải thích họ cần đọc.
        var loi = java.util.List.<RuntimeException>of(
                new EmailAlreadyExistsException("Email đã đăng ký"),
                new AlreadyReaderException(),
                new AlreadyAppliedException(),
                new AvailabilityConflictException(),
                new InvalidAvailabilityTimeException(),
                new InvalidCredentialsException("Sai mật khẩu"));

        loi.forEach(e -> assertEquals(HttpStatus.BAD_REQUEST,
                handler.handleConflictAndBadRequests(e).getStatusCode(),
                e.getClass().getSimpleName() + " phải là 400"));
    }

    @Test
    @DisplayName("Ngoại lệ nghiệp vụ giữ nguyên câu giải thích cho người dùng")
    void giuNguyenCauGiaiThich() {
        var res = handler.handleConflictAndBadRequests(
                new EmailAlreadyExistsException("Email này đã được đăng ký"));

        assertAll(
                () -> assertFalse(res.getBody().isSuccess()),
                () -> assertEquals("Email này đã được đăng ký", res.getBody().getMessage()));
    }

    @Test
    @DisplayName("Không tìm thấy đối tượng trả 404")
    void khongTimThayTra404() {
        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND,
                        handler.handleResourceNotFound(
                                new ResourceNotFoundException("Không tìm thấy lịch hẹn")).getStatusCode()),
                () -> assertEquals(HttpStatus.NOT_FOUND,
                        handler.handleNotFoundDomain(new ReaderNotVerifiedException()).getStatusCode()),
                () -> assertEquals(HttpStatus.NOT_FOUND,
                        handler.handleNotFoundDomain(new ApplicationNotFoundException()).getStatusCode()));
    }

    @Test
    @DisplayName("Từ chối quyền GIỮ NGUYÊN lý do khi tầng service tự nêu")
    void tuChoiQuyenGiuLyDo() {
        var res = handler.handleAccessDenied(
                new AccessDeniedException("Không thể hạ vai trò của quản trị viên cuối cùng"));

        // Nuốt lý do đi thì giao diện chỉ nói được "bị từ chối", và người dùng
        // không biết phải làm gì khác đi.
        assertAll(
                () -> assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode()),
                () -> assertEquals("Không thể hạ vai trò của quản trị viên cuối cùng",
                        res.getBody().getMessage()));
    }

    @Test
    @DisplayName("Câu mặc định của Spring được thay bằng câu tiếng Việt")
    void thayCauMacDinh() {
        // @PreAuthorize chặn thì Spring ném "Access Denied" — một câu tiếng
        // Anh không dành cho người dùng cuối.
        for (String macDinh : new String[]{"Access Denied", "access denied", null, "  "}) {
            var res = handler.handleAccessDenied(
                    macDinh == null ? new AccessDeniedException(null) : new AccessDeniedException(macDinh));
            assertEquals("Bạn không có quyền thực hiện thao tác này",
                    res.getBody().getMessage(), "câu mặc định [" + macDinh + "] phải được thay");
        }
    }

    @Test
    @DisplayName("Token hỏng trả 401, không phải 403")
    void tokenHongTra401() {
        // 401 nghĩa là "đăng nhập lại đi"; 403 nghĩa là "đã đăng nhập nhưng
        // không đủ quyền". Nhầm chỗ này thì giao diện hiện sai màn hình.
        assertEquals(HttpStatus.UNAUTHORIZED,
                handler.handleInvalidToken(new InvalidTokenException("JWT token expired")).getStatusCode());
    }

    @Test
    @DisplayName("Nhà cung cấp AI lỗi trả 502, không phải 500")
    void aiLoiTra502() {
        var res = handler.handleLlmProvider(new LLMProviderException("429 Too Many Requests"));

        assertAll(
                // Hỏng nằm ở dịch vụ bên ngoài; 500 làm cảnh báo lỗi hệ thống
                // của ta kêu oan cho một sự cố không phải của ta.
                () -> assertEquals(HttpStatus.BAD_GATEWAY, res.getStatusCode()),
                // Câu lỗi kỹ thuật của nhà cung cấp không đưa cho người dùng.
                () -> assertFalse(res.getBody().getMessage().contains("429")),
                () -> assertTrue(res.getBody().getMessage().contains("thử lại")));
    }

    @Test
    @DisplayName("Quá giới hạn gọi trả 429 và giữ nguyên câu nhắc")
    void quaGioiHanTra429() {
        var res = handler.handleRateLimit(
                new RateLimitExceededException("Bạn đã dùng hết 5 lượt hôm nay"));

        assertAll(
                () -> assertEquals(HttpStatus.TOO_MANY_REQUESTS, res.getStatusCode()),
                () -> assertEquals("Bạn đã dùng hết 5 lượt hôm nay", res.getBody().getMessage()));
    }

    @Test
    @DisplayName("Body JSON hỏng trả 400, không đổ lỗi cho máy chủ")
    void bodyHongTra400() {
        var res = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("JSON parse error",
                        (org.springframework.http.HttpInputMessage) null));

        // 500 ở đây là báo cho client rằng máy chủ hỏng, trong khi lỗi nằm ở
        // request họ gửi.
        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode()),
                () -> assertTrue(res.getBody().getMessage().contains("UTF-8")));
    }

    @Test
    @DisplayName("Đường dẫn không tồn tại trả 404, không sinh stack trace")
    void duongDanKhongTonTaiTra404() {
        var res = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/wp-login.php"));

        // Bot dò /.env, /wp-login.php, /admin.php cả ngày. Mỗi lần trả 500 là
        // một stack trace, và log thật chìm nghỉm trong đó. Đã gặp thật: tắt
        // Swagger xong thì /swagger-ui/index.html trả 500 chứ không phải 404.
        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode()),
                () -> assertEquals("Không tìm thấy đường dẫn này.", res.getBody().getMessage()));
    }

    @Test
    @DisplayName("Tham số sai và trạng thái sai đều trả 400")
    void thamSoSaiTra400() {
        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST,
                        handler.handleIllegalArgument(
                                new IllegalArgumentException("Thời lượng chỉ nhận 15, 30 hoặc 60 phút"))
                                .getStatusCode()),
                // Luồng duyệt bài ném IllegalStateException khi Manager duyệt
                // một bài chưa gửi; thiếu handler thì nó thành 500.
                () -> assertEquals(HttpStatus.BAD_REQUEST,
                        handler.handleIllegalState(
                                new IllegalStateException("Bài viết phải ở trạng thái PENDING"))
                                .getStatusCode()));
    }

    @Test
    @DisplayName("Lỗi xác thực trả 401, và lỗi của Spring Security không lộ chi tiết")
    void loiXacThucTra401() {
        var cuaTa = handler.handleAuthenticationException(
                new javax.naming.AuthenticationException("Phiên đã hết hạn"));
        var cuaSpring = handler.handleSpringSecurityAuthException(
                new org.springframework.security.authentication.BadCredentialsException(
                        "Bad credentials for user admin@example.com"));

        assertAll(
                () -> assertEquals(HttpStatus.UNAUTHORIZED, cuaTa.getStatusCode()),
                () -> assertEquals("Phiên đã hết hạn", cuaTa.getBody().getMessage()),
                () -> assertEquals(HttpStatus.UNAUTHORIZED, cuaSpring.getStatusCode()),
                // Không nhắc lại email trong câu lỗi: đó là cách xác nhận cho
                // người dò rằng email ấy có tồn tại.
                () -> assertFalse(cuaSpring.getBody().getMessage().contains("admin@example.com")));
    }

    @Test
    @DisplayName("Lỗi KHÔNG lường trước trả 500 và KHÔNG lộ chi tiết kỹ thuật")
    void loiKhongLuongTruoc() {
        var res = handler.handleGeneralException(
                new NullPointerException("Cannot invoke \"User.getId()\" because \"user\" is null"));

        // Câu lỗi Java nói cho người tấn công biết tên lớp và cấu trúc dữ liệu
        // bên trong. Ghi vào log thì được; trả về cho client thì không.
        assertAll(
                () -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode()),
                () -> assertFalse(res.getBody().getMessage().contains("User.getId")),
                () -> assertFalse(res.getBody().getMessage().contains("NullPointer")));
    }
}
