package com.exe.astratarot.domain.dto.game;

import com.exe.astratarot.game.BuocXoa;
import com.exe.astratarot.game.NhanVat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Các gói dữ liệu của mini game "Giao Hội".
 *
 * <h2>Vì sao không có trường "điểm" nào ở phía gửi lên</h2>
 *
 * Điểm đổi được ra lượt AI, nên nếu API nhận điểm thì ai mở Console cũng gửi
 * được điểm tối đa mỗi ngày mà không cần chơi. Mọi {@code Request} ở đây chỉ
 * mang Ô NÀO — máy chủ tự tính ra điểm. Không có đường nào khác để điểm đi vào.
 */
public final class GameDtos {

    private GameDtos() {}

    /** Chỉ số ô lớn nhất trên bàn 7×7. Để đây cho các ràng buộc bên dưới đọc được. */
    private static final int O_CUOI = 48;

    // ------------------------------------------------------------------
    // Gửi lên
    // ------------------------------------------------------------------

    public record BatDauRequest(
            @NotNull(message = "Chọn một nhân vật trước khi vào bàn")
            NhanVat nhanVat) {
    }

    /**
     * Hai ô muốn đổi chỗ, đánh số {@code hang * 7 + cot}.
     *
     * <p>Chặn chỉ số ngoài bàn ngay ở đây thay vì để bộ máy ném: ném từ bộ máy
     * ra thì thành 500, còn ở đây là 400 kèm câu nói rõ sai ở đâu.
     */
    public record DoiChoRequest(
            @NotNull @Min(0) @Max(O_CUOI) Integer o1,
            @NotNull @Min(0) @Max(O_CUOI) Integer o2) {
    }

    /** Ô mà kĩ năng nhắm vào. */
    public record KiNangRequest(
            @NotNull @Min(0) @Max(O_CUOI) Integer o) {
    }

    // ------------------------------------------------------------------
    // Trả về
    // ------------------------------------------------------------------

    /** Một nhân vật và kĩ năng của họ, để giao diện dựng màn chọn. */
    public record NhanVatResponse(
            String ma,
            String ten,
            String tenKiNang,
            String moTaKiNang,
            int nangLuongCan) {

        public static NhanVatResponse tu(NhanVat nv) {
            return new NhanVatResponse(nv.name(), nv.ten(), nv.tenKiNang(),
                    nv.moTaKiNang(), nv.nangLuongCan());
        }
    }

    /**
     * Trạng thái một phiên chơi.
     *
     * @param bang        bàn 7×7 dưới dạng 49 ký tự, mỗi ký tự một loại ô
     * @param nangLuongCan mốc nạp đủ để bấm kĩ năng, nhắc lại ở đây để giao
     *                     diện vẽ được thanh nạp mà không phải tra bảng nhân vật
     */
    public record PhienResponse(
            UUID id,
            NhanVatResponse nhanVat,
            String bang,
            int diem,
            int luotConLai,
            int nangLuong,
            int nangLuongCan,
            boolean daChot) {
    }

    /**
     * Phần thưởng của một phiên vừa chốt.
     *
     * @param diemTinhVaoQuyDoi  điểm của phiên này được tính vào quy đổi; nhỏ
     *                           hơn điểm phiên khi đã chạm trần điểm ngày
     * @param luotVuaNhan        lượt AI vừa được thưởng ngay lúc này
     * @param luotDaNhanHomNay   tổng lượt AI đã thưởng trong ngày
     * @param diemConDuocTinh    còn bao nhiêu điểm nữa trong ngày là được tính
     * @param luotConDuocNhan    còn được nhận thêm bao nhiêu lượt hôm nay
     */
    public record ThuongResponse(
            int diemTinhVaoQuyDoi,
            int luotVuaNhan,
            int diemDaTinhHomNay,
            int luotDaNhanHomNay,
            int diemConDuocTinh,
            int luotConDuocNhan) {
    }

    /**
     * Kết quả một nước đi.
     *
     * @param cacBuoc từng bậc dây chuyền theo thứ tự, để giao diện diễn lại
     * @param thuong  chỉ có khi nước đi này làm hết lượt và phiên vừa chốt
     */
    public record NuocDiResponse(
            List<BuocXoa> cacBuoc,
            int diemThem,
            boolean daDaoBan,
            PhienResponse phien,
            ThuongResponse thuong) {
    }

    /**
     * Luật và hạn mức — giao diện hiện nguyên văn, không tự nhẩm lại.
     *
     * <p>Để giao diện tự nhân chia các con số này là mở đường cho hai bên lệch
     * nhau: người chơi đọc "còn 2 lượt" rồi chơi xong không thấy lượt nào.
     */
    public record TrangThaiResponse(
            int canhBan,
            int luotMoiPhien,
            int diemMoiLuot,
            int tranDiemNgay,
            int tranLuotNgay,
            int diemDaTinhHomNay,
            int luotDaNhanHomNay,
            int diemConDuocTinh,
            int luotConDuocNhan,
            List<NhanVatResponse> cacNhanVat,
            PhienResponse phienDangMo) {
    }
}
