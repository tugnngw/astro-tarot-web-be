package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.ai.BaoCaoNoiDungAiRequest;
import com.exe.astratarot.domain.entity.AiContentReport;
import com.exe.astratarot.domain.entity.TarotReading;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.AiContentReportRepository;
import com.exe.astratarot.repository.TarotReadingRepository;
import com.exe.astratarot.service.BaoCaoNoiDungAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Nhận báo cáo về lời giải do AI sinh.
 *
 * <p>Chính sách AI tạo sinh của CH Play buộc ứng dụng có nội dung do AI sinh
 * phải cho người dùng báo cáo ngay trong ứng dụng.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BaoCaoNoiDungAiServiceImpl implements BaoCaoNoiDungAiService {

    private final AiContentReportRepository aiContentReportRepository;
    private final TarotReadingRepository tarotReadingRepository;

    @Override
    @Transactional
    public void bao(User nguoiBao, BaoCaoNoiDungAiRequest yeuCau) {
        if (!AiContentReport.LY_DO_HOP_LE.contains(yeuCau.lyDo())) {
            throw new IllegalArgumentException("Lý do không hợp lệ");
        }

        TarotReading reading = tarotReadingRepository.findById(yeuCau.readingId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt trải bài"));

        // Chỉ báo được lượt trải bài của CHÍNH MÌNH.
        //
        // Không phải để bảo vệ nội dung — nó là của người ta — mà để chặn việc
        // dò id: cho báo lượt bất kỳ thì ai cũng thử được một dãy uuid và biết
        // được cái nào có thật.
        if (reading.getUser() == null || !reading.getUser().getId().equals(nguoiBao.getId())) {
            throw new AccessDeniedException("Chỉ báo được lượt trải bài của chính bạn");
        }

        // Bấm nhầm hai lần không được thành hai việc cho người xử lý. Trả về êm
        // chứ không ném lỗi: với người dùng thì bấm lần hai cũng là "đã báo
        // rồi", báo một lỗi đỏ ở đây chỉ gây hoang mang.
        if (aiContentReportRepository.existsByReporterUserIdAndReadingId(
                nguoiBao.getId(), reading.getId())) {
            log.debug("User {} báo lại lượt trải bài {} đã báo trước đó",
                    nguoiBao.getId(), reading.getId());
            return;
        }

        aiContentReportRepository.save(AiContentReport.builder()
                .reporterUser(nguoiBao)
                .reading(reading)
                // Chụp lại câu hỏi ngay lúc báo. Lời giải đầy đủ nằm ở bảng
                // khác và có thể bị sinh lại; ít nhất giữ được ngữ cảnh để
                // người xử lý biết chuyện gì đã xảy ra.
                .noiDungBiBao(reading.getMainQuestion())
                .lyDo(yeuCau.lyDo())
                .moTa(yeuCau.moTa())
                .build());

        log.info("Nhận báo cáo nội dung AI: reading={}, lyDo={}", reading.getId(), yeuCau.lyDo());
    }

    @Override
    public long demChoXuLy() {
        return aiContentReportRepository.countByTrangThai(AiContentReport.CHO_XU_LY);
    }

    /** Dùng cho kiểm thử và cho nơi chỉ có id. */
    public boolean daBao(UUID nguoiBaoId, UUID readingId) {
        return aiContentReportRepository.existsByReporterUserIdAndReadingId(nguoiBaoId, readingId);
    }
}
