package com.exe.astratarot.service;

import com.exe.astratarot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ai đang online, và ai rời đi lúc nào.
 *
 * <h3>Đếm PHIÊN chứ không đánh dấu một cờ online</h3>
 *
 * <p>Một người có thể mở web trên máy tính và app trên điện thoại cùng lúc —
 * hai phiên STOMP của cùng một tài khoản. Nếu chỉ giữ một cờ thì đóng tab máy
 * tính sẽ đặt cờ về offline, trong khi họ vẫn đang cầm điện thoại và vẫn nhắn
 * được. Người bên kia thấy "đã offline" rồi bỏ đi, còn tin nhắn thì vẫn tới
 * nơi.
 *
 * <p>Nên đếm: online khi còn ít nhất một phiên, và chỉ ghi giờ rời đi khi
 * phiên CUỐI cùng đóng lại.
 *
 * <h3>Giữ trong bộ nhớ, và vì sao thế là đủ</h3>
 *
 * <p>Trạng thái online gắn với kết nối WebSocket, mà kết nối thì nằm ở đúng
 * tiến trình đang giữ nó. Backend chạy một bản duy nhất trên Render nên bản
 * đồ này là toàn bộ sự thật.
 *
 * <p>Ngày nào chạy nhiều bản thì phải chuyển sang Redis (dự án đã có Upstash):
 * mỗi bản chỉ biết những kết nối của chính nó, nên người dùng nối vào bản A sẽ
 * hiện offline với người hỏi bản B. Ghi lại đây để không phải tự phát hiện lại
 * bằng một lỗi khó hiểu.
 *
 * <h3>Giờ rời đi thì ghi xuống database</h3>
 *
 * <p>"Hoạt động 5 phút trước" phải sống qua một lần khởi động lại. Gói free
 * của Render cho container ngủ sau 15 phút, nên nếu chỉ giữ trong bộ nhớ thì
 * sáng hôm sau mọi người đều thành "chưa từng hoạt động".
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PresenceService {

    private final UserRepository userRepository;

    /** userId → số phiên STOMP đang mở. Không có khoá nghĩa là offline. */
    private final Map<UUID, Integer> soPhien = new ConcurrentHashMap<>();

    // @Transactional nằm ở ĐÂY chứ không ở ghiGio: Spring bọc proxy quanh
    // bean, nên một phương thức tự gọi phương thức khác trong cùng lớp sẽ đi
    // thẳng, không qua proxy, và không có giao dịch nào được mở. Sự kiện
    // WebSocket thì do Spring gọi vào nên nó ĐI qua proxy.
    @Transactional
    @EventListener
    public void khiNoi(SessionConnectedEvent event) {
        UUID id = layId(event.getUser());
        if (id == null) {
            return;
        }
        soPhien.merge(id, 1, Integer::sum);
        // Ghi luôn lúc nối, không đợi lúc rời: nếu tiến trình bị giết đột ngột
        // (Render đẩy bản mới, container hết bộ nhớ) thì sự kiện rời đi không
        // bao giờ chạy, và mốc cuối cùng còn lại vẫn đúng là lúc họ có mặt.
        ghiGio(id);
    }

    @Transactional
    @EventListener
    public void khiRoi(SessionDisconnectEvent event) {
        UUID id = layId(event.getUser());
        if (id == null) {
            return;
        }
        // compute trả null để XOÁ hẳn khoá khi về 0 — giữ lại một mục đếm 0
        // cho mọi người từng đăng nhập là một chỗ rò bộ nhớ chậm.
        soPhien.compute(id, (k, con) -> con == null || con <= 1 ? null : con - 1);
        if (!soPhien.containsKey(id)) {
            ghiGio(id);
        }
    }

    /** Còn ít nhất một phiên đang mở. */
    public boolean dangOnline(UUID userId) {
        return userId != null && soPhien.containsKey(userId);
    }

    /**
     * Lần cuối còn kết nối. Rỗng khi chưa từng nối lần nào.
     *
     * <p>Người đang online cũng có giá trị này (ghi lúc họ nối vào), nhưng
     * người gọi nên hiện "đang hoạt động" thay vì một mốc thời gian.
     */
    @Transactional(readOnly = true)
    public Optional<Instant> lanCuoiThay(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.lastSeenCua(userId);
    }

    private void ghiGio(UUID id) {
        try {
            userRepository.ghiLastSeen(id, Instant.now());
        } catch (RuntimeException e) {
            // Không để việc ghi một dấu thời gian trang trí làm hỏng vòng đời
            // WebSocket. Hỏng ở đây nghĩa là dòng "hoạt động lúc nào" sai một
            // chút, không phải là ai đó mất kết nối.
            log.warn("Không ghi được last_seen_at cho {}: {}", id, e.getMessage());
        }
    }

    private UUID layId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            // Principal.getName() là UUID người dùng — xem StompAuthChannelInterceptor.
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
