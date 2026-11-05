package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.feedback.AdminFeedbackItemResponse;
import com.exe.astratarot.domain.dto.feedback.FeedbackStatsResponse;
import com.exe.astratarot.domain.dto.feedback.SubmitFeedbackRequest;
import com.exe.astratarot.domain.dto.feedback.UserFeedbackResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserFeedback;
import com.exe.astratarot.repository.UserFeedbackRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final Set<String> SOURCES = Set.of(
            "TAROT_AI", "BOOKING", "GENERAL", "LANDING");

    private final UserFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public UserFeedbackResponse submit(UUID userIdOrNull, SubmitFeedbackRequest request) {
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException("Số sao đánh giá phải từ 1 đến 5");
        }

        String source = request.source() != null ? request.source().trim().toUpperCase() : "GENERAL";
        if (!SOURCES.contains(source)) {
            throw new IllegalArgumentException("Nguồn phản hồi không hợp lệ: " + request.source());
        }

        // Quy tắc tương thích ngược NPS: nếu client không truyền, suy ra nps = rating * 2
        // Thang sao 1–5 ánh xạ tương ứng 2, 4, 6, 8, 10 trên thang NPS (0–10)
        int effectiveNps = request.nps() != null
                ? Math.max(0, Math.min(10, request.nps()))
                : request.rating() * 2;

        // Mỗi lần gửi là một bản ghi riêng, kể cả của người đã đăng nhập.
        //
        // Trước đây tài khoản đã đăng nhập chỉ giữ ĐÚNG MỘT bản góp ý: gửi lần
        // hai thì tìm bản cũ ra rồi ghi đè lên. Nghĩa là góp ý sau khi trải
        // bài tháng trước và góp ý sau buổi xem hôm nay không cùng tồn tại
        // được — cái sau xoá mất cái trước, và không ai biết là đã mất.
        //
        // Một người góp ý nhiều lần là chuyện bình thường và đáng giữ: mỗi
        // lần ứng với một lần dùng khác nhau, ở một thời điểm khác nhau. Giữ
        // cả thì mới thấy được cảm nhận đổi theo thời gian.
        //
        // Bỏ luôn khối `synchronized` khoá theo userId trước đây: nó dựng để
        // chặn hai yêu cầu cùng lúc tạo ra hai bản trùng, mà giờ hai bản là
        // điều mong muốn. Nó cũng chưa bao giờ làm được việc ấy cho tử tế —
        // khoá trên chuỗi `intern()` chỉ có tác dụng trong một tiến trình,
        // nên khi chạy nhiều bản sao thì vô nghĩa.
        UserFeedback saved = feedbackRepository.save(UserFeedback.builder()
                .user(userIdOrNull == null ? null : userRepository.findById(userIdOrNull).orElse(null))
                .source(source)
                .nps(effectiveNps)
                .rating(request.rating())
                .comment(blankToNull(request.comment()))
                .utmSource(blankToNull(request.utmSource()))
                .utmMedium(blankToNull(request.utmMedium()))
                .utmCampaign(blankToNull(request.utmCampaign()))
                .updatedAt(Instant.now())
                .build());

        return toUserFeedbackResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSubmitted(UUID userId) {
        return userId != null && feedbackRepository.countByUserId(userId) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserFeedbackResponse> getMyFeedback(UUID userId) {
        if (userId == null) return Optional.empty();
        return feedbackRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(this::toUserFeedbackResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return feedbackRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackStatsResponse getStats() {
        long totalCount = feedbackRepository.count();

        Double rawAvgRating = feedbackRepository.getAverageRating();
        double averageRating = 0.0;
        if (rawAvgRating != null) {
            averageRating = BigDecimal.valueOf(rawAvgRating)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        Double rawAvgNps = feedbackRepository.getAverageNps();
        Double averageNps = null;
        if (rawAvgNps != null) {
            averageNps = BigDecimal.valueOf(rawAvgNps)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // Đảm bảo luôn có đủ 5 mức sao từ 1 đến 5 (kể cả khi count = 0)
        Map<Integer, Long> ratingDistribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            ratingDistribution.put(i, 0L);
        }
        for (Object[] row : feedbackRepository.countByRatingGroup()) {
            if (row != null && row.length >= 2 && row[0] instanceof Number r && row[1] instanceof Number c) {
                int star = r.intValue();
                if (star >= 1 && star <= 5) {
                    ratingDistribution.put(star, c.longValue());
                }
            }
        }

        // Phân bổ theo nguồn
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (String s : SOURCES) {
            bySource.put(s, 0L);
        }
        for (Object[] row : feedbackRepository.countBySourceGroup()) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] instanceof Number c) {
                bySource.put(row[0].toString(), c.longValue());
            }
        }

        return new FeedbackStatsResponse(
                totalCount,
                averageRating,
                ratingDistribution,
                averageNps,
                bySource
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminFeedbackItemResponse> listAdminFeedback(
            Integer rating, String source, String keyword, Pageable pageable) {
        String cleanSource = (source != null && !source.isBlank()) ? source.trim().toUpperCase() : null;
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        // Dựng sẵn chuỗi so khớp ở đây; truy vấn không được tự ghép bằng
        // CONCAT, xem chú thích ở findAdminFeedback.
        String pattern = cleanKeyword == null
                ? null
                : "%" + cleanKeyword.toLowerCase() + "%";

        return feedbackRepository.findAdminFeedback(rating, cleanSource, pattern, pageable)
                .map(this::toAdminFeedbackItemResponse);
    }

    private UserFeedbackResponse toUserFeedbackResponse(UserFeedback f) {
        return new UserFeedbackResponse(
                f.getId(),
                f.getRating(),
                f.getNps(),
                f.getComment(),
                f.getSource(),
                f.getUtmSource(),
                f.getUtmMedium(),
                f.getUtmCampaign(),
                f.getCreatedAt(),
                f.getUpdatedAt() != null ? f.getUpdatedAt() : f.getCreatedAt()
        );
    }

    private AdminFeedbackItemResponse toAdminFeedbackItemResponse(UserFeedback f) {
        User u = f.getUser();
        boolean isAnon = (u == null);
        return new AdminFeedbackItemResponse(
                f.getId(),
                isAnon ? null : u.getId(),
                isAnon ? null : u.getUsername(),
                isAnon ? null : u.getFullName(),
                isAnon ? null : u.getEmail(),
                isAnon,
                f.getRating(),
                f.getNps(),
                f.getComment(),
                f.getSource(),
                f.getUtmSource(),
                f.getUtmMedium(),
                f.getUtmCampaign(),
                f.getCreatedAt(),
                f.getUpdatedAt() != null ? f.getUpdatedAt() : f.getCreatedAt()
        );
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
