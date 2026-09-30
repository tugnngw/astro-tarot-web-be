package com.exe.astratarot.service.impl;

import com.exe.astratarot.config.security.SecurityUtils;
import com.exe.astratarot.domain.dto.ai.*;
import com.exe.astratarot.domain.entity.*;
import com.exe.astratarot.domain.enums.TargetType;
import com.exe.astratarot.exception.QuotaExceededException;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.*;
import com.exe.astratarot.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

        // Snapshot data at purchase time
        Instant startAt = Instant.now();
        Instant endAt = startAt.plus(plan.getDurationDays(), ChronoUnit.DAYS);

        UserPlanPurchase purchase = UserPlanPurchase.builder()
                .userId(userId)
                .planId(plan.getId())
                .planNameSnapshot(plan.getName())
                .dailyQuotaSnapshot(plan.getDailyQuota())
                .priceSnapshot(plan.getPrice())
                .startAt(Timestamp.from(startAt))
                .endAt(Timestamp.from(endAt))
                .status(UserPlanPurchase.PurchaseStatus.ACTIVE)
                .purchaseType(request.getPurchaseType() != null ? request.getPurchaseType() : UserPlanPurchase.PurchaseType.MANUAL)
                .build();

        UserPlanPurchase savedPurchase = userPlanPurchaseRepository.save(purchase);

        // Extend / supersede previous packages of the same monthly subscription if applicable
        extendPreviousPackages(userId, savedPurchase.getId(), plan.getPlanType());

        // Audit log
        auditLogRepository.save(PlanChangeAuditLog.builder()
                .targetType(TargetType.USER_PURCHASE)
                .targetId(savedPurchase.getId())
                .fieldName("STATUS")
                .oldValue(null)
                .newValue(savedPurchase.getStatus().name())
                .changedBy(userId)
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
        List<UserPlanPurchase> previousPurchases = userPlanPurchaseRepository.findByUserIdAndIdNotAndStatus(
                userId, newPurchaseId, UserPlanPurchase.PurchaseStatus.ACTIVE);

        for (UserPlanPurchase purchase : previousPurchases) {
            if (purchase.getPlan() != null && purchase.getPlan().getPlanType() == newPlanType && newPlanType == SubscriptionPlan.PlanType.MONTHLY) {
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

        // Audit log
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
}
