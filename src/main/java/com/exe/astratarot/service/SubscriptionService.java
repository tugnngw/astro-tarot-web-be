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

    boolean canUseAI(UUID userId);

    void recordAIUsage(UUID userId);

    SubscriptionPlan createPlan(CreatePlanRequest request);

    SubscriptionPlan updatePlan(UUID planId, UpdatePlanRequest request);

    UserPlanPurchase updateUserPurchase(UUID purchaseId, UpdateUserPurchaseRequest request);

    List<SubscriptionPlan> getAllActivePlans();

    List<UserPlanPurchase> getUserActivePurchases(UUID userId);

    List<AiUsageRecord> getUserAIUsage(UUID userId, LocalDate date);

    Optional<SubscriptionPlan> getFreePlan();

    int getDailyQuotaForUser(UUID userId, LocalDate date);
}
