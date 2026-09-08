package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.reader.AddUnavailableDateRequest;
import com.exe.astratarot.domain.dto.reader.ReaderUnavailableDateResponse;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.ReaderUnavailableDate;
import com.exe.astratarot.exception.ReaderNotVerifiedException;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReaderUnavailableDateRepository;
import com.exe.astratarot.service.ReaderUnavailableDateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReaderUnavailableDateServiceImpl implements ReaderUnavailableDateService {

    private final ReaderUnavailableDateRepository readerUnavailableDateRepository;
    private final ReaderProfileRepository readerProfileRepository;

    @Override
    @Transactional
    public void addDate(UUID userId, AddUnavailableDateRequest request) {
        ReaderProfile profile = readerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        boolean exists = readerUnavailableDateRepository.existsByReaderIdAndUnavailableDate(
                profile.getId(), request.getUnavailableDate());

        if (exists) {
            throw new RuntimeException("Unavailable date already exists");
        }

        ReaderUnavailableDate date = ReaderUnavailableDate.builder()
                .reader(profile)
                .unavailableDate(request.getUnavailableDate())
                .reason(request.getReason())
                .build();

        readerUnavailableDateRepository.save(date);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<ReaderUnavailableDateResponse>> getUnavailableDates(UUID userId) {
        ReaderProfile profile = readerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        List<ReaderUnavailableDateResponse> dates = readerUnavailableDateRepository.findByReaderId(profile.getId()).stream()
                .map(date -> ReaderUnavailableDateResponse.builder()
                        .id(date.getId())
                        .unavailableDate(date.getUnavailableDate())
                        .reason(date.getReason())
                        .build())
                .collect(Collectors.toList());

        return ApiResponse.success(dates);
    }

    @Override
    @Transactional
    public void removeDate(UUID userId, UUID unavailableDateId) {
        ReaderUnavailableDate date = readerUnavailableDateRepository.findById(unavailableDateId)
                .orElseThrow(() -> new RuntimeException("Unavailable date not found"));

        ReaderProfile profile = readerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ReaderNotVerifiedException());

        if (!date.getReader().getId().equals(profile.getId())) {
            throw new RuntimeException("Unauthorized access to unavailable date");
        }

        readerUnavailableDateRepository.delete(date);
    }
}