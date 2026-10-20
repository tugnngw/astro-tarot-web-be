package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.khuyenmai.GiaSauGiam;
import com.exe.astratarot.domain.dto.khuyenmai.KhuyenMaiResponse;
import com.exe.astratarot.domain.dto.khuyenmai.LuuKhuyenMaiRequest;
import com.exe.astratarot.domain.entity.KhuyenMai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KhuyenMaiService {

    /** Đợt đang chạy có lợi nhất cho khách trên một giá cụ thể. */
    Optional<KhuyenMai> dotTotNhat(long giaGoc);

    /** Áp đợt tốt nhất lên một giá. Không có đợt nào thì trả nguyên giá. */
    GiaSauGiam apDung(long giaGoc);

    /** Đợt đang chạy, cho banner ngoài trang. */
    List<KhuyenMaiResponse> dangChay();

    /**
     * Giá sau giảm của MỌI gói đang bán, khoá là id gói.
     *
     * <p>Có endpoint này để giao diện không phải tự tính lại mức giảm. Lặp
     * quy tắc ở web rồi lặp lần nữa ở app là ba bản cài đặt của cùng một
     * phép tính; chỉ cần một bản quên cái trần giảm là trang hiện một giá
     * còn ví trừ một giá khác.
     */
    java.util.Map<String, GiaSauGiam> giaMoiGoi();

    /**
     * Lời chúc người này CHƯA xem, và đánh dấu đã xem ngay trong cùng lượt gọi.
     *
     * <p>Gọi lần thứ hai trả về rỗng — kể cả khi hai thiết bị gọi gần như
     * cùng lúc.
     */
    Optional<KhuyenMaiResponse> loiChucChuaXem(UUID userId);

    // ---- Quản trị ----
    List<KhuyenMaiResponse> tatCa();

    KhuyenMaiResponse tao(LuuKhuyenMaiRequest request);

    KhuyenMaiResponse sua(UUID id, LuuKhuyenMaiRequest request);

    void xoa(UUID id);
}
