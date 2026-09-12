package com.exe.astratarot.service.impl;

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
import java.util.LinkedHashMap;
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

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
