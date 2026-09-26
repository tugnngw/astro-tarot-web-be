package com.exe.astratarot.service;

import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ai đang online, và ai rời đi lúc nào.
 *
 * <p>Thứ đáng giữ nhất ở đây là <b>đếm phiên</b> chứ không phải một cờ bật
 * tắt. Một người mở web trên máy tính và app trên điện thoại là hai phiên
 * STOMP của cùng một tài khoản; đóng tab máy tính mà đặt họ thành offline là
 * nói sai với người bên kia — họ vẫn đang cầm điện thoại và vẫn nhắn được.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Trạng thái hoạt động")
class PresenceServiceTest {

    @Mock
    private UserRepository userRepository;

    private PresenceService service;
    private UUID ai;

    @BeforeEach
    void setUp() {
        service = new PresenceService(userRepository);
        ai = UUID.randomUUID();
    }

    private Principal nguoi(UUID id) {
        return new UsernamePasswordAuthenticationToken(id.toString(), null);
    }

    private SessionConnectedEvent noi(Principal p) {
        Message<byte[]> m = new GenericMessage<>(new byte[0]);
        return new SessionConnectedEvent(this, m, p);
    }

    private SessionDisconnectEvent roi(Principal p) {
        Message<byte[]> m = new GenericMessage<>(new byte[0]);
        return new SessionDisconnectEvent(this, m, "phien-" + UUID.randomUUID(), null, p);
    }

    @Test
    @DisplayName("Nối vào thì online, rời đi thì offline")
    void noiRoi() {
        assertFalse(service.dangOnline(ai), "chưa nối thì phải là offline");

        service.khiNoi(noi(nguoi(ai)));
        assertTrue(service.dangOnline(ai));

        service.khiRoi(roi(nguoi(ai)));
        assertFalse(service.dangOnline(ai));
    }

    @Test
    @DisplayName("Hai thiết bị: đóng MỘT cái thì vẫn còn online")
    void haiThietBi() {
        Principal p = nguoi(ai);
        service.khiNoi(noi(p));
        service.khiNoi(noi(p));

        service.khiRoi(roi(p));

        // Web trên máy tính đóng, app điện thoại còn mở. Đặt offline ở đây là
        // người bên kia thấy "đã rời đi" rồi bỏ cuộc, trong khi tin nhắn vẫn
        // tới nơi và vẫn được đọc ngay.
        assertTrue(service.dangOnline(ai));

        service.khiRoi(roi(p));
        assertFalse(service.dangOnline(ai));
    }

    @Test
    @DisplayName("Chỉ ghi giờ rời đi khi phiên CUỐI đóng lại")
    void chiGhiKhiPhienCuoiDong() {
        Principal p = nguoi(ai);
        service.khiNoi(noi(p));
        service.khiNoi(noi(p));
        // Hai lần nối đã ghi hai lần; đếm từ đây trở đi.
        verify(userRepository, org.mockito.Mockito.times(2)).ghiLastSeen(eq(ai), any());

        service.khiRoi(roi(p));
        verify(userRepository, org.mockito.Mockito.times(2)).ghiLastSeen(eq(ai), any());

        service.khiRoi(roi(p));
        verify(userRepository, org.mockito.Mockito.times(3)).ghiLastSeen(eq(ai), any());
    }

    @Test
    @DisplayName("Ghi giờ NGAY LÚC NỐI, không đợi lúc rời")
    void ghiGioNgayLucNoi() {
        service.khiNoi(noi(nguoi(ai)));

        // Tiến trình bị giết đột ngột — Render đẩy bản mới, container hết bộ
        // nhớ — thì sự kiện rời đi không bao giờ chạy. Ghi lúc nối thì mốc
        // còn lại vẫn đúng là lúc họ có mặt, thay vì một mốc cũ hàng ngày.
        verify(userRepository).ghiLastSeen(eq(ai), any(Instant.class));
    }

    @Test
    @DisplayName("Rời đi mà chưa từng nối thì không đếm xuống âm")
    void roiMaChuaTungNoi() {
        service.khiRoi(roi(nguoi(ai)));

        assertFalse(service.dangOnline(ai));
        // Nối vào sau đó phải online bình thường — nếu bộ đếm tụt xuống -1
        // thì một lần nối chỉ đưa nó về 0 và người này vô hình mãi mãi.
        service.khiNoi(noi(nguoi(ai)));
        assertTrue(service.dangOnline(ai));
    }

    @Test
    @DisplayName("Không có principal, hoặc tên không phải UUID, thì bỏ qua")
    void khongCoNguoi() {
        assertAll(
                () -> service.khiNoi(noi(null)),
                () -> service.khiRoi(roi(null)),
                () -> service.khiNoi(noi(
                        new UsernamePasswordAuthenticationToken("khong-phai-uuid", null))));
        verify(userRepository, never()).ghiLastSeen(any(), any());
    }

    @Test
    @DisplayName("Ghi giờ hỏng thì KHÔNG làm hỏng vòng đời WebSocket")
    void ghiHongThiKhongNem() {
        doThrow(new RuntimeException("database ngủ"))
                .when(userRepository).ghiLastSeen(any(), any());

        // Một dấu thời gian trang trí không được phép làm rớt kết nối của
        // người dùng. Hỏng ở đây là dòng "hoạt động lúc nào" sai một chút.
        service.khiNoi(noi(nguoi(ai)));

        assertTrue(service.dangOnline(ai), "vẫn phải tính là đang online");
    }

    @Test
    @DisplayName("Chưa từng kết nối thì không có mốc nào")
    void chuaTungKetNoi() {
        when(userRepository.lastSeenCua(ai)).thenReturn(Optional.empty());

        // Giao diện phải chịu được chỗ trống này chứ không hiện "Hoạt động 56
        // năm trước" từ một mốc mặc định.
        assertTrue(service.lanCuoiThay(ai).isEmpty());
        assertTrue(service.lanCuoiThay(null).isEmpty());
    }

    @Test
    @DisplayName("Đọc được mốc đã ghi")
    void docDuocMoc() {
        Instant luc = Instant.parse("2026-09-26T10:00:00Z");
        when(userRepository.lastSeenCua(ai)).thenReturn(Optional.of(luc));

        assertEquals(luc, service.lanCuoiThay(ai).orElseThrow());
    }
}
