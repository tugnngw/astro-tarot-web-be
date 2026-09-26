package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PayoutStatus;
import com.exe.astratarot.domain.enums.ReportStatus;
import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.repository.AIUsageLogRepository;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.PaymentTransactionRepository;
import com.exe.astratarot.repository.PayoutRequestRepository;
import com.exe.astratarot.repository.ProductClickRepository;
import com.exe.astratarot.repository.ProductRepository;
import com.exe.astratarot.repository.ReaderApplicationRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReportRepository;
import com.exe.astratarot.repository.ReviewRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.FeedbackService;
import com.exe.astratarot.service.MarketingEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Bảng số liệu quản trị. Lớp này trước đây phủ 0,0%.
 *
 * <p>Đây là trang người ta nhìn để ra quyết định, nên sai ở đây không gây lỗi
 * gì cả — nó chỉ làm người ta quyết định sai, và không ai biết. Ba chỗ đáng
 * kiểm:
 *
 * <ol>
 *   <li><b>Phép cộng doanh thu và phí.</b> Phần nền tảng giữ lại phải đúng
 *       bằng tổng thu trừ phần của Reader — hai con số này hiện cạnh nhau trên
 *       màn hình nên lệch là thấy ngay, nhưng chỉ khi có người cộng lại.
 *   <li><b>Lãi thật trừ chi phí AI.</b> Chi phí AI tính bằng đô, doanh thu tính
 *       bằng đồng. Quên quy đổi là báo lãi cao gấp hai vạn lần.
 *   <li><b>Null từ SUM rỗng.</b> {@code SUM()} trên bảng trống trả null, và
 *       null lọt vào {@code doubleValue()} là NullPointerException — bảng số
 *       liệu chết trắng đúng lúc hệ thống còn mới, chưa có dữ liệu.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminStatsServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private ReaderApplicationRepository readerApplicationRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductClickRepository productClickRepository;
    @Mock private AIUsageLogRepository aiUsageLogRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private PayoutRequestRepository payoutRequestRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private FeedbackService feedbackService;
    @Mock private MarketingEventService marketingEventService;

    private AdminStatsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminStatsServiceImpl(userRepository, readerApplicationRepository,
                readerProfileRepository, bookingRepository, reportRepository, productRepository,
                productClickRepository, aiUsageLogRepository, paymentTransactionRepository,
                payoutRequestRepository, reviewRepository, feedbackService, marketingEventService);

        // Mặc định: hệ thống trống trơn. Từng phép kiểm chỉ nói thêm phần nó cần.
        lenient().when(userRepository.countByRoleAndDeletedAtIsNull(any())).thenReturn(0L);
        lenient().when(userRepository.countByCreatedAtAfterAndDeletedAtIsNull(any())).thenReturn(0L);
        lenient().when(readerApplicationRepository.countByStatus(any())).thenReturn(0L);
        lenient().when(readerProfileRepository.count()).thenReturn(0L);
        lenient().when(bookingRepository.countByStatus(any())).thenReturn(0L);
        lenient().when(reportRepository.countByStatus(any())).thenReturn(0L);
        lenient().when(productRepository.countByActiveTrueAndAffiliateUrlIsNotNull()).thenReturn(0L);
        lenient().when(productClickRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        lenient().when(productClickRepository.count()).thenReturn(0L);
        lenient().when(aiUsageLogRepository.count()).thenReturn(0L);
        lenient().when(aiUsageLogRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(0L);
        lenient().when(aiUsageLogRepository.sumPromptTokens()).thenReturn(0L);
        lenient().when(aiUsageLogRepository.sumCompletionTokens()).thenReturn(0L);
        lenient().when(aiUsageLogRepository.sumTotalTokens()).thenReturn(0L);
        lenient().when(aiUsageLogRepository.sumTotalTokensSince(any())).thenReturn(0L);
        lenient().when(aiUsageLogRepository.sumEstimatedCostUsd()).thenReturn(BigDecimal.ZERO);
        lenient().when(aiUsageLogRepository.sumTotalTokensGroupedByModel()).thenReturn(List.of());
        lenient().when(paymentTransactionRepository.sumAmountByStatus(any())).thenReturn(0L);
        lenient().when(paymentTransactionRepository.sumAmountByStatusSince(any(), any())).thenReturn(0L);
        lenient().when(paymentTransactionRepository.countByStatus(any())).thenReturn(0L);
        lenient().when(paymentTransactionRepository.sumAmountByMonthSince(any())).thenReturn(List.of());
        lenient().when(payoutRequestRepository.sumAmountByStatus(any())).thenReturn(0L);
        lenient().when(reviewRepository.count()).thenReturn(0L);
        lenient().when(feedbackService.countAll()).thenReturn(0L);
        lenient().when(marketingEventService.countsLast30Days()).thenReturn(Map.of());
    }

    // =====================================================================

    @Test
    @DisplayName("Hệ thống trống trơn: mọi con số là 0, không chỗ nào nổ vì null")
    void heThongTrongTron() {
        when(aiUsageLogRepository.sumEstimatedCostUsd()).thenReturn(null);

        var kq = service.getStats();

        // SUM() trên bảng trống trả null. Null lọt vào doubleValue() là
        // NullPointerException — bảng số liệu chết trắng đúng lúc hệ thống còn
        // mới, chưa có dữ liệu, tức là đúng lúc người ta xem nhiều nhất.
        assertAll(
                () -> assertEquals(0L, kq.users().total()),
                () -> assertEquals(0d, kq.ai().estimatedCostUsd()),
                () -> assertEquals(0L, kq.revenue().grossRevenue()),
                () -> assertTrue(kq.revenue().revenueByMonth().isEmpty()));
    }

    @Test
    @DisplayName("Tổng người dùng là tổng của mọi vai trò, không đếm riêng lẻ")
    void tongNguoiDung() {
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.USER)).thenReturn(100L);
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.STAFF)).thenReturn(5L);
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(2L);

        var kq = service.getStats();

        assertAll(
                () -> assertEquals(107L, kq.users().total()),
                () -> assertEquals(100L, kq.users().byRole().get("USER")),
                // Mọi vai trò phải có mặt kể cả khi bằng 0, nếu không thì biểu
                // đồ tròn thiếu lát và không ai biết là thiếu.
                () -> assertEquals(UserRole.values().length, kq.users().byRole().size()));
    }

    @Test
    @DisplayName("Tổng lịch hẹn là tổng mọi trạng thái, và mọi trạng thái đều có mặt")
    void tongLichHen() {
        when(bookingRepository.countByStatus(BookingStatus.COMPLETED)).thenReturn(40L);
        when(bookingRepository.countByStatus(BookingStatus.CANCELLED)).thenReturn(10L);

        var kq = service.getStats();

        assertAll(
                () -> assertEquals(50L, kq.bookings().total()),
                () -> assertEquals(BookingStatus.values().length, kq.bookings().byStatus().size()),
                () -> assertEquals(40L, kq.traction().completedBookings()));
    }

    @Test
    @DisplayName("Phí nền tảng và phần Reader cộng lại đúng bằng tổng thu")
    void phiVaPhanReader() {
        when(paymentTransactionRepository.sumAmountByStatus(TransactionStatus.SUCCESS))
                .thenReturn(10_000_000L);

        var r = service.getStats().revenue();

        // Hai con số này hiện cạnh nhau trên màn hình, nên lệch là thấy ngay —
        // nhưng chỉ khi có người cộng lại.
        assertAll(
                () -> assertEquals(10_000_000L, r.grossRevenue()),
                () -> assertEquals(10_000_000L * EscrowServiceImpl.PLATFORM_FEE_PERCENT / 100,
                        r.platformFee()),
                () -> assertEquals(10_000_000L, r.platformFee() + r.readerShare()));
    }

    @Test
    @DisplayName("Chi phí AI quy từ đô sang đồng trước khi trừ vào lãi")
    void chiPhiAiQuyDoi() {
        when(paymentTransactionRepository.sumAmountByStatus(TransactionStatus.SUCCESS))
                .thenReturn(10_000_000L);
        when(aiUsageLogRepository.sumEstimatedCostUsd()).thenReturn(new BigDecimal("2.50"));

        var r = service.getStats().revenue();

        // Quên quy đổi là báo lãi cao hơn thật gấp hai vạn lần, và con số đó
        // trông vẫn hợp lý nên không ai nghi ngờ.
        assertAll(
                () -> assertEquals(62_500L, r.aiCostVnd()),
                () -> assertEquals(r.platformFee() - 62_500L, r.netProfit()));
    }

    @Test
    @DisplayName("Lãi ÂM được báo đúng là âm, không kẹp về 0")
    void laiAm() {
        when(paymentTransactionRepository.sumAmountByStatus(TransactionStatus.SUCCESS))
                .thenReturn(100_000L);
        when(aiUsageLogRepository.sumEstimatedCostUsd()).thenReturn(new BigDecimal("50"));

        // Kẹp về 0 là giấu đúng con số quan trọng nhất: tháng này đang lỗ.
        assertTrue(service.getStats().revenue().netProfit() < 0);
    }

    @Test
    @DisplayName("Doanh thu theo tháng giữ nguyên thứ tự truy vấn trả về")
    void doanhThuTheoThang() {
        List<Object[]> dong = List.<Object[]>of(
                new Object[]{"2026-07", 1_000_000L},
                new Object[]{"2026-08", 2_000_000L},
                new Object[]{"2026-09", 3_000_000L});
        when(paymentTransactionRepository.sumAmountByMonthSince(any())).thenReturn(dong);

        var theoThang = service.getStats().revenue().revenueByMonth();

        // Biểu đồ đường vẽ theo đúng thứ tự này. Đảo thứ tự là một đường đi
        // ngược thời gian.
        assertAll(
                () -> assertEquals(List.of("2026-07", "2026-08", "2026-09"),
                        List.copyOf(theoThang.keySet())),
                () -> assertEquals(3_000_000L, theoThang.get("2026-09")));
    }

    @Test
    @DisplayName("Dòng số liệu khuyết nhãn hoặc khuyết số vẫn không làm hỏng bảng")
    void dongSoLieuKhuyet() {
        // List.of(new Object[]{...}) suy ra List<Object> vi mang mot phan tu;
        // khai kieu tuong minh de khop chu ky List<Object[]>.
        List<Object[]> theoThang = List.<Object[]>of(
                new Object[]{null, 500_000L},
                new Object[]{"2026-09", null});
        List<Object[]> theoModel = List.<Object[]>of(new Object[]{null, 1_000L});
        when(paymentTransactionRepository.sumAmountByMonthSince(any())).thenReturn(theoThang);
        when(aiUsageLogRepository.sumTotalTokensGroupedByModel()).thenReturn(theoModel);

        var kq = service.getStats();

        assertAll(
                () -> assertEquals(500_000L, kq.revenue().revenueByMonth().get("?")),
                () -> assertEquals(0L, kq.revenue().revenueByMonth().get("2026-09")),
                () -> assertEquals(1_000L, kq.ai().tokensByModel().get("unknown")));
    }

    @Test
    @DisplayName("Cờ đủ góp ý bật đúng ở mốc 20")
    void coDuGopY() {
        when(feedbackService.countAll()).thenReturn(19L);
        assertFalse(service.getStats().traction().feedbackGoalMet());

        when(feedbackService.countAll()).thenReturn(20L);
        assertTrue(service.getStats().traction().feedbackGoalMet());
    }

    @Test
    @DisplayName("Số liệu chi tiền ra lấy theo từng trạng thái riêng")
    void soLieuChiTienRa() {
        when(payoutRequestRepository.sumAmountByStatus(PayoutStatus.PAID)).thenReturn(3_000_000L);
        when(payoutRequestRepository.sumAmountByStatus(PayoutStatus.PENDING)).thenReturn(500_000L);

        var r = service.getStats().revenue();

        // Đã chi và đang chờ chi là hai con số khác hẳn nhau về nghĩa: một cái
        // đã ra khỏi tài khoản, một cái là nợ.
        assertAll(
                () -> assertEquals(3_000_000L, r.paidOut()),
                () -> assertEquals(500_000L, r.pendingPayout()));
    }

    @Test
    @DisplayName("Đơn Reader chờ duyệt đếm đúng trạng thái PENDING")
    void donReaderChoDuyet() {
        when(readerApplicationRepository.countByStatus(
                ReaderApplication.ApplicationStatus.PENDING)).thenReturn(4L);
        when(reportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(2L);

        var kq = service.getStats();

        assertAll(
                () -> assertEquals(4L, kq.readers().pendingApplications()),
                () -> assertEquals(2L, kq.moderation().pendingReports()));
    }
}
