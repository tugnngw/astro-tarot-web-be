package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.game.GameDtos.NuocDiResponse;
import com.exe.astratarot.domain.dto.game.GameDtos.TrangThaiResponse;
import com.exe.astratarot.domain.entity.GameNgay;
import com.exe.astratarot.domain.entity.GamePhien;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.game.Bang;
import com.exe.astratarot.game.LuatGame;
import com.exe.astratarot.game.MayNgauNhien;
import com.exe.astratarot.game.NhanVat;
import com.exe.astratarot.repository.GameNgayRepository;
import com.exe.astratarot.repository.GamePhienRepository;
import com.exe.astratarot.service.impl.GameServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phần kinh tế của mini game "Giao Hội".
 *
 * <h2>Vì sao tệp này quan trọng hơn tệp luật bàn chơi</h2>
 *
 * Sai luật bàn thì game chơi dở. Sai ở đây thì máy chủ phát lượt gọi Gemini
 * miễn phí quá tay, và tiền ra là tiền thật — nên các bài dưới đây nhắm vào
 * đúng hai con số chặn thiệt hại: trần điểm ngày và trần lượt ngày.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameServiceTest {

    @Mock
    private GamePhienRepository phienRepo;

    @Mock
    private GameNgayRepository ngayRepo;

    private GameServiceImpl game;
    private UUID nguoi;

    @BeforeEach
    void setUp() {
        game = new GameServiceImpl(phienRepo, ngayRepo);
        nguoi = UUID.randomUUID();
        when(phienRepo.timPhienChuaChot(any())).thenReturn(List.of());
        when(ngayRepo.findById(any())).thenReturn(Optional.empty());
        when(ngayRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(phienRepo.save(any())).thenAnswer(i -> {
            GamePhien p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
    }

    // ==================================================================
    // Quy đổi điểm sang lượt AI
    // ==================================================================

    @Nested
    @DisplayName("Quy đổi điểm sang lượt AI")
    class QuyDoi {

        private GameNgay ngay() {
            return GameNgay.builder().userId(UUID.randomUUID())
                    .ngay(java.time.LocalDate.now()).diemDaTinh(0).luotDaNhan(0).build();
        }

        @Test
        @DisplayName("đủ mốc điểm thì được một lượt, thiếu một điểm thì không")
        void duMocThiDuocLuot() {
            assertThat(ngay().congDiemVaThuong(LuatGame.DIEM_MOI_LUOT).luotMoi())
                    .isEqualTo(1);
            assertThat(ngay().congDiemVaThuong(LuatGame.DIEM_MOI_LUOT - 1).luotMoi())
                    .isZero();
        }

        /**
         * Đây là lý do lượt được tính từ TỔNG điểm trong ngày chứ không từ điểm
         * mỗi phiên. Chia từng phiên rồi làm tròn xuống thì ba phiên 400 điểm
         * cho ra không lượt nào, còn một phiên 1200 điểm cho một lượt — cùng
         * công sức mà khác kết quả, và người chơi bị luật ép phải chơi dồn.
         */
        @Test
        @DisplayName("điểm cộng dồn qua nhiều phiên trong ngày")
        void diemCongDonQuaNhieuPhien() {
            GameNgay n = ngay();
            assertThat(n.congDiemVaThuong(400).luotMoi()).isZero();
            assertThat(n.congDiemVaThuong(400).luotMoi()).isZero();
            assertThat(n.congDiemVaThuong(400).luotMoi()).isEqualTo(1);
            assertThat(n.getDiemDaTinh()).isEqualTo(1200);
            assertThat(n.getLuotDaNhan()).isEqualTo(1);
        }

        @Test
        @DisplayName("chơi bao nhiêu cũng không vượt trần lượt mỗi ngày")
        void khongVuotTranLuotNgay() {
            GameNgay n = ngay();
            int tong = 0;
            // Mỗi phiên đổ vào đúng trần điểm ngày, lặp hai mươi lần.
            for (int i = 0; i < 20; i++) {
                tong += n.congDiemVaThuong(LuatGame.TRAN_DIEM_NGAY).luotMoi();
            }
            assertThat(tong).isEqualTo(LuatGame.TRAN_LUOT_NGAY);
            assertThat(n.getLuotDaNhan()).isEqualTo(LuatGame.TRAN_LUOT_NGAY);
            assertThat(n.luotConDuocNhan()).isZero();
        }

        @Test
        @DisplayName("điểm vượt trần ngày thì không được tính, nhưng vẫn chơi được")
        void diemVuotTranThiKhongTinh() {
            GameNgay n = ngay();
            n.congDiemVaThuong(LuatGame.TRAN_DIEM_NGAY);
            assertThat(n.getDiemDaTinh()).isEqualTo(LuatGame.TRAN_DIEM_NGAY);
            assertThat(n.diemConDuocTinh()).isZero();

            // Phiên tiếp theo không cộng thêm điểm nào vào quy đổi.
            GameNgay.KetQuaCong them = n.congDiemVaThuong(5000);
            assertThat(them.diemTinh()).isZero();
            assertThat(them.luotMoi()).isZero();
            assertThat(n.getDiemDaTinh()).isEqualTo(LuatGame.TRAN_DIEM_NGAY);
        }

        @Test
        @DisplayName("phần điểm bị trần cắt được báo đúng, không báo nguyên điểm phiên")
        void baoDungPhanDiemThatSuDuocTinh() {
            GameNgay n = ngay();
            n.congDiemVaThuong(LuatGame.TRAN_DIEM_NGAY - 100);

            // Phiên này được 900 điểm nhưng chỉ còn 100 chỗ. Báo 900 cho người
            // chơi là nói sai: họ sẽ chờ thêm lượt mà không bao giờ tới.
            GameNgay.KetQuaCong cong = n.congDiemVaThuong(900);
            assertThat(cong.diemTinh()).isEqualTo(100);
        }

        @Test
        @DisplayName("điểm âm không rút bớt điểm đã tính")
        void diemAmKhongRutBot() {
            GameNgay n = ngay();
            n.congDiemVaThuong(2000);
            n.congDiemVaThuong(-5000);
            assertThat(n.getDiemDaTinh()).isEqualTo(2000);
            assertThat(n.getLuotDaNhan()).isEqualTo(2);
        }
    }

    // ==================================================================
    // Mở phiên
    // ==================================================================

    @Test
    @DisplayName("phiên mới có bàn đầy đủ, đủ lượt, chưa có năng lượng")
    void phienMoiDungTrangThaiBanDau() {
        TrangThaiResponse tt = game.batDau(nguoi, NhanVat.SOL);

        assertThat(tt.phienDangMo()).isNotNull();
        assertThat(tt.phienDangMo().bang()).hasSize(LuatGame.SO_O);
        assertThat(tt.phienDangMo().luotConLai()).isEqualTo(LuatGame.LUOT_MOI_PHIEN);
        assertThat(tt.phienDangMo().diem()).isZero();
        assertThat(tt.phienDangMo().nangLuong()).isZero();
        assertThat(tt.phienDangMo().nangLuongCan()).isEqualTo(NhanVat.SOL.nangLuongCan());
        assertThat(tt.cacNhanVat()).hasSize(NhanVat.values().length);
    }

    /**
     * Người chơi dở mà bấm "Chơi lại" — hay chỉ mở lại trang trên điện thoại —
     * không được mất điểm đã kiếm. Thưởng cho phiên cũ không mở đường ăn gian
     * nào: điểm vẫn phải kiếm bằng nước đi thật, và trần ngày vẫn chặn tổng.
     */
    @Test
    @DisplayName("mở phiên mới thì chốt và thưởng cho phiên cũ còn dở")
    void moPhienMoiThiChotPhienCu() {
        GamePhien cu = phienMau(5, 1500);
        when(phienRepo.timPhienChuaChot(nguoi)).thenReturn(List.of(cu));

        TrangThaiResponse tt = game.batDau(nguoi, NhanVat.LUNA);

        assertThat(cu.getDaChot()).isTrue();
        assertThat(tt.diemDaTinhHomNay()).isEqualTo(1500);
        assertThat(tt.luotDaNhanHomNay()).isEqualTo(1);
        verify(phienRepo).save(cu);
    }

    // ==================================================================
    // Nước đi
    // ==================================================================

    @Test
    @DisplayName("nước đi không tạo chuỗi bị từ chối và không tốn lượt")
    void nuocVoNghiaKhongTonLuot() {
        GamePhien p = phienMau(LuatGame.LUOT_MOI_PHIEN, 0);
        when(phienRepo.timVaKhoa(p.getId(), nguoi)).thenReturn(Optional.of(p));

        int[] hong = timNuocKhongHopLe(p.getBang());
        assertThatThrownBy(() -> game.doiCho(nguoi, p.getId(), hong[0], hong[1]))
                .isInstanceOf(Bang.NuocDiKhongHopLe.class);

        assertThat(p.getLuotConLai()).isEqualTo(LuatGame.LUOT_MOI_PHIEN);
        assertThat(p.getDiem()).isZero();
        verify(phienRepo, never()).save(any());
    }

    @Test
    @DisplayName("nước đi hợp lệ cộng điểm, trừ một lượt, nạp năng lượng")
    void nuocHopLeCongDiemVaNapNangLuong() {
        GamePhien p = phienMau(LuatGame.LUOT_MOI_PHIEN, 0);
        when(phienRepo.timVaKhoa(p.getId(), nguoi)).thenReturn(Optional.of(p));

        int[] tot = timNuocHopLe(p.getBang());
        NuocDiResponse kq = game.doiCho(nguoi, p.getId(), tot[0], tot[1]);

        assertThat(kq.cacBuoc()).isNotEmpty();
        assertThat(kq.diemThem()).isPositive();
        assertThat(kq.phien().luotConLai()).isEqualTo(LuatGame.LUOT_MOI_PHIEN - 1);
        assertThat(kq.phien().diem()).isEqualTo(kq.diemThem());
        assertThat(kq.phien().nangLuong()).isPositive();
        // Chưa hết lượt thì chưa chốt, nên chưa có thưởng.
        assertThat(kq.thuong()).isNull();
        assertThat(kq.phien().bang()).hasSize(LuatGame.SO_O);
    }

    @Test
    @DisplayName("nước cuối chốt phiên và trả phần thưởng")
    void nuocCuoiChotPhien() {
        GamePhien p = phienMau(1, 2500);
        when(phienRepo.timVaKhoa(p.getId(), nguoi)).thenReturn(Optional.of(p));

        int[] tot = timNuocHopLe(p.getBang());
        NuocDiResponse kq = game.doiCho(nguoi, p.getId(), tot[0], tot[1]);

        assertThat(kq.phien().luotConLai()).isZero();
        assertThat(kq.phien().daChot()).isTrue();
        assertThat(kq.thuong()).isNotNull();
        assertThat(kq.thuong().luotVuaNhan()).isEqualTo(2);
        assertThat(kq.thuong().luotConDuocNhan())
                .isEqualTo(LuatGame.TRAN_LUOT_NGAY - 2);
    }

    /**
     * Phiên đã chốt là phiên đã phát thưởng. Cho nó nhận thêm nước đi là mở
     * đường chơi tiếp rồi chốt lần nữa trên cùng một phiên.
     */
    @Test
    @DisplayName("phiên đã chốt không nhận nước đi nào nữa")
    void phienDaChotKhongDiDuoc() {
        GamePhien p = phienMau(0, 1000);
        p.setDaChot(true);
        when(phienRepo.timVaKhoa(p.getId(), nguoi)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> game.doiCho(nguoi, p.getId(), 0, 1))
                .isInstanceOf(Bang.NuocDiKhongHopLe.class)
                .hasMessageContaining("đã kết thúc");
        assertThatThrownBy(() -> game.dungKiNang(nguoi, p.getId(), 0))
                .isInstanceOf(Bang.NuocDiKhongHopLe.class);
    }

    /**
     * Truy vấn lọc theo cả id phiên LẪN id người. Chỉ tìm theo id phiên rồi so
     * chủ sở hữu ở tầng trên thì quên mất phép so ấy là ai có id phiên của
     * người khác cũng đi được nước đi trong phiên của họ.
     */
    @Test
    @DisplayName("không đi được trong phiên của người khác")
    void khongDiDuocPhienNguoiKhac() {
        UUID phienNguoiKhac = UUID.randomUUID();
        when(phienRepo.timVaKhoa(phienNguoiKhac, nguoi)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> game.doiCho(nguoi, phienNguoiKhac, 0, 1))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================================================================
    // Kĩ năng
    // ==================================================================

    @Test
    @DisplayName("chưa nạp đủ thì không bấm được kĩ năng")
    void chuaNapDuThiKhongBamDuoc() {
        GamePhien p = phienMau(10, 0);
        p.setNangLuong(NhanVat.SOL.nangLuongCan() - 1);
        when(phienRepo.timVaKhoa(p.getId(), nguoi)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> game.dungKiNang(nguoi, p.getId(), 24))
                .isInstanceOf(Bang.NuocDiKhongHopLe.class)
                .hasMessageContaining(NhanVat.SOL.tenKiNang());
        verify(phienRepo, never()).save(any());
    }

    @Test
    @DisplayName("kĩ năng tiêu hết năng lượng nhưng không tốn lượt đổi chỗ")
    void kiNangKhongTonLuot() {
        GamePhien p = phienMau(10, 0);
        p.setNangLuong(NhanVat.SOL.nangLuongCan());
        when(phienRepo.timVaKhoa(p.getId(), nguoi)).thenReturn(Optional.of(p));

        NuocDiResponse kq = game.dungKiNang(nguoi, p.getId(), 24);

        assertThat(kq.phien().luotConLai()).isEqualTo(10);
        assertThat(kq.diemThem()).isPositive();
        // Ô do chính kĩ năng xoá vẫn nạp lại một ít, nhưng không được còn đầy —
        // còn đầy là bấm được vô hạn, và kĩ năng thành nút cho điểm.
        assertThat(kq.phien().nangLuong())
                .isPositive()
                .isLessThan(NhanVat.SOL.nangLuongCan());
    }

    @Test
    @DisplayName("năng lượng không vượt quá mốc cần")
    void nangLuongKhongVuotMoc() {
        GamePhien p = phienMau(LuatGame.LUOT_MOI_PHIEN, 0);
        p.setNangLuong(NhanVat.SOL.nangLuongCan() - 1);
        when(phienRepo.timVaKhoa(p.getId(), nguoi)).thenReturn(Optional.of(p));

        int[] tot = timNuocHopLe(p.getBang());
        NuocDiResponse kq = game.doiCho(nguoi, p.getId(), tot[0], tot[1]);

        assertThat(kq.phien().nangLuong()).isEqualTo(NhanVat.SOL.nangLuongCan());
    }

    // ==================================================================
    // Phụ trợ
    // ==================================================================

    private GamePhien phienMau(int luotConLai, int diem) {
        Bang b = Bang.moi(new MayNgauNhien(424242L));
        return GamePhien.builder()
                .id(UUID.randomUUID())
                .userId(nguoi)
                .nhanVat(NhanVat.SOL)
                .bang(b.chuoi())
                .hatGiong(b.may().hatGiong())
                .diem(diem)
                .luotConLai(luotConLai)
                .nangLuong(0)
                .daChot(false)
                .build();
    }

    /** Một cặp ô kề nhau mà đổi chỗ sẽ tạo được chuỗi. */
    private static int[] timNuocHopLe(String bang) {
        Bang b = Bang.tu(bang, new MayNgauNhien(1L));
        for (int i = 0; i < LuatGame.SO_O; i++) {
            int cot = i % LuatGame.CANH;
            if (cot + 1 < LuatGame.CANH && b.doiChoHopLe(i, i + 1)) {
                return new int[] {i, i + 1};
            }
            if (i + LuatGame.CANH < LuatGame.SO_O && b.doiChoHopLe(i, i + LuatGame.CANH)) {
                return new int[] {i, i + LuatGame.CANH};
            }
        }
        throw new AssertionError("bàn mẫu không có nước đi nào — đổi hạt giống");
    }

    /** Một cặp ô kề nhau mà đổi chỗ KHÔNG tạo được chuỗi nào. */
    private static int[] timNuocKhongHopLe(String bang) {
        Bang b = Bang.tu(bang, new MayNgauNhien(1L));
        for (int i = 0; i < LuatGame.SO_O; i++) {
            int cot = i % LuatGame.CANH;
            if (cot + 1 < LuatGame.CANH && !b.doiChoHopLe(i, i + 1)) {
                return new int[] {i, i + 1};
            }
        }
        throw new AssertionError("bàn mẫu không có nước vô nghĩa nào");
    }
}
