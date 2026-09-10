package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.admin.AdminStatsResponse;
import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.ReportStatus;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.repository.AIUsageLogRepository;
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

import java.math.BigDecimal;
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
    private final AIUsageLogRepository aiUsageLogRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        Instant now = Instant.now();
        Instant since30d = now.minus(30, ChronoUnit.DAYS);

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
        long clicks30d = productClickRepository.countByCreatedAtAfter(since30d);
        long clicksTotal = productClickRepository.count();

        // --- Tarot AI (token) ---
        long aiCalls = aiUsageLogRepository.count();
        long aiCalls30d = aiUsageLogRepository.countByCreatedAtGreaterThanEqual(since30d);
        long promptTokens = aiUsageLogRepository.sumPromptTokens();
        long completionTokens = aiUsageLogRepository.sumCompletionTokens();
        long totalTokens = aiUsageLogRepository.sumTotalTokens();
        long tokens30d = aiUsageLogRepository.sumTotalTokensSince(since30d);
        BigDecimal cost = aiUsageLogRepository.sumEstimatedCostUsd();
        double estimatedCostUsd = cost != null ? cost.doubleValue() : 0d;

        Map<String, Long> tokensByModel = new LinkedHashMap<>();
        for (Object[] row : aiUsageLogRepository.sumTotalTokensGroupedByModel()) {
            String model = row[0] != null ? row[0].toString() : "unknown";
            long tokens = row[1] instanceof Number n ? n.longValue() : 0L;
            tokensByModel.put(model, tokens);
        }

        return new AdminStatsResponse(
                new AdminStatsResponse.UserStats(totalUsers, byRole, newUsers),
                new AdminStatsResponse.ReaderStats(pendingApplications, activeProfiles),
                new AdminStatsResponse.BookingStats(totalBookings, bookingByStatus),
                new AdminStatsResponse.ModerationStats(pendingReports),
                new AdminStatsResponse.ShopStats(activeProducts, clicks30d, clicksTotal),
                new AdminStatsResponse.AiStats(
                        aiCalls,
                        aiCalls30d,
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        tokens30d,
                        estimatedCostUsd,
                        tokensByModel
                )
        );
    }
}
