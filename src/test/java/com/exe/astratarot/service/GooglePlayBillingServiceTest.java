package com.exe.astratarot.service;

import com.exe.astratarot.domain.entity.GooglePlayPurchase;
import com.exe.astratarot.domain.entity.SubscriptionPlan;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserPlanPurchase;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.GooglePlayPurchaseRepository;
import com.exe.astratarot.repository.SubscriptionPlanRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.impl.GooglePlayBillingServiceImpl;
import com.google.api.services.androidpublisher.model.ProductPurchase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Luật của việc đổi lượt mua Google Play lấy gói.
 *
 * <p>Phần gọi HTTP sang Google được thay bằng chỗ nối {@code hoiGoogle} /
 * {@code xacNhanVoiGoogle}: cái đáng test ở đây là những chỗ mà sai một lần
 * là khách mất tiền hoặc được gói không trả tiền.
 */
@ExtendWith(MockitoExtension.class)
class GooglePlayBillingServiceTest {

    @Mock
    private SubscriptionPlanRepository planRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GooglePlayPurchaseRepository googlePlayPurchaseRepository;
    @Mock
    private SubscriptionService subscriptionService;

    private static final String TOKEN = "token-google-dai-ngoang-0001";
    private static final String MA_SP = "ai_basic_1d";

    private final UUID userId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID luotMuaId = UUID.randomUUID();

    /** Lượt mua mà "Google" trả về. */
    private ProductPurchase traVe;

    /** Số lần gọi acknowledge. */
    private int soLanXacNhan;

    private GooglePlayBillingServiceImpl service;

    @BeforeEach
    void setUp() {
        traVe = new ProductPurchase().setPurchaseState(0).setOrderId("GPA.1111");
        soLanXacNhan = 0;

        service = new GooglePlayBillingServiceImpl(
                planRepository, userRepository, googlePlayPurchaseRepository,
                subscriptionService) {
            @Override
            protected ProductPurchase hoiGoogle(String productId, String purchaseToken) {
                return traVe;
            }

            @Override
            protected void xacNhanVoiGoogle(String productId, String purchaseToken) {
                soLanXacNhan++;
            }
        };
        // Khoá tài khoản dịch vụ: chỉ cần khác rỗng để tính năng coi là đã
        // cấu hình — bản thật đọc từ biến môi trường của Render.
        ReflectionTestUtils.setField(service, "serviceAccountJson", "{\"type\":\"gia\"}");
        ReflectionTestUtils.setField(service, "packageName", "com.astrotarot.astrotarot_mobile");
    }

    private SubscriptionPlan goi() {
        SubscriptionPlan p = new SubscriptionPlan();
        p.setId(planId);
        p.setName("Basic 1 ngày");
        p.setGooglePlayProductId(MA_SP);
        p.setGooglePlayPrice(19000L);
        p.setPrice(15000L);
        p.setDailyQuota(10);
        p.setDurationDays(1);
        return p;
    }

    private void duongThuanSanSang() {
        when(planRepository.findByGooglePlayProductId(MA_SP)).thenReturn(Optional.of(goi()));
        User u = new User();
        u.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(u));
        UserPlanPurchase capGoi = UserPlanPurchase.builder().id(luotMuaId).build();
        when(subscriptionService.capGoiTuGooglePlay(any(), any())).thenReturn(capGoi);
    }

    @Test
    @DisplayName("đường thuận: cấp gói, ghi lại token, rồi xác nhận với Google")
    void duongThuan() {
        when(googlePlayPurchaseRepository.findById(TOKEN)).thenReturn(Optional.empty());
        duongThuanSanSang();

        UUID ketQua = service.doiLayGoi(userId, MA_SP, TOKEN);

        assertThat(ketQua).isEqualTo(luotMuaId);

        ArgumentCaptor<GooglePlayPurchase> ghi =
                ArgumentCaptor.forClass(GooglePlayPurchase.class);
        verify(googlePlayPurchaseRepository).save(ghi.capture());
        assertThat(ghi.getValue().getPurchaseToken()).isEqualTo(TOKEN);
        assertThat(ghi.getValue().getUserId()).isEqualTo(userId);
        assertThat(ghi.getValue().getOrderId()).isEqualTo("GPA.1111");

        // Không acknowledge trong ba ngày là Google tự hoàn tiền.
        assertThat(soLanXacNhan).isEqualTo(1);
    }

    @Test
    @DisplayName("gửi lại cùng token: trả kết quả cũ, KHÔNG cấp thêm gói")
    void guiLaiCungToken() {
        // Chuyện thường ngày, không phải trường hợp hiếm: app phát lại những
        // lượt mua chưa acknowledge mỗi lần mở lại.
        when(googlePlayPurchaseRepository.findById(TOKEN)).thenReturn(Optional.of(
                GooglePlayPurchase.builder()
                        .purchaseToken(TOKEN)
                        .userId(userId)
                        .planId(planId)
                        .userPurchaseId(luotMuaId)
                        .build()));

        UUID ketQua = service.doiLayGoi(userId, MA_SP, TOKEN);

        assertThat(ketQua).isEqualTo(luotMuaId);
        verify(subscriptionService, never()).capGoiTuGooglePlay(any(), any());
        verify(googlePlayPurchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("token của người khác: không cấp gói")
    void tokenCuaNguoiKhac() {
        UUID nguoiKhac = UUID.randomUUID();
        when(googlePlayPurchaseRepository.findById(TOKEN)).thenReturn(Optional.of(
                GooglePlayPurchase.builder()
                        .purchaseToken(TOKEN)
                        .userId(nguoiKhac)
                        .userPurchaseId(luotMuaId)
                        .build()));

        assertThatThrownBy(() -> service.doiLayGoi(userId, MA_SP, TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tài khoản khác");
        verify(subscriptionService, never()).capGoiTuGooglePlay(any(), any());
    }

    @Test
    @DisplayName("đơn đang chờ thanh toán: chưa cấp gói, nói rõ để khách mở lại sau")
    void donDangCho() {
        // Trả bằng chuyển khoản hay thẻ cào thì Google giữ đơn ở PENDING từ
        // vài phút tới vài ngày. Cấp gói lúc ấy là cấp cho đơn có thể không
        // bao giờ trả tiền.
        when(googlePlayPurchaseRepository.findById(TOKEN)).thenReturn(Optional.empty());
        when(planRepository.findByGooglePlayProductId(MA_SP)).thenReturn(Optional.of(goi()));
        traVe.setPurchaseState(2);

        assertThatThrownBy(() -> service.doiLayGoi(userId, MA_SP, TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đang chờ thanh toán");

        verify(subscriptionService, never()).capGoiTuGooglePlay(any(), any());
        verify(googlePlayPurchaseRepository, never()).save(any());
        assertThat(soLanXacNhan).isZero();
    }

    @Test
    @DisplayName("đơn đã huỷ: không cấp gói")
    void donDaHuy() {
        when(googlePlayPurchaseRepository.findById(TOKEN)).thenReturn(Optional.empty());
        when(planRepository.findByGooglePlayProductId(MA_SP)).thenReturn(Optional.of(goi()));
        traVe.setPurchaseState(1);

        assertThatThrownBy(() -> service.doiLayGoi(userId, MA_SP, TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ hoặc đã huỷ");
        verify(subscriptionService, never()).capGoiTuGooglePlay(any(), any());
    }

    @Test
    @DisplayName("mã sản phẩm lạ: không cấp gói nào")
    void maSanPhamLa() {
        when(googlePlayPurchaseRepository.findById(TOKEN)).thenReturn(Optional.empty());
        when(planRepository.findByGooglePlayProductId("ai_khong_co"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.doiLayGoi(userId, "ai_khong_co", TOKEN))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(subscriptionService, never()).capGoiTuGooglePlay(any(), any());
    }

    @Test
    @DisplayName("thiếu mã sản phẩm hay token: chặn trước khi hỏi Google")
    void thieuThamSo() {
        for (String[] bo : List.of(
                new String[]{null, TOKEN},
                new String[]{"", TOKEN},
                new String[]{MA_SP, null},
                new String[]{MA_SP, "  "})) {
            assertThatThrownBy(() -> service.doiLayGoi(userId, bo[0], bo[1]))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Thiếu");
        }
        verify(googlePlayPurchaseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("chưa có khoá tài khoản dịch vụ: nói rõ chứ không hỏi Google")
    void chuaCauHinh() {
        ReflectionTestUtils.setField(service, "serviceAccountJson", "");

        assertThat(service.daCauHinh()).isFalse();
        assertThatThrownBy(() -> service.doiLayGoi(userId, MA_SP, TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chưa cấu hình");
    }

    @Test
    @DisplayName("acknowledge hỏng: vẫn cấp gói cho khách, chỉ ghi log")
    void xacNhanHong() {
        when(googlePlayPurchaseRepository.findById(TOKEN)).thenReturn(Optional.empty());
        duongThuanSanSang();

        // Lần này KHÔNG thay `xacNhanVoiGoogle`: bản thật sẽ cố dựng client
        // từ khoá giả ở trên và hỏng ngay bước đọc khoá. Đúng cái cần test —
        // lỗi ấy phải bị nuốt, vì khách đã trả tiền và gói đã vào. Ném ra là
        // app tưởng mua thất bại rồi thử lại mãi.
        GooglePlayBillingServiceImpl thatXacNhan = new GooglePlayBillingServiceImpl(
                planRepository, userRepository, googlePlayPurchaseRepository,
                subscriptionService) {
            @Override
            protected ProductPurchase hoiGoogle(String productId, String purchaseToken) {
                return traVe;
            }
        };
        ReflectionTestUtils.setField(thatXacNhan, "serviceAccountJson", "{\"type\":\"gia\"}");

        assertThat(thatXacNhan.doiLayGoi(userId, MA_SP, TOKEN)).isEqualTo(luotMuaId);
        verify(googlePlayPurchaseRepository).save(any());
    }
}
