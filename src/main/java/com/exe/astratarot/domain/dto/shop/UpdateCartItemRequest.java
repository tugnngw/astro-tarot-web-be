package com.exe.astratarot.domain.dto.shop;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCartItemRequest {
    @NotNull(message = "quantity là bắt buộc")
    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer quantity;
}
