package com.exe.astratarot.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lượt người dùng báo nội dung do AI sinh ra là không ổn.
 *
 * <p>Chính sách AI tạo sinh của CH Play buộc ứng dụng có nội dung do AI sinh
 * phải cho người dùng báo cáo ngay trong ứng dụng. Lời giải Tarot do Gemini
 * sinh thuộc diện này.
 *
 * <p>Không dùng lại {@link Report} vì bảng ấy để tố cáo NGƯỜI — cột
 * {@code reported_user_id} khai NOT NULL, mà nội dung AI thì không có người nào
 * để tố.
 */
@Entity
@Table(name = "ai_content_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiContentReport {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id")
    private User reporterUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reading_id")
    private TarotReading reading;

    /**
     * Chép lại đúng đoạn bị báo.
     *
     * <p>Lời giải có thể bị sinh lại hoặc xoá trước khi có người xem báo cáo,
     * và lúc ấy báo cáo thành vô dụng: biết có người kêu mà không biết kêu về
     * cái gì.
     */
    @Column(name = "noi_dung_bi_bao", columnDefinition = "TEXT")
    private String noiDungBiBao;

    @Column(name = "ly_do", nullable = false, length = 40)
    private String lyDo;

    @Column(name = "mo_ta", columnDefinition = "TEXT")
    private String moTa;

    @Builder.Default
    @Column(name = "trang_thai", nullable = false, length = 20)
    private String trangThai = CHO_XU_LY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nguoi_xu_ly_id")
    private User nguoiXuLy;

    @Column(name = "ghi_chu_xu_ly", columnDefinition = "TEXT")
    private String ghiChuXuLy;

    @Column(name = "xu_ly_luc")
    private Instant xuLyLuc;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static final String CHO_XU_LY = "CHO_XU_LY";

    /** Lý do hợp lệ, khớp ràng buộc CHECK của bảng. */
    public static final java.util.Set<String> LY_DO_HOP_LE =
            java.util.Set.of("SAI_LECH", "XUC_PHAM", "NGUY_HIEM", "KHAC");
}
