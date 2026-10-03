package com.exe.astratarot.controller;

import com.exe.astratarot.config.TestSecurityConfig;
import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.wallet.TopupRequest;
import com.exe.astratarot.domain.dto.wallet.UserWalletResponse;
import com.exe.astratarot.domain.dto.wallet.WalletTransactionResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.WalletTransactionType;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.security.JwtService;
import com.exe.astratarot.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WalletService walletService;

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

    @Test
    @DisplayName("GET /api/v1/wallet/me - Lấy thông tin số dư ví thành công")
    void getMyWallet_Authenticated_Returns200() throws Exception {
        UserWalletResponse walletResponse = UserWalletResponse.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .balance(150000L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(walletService.getWalletResponse(TEST_USER_ID)).thenReturn(walletResponse);

        mockMvc.perform(get("/api/v1/wallet/me")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(150000))
                .andExpect(jsonPath("$.data.userId").value(TEST_USER_ID.toString()));
    }

    @Test
    @DisplayName("POST /api/v1/wallet/topup - Tạo yêu cầu nạp tiền trả về VietQR")
    void createTopup_ValidAmount_ReturnsPaymentInstruction() throws Exception {
        TopupRequest request = new TopupRequest(50000L);

        PaymentInstructionResponse instruction = PaymentInstructionResponse.builder()
                .transactionId(UUID.randomUUID())
                .amount(50000L)
                .paymentMethod("PAYOS")
                .paymentPhase("TOPUP")
                .referenceCode("123456789")
                .qrCode("vietqr-payload")
                .checkoutUrl("https://payos.vn/checkout")
                .status("PENDING")
                .build();

        when(walletService.createTopupIntent(eq(TEST_USER_ID), eq(50000L))).thenReturn(instruction);

        mockMvc.perform(post("/api/v1/wallet/topup")
                        .header("Authorization", "Bearer " + TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(50000))
                .andExpect(jsonPath("$.data.paymentMethod").value("PAYOS"))
                .andExpect(jsonPath("$.data.paymentPhase").value("TOPUP"));
    }

    @Test
    @DisplayName("GET /api/v1/wallet/transactions - Lấy lịch sử biến động số dư ví")
    void getTransactions_Authenticated_ReturnsPage() throws Exception {
        WalletTransactionResponse tx = WalletTransactionResponse.builder()
                .id(UUID.randomUUID())
                .type(WalletTransactionType.TOPUP)
                .amount(50000L)
                .balanceBefore(0L)
                .balanceAfter(50000L)
                .status(TransactionStatus.SUCCESS)
                .description("Nạp tiền vào ví")
                .createdAt(Instant.now())
                .build();

        when(walletService.getTransactions(eq(TEST_USER_ID), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(tx)));

        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .header("Authorization", "Bearer " + TEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].amount").value(50000))
                .andExpect(jsonPath("$.data.content[0].type").value("TOPUP"))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(50000));
    }
}
