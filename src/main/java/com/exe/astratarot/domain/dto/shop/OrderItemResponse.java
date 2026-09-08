package com.exe.astratarot.domain.dto.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private UUID id;
    /** Null nếu sản phẩm đã bị gỡ khỏi catalog sau khi đặt. */
    private UUID productId;
    private String productName;
    private String productImageUrl;
    private Long unitPrice;
    private Integer quantity;
    private Long lineTotal;
}
