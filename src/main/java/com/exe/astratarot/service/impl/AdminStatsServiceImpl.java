package com.exe.astratarot.service.impl;

import java.sql.Timestamp;
import com.exe.astratarot.repository.WalletTransactionRepository;
import com.exe.astratarot.repository.UserPlanPurchaseRepository;
import com.exe.astratarot.repository.SubscriptionPlanRepository;
import com.exe.astratarot.domain.enums.WalletTransactionType;
import com.exe.astratarot.domain.enums.PaymentPhase;
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
import com.exe.astratarot.repository.ReviewRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.AdminStatsService;
import com.exe.astratarot.service.FeedbackService;
import com.exe.astratarot.service.MarketingEventService;
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
    private final ReviewRepository reviewRepository;
    private final FeedbackService feedbackService;
    private final MarketingEventService marketingEventService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserPlanPurchaseRepository userPlanPurchaseRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    private static final long USD_TO_VND = 25_000L;

    @Override
    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        Instant now = Instant.now();
        Instant since30d = now.minus(30, ChronoUnit.DAYS);

        Map<String, Long> byRole = new LinkedHashMap<>();
        long totalUsers = 0;
        for (UserRole role : UserRole.values()) {
            long c = userRepository.countByRoleAndDeletedAtIsNull(role);
            byRole.put(role.name(), c);
            totalUsers += c;
        }
        long newUsers = userRepository.countByCreatedAtAfterAndDeletedAtIsNull(
                now.minus(7, ChronoUnit.DAYS));

        long pendingApplications =
                readerApplicationRepository.countByStatus(ReaderApplication.ApplicationStatus.PENDING);
        long activeProfiles = readerProfileRepository.count();

        Map<String, Long> bookingByStatus = new LinkedHashMap<>();
        long totalBookings = 0;
        for (BookingStatus status : BookingStatus.values()) {
            long c = bookingRepository.countByStatus(status);
            bookingByStatus.put(status.name(), c);
            totalBookings += c;
        }

        long pendingReports = reportRepository.countByStatus(ReportStatus.PENDING);

        long activeProducts = productRepository.countByActiveTrueAndAffiliateUrlIsNotNull();
        long clicks30d = productClickRepository.countByCreatedAtAfter(since30d);
        long clicksTotal = productClickRepository.count();

        long aiCalls = aiUsageLogRepository.count();
        long aiCalls30d = aiUsageLogRepository.countByCreatedAtGreaterThanEqual(since30d);
        long promptTokens = aiUsageLogRepository.sumPromptTokens();
        long completionTokens = aiUsageLogRepository.sumCompletionTokens();
        long totalTokens = aiUsageLogRepository.sumTotalTokens();
        long tokens30d = aiUsageLogRepository.sumTotalTokensSince(since30d);
        BigDecimal cost = aiUsageLogRepository.sumEstimatedCostUsd();
        double estimatedCostUsd = cost != null ? cost.doubleValue() : 0d;

        // Giữ seed trong doanh thu để môi trường demo/test có biểu đồ đầy đủ.
        //
        // grossRevenue là TIỀN VÀO: khách trả thẳng cho lịch hẹn, cộng tiền
        // khách nạp ví. Đó là con số đúng cho câu hỏi "đã thu được bao nhiêu".
        long grossRevenue = paymentTransactionRepository.sumAmountByStatus(TransactionStatus.SUCCESS);
        long grossRevenue30d = paymentTransactionRepository.sumAmountByStatusSince(TransactionStatus.SUCCESS, since30d);
        int feePercent = com.exe.astratarot.service.impl.EscrowServiceImpl.PLATFORM_FEE_PERCENT;

        // Nhưng phần chia cho Reader thì KHÔNG được tính trên tiền vào.
        //
        // Trước 05/10/2026 chỗ này lấy readerShare = grossRevenue * 85%. Tiền
        // khách nạp ví để mua gói AI cũng nằm trong grossRevenue, mà gói AI
        // thì không có Reader nào dự phần — nên màn Tổng quan báo một khoản nợ
        // Reader không ai được hưởng, đồng thời khai thiếu phần của nền tảng.
        //
        // Tiền của lịch hẹn đến từ hai đường, phải cộng cả hai:
        //   • khách trả thẳng qua cổng  -> payment_transactions, phase khác TOPUP
        //   • khách trả bằng số dư ví   -> wallet_transactions, BOOKING_PAYMENT
        //                                  (đường này KHÔNG sinh hàng nào ở
        //                                   payment_transactions, nhìn một bảng
        //                                   là bỏ sót)
        long tienLichHenTraThang = paymentTransactionRepository
                .sumAmountByStatusExcludingPhase(TransactionStatus.SUCCESS, PaymentPhase.TOPUP);
        long tienLichHenTraBangVi = walletTransactionRepository
                .sumAmountByTypeAndStatus(WalletTransactionType.BOOKING_PAYMENT, TransactionStatus.SUCCESS);
        long doanhThuLichHen = tienLichHenTraThang + tienLichHenTraBangVi;

        long platformFee = doanhThuLichHen * feePercent / 100;
        long readerShare = doanhThuLichHen - platformFee;
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

        // ---- Bán gói AI ----
        //
        // Màn Tổng quan trước đây không có một con số nào về việc này: biết AI
        // tiêu bao nhiêu token mà không biết bán được bao nhiêu gói, tức là
        // biết chi phí mà không biết doanh thu của đúng tính năng ấy.
        Timestamp moc30Ngay = Timestamp.from(since30d);
        long goiDangBan = subscriptionPlanRepository.countByIsActiveTrue();
        long luotMuaGoi = userPlanPurchaseRepository.demLuotMuaCoThuTien();
        long luotMuaGoi30d = userPlanPurchaseRepository.demLuotMuaTu(moc30Ngay);
        long goiConHan = userPlanPurchaseRepository.demGoiConHan(Timestamp.from(now));
        long tienBanGoi = userPlanPurchaseRepository.tongTienBanGoi();
        long tienBanGoi30d = userPlanPurchaseRepository.tienBanGoiTu(moc30Ngay);

        Map<String, Long> luotMuaTheoGoi = new LinkedHashMap<>();
        for (Object[] row : userPlanPurchaseRepository.demTheoTenGoi()) {
            String ten = row[0] != null ? row[0].toString() : "?";
            long so = row[1] instanceof Number n ? n.longValue() : 0L;
            luotMuaTheoGoi.put(ten, so);
        }

        long feedbackCount = feedbackService.countAll();
        long completedBookings = bookingByStatus.getOrDefault(BookingStatus.COMPLETED.name(), 0L);
        long reviewsCount = reviewRepository.count();

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
                new AdminStatsResponse.SubscriptionStats(
                        goiDangBan,
                        luotMuaGoi,
                        luotMuaGoi30d,
                        goiConHan,
                        tienBanGoi,
                        tienBanGoi30d,
                        luotMuaTheoGoi
                ),
                buildRevenueStats(
                        grossRevenue, grossRevenue30d, feePercent, platformFee, readerShare,
                        paidOut, pendingPayout, estimatedCostUsd,
                        successfulPayments, pendingPayments, revenueByMonth,
                        tienBanGoi
                ),
                new AdminStatsResponse.TractionStats(
                        totalUsers,
                        successfulPayments,
                        completedBookings,
                        reviewsCount,
                        feedbackCount,
                        feedbackCount >= 20,
                        clicks30d,
                        marketingEventService.countsLast30Days()
                )
        );
    }

    private AdminStatsResponse.RevenueStats buildRevenueStats(
            long grossRevenue, long grossRevenue30d, int feePercent, long platformFee, long readerShare,
            long paidOut, long pendingPayout, double estimatedCostUsd,
            long successfulPayments, long pendingPayments, Map<String, Long> revenueByMonth,
            long tienBanGoi) {
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
                // Lãi của nền tảng = phí giữ lại từ lịch hẹn CỘNG toàn bộ tiền
                // bán gói AI, trừ chi phí gọi AI. Tiền bán gói là của nền tảng
                // trọn vẹn, không chia cho ai.
                platformFee + tienBanGoi - aiCostVnd,
                successfulPayments,
                pendingPayments,
                revenueByMonth
        );
    }
}
