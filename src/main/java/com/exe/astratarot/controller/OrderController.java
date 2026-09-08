package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.shop.CheckoutRequest;
import com.exe.astratarot.domain.dto.shop.OrderResponse;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Đơn hàng của người dùng đang đăng nhập.
 *
 * Checkout hiện chỉ tạo đơn ở trạng thái PENDING / UNPAID và trừ tồn kho;
 * chưa nối cổng thanh toán. Khi tích hợp VNPay/Momo thì thêm bước tạo
 * payment URL ở đây và cập nhật payment_status qua IPN callback.
 */
@RestController
@RequestMapping("/api/v1/shop/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CheckoutRequest request) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(
                ApiResponse.success("Đặt hàng thành công", orderService.checkout(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> myOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success(orderService.getMyOrders(userId)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> orderDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success(orderService.getMyOrder(userId, orderId)));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @RequestBody(required = false) Map<String, String> body) {
        UUID userId = userDetails.getUser().getId();
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(
                ApiResponse.success("Đã huỷ đơn", orderService.cancel(userId, orderId, reason)));
    }
}
