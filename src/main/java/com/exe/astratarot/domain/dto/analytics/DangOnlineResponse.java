package com.exe.astratarot.domain.dto.analytics;

import java.util.List;

/**
 * Ai đang ở trên web NGAY BÂY GIỜ, ở đâu, và đang xem gì.
 *
 * <h2>Vì sao có DTO riêng thay vì nhét vào báo cáo thường</h2>
 *
 * Báo cáo thường của GA4 trễ 24–48 giờ. Nhìn vào đó lúc 9 giờ tối để biết
 * "hiện có ai đang dùng web không" là nhìn vào số của hôm kia.
 *
 * <h2>Vì sao cột nguồn KHÔNG lấy từ GA4</h2>
 *
 * Realtime API của GA4 chỉ nhận một tập chiều rất hẹp — {@code city},
 * {@code country}, {@code deviceCategory}, {@code eventName},
 * {@code unifiedScreenName}… và **không có** {@code sessionSource}. Nghĩa là
 * Google biết đang có 3 người ở TP.HCM xem trang Tarot, nhưng không nói được
 * họ đến từ Facebook hay tự gõ địa chỉ.
 *
 * <p>Nên phần "đến từ đâu" lấy từ bảng {@code marketing_events} của chính hệ
 * thống. Đó cũng là lý do bảng ấy đáng tồn tại song song với GA4 chứ không
 * phải làm trùng việc.
 *
 * @param soNguoiDangOnline tổng người hoạt động trong 30 phút qua; {@code -1}
 *                          nghĩa là không hỏi được (chưa cấu hình khoá, hoặc
 *                          lượt gọi hỏng), còn {@code 0} là hỏi được và đúng
 *                          là không có ai
 * @param theoNoi           thành phố → số người
 * @param theoTrang         tên trang đang mở → số người
 * @param theoHanhDong      tên sự kiện vừa xảy ra → số lượt
 * @param theoThietBi       mobile/desktop/tablet → số người
 * @param thongDiep         câu giải thích khi không lấy được số
 */
public record DangOnlineResponse(
        long soNguoiDangOnline,
        List<DongSo> theoNoi,
        List<DongSo> theoTrang,
        List<DongSo> theoHanhDong,
        List<DongSo> theoThietBi,
        String thongDiep
) {
    /** Một hàng "tên → số", dùng chung cho cả bốn bảng. */
    public record DongSo(String ten, long so) {
    }

    public static DangOnlineResponse khongLayDuoc(String thongDiep) {
        return new DangOnlineResponse(-1L, List.of(), List.of(), List.of(), List.of(), thongDiep);
    }
}
