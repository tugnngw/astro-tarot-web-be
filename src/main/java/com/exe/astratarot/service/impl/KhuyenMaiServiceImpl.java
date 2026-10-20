package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.khuyenmai.GiaSauGiam;
import com.exe.astratarot.domain.dto.khuyenmai.KhuyenMaiResponse;
import com.exe.astratarot.domain.dto.khuyenmai.LuuKhuyenMaiRequest;
import com.exe.astratarot.domain.entity.KhuyenMai;
import com.exe.astratarot.domain.entity.KhuyenMaiDaChuc;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.KhuyenMaiDaChucRepository;
import com.exe.astratarot.repository.KhuyenMaiRepository;
import com.exe.astratarot.service.KhuyenMaiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Khuyến mãi theo dịp lễ.
 *
 * <h2>Vì sao chọn đợt ở đúng một chỗ</h2>
 *
 * Quản trị hoàn toàn có thể nhập hai đợt chồng lịch — Tết dương và Tết âm
 * nằm sát nhau là chuyện thường. Nếu mỗi nơi gọi tự chọn "đợt đầu tiên tìm
 * thấy" thì trang gói AI hiện một giá, lúc trừ tiền lại ra giá khác, và
 * khách thấy mình bị tính nhiều hơn số vừa nhìn.
 *
 * <p>Nên quy tắc nằm gọn trong {@link #dotTotNhat(long)}: chọn đợt giảm
 * NHIỀU TIỀN NHẤT trên đúng giá đang xét. So theo số tiền chứ không theo
 * phần trăm, vì "giảm 30%" và "giảm 50.000" không so trực tiếp được — cái
 * nào lợi hơn còn tuỳ giá gói.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KhuyenMaiServiceImpl implements KhuyenMaiService {

    /** Kênh phát cho mọi người đang mở app/web, kể cả chưa đăng nhập. */
    public static final String KENH_KHUYEN_MAI = "/topic/khuyen-mai";

    private final KhuyenMaiRepository khuyenMaiRepository;
    private final KhuyenMaiDaChucRepository daChucRepository;
    private final com.exe.astratarot.repository.SubscriptionPlanRepository subscriptionPlanRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(readOnly = true)
    public Optional<KhuyenMai> dotTotNhat(long giaGoc) {
        if (giaGoc <= 0) return Optional.empty();
        return khuyenMaiRepository.dangChay(Instant.now()).stream()
                .max(Comparator.comparingLong(k -> k.tienGiam(giaGoc)))
                .filter(k -> k.tienGiam(giaGoc) > 0);
    }

    @Override
    @Transactional(readOnly = true)
    public GiaSauGiam apDung(long giaGoc) {
        return dotTotNhat(giaGoc)
                .map(k -> new GiaSauGiam(
                        giaGoc, k.tienGiam(giaGoc), k.giaSauGiam(giaGoc), k.getTen()))
                .orElseGet(() -> GiaSauGiam.khongGiam(giaGoc));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KhuyenMaiResponse> dangChay() {
        Instant luc = Instant.now();
        return khuyenMaiRepository.dangChay(luc).stream()
                .map(k -> KhuyenMaiResponse.tu(k, luc))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Map<String, GiaSauGiam> giaMoiGoi() {
        java.util.Map<String, GiaSauGiam> ketQua = new java.util.LinkedHashMap<>();
        for (var goi : subscriptionPlanRepository.findByIsActiveTrue()) {
            long gia = goi.getPrice() != null ? goi.getPrice() : 0L;
            if (gia <= 0) continue;
            ketQua.put(goi.getId().toString(), apDung(gia));
        }
        return ketQua;
    }

    @Override
    @Transactional
    public Optional<KhuyenMaiResponse> loiChucChuaXem(UUID userId) {
        if (userId == null) return Optional.empty();

        Instant luc = Instant.now();
        for (KhuyenMai k : khuyenMaiRepository.dangChay(luc)) {
            if (k.getLoiChuc() == null || k.getLoiChuc().isBlank()) continue;
            if (daChucRepository.existsByKhuyenMaiIdAndUserId(k.getId(), userId)) continue;

            // Đánh dấu NGAY trong cùng lượt gọi, không đợi giao diện báo lại
            // "tôi đã hiện rồi". Giao diện có thể đóng app giữa chừng, và lần
            // sau lời chúc lại hiện — người dùng thấy lời chúc Tết ba lần.
            try {
                daChucRepository.save(KhuyenMaiDaChuc.builder()
                        .khuyenMaiId(k.getId())
                        .userId(userId)
                        .xemLuc(luc)
                        .build());
            } catch (DataIntegrityViolationException e) {
                // Hai thiết bị gọi gần như cùng lúc: cả hai đọc thấy "chưa
                // xem", nhưng khoá chính ghép chỉ cho một hàng vào. Bên thua
                // coi như đã xem rồi — đúng ý "chỉ một lần".
                continue;
            }
            return Optional.of(KhuyenMaiResponse.tu(k, luc));
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KhuyenMaiResponse> tatCa() {
        Instant luc = Instant.now();
        return khuyenMaiRepository.findAllByOrderByBatDauDesc().stream()
                .map(k -> KhuyenMaiResponse.tu(k, luc))
                .toList();
    }

    @Override
    @Transactional
    public KhuyenMaiResponse tao(LuuKhuyenMaiRequest request) {
        KhuyenMai k = new KhuyenMai();
        apVao(k, request);
        KhuyenMai daLuu = khuyenMaiRepository.save(k);
        phatThayDoi();
        return KhuyenMaiResponse.tu(daLuu, Instant.now());
    }

    @Override
    @Transactional
    public KhuyenMaiResponse sua(UUID id, LuuKhuyenMaiRequest request) {
        KhuyenMai k = khuyenMaiRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đợt khuyến mãi"));
        apVao(k, request);
        KhuyenMai daLuu = khuyenMaiRepository.save(k);
        phatThayDoi();
        return KhuyenMaiResponse.tu(daLuu, Instant.now());
    }

    @Override
    @Transactional
    public void xoa(UUID id) {
        if (!khuyenMaiRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy đợt khuyến mãi");
        }
        khuyenMaiRepository.deleteById(id);
        phatThayDoi();
    }

    private void apVao(KhuyenMai k, LuuKhuyenMaiRequest r) {
        KhuyenMai.LoaiGiam loai = KhuyenMai.LoaiGiam.valueOf(r.loaiGiam());

        if (!r.ketThuc().isAfter(r.batDau())) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu.");
        }
        // Kiểm lại ở đây dù DTO đã có @Pattern và CSDL đã có CHECK. Gõ nhầm
        // 150% thì khách được TRẢ TIỀN để mua gói, và tiền ấy không đòi lại
        // được — chỗ này đáng kiểm ba lần.
        if (loai == KhuyenMai.LoaiGiam.PHAN_TRAM && (r.giaTri() < 1 || r.giaTri() > 100)) {
            throw new IllegalArgumentException("Giảm theo phần trăm phải nằm trong 1–100.");
        }

        k.setTen(r.ten().trim());
        k.setLoiChuc(r.loiChuc() == null || r.loiChuc().isBlank() ? null : r.loiChuc().trim());
        k.setLoaiGiam(loai);
        k.setGiaTri(r.giaTri());
        k.setGiamToiDa(loai == KhuyenMai.LoaiGiam.PHAN_TRAM ? r.giamToiDa() : null);
        k.setBatDau(r.batDau());
        k.setKetThuc(r.ketThuc());
        k.setDangBat(r.dangBat() == null || r.dangBat());
    }

    /**
     * Báo cho mọi máy đang mở rằng danh sách đợt đã đổi.
     *
     * <p>Không gửi kèm nội dung: người nhận tự gọi lại {@code /dang-chay}.
     * Gửi kèm thì phải tự hỏi "người này có quyền thấy đợt đang tắt không",
     * và một lần trả lời sai là lộ đợt chưa công bố cho toàn bộ khách.
     *
     * <p>Hỏng thì chỉ ghi log: việc chính đã lưu xong, và lần tải trang sau
     * người dùng vẫn thấy đúng.
     */
    private void phatThayDoi() {
        try {
            messagingTemplate.convertAndSend(KENH_KHUYEN_MAI, "DOI");
        } catch (Exception e) {
            log.warn("Không phát được thay đổi khuyến mãi: {}", e.getMessage());
        }
    }
}
