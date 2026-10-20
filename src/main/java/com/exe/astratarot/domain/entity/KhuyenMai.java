package com.exe.astratarot.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Một đợt khuyến mãi theo dịp lễ, do quản trị nhập.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "khuyen_mai")
public class KhuyenMai {

    public enum LoaiGiam {
        /** {@code giaTri} là phần trăm, 1..100. */
        PHAN_TRAM,
        /** {@code giaTri} là số đồng. */
        SO_TIEN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String ten;

    /** Hiện đúng một lần khi đăng nhập. Để trống thì đợt này không chúc gì. */
    @Column(name = "loi_chuc", columnDefinition = "TEXT")
    private String loiChuc;

    @Enumerated(EnumType.STRING)
    @Column(name = "loai_giam", nullable = false, length = 20)
    private LoaiGiam loaiGiam;

    @Column(name = "gia_tri", nullable = false)
    private Long giaTri;

    /**
     * Trần giảm cho loại {@link LoaiGiam#PHAN_TRAM}; {@code null} là không chặn.
     *
     * <p>Có cột này vì "giảm 50%" trên gói 239.000 là 119.500 — con số mà
     * người nhập không nhìn thấy lúc gõ số 50. Trần giá chặn đúng loại sai
     * sót ấy, và sai sót ấy mất tiền thật.
     */
    @Column(name = "giam_toi_da")
    private Long giamToiDa;

    @Column(name = "bat_dau", nullable = false)
    private Instant batDau;

    @Column(name = "ket_thuc", nullable = false)
    private Instant ketThuc;

    /**
     * Tắt tay mà không phải sửa ngày.
     *
     * <p>Cần tắt gấp một đợt đang chạy thì sửa {@code ketThuc} về quá khứ là
     * làm hỏng luôn lịch sử của đợt đó.
     */
    @Builder.Default
    @Column(name = "dang_bat", nullable = false)
    private Boolean dangBat = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Đang chạy tại thời điểm {@code luc} hay chưa. */
    public boolean dangChay(Instant luc) {
        return Boolean.TRUE.equals(dangBat)
                && batDau != null && ketThuc != null
                && !luc.isBefore(batDau) && luc.isBefore(ketThuc);
    }

    /**
     * Số tiền được giảm trên một giá gốc.
     *
     * <p>Không bao giờ giảm quá giá gốc: một đợt "giảm 500.000" gặp gói
     * 119.000 phải ra 0 đồng, không ra số âm. Số âm ở đây chảy thẳng vào
     * lệnh trừ ví và thành cộng tiền cho khách.
     */
    public long tienGiam(long giaGoc) {
        if (giaGoc <= 0 || giaTri == null) return 0L;

        long giam = loaiGiam == LoaiGiam.PHAN_TRAM
                ? giaGoc * giaTri / 100
                : giaTri;

        if (loaiGiam == LoaiGiam.PHAN_TRAM && giamToiDa != null) {
            giam = Math.min(giam, giamToiDa);
        }
        return Math.max(0L, Math.min(giam, giaGoc));
    }

    /** Giá sau khi giảm, không bao giờ âm. */
    public long giaSauGiam(long giaGoc) {
        return Math.max(0L, giaGoc - tienGiam(giaGoc));
    }
}
