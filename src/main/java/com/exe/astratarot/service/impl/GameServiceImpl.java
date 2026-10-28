package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.game.GameDtos.NhanVatResponse;
import com.exe.astratarot.domain.dto.game.GameDtos.NuocDiResponse;
import com.exe.astratarot.domain.dto.game.GameDtos.PhienResponse;
import com.exe.astratarot.domain.dto.game.GameDtos.ThuongResponse;
import com.exe.astratarot.domain.dto.game.GameDtos.TrangThaiResponse;
import com.exe.astratarot.domain.entity.GameNgay;
import com.exe.astratarot.domain.entity.GameNgayId;
import com.exe.astratarot.domain.entity.GamePhien;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.game.Bang;
import com.exe.astratarot.game.KetQuaNuocDi;
import com.exe.astratarot.game.LuatGame;
import com.exe.astratarot.game.MayNgauNhien;
import com.exe.astratarot.game.NhanVat;
import com.exe.astratarot.repository.GameNgayRepository;
import com.exe.astratarot.repository.GamePhienRepository;
import com.exe.astratarot.service.GameService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mini game "Giao Hội".
 *
 * <h2>Vì sao máy chủ giữ bàn chơi</h2>
 *
 * Điểm đổi được ra lượt AI, mà một lượt AI là một lượt gọi Gemini có hoá đơn
 * thật. Nếu trình duyệt tự tính điểm rồi gửi lên thì ai mở Console cũng lấy
 * được trần điểm mỗi ngày mà không cần chơi — trần chỉ chặn mức thiệt hại, chứ
 * không chặn được chuyện ấy.
 *
 * <p>Nên bàn nằm ở đây. Trình duyệt chỉ gửi "đổi ô này với ô kia", không có
 * trường nào mang điểm.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final GamePhienRepository phienRepo;
    private final GameNgayRepository ngayRepo;

    private static final List<NhanVatResponse> CAC_NHAN_VAT =
            Arrays.stream(NhanVat.values()).map(NhanVatResponse::tu).toList();

    // ------------------------------------------------------------------
    // Trạng thái
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public TrangThaiResponse trangThai(UUID userId) {
        GameNgay ngay = ngayHomNay(userId);
        GamePhien phien = phienRepo.timPhienChuaChot(userId).stream()
                .filter(GamePhien::dangMo)
                .findFirst()
                .orElse(null);
        return dungTrangThai(ngay, phien);
    }

    private TrangThaiResponse dungTrangThai(GameNgay ngay, GamePhien phien) {
        return new TrangThaiResponse(
                LuatGame.CANH,
                LuatGame.LUOT_MOI_PHIEN,
                LuatGame.DIEM_MOI_LUOT,
                LuatGame.TRAN_DIEM_NGAY,
                LuatGame.TRAN_LUOT_NGAY,
                ngay.getDiemDaTinh(),
                ngay.getLuotDaNhan(),
                ngay.diemConDuocTinh(),
                ngay.luotConDuocNhan(),
                CAC_NHAN_VAT,
                phien == null ? null : dungPhien(phien));
    }

    private static PhienResponse dungPhien(GamePhien p) {
        return new PhienResponse(
                p.getId(),
                NhanVatResponse.tu(p.getNhanVat()),
                p.getBang(),
                p.getDiem(),
                p.getLuotConLai(),
                p.getNangLuong(),
                p.getNhanVat().nangLuongCan(),
                Boolean.TRUE.equals(p.getDaChot()));
    }

    // ------------------------------------------------------------------
    // Mở phiên
    // ------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <h2>Vì sao phiên cũ được chốt KÈM thưởng</h2>
     *
     * Cách còn lại là bỏ trắng phiên cũ. Khi ấy người đang chơi dở mà bấm "Chơi
     * lại" — hoặc chỉ là mở lại trang trên điện thoại — thì mất sạch điểm đã
     * kiếm, và không có gì báo trước. Thưởng cho phiên cũ không mở ra đường ăn
     * gian nào: điểm vẫn phải kiếm bằng nước đi thật, và trần ngày vẫn chặn
     * tổng.
     */
    @Override
    @Transactional
    public TrangThaiResponse batDau(UUID userId, NhanVat nhanVat) {
        GameNgay ngay = ngayHomNay(userId);
        List<GamePhien> phienCu = phienRepo.timPhienChuaChot(userId);
        for (GamePhien cu : phienCu) {
            chot(cu, ngay);
            phienRepo.save(cu);
        }
        // Chỉ ghi bảng ngày khi có phiên cũ vừa chốt. Ghi vô điều kiện thì mỗi
        // người bấm "Chơi" lại thêm một hàng toàn số 0.
        if (!phienCu.isEmpty()) {
            ngayRepo.save(ngay);
        }

        long hatGiong = ThreadLocalRandom.current().nextLong();
        Bang bang = Bang.moi(new MayNgauNhien(hatGiong));

        GamePhien phien = phienRepo.save(GamePhien.builder()
                .userId(userId)
                .nhanVat(nhanVat)
                .bang(bang.chuoi())
                .hatGiong(bang.may().hatGiong())
                .diem(0)
                .luotConLai(LuatGame.LUOT_MOI_PHIEN)
                .nangLuong(0)
                .daChot(false)
                .build());

        return dungTrangThai(ngay, phien);
    }

    // ------------------------------------------------------------------
    // Nước đi
    // ------------------------------------------------------------------

    @Override
    @Transactional
    public NuocDiResponse doiCho(UUID userId, UUID phienId, int o1, int o2) {
        GamePhien phien = phienDangMo(userId, phienId);
        Bang bang = Bang.tu(phien.getBang(), new MayNgauNhien(phien.getHatGiong()));

        // Ném từ đây là nước đi KHÔNG hợp lệ, và nó không tốn lượt nào: bàn
        // chưa đổi, hạt giống chưa tiêu, giao dịch bị cuộn lại.
        KetQuaNuocDi kq = bang.doiCho(o1, o2);

        phien.setLuotConLai(phien.getLuotConLai() - 1);
        return apDung(phien, bang, kq);
    }

    @Override
    @Transactional
    public NuocDiResponse dungKiNang(UUID userId, UUID phienId, int o) {
        GamePhien phien = phienDangMo(userId, phienId);
        int can = phien.getNhanVat().nangLuongCan();
        if (phien.getNangLuong() < can) {
            throw new Bang.NuocDiKhongHopLe(
                    phien.getNhanVat().tenKiNang() + " chưa nạp đủ ("
                            + phien.getNangLuong() + "/" + can + ")");
        }

        Bang bang = Bang.tu(phien.getBang(), new MayNgauNhien(phien.getHatGiong()));
        KetQuaNuocDi kq = bang.dungKiNang(phien.getNhanVat(), o);

        // Tiêu hết năng lượng TRƯỚC khi cộng phần nạp từ chính phát này, nên ô
        // do kĩ năng xoá vẫn nạp lại một ít — y như mọi ô khác.
        phien.setNangLuong(0);
        return apDung(phien, bang, kq);
    }

    /** Ghi kết quả một nước đi vào phiên, chốt nếu đã hết lượt. */
    private NuocDiResponse apDung(GamePhien phien, Bang bang, KetQuaNuocDi kq) {
        phien.setBang(bang.chuoi());
        phien.setHatGiong(bang.may().hatGiong());
        phien.setDiem(phien.getDiem() + kq.diemThem());

        int can = phien.getNhanVat().nangLuongCan();
        int nap = phien.getNangLuong() + kq.soOXoa() * LuatGame.NANG_LUONG_MOI_O;
        phien.setNangLuong(Math.min(can, nap));

        // Chỉ đụng tới bảng ngày khi phiên thật sự chốt — nước đi giữa phiên
        // không cần biết hạn mức ngày, nên không phải thêm một lượt truy vấn.
        ThuongResponse thuong = null;
        if (phien.getLuotConLai() <= 0) {
            GameNgay ngay = ngayHomNay(phien.getUserId());
            thuong = chot(phien, ngay);
            ngayRepo.save(ngay);
        }
        phienRepo.save(phien);

        return new NuocDiResponse(
                kq.cacBuoc(), kq.diemThem(), kq.daDaoBan(), dungPhien(phien), thuong);
    }

    private GamePhien phienDangMo(UUID userId, UUID phienId) {
        GamePhien phien = phienRepo.timVaKhoa(phienId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phiên chơi này"));
        if (!phien.dangMo()) {
            throw new Bang.NuocDiKhongHopLe("Phiên này đã kết thúc, mở phiên mới nhé");
        }
        return phien;
    }

    // ------------------------------------------------------------------
    // Chốt và thưởng
    // ------------------------------------------------------------------

    /**
     * Chốt một phiên: cộng điểm vào hạn mức ngày và cấp lượt AI thưởng.
     *
     * <p>Đặt {@code daChot} trước khi cộng, và không bao giờ bỏ cờ ấy ra: chốt
     * hai lần là cấp thưởng hai lần cho cùng một phiên.
     */
    private ThuongResponse chot(GamePhien phien, GameNgay ngay) {
        if (Boolean.TRUE.equals(phien.getDaChot())) {
            return null;
        }
        phien.setDaChot(true);

        GameNgay.KetQuaCong cong = ngay.congDiemVaThuong(phien.getDiem());

        if (cong.luotMoi() > 0) {
            log.info("Game Giao Hội: người {} được thưởng {} lượt AI (điểm phiên {})",
                    phien.getUserId(), cong.luotMoi(), phien.getDiem());
        }
        return new ThuongResponse(
                cong.diemTinh(),
                cong.luotMoi(),
                ngay.getDiemDaTinh(),
                ngay.getLuotDaNhan(),
                ngay.diemConDuocTinh(),
                ngay.luotConDuocNhan());
    }

    /**
     * Bản ghi ngày hôm nay, tạo mới nếu chưa có.
     *
     * <p>Chưa lưu xuống — bên gọi lưu, để nước đi không ghi gì khi phiên chưa
     * chốt. Ghi một hàng toàn số 0 cho mỗi người mở trang game là rác.
     */
    private GameNgay ngayHomNay(UUID userId) {
        LocalDate homNay = LocalDate.now(VN_ZONE);
        return ngayRepo.findById(new GameNgayId(userId, homNay))
                .orElseGet(() -> GameNgay.builder()
                        .userId(userId)
                        .ngay(homNay)
                        .diemDaTinh(0)
                        .luotDaNhan(0)
                        .build());
    }
}
