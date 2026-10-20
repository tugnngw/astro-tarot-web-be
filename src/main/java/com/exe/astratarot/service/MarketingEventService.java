package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.marketing.HanhTrinhKhach;
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

    /**
     * Những khách gần đây nhất, mỗi người kèm hành trình theo thứ tự.
     *
     * <p>Trả lời câu mà bảng gộp theo nguồn không trả lời được: không phải
     * "Facebook mang về 21 lượt" mà "khách này vào từ Facebook, xem trang
     * chủ, mở Tarot, rút một quẻ, rồi đi".
     */
    List<HanhTrinhKhach> hanhTrinhGanDay(int soKhach);
}
