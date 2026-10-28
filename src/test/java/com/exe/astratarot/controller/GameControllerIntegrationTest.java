package com.exe.astratarot.controller;

import com.exe.astratarot.config.TestSecurityConfig;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.game.Bang;
import com.exe.astratarot.game.LuatGame;
import com.exe.astratarot.game.MayNgauNhien;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.security.JwtService;
import com.exe.astratarot.service.SubscriptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chơi hết một phiên Giao Hội thật, qua controller và cơ sở dữ liệu thật.
 *
 * <h2>Vì sao cần, khi đã có bài kiểm cho bộ máy và cho tầng dịch vụ</h2>
 *
 * Hai tệp kia kiểm luật. Tệp này kiểm phần ở GIỮA luật và trình duyệt, chỗ
 * không luật nào nhìn thấy:
 *
 * <ul>
 *   <li>Tên trường trong JSON. Giao diện đọc {@code luotConLai}; đổi tên một
 *       trường trong record là giao diện nhận {@code undefined} và hiện 0 lượt
 *       — không có lỗi nào, chỉ là con số sai.</li>
 *   <li>Bàn chơi sống qua các lượt gọi HTTP riêng. Quên lưu hạt giống thì bàn
 *       vẫn đúng ở lượt đầu và chỉ lệch từ lượt thứ hai.</li>
 *   <li>Lượt thưởng có thật sự chảy vào hạn mức AI hay không. Đây là lúc duy
 *       nhất cả hai nửa cùng chạy trên một cơ sở dữ liệu.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class GameControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private SubscriptionService subscriptionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private static final String TOKEN = "valid-token";
    private static final String USERNAME = "nguoi_choi_game";

    private UUID idNguoi;

    @BeforeEach
    void setUp() {
        idNguoi = UUID.randomUUID();
        when(jwtService.extractSubjectSafely(TOKEN)).thenReturn(USERNAME);
        when(jwtService.validateToken(eq(TOKEN), any(CustomUserDetails.class))).thenReturn(true);

        User u = User.builder()
                .id(idNguoi)
                .username(USERNAME)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(new CustomUserDetails(u));
    }

    // ------------------------------------------------------------------
    // Phụ trợ
    // ------------------------------------------------------------------

    private JsonNode goiGet(String duongDan) throws Exception {
        String than = mockMvc.perform(get(duongDan)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(than).get("data");
    }

    private JsonNode goiPost(String duongDan, String than) throws Exception {
        String ra = mockMvc.perform(post(duongDan)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(than))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(ra).get("data");
    }

    /** Một cặp ô kề nhau mà đổi chỗ sẽ tạo được chuỗi trên bàn này. */
    private static int[] nuocHopLe(String bang) {
        Bang b = Bang.tu(bang, new MayNgauNhien(1L));
        for (int i = 0; i < LuatGame.SO_O; i++) {
            int cot = i % LuatGame.CANH;
            if (cot + 1 < LuatGame.CANH && b.doiChoHopLe(i, i + 1)) {
                return new int[] {i, i + 1};
            }
            if (i + LuatGame.CANH < LuatGame.SO_O
                    && b.doiChoHopLe(i, i + LuatGame.CANH)) {
                return new int[] {i, i + LuatGame.CANH};
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Bài kiểm
    // ------------------------------------------------------------------

    @Test
    @DisplayName("chưa đăng nhập thì không vào được")
    void chuaDangNhapThiChan() throws Exception {
        mockMvc.perform(get("/api/v1/game/trang-thai"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/game/phien")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nhanVat\":\"SOL\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Vai USER — vai thấp nhất — phải chơi được. Nếu một lần siết quyền về sau
     * vô tình cần thêm quyền cho nhóm đường dẫn này thì bài này đỏ, thay vì để
     * người dùng thường gặp 403 trên một trang nằm trong thanh điều hướng.
     */
    @Test
    @DisplayName("vai USER chơi được, và trạng thái trả đủ tên trường giao diện đọc")
    void vaiUserChoiDuocVaDuTruong() throws Exception {
        JsonNode tt = goiGet("/api/v1/game/trang-thai");

        for (String truong : new String[] {
                "canhBan", "luotMoiPhien", "diemMoiLuot", "tranDiemNgay",
                "tranLuotNgay", "diemDaTinhHomNay", "luotDaNhanHomNay",
                "diemConDuocTinh", "luotConDuocNhan", "cacNhanVat"}) {
            assertThat(tt.has(truong)).as("thiếu trường " + truong).isTrue();
        }
        assertThat(tt.get("canhBan").asInt()).isEqualTo(LuatGame.CANH);
        assertThat(tt.get("phienDangMo").isNull()).isTrue();
        assertThat(tt.get("cacNhanVat")).hasSize(3);

        JsonNode nv = tt.get("cacNhanVat").get(0);
        for (String truong : new String[] {
                "ma", "ten", "tenKiNang", "moTaKiNang", "nangLuongCan"}) {
            assertThat(nv.has(truong)).as("nhân vật thiếu trường " + truong).isTrue();
        }
    }

    @Test
    @DisplayName("mở phiên rồi mở lại trang thì chơi tiếp đúng bàn đang dở")
    void moLaiTrangThiChoiTiep() throws Exception {
        JsonNode sauMo = goiPost("/api/v1/game/phien", "{\"nhanVat\":\"ASTRA\"}");
        JsonNode phien = sauMo.get("phienDangMo");
        assertThat(phien.get("bang").asText()).hasSize(LuatGame.SO_O);
        assertThat(phien.get("luotConLai").asInt()).isEqualTo(LuatGame.LUOT_MOI_PHIEN);

        // Lượt gọi mới, không mang theo gì ngoài token: bàn phải từ cơ sở dữ liệu.
        JsonNode lai = goiGet("/api/v1/game/trang-thai");
        assertThat(lai.get("phienDangMo").get("id").asText())
                .isEqualTo(phien.get("id").asText());
        assertThat(lai.get("phienDangMo").get("bang").asText())
                .isEqualTo(phien.get("bang").asText());
    }

    /**
     * Nước vô nghĩa phải bị từ chối bằng 400 kèm lời giải thích, và KHÔNG tốn
     * lượt. Trả 500 ở đây là người chơi bấm nhầm hai ô rồi nhận một trang lỗi.
     */
    @Test
    @DisplayName("nước đi vô nghĩa trả 400 và không tốn lượt")
    void nuocVoNghiaTra400() throws Exception {
        JsonNode phien = goiPost("/api/v1/game/phien", "{\"nhanVat\":\"SOL\"}")
                .get("phienDangMo");
        String id = phien.get("id").asText();
        String bang = phien.get("bang").asText();

        // Tìm một cặp kề nhau KHÔNG tạo được chuỗi.
        Bang b = Bang.tu(bang, new MayNgauNhien(1L));
        int a = -1;
        for (int i = 0; i < LuatGame.SO_O && a < 0; i++) {
            if (i % LuatGame.CANH + 1 < LuatGame.CANH && !b.doiChoHopLe(i, i + 1)) a = i;
        }
        assertThat(a).as("bàn mẫu phải có ít nhất một nước vô nghĩa").isNotNegative();

        mockMvc.perform(post("/api/v1/game/phien/" + id + "/doi-cho")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"o1\":" + a + ",\"o2\":" + (a + 1) + "}"))
                .andExpect(status().isBadRequest());

        JsonNode sau = goiGet("/api/v1/game/trang-thai").get("phienDangMo");
        assertThat(sau.get("luotConLai").asInt()).isEqualTo(LuatGame.LUOT_MOI_PHIEN);
        assertThat(sau.get("bang").asText()).isEqualTo(bang);
    }

    @Test
    @DisplayName("ô ngoài bàn trả 400, không phải 500")
    void oNgoaiBanTra400() throws Exception {
        String id = goiPost("/api/v1/game/phien", "{\"nhanVat\":\"SOL\"}")
                .get("phienDangMo").get("id").asText();

        mockMvc.perform(post("/api/v1/game/phien/" + id + "/doi-cho")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"o1\":0,\"o2\":999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("nhân vật không hợp lệ trả 400")
    void nhanVatSaiTra400() throws Exception {
        mockMvc.perform(post("/api/v1/game/phien")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nhanVat\":\"GANDALF\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("không đi được trong phiên của người khác")
    void khongDiDuocPhienNguoiKhac() throws Exception {
        String id = goiPost("/api/v1/game/phien", "{\"nhanVat\":\"SOL\"}")
                .get("phienDangMo").get("id").asText();

        // Đổi sang một người khác, giữ nguyên id phiên vừa lấy được.
        User khac = User.builder()
                .id(UUID.randomUUID())
                .username(USERNAME)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(new CustomUserDetails(khac));

        mockMvc.perform(post("/api/v1/game/phien/" + id + "/doi-cho")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"o1\":0,\"o2\":1}"))
                .andExpect(status().isNotFound());
    }

    /**
     * Chơi trọn một phiên hai mươi lượt, rồi xem lượt thưởng có chảy vào hạn
     * mức AI không.
     *
     * <p>Đây là chỗ duy nhất cả hai nửa cùng chạy trên một cơ sở dữ liệu. Nối
     * sai — chẳng hạn để lượt thưởng tham gia phép max() của các gói mua thay
     * vì cộng vào — thì game vẫn báo "được 2 lượt", vẫn ghi đúng vào bảng
     * game_ngay, mà hạn mức AI không nhích một lượt.
     */
    @Test
    @DisplayName("chơi hết phiên thì được chốt, và lượt thưởng vào hạn mức AI")
    void choiHetPhienThiThuongVaoHanMuc() throws Exception {
        int hanMucTruoc = subscriptionService.getDailyQuotaForUser(idNguoi, LocalDate.now());

        JsonNode phien = goiPost("/api/v1/game/phien", "{\"nhanVat\":\"LUNA\"}")
                .get("phienDangMo");
        String id = phien.get("id").asText();
        String bang = phien.get("bang").asText();

        int conLai = phien.get("luotConLai").asInt();
        int tongDiem = 0;
        JsonNode thuong = null;

        while (conLai > 0) {
            int[] nuoc = nuocHopLe(bang);
            assertThat(nuoc).as("bàn phải luôn còn nước đi — đảo bàn đã không chạy")
                    .isNotNull();

            JsonNode kq = goiPost("/api/v1/game/phien/" + id + "/doi-cho",
                    "{\"o1\":" + nuoc[0] + ",\"o2\":" + nuoc[1] + "}");

            assertThat(kq.get("cacBuoc")).isNotEmpty();
            JsonNode buoc = kq.get("cacBuoc").get(0);
            for (String truong : new String[] {
                    "cacOXoa", "bangSauXoa", "bangSau", "diem", "heSo", "bac"}) {
                assertThat(buoc.has(truong)).as("bậc thiếu trường " + truong).isTrue();
            }
            // Giao diện dựng hoạt ảnh rơi từ bangSauXoa, nên nó phải thật sự
            // có ô rỗng. Trả hai ảnh bàn giống nhau là hoạt ảnh không có gì
            // để diễn, mà không có lỗi nào báo.
            assertThat(buoc.get("bangSauXoa").asText()).contains("0");
            assertThat(buoc.get("bangSau").asText()).doesNotContain("0");

            tongDiem += kq.get("diemThem").asInt();
            JsonNode p = kq.get("phien");
            assertThat(p.get("diem").asInt()).isEqualTo(tongDiem);
            assertThat(p.get("luotConLai").asInt()).isEqualTo(conLai - 1);

            conLai = p.get("luotConLai").asInt();
            bang = p.get("bang").asText();
            if (!kq.get("thuong").isNull()) thuong = kq.get("thuong");
        }

        assertThat(thuong).as("hết lượt thì phải có phần thưởng").isNotNull();
        assertThat(tongDiem).isPositive();

        int luotNhan = thuong.get("luotDaNhanHomNay").asInt();
        assertThat(luotNhan).isEqualTo(tongDiem >= LuatGame.DIEM_MOI_LUOT
                ? Math.min(LuatGame.TRAN_LUOT_NGAY, tongDiem / LuatGame.DIEM_MOI_LUOT)
                : 0);

        int hanMucSau = subscriptionService.getDailyQuotaForUser(idNguoi, LocalDate.now());
        assertThat(hanMucSau)
                .as("hạn mức AI phải tăng đúng số lượt vừa thưởng")
                .isEqualTo(hanMucTruoc + luotNhan);

        // Phiên đã chốt thì không đi thêm được nước nào.
        mockMvc.perform(post("/api/v1/game/phien/" + id + "/doi-cho")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"o1\":0,\"o2\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("kĩ năng chưa nạp đủ thì trả 400")
    void kiNangChuaDuTra400() throws Exception {
        String id = goiPost("/api/v1/game/phien", "{\"nhanVat\":\"LUNA\"}")
                .get("phienDangMo").get("id").asText();

        mockMvc.perform(post("/api/v1/game/phien/" + id + "/ki-nang")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"o\":24}"))
                .andExpect(status().isBadRequest());
    }
}
