package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.reader.ApplyReaderRequest;
import com.exe.astratarot.domain.dto.reader.ReviewReaderRequest;

import java.util.UUID;

public interface ReaderService {

    void apply(UUID userId, ApplyReaderRequest request);

    void review(UUID reviewerId, UUID applicationId, ReviewReaderRequest request);
}