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
    private final com.exe.astratarot.service.NotificationService notificationService;
    private final com.exe.astratarot.domain.mapper.ReaderApplicationMapper readerApplicationMapper;

    /**
     * Đơn gần nhất của chính người đang đăng nhập.
     *
     * Trang quản trị đã có danh sách đơn chờ, nhưng người nộp thì không có gì
     * để xem: nộp xong là mù tịt. Trả về đơn mới nhất bất kể trạng thái để giao
     * diện hiện được "đang chờ" / "đã duyệt" / "bị từ chối kèm lý do".
     */
    @Transactional(readOnly = true)
    public java.util.Optional<com.exe.astratarot.domain.dto.reader.ReaderApplicationResponse> myApplication(UUID userId) {
        return readerApplicationRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(readerApplicationMapper::toResponse);
    }

    @Transactional
    public void apply(UUID userId, ApplyReaderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Hỏi "đã có hồ sơ Reader chưa", KHÔNG hỏi "có phải Nhân viên không".
        //
        // Hai câu đó không đồng nghĩa, và khoảng lệch giữa chúng từng tạo ra
        // một lối cụt: tài khoản Nhân viên chưa có hồ sơ Reader thì không nộp
        // đơn được (bị chặn ở đây), mà cũng không ai tạo hộ được — mắc kẹt
        // vĩnh viễn ở màn "Bạn chưa có hồ sơ Reader".
        if (readerProfileRepository.findByUserId(userId).isPresent()) {
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
            user.setRole(UserRole.STAFF);

            ReaderProfile profile = ReaderProfile.builder()
                    .user(user)
                    .bio(application.getBio())
                    .yearsExperience(application.getExperience())
                    .specialties(application.getSpecialties())
                    .verifiedAt(java.time.Instant.now())
                    .build();

            readerProfileRepository.save(profile);
            userRepository.save(user);

            notificationService.push(user,
                    com.exe.astratarot.service.NotificationTypes.READER_APPLICATION_APPROVED,
                    "Hồ sơ Reader đã được duyệt",
                    "Từ giờ bạn nhận được lịch hẹn. Nhớ khai báo khung giờ rảnh để khách đặt được.",
                    java.util.Map.of("applicationId", application.getId().toString()));
        } else if ("REJECTED".equalsIgnoreCase(request.getAction())) {
            application.setStatus(ReaderApplication.ApplicationStatus.REJECTED);
            application.setReviewedBy(reviewer);
            application.setReviewedAt(java.time.Instant.now());
            application.setRejectionReason(request.getRejectionReason());

            notificationService.push(application.getUser(),
                    com.exe.astratarot.service.NotificationTypes.READER_APPLICATION_REJECTED,
                    "Hồ sơ Reader chưa được duyệt",
                    request.getRejectionReason() == null || request.getRejectionReason().isBlank()
                            ? "Hồ sơ của bạn chưa được duyệt lần này."
                            : request.getRejectionReason(),
                    java.util.Map.of("applicationId", application.getId().toString()));
        } else {
            throw new IllegalArgumentException("Invalid action: " + request.getAction());
        }

        readerApplicationRepository.save(application);
    }
}