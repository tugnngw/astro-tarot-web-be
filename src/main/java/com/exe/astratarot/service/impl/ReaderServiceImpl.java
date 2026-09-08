package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.repository.ReaderApplicationRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.ReaderService;
import com.exe.astratarot.domain.dto.reader.ApplyReaderRequest;
import com.exe.astratarot.domain.dto.reader.ReviewReaderRequest;
import com.exe.astratarot.exception.AlreadyAppliedException;
import com.exe.astratarot.exception.AlreadyReaderException;
import com.exe.astratarot.exception.ApplicationNotFoundException;
import com.exe.astratarot.exception.InvalidApplicationStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReaderServiceImpl implements ReaderService {

    private final ReaderApplicationRepository readerApplicationRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public void apply(UUID userId, ApplyReaderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole() == UserRole.READER) {
            throw new AlreadyReaderException();
        }

        if (readerApplicationRepository.existsByUserIdAndStatus(
                userId, ReaderApplication.ApplicationStatus.PENDING)) {
            throw new AlreadyAppliedException();
        }

        ReaderApplication application = ReaderApplication.builder()
                .user(user)
                .bio(request.getBio())
                .experience(request.getExperience() != null ? request.getExperience() : 0)
                .specialties(request.getSpecialties() != null ? request.getSpecialties() : new String[]{})
                .status(ReaderApplication.ApplicationStatus.PENDING)
                .build();

        readerApplicationRepository.save(application);
    }

    @Transactional
    public void review(UUID reviewerId, UUID applicationId, ReviewReaderRequest request) {
        ReaderApplication application = readerApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ApplicationNotFoundException());

        if (application.getStatus() != ReaderApplication.ApplicationStatus.PENDING) {
            throw new InvalidApplicationStatusException();
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if ("APPROVED".equalsIgnoreCase(request.getAction())) {
            application.setStatus(ReaderApplication.ApplicationStatus.APPROVED);
            application.setReviewedBy(reviewer);
            application.setReviewedAt(java.time.Instant.now());

            User user = application.getUser();
            user.setRole(UserRole.READER);

            ReaderProfile profile = ReaderProfile.builder()
                    .user(user)
                    .bio(application.getBio())
                    .yearsExperience(application.getExperience())
                    .specialties(application.getSpecialties())
                    .verifiedAt(java.time.Instant.now())
                    .build();

            readerProfileRepository.save(profile);
            userRepository.save(user);
        } else if ("REJECTED".equalsIgnoreCase(request.getAction())) {
            application.setStatus(ReaderApplication.ApplicationStatus.REJECTED);
            application.setReviewedBy(reviewer);
            application.setReviewedAt(java.time.Instant.now());
            application.setRejectionReason(request.getRejectionReason());
        } else {
            throw new IllegalArgumentException("Invalid action: " + request.getAction());
        }

        readerApplicationRepository.save(application);
    }
}