package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.reader.CreateAvailabilityRequest;
import com.exe.astratarot.domain.dto.reader.ReaderAvailabilityResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;

import java.util.List;
import java.util.UUID;

public interface ReaderAvailabilityService {
    void create(UUID userId, CreateAvailabilityRequest request);
    ApiResponse<List<ReaderAvailabilityResponse>> getWeeklySchedule(UUID userId);
    void delete(UUID userId, UUID availabilityId);
}