package com.exe.astratarot.service.impl;

import com.exe.astratarot.config.PayOsConfig.PayOsClient;
import com.exe.astratarot.config.PayOsProperties;
import com.exe.astratarot.config.security.SecurityUtils;
import com.exe.astratarot.domain.dto.ai.*;
import com.exe.astratarot.domain.entity.*;
import com.exe.astratarot.domain.enums.PaymentPhase;
import com.exe.astratarot.domain.enums.TargetType;
import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.domain.enums.WalletTransactionType;
import com.exe.astratarot.exception.QuotaExceededException;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.*;
import com.exe.astratarot.service.SubscriptionService;
import com.exe.astratarot.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Integer FREE_DAILY_QUOTA = 3;

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserPlanPurchaseRepository userPlanPurchaseRepository;
    private final AiUsageDailyRepository aiUsageDailyRepository;
    private final PlanChangeAuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PayOsClient payOsClient;
    private final PayOsProperties payOsProperties;
    private final ObjectMapper objectMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend-url:http://localhost:8081}")
    private String frontendUrl;

    // ------------------------------------------------------------
    // 1. Create new purchase for user (snapshots plan data)
    // ------------------------------------------------------------
    @Override
    @Transactional
    public AIPlanResponse createPurchase(UUID userId, CreatePurchaseRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        SubscriptionPlan plan = subscriptionPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        if (Boolean.FALSE.equals(plan.getIsActive())) {
            throw new IllegalArgumentException("Plan is not active");
        }

        long price = plan.getPrice() != null ? plan.getPrice() : 0L;
        if (price <= 0 || plan.getPlanType() == SubscriptionPlan.PlanType.FREE) {
            throw new IllegalArgumentException(
                    "Gói miễn phí là mặc định (" + FREE_DAILY_QUOTA + " lượt/ngày), không cần đăng ký.");
        }

        if (request.getPurchaseType() == null) {
            throw new IllegalArgumentException("Thiếu phương thức thanh toán.");
        }
        UserPlanPurchase.PurchaseType purchaseType = request.getPurchaseType();

        assertMayAcquirePlan(userId, plan);

        return switch (purchaseType) {
            case WALLET -> {
                walletService.debit(user, price, WalletTransactionType.AI_SUBSCRIPTION,
                        plan.getId().toString(), "Mua gói AI " + plan.getName());
                yield activatePurchase(user, plan, UserPlanPurchase.PurchaseType.WALLET);
            }
            case PAYOS -> createPayOsCheckout(user, plan);
            case MANUAL -> {
                if (!SecurityUtils.isAdmin()) {
                    throw new IllegalArgumentException(
                            "Chỉ quản trị viên mới có thể gán gói thủ công.");
                }
                yield activatePurchase(user, plan, UserPlanPurchase.PurchaseType.MANUAL);
            }
            case STRIPE -> throw new IllegalArgumentException(
                    "Thanh toán thẻ quốc tế chưa được hỗ trợ. Vui lòng dùng Ví hoặc PayOS.");
        };
    }

    @Override
    @Transactional
    public AIPlanResponse activatePaidPurchase(UUID userId, UUID planId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        assertMayAcquirePlan(userId, plan);
        return activatePurchase(user, plan, UserPlanPurchase.PurchaseType.PAYOS);
    }

    /**
     * Một gói tháng đang active → chỉ cho nâng cấp (quota hoặc giá cao hơn).
     * Không cho mua lại cùng planId. DAY_PASS vẫn mua thêm được.
     */
    private void assertMayAcquirePlan(UUID userId, SubscriptionPlan plan) {
        List<UserPlanPurchase> active = listTrulyActivePurchases(userId);

        boolean samePlan = active.stream().anyMatch(p -> plan.getId().equals(p.getPlanId()));
        if (samePlan) {
            throw new IllegalArgumentException("Bạn đang dùng gói này rồi.");
        }

        if (plan.getPlanType() != SubscriptionPlan.PlanType.MONTHLY) {
            return;
        }

        Optional<UserPlanPurchase> currentMonthly = active.stream()
                .filter(p -> resolvePlanType(p) == SubscriptionPlan.PlanType.MONTHLY)
                .findFirst();

        if (currentMonthly.isEmpty()) {
            return;
        }

        UserPlanPurchase cur = currentMonthly.get();
        int curQuota = cur.getDailyQuotaSnapshot() != null ? cur.getDailyQuotaSnapshot() : 0;
        long curPrice = cur.getPriceSnapshot() != null ? cur.getPriceSnapshot() : 0L;
        int newQuota = plan.getDailyQuota() != null ? plan.getDailyQuota() : 0;
        long newPrice = plan.getPrice() != null ? plan.getPrice() : 0L;

        boolean upgrade = newQuota > curQuota || newPrice > curPrice;
        if (!upgrade) {
            throw new IllegalArgumentException(
                    "Bạn đang có gói tháng đang hiệu lực. Chỉ được nâng cấp lên gói có hạn mức hoặc giá cao hơn.");
        }
    }

    private List<UserPlanPurchase> listTrulyActivePurchases(UUID userId) {
        return userPlanPurchaseRepository
                .findByUserIdAndStatus(userId, UserPlanPurchase.PurchaseStatus.ACTIVE)
                .stream()
                .filter(UserPlanPurchase::isActive)
                .toList();
    }

    private SubscriptionPlan.PlanType resolvePlanType(UserPlanPurchase purchase) {
        if (purchase.getPlan() != null && purchase.getPlan().getPlanType() != null) {
            return purchase.getPlan().getPlanType();
        }
        return subscriptionPlanRepository.findById(purchase.getPlanId())
                .map(SubscriptionPlan::getPlanType)
                .orElse(null);
    }

    private AIPlanResponse activatePurchase(User user, SubscriptionPlan plan,
                                            UserPlanPurchase.PurchaseType purchaseType) {
        Instant startAt = Instant.now();
        Instant endAt = startAt.plus(plan.getDurationDays(), ChronoUnit.DAYS);

        UserPlanPurchase purchase = UserPlanPurchase.builder()
                .userId(user.getId())
                .planId(plan.getId())
                .planNameSnapshot(plan.getName())
                .dailyQuotaSnapshot(plan.getDailyQuota())
                .priceSnapshot(plan.getPrice())
                .startAt(Timestamp.from(startAt))
                .endAt(Timestamp.from(endAt))
                .status(UserPlanPurchase.PurchaseStatus.ACTIVE)
                .purchaseType(purchaseType)
                .build();

        UserPlanPurchase savedPurchase = userPlanPurchaseRepository.save(purchase);

        extendPreviousPackages(user.getId(), savedPurchase.getId(), plan.getPlanType());

        auditLogRepository.save(PlanChangeAuditLog.builder()
                .targetType(TargetType.USER_PURCHASE)
                .targetId(savedPurchase.getId())
                .fieldName("STATUS")
                .oldValue(null)
                .newValue(savedPurchase.getStatus().name())
                .changedBy(user.getId())
                .build());

        return AIPlanResponse.builder()
                .purchaseId(savedPurchase.getId())
                .planName(plan.getName())
                .dailyQuota(plan.getDailyQuota())
                .price(plan.getPrice())
                .startAt(startAt)
                .endAt(endAt)
                .remainingDays(plan.getDurationDays())
                .isActive(savedPurchase.isActive())
                .paymentPending(false)
                .build();
    }

    /**
     * Tạo link PayOS — chưa kích hoạt gói. Webhook AI_SUBSCRIPTION mới gọi
     * {@link #activatePaidPurchase}.
     */
    private AIPlanResponse createPayOsCheckout(User user, SubscriptionPlan plan) {
        if (!payOsClient.enabled()) {
            throw new IllegalStateException("Cổng PayOS chưa được cấu hình trên hệ thống");
        }

        long amount = plan.getPrice();
        long orderCode = nextOrderCode();
        String returnUrl = firstNonBlank(payOsProperties.getReturnUrl(),
                trimSlash(frontendUrl) + "/subscription?payment=success");
        String cancelUrl = firstNonBlank(payOsProperties.getCancelUrl(),
                trimSlash(frontendUrl) + "/subscription?payment=cancel");

        String description = "AI " + plan.getName();
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        CreatePaymentLinkRequest payRequest = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .buyerName(user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName() : user.getUsername())
                .buyerEmail(user.getEmail())
                .build();

        CreatePaymentLinkResponse link;
        try {
            link = payOsClient.sdk().paymentRequests().create(payRequest);
        } catch (Exception e) {
            log.error("Tạo link PayOS gói AI thất bại user={} plan={}: {}",
                    user.getId(), plan.getId(), e.getMessage());
            throw new IllegalStateException("Không tạo được link thanh toán PayOS: " + e.getMessage(), e);
        }

        Map<String, Object> meta = new HashMap<>();
        meta.put("checkoutUrl", link.getCheckoutUrl());
        meta.put("qrCode", link.getQrCode());
        meta.put("paymentLinkId", link.getPaymentLinkId());
        meta.put("planId", plan.getId().toString());
        meta.put("purpose", "AI_SUBSCRIPTION");

        paymentTransactionRepository.save(PaymentTransaction.builder()
                .booking(null)
                .user(user)
                .amount(amount)
                .phase(PaymentPhase.AI_SUBSCRIPTION)
                .paymentMethod("PAYOS")
                .externalTransactionId(String.valueOf(orderCode))
                .status(TransactionStatus.PENDING)
                .metadata(writeJson(meta))
                .build());

        log.info("Tạo PayOS checkout gói AI order={} user={} plan={} amount={}",
                orderCode, user.getId(), plan.getId(), amount);

        return AIPlanResponse.builder()
                .purchaseId(null)
                .planName(plan.getName())
                .dailyQuota(plan.getDailyQuota())
                .price(plan.getPrice())
                .remainingDays(plan.getDurationDays())
                .isActive(false)
                .checkoutUrl(link.getCheckoutUrl())
                .qrCode(link.getQrCode())
                .paymentPending(true)
                .build();
    }

    // ------------------------------------------------------------
    // 2. Check if AI can be used (quota check)
    // ------------------------------------------------------------
    @Override
    public boolean canUseAI(UUID userId) {
        return getQuotaForToday(userId) > getUsageCountForToday(userId);
    }

    // ------------------------------------------------------------
    // 3. Record AI usage
    // ------------------------------------------------------------
    @Override
    @Transactional
    public void recordAIUsage(UUID userId) {
        LocalDate today = LocalDate.now(VN_ZONE);
        AiUsageDailyId key = new AiUsageDailyId(userId, today);
        AiUsageDaily usage = aiUsageDailyRepository.findById(key)
                .orElseGet(() -> AiUsageDaily.builder()
                        .userId(userId)
                        .usageDate(today)
                        .countUsed(0)
                        .build());

        int quotaToday = getQuotaForToday(userId);
        if (usage.getCountUsed() >= quotaToday) {
            throw new QuotaExceededException("Đã hết lượt dùng AI hôm nay (" + quotaToday + "/" + quotaToday + ")");
        }

        usage.increment();
        aiUsageDailyRepository.save(usage);
    }

    // ------------------------------------------------------------
    // 4. Helper: get quota for today based on active packages (max logic)
    // ------------------------------------------------------------
    private int getQuotaForToday(UUID userId) {
        List<UserPlanPurchase> activePurchases = userPlanPurchaseRepository.findByUserIdAndStatus(
                userId, UserPlanPurchase.PurchaseStatus.ACTIVE);

        int quotaToday = activePurchases.stream()
                .filter(UserPlanPurchase::isActive)
                .mapToInt(UserPlanPurchase::getDailyQuotaSnapshot)
                .max()
                .orElse(0);

        if (quotaToday == 0) {
            quotaToday = FREE_DAILY_QUOTA;
        }
        return quotaToday;
    }

    // ------------------------------------------------------------
    // 5. Helper: get usage count for today
    // ------------------------------------------------------------
    private int getUsageCountForToday(UUID userId) {
        LocalDate today = LocalDate.now(VN_ZONE);
        AiUsageDailyId key = new AiUsageDailyId(userId, today);
        AiUsageDaily usage = aiUsageDailyRepository.findById(key).orElse(null);
        return usage != null ? usage.getCountUsed() : 0;
    }

    // ------------------------------------------------------------
    // 6. Extend previous packages to SUPERSEDED
    // ------------------------------------------------------------
    private void extendPreviousPackages(UUID userId, UUID newPurchaseId, SubscriptionPlan.PlanType newPlanType) {
        if (newPlanType != SubscriptionPlan.PlanType.MONTHLY) {
            return;
        }

        List<UserPlanPurchase> previousPurchases = userPlanPurchaseRepository.findByUserIdAndIdNotAndStatus(
                userId, newPurchaseId, UserPlanPurchase.PurchaseStatus.ACTIVE);

        for (UserPlanPurchase purchase : previousPurchases) {
            if (resolvePlanType(purchase) == SubscriptionPlan.PlanType.MONTHLY) {
                purchase.setStatus(UserPlanPurchase.PurchaseStatus.SUPERSEDED);
                userPlanPurchaseRepository.save(purchase);

                auditLogRepository.save(PlanChangeAuditLog.builder()
                        .targetType(TargetType.USER_PURCHASE)
                        .targetId(purchase.getId())
                        .fieldName("STATUS")
                        .oldValue(UserPlanPurchase.PurchaseStatus.ACTIVE.name())
                        .newValue(UserPlanPurchase.PurchaseStatus.SUPERSEDED.name())
                        .changedBy(userId)
                        .build());
            }
        }
    }

    // ------------------------------------------------------------
    // 7. Admin: Create plan
    // ------------------------------------------------------------
    @Override
    @Transactional
    @CacheEvict(cacheNames = "subscription-plans-active", allEntries = true)
    public SubscriptionPlan createPlan(CreatePlanRequest request) {
        if (request.getPlanType() == SubscriptionPlan.PlanType.FREE) {
            SubscriptionPlan existingFree = subscriptionPlanRepository
                    .findByPlanTypeAndIsActiveTrue(SubscriptionPlan.PlanType.FREE)
                    .orElse(null);
            if (existingFree != null) {
                throw new IllegalArgumentException("Free plan already exists");
            }
        }

        SubscriptionPlan plan = SubscriptionPlan.builder()
                .planType(request.getPlanType())
                .name(request.getName())
                .dailyQuota(request.getDailyQuota())
                .price(request.getPrice())
                .durationDays(request.getDurationDays())
                .isActive(true)
                .description(request.getDescription())
                .build();

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);

        UUID adminId = SecurityUtils.getCurrentUserUUID();
        auditLogRepository.save(PlanChangeAuditLog.builder()
                .targetType(TargetType.PLAN)
                .targetId(savedPlan.getId())
                .fieldName("ALL")
                .oldValue(null)
                .newValue(savedPlan.toString())
                .changedBy(adminId)
                .build());

        return savedPlan;
    }

    // ------------------------------------------------------------
    // 8. Admin: Update plan
    // ------------------------------------------------------------
    @Override
    @Transactional
    @CacheEvict(cacheNames = "subscription-plans-active", allEntries = true)
    public SubscriptionPlan updatePlan(UUID planId, UpdatePlanRequest request) {
        SubscriptionPlan plan = subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        UUID adminId = SecurityUtils.getCurrentUserUUID();

        if (request.getDailyQuota() != null) {
            String oldVal = plan.getDailyQuota() != null ? plan.getDailyQuota().toString() : null;
            plan.setDailyQuota(request.getDailyQuota());
            auditLogRepository.save(PlanChangeAuditLog.builder()
                    .targetType(TargetType.PLAN)
                    .targetId(planId)
                    .fieldName("DAILY_QUOTA")
                    .oldValue(oldVal)
                    .newValue(request.getDailyQuota().toString())
                    .changedBy(adminId)
                    .build());
        }

        if (request.getPrice() != null) {
            String oldVal = plan.getPrice() != null ? plan.getPrice().toString() : null;
            plan.setPrice(request.getPrice());
            auditLogRepository.save(PlanChangeAuditLog.builder()
                    .targetType(TargetType.PLAN)
                    .targetId(planId)
                    .fieldName("PRICE")
                    .oldValue(oldVal)
                    .newValue(request.getPrice().toString())
                    .changedBy(adminId)
                    .build());
        }

        if (request.getIsActive() != null) {
            String oldVal = plan.getIsActive() != null ? plan.getIsActive().toString() : null;
            plan.setIsActive(request.getIsActive());
            auditLogRepository.save(PlanChangeAuditLog.builder()
                    .targetType(TargetType.PLAN)
                    .targetId(planId)
                    .fieldName("IS_ACTIVE")
                    .oldValue(oldVal)
                    .newValue(request.getIsActive().toString())
                    .changedBy(adminId)
                    .build());
        }

        if (request.getDescription() != null) {
            plan.setDescription(request.getDescription());
        }

        return subscriptionPlanRepository.save(plan);
    }

    // ------------------------------------------------------------
    // 9. Admin: Update user purchase
    // ------------------------------------------------------------
    @Override
    @Transactional
    public UserPlanPurchase updateUserPurchase(UUID purchaseId, UpdateUserPurchaseRequest request) {
        UserPlanPurchase purchase = userPlanPurchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("User purchase not found"));

        UUID currentUserId = SecurityUtils.getCurrentUserUUID();
        if (!SecurityUtils.isAdmin() && !purchase.getUserId().equals(currentUserId)) {
            throw new SecurityException("Không có quyền sửa purchase này");
        }

        if (request.getDailyQuotaSnapshot() != null) {
            purchase.setDailyQuotaSnapshot(request.getDailyQuotaSnapshot());
        }

        if (request.getEndAt() != null) {
            purchase.setEndAt(Timestamp.from(request.getEndAt()));
        }

        userPlanPurchaseRepository.save(purchase);

        auditLogRepository.save(PlanChangeAuditLog.builder()
                .targetType(TargetType.USER_PURCHASE)
                .targetId(purchaseId)
                .fieldName("USER_PURCHASE_UPDATE")
                .oldValue(null)
                .newValue(request.toString())
                .changedBy(currentUserId)
                .build());

        return purchase;
    }

    // ------------------------------------------------------------
    // 10. Get all active plans
    // ------------------------------------------------------------
    @Override
    @Cacheable(cacheNames = "subscription-plans-active", unless = "#result == null || #result.isEmpty()")
    public List<SubscriptionPlan> getAllActivePlans() {
        return subscriptionPlanRepository.findByIsActiveTrue();
    }

    @Override
    public List<SubscriptionPlan> getAllPlans() {
        return subscriptionPlanRepository.findAll();
    }

    // ------------------------------------------------------------
    // 11. Get user active purchases
    // ------------------------------------------------------------
    @Override
    public List<UserPlanPurchase> getUserActivePurchases(UUID userId) {
        return userPlanPurchaseRepository.findByUserIdAndStatus(userId, UserPlanPurchase.PurchaseStatus.ACTIVE)
                .stream()
                .filter(UserPlanPurchase::isActive)
                .toList();
    }

    // ------------------------------------------------------------
    // 12. Get user AI usage records for a date
    // ------------------------------------------------------------
    @Override
    public List<AiUsageRecord> getUserAIUsage(UUID userId, LocalDate date) {
        AiUsageDailyId key = new AiUsageDailyId(userId, date);
        AiUsageDaily usage = aiUsageDailyRepository.findById(key).orElse(null);

        return usage != null
                ? List.of(new AiUsageRecord(userId, date, usage.getCountUsed()))
                : List.of(new AiUsageRecord(userId, date, 0));
    }

    // ------------------------------------------------------------
    // 13. Get free plan
    // ------------------------------------------------------------
    @Override
    public Optional<SubscriptionPlan> getFreePlan() {
        return subscriptionPlanRepository.findByPlanTypeAndIsActiveTrue(SubscriptionPlan.PlanType.FREE);
    }

    // ------------------------------------------------------------
    // 14. Get daily quota for user (helper)
    // ------------------------------------------------------------
    @Override
    public int getDailyQuotaForUser(UUID userId, LocalDate date) {
        return getQuotaForToday(userId);
    }

    private long nextOrderCode() {
        for (int i = 0; i < 8; i++) {
            long code = Instant.now().getEpochSecond() * 1000L + secureRandom.nextInt(1000);
            if (paymentTransactionRepository.findByExternalTransactionId(String.valueOf(code)).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Không sinh được orderCode PayOS duy nhất");
    }

    private String writeJson(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            throw new IllegalStateException("Không ghi được metadata gói AI", e);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
