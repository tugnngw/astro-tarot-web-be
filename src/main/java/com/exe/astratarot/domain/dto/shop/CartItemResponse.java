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
public class CartItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productSlug;
    private String imageUrl;
    private Long unitPrice;
    private Integer quantity;
    /** unitPrice * quantity, tính sẵn để FE khỏi lặp lại phép nhân. */
    private Long lineTotal;
    /** Tồn kho hiện tại, để FE chặn tăng quá số lượng còn lại. */
    private Integer stock;
}
