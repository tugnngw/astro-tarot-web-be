package com.exe.astratarot.controller;

import com.exe.astratarot.config.TestSecurityConfig;
import com.exe.astratarot.domain.dto.ai.*;
import com.exe.astratarot.domain.entity.SubscriptionPlan;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserPlanPurchase;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.security.JwtService;
import com.exe.astratarot.service.SubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static final String TEST_TOKEN = "test-jwt-token";
    private static final String TEST_USERNAME = "testuser";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);

        User mockUser = User.builder()
                .id(TEST_USER_ID)
                .username(TEST_USERNAME)
                .role(UserRole.USER)
                .build();
        CustomUserDetails customUserDetails = new CustomUserDetails(mockUser);

        when(jwtService.extractSubjectSafely(TEST_TOKEN)).thenReturn(TEST_USERNAME);
        when(jwtService.validateToken(eq(TEST_TOKEN), any(CustomUserDetails.class))).thenReturn(true);
        when(userDetailsService.loadUserByUsername(TEST_USERNAME)).thenReturn(customUserDetails);
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    @Test
    @DisplayName("GET /api/admin/subscriptions/plans/active - Public endpoint trả danh sách gói active")
    void getActivePlans_Success_Returns200() throws Exception {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("Basic 1 Day")
                .planType(SubscriptionPlan.PlanType.DAY_PASS)
                .dailyQuota(5)
                .price(15000L)
                .durationDays(1)
                .isActive(true)
                .build();

        when(subscriptionService.getAllActivePlans()).thenReturn(List.of(plan));

        mockMvc.perform(get("/api/admin/subscriptions/plans/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Basic 1 Day"))
                .andExpect(jsonPath("$[0].dailyQuota").value(5))
                .andExpect(jsonPath("$[0].price").value(15000));
    }

    @Test
    @DisplayName("POST /api/admin/subscriptions/purchase - Mua gói dịch vụ thành công")
    void createPurchase_Authenticated_Returns200() throws Exception {
        UUID planId = UUID.randomUUID();
        CreatePurchaseRequest request = new CreatePurchaseRequest(planId, UserPlanPurchase.PurchaseType.WALLET);

        AIPlanResponse response = AIPlanResponse.builder()
                .purchaseId(UUID.randomUUID())
                .planName("Pro Monthly")
                .dailyQuota(20)
                .price(199000L)
                .remainingDays(30)
                .isActive(true)
                .build();

        when(subscriptionService.createPurchase(eq(TEST_USER_ID), any(CreatePurchaseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/admin/subscriptions/purchase")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("Pro Monthly"))
                .andExpect(jsonPath("$.dailyQuota").value(20))
                .andExpect(jsonPath("$.price").value(199000));
    }

    @Test
    @DisplayName("GET /api/admin/subscriptions/users/{userId}/purchases/active - Lấy danh sách gói active của user")
    void getUserActivePurchases_Returns200() throws Exception {
        Instant now = Instant.now();
        UserPlanPurchase purchase = UserPlanPurchase.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .planId(UUID.randomUUID())
                .planNameSnapshot("3-Day Combo")
                .dailyQuotaSnapshot(10)
                .priceSnapshot(39000L)
                .status(UserPlanPurchase.PurchaseStatus.ACTIVE)
                .startAt(Timestamp.from(now))
                .endAt(Timestamp.from(now.plus(3, ChronoUnit.DAYS)))
                .build();

        when(subscriptionService.getUserActivePurchases(TEST_USER_ID)).thenReturn(List.of(purchase));

        mockMvc.perform(get("/api/admin/subscriptions/users/{userId}/purchases/active", TEST_USER_ID)
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].planNameSnapshot").value("3-Day Combo"))
                .andExpect(jsonPath("$[0].dailyQuotaSnapshot").value(10));
    }

    @Test
    @DisplayName("GET /api/admin/subscriptions/users/{userId}/ai-usage - Lấy usage AI của ngày")
    void getUserAIUsage_Returns200() throws Exception {
        LocalDate date = LocalDate.of(2026, 9, 30);
        AiUsageRecord record = new AiUsageRecord(TEST_USER_ID, date, 3);

        when(subscriptionService.getUserAIUsage(TEST_USER_ID, date)).thenReturn(List.of(record));

        mockMvc.perform(get("/api/admin/subscriptions/users/{userId}/ai-usage", TEST_USER_ID)
                        .param("date", "2026-09-30")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].countUsed").value(3))
                .andExpect(jsonPath("$[0].usageDate").value("2026-09-30"));
    }

    @Test
    @DisplayName("POST /api/admin/subscriptions/plans - Tạo gói mới")
    void createPlan_Returns200() throws Exception {
        CreatePlanRequest request = CreatePlanRequest.builder()
                .name("VIP 7 Days")
                .planType(SubscriptionPlan.PlanType.DAY_PASS)
                .dailyQuota(50)
                .price(79000L)
                .durationDays(7)
                .description("Gói VIP 7 ngày")
                .build();

        SubscriptionPlan created = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .name("VIP 7 Days")
                .planType(SubscriptionPlan.PlanType.DAY_PASS)
                .dailyQuota(50)
                .price(79000L)
                .durationDays(7)
                .isActive(true)
                .description("Gói VIP 7 ngày")
                .build();

        when(subscriptionService.createPlan(any(CreatePlanRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/admin/subscriptions/plans")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("VIP 7 Days"))
                .andExpect(jsonPath("$.dailyQuota").value(50));
    }
}
