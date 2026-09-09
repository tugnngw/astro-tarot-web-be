package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.report.CreateReportRequest;
import com.exe.astratarot.domain.dto.report.HandleReportRequest;
import com.exe.astratarot.domain.dto.report.ReportResponse;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.Report;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.ReportStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.ReportRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.security.AdminActions;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.NotificationTypes;
import com.exe.astratarot.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Báo cáo vi phạm.
 *
 * <p>Người bị báo cáo KHÔNG được biết ai đã tố mình. Nếu biết, việc tố cáo trở
 * thành rủi ro cá nhân và sẽ không còn ai dám báo — nhất là khi người bị tố là
 * Reader mà họ vẫn phải gặp lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    @Override
    @Transactional
    public ReportResponse create(UUID reporterId, CreateReportRequest request) {
        User reporter = findUser(reporterId);
        User reported = findUser(request.getReportedUserId());

        if (reporter.getId().equals(reported.getId())) {
            throw new IllegalArgumentException("Không tự báo cáo chính mình được");
        }
        // Một người tố cùng một người nhiều lần khi việc cũ chưa xử lý xong chỉ
        // làm hàng chờ phình ra mà không thêm thông tin gì.
        if (reportRepository.existsByReporterUserIdAndReportedUserIdAndStatus(
                reporterId, reported.getId(), ReportStatus.PENDING)) {
            throw new IllegalArgumentException(
                    "Bạn đã báo cáo người này và chúng tôi đang xử lý. Chờ kết quả nhé.");
        }

        Booking booking = null;
        if (request.getBookingId() != null) {
            booking = bookingRepository.findByIdWithParties(request.getBookingId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));
            // Chỉ gắn được lịch hẹn mà mình có liên quan — nếu không, ai cũng
            // đính kèm booking của người lạ để lấy cớ.
            boolean party = booking.getUser().getId().equals(reporterId)
                    || booking.getReaderProfile().getUser().getId().equals(reporterId);
            if (!party) {
                throw new AccessDeniedException("Lịch hẹn này không liên quan tới bạn");
            }
        }

        Report report = reportRepository.save(Report.builder()
                .reporterUser(reporter)
                .reportedUser(reported)
                .booking(booking)
                .reportType(request.getReportType().trim())
                .description(request.getDescription() == null || request.getDescription().isBlank()
                        ? null
                        : request.getDescription().trim())
                .status(ReportStatus.PENDING)
                .build());

        // KHÔNG báo cho người bị tố ở bước này. Họ chỉ biết khi có kết luận, và
        // cũng không biết ai đã tố.
        log.info("Báo cáo {} : {} tố {}", report.getId(), reporterId, reported.getId());
        return toResponse(report);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> list(String status, Pageable pageable) {
        return reportRepository.search(parseStatus(status), pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long pendingCount() {
        return reportRepository.countByStatus(ReportStatus.PENDING);
    }

    @Override
    @Transactional
    public ReportResponse handle(UUID actorId, UUID reportId, HandleReportRequest request) {
        Report report = reportRepository.findByIdWithParties(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo"));

        if (report.getStatus() != ReportStatus.PENDING && report.getStatus() != ReportStatus.REVIEWED) {
            throw new IllegalArgumentException("Báo cáo này đã có kết luận rồi");
        }
        ReportStatus next = parseStatus(request.getStatus());
        if (next == null || next == ReportStatus.PENDING) {
            throw new IllegalArgumentException("Phải chọn kết luận: REVIEWED, RESOLVED hoặc REJECTED");
        }

        report.setStatus(next);
        report.setHandledBy(findUser(actorId));
        report.setHandledAt(Instant.now());
        report.setResolutionNote(request.getResolutionNote() == null || request.getResolutionNote().isBlank()
                ? null
                : request.getResolutionNote().trim());

        activityLogService.record(actorId, AdminActions.REPORT_HANDLE, AdminActions.ENTITY_REPORT,
                reportId, Map.of("status", next.name(), "reportedUserId",
                        report.getReportedUser().getId().toString()));

        // Chỉ báo cho NGƯỜI TỐ CÁO: họ đang chờ kết quả. Người bị tố nhận hình
        // thức xử lý riêng (khoá tài khoản, nhắc nhở), không phải qua đây.
        notificationService.push(report.getReporterUser(), NotificationTypes.REPORT_RESOLVED,
                "Báo cáo của bạn đã được xử lý",
                report.getResolutionNote() == null
                        ? "Chúng tôi đã xem xét và có kết luận."
                        : report.getResolutionNote(),
                Map.of("reportId", reportId.toString()));

        return toResponse(report);
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private static ReportStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ReportStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái báo cáo không hợp lệ: " + status);
        }
    }

    private ReportResponse toResponse(Report r) {
        return ReportResponse.builder()
                .id(r.getId())
                .reporterName(r.getReporterUser().getFullName())
                .reportedUserId(r.getReportedUser().getId())
                .reportedName(r.getReportedUser().getFullName())
                .reportedRole(r.getReportedUser().getRole().name())
                .bookingId(r.getBooking() == null ? null : r.getBooking().getId())
                .reportType(r.getReportType())
                .description(r.getDescription())
                .status(r.getStatus().name())
                .handledByName(r.getHandledBy() == null ? null : r.getHandledBy().getFullName())
                .handledAt(r.getHandledAt())
                .resolutionNote(r.getResolutionNote())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
