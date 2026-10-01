package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.ai.*;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.entity.SubscriptionPlan;
import com.exe.astratarot.domain.entity.UserPlanPurchase;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    private UUID resolveUserId(CustomUserDetails userDetails, Authentication auth) {
        if (userDetails != null && userDetails.getUser() != null) {
            return userDetails.getUser().getId();
        }
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getUser().getId();
        }
        if (auth != null) {
            try {
                return UUID.fromString(auth.getName());
            } catch (Exception ignored) {
                // fall through
            }
        }
        throw new SecurityException("No authenticated user found");
    }

    // -------- Public / User endpoints --------

    @GetMapping("/plans/active")
    public ResponseEntity<ApiResponse<List<SubscriptionPlan>>> getActivePlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getAllActivePlans()));
    }

    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<AIPlanResponse>> createPurchase(
            @RequestBody CreatePurchaseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication auth) {
        UUID userId = resolveUserId(userDetails, auth);
        AIPlanResponse response = subscriptionService.createPurchase(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}/purchases/active")
    public ResponseEntity<ApiResponse<List<UserPlanPurchase>>> getUserActivePurchases(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getUserActivePurchases(userId)));
    }

    @GetMapping("/users/{userId}/ai-usage")
    public ResponseEntity<ApiResponse<List<AiUsageRecord>>> getUserAIUsage(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getUserAIUsage(userId, date)));
    }

    // -------- Admin endpoints --------

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlan>>> getAllPlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getAllPlans()));
    }

    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> createPlan(
            @RequestBody CreatePlanRequest request) {
        SubscriptionPlan plan = subscriptionService.createPlan(request);
        return ResponseEntity.ok(ApiResponse.success(plan));
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> updatePlan(
            @PathVariable UUID planId,
            @RequestBody UpdatePlanRequest request) {
        SubscriptionPlan updated = subscriptionService.updatePlan(planId, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PatchMapping("/user-purchases/{purchaseId}")
    public ResponseEntity<ApiResponse<UserPlanPurchase>> updateUserPurchase(
            @PathVariable UUID purchaseId,
            @RequestBody UpdateUserPurchaseRequest request) {
        UserPlanPurchase updated = subscriptionService.updateUserPurchase(purchaseId, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
