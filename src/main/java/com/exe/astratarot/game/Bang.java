package com.exe.astratarot.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bàn chơi "Giao Hội" và toàn bộ luật của nó.
 *
 * <h2>Vì sao bộ máy nằm ở máy chủ, không ở trình duyệt</h2>
 *
 * Điểm trong game đổi được ra lượt AI, và một lượt AI là một lượt gọi Gemini có
 * hoá đơn thật. Nếu trình duyệt tự tính điểm rồi gửi lên thì ai mở Console cũng
 * gửi được điểm tối đa mỗi ngày mà không cần chơi — trần điểm chỉ chặn được mức
 * thiệt hại, không chặn được chuyện ấy.
 *
 * <p>Nên bàn chơi nằm ở đây. Trình duyệt gửi "đổi ô này với ô kia", máy chủ tự
 * kiểm nước đi có hợp lệ không và tự cộng điểm. Không có đường nào để gửi điểm
 * lên: API không nhận điểm.
 *
 * <h2>Vì sao chỉ có một bản bộ máy</h2>
 *
 * Cách làm còn lại là viết bộ máy hai lần — một bản TypeScript ở trình duyệt cho
 * chạy mượt, một bản Java ở máy chủ để kiểm lại. Hai bản phải khớp nhau đến từng
 * con số, kể cả bộ sinh số ngẫu nhiên, nên một chỗ lệch là điểm hai bên khác
 * nhau và người chơi mất điểm mà không hiểu vì sao. Ở đây máy chủ trả sẵn từng
 * bậc dây chuyền, trình duyệt chỉ diễn lại — nên chỉ có một bản luật.
 *
 * <h2>Hệ toạ độ</h2>
 *
 * Ô được đánh số {@code hang * CANH + cot}, hàng 0 ở TRÊN. Trọng lực kéo về
 * hàng lớn, ô mới rót vào từ hàng 0.
 */
public final class Bang {

    private final char[] o;
    private final MayNgauNhien may;

    private Bang(char[] o, MayNgauNhien may) {
        this.o = o;
        this.may = may;
    }

    // ------------------------------------------------------------------
    // Dựng bàn
    // ------------------------------------------------------------------

    /**
     * Bàn mới: không có chuỗi nào sẵn, và chắc chắn có ít nhất một nước đi.
     *
     * <p>Bàn có chuỗi sẵn thì vừa mở ra đã tự nổ một tràng — người chơi được
     * điểm mà không làm gì, và không hiểu bàn vừa xảy ra chuyện gì.
     */
    public static Bang moi(MayNgauNhien may) {
        char[] o = new char[LuatGame.SO_O];
        for (int i = 0; i < o.length; i++) {
            o[i] = chonLoaiKhongTaoChuoi(o, i, may);
        }
        Bang bang = new Bang(o, may);
        if (!bang.conNuocDi()) bang.daoBan();
        return bang;
    }

    /** Dựng lại bàn đã lưu. */
    public static Bang tu(String chuoi, MayNgauNhien may) {
        if (chuoi == null || chuoi.length() != LuatGame.SO_O) {
            throw new IllegalArgumentException(
                    "Bàn phải đúng " + LuatGame.SO_O + " ký tự, nhận "
                            + (chuoi == null ? "null" : chuoi.length()));
        }
        char[] o = chuoi.toCharArray();
        for (char c : o) {
            if (c != LoaiO.RONG) LoaiO.theoMa(c);
        }
        return new Bang(o, may);
    }

    public String chuoi() {
        return new String(o);
    }

    public MayNgauNhien may() {
        return may;
    }

    /**
     * Chọn một loại ô cho vị trí {@code i} sao cho nó không khép lại chuỗi ba.
     *
     * <p>Chỉ cần nhìn hai ô bên trái và hai ô bên trên, vì các ô sau {@code i}
     * còn chưa đặt. Vòng lặp thử tối đa sáu lần — mỗi lần loại bỏ một loại, mà
     * chỉ có nhiều nhất hai loại bị cấm (một ngang, một dọc), nên luôn tìm ra.
     */
    private static char chonLoaiKhongTaoChuoi(char[] o, int i, MayNgauNhien may) {
        int hang = i / LuatGame.CANH;
        int cot = i % LuatGame.CANH;
        for (int lan = 0; lan < LoaiO.soLoai(); lan++) {
            char thu = LoaiO.theoThuTu(may.toi(LoaiO.soLoai())).ma();
            boolean ngang = cot >= 2 && o[i - 1] == thu && o[i - 2] == thu;
            boolean doc = hang >= 2
                    && o[i - LuatGame.CANH] == thu
                    && o[i - 2 * LuatGame.CANH] == thu;
            if (!ngang && !doc) return thu;
        }
        // Không tới được trong thực tế; để đây thì bàn vẫn dựng ra được chứ
        // không ném giữa lúc người chơi bấm Bắt đầu.
        return LoaiO.theoThuTu(0).ma();
    }

    // ------------------------------------------------------------------
    // Tìm chuỗi
    // ------------------------------------------------------------------

    /**
     * Mọi chuỗi từ ba ô cùng loại trở lên, ngang và dọc, trên bàn hiện tại.
     *
     * <p>Một ô nằm ở cả chuỗi ngang lẫn chuỗi dọc (hình chữ L hay chữ T) được
     * tính trong CẢ HAI chuỗi. Đó là cố ý: hình chữ L khó xếp hơn một hàng
     * thẳng, nên nó đáng được nhiều điểm hơn.
     */
    private List<List<Integer>> timCacChuoi() {
        List<List<Integer>> chuoi = new ArrayList<>();
        for (int hang = 0; hang < LuatGame.CANH; hang++) {
            gomChuoi(chuoi, hang * LuatGame.CANH, 1);
        }
        for (int cot = 0; cot < LuatGame.CANH; cot++) {
            gomChuoi(chuoi, cot, LuatGame.CANH);
        }
        return chuoi;
    }

    /** Quét một hàng hoặc một cột, bắt đầu từ {@code dau}, mỗi bước nhảy {@code buoc}. */
    private void gomChuoi(List<List<Integer>> chuoi, int dau, int buoc) {
        int batDau = 0;
        for (int k = 1; k <= LuatGame.CANH; k++) {
            boolean het = k == LuatGame.CANH;
            char truoc = o[dau + (k - 1) * buoc];
            boolean ngat = het || o[dau + k * buoc] != truoc || truoc == LoaiO.RONG;
            if (!ngat) continue;
            int dai = k - batDau;
            if (dai >= 3 && truoc != LoaiO.RONG) {
                List<Integer> mot = new ArrayList<>(dai);
                for (int t = batDau; t < k; t++) mot.add(dau + t * buoc);
                chuoi.add(mot);
            }
            batDau = k;
        }
    }

    private static int diemChuoi(int dai) {
        return LuatGame.DIEM_CHUOI_BA + LuatGame.DIEM_O_THEM * (dai - 3);
    }

    // ------------------------------------------------------------------
    // Nước đi
    // ------------------------------------------------------------------

    public static boolean keNhau(int a, int b) {
        int ha = a / LuatGame.CANH, ca = a % LuatGame.CANH;
        int hb = b / LuatGame.CANH, cb = b % LuatGame.CANH;
        return Math.abs(ha - hb) + Math.abs(ca - cb) == 1;
    }

    private void trongO(int i) {
        if (i < 0 || i >= LuatGame.SO_O) {
            throw new IllegalArgumentException("Ô " + i + " nằm ngoài bàn");
        }
    }

    /** Đổi chỗ hai ô này có tạo ra chuỗi nào không. Không làm bàn đổi. */
    public boolean doiChoHopLe(int a, int b) {
        if (a < 0 || b < 0 || a >= LuatGame.SO_O || b >= LuatGame.SO_O) return false;
        if (!keNhau(a, b)) return false;
        doi(a, b);
        boolean co = !timCacChuoi().isEmpty();
        doi(a, b);
        return co;
    }

    private void doi(int a, int b) {
        char t = o[a];
        o[a] = o[b];
        o[b] = t;
    }

    /**
     * Đổi chỗ hai ô kề nhau rồi giải hết dây chuyền.
     *
     * @throws NuocDiKhongHopLe khi hai ô không kề nhau, hoặc đổi xong không có
     *                          chuỗi nào — nước đi ấy KHÔNG tốn lượt
     */
    public KetQuaNuocDi doiCho(int a, int b) {
        trongO(a);
        trongO(b);
        if (!keNhau(a, b)) {
            throw new NuocDiKhongHopLe("Chỉ đổi được hai ô kề nhau");
        }
        if (!doiChoHopLe(a, b)) {
            throw new NuocDiKhongHopLe("Đổi hai ô này không tạo được chuỗi ba nào");
        }
        doi(a, b);
        return giaiDayChuyen(List.of());
    }

    /**
     * Dùng kĩ năng nhắm vào một ô.
     *
     * <p>Năng lượng do bên gọi kiểm — {@link Bang} không giữ năng lượng, vì nó
     * thuộc phiên chơi chứ không thuộc bàn.
     */
    public KetQuaNuocDi dungKiNang(NhanVat nhanVat, int chiSo) {
        trongO(chiSo);
        if (o[chiSo] == LoaiO.RONG) {
            throw new NuocDiKhongHopLe("Ô đã trống");
        }
        Set<Integer> dich = nhanVat.cacODichCuaKiNang(o, chiSo);
        return giaiDayChuyen(new ArrayList<>(dich));
    }

    /**
     * Giải dây chuyền cho đến khi bàn không còn chuỗi nào.
     *
     * @param xoaTruoc những ô bị kĩ năng xoá ở bậc đầu; rỗng khi là nước đổi chỗ
     */
    private KetQuaNuocDi giaiDayChuyen(List<Integer> xoaTruoc) {
        List<BuocXoa> cacBuoc = new ArrayList<>();
        int tongDiem = 0;
        int tongO = 0;
        int bac = 1;

        List<Integer> xoa = xoaTruoc;
        int diemThoBac = xoa.size() * LuatGame.DIEM_O_KI_NANG;

        while (bac <= LuatGame.TOI_DA_BAC_DAY_CHUYEN) {
            if (xoa.isEmpty()) {
                List<List<Integer>> chuoi = timCacChuoi();
                if (chuoi.isEmpty()) break;
                Set<Integer> hop = new LinkedHashSet<>();
                diemThoBac = 0;
                for (List<Integer> c : chuoi) {
                    hop.addAll(c);
                    diemThoBac += diemChuoi(c.size());
                }
                xoa = new ArrayList<>(new TreeSet<>(hop));
            } else {
                xoa = new ArrayList<>(new TreeSet<>(xoa));
            }

            double heSo = 1.0 + 0.5 * (bac - 1);
            int diem = (int) Math.floor(diemThoBac * heSo);

            for (int i : xoa) o[i] = LoaiO.RONG;
            String sauXoa = new String(o);
            roi();
            napMoi();

            cacBuoc.add(new BuocXoa(xoa, sauXoa, new String(o), diem, heSo, bac));
            tongDiem += diem;
            tongO += xoa.size();

            xoa = List.of();
            bac++;
        }

        boolean daoBan = false;
        if (!conNuocDi()) {
            daoBan();
            daoBan = true;
        }
        return new KetQuaNuocDi(cacBuoc, tongDiem, tongO, daoBan, new String(o));
    }

    // ------------------------------------------------------------------
    // Trọng lực và rót ô mới
    // ------------------------------------------------------------------

    private void roi() {
        for (int cot = 0; cot < LuatGame.CANH; cot++) {
            int viTri = LuatGame.CANH - 1;
            for (int hang = LuatGame.CANH - 1; hang >= 0; hang--) {
                char c = o[hang * LuatGame.CANH + cot];
                if (c != LoaiO.RONG) {
                    o[viTri * LuatGame.CANH + cot] = c;
                    viTri--;
                }
            }
            for (int hang = viTri; hang >= 0; hang--) {
                o[hang * LuatGame.CANH + cot] = LoaiO.RONG;
            }
        }
    }

    private void napMoi() {
        for (int i = 0; i < o.length; i++) {
            if (o[i] == LoaiO.RONG) {
                o[i] = LoaiO.theoThuTu(may.toi(LoaiO.soLoai())).ma();
            }
        }
    }

    // ------------------------------------------------------------------
    // Bàn chết
    // ------------------------------------------------------------------

    /** Có nước đổi chỗ nào tạo được chuỗi không. */
    public boolean conNuocDi() {
        for (int i = 0; i < LuatGame.SO_O; i++) {
            int cot = i % LuatGame.CANH;
            if (cot + 1 < LuatGame.CANH && doiChoHopLe(i, i + 1)) return true;
            if (i + LuatGame.CANH < LuatGame.SO_O && doiChoHopLe(i, i + LuatGame.CANH)) return true;
        }
        return false;
    }

    /**
     * Trộn lại chính những ô đang có trên bàn cho đến khi lại có nước đi.
     *
     * <h2>Vì sao trộn chứ không dựng bàn mới</h2>
     *
     * Dựng bàn mới là rót vào một bộ ô khác hẳn. Người chơi vừa dọn gần hết một
     * loại ô để chuẩn bị kĩ năng thì bàn mới xoá công ấy đi. Trộn giữ nguyên số
     * lượng từng loại, nên chỉ chỗ đứng đổi.
     */
    private void daoBan() {
        for (int lan = 0; lan < 200; lan++) {
            for (int i = o.length - 1; i > 0; i--) {
                int j = may.toi(i + 1);
                doi(i, j);
            }
            if (timCacChuoi().isEmpty() && conNuocDi()) return;
        }
        // Trộn mãi không ra thì bộ ô trên bàn thật sự không xếp được — dựng lại.
        for (int i = 0; i < o.length; i++) o[i] = LoaiO.RONG;
        for (int i = 0; i < o.length; i++) {
            o[i] = chonLoaiKhongTaoChuoi(o, i, may);
        }
    }

    /**
     * Nước đi mà người chơi gửi lên không hợp luật.
     *
     * <h2>Vì sao kế thừa {@link IllegalArgumentException}</h2>
     *
     * {@code GlobalExceptionHandler} có sẵn handler cho
     * {@code IllegalArgumentException} trả 400 kèm nguyên văn thông điệp. Để nó
     * là {@code RuntimeException} trần thì nó rơi xuống
     * {@code @ExceptionHandler(Exception)} và người chơi bấm nhầm hai ô sẽ nhận
     * một trang 500 — đúng cái đã xảy ra với đăng nhập Google trước đây.
     */
    public static class NuocDiKhongHopLe extends IllegalArgumentException {
        public NuocDiKhongHopLe(String thongDiep) {
            super(thongDiep);
        }
    }
}
