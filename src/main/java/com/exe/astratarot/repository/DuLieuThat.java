package com.exe.astratarot.repository;

/**
 * Ranh giới giữa dữ liệu THẬT và dữ liệu mẫu, dùng chung cho màn Tổng quan.
 *
 * <h2>Vì sao không lọc theo email</h2>
 *
 * Cách tưởng như hiển nhiên là loại tài khoản {@code @astrotarot.demo}. Cách
 * ấy SAI: {@code V2_23} và {@code V2_25} gieo buổi hẹn, thanh toán và đánh giá
 * lên hai hộp thư Gmail có thật của nhóm ({@code tranduydatqtdl@gmail.com},
 * {@code alloffdenchi13@gmail.com}) để bản demo trông có người dùng thật.
 * Lọc theo tên miền thì toàn bộ lô ấy được tính là thật.
 *
 * <h2>Dấu hiệu thật sự dùng được</h2>
 *
 * Mọi hàng gieo sẵn đều mang khoá chính viết tay theo đúng một khuôn:
 * {@code ........-0000-4000-8000-............}. Kiểm lại toàn bộ thư mục
 * migration: 223/223 khoá chính trong các tệp seed khớp khuôn này, không có
 * ngoại lệ.
 *
 * <p>Hàng thật thì khoá chính do {@code GenerationType.UUID} sinh ngẫu nhiên.
 * Để trùng khuôn trên, một UUID ngẫu nhiên phải có đúng {@code 0000} ở nhóm
 * hai và {@code 8000} ở nhóm bốn — khoảng một phần nghìn tỉ. Nói cách khác
 * nhầm lẫn theo chiều ngược lại gần như không xảy ra được.
 *
 * <h2>Khi gieo thêm dữ liệu mẫu</h2>
 *
 * Giữ nguyên khuôn khoá chính ấy. Gieo bằng {@code gen_random_uuid()} là số
 * "thật" ở trang quản trị phồng lên ngay, và không có gì báo lỗi cả — con số
 * vẫn hiện ra, chỉ là sai.
 */
public final class DuLieuThat {

    /** Khuôn khoá chính mà mọi tệp seed đang dùng. */
    public static final String KHUON_GIEO_SAN = "-0000-4000-8000-";

    /**
     * Mảnh điều kiện nối vào {@code @Query} — phải là hằng biên dịch.
     *
     * <p>Viết sẵn theo bí danh {@code x}, nên câu truy vấn dùng nó phải đặt
     * bí danh ấy cho bảng đang đếm. Hibernate dịch ra
     * {@code cast(id as varchar) not like '%-0000-4000-8000-%'}.
     */
    public static final String DIEU_KIEN =
            "CAST(x.id AS String) NOT LIKE '%" + KHUON_GIEO_SAN + "%'";

    /**
     * Cùng một quy tắc với {@link #DIEU_KIEN}, dạng gọi được từ Java.
     *
     * <p>Có mặt để bài kiểm chứng minh được quy tắc trên đúng mà không cần
     * dựng cơ sở dữ liệu — {@code payment_transactions} có cột {@code jsonb},
     * mà H2 không tạo nổi bảng ấy.
     */
    public static boolean laGieoSan(java.util.UUID id) {
        return id != null && id.toString().contains(KHUON_GIEO_SAN);
    }

    private DuLieuThat() {
    }
}
