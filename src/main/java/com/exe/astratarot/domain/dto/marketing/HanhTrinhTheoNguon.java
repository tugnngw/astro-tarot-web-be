package com.exe.astratarot.domain.dto.marketing;

import java.util.Map;

/**
 * Một hàng của bảng "khách đến từ đâu, rồi làm gì".
 *
 * <p>Đây là câu hỏi mà bảng đếm theo tên sự kiện không trả lời được. Nó cho
 * biết có 40 lượt bấm "đặt lịch", nhưng không cho biết 40 lượt ấy đến từ
 * TikTok hay từ người tự gõ địa chỉ — tức là không biết kênh nào đáng đăng
 * tiếp.
 *
 * <p>Số liệu lấy từ bảng {@code marketing_events} của chính hệ thống, không
 * phải từ Google. Hai chỗ khác nhau ở ba điểm đáng kể:
 *
 * <ul>
 *   <li>hiện ngay, trong khi báo cáo thường của GA4 trễ 24–48 giờ;</li>
 *   <li>không bị trình chặn quảng cáo cắt, vì đi qua API của mình;</li>
 *   <li>nối được với {@code user_id}, nên đếm được NGƯỜI chứ không chỉ lượt.</li>
 * </ul>
 *
 * @param nguon           giá trị {@code utm_source}; rỗng thì tầng dịch vụ đặt
 *                        thành "(không rõ nguồn)" — phần lớn là người tự gõ
 *                        địa chỉ hoặc vào từ link không gắn UTM
 * @param tongLuot        tổng số lượt của nguồn này
 * @param soNguoiDangNhap số NGƯỜI đã đăng nhập, đếm theo {@code user_id}; một
 *                        người bấm bảy nút vẫn là một. Khách chưa đăng nhập
 *                        không đếm được ở đây
 * @param theoHoatDong    tên hoạt động → số lượt, sắp theo lượt giảm dần
 */
public record HanhTrinhTheoNguon(
        String nguon,
        long tongLuot,
        long soNguoiDangNhap,
        Map<String, Long> theoHoatDong
) {
}
