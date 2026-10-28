package com.exe.astratarot.game;

/**
 * Sáu biểu tượng trên bàn: bốn hoa của bộ Tarot, cộng Sao và Trăng.
 *
 * <h2>Vì sao sáu</h2>
 *
 * Năm loại thì dây chuyền tự nổ liên tục, bàn gần như tự chơi. Bảy loại thì
 * xác suất ba ô cùng loại gặp nhau thấp quá, người chơi ngồi tìm. Sáu là con số
 * các game xếp ba phổ biến dùng, và vừa đủ để mỗi loại có một biểu tượng riêng
 * phân biệt được ở kích thước ô trên điện thoại.
 *
 * <p>Ký tự {@link #ma()} là thứ được lưu xuống cơ sở dữ liệu và gửi cho giao
 * diện: cả bàn 7×7 nằm gọn trong một chuỗi 49 ký tự, đọc log cũng thấy được bàn.
 */
public enum LoaiO {

    GAY('1', "Gậy"),
    COC('2', "Cốc"),
    GUOM('3', "Gươm"),
    TIEN('4', "Tiền"),
    SAO('5', "Sao"),
    TRANG('6', "Trăng");

    /** Ô rỗng — chỉ tồn tại giữa lúc xoá và lúc rót ô mới xuống. */
    public static final char RONG = '0';

    private final char ma;
    private final String ten;

    LoaiO(char ma, String ten) {
        this.ma = ma;
        this.ten = ten;
    }

    public char ma() {
        return ma;
    }

    public String ten() {
        return ten;
    }

    private static final LoaiO[] TAT_CA = values();

    public static int soLoai() {
        return TAT_CA.length;
    }

    public static LoaiO theoThuTu(int i) {
        return TAT_CA[i];
    }

    public static LoaiO theoMa(char ma) {
        for (LoaiO l : TAT_CA) {
            if (l.ma == ma) return l;
        }
        throw new IllegalArgumentException("Không có loại ô mang ký tự '" + ma + "'");
    }
}
