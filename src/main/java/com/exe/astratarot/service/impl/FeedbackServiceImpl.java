package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.feedback.SubmitFeedbackRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserFeedback;
import com.exe.astratarot.repository.UserFeedbackRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final Set<String> SOURCES = Set.of(
            "TAROT_AI", "BOOKING", "GENERAL", "LANDING");

    private final UserFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void submit(UUID userIdOrNull, SubmitFeedbackRequest request) {
        String source = request.source().trim().toUpperCase();
        if (!SOURCES.contains(source)) {
            throw new IllegalArgumentException("Nguồn phản hồi không hợp lệ: " + request.source());
        }
        User user = null;
        if (userIdOrNull != null) {
            user = userRepository.findById(userIdOrNull).orElse(null);
        }
        feedbackRepository.save(UserFeedback.builder()
                .user(user)
                .source(source)
                .nps(request.nps())
                .rating(request.rating())
                .comment(blankToNull(request.comment()))
                .utmSource(blankToNull(request.utmSource()))
                .utmMedium(blankToNull(request.utmMedium()))
                .utmCampaign(blankToNull(request.utmCampaign()))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSubmitted(UUID userId) {
        return userId != null && feedbackRepository.countByUserId(userId) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return feedbackRepository.count();
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
