package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.admin.AdminStatsResponse;
import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.ReportStatus;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.PayoutStatus;
import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.repository.AIUsageLogRepository;
import com.exe.astratarot.repository.PaymentTransactionRepository;
import com.exe.astratarot.repository.PayoutRequestRepository;
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
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PayoutRequestRepository payoutRequestRepository;

    /**
     * Tỷ giá quy đổi chi phí AI (tính bằng USD) sang VND để so được với doanh
     * thu. Cố định ở đây vì đây là con số ước lượng cho trang thống kê, không
     * phải tỷ giá dùng để thu tiền của ai. Cần chính xác thì đưa ra cấu hình.
     */
    private static final long USD_TO_VND = 25_000L;

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

        // --- Tiền ---
        long grossRevenue = paymentTransactionRepository.sumAmountByStatus(TransactionStatus.SUCCESS);
        long grossRevenue30d = paymentTransactionRepository.sumAmountByStatusSince(TransactionStatus.SUCCESS, since30d);
        int feePercent = com.exe.astratarot.service.impl.EscrowServiceImpl.PLATFORM_FEE_PERCENT;
        long platformFee = grossRevenue * feePercent / 100;
        long readerShare = grossRevenue - platformFee;
        long paidOut = payoutRequestRepository.sumAmountByStatus(PayoutStatus.PAID);
        long pendingPayout = payoutRequestRepository.sumAmountByStatus(PayoutStatus.PENDING);
        long successfulPayments = paymentTransactionRepository.countByStatus(TransactionStatus.SUCCESS);
        long pendingPayments = paymentTransactionRepository.countByStatus(TransactionStatus.PENDING);

        Map<String, Long> revenueByMonth = new LinkedHashMap<>();
        for (Object[] row : paymentTransactionRepository.sumAmountByMonthSince(now.minus(365, ChronoUnit.DAYS))) {
            String month = row[0] != null ? row[0].toString() : "?";
            long sum = row[1] instanceof Number n ? n.longValue() : 0L;
            revenueByMonth.put(month, sum);
        }

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
                ),
                buildRevenueStats(
                        grossRevenue, grossRevenue30d, feePercent, platformFee, readerShare,
                        paidOut, pendingPayout, estimatedCostUsd,
                        successfulPayments, pendingPayments, revenueByMonth
                )
        );
    }

    /**
     * Lợi nhuận ở đây là phần phí nền tảng giữ lại TRỪ chi phí AI — không phải
     * doanh thu gộp. Phần lớn tiền khách trả là của Reader, gọi đó là lợi nhuận
     * thì trang thống kê sẽ nói dối một con số lớn gấp mấy lần sự thật.
     */
    private AdminStatsResponse.RevenueStats buildRevenueStats(
            long grossRevenue, long grossRevenue30d, int feePercent, long platformFee, long readerShare,
            long paidOut, long pendingPayout, double estimatedCostUsd,
            long successfulPayments, long pendingPayments, Map<String, Long> revenueByMonth) {
        long aiCostVnd = Math.round(estimatedCostUsd * USD_TO_VND);
        return new AdminStatsResponse.RevenueStats(
                grossRevenue,
                grossRevenue30d,
                feePercent,
                platformFee,
                readerShare,
                paidOut,
                pendingPayout,
                aiCostVnd,
                platformFee - aiCostVnd,
                successfulPayments,
                pendingPayments,
                revenueByMonth
        );
    }
}
