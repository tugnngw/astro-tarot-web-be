package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.shop.AffiliateStatsResponse;
import com.exe.astratarot.domain.dto.shop.ProductResponse;
import com.exe.astratarot.domain.dto.shop.SaveProductRequest;

import java.util.UUID;

public interface ProductAdminService {

    ProductResponse create(UUID actorId, SaveProductRequest request);

    ProductResponse update(UUID actorId, UUID productId, SaveProductRequest request);

    /** Ẩn hoặc hiện lại. Cố ý không có xoá cứng — xem chú thích ở impl. */
    ProductResponse setActive(UUID actorId, UUID productId, boolean active);

    /** Ghi nhận lượt bấm rồi trả về đường dẫn sang sàn. */
    String registerClick(UUID userIdOrNull, String slug, String referrer);

    AffiliateStatsResponse stats(int days);
}
