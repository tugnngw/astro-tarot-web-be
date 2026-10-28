package com.exe.astratarot.domain.entity;

import com.exe.astratarot.game.NhanVat;
import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Một phiên chơi mini game "Giao Hội".
 *
 * <h2>Vì sao bàn chơi nằm ở bảng này</h2>
 *
 * Điểm đổi được ra lượt AI, và một lượt AI là một lượt gọi Gemini có hoá đơn
 * thật. Nên máy chủ phải tự tính điểm, và muốn tự tính thì nó phải giữ bàn.
 * Mỗi nước đi là một lượt gọi HTTP riêng, giữa hai lượt không còn gì trong bộ
 * nhớ, nên bàn phải ghi xuống đây.
 */
@Entity
@Table(name = "game_phien")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GamePhien {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "nhan_vat", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private NhanVat nhanVat;

    /** Bàn 7×7 dưới dạng 49 ký tự. */
    @Column(name = "bang", nullable = false, length = 64)
    private String bang;

    /** Trạng thái bộ sinh số ngẫu nhiên, để nước đi sau nạp lại đúng dãy cũ. */
    @Column(name = "hat_giong", nullable = false)
    private Long hatGiong;

    @Column(name = "diem", nullable = false)
    @Builder.Default
    private Integer diem = 0;

    @Column(name = "luot_con_lai", nullable = false)
    private Integer luotConLai;

    @Column(name = "nang_luong", nullable = false)
    @Builder.Default
    private Integer nangLuong = 0;

    /**
     * Phiên đã chốt thì không nhận nước đi nào nữa.
     *
     * <p>Chốt là lúc cộng điểm vào hạn mức ngày và cấp lượt thưởng, nên chốt
     * hai lần là cấp hai lần. Cờ này là thứ chặn chuyện ấy.
     */
    @Column(name = "da_chot", nullable = false)
    @Builder.Default
    private Boolean daChot = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Timestamp.from(Instant.now());
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Timestamp.from(Instant.now());
    }

    public boolean dangMo() {
        return !Boolean.TRUE.equals(daChot) && luotConLai != null && luotConLai > 0;
    }
}
