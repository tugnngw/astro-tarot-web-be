package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tự động chốt những buổi xem đã qua mà chưa ai bấm "hoàn tất".
 *
 * <p>Vì sao cần: tiền chỉ chuyển từ "đang giữ" sang "rút được" khi Reader tự
 * đánh dấu buổi xem hoàn tất. Reader quên bấm — chuyện xảy ra thường xuyên với
 * bất kỳ thao tác thủ công nào — thì tiền nằm lại ở phần đang giữ vĩnh viễn.
 * Khách đã trả, buổi xem đã diễn ra, mà không ai được đồng nào. Đó không phải
 * là trạng thái mà hệ thống được phép rơi vào rồi nằm im.
 *
 * <p>Lớp này CỐ Ý không có {@code @Transactional}: nó chỉ chọn việc rồi giao
 * từng việc một cho {@link BookingSettlementService}, mỗi việc một giao dịch
 * riêng. Bọc cả vòng lặp trong một giao dịch thì một buổi hỏng sẽ đánh dấu
 * rollback-only và kéo đổ cả lượt chạy — kể cả những buổi đã chốt đúng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingSettlementJob {

    /**
     * Số giờ chờ sau khi buổi xem kết thúc trước khi tự chốt.
     *
     * <p>24 tiếng: đủ dài để khách ngủ một đêm rồi vẫn kịp khiếu nại, đủ ngắn
     * để Reader không phải chờ tiền cả tuần.
     */
    public static final long HAN_CHO_GIO = 24;

    /** Trần mỗi lượt chạy, để một đợt tồn đọng lớn không giữ database quá lâu. */
    private static final int TOI_DA_MOI_LUOT = 200;

    private final BookingRepository bookingRepository;
    private final BookingSettlementService settlementService;

    /**
     * Chạy mỗi giờ.
     *
     * <p>Không cần dày hơn: mốc so sánh là 24 tiếng, nên sai số một giờ không
     * đổi được gì cho ai. Chạy dày chỉ tốn lượt đánh thức database.
     *
     * <p>{@code initialDelay} để lượt đầu không tranh tài nguyên với lúc ứng
     * dụng đang khởi động — trên gói free của Render đó là quãng chật vật nhất.
     */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    public void chotCacBuoiXemDaQua() {
        Instant hanChot = Instant.now().minus(Duration.ofHours(HAN_CHO_GIO));
        List<UUID> canChot = bookingRepository.findIdsDueForSettlement(
                BookingStatus.CONFIRMED, PaymentStatus.PAID, hanChot,
                PageRequest.of(0, TOI_DA_MOI_LUOT));

        if (canChot.isEmpty()) return;
        log.info("Tự chốt {} buổi xem đã quá hạn {} tiếng", canChot.size(), HAN_CHO_GIO);

        int xong = 0;
        for (UUID id : canChot) {
            try {
                if (settlementService.chotMotBuoi(id)) xong++;
            } catch (RuntimeException e) {
                // Một buổi hỏng không được kéo theo cả lượt. Ghi log rồi đi
                // tiếp; lượt sau sẽ gặp lại đúng buổi này.
                log.error("Không chốt được booking {}: {}", id, e.getMessage());
            }
        }
        log.info("Đã chốt {}/{} buổi", xong, canChot.size());
    }
}
