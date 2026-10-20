package com.exe.astratarot.domain.dto.khuyenmai;

import com.exe.astratarot.domain.entity.KhuyenMai;

import java.time.Instant;
import java.util.UUID;

/**
 * Một đợt khuyến mãi, như giao diện nhìn thấy.
 *
 * @param dangChay tính sẵn ở máy chủ, giao diện không tự so ngày
 */
public record KhuyenMaiResponse(
        UUID id,
        String ten,
        String loiChuc,
        String loaiGiam,
        long giaTri,
        Long giamToiDa,
        Instant batDau,
        Instant ketThuc,
        boolean dangBat,
        boolean dangChay
) {
    public static KhuyenMaiResponse tu(KhuyenMai k, Instant luc) {
        return new KhuyenMaiResponse(
                k.getId(),
                k.getTen(),
                k.getLoiChuc(),
                k.getLoaiGiam() != null ? k.getLoaiGiam().name() : null,
                k.getGiaTri() != null ? k.getGiaTri() : 0L,
                k.getGiamToiDa(),
                k.getBatDau(),
                k.getKetThuc(),
                Boolean.TRUE.equals(k.getDangBat()),
                k.dangChay(luc));
    }
}
