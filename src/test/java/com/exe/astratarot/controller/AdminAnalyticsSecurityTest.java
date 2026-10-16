package com.exe.astratarot.controller;

import com.exe.astratarot.config.TestSecurityConfig;
import com.exe.astratarot.domain.dto.analytics.GA4OverviewDto;
import com.exe.astratarot.domain.dto.analytics.GA4ReportResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.security.JwtService;
import com.exe.astratarot.service.analytics.GA4AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class AdminAnalyticsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GA4AnalyticsService ga4AnalyticsService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private static final String TOKEN = "valid-token";
    private static final String USERNAME = "user_subject";

    @BeforeEach
    void setUp() {
        when(jwtService.extractSubjectSafely(TOKEN)).thenReturn(USERNAME);
        when(jwtService.validateToken(eq(TOKEN), any(CustomUserDetails.class))).thenReturn(true);
    }

    private void authenticateAs(UserRole role) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(USERNAME)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(new CustomUserDetails(user));
    }

    @Test
    @DisplayName("Chưa đăng nhập -> Trả về 401 Unauthorized")
    void unauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics/report"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đăng nhập với vai trò USER -> Bị chặn 403 vì thiếu USERS_MANAGE / AUDIT_VIEW")
    void userRole_returns403() throws Exception {
        authenticateAs(UserRole.USER);

        mockMvc.perform(get("/api/v1/admin/analytics/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Đăng nhập với vai trò STAFF -> Bị chặn 403 vì thiếu USERS_MANAGE / AUDIT_VIEW")
    void staffRole_returns403() throws Exception {
        authenticateAs(UserRole.STAFF);

        mockMvc.perform(get("/api/v1/admin/analytics/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Đăng nhập với vai trò ADMIN -> Cho phép 200 OK vì có USERS_MANAGE và AUDIT_VIEW")
    void adminRole_returns200() throws Exception {
        authenticateAs(UserRole.ADMIN);

        GA4ReportResponse response = new GA4ReportResponse(
                true,
                "559341552",
                "30daysAgo",
                "today",
                new GA4OverviewDto(100L, 80L, 120L, 300L, 0.25, 90.0),
                List.of(),
                List.of(),
                "SUCCESS",
                "OK"
        );
        when(ga4AnalyticsService.getReport("30daysAgo", "today")).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/analytics/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.propertyId").value("559341552"))
                .andExpect(jsonPath("$.data.overview.activeUsers").value(100));
    }
}
