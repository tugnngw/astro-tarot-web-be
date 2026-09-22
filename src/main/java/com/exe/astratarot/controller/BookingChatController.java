package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.booking.BookingMessageResponse;
import com.exe.astratarot.domain.dto.booking.CallSignalMessage;
import com.exe.astratarot.domain.dto.booking.SendBookingMessageRequest;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.BookingChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * Nhắn tin và gọi trong một buổi tư vấn.
 *
 * <h3>Vì sao có cả REST lẫn STOMP cho việc gửi tin</h3>
 *
 * <p>STOMP là đường chính: gửi xong cả hai bên thấy ngay. Nhưng WebSocket đứt
 * là chuyện thường — Render ngủ dậy, mạng 4G chuyển trạm, máy khoá màn hình.
 * Lúc đó {@code POST /messages} vẫn gửi được, và tin sẽ hiện ra ở phía kia khi
 * họ tải lại hoặc nối lại. Không có lối REST thì mất sóng một cái là không
 * nhắn nổi, dù mọi thứ khác vẫn chạy.
 *
 * <h3>Lỗi trong STOMP thì báo về đâu</h3>
 *
 * <p>Một {@code @MessageMapping} ném ngoại lệ thì client KHÔNG nhận được gì —
 * người dùng thấy tin nhắn im lặng biến mất. Nên bắt tại chỗ rồi đẩy một gói
 * lỗi về đúng người gửi qua {@code /user/queue/errors}.
 */
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}")
@RequiredArgsConstructor
@Slf4j
public class BookingChatController {

    /** Giữ khớp với {@code @Size} ở {@link SendBookingMessageRequest}. */
    private static final int MAX_BODY = 4000;

    private final BookingChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    // ---------- REST ----------

    @GetMapping("/messages")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<Page<BookingMessageResponse>>> history(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID bookingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.success(chatService.history(
                bookingId, me.getUser().getId(), PageRequest.of(page, size))));
    }

    @PostMapping("/messages")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<BookingMessageResponse>> send(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID bookingId,
            @Valid @RequestBody SendBookingMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đã gửi",
                chatService.send(bookingId, me.getUser().getId(), request.getBody())));
    }

    @PostMapping("/messages/read")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markRead(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID bookingId) {
        int marked = chatService.markRead(bookingId, me.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("marked", marked)));
    }

    @GetMapping("/messages/unread")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unread(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID bookingId) {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "unread", chatService.unreadCount(bookingId, me.getUser().getId()))));
    }

    // ---------- STOMP ----------

    /**
     * Client gửi tới {@code /app/bookings/{bookingId}/chat}.
     *
     * <p>Cố tình KHÔNG dùng {@code @Valid} ở đây. Validation của Spring chạy
     * trước khi vào thân hàm, nên ngoại lệ nó ném ra nằm ngoài khối try dưới
     * đây và không bao giờ tới được {@link #reportError} — người dùng gõ quá
     * dài sẽ thấy tin nhắn biến mất không một lời giải thích. Tự kiểm tra
     * trong khối try thì lỗi mới về được đúng người gửi.
     */
    @MessageMapping("/bookings/{bookingId}/chat")
    public void sendOverSocket(@DestinationVariable UUID bookingId,
                               SendBookingMessageRequest request,
                               Principal principal) {
        UUID senderId = UUID.fromString(principal.getName());
        try {
            String body = request == null ? null : request.getBody();
            if (body == null || body.isBlank()) {
                throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
            }
            if (body.length() > MAX_BODY) {
                throw new IllegalArgumentException("Tin nhắn tối đa " + MAX_BODY + " ký tự");
            }
            chatService.send(bookingId, senderId, body);
        } catch (Exception e) {
            reportError(senderId, "CHAT", e);
        }
    }

    /** Client gửi tới {@code /app/bookings/{bookingId}/call}. */
    @MessageMapping("/bookings/{bookingId}/call")
    public void signal(@DestinationVariable UUID bookingId,
                       CallSignalMessage signal,
                       Principal principal) {
        UUID senderId = UUID.fromString(principal.getName());
        try {
            chatService.relaySignal(bookingId, senderId, signal);
        } catch (Exception e) {
            reportError(senderId, "CALL", e);
        }
    }

    private void reportError(UUID userId, String scope, Exception e) {
        log.warn("STOMP {} lỗi cho user {}: {}", scope, userId, e.toString());
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/errors", Map.of(
                    "scope", scope,
                    "message", e.getMessage() == null ? "Không gửi được" : e.getMessage()));
        } catch (Exception ignored) {
            // Báo lỗi mà cũng lỗi thì thôi, đã ghi log ở trên.
        }
    }
}
