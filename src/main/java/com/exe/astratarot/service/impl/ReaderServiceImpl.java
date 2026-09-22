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

    /**
     * Nộp hồ sơ Reader.
     *
     * <h3>Nhân viên không phải xếp hàng</h3>
     *
     * <p>Người đã là STAFF thì chính bạn đã cất họ lên làm nhân viên — bắt họ
     * nộp đơn để lại chính bạn duyệt là thủ tục rỗng. Họ điền form và có hồ sơ
     * ngay. Người ngoài (USER) vẫn phải qua duyệt: đây là sàn có ký quỹ và
     * tiền thật, ai cũng tự xưng Reader rồi nhận tiền khách là rủi ro.
     *
     * <p>Vẫn LƯU một bản ghi đơn ở trạng thái APPROVED cho trường hợp nhân
     * viên, chứ không bỏ qua bảng đơn: nó là dấu vết kiểm toán trả lời "hồ sơ
     * này có từ đâu, ai duyệt, lúc nào" — câu hỏi sẽ được hỏi khi có tranh
     * chấp tiền, và lúc đó không có gì để tra là tệ.
     */
    @Transactional
    public ApplyOutcome apply(UUID userId, ApplyReaderRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Hỏi đúng câu: "đã có hồ sơ Reader chưa?", không phải "vai trò có
        // phải READER không". Hai câu đó từng bị coi là một, và khoảng lệch
        // giữa chúng chính là chỗ nhân viên bị kẹt không nộp đơn được.
        if (readerProfileRepository.existsByUserId(userId)) {
            throw new AlreadyReaderException();
        }

        if (readerApplicationRepository.existsByUserIdAndStatus(
                userId, ReaderApplication.ApplicationStatus.PENDING)) {
            throw new AlreadyAppliedException();
        }

        boolean laNhanVien = user.getRole() == UserRole.STAFF;

        ReaderApplication application = ReaderApplication.builder()
                .user(user)
                .bio(request.getBio())
                .experience(request.getExperience() != null ? request.getExperience() : 0)
                .specialties(request.getSpecialties() != null ? request.getSpecialties() : new String[]{})
                .status(laNhanVien
                        ? ReaderApplication.ApplicationStatus.APPROVED
                        : ReaderApplication.ApplicationStatus.PENDING)
                .build();

        if (laNhanVien) {
            application.setReviewedBy(user);
            application.setReviewedAt(java.time.Instant.now());
        }
        readerApplicationRepository.save(application);

        if (!laNhanVien) {
            return ApplyOutcome.SUBMITTED;
        }

        taoHoSoReader(user, application);
        return ApplyOutcome.APPROVED_IMMEDIATELY;
    }

    /**
     * Dựng ReaderProfile từ nội dung đơn.
     *
     * <p>Tách ra vì có HAI lối vào (nhân viên tự tạo, và quản trị duyệt đơn
     * người ngoài). Để hai lối tự dựng lấy thì sớm muộn một lối quên gán
     * verifiedAt hoặc quên nâng vai trò, và người dùng rơi vào trạng thái nửa
     * vời không ai gỡ được.
     */
    private void taoHoSoReader(User user, ReaderApplication application) {
        // Nâng lên STAFF — KHÔNG phải READER. Vai trò READER là tập con thật
        // sự của STAFF: nó không thêm quyền nào, chỉ bớt SUPPORT_RESPOND. Gán
        // READER cho một nhân viên vừa được duyệt là giáng quyền họ, khiến họ
        // vẫn nhìn thấy hàng chờ hỗ trợ mà không trả lời khách được nữa.
        // "Là Reader" được thể hiện bằng SỰ TỒN TẠI của ReaderProfile, không
        // phải bằng vai trò — xem migration V2_15.
        user.setRole(UserRole.STAFF);

        readerProfileRepository.save(ReaderProfile.builder()
                .user(user)
                .bio(application.getBio())
                .yearsExperience(application.getExperience())
                .specialties(application.getSpecialties())
                .verifiedAt(java.time.Instant.now())
                .build());

        userRepository.save(user);
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
            taoHoSoReader(user, application);

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