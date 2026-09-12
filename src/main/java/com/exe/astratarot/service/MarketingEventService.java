package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.marketing.TrackMarketingEventRequest;

import java.util.Map;
import java.util.UUID;

public interface MarketingEventService {
    void track(UUID userIdOrNull, TrackMarketingEventRequest request);

    Map<String, Long> countsLast30Days();
}
