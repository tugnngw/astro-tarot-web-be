package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.marketing.HanhTrinhTheoNguon;
import com.exe.astratarot.domain.dto.marketing.TrackMarketingEventRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface MarketingEventService {
    void track(UUID userIdOrNull, TrackMarketingEventRequest request);

    Map<String, Long> countsLast30Days();

    /**
     * Khách đến từ đâu, rồi làm gì — 30 ngày gần nhất.
     *
     * <p>Sắp theo số lượt giảm dần, nên nguồn mang về nhiều nhất nằm trên
     * cùng.
     */
    List<HanhTrinhTheoNguon> hanhTrinhTheoNguon30Ngay();
}
