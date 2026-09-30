package com.exe.astratarot.service.impl;

import com.exe.astratarot.config.security.SecurityUtils;
import com.exe.astratarot.domain.dto.ai.*;
import com.exe.astratarot.domain.entity.*;
import com.exe.astratarot.domain.enums.TargetType;
import com.exe.astratarot.exception.QuotaExceededException;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceImplTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private UserPlanPurchaseRepository userPlanPurchaseRepository;

    @Mock
    private AiUsageDailyRepository aiUsageDailyRepository;

    @Mock
    private PlanChangeAuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private UUID userId;
    private UUID planId;
    private User user;
    private SubscriptionPlan standardPlan;
    private MockedStatic<SecurityUtils> mockedSecurityUtils;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        planId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .username("testuser")
                .email("test@example.com")
                .build();

        standardPlan = SubscriptionPlan.builder()
                .id(planId)
                .name("Pro Monthly")
                .planType(SubscriptionPlan.PlanType.MONTHLY)
                .dailyQuota(20)
                .price(199000L)
                .durationDays(30)
                .isActive(true)
                .description("Pro monthly subscription")
                .build();

        mockedSecurityUtils = mockStatic(SecurityUtils.class);
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserUUID).thenReturn(userId);
        mockedSecurityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(userId.toString());
        mockedSecurityUtils.when(SecurityUtils::isAdmin).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (mockedSecurityUtils != null) {
            mockedSecurityUtils.close();
        }
    }

    // ------------------------------------------------------------
    // 1. createPurchase tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("Tạo purchase mới: chụp ảnh gói dịch vụ (snapshot) và lưu trạng thái ACTIVE")
    void createPurchase_Success_SnapshotsPlanAndSetsActive() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(standardPlan));
        when(userPlanPurchaseRepository.save(any(UserPlanPurchase.class))).thenAnswer(invocation -> {
            UserPlanPurchase p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        CreatePurchaseRequest request = new CreatePurchaseRequest(planId, UserPlanPurchase.PurchaseType.PAYOS);
        AIPlanResponse response = subscriptionService.createPurchase(userId, request);

        assertNotNull(response);
        assertEquals("Pro Monthly", response.getPlanName());
        assertEquals(20, response.getDailyQuota());
        assertEquals(199000L, response.getPrice());
        assertEquals(30, response.getRemainingDays());

        ArgumentCaptor<UserPlanPurchase> purchaseCaptor = ArgumentCaptor.forClass(UserPlanPurchase.class);
        verify(userPlanPurchaseRepository).save(purchaseCaptor.capture());
        UserPlanPurchase saved = purchaseCaptor.getValue();

        assertEquals(userId, saved.getUserId());
        assertEquals(planId, saved.getPlanId());
        assertEquals("Pro Monthly", saved.getPlanNameSnapshot());
        assertEquals(20, saved.getDailyQuotaSnapshot());
        assertEquals(199000L, saved.getPriceSnapshot());
        assertEquals(UserPlanPurchase.PurchaseStatus.ACTIVE, saved.getStatus());
        assertEquals(UserPlanPurchase.PurchaseType.PAYOS, saved.getPurchaseType());

        // Verify audit log
        verify(auditLogRepository, atLeastOnce()).save(any(PlanChangeAuditLog.class));
    }

    @Test
    @DisplayName("Tạo purchase thất bại khi user không tồn tại")
    void createPurchase_UserNotFound_ThrowsException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CreatePurchaseRequest request = new CreatePurchaseRequest(planId, UserPlanPurchase.PurchaseType.STRIPE);
        assertThrows(ResourceNotFoundException.class, () -> subscriptionService.createPurchase(userId, request));
    }

    @Test
    @DisplayName("Tạo purchase thất bại khi gói không active")
    void createPurchase_InactivePlan_ThrowsException() {
        standardPlan.setIsActive(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(standardPlan));

        CreatePurchaseRequest request = new CreatePurchaseRequest(planId, UserPlanPurchase.PurchaseType.STRIPE);
        assertThrows(IllegalArgumentException.class, () -> subscriptionService.createPurchase(userId, request));
    }

    // ------------------------------------------------------------
    // 2. Quota check & max() logic tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("Không có gói nào active: dùng Free tier mặc định (3 lượt/ngày)")
    void canUseAI_NoActivePurchases_FallsBackToFreeTier() {
        when(userPlanPurchaseRepository.findByUserIdAndStatus(userId, UserPlanPurchase.PurchaseStatus.ACTIVE))
                .thenReturn(List.of());

        LocalDate today = LocalDate.now(VN_ZONE);
        AiUsageDailyId key = new AiUsageDailyId(userId, today);

        // Đã dùng 2 lượt -> còn 1 lượt -> true
        when(aiUsageDailyRepository.findById(key)).thenReturn(Optional.of(
                AiUsageDaily.builder().userId(userId).usageDate(today).countUsed(2).build()
        ));
        assertTrue(subscriptionService.canUseAI(userId));

        // Đã dùng 3 lượt -> hết lượt -> false
        when(aiUsageDailyRepository.findById(key)).thenReturn(Optional.of(
                AiUsageDaily.builder().userId(userId).usageDate(today).countUsed(3).build()
        ));
        assertFalse(subscriptionService.canUseAI(userId));
    }

    @Test
    @DisplayName("Người dùng có nhiều gói active: lấy max() quota giữa các gói")
    void canUseAI_MultipleActivePackages_UsesMaxQuota() {
        Instant now = Instant.now();
        UserPlanPurchase package5 = UserPlanPurchase.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .dailyQuotaSnapshot(5)
                .status(UserPlanPurchase.PurchaseStatus.ACTIVE)
                .startAt(Timestamp.from(now.minus(1, ChronoUnit.DAYS)))
                .endAt(Timestamp.from(now.plus(5, ChronoUnit.DAYS)))
                .build();

        UserPlanPurchase package15 = UserPlanPurchase.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .dailyQuotaSnapshot(15)
                .status(UserPlanPurchase.PurchaseStatus.ACTIVE)
                .startAt(Timestamp.from(now.minus(1, ChronoUnit.DAYS)))
                .endAt(Timestamp.from(now.plus(5, ChronoUnit.DAYS)))
                .build();

        when(userPlanPurchaseRepository.findByUserIdAndStatus(userId, UserPlanPurchase.PurchaseStatus.ACTIVE))
                .thenReturn(List.of(package5, package15));

        LocalDate today = LocalDate.now(VN_ZONE);
        AiUsageDailyId key = new AiUsageDailyId(userId, today);

        // Max quota là 15. Dùng 10 -> còn 5 -> true
        when(aiUsageDailyRepository.findById(key)).thenReturn(Optional.of(
                AiUsageDaily.builder().userId(userId).usageDate(today).countUsed(10).build()
        ));
        assertTrue(subscriptionService.canUseAI(userId));

        // Dùng 15 -> hết -> false
        when(aiUsageDailyRepository.findById(key)).thenReturn(Optional.of(
                AiUsageDaily.builder().userId(userId).usageDate(today).countUsed(15).build()
        ));
        assertFalse(subscriptionService.canUseAI(userId));
    }

    // ------------------------------------------------------------
    // 3. recordAIUsage tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("Ghi nhận lượt dùng AI thành công: tăng countUsed thêm 1")
    void recordAIUsage_Success_IncrementsCount() {
        when(userPlanPurchaseRepository.findByUserIdAndStatus(userId, UserPlanPurchase.PurchaseStatus.ACTIVE))
                .thenReturn(List.of());

        LocalDate today = LocalDate.now(VN_ZONE);
        AiUsageDailyId key = new AiUsageDailyId(userId, today);
        AiUsageDaily usage = AiUsageDaily.builder().userId(userId).usageDate(today).countUsed(1).build();

        when(aiUsageDailyRepository.findById(key)).thenReturn(Optional.of(usage));

        subscriptionService.recordAIUsage(userId);

        assertEquals(2, usage.getCountUsed());
        verify(aiUsageDailyRepository).save(usage);
    }

    @Test
    @DisplayName("Ghi nhận lượt dùng AI khi đã chạm giới hạn: ném QuotaExceededException")
    void recordAIUsage_QuotaExceeded_ThrowsQuotaExceededException() {
        when(userPlanPurchaseRepository.findByUserIdAndStatus(userId, UserPlanPurchase.PurchaseStatus.ACTIVE))
                .thenReturn(List.of()); // Free tier max 3

        LocalDate today = LocalDate.now(VN_ZONE);
        AiUsageDailyId key = new AiUsageDailyId(userId, today);
        AiUsageDaily usage = AiUsageDaily.builder().userId(userId).usageDate(today).countUsed(3).build();

        when(aiUsageDailyRepository.findById(key)).thenReturn(Optional.of(usage));

        assertThrows(QuotaExceededException.class, () -> subscriptionService.recordAIUsage(userId));
        verify(aiUsageDailyRepository, never()).save(usage);
    }

    // ------------------------------------------------------------
    // 4. Admin Plan & Purchase management tests
    // ------------------------------------------------------------

    @Test
    @DisplayName("Admin cập nhật cấu hình gói: ghi nhận audit log")
    void updatePlan_Success_LogsAudit() {
        when(subscriptionPlanRepository.findById(planId)).thenReturn(Optional.of(standardPlan));
        when(subscriptionPlanRepository.save(any(SubscriptionPlan.class))).thenReturn(standardPlan);

        UpdatePlanRequest request = new UpdatePlanRequest();
        request.setDailyQuota(25);
        request.setPrice(249000L);

        SubscriptionPlan updated = subscriptionService.updatePlan(planId, request);

        assertEquals(25, updated.getDailyQuota());
        assertEquals(249000L, updated.getPrice());
        verify(auditLogRepository, atLeast(2)).save(any(PlanChangeAuditLog.class));
    }

    @Test
    @DisplayName("Admin cập nhật lượt mua của người dùng: sửa snapshot và ghi audit log")
    void updateUserPurchase_Success_LogsAudit() {
        UUID purchaseId = UUID.randomUUID();
        Instant now = Instant.now();
        UserPlanPurchase purchase = UserPlanPurchase.builder()
                .id(purchaseId)
                .userId(userId)
                .dailyQuotaSnapshot(10)
                .startAt(Timestamp.from(now.minus(1, ChronoUnit.DAYS)))
                .endAt(Timestamp.from(now.plus(10, ChronoUnit.DAYS)))
                .status(UserPlanPurchase.PurchaseStatus.ACTIVE)
                .build();

        when(userPlanPurchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));
        when(userPlanPurchaseRepository.save(any(UserPlanPurchase.class))).thenReturn(purchase);

        UpdateUserPurchaseRequest request = new UpdateUserPurchaseRequest();
        request.setDailyQuotaSnapshot(15);

        UserPlanPurchase updated = subscriptionService.updateUserPurchase(purchaseId, request);

        assertEquals(15, updated.getDailyQuotaSnapshot());
        verify(auditLogRepository).save(any(PlanChangeAuditLog.class));
    }
}
