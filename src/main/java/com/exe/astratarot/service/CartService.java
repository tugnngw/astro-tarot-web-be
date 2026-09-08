package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.shop.AddToCartRequest;
import com.exe.astratarot.domain.dto.shop.CartResponse;
import com.exe.astratarot.domain.dto.shop.UpdateCartItemRequest;

import java.util.UUID;

public interface CartService {
    CartResponse getCart(UUID userId);
    CartResponse addItem(UUID userId, AddToCartRequest request);
    CartResponse updateItem(UUID userId, UUID cartItemId, UpdateCartItemRequest request);
    CartResponse removeItem(UUID userId, UUID cartItemId);
    void clear(UUID userId);
}
