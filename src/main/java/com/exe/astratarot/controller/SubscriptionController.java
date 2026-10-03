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
import org.springframework.security.access.prepost.PreAuthorize;
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

    // Gói đang mở bán thì ai cũng xem được, kể cả khách chưa đăng nhập.
    @PreAuthorize("permitAll()")
    @GetMapping("/plans/active")
    public ResponseEntity<ApiResponse<List<SubscriptionPlan>>> getActivePlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getAllActivePlans()));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/purchase")
    public ResponseEntity<ApiResponse<AIPlanResponse>> createPurchase(
            @RequestBody CreatePurchaseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication auth) {
        UUID userId = resolveUserId(userDetails, auth);
        AIPlanResponse response = subscriptionService.createPurchase(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Chỉ xem được của CHÍNH MÌNH. userId nằm trên đường dẫn, nên không có
    // dòng này thì đổi một chữ trong URL là đọc được dữ liệu người khác.
    @PreAuthorize("#userId == authentication.principal.user.id or hasAuthority('USERS_MANAGE')")
    @GetMapping("/users/{userId}/purchases/active")
    public ResponseEntity<ApiResponse<List<UserPlanPurchase>>> getUserActivePurchases(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getUserActivePurchases(userId)));
    }

    // Chỉ xem được của CHÍNH MÌNH. userId nằm trên đường dẫn, nên không có
    // dòng này thì đổi một chữ trong URL là đọc được dữ liệu người khác.
    @PreAuthorize("#userId == authentication.principal.user.id or hasAuthority('USERS_MANAGE')")
    @GetMapping("/users/{userId}/ai-usage")
    public ResponseEntity<ApiResponse<List<AiUsageRecord>>> getUserAIUsage(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getUserAIUsage(userId, date)));
    }

    // -------- Admin endpoints --------

    @PreAuthorize("hasAuthority('PAYMENTS_MANAGE')")
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlan>>> getAllPlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getAllPlans()));
    }

    @PreAuthorize("hasAuthority('PAYMENTS_MANAGE')")
    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> createPlan(
            @RequestBody CreatePlanRequest request) {
        SubscriptionPlan plan = subscriptionService.createPlan(request);
        return ResponseEntity.ok(ApiResponse.success(plan));
    }

    @PreAuthorize("hasAuthority('PAYMENTS_MANAGE')")
    @PutMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> updatePlan(
            @PathVariable UUID planId,
            @RequestBody UpdatePlanRequest request) {
        SubscriptionPlan updated = subscriptionService.updatePlan(planId, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PreAuthorize("hasAuthority('PAYMENTS_MANAGE')")
    @PatchMapping("/user-purchases/{purchaseId}")
    public ResponseEntity<ApiResponse<UserPlanPurchase>> updateUserPurchase(
            @PathVariable UUID purchaseId,
            @RequestBody UpdateUserPurchaseRequest request) {
        UserPlanPurchase updated = subscriptionService.updateUserPurchase(purchaseId, request);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
