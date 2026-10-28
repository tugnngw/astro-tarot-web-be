package com.exe.astratarot.game;

import java.util.List;

/**
 * Kết quả một nước đi: đổi chỗ hoặc dùng kĩ năng.
 *
 * @param cacBuoc   từng bậc dây chuyền theo thứ tự, để giao diện diễn lại
 * @param diemThem  tổng điểm cộng vào phiên
 * @param soOXoa    tổng số ô đã xoá, dùng để nạp năng lượng kĩ năng
 * @param daDaoBan  bàn hết nước đi nên máy chủ đã đảo lại các ô
 * @param bangCuoi  bàn sau cùng, để giao diện chốt lại nếu bỏ qua hoạt ảnh
 */
public record KetQuaNuocDi(
        List<BuocXoa> cacBuoc,
        int diemThem,
        int soOXoa,
        boolean daDaoBan,
        String bangCuoi) {
}
