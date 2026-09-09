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
        ShopStats shop
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
}
