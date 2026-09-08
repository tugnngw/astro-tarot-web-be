package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.shop.CheckoutRequest;
import com.exe.astratarot.domain.dto.shop.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    OrderResponse checkout(UUID userId, CheckoutRequest request);
    List<OrderResponse> getMyOrders(UUID userId);
    OrderResponse getMyOrder(UUID userId, UUID orderId);
    OrderResponse cancel(UUID userId, UUID orderId, String reason);
}
