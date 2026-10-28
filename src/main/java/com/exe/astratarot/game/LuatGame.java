package com.exe.astratarot.game;

/**
 * Mọi con số của mini game "Giao Hội" nằm ở đây.
 *
 * <h2>Vì sao gom vào một chỗ</h2>
 *
 * Trần điểm và trần lượt thưởng là thứ chặn thiệt hại: một lượt AI là một lượt
 * gọi Gemini có hoá đơn thật. Rải các con số ấy vào trong bộ máy game thì sau
 * này muốn biết "chơi cả ngày thì tối đa được mấy lượt" phải đi đọc ba bốn tệp.
 */
public final class LuatGame {

    private LuatGame() {}

    /** Bàn vuông 7×7. Nhỏ hơn thì ít nước đi, lớn hơn thì ô bé quá trên điện thoại. */
    public static final int CANH = 7;

    public static final int SO_O = CANH * CANH;

    /** Số lượt đổi chỗ cho mỗi phiên. Một phiên gọn chừng một hai phút. */
    public static final int LUOT_MOI_PHIEN = 20;

    /** Điểm cho một chuỗi 3 ô. */
    public static final int DIEM_CHUOI_BA = 30;

    /** Mỗi ô dài thêm ngoài 3 ô. */
    public static final int DIEM_O_THEM = 20;

    /** Mỗi ô kĩ năng xoá ăn bấy nhiêu điểm — kĩ năng không tạo chuỗi nên không tính theo chuỗi. */
    public static final int DIEM_O_KI_NANG = 20;

    /** Mỗi ô bị xoá nạp một điểm năng lượng cho kĩ năng. */
    public static final int NANG_LUONG_MOI_O = 1;

    /** Chống vòng lặp vô tận nếu có lỗi logic làm dây chuyền không bao giờ dừng. */
    public static final int TOI_DA_BAC_DAY_CHUYEN = 30;

    // ---- Quy đổi sang lượt AI ----

    /** Bao nhiêu điểm thành một lượt AI thưởng. */
    public static final int DIEM_MOI_LUOT = 1000;

    /**
     * Điểm tối đa trong một ngày được tính vào quy đổi.
     *
     * <p>Chơi thêm vẫn chơi được — chỉ là không cộng thêm điểm quy đổi nữa. Giữ
     * cho người ta chơi vì vui chứ không phải cày.
     */
    public static final int TRAN_DIEM_NGAY = 6000;

    /**
     * Lượt AI thưởng tối đa một ngày.
     *
     * <p>Gói miễn phí là 3 lượt/ngày, nên 3 lượt thưởng là nhiều nhất gấp đôi.
     * Lượt thưởng hết hạn cuối ngày y như hạn mức thường, nên không ai dồn được
     * một kho lượt rồi tiêu một lúc.
     */
    public static final int TRAN_LUOT_NGAY = 3;
}
