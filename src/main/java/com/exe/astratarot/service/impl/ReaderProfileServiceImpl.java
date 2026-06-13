package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.reader.ReaderAvailabilityResponse;
import com.exe.astratarot.domain.dto.reader.ReaderUnavailableDateResponse;
import com.exe.astratarot.domain.dto.reader.UpdateProfileRequest;
import com.exe.astratarot.domain.dto.reader.ReaderProfileResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.domain.entity.ReaderAvailability;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.ReaderUnavailableDate;
import com.exe.astratarot.domain.mapper.ReaderAvailabilityMapper;
import com.exe.astratarot.domain.mapper.ReaderProfileMapper;
import com.exe.astratarot.domain.mapper.ReaderUnavailableDateMapper;
import com.exe.astratarot.exception.ReaderNotVerifiedException;
import com.exe.astratarot.repository.ReaderApplicationRepository;
import com.exe.astratarot.repository.ReaderAvailabilityRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReaderUnavailableDateRepository;
import com.exe.astratarot.service.ReaderProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReaderProfileServiceImpl implements ReaderProfileService {

    private final ReaderProfileRepository readerProfileRepository;
    private final ReaderAvailabilityRepository readerAvailabilityRepository;
    private final ReaderUnavailableDateRepository readerUnavailableDateRepository;
    private final ReaderApplicationRepository readerApplicationRepository;

    private final ReaderProfileMapper readerProfileMapper;
    private final ReaderAvailabilityMapper readerAvailabilityMapper;
    private final ReaderUnavailableDateMapper readerUnavailableDateMapper;

    @Override
    @Transactional
    public void updateProfile(UUID userId, UpdateProfileRequest request) {
        ReaderProfile profile = readerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        readerProfileMapper.updateFromRequest(request, profile);
        readerProfileRepository.save(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<ReaderProfileResponse> getMyProfile(UUID userId) {
        ReaderProfile profile = readerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        ReaderProfileResponse response = mapToResponse(profile);
        return ApiResponse.success(response);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<ReaderProfileResponse>> getAllVerifiedReaders() {
        List<ReaderProfile> profiles = readerProfileRepository.findAllByVerifiedAtIsNotNull();
        List<ReaderProfileResponse> responses = profiles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<ReaderProfileResponse> getReaderById(UUID readerId) {
        ReaderProfile profile = readerProfileRepository.findById(readerId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        ReaderProfileResponse response = mapToResponse(profile);
        return ApiResponse.success(response);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> getAllPendingApplications(UUID adminId) {
        List<ReaderApplication> applications = readerApplicationRepository.findAll().stream()
                .filter(app -> app.getStatus() == ReaderApplication.ApplicationStatus.PENDING)
                .collect(Collectors.toList());
        List<Map<String, Object>> result = applications.stream()
                .map(app -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", app.getId());
                    map.put("userId", app.getUser().getId());
                    map.put("username", app.getUser().getUsername());
                    map.put("bio", app.getBio());
                    map.put("status", app.getStatus().toString());
                    map.put("created_at", app.getCreatedAt());
                    return map;
                })
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    private ReaderProfileResponse mapToResponse(ReaderProfile profile) {
        List<ReaderAvailabilityResponse> availabilityList =
            readerAvailabilityRepository.findByReaderId(profile.getId()).stream()
                .map(readerAvailabilityMapper::toResponse)
                .collect(Collectors.toList());

        List<ReaderUnavailableDateResponse> unavailableDates =
            readerUnavailableDateRepository.findByReaderId(profile.getId()).stream()
                .map(readerUnavailableDateMapper::toResponse)
                .collect(Collectors.toList());

        ReaderProfileResponse response = readerProfileMapper.toResponse(profile);
        response.setWeeklyAvailability(availabilityList);
        response.setUnavailableDates(unavailableDates);
        return response;
    }
}