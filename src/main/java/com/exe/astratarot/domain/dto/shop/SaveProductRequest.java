package com.exe.astratarot.domain.dto.shop;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Dùng chung cho tạo mới và sửa. Slug sinh từ tên, chỉ sinh một lần lúc tạo. */
@Data
public class SaveProductRequest {

    @NotBlank(message = "Phải nhập tên sản phẩm")
    @Size(max = 200)
    private String name;

    private String description;

    @NotNull(message = "Phải nhập giá tham khảo")
    @Min(value = 0, message = "Giá không âm")
    private Long price;

    /** Giá gạch ngang khi sàn đang giảm giá. */
    private Long compareAtPrice;

    private String imageUrl;

    private Boolean imageIsIllustrative;

    /** Bỏ trống thì sản phẩm hiện nhưng không có nút mua. */
    private String affiliateUrl;

    /** SHOPEE, LAZADA, TIKI, TIKTOK hoặc OTHER. Bỏ trống mặc định SHOPEE. */
    private String affiliatePlatform;

    @DecimalMin(value = "0.0", message = "Tỉ lệ hoa hồng không âm")
    @DecimalMax(value = "100.0", message = "Tỉ lệ hoa hồng tối đa 100%")
    private BigDecimal commissionPercent;

    private Boolean featured;

    private Boolean active;

    private UUID categoryId;
}
