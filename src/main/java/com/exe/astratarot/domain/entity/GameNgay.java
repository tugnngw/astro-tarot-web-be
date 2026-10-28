package com.exe.astratarot.domain.entity;

import com.exe.astratarot.game.LuatGame;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Điểm đã quy đổi và lượt AI đã thưởng của một người trong một ngày.
 *
 * <h2>Vì sao tính theo ngày chứ không cộng dồn thành một kho</h2>
 *
 * Lượt AI thường cũng tính theo ngày ({@link AiUsageDaily}), nên lượt thưởng
 * theo ngày là cùng một đơn vị — không phải dựng thêm khái niệm số dư. Nó cũng
 * tự chặn chuyện dồn: chơi cả tuần rồi tiêu một lúc ba mươi lượt thì hoá đơn
 * Gemini dồn vào một ngày, mà trần ngày lại không thấy gì cả.
 */
@Entity
@Table(name = "game_ngay")
@IdClass(GameNgayId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameNgay {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Column(name = "ngay")
    private LocalDate ngay;

    /**
     * Điểm ĐÃ TÍNH vào quy đổi hôm nay, không phải tổng điểm đã chơi.
     *
     * <p>Chơi quá trần vẫn chơi được — chỉ là phần vượt không cộng vào đây.
     * Giữ cho người ta chơi vì vui chứ không phải cày.
     */
    @Column(name = "diem_da_tinh", nullable = false)
    @Builder.Default
    private Integer diemDaTinh = 0;

    @Column(name = "luot_da_nhan", nullable = false)
    @Builder.Default
    private Integer luotDaNhan = 0;

    /** Còn bao nhiêu điểm nữa trong ngày là được tính vào quy đổi. */
    public int diemConDuocTinh() {
        return Math.max(0, LuatGame.TRAN_DIEM_NGAY - diemDaTinh);
    }

    /** Còn được nhận thêm bao nhiêu lượt AI thưởng hôm nay. */
    public int luotConDuocNhan() {
        return Math.max(0, LuatGame.TRAN_LUOT_NGAY - luotDaNhan);
    }

    /**
     * Cộng điểm của một phiên vừa chốt, trả về số lượt AI vừa được thưởng.
     *
     * <h2>Vì sao tính lượt từ TỔNG điểm trong ngày</h2>
     *
     * Cách còn lại là mỗi phiên tự chia điểm của mình cho 1000 rồi làm tròn
     * xuống. Khi ấy ba phiên 400 điểm — tổng 1200 — cho ra không lượt nào, còn
     * một phiên 1200 điểm cho một lượt. Cùng công sức mà khác kết quả, và người
     * chơi sẽ học được là phải dồn vào một phiên dài, tức là bị luật ép chơi
     * theo một kiểu chứ không phải chọn.
     */
    public KetQuaCong congDiemVaThuong(int diemPhien) {
        int diemTinh = Math.min(Math.max(0, diemPhien), diemConDuocTinh());
        diemDaTinh += diemTinh;

        int luotDangLe = Math.min(LuatGame.TRAN_LUOT_NGAY,
                diemDaTinh / LuatGame.DIEM_MOI_LUOT);
        int luotMoi = Math.max(0, luotDangLe - luotDaNhan);
        luotDaNhan += luotMoi;
        return new KetQuaCong(diemTinh, luotMoi);
    }

    /**
     * Phần của một phiên thật sự được tính, và lượt vừa thưởng.
     *
     * <p>Trả cả hai vì phép cắt theo trần chỉ đúng một lần: tầng dịch vụ tự
     * nhẩm lại {@code min(điểm phiên, còn được tính)} là hai chỗ cùng giữ một
     * luật, và sửa trần ở đây thì chỗ kia lặng lẽ sai.
     */
    public record KetQuaCong(int diemTinh, int luotMoi) {
    }
}
