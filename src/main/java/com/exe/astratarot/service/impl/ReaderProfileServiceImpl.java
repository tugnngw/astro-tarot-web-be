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

    @Override
    @Transactional
    public void updateProfile(UUID userId, UpdateProfileRequest request) {
        ReaderProfile profile = readerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        if (request.getBio() != null) profile.setBio(request.getBio());
        if (request.getSpecialties() != null) profile.setSpecialties(request.getSpecialties());
        if (request.getYearsExperience() != null) profile.setYearsExperience(request.getYearsExperience());
        if (request.getPricePer15m() != null) profile.setPricePer15m(request.getPricePer15m());
        if (request.getPricePer30m() != null) profile.setPricePer30m(request.getPricePer30m());
        if (request.getPricePer60m() != null) profile.setPricePer60m(request.getPricePer60m());

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
                .map(avail -> ReaderAvailabilityResponse.builder()
                    .id(avail.getId())
                    .dayOfWeek(avail.getDayOfWeek())
                    .startTime(avail.getStartTime())
                    .endTime(avail.getEndTime())
                    .isActive(avail.getActive())
                    .build())
                .collect(Collectors.toList());

        List<ReaderUnavailableDateResponse> unavailableDates = 
            readerUnavailableDateRepository.findByReaderId(profile.getId()).stream()
                .map(date -> ReaderUnavailableDateResponse.builder()
                    .id(date.getId())
                    .unavailableDate(date.getUnavailableDate())
                    .reason(date.getReason())
                    .build())
                .collect(Collectors.toList());

        return ReaderProfileResponse.builder()
                .id(profile.getId())
                .username(profile.getUser().getUsername())
                .bio(profile.getBio())
                .specialties(profile.getSpecialties())
                .yearsExperience(profile.getYearsExperience())
                .pricePer15m(profile.getPricePer15m())
                .pricePer30m(profile.getPricePer30m())
                .pricePer60m(profile.getPricePer60m())
                .rating(profile.getRating())
                .totalReviews(profile.getTotalReviews())
                .isAvailable(profile.getAvailable())
                .weeklyAvailability(availabilityList)
                .unavailableDates(unavailableDates)
                .build();
    }
}