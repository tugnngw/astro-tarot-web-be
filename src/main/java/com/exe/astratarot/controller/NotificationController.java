package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.notification.NotificationResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Thông báo của người đang đăng nhập.
 *
 * <p>Lấy userId từ token chứ không nhận từ đường dẫn. Nếu để dạng
 * /users/{id}/notifications thì phải tự kiểm tra quyền ở mọi endpoint, sót một
 * chỗ là đọc được hộp thư của người khác.
 */
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> list(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.list(me.getUser().getId(), PageRequest.of(page, size))));
    }

    /** Chỉ con số cho chuông trên header — gọi thường xuyên nên tách khỏi danh sách. */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @AuthenticationPrincipal CustomUserDetails me) {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("count", notificationService.unreadCount(me.getUser().getId()))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable UUID id) {
        notificationService.markRead(me.getUser().getId(), id);
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu đã đọc", null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(
            @AuthenticationPrincipal CustomUserDetails me) {
        int updated = notificationService.markAllRead(me.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu tất cả đã đọc",
                Map.of("updated", updated)));
    }

    /**
     * Xoá hẳn mọi thông báo ĐÃ ĐỌC của chính mình.
     *
     * <p>Đánh dấu đã đọc chỉ làm tắt chấm tròn, danh sách vẫn dài ra mãi. Đây
     * là đường dọn dẹp thật.
     */
    @DeleteMapping("/read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> deleteRead(
            @AuthenticationPrincipal CustomUserDetails me) {
        int soDong = notificationService.deleteAllRead(me.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Đã xoá thông báo đã đọc",
                Map.of("deleted", soDong)));
    }
}
