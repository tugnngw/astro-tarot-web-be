package com.exe.astratarot.domain.dto.khuyenmai;

/**
 * Giá một gói sau khi áp đợt khuyến mãi đang chạy.
 *
 * @param giaGoc    giá niêm yết
 * @param tienGiam  số tiền được bớt, 0 khi không có đợt nào
 * @param giaCuoi   số thật sự phải trả
 * @param tenDot    tên đợt đang áp, {@code null} khi không có
 */
public record GiaSauGiam(long giaGoc, long tienGiam, long giaCuoi, String tenDot) {

    public static GiaSauGiam khongGiam(long giaGoc) {
        return new GiaSauGiam(giaGoc, 0L, giaGoc, null);
    }
}
