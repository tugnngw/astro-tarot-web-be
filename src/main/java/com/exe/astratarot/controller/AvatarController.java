package com.exe.astratarot.controller;

import com.exe.astratarot.repository.UserAvatarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Phục vụ ảnh đại diện lấy từ cơ sở dữ liệu.
 *
 * <p>Công khai như mọi ảnh đại diện khác: tên và ảnh của Reader hiện trên trang
 * danh sách mà khách chưa đăng nhập cũng xem được. Chỉ trả đúng khối byte đã
 * lưu, không có thông tin nào khác của người dùng.
 */
@RestController
@RequiredArgsConstructor
public class AvatarController {

    private final UserAvatarRepository userAvatarRepository;

    @GetMapping("/api/v1/users/{userId}/avatar")
    @PreAuthorize("permitAll()")
    public ResponseEntity<byte[]> avatar(@PathVariable UUID userId) {
        return userAvatarRepository.findById(userId)
                .map(a -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(a.getContentType()))
                        // Đường dẫn đã kèm ?v=<mốc thời gian>, đổi ảnh là đổi
                        // đường dẫn, nên cache lâu được mà không sợ ảnh cũ dính lại.
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .body(a.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
