package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.admin.AdminStatsResponse;
import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.ReportStatus;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.ProductClickRepository;
import com.exe.astratarot.repository.ProductRepository;
import com.exe.astratarot.repository.ReaderApplicationRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReportRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.AdminStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mỗi con số ở đây là một phép đếm ở tầng cơ sở dữ liệu, không kéo bản ghi về
 * rồi đếm trong bộ nhớ — bảng người dùng hay lượt bấm có thể rất lớn.
 *
 * readOnly: cả phương thức chỉ đọc, gộp vào một giao dịch để mọi con số chụp
 * cùng một thời điểm thay vì lệch nhau giữa các câu đếm.
 */
@Service
@RequiredArgsConstructor
public class AdminStatsServiceImpl implements AdminStatsService {

    private final UserRepository userRepository;
    private final ReaderApplicationRepository readerApplicationRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final BookingRepository bookingRepository;
    private final ReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final ProductClickRepository productClickRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        Instant now = Instant.now();

        // --- Tài khoản ---
        // Giữ thứ tự vai trò cố định để giao diện luôn vẽ cùng một hàng.
        Map<String, Long> byRole = new LinkedHashMap<>();
        long totalUsers = 0;
        for (UserRole role : UserRole.values()) {
            long c = userRepository.countByRoleAndDeletedAtIsNull(role);
            byRole.put(role.name(), c);
            totalUsers += c;
        }
        long newUsers = userRepository.countByCreatedAtAfterAndDeletedAtIsNull(
                now.minus(7, ChronoUnit.DAYS));

        // --- Reader ---
        long pendingApplications =
                readerApplicationRepository.countByStatus(ReaderApplication.ApplicationStatus.PENDING);
        long activeProfiles = readerProfileRepository.count();

        // --- Đặt lịch ---
        Map<String, Long> bookingByStatus = new LinkedHashMap<>();
        long totalBookings = 0;
        for (BookingStatus status : BookingStatus.values()) {
            long c = bookingRepository.countByStatus(status);
            bookingByStatus.put(status.name(), c);
            totalBookings += c;
        }

        // --- Kiểm duyệt ---
        long pendingReports = reportRepository.countByStatus(ReportStatus.PENDING);

        // --- Cửa hàng liên kết ---
        long activeProducts = productRepository.countByActiveTrueAndAffiliateUrlIsNotNull();
        long clicks30d = productClickRepository.countByCreatedAtAfter(now.minus(30, ChronoUnit.DAYS));
        long clicksTotal = productClickRepository.count();

        return new AdminStatsResponse(
                new AdminStatsResponse.UserStats(totalUsers, byRole, newUsers),
                new AdminStatsResponse.ReaderStats(pendingApplications, activeProfiles),
                new AdminStatsResponse.BookingStats(totalBookings, bookingByStatus),
                new AdminStatsResponse.ModerationStats(pendingReports),
                new AdminStatsResponse.ShopStats(activeProducts, clicks30d, clicksTotal)
        );
    }
}
