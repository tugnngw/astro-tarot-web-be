package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.ai.*;
import com.exe.astratarot.domain.entity.SubscriptionPlan;
import com.exe.astratarot.domain.entity.UserPlanPurchase;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionService {

    AIPlanResponse createPurchase(UUID userId, CreatePurchaseRequest request);

    /**
     * Kích hoạt gói sau khi PayOS (hoặc admin) xác nhận thanh toán AI_SUBSCRIPTION.
     */
    AIPlanResponse activatePaidPurchase(UUID userId, UUID planId);

    /**
     * Cấp gói từ một lượt mua ĐÃ xác minh với Google Play.
     *
     * <p>Khác {@link #activatePaidPurchase}: ở đây tiền đã thu xong rồi —
     * Google trừ tiền khách trước khi máy chủ biết chuyện — nên không chặn
     * vì lý do "đang có gói tháng". Chặn ở bước này là khách mất tiền mà
     * không có gói.
     */
    com.exe.astratarot.domain.entity.UserPlanPurchase capGoiTuGooglePlay(
            com.exe.astratarot.domain.entity.User user,
            com.exe.astratarot.domain.entity.SubscriptionPlan plan);

    boolean canUseAI(UUID userId);

    void recordAIUsage(UUID userId);

    SubscriptionPlan createPlan(CreatePlanRequest request);

    SubscriptionPlan updatePlan(UUID planId, UpdatePlanRequest request);

    UserPlanPurchase updateUserPurchase(UUID purchaseId, UpdateUserPurchaseRequest request);

    List<SubscriptionPlan> getAllActivePlans();

    List<SubscriptionPlan> getAllPlans();

    List<UserPlanPurchase> getUserActivePurchases(UUID userId);

    List<AiUsageRecord> getUserAIUsage(UUID userId, LocalDate date);

    Optional<SubscriptionPlan> getFreePlan();

    int getDailyQuotaForUser(UUID userId, LocalDate date);
}
