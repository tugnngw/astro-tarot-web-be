package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.GooglePlayBillingService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Đổi lượt mua trên Google Play lấy gói AI.
 *
 * <p>Chỉ app Android gọi. Web vẫn mua bằng ví qua PayOS như cũ — chính sách
 * của Google chỉ ràng buộc thứ bán TRONG ứng dụng Android.
 *
 * <p>Không khai {@code @PreAuthorize} nào: SecurityConfig đã
 * {@code .anyRequest().authenticated()}, nên endpoint này mặc định đòi đăng
 * nhập. Mọi quyền sâu hơn là thừa — ai mua thì người ấy nhận gói, lấy đúng
 * từ phiên đăng nhập chứ không nhận userId từ thân yêu cầu.
 */
@RestController
@RequestMapping("/api/v1/billing/google-play")
@RequiredArgsConstructor
public class GooglePlayBillingController {

    private final GooglePlayBillingService googlePlayBillingService;

    public record RedeemRequest(
            @NotBlank String productId,
            @NotBlank String purchaseToken) {
    }

    /**
     * Xác minh với Google rồi cấp gói.
     *
     * <p>Gọi lại nhiều lần với cùng một {@code purchaseToken} là chuyện bình
     * thường, không phải lỗi: app phát lại những lượt mua chưa xác nhận mỗi
     * lần mở lại. Lần sau trả về đúng kết quả lần đầu.
     */
    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<Map<String, UUID>>> redeem(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestBody RedeemRequest body) {
        UUID purchaseId = googlePlayBillingService.doiLayGoi(
                me.getUser().getId(), body.productId(), body.purchaseToken());
        return ResponseEntity.ok(ApiResponse.success(Map.of("purchaseId", purchaseId)));
    }
}
