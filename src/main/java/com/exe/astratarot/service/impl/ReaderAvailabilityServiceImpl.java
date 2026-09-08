package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.reader.CreateAvailabilityRequest;
import com.exe.astratarot.domain.dto.reader.ReaderAvailabilityResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.entity.ReaderAvailability;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.exception.AvailabilityConflictException;
import com.exe.astratarot.exception.InvalidAvailabilityTimeException;
import com.exe.astratarot.exception.ReaderNotVerifiedException;
import com.exe.astratarot.repository.ReaderAvailabilityRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.service.ReaderAvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReaderAvailabilityServiceImpl implements ReaderAvailabilityService {

    private final ReaderAvailabilityRepository readerAvailabilityRepository;
    private final ReaderProfileRepository readerProfileRepository;

    @Override
    @Transactional
    public void create(UUID userId, CreateAvailabilityRequest request) {
        ReaderProfile profile = readerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new InvalidAvailabilityTimeException();
        }

        boolean hasConflict = readerAvailabilityRepository.findByReaderId(profile.getId()).stream()
                .filter(avail -> avail.getActive() && avail.getDayOfWeek().equals(request.getDayOfWeek()))
                .anyMatch(avail -> 
                    request.getStartTime().isBefore(avail.getEndTime()) && request.getEndTime().isAfter(avail.getStartTime())
                );

        if (hasConflict) {
            throw new AvailabilityConflictException();
        }

        ReaderAvailability availability = ReaderAvailability.builder()
                .reader(profile)
                .dayOfWeek(request.getDayOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .active(true)
                .build();

        readerAvailabilityRepository.save(availability);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<ReaderAvailabilityResponse>> getWeeklySchedule(UUID userId) {
        ReaderProfile profile = readerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        List<ReaderAvailabilityResponse> schedule = readerAvailabilityRepository.findByReaderId(profile.getId()).stream()
                .map(avail -> ReaderAvailabilityResponse.builder()
                        .id(avail.getId())
                        .dayOfWeek(avail.getDayOfWeek())
                        .startTime(avail.getStartTime())
                        .endTime(avail.getEndTime())
                        .isActive(avail.getActive())
                        .build())
                .sorted((a, b) -> a.getDayOfWeek().compareTo(b.getDayOfWeek()))
                .collect(Collectors.toList());

        return ApiResponse.success(schedule);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID availabilityId) {
        ReaderAvailability availability = readerAvailabilityRepository.findById(availabilityId)
                .orElseThrow(() -> new RuntimeException("Availability not found"));

        ReaderProfile profile = readerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        if (!availability.getReader().getId().equals(profile.getId())) {
            throw new RuntimeException("Unauthorized access to availability");
        }

        availability.setActive(false);
        readerAvailabilityRepository.save(availability);
    }
}