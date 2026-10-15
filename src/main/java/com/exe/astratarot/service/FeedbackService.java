package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.feedback.AdminFeedbackItemResponse;
import com.exe.astratarot.domain.dto.feedback.FeedbackStatsResponse;
import com.exe.astratarot.domain.dto.feedback.SubmitFeedbackRequest;
import com.exe.astratarot.domain.dto.feedback.UserFeedbackResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface FeedbackService {

    UserFeedbackResponse submit(UUID userIdOrNull, SubmitFeedbackRequest request);

    boolean hasSubmitted(UUID userId);

    Optional<UserFeedbackResponse> getMyFeedback(UUID userId);

    long countAll();

    FeedbackStatsResponse getStats();

    Page<AdminFeedbackItemResponse> listAdminFeedback(Integer rating, String source, String keyword, Pageable pageable);
}
