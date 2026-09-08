package com.exe.astratarot.domain.dto.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private String orderCode;
    private String status;
    private String paymentStatus;
    private Long subtotal;
    private Long shippingFee;
    private Long totalAmount;
    private String receiverName;
    private String receiverPhone;
    private String shippingAddress;
    private String note;
    private String cancelReason;
    private List<OrderItemResponse> items;
    private Instant createdAt;
}
