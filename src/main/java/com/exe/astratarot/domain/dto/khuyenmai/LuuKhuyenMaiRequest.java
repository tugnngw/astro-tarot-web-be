package com.exe.astratarot.domain.dto.khuyenmai;

import jakarta.validation.constraints.*;

import java.time.Instant;

/**
 * Quản trị nhập một đợt khuyến mãi.
 *
 * <p>Ràng buộc "phần trăm phải 1..100" được kiểm ở cả ba tầng — ở đây, ở
 * tầng dịch vụ, và bằng CHECK trong cơ sở dữ liệu. Không thừa: gõ nhầm 150
 * thì khách được trả tiền để mua gói, và tiền ấy không đòi lại được.
 */
public record LuuKhuyenMaiRequest(
        @NotBlank(message = "Tên đợt là bắt buộc")
        @Size(max = 120, message = "Tên đợt tối đa 120 ký tự")
        String ten,

        String loiChuc,

        @NotNull(message = "Chọn kiểu giảm")
        @Pattern(regexp = "PHAN_TRAM|SO_TIEN", message = "Kiểu giảm phải là PHAN_TRAM hoặc SO_TIEN")
        String loaiGiam,

        @NotNull(message = "Nhập mức giảm")
        @Positive(message = "Mức giảm phải lớn hơn 0")
        Long giaTri,

        @Positive(message = "Trần giảm phải lớn hơn 0")
        Long giamToiDa,

        @NotNull(message = "Nhập ngày bắt đầu")
        Instant batDau,

        @NotNull(message = "Nhập ngày kết thúc")
        Instant ketThuc,

        Boolean dangBat
) {
}
