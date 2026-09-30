package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.ai.*;
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
    public ResponseEntity<List<SubscriptionPlan>> getActivePlans() {
        return ResponseEntity.ok(subscriptionService.getAllActivePlans());
    }

    @PostMapping("/purchase")
    public ResponseEntity<AIPlanResponse> createPurchase(
            @RequestBody CreatePurchaseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication auth) {
        UUID userId = resolveUserId(userDetails, auth);
        AIPlanResponse response = subscriptionService.createPurchase(userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}/purchases/active")
    public ResponseEntity<List<UserPlanPurchase>> getUserActivePurchases(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication auth) {
        return ResponseEntity.ok(subscriptionService.getUserActivePurchases(userId));
    }

    @GetMapping("/users/{userId}/ai-usage")
    public ResponseEntity<List<AiUsageRecord>> getUserAIUsage(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication auth) {
        return ResponseEntity.ok(subscriptionService.getUserAIUsage(userId, date));
    }

    // -------- Admin endpoints --------

    @PostMapping("/plans")
    public ResponseEntity<SubscriptionPlan> createPlan(
            @RequestBody CreatePlanRequest request) {
        SubscriptionPlan plan = subscriptionService.createPlan(request);
        return ResponseEntity.ok(plan);
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<SubscriptionPlan> updatePlan(
            @PathVariable UUID planId,
            @RequestBody UpdatePlanRequest request) {
        SubscriptionPlan updated = subscriptionService.updatePlan(planId, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/user-purchases/{purchaseId}")
    public ResponseEntity<UserPlanPurchase> updateUserPurchase(
            @PathVariable UUID purchaseId,
            @RequestBody UpdateUserPurchaseRequest request) {
        UserPlanPurchase updated = subscriptionService.updateUserPurchase(purchaseId, request);
        return ResponseEntity.ok(updated);
    }
}
