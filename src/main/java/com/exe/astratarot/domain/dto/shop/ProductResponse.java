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
public class ProductResponse {
    private UUID id;
    private String name;
    private String slug;
    private String description;
    private Long price;
    private Long compareAtPrice;
    private Integer stock;
    private String imageUrl;
    private Boolean imageIsIllustrative;
    /** Đường dẫn sang sàn. NULL nghĩa là chưa gắn link — giao diện ẩn nút mua. */
    private String affiliateUrl;
    private String affiliatePlatform;
    private java.math.BigDecimal commissionPercent;
    private Long clickCount;
    private Boolean featured;
    private String categoryName;
    private String categorySlug;
}
