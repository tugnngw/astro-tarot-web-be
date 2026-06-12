package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.reader.*;
import com.exe.astratarot.domain.dto.common.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ReaderProfileService {
    void updateProfile(UUID userId, UpdateProfileRequest request);
    ApiResponse<ReaderProfileResponse> getMyProfile(UUID userId);
    ApiResponse<List<ReaderProfileResponse>> getAllVerifiedReaders();
    ApiResponse<ReaderProfileResponse> getReaderById(UUID readerId);
    ApiResponse<List<Map<String, Object>>> getAllPendingApplications(UUID adminId);
}