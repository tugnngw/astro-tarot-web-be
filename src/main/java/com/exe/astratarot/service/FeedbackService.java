package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.feedback.SubmitFeedbackRequest;

import java.util.UUID;

public interface FeedbackService {
    void submit(UUID userIdOrNull, SubmitFeedbackRequest request);

    boolean hasSubmitted(UUID userId);

    long countAll();
}
