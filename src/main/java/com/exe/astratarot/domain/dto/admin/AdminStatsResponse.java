package com.exe.astratarot.domain.dto.admin;

import java.util.Map;

/**
 * Số liệu tổng quan cho trang Quản trị.
 *
 * Gom nhiều phép đếm rời rạc thành một lần gọi để trang không phải bắn bảy
 * request rồi tự ghép. Toàn số đếm, không có dữ liệu cá nhân, nên trả thẳng
 * cho giao diện là an toàn.
 */
public record AdminStatsResponse(
        UserStats users,
        ReaderStats readers,
        BookingStats bookings,
        ModerationStats moderation,
        ShopStats shop,
        AiStats ai,
        RevenueStats revenue
) {
    /**
     * @param total    tổng tài khoản còn hiệu lực (chưa xoá mềm)
     * @param byRole   số tài khoản theo từng vai trò
     * @param newLast7Days tài khoản tạo trong 7 ngày gần nhất
     */
    public record UserStats(long total, Map<String, Long> byRole, long newLast7Days) {}

    /**
     * @param pendingApplications hồ sơ Reader đang chờ duyệt
     * @param activeProfiles      hồ sơ Reader đã có trên hệ thống
     */
    public record ReaderStats(long pendingApplications, long activeProfiles) {}

    /**
     * @param total tổng lượt đặt lịch
     * @param byStatus số lượt theo từng trạng thái (PENDING/CONFIRMED/COMPLETED/CANCELLED)
     */
    public record BookingStats(long total, Map<String, Long> byStatus) {}

    /**
     * @param pendingReports báo cáo vi phạm chờ xử lý
     */
    public record ModerationStats(long pendingReports) {}

    /**
     * @param activeProducts   sản phẩm đang bán có gắn link liên kết
     * @param clicksLast30Days lượt bấm sang sàn trong 30 ngày
     * @param clicksTotal      tổng lượt bấm sang sàn từ trước tới nay
     */
    public record ShopStats(long activeProducts, long clicksLast30Days, long clicksTotal) {}

    /**
     * Token tiêu thụ của Tarot AI (đọc từ {@code ai_usage_logs}).
     *
     * @param totalCalls           số lần gọi AI đã ghi nhận
     * @param callsLast30Days      số lần gọi trong 30 ngày
     * @param promptTokens         tổng token đầu vào
     * @param completionTokens     tổng token đầu ra
     * @param totalTokens          tổng token (prompt + completion khi API trả)
     * @param tokensLast30Days     tổng token trong 30 ngày
     * @param estimatedCostUsd     chi phí ước lượng (USD)
     * @param tokensByModel        tổng token theo từng model (key = tên model)
     */
    /**
     * Tiền vào, tiền ra và phần còn lại của nền tảng.
     *
     * Doanh thu gộp là tiền khách thực trả (giao dịch SUCCESS). Nền tảng giữ
     * {@code platformFeePercent}%, phần còn lại là của Reader — nên "lợi nhuận"
     * ở đây KHÔNG phải doanh thu gộp mà là phần phí nền tảng, trừ đi chi phí AI.
     *
     * @param grossRevenue         tổng tiền khách đã trả
     * @param grossRevenueLast30Days   phần trong 30 ngày
     * @param platformFeePercent   phần trăm nền tảng giữ lại (khớp EscrowServiceImpl)
     * @param platformFee          phần nền tảng giữ = grossRevenue * percent / 100
     * @param readerShare          phần thuộc về Reader = grossRevenue - platformFee
     * @param paidOut              tiền đã chi trả cho Reader (payout PAID)
     * @param pendingPayout        tiền Reader đã yêu cầu rút, chưa chi
     * @param aiCostVnd            chi phí AI quy đổi sang VND
     * @param netProfit            platformFee - aiCostVnd
     * @param successfulPayments   số giao dịch thành công
     * @param pendingPayments      số giao dịch còn chờ
     * @param revenueByMonth       doanh thu theo tháng (key "YYYY-MM"), 12 tháng gần nhất
     */
    public record RevenueStats(
            long grossRevenue,
            long grossRevenueLast30Days,
            int platformFeePercent,
            long platformFee,
            long readerShare,
            long paidOut,
            long pendingPayout,
            long aiCostVnd,
            long netProfit,
            long successfulPayments,
            long pendingPayments,
            Map<String, Long> revenueByMonth
    ) {}

    public record AiStats(
            long totalCalls,
            long callsLast30Days,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            long tokensLast30Days,
            double estimatedCostUsd,
            Map<String, Long> tokensByModel
    ) {}
}
