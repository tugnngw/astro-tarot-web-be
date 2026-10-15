package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.GooglePlayPurchase;
import com.exe.astratarot.domain.entity.SubscriptionPlan;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserPlanPurchase;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.GooglePlayPurchaseRepository;
import com.exe.astratarot.repository.SubscriptionPlanRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.GooglePlayBillingService;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.ProductPurchase;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

/**
 * Đổi một lượt mua trên Google Play lấy gói AI.
 *
 * <p>Máy khách chỉ gửi lên {@code purchaseToken} và {@code productId}. CHỈ
 * Google mới nói được token ấy có thật không, đã thanh toán xong chưa, và
 * thuộc sản phẩm nào — nên mọi thứ đều hỏi lại Google, không tin một chữ nào
 * từ máy khách. Tin máy khách thì ai cũng tự cấp gói cho mình bằng một lần
 * gọi API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePlayBillingServiceImpl implements GooglePlayBillingService {

    private final SubscriptionPlanRepository planRepository;
    private final UserRepository userRepository;
    private final GooglePlayPurchaseRepository googlePlayPurchaseRepository;
    private final com.exe.astratarot.service.SubscriptionService subscriptionService;

    /** Tên gói ứng dụng. Phải khớp applicationId của bản dựng Android. */
    @Value("${google.play.package-name:com.astrotarot.astrotarot_mobile}")
    private String packageName;

    /**
     * Nội dung JSON của tài khoản dịch vụ, đọc từ biến môi trường.
     *
     * <p>Để TRỐNG thì tính năng tự tắt chứ không làm sập ứng dụng lúc khởi
     * động: máy của lập trình viên và môi trường thử không có khoá này, mà
     * mọi tính năng khác vẫn phải chạy được.
     */
    @Value("${GOOGLE_PLAY_SERVICE_ACCOUNT_JSON:}")
    private String serviceAccountJson;

    private volatile AndroidPublisher publisher;

    @Override
    public boolean daCauHinh() {
        return serviceAccountJson != null && !serviceAccountJson.isBlank();
    }

    /**
     * Xác minh rồi cấp gói.
     *
     * @return bản ghi gói vừa cấp, hoặc gói đã cấp trước đó nếu token này
     *         từng được đổi rồi — gọi lại nhiều lần cho ra cùng một kết quả.
     */
    @Override
    @Transactional
    public UUID doiLayGoi(UUID userId, String productId, String purchaseToken) {
        if (!daCauHinh()) {
            throw new IllegalStateException(
                    "Máy chủ chưa cấu hình khoá Google Play. Báo với quản trị viên.");
        }
        if (productId == null || productId.isBlank()
                || purchaseToken == null || purchaseToken.isBlank()) {
            throw new IllegalArgumentException("Thiếu mã sản phẩm hoặc mã lượt mua.");
        }

        // Đã đổi rồi thì trả lại kết quả cũ, KHÔNG cấp thêm gói.
        //
        // Nhánh này chạy thường xuyên chứ không phải trường hợp hiếm: app
        // phát lại những lượt mua chưa acknowledge mỗi lần mở lại.
        var daCo = googlePlayPurchaseRepository.findById(purchaseToken);
        if (daCo.isPresent()) {
            GooglePlayPurchase cu = daCo.get();
            if (!cu.getUserId().equals(userId)) {
                // Cùng một token mà người khác gửi lên: hoặc hai máy dùng
                // chung tài khoản Google, hoặc ai đó đang thử dùng lại biên
                // nhận của người khác. Không cấp, và ghi lại để còn soi.
                log.warn("purchaseToken {} đã thuộc user {} nhưng user {} gửi lại",
                        rutGon(purchaseToken), cu.getUserId(), userId);
                throw new IllegalArgumentException(
                        "Lượt mua này đã được dùng cho một tài khoản khác.");
            }
            return cu.getUserPurchaseId();
        }

        SubscriptionPlan plan = planRepository.findByGooglePlayProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không có gói nào khớp mã sản phẩm " + productId));

        ProductPurchase muaHang = hoiGoogle(productId, purchaseToken);

        // purchaseState: 0 = đã mua, 1 = đã huỷ, 2 = đang chờ.
        //
        // "Đang chờ" là thật trong đời sống Việt Nam: trả bằng chuyển khoản
        // hay thẻ cào thì Google giữ đơn ở trạng thái PENDING vài phút tới
        // vài ngày. Cấp gói lúc ấy là cấp cho một đơn có thể không bao giờ
        // trả tiền.
        Integer trangThai = muaHang.getPurchaseState();
        if (trangThai != null && trangThai != 0) {
            throw new IllegalArgumentException(trangThai == 2
                    ? "Google báo đơn này đang chờ thanh toán. Khi nào tiền về, mở lại app là gói được cấp."
                    : "Google báo lượt mua này không hợp lệ hoặc đã huỷ.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        UserPlanPurchase capGoi = subscriptionService.capGoiTuGooglePlay(user, plan);

        googlePlayPurchaseRepository.save(GooglePlayPurchase.builder()
                .purchaseToken(purchaseToken)
                .orderId(muaHang.getOrderId())
                .productId(productId)
                .userId(userId)
                .planId(plan.getId())
                .userPurchaseId(capGoi.getId())
                .build());

        // Báo cho Google là đã giao hàng.
        //
        // KHÔNG bỏ bước này: đơn không được acknowledge trong ba ngày sẽ bị
        // Google tự hoàn tiền, khách mất gói mà mình thì mất tiền.
        xacNhanVoiGoogle(productId, purchaseToken);

        log.info("Cấp gói {} cho user {} từ Google Play, đơn {}",
                plan.getName(), userId, muaHang.getOrderId());
        return capGoi.getId();
    }

    /**
     * Hỏi Google về một lượt mua.
     *
     * <p>Để {@code protected} làm chỗ nối cho test: phần đáng test ở lớp này
     * là luật — chặn đơn đang chờ, không đổi hai lần một token, ghi đúng giá
     * — chứ không phải việc gọi HTTP sang Google.
     */
    protected ProductPurchase hoiGoogle(String productId, String purchaseToken) {
        try {
            return publisher().purchases().products()
                    .get(packageName, productId, purchaseToken)
                    .execute();
        } catch (Exception e) {
            log.error("Hỏi Google về lượt mua {} thất bại", rutGon(purchaseToken), e);
            throw new IllegalArgumentException(
                    "Không xác minh được lượt mua với Google. Thử lại sau ít phút.");
        }
    }

    /** Chỗ nối cho test, cùng lý do như {@link #hoiGoogle}. */
    protected void xacNhanVoiGoogle(String productId, String purchaseToken) {
        try {
            publisher().purchases().products()
                    .acknowledge(packageName, productId, purchaseToken, null)
                    .execute();
        } catch (Exception e) {
            // Gói ĐÃ cấp rồi, nên không ném lỗi ngược về khách. Nhưng phải
            // kêu to: acknowledge hỏng mà không ai biết thì ba ngày sau
            // Google hoàn tiền hàng loạt.
            log.error("KHÔNG acknowledge được lượt mua {} — Google sẽ tự hoàn tiền sau 3 ngày "
                    + "nếu không xử lý tay", rutGon(purchaseToken), e);
        }
    }

    /**
     * Dựng client một lần rồi dùng lại.
     *
     * <p>Dựng lại mỗi lượt mua thì mỗi lượt phải đi lấy token OAuth mới, vừa
     * chậm vừa dễ chạm hạn mức.
     */
    private AndroidPublisher publisher() throws Exception {
        AndroidPublisher cu = publisher;
        if (cu != null) {
            return cu;
        }
        synchronized (this) {
            if (publisher == null) {
                GoogleCredentials cred = GoogleCredentials
                        .fromStream(new ByteArrayInputStream(
                                serviceAccountJson.getBytes(StandardCharsets.UTF_8)))
                        .createScoped(Collections.singleton(
                                AndroidPublisherScopes.ANDROIDPUBLISHER));
                publisher = new AndroidPublisher.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(cred))
                        .setApplicationName("AstroTarot")
                        .build();
            }
            return publisher;
        }
    }

    /** Token dài và là bí mật — log vào thì đừng log cả chuỗi. */
    private static String rutGon(String token) {
        return token == null || token.length() < 12
                ? "…"
                : token.substring(0, 8) + "…" + token.substring(token.length() - 4);
    }
}
