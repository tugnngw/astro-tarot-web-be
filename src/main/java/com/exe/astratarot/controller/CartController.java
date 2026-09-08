package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.shop.AddToCartRequest;
import com.exe.astratarot.domain.dto.shop.CartResponse;
import com.exe.astratarot.domain.dto.shop.UpdateCartItemRequest;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Giỏ hàng của người dùng đang đăng nhập.
 * Mọi thao tác đều trả về giỏ đầy đủ sau khi cập nhật, để FE chỉ cần
 * thay nguyên state thay vì tự suy ra kết quả.
 */
@RestController
@RequestMapping("/api/v1/shop/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(ApiResponse.success(cartService.getCart(userId)));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AddToCartRequest request) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(
                ApiResponse.success("Đã thêm vào giỏ", cartService.addItem(userId, request)));
    }

    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID cartItemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(
                ApiResponse.success("Đã cập nhật giỏ", cartService.updateItem(userId, cartItemId, request)));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID cartItemId) {
        UUID userId = userDetails.getUser().getId();
        return ResponseEntity.ok(
                ApiResponse.success("Đã xoá khỏi giỏ", cartService.removeItem(userId, cartItemId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clear(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        cartService.clear(userDetails.getUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Đã xoá toàn bộ giỏ hàng", null));
    }
}
