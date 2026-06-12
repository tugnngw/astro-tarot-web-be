package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.reader.AddUnavailableDateRequest;
import com.exe.astratarot.domain.dto.reader.ReaderUnavailableDateResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;

import java.util.List;
import java.util.UUID;

public interface ReaderUnavailableDateService {
    void addDate(UUID userId, AddUnavailableDateRequest request);
    ApiResponse<List<ReaderUnavailableDateResponse>> getUnavailableDates(UUID userId);
    void removeDate(UUID userId, UUID unavailableDateId);
}