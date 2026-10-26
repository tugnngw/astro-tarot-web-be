package com.exe.astratarot.domain.dto.marketing;

import java.time.Instant;
import java.util.List;

/**
 * Một khách: vào từ đâu, và đã làm những gì, theo thứ tự.
 *
 * <p>Đây là thứ mà bảng gộp theo nguồn không trả lời được. Bảng kia nói
 * "Facebook mang về 21 lượt"; cái này nói "khách <code>ab12cd</code> vào từ
 * Facebook lúc 20:14, xem trang chủ, mở Tarot, rút một quẻ, rồi đi".
 *
 * @param maKhach    sáu ký tự đầu của mã khách — đủ để phân biệt trên màn
 *                   hình mà không bày nguyên chuỗi dài
 * @param nguon      {@code utm_source} hoặc nguồn suy từ referrer; rỗng thì
 *                   tầng dịch vụ đặt thành "(không rõ nguồn)"
 * @param kenh       {@code utm_medium} — `referral`, `organic`, `social`…
 * @param chienDich  {@code utm_campaign}, chỉ có khi vào bằng link gắn tay
 * @param tenNguoiDung tên tài khoản nếu khách có đăng nhập trong phiên
 * @param batDau     mốc hành động đầu tiên
 * @param ketThuc    mốc hành động cuối
 * @param soHanhDong tổng số hành động ghi được
 * @param cacBuoc    danh sách hành động theo đúng thứ tự xảy ra
 */
public record HanhTrinhKhach(
        String maKhach,
        String nguon,
        String kenh,
        String chienDich,
        String tenNguoiDung,
        Instant batDau,
        Instant ketThuc,
        long soHanhDong,
        List<Buoc> cacBuoc
) {
    /**
     * Một bước trong hành trình.
     *
     * @param hanhDong tên sự kiện, ví dụ `xem_trang`, `rut_bai_tarot`
     * @param duongDan trang xảy ra chuyện đó
     * @param luc      thời điểm
     * @param metadata chuỗi JSON kèm theo sự kiện, hoặc {@code null}.
     *                 <p>Đây là thứ biến "Sang Shopee" thành "Sang Shopee —
     *                 bo-bai-rider-waite", và "Rút bài" thành "Rút bài —
     *                 Past-Present-Future, 3 lá". Bảng quản trị không đoán
     *                 được những chi tiết ấy từ tên sự kiện với đường dẫn.
     *                 <p>Trả nguyên chuỗi chứ không tách sẵn thành các
     *                 trường: mỗi loại sự kiện kèm một bộ khoá khác nhau, và
     *                 bộ ấy còn đổi khi thêm nút mới. Tách ở đây thì cứ thêm
     *                 một khoá là phải sửa cả DTO lẫn tầng dịch vụ.
     */
    public record Buoc(String hanhDong, String duongDan, Instant luc, String metadata) {
    }
}
