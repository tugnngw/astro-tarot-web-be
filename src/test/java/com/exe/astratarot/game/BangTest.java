package com.exe.astratarot.game;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Luật của bàn "Giao Hội".
 *
 * <h2>Vì sao dựng bàn bằng tay thay vì chơi thật rồi xem điểm</h2>
 *
 * Bàn thật rót ô mới ngẫu nhiên, nên một nước đi hay kéo theo dây chuyền — và
 * khi đó không biết điểm nhận được là của chuỗi mình vừa xếp hay của dây chuyền
 * ăn may. Kiểm thử kiểu ấy hễ đổi hạt giống là đỏ, mà đỏ không nói lên điều gì.
 *
 * <p>Nên các bài ở đây dựng bàn từ một khuôn KHÔNG có chuỗi nào sẵn, đặt vào
 * đúng một hình, rồi chỉ soi BẬC ĐẦU của dây chuyền — bậc ấy do bàn quyết định
 * hoàn toàn, không phụ thuộc ô nào sắp rót xuống.
 */
class BangTest {

    /**
     * Khuôn bàn bảo đảm không có chuỗi ba nào.
     *
     * <p>Ngang thì hai ô cạnh nhau lệch 1, dọc thì lệch 2 — cả hai đều khác 0
     * theo modulo 6, nên không bao giờ có hai ô kề nhau cùng loại, càng không
     * có ba.
     */
    private static char[][] khuon() {
        char[][] g = new char[LuatGame.CANH][LuatGame.CANH];
        for (int h = 0; h < LuatGame.CANH; h++) {
            for (int c = 0; c < LuatGame.CANH; c++) {
                g[h][c] = LoaiO.theoThuTu((h * 2 + c) % LoaiO.soLoai()).ma();
            }
        }
        return g;
    }

    private static String chuoi(char[][] g) {
        StringBuilder sb = new StringBuilder(LuatGame.SO_O);
        for (char[] hang : g) sb.append(hang);
        return sb.toString();
    }

    private static int chiSo(int hang, int cot) {
        return hang * LuatGame.CANH + cot;
    }

    private static Bang tuKhuon(char[][] g) {
        return Bang.tu(chuoi(g), new MayNgauNhien(12345L));
    }

    // ------------------------------------------------------------------
    // Dựng bàn
    // ------------------------------------------------------------------

    @Test
    @DisplayName("bàn mới không có chuỗi nào sẵn và luôn còn nước đi")
    void banMoiSachVaChoiDuoc() {
        // Nhiều hạt giống, vì một hạt giống may mắn không chứng minh được gì.
        for (long hg = 1; hg <= 200; hg++) {
            Bang b = Bang.moi(new MayNgauNhien(hg));
            String truoc = b.chuoi();

            assertEquals(LuatGame.SO_O, truoc.length(), "hạt giống " + hg);
            assertFalse(truoc.indexOf(LoaiO.RONG) >= 0,
                    "bàn mới không được có ô rỗng, hạt giống " + hg);
            assertTrue(b.conNuocDi(), "bàn mới phải còn nước đi, hạt giống " + hg);

            // Có chuỗi sẵn thì bàn sẽ tự nổ ngay khi người chơi đi nước đầu —
            // cách duy nhất nhìn thấy từ ngoài là bàn đổi mà chưa ai làm gì.
            assertFalse(coChuoi(truoc), "bàn mới có chuỗi sẵn, hạt giống " + hg);
        }
    }

    /** Dò chuỗi ba ngay trên chuỗi ký tự, độc lập với cách {@link Bang} dò. */
    private static boolean coChuoi(String bang) {
        for (int h = 0; h < LuatGame.CANH; h++) {
            for (int c = 0; c + 2 < LuatGame.CANH; c++) {
                char x = bang.charAt(chiSo(h, c));
                if (x != LoaiO.RONG
                        && x == bang.charAt(chiSo(h, c + 1))
                        && x == bang.charAt(chiSo(h, c + 2))) return true;
            }
        }
        for (int c = 0; c < LuatGame.CANH; c++) {
            for (int h = 0; h + 2 < LuatGame.CANH; h++) {
                char x = bang.charAt(chiSo(h, c));
                if (x != LoaiO.RONG
                        && x == bang.charAt(chiSo(h + 1, c))
                        && x == bang.charAt(chiSo(h + 2, c))) return true;
            }
        }
        return false;
    }

    /**
     * Phiên chơi sống qua lượt gọi HTTP bằng cách lưu bàn VÀ hạt giống. Quên
     * hạt giống thì nước đi sau khi nạp lại rót ra ô khác, nên bài này canh
     * đúng chỗ dễ sót nhất khi sửa phần lưu trữ.
     */
    @Test
    @DisplayName("lưu bàn kèm hạt giống rồi nạp lại thì nước đi tiếp theo y hệt")
    void napLaiThiDiTiepGiongHet() {
        char[][] g = khuon();
        g[3][1] = LoaiO.theoThuTu(0).ma();

        Bang mot = Bang.tu(chuoi(g), new MayNgauNhien(777L));
        KetQuaNuocDi kqMot = mot.doiCho(chiSo(3, 2), chiSo(2, 2));

        Bang hai = Bang.tu(chuoi(g), new MayNgauNhien(777L));
        KetQuaNuocDi kqHai = hai.doiCho(chiSo(3, 2), chiSo(2, 2));

        assertAll(
                () -> assertEquals(kqMot.bangCuoi(), kqHai.bangCuoi()),
                () -> assertEquals(kqMot.diemThem(), kqHai.diemThem()),
                () -> assertEquals(mot.may().hatGiong(), hai.may().hatGiong()));

        // Và hạt giống phải thật sự chạy — nếu nó đứng im thì bài trên vẫn
        // xanh trong khi ô mới rót xuống lặp lại mãi một loại.
        assertNotEquals(777L, mot.may().hatGiong());
    }

    // ------------------------------------------------------------------
    // Nước đổi chỗ
    // ------------------------------------------------------------------

    @Test
    @DisplayName("đổi chỗ tạo chuỗi ba thì xoá đúng ba ô ấy, được 30 điểm")
    void chuoiBaDung30Diem() {
        char[][] g = khuon();
        // Hàng 3 vốn là 1,2,3,4,5,6,1 — đổi ô (3,1) thành Gậy để có 1,_,3...
        g[3][1] = LoaiO.theoThuTu(0).ma();

        Bang b = tuKhuon(g);
        assertTrue(b.doiChoHopLe(chiSo(3, 2), chiSo(2, 2)),
                "đổi (3,2) với (2,2) phải tạo được chuỗi");

        KetQuaNuocDi kq = b.doiCho(chiSo(3, 2), chiSo(2, 2));
        BuocXoa dau = kq.cacBuoc().get(0);

        assertAll(
                () -> assertEquals(List.of(chiSo(3, 0), chiSo(3, 1), chiSo(3, 2)),
                        dau.cacOXoa()),
                () -> assertEquals(LuatGame.DIEM_CHUOI_BA, dau.diem()),
                () -> assertEquals(1.0, dau.heSo()),
                () -> assertEquals(1, dau.bac()));
    }

    @Test
    @DisplayName("hai ô không kề nhau thì không phải nước đi")
    void khongKeNhauThiNem() {
        Bang b = tuKhuon(khuon());
        assertThrows(Bang.NuocDiKhongHopLe.class,
                () -> b.doiCho(chiSo(0, 0), chiSo(0, 2)));
        assertThrows(Bang.NuocDiKhongHopLe.class,
                () -> b.doiCho(chiSo(0, 0), chiSo(0, 0)));
    }

    /**
     * Đây là chỗ một lỗi sẽ im lặng ăn lượt của người chơi: nếu nước đi vô
     * nghĩa vẫn trừ lượt thì ai bấm nhầm hai ô cũng mất một lượt trong hai
     * mươi, mà trên màn hình không có gì xảy ra để họ hiểu vì sao.
     */
    @Test
    @DisplayName("đổi chỗ không tạo chuỗi thì bị từ chối và bàn không đổi")
    void khongTaoChuoiThiTuChoi() {
        Bang b = tuKhuon(khuon());
        String truoc = b.chuoi();
        long hatGiongTruoc = b.may().hatGiong();

        assertThrows(Bang.NuocDiKhongHopLe.class,
                () -> b.doiCho(chiSo(0, 0), chiSo(0, 1)));

        assertEquals(truoc, b.chuoi(), "bàn phải y nguyên");
        assertEquals(hatGiongTruoc, b.may().hatGiong(),
                "nước đi bị từ chối không được tiêu số ngẫu nhiên nào");
    }

    @Test
    @DisplayName("chuỗi bốn ô ăn nhiều hơn chuỗi ba")
    void chuoiBonAnNhieuHon() {
        char[][] g = khuon();
        char gay = LoaiO.theoThuTu(0).ma();
        // Hàng 3: Gậy ở cột 0,1,3 — đổi (3,2) lên để khép thành bốn ô liền.
        g[3][1] = gay;
        g[3][3] = gay;

        Bang b = tuKhuon(g);
        KetQuaNuocDi kq = b.doiCho(chiSo(3, 2), chiSo(2, 2));
        BuocXoa dau = kq.cacBuoc().get(0);

        assertEquals(4, dau.cacOXoa().size());
        assertEquals(LuatGame.DIEM_CHUOI_BA + LuatGame.DIEM_O_THEM, dau.diem());
    }

    /**
     * Hình chữ L được tính hai lần — một cho chuỗi ngang, một cho chuỗi dọc.
     * Cố ý như vậy vì xếp được chữ L khó hơn xếp một hàng thẳng.
     */
    @Test
    @DisplayName("hình chữ L tính cả chuỗi ngang và chuỗi dọc")
    void hinhChuLTinhCaHaiChuoi() {
        char[][] g = khuon();
        char gay = LoaiO.theoThuTu(0).ma();
        g[3][1] = gay;          // hàng 3 có Gậy ở cột 0, 1
        g[4][2] = gay;          // cột 2 có Gậy ở hàng 4, 5
        g[5][2] = gay;

        Bang b = tuKhuon(g);
        // Đưa Gậy từ (2,2) xuống (3,2): khép hàng 3 (cột 0,1,2) và cột 2
        // (hàng 3,4,5) cùng lúc.
        KetQuaNuocDi kq = b.doiCho(chiSo(3, 2), chiSo(2, 2));
        BuocXoa dau = kq.cacBuoc().get(0);

        assertEquals(5, dau.cacOXoa().size(), "năm ô riêng biệt trong hình chữ L");
        assertEquals(LuatGame.DIEM_CHUOI_BA * 2, dau.diem(),
                "hai chuỗi ba, ô góc được tính trong cả hai");
    }

    // ------------------------------------------------------------------
    // Dây chuyền
    // ------------------------------------------------------------------

    @Test
    @DisplayName("bậc dây chuyền sau ăn hệ số cao hơn bậc trước")
    void heSoTangTheoBac() {
        // Chơi nhiều phiên tới khi gặp một dây chuyền từ hai bậc, rồi soi hệ số.
        // Không dựng bàn tay được vì bậc hai do ô RÓT XUỐNG quyết định.
        for (long hg = 1; hg <= 400; hg++) {
            Bang b = Bang.moi(new MayNgauNhien(hg));
            KetQuaNuocDi kq = nuocDiDauTien(b);
            if (kq == null || kq.cacBuoc().size() < 2) continue;

            for (int i = 0; i < kq.cacBuoc().size(); i++) {
                BuocXoa buoc = kq.cacBuoc().get(i);
                assertEquals(i + 1, buoc.bac());
                assertEquals(1.0 + 0.5 * i, buoc.heSo(),
                        "hệ số bậc " + buoc.bac());
            }
            return;
        }
        throw new AssertionError("400 hạt giống mà không gặp dây chuyền nào từ hai bậc");
    }

    @Test
    @DisplayName("sau mỗi nước đi bàn vẫn đầy, không còn ô rỗng")
    void banLuonDayLaiSauNuocDi() {
        for (long hg = 1; hg <= 60; hg++) {
            Bang b = Bang.moi(new MayNgauNhien(hg));
            for (int luot = 0; luot < LuatGame.LUOT_MOI_PHIEN; luot++) {
                KetQuaNuocDi kq = nuocDiDauTien(b);
                assertNotNull(kq, "bàn hết nước đi ở lượt " + luot
                        + ", hạt giống " + hg + " — đảo bàn đã không chạy");
                assertFalse(b.chuoi().indexOf(LoaiO.RONG) >= 0,
                        "còn ô rỗng sau nước đi, hạt giống " + hg);
                assertEquals(LuatGame.SO_O, b.chuoi().length());
                // Bàn nghỉ phải không còn chuỗi nào: dây chuyền đã giải hết.
                assertFalse(coChuoi(kq.bangCuoi()),
                        "bàn nghỉ vẫn còn chuỗi chưa nổ, hạt giống " + hg);
            }
        }
    }

    /**
     * Ô còn lại rơi xuống lấp chỗ trống, và ô mới chỉ rót vào từ trên.
     *
     * <h2>Vì sao cần một bài riêng cho trọng lực</h2>
     *
     * Bỏ hẳn bước rơi đi thì mọi bài khác ở tệp này vẫn xanh: bước rót ô mới
     * lấp hết ô rỗng nên bàn trông vẫn đầy, điểm vẫn đúng, dây chuyền vẫn
     * chạy. Thứ duy nhất sai là ô đứng sai chỗ — người chơi thấy ô lơ lửng
     * giữa bàn trong khi bên dưới vừa nổ. Bài này đối chiếu từng ô một.
     */
    @Test
    @DisplayName("ô bên trên rơi xuống đúng chỗ, ô mới chỉ vào từ trên")
    void oRoiXuongDungCho() {
        char[][] g = khuon();
        String banDau = chuoi(g);
        Bang b = Bang.tu(banDau, new MayNgauNhien(55L));

        // Quang Phát ở (5,3) dọn khối hàng 4..6 × cột 2..4 — chừa đúng bốn ô
        // phía trên mỗi cột ấy để chúng phải rơi xuống ba bậc.
        BuocXoa dau = b.dungKiNang(NhanVat.SOL, chiSo(5, 3)).cacBuoc().get(0);
        assertEquals(9, dau.cacOXoa().size());

        String sau = dau.bangSau();
        for (int cot = 2; cot <= 4; cot++) {
            for (int k = 0; k <= 3; k++) {
                assertEquals(
                        banDau.charAt(chiSo(3 - k, cot)),
                        sau.charAt(chiSo(6 - k, cot)),
                        "ô (" + (3 - k) + "," + cot + ") phải rơi xuống hàng " + (6 - k));
            }
            for (int hang = 0; hang <= 2; hang++) {
                assertNotEquals(LoaiO.RONG, sau.charAt(chiSo(hang, cot)),
                        "ba hàng trên cột " + cot + " phải được rót ô mới");
            }
        }

        // Các cột không bị kĩ năng chạm tới thì y nguyên, không được xô lệch.
        for (int cot : new int[] {0, 1, 5, 6}) {
            for (int hang = 0; hang < LuatGame.CANH; hang++) {
                assertEquals(banDau.charAt(chiSo(hang, cot)), sau.charAt(chiSo(hang, cot)),
                        "cột " + cot + " không bị chạm mà lại đổi ở hàng " + hang);
            }
        }
    }

    /** Đi nước hợp lệ đầu tiên tìm được; null khi bàn không còn nước nào. */
    private static KetQuaNuocDi nuocDiDauTien(Bang b) {
        for (int i = 0; i < LuatGame.SO_O; i++) {
            int cot = i % LuatGame.CANH;
            if (cot + 1 < LuatGame.CANH && b.doiChoHopLe(i, i + 1)) {
                return b.doiCho(i, i + 1);
            }
            if (i + LuatGame.CANH < LuatGame.SO_O && b.doiChoHopLe(i, i + LuatGame.CANH)) {
                return b.doiCho(i, i + LuatGame.CANH);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Kĩ năng nhân vật
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Triều Âm của Luna gọi về mọi ô cùng loại")
    void trieuAmXoaHetMotLoai() {
        char[][] g = khuon();
        Bang b = tuKhuon(g);

        char loai = chuoi(g).charAt(chiSo(3, 3));
        long demTruoc = chuoi(g).chars().filter(c -> c == loai).count();

        KetQuaNuocDi kq = b.dungKiNang(NhanVat.LUNA, chiSo(3, 3));
        BuocXoa dau = kq.cacBuoc().get(0);

        assertEquals(demTruoc, dau.cacOXoa().size());
        assertEquals(demTruoc * LuatGame.DIEM_O_KI_NANG, dau.diem());
        // Bàn ngay sau khi xoá không còn ô nào của loại ấy.
        assertEquals(0, dau.bangSauXoa().chars().filter(c -> c == loai).count());
    }

    @Test
    @DisplayName("Quang Phát của Sol xoá khối 3x3, ở góc thì bị bàn cắt bớt")
    void quangPhatKhoiBaNhanBa() {
        Bang giua = tuKhuon(khuon());
        assertEquals(9, giua.dungKiNang(NhanVat.SOL, chiSo(3, 3))
                .cacBuoc().get(0).cacOXoa().size());

        // Ở góc, khối 3x3 chìa ra ngoài bàn. Không cắt thì chỉ số âm, và lỗi ấy
        // chỉ lộ ra khi có người bấm đúng vào góc.
        Bang goc = tuKhuon(khuon());
        BuocXoa dau = goc.dungKiNang(NhanVat.SOL, chiSo(0, 0)).cacBuoc().get(0);
        assertEquals(4, dau.cacOXoa().size());
        assertEquals(List.of(chiSo(0, 0), chiSo(0, 1), chiSo(1, 0), chiSo(1, 1)),
                dau.cacOXoa());
    }

    @Test
    @DisplayName("Thập Tinh của Astra quét trọn hàng và cột, ô giao chỉ tính một lần")
    void thapTinhHangVaCot() {
        Bang b = tuKhuon(khuon());
        BuocXoa dau = b.dungKiNang(NhanVat.ASTRA, chiSo(2, 4)).cacBuoc().get(0);

        // 7 ô hàng + 7 ô cột, trừ ô giao bị đếm hai lần.
        assertEquals(LuatGame.CANH * 2 - 1, dau.cacOXoa().size());
        assertEquals((LuatGame.CANH * 2 - 1) * LuatGame.DIEM_O_KI_NANG, dau.diem());
    }

    @Test
    @DisplayName("kĩ năng không tốn số ngẫu nhiên nếu nhắm ra ngoài bàn")
    void kiNangNgoaiBanThiNem() {
        Bang b = tuKhuon(khuon());
        assertThrows(IllegalArgumentException.class,
                () -> b.dungKiNang(NhanVat.SOL, LuatGame.SO_O));
        assertThrows(IllegalArgumentException.class,
                () -> b.dungKiNang(NhanVat.SOL, -1));
    }

    @Test
    @DisplayName("mỗi nhân vật nạp năng lượng một mức khác nhau")
    void moiNhanVatNapKhacNhau() {
        for (NhanVat nv : NhanVat.values()) {
            assertTrue(nv.nangLuongCan() > 0, nv.name());
            assertFalse(nv.ten().isBlank(), nv.name());
            assertFalse(nv.tenKiNang().isBlank(), nv.name());
            assertFalse(nv.moTaKiNang().isBlank(), nv.name());
        }
        // Triều Âm xoá khoảng một phần sáu bàn rải khắp mặt bàn — mạnh nhất,
        // nên phải nạp nặng nhất. Để Sol nạp ngang Luna là Luna thành vô dụng.
        assertTrue(NhanVat.LUNA.nangLuongCan() > NhanVat.ASTRA.nangLuongCan());
        assertTrue(NhanVat.ASTRA.nangLuongCan() > NhanVat.SOL.nangLuongCan());
    }

    // ------------------------------------------------------------------
    // Bàn chết
    // ------------------------------------------------------------------

    /** Bàn chéo: mỗi đường chéo một loại, nên đổi hai ô nào cũng không ra chuỗi. */
    private static char[][] khuonChet() {
        char[][] g = new char[LuatGame.CANH][LuatGame.CANH];
        for (int h = 0; h < LuatGame.CANH; h++) {
            for (int c = 0; c < LuatGame.CANH; c++) {
                g[h][c] = LoaiO.theoThuTu((h + c) % LoaiO.soLoai()).ma();
            }
        }
        return g;
    }

    /**
     * Nhận ra bàn chết là điều kiện để đảo bàn chạy. Nhận sai thành "còn nước
     * đi" thì người chơi ngồi trước một bàn không bấm được gì cho tới khi hết
     * lượt, và không có thông báo nào.
     */
    @Test
    @DisplayName("nhận ra bàn không còn nước đi nào")
    void nhanRaBanChet() {
        Bang chet = Bang.tu(chuoi(khuonChet()), new MayNgauNhien(99L));
        assertFalse(chet.conNuocDi(), "bàn chéo không có nước đi nào");

        // Khuôn trơn ở {@link #khuon()} cũng KHÔNG có nước đi nào — hai ô kề
        // nhau luôn lệch 1 hoặc 2 theo modulo 6, nên đổi chỗ kiểu gì cũng
        // không ghép được ba. Phải đặt thêm một ô vào mới có nước đi, và đó
        // chính là thứ các bài chấm điểm ở trên làm.
        char[][] g = khuon();
        g[3][1] = LoaiO.theoThuTu(0).ma();
        assertTrue(tuKhuon(g).conNuocDi(), "đặt thêm một ô là có nước đi");

        // Và phải là nước đúng chỗ ấy, không phải một nước nào khác tình cờ.
        assertTrue(tuKhuon(g).doiChoHopLe(chiSo(3, 2), chiSo(2, 2)));
    }

    /**
     * Đảo bàn giữ nguyên số lượng từng loại ô. Dựng bàn mới thay vì đảo thì
     * người chơi vừa dọn gần hết một loại để nạp kĩ năng sẽ mất sạch công ấy —
     * và cách duy nhất thấy được từ ngoài là đếm từng loại trước và sau.
     */
    @Test
    @DisplayName("đảo bàn chết xong vẫn chơi được, và không đổi số lượng từng loại")
    void daoBanGiuNguyenSoLuongTungLoai() {
        char[][] g = khuonChet();
        String truoc = chuoi(g);

        // Kĩ năng chạy được trên cả bàn chết, nên nó là đường duy nhất đưa một
        // bàn chết vào bộ giải — và bộ giải là nơi đảo bàn.
        Bang b = Bang.tu(truoc, new MayNgauNhien(2024L));
        KetQuaNuocDi kq = b.dungKiNang(NhanVat.ASTRA, chiSo(3, 3));

        assertTrue(b.conNuocDi(), "sau nước đi bàn phải chơi được");
        if (!kq.daDaoBan()) return; // dây chuyền tình cờ đã mở ra nước đi

        // Đảo thì chỉ chỗ đứng đổi: đếm từng loại phải y nguyên so với ngay
        // trước lúc đảo, tức là bàn ở bậc cuối của dây chuyền.
        String truocDao = kq.cacBuoc().get(kq.cacBuoc().size() - 1).bangSau();
        for (LoaiO l : LoaiO.values()) {
            assertEquals(
                    truocDao.chars().filter(c -> c == l.ma()).count(),
                    kq.bangCuoi().chars().filter(c -> c == l.ma()).count(),
                    "số ô " + l.ten() + " đổi sau khi đảo bàn");
        }
    }

    @Test
    @DisplayName("dựng lại từ chuỗi sai độ dài thì ném ngay")
    void chuoiSaiThiNem() {
        MayNgauNhien may = new MayNgauNhien(1L);
        assertThrows(IllegalArgumentException.class, () -> Bang.tu(null, may));
        assertThrows(IllegalArgumentException.class, () -> Bang.tu("123", may));
        assertThrows(IllegalArgumentException.class,
                () -> Bang.tu("9".repeat(LuatGame.SO_O), may));
    }
}
