package com.exe.astratarot.game;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ba nhân vật tượng trưng, mỗi người một kĩ năng tác động lên bàn.
 *
 * <h2>Vì sao kĩ năng nào cũng cần người chơi chỉ một ô</h2>
 *
 * Kĩ năng tự chọn chỗ nổ thì người chơi không có gì để quyết — nạp đầy rồi bấm,
 * thế là xong, và nó thành một cái đèn báo chứ không phải một nước đi. Bắt chỉ ô
 * đích làm cả ba kĩ năng thành quyết định thật: Quang Phát muốn chỉ vào chỗ đông
 * ô, Triều Âm muốn chỉ vào loại ô đang nhiều nhất, Thập Tinh muốn hàng và cột
 * nào đang dày.
 *
 * <p>Nó cũng cho ba kĩ năng dùng chung một đường dẫn API và một ô nhập liệu, nên
 * thêm nhân vật thứ tư về sau không phải sửa giao diện.
 *
 * <h2>Vì sao kĩ năng không tốn lượt đổi chỗ</h2>
 *
 * Năng lượng chỉ nạp từ số ô đã xoá, mà ô chỉ xoá được bằng lượt đổi chỗ. Bắt kĩ
 * năng tốn thêm một lượt là thu lại đúng thứ vừa thưởng, và người chơi sẽ học
 * được là đừng bấm.
 */
public enum NhanVat {

    /**
     * Luna — Triều Âm: gọi về mọi ô cùng loại với ô được chỉ.
     *
     * <p>Nạp nặng nhất vì đây là kĩ năng mạnh nhất: một phát xoá chừng một phần
     * sáu bàn, rải khắp mặt bàn nên gần như chắc chắn kéo theo dây chuyền.
     */
    LUNA("Luna", "Triều Âm", "Gọi về mọi ô cùng loại với ô bạn chỉ", 100) {
        @Override
        Set<Integer> cacODichCuaKiNang(char[] o, int chiSo) {
            char loai = o[chiSo];
            Set<Integer> dich = new LinkedHashSet<>();
            for (int i = 0; i < o.length; i++) {
                if (o[i] == loai) dich.add(i);
            }
            return dich;
        }
    },

    /** Sol — Quang Phát: thiêu rụi khối 3×3 quanh ô được chỉ. */
    SOL("Sol", "Quang Phát", "Thiêu khối 3×3 quanh ô bạn chỉ", 80) {
        @Override
        Set<Integer> cacODichCuaKiNang(char[] o, int chiSo) {
            int hang = chiSo / LuatGame.CANH;
            int cot = chiSo % LuatGame.CANH;
            Set<Integer> dich = new LinkedHashSet<>();
            for (int h = hang - 1; h <= hang + 1; h++) {
                for (int c = cot - 1; c <= cot + 1; c++) {
                    if (h >= 0 && h < LuatGame.CANH && c >= 0 && c < LuatGame.CANH) {
                        dich.add(h * LuatGame.CANH + c);
                    }
                }
            }
            return dich;
        }
    },

    /** Astra — Thập Tinh: một vệt sáng dọc và một vệt ngang qua ô được chỉ. */
    ASTRA("Astra", "Thập Tinh", "Quét trọn hàng và cột qua ô bạn chỉ", 90) {
        @Override
        Set<Integer> cacODichCuaKiNang(char[] o, int chiSo) {
            int hang = chiSo / LuatGame.CANH;
            int cot = chiSo % LuatGame.CANH;
            Set<Integer> dich = new LinkedHashSet<>();
            for (int c = 0; c < LuatGame.CANH; c++) dich.add(hang * LuatGame.CANH + c);
            for (int h = 0; h < LuatGame.CANH; h++) dich.add(h * LuatGame.CANH + cot);
            return dich;
        }
    };

    private final String ten;
    private final String tenKiNang;
    private final String moTaKiNang;
    private final int nangLuongCan;

    NhanVat(String ten, String tenKiNang, String moTaKiNang, int nangLuongCan) {
        this.ten = ten;
        this.tenKiNang = tenKiNang;
        this.moTaKiNang = moTaKiNang;
        this.nangLuongCan = nangLuongCan;
    }

    public String ten() {
        return ten;
    }

    public String tenKiNang() {
        return tenKiNang;
    }

    public String moTaKiNang() {
        return moTaKiNang;
    }

    public int nangLuongCan() {
        return nangLuongCan;
    }

    /**
     * Những ô kĩ năng sẽ xoá khi người chơi chỉ vào {@code chiSo}.
     *
     * <p>Chỉ gói trong {@link Bang} mới gọi — bàn chơi phải là nguồn duy nhất
     * quyết định xoá ô nào, nên bên ngoài không cần thấy hàm này.
     */
    abstract Set<Integer> cacODichCuaKiNang(char[] o, int chiSo);
}
