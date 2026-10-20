package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.marketing.HanhTrinhTheoNguon;
import com.exe.astratarot.domain.dto.marketing.TrackMarketingEventRequest;
import com.exe.astratarot.domain.entity.MarketingEvent;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.repository.MarketingEventRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.MarketingEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketingEventServiceImpl implements MarketingEventService {

    private final MarketingEventRepository eventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void track(UUID userIdOrNull, TrackMarketingEventRequest request) {
        User user = null;
        if (userIdOrNull != null) {
            user = userRepository.findById(userIdOrNull).orElse(null);
        }
        eventRepository.save(MarketingEvent.builder()
                .user(user)
                .eventName(request.eventName().trim())
                .path(blankToNull(request.path()))
                .utmSource(blankToNull(request.utmSource()))
                .utmMedium(blankToNull(request.utmMedium()))
                .utmCampaign(blankToNull(request.utmCampaign()))
                .metadata(blankToNull(request.metadata()))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> countsLast30Days() {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] row : eventRepository.countByEventSince(since)) {
            String name = row[0] != null ? row[0].toString() : "unknown";
            long c = row[1] instanceof Number n ? n.longValue() : 0L;
            out.put(name, c);
        }
        return out;
    }

    /** Nguồn rỗng — phần lớn là người tự gõ địa chỉ, hoặc link chưa gắn UTM. */
    static final String KHONG_RO_NGUON = "(không rõ nguồn)";

    @Override
    @Transactional(readOnly = true)
    public List<HanhTrinhTheoNguon> hanhTrinhTheoNguon30Ngay() {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        // Gộp hai truy vấn lại ở đây chứ không JOIN trong SQL: đếm lượt và
        // đếm người là hai phép gộp khác nhau (một theo (nguồn, hoạt động),
        // một theo nguồn), nhét chung một câu là phải DISTINCT lồng nhau mà
        // vẫn dễ ra số sai.
        Map<String, Long> nguoiTheoNguon = new LinkedHashMap<>();
        for (Object[] row : eventRepository.countUsersBySourceSince(since)) {
            nguoiTheoNguon.put(tenNguon(row[0]), soLuong(row[1]));
        }

        Map<String, Map<String, Long>> theoNguon = new LinkedHashMap<>();
        Map<String, Long> tongTheoNguon = new LinkedHashMap<>();
        for (Object[] row : eventRepository.countBySourceAndEventSince(since)) {
            String nguon = tenNguon(row[0]);
            String hoatDong = row[1] != null ? row[1].toString() : "unknown";
            long so = soLuong(row[2]);
            // Truy vấn đã sắp theo lượt giảm dần, nên LinkedHashMap giữ đúng
            // thứ tự ấy trong từng nguồn.
            theoNguon.computeIfAbsent(nguon, k -> new LinkedHashMap<>()).merge(hoatDong, so, Long::sum);
            tongTheoNguon.merge(nguon, so, Long::sum);
        }

        List<HanhTrinhTheoNguon> ketQua = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> e : theoNguon.entrySet()) {
            ketQua.add(new HanhTrinhTheoNguon(
                    e.getKey(),
                    tongTheoNguon.getOrDefault(e.getKey(), 0L),
                    nguoiTheoNguon.getOrDefault(e.getKey(), 0L),
                    e.getValue()));
        }
        ketQua.sort(Comparator.comparingLong(HanhTrinhTheoNguon::tongLuot).reversed());
        return ketQua;
    }

    private static String tenNguon(Object raw) {
        if (raw == null) return KHONG_RO_NGUON;
        String s = raw.toString().trim();
        return s.isEmpty() ? KHONG_RO_NGUON : s;
    }

    private static long soLuong(Object raw) {
        return raw instanceof Number n ? n.longValue() : 0L;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
