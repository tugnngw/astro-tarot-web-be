package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.astrology.AstrologyProfileDTO;
import com.exe.astratarot.domain.dto.astrology.CreateAstrologyProfileRequest;
import com.exe.astratarot.domain.dto.astrology.UpdateAstrologyProfileRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.AstrologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cửa HTTP của hồ sơ chiêm tinh. Lớp này trước đây phủ 2,8%.
 *
 * <p>Điểm đáng kiểm là <b>mã trạng thái</b>, vì ở đây chúng mang nghĩa khác
 * nhau và giao diện xử lý khác nhau:
 *
 * <ul>
 *   <li><b>201</b> khi tạo — để giao diện biết đã có bản ghi mới chứ không phải
 *       vừa đọc lại bản cũ.
 *   <li><b>404 cho hồ sơ chính</b> khi chưa có — người dùng mới chưa khai ngày
 *       sinh, và giao diện dựa vào đúng mã này để hiện lời mời tạo hồ sơ.
 *   <li><b>404 thay vì 500</b> khi sửa hay xoá một hồ sơ không thuộc về mình.
 *       Trả 403 sẽ xác nhận rằng hồ sơ ấy tồn tại; 404 thì không nói gì cả.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AstrologyControllerTest {

    @Mock private AstrologyService astrologyService;

    private AstrologyController controller;

    private User nguoiDung;
    private CustomUserDetails toi;

    @BeforeEach
    void setUp() {
        controller = new AstrologyController(astrologyService);

        nguoiDung = new User();
        nguoiDung.setId(UUID.randomUUID());
        nguoiDung.setEmail("khach@example.com");
        nguoiDung.setPasswordHash("x");
        nguoiDung.setRole(UserRole.USER);
        toi = new CustomUserDetails(nguoiDung);

        lenient().when(astrologyService.getProfiles(any())).thenReturn(List.of());
        lenient().when(astrologyService.getPrimaryProfile(any())).thenReturn(Optional.empty());
    }

    private AstrologyProfileDTO hoSo() {
        return AstrologyProfileDTO.builder().id(UUID.randomUUID()).title("Bản đồ sao").build();
    }

    // =====================================================================

    @Test
    @DisplayName("Tạo hồ sơ trả 201, không phải 200")
    void taoHoSoTra201() {
        CreateAstrologyProfileRequest r = CreateAstrologyProfileRequest.builder().build();
        AstrologyProfileDTO tao = hoSo();
        when(astrologyService.createProfile(any(), any())).thenReturn(tao);

        var res = controller.createProfile(toi, r);

        // 201 cho giao diện biết đã có bản ghi MỚI, không phải vừa đọc lại bản
        // cũ — nó quyết định có chuyển màn hình hay không.
        assertAll(
                () -> assertEquals(HttpStatus.CREATED, res.getStatusCode()),
                () -> assertEquals(tao, res.getBody().getData()),
                () -> assertTrue(res.getBody().isSuccess()));
        verify(astrologyService).createProfile(eq(nguoiDung.getId()), eq(r));
    }

    @Test
    @DisplayName("Danh sách hồ sơ lấy id từ token, chưa có thì rỗng chứ không lỗi")
    void danhSachHoSo() {
        var res = controller.getProfiles(toi);

        assertAll(
                () -> assertEquals(200, res.getStatusCode().value()),
                () -> assertTrue(res.getBody().getData().isEmpty()));
        verify(astrologyService).getProfiles(nguoiDung.getId());
    }

    @Test
    @DisplayName("Chưa có hồ sơ CHÍNH thì trả 404 — giao diện dựa vào đó để mời tạo")
    void chuaCoHoSoChinh() {
        var res = controller.getPrimaryProfile(toi);

        // Người dùng mới chưa khai ngày sinh. Trả 200 kèm data rỗng thì giao
        // diện không phân biệt được "chưa có" với "có nhưng trống".
        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode()),
                () -> assertFalse(res.getBody().isSuccess()),
                () -> assertNull(res.getBody().getData()));
    }

    @Test
    @DisplayName("Có hồ sơ chính thì trả 200 kèm hồ sơ")
    void coHoSoChinh() {
        AstrologyProfileDTO chinh = hoSo();
        when(astrologyService.getPrimaryProfile(nguoiDung.getId())).thenReturn(Optional.of(chinh));

        var res = controller.getPrimaryProfile(toi);

        assertAll(
                () -> assertEquals(200, res.getStatusCode().value()),
                () -> assertEquals(chinh, res.getBody().getData()));
    }

    @Test
    @DisplayName("Sửa hồ sơ gắn đúng người và đúng hồ sơ")
    void suaHoSo() {
        UUID hoSoId = UUID.randomUUID();
        UpdateAstrologyProfileRequest r = UpdateAstrologyProfileRequest.builder().build();
        when(astrologyService.updateProfile(any(), any(), any())).thenReturn(hoSo());

        var res = controller.updateProfile(toi, hoSoId, r);

        assertEquals(200, res.getStatusCode().value());
        // userId đi kèm profileId xuống service: đó là hàng rào chặn việc sửa
        // hồ sơ của người khác, và nó nằm ở tầng truy vấn.
        verify(astrologyService).updateProfile(eq(nguoiDung.getId()), eq(hoSoId), eq(r));
    }

    @Test
    @DisplayName("Sửa hồ sơ KHÔNG thuộc về mình trả 404, không trả 403")
    void suaHoSoCuaNguoiKhac() {
        UUID hoSoId = UUID.randomUUID();
        when(astrologyService.updateProfile(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Profile not found"));

        var res = controller.updateProfile(toi, hoSoId,
                UpdateAstrologyProfileRequest.builder().build());

        // 403 sẽ xác nhận rằng hồ sơ ấy tồn tại — một cách dò xem người khác có
        // hồ sơ hay không. 404 thì không nói gì cả.
        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode()),
                () -> assertFalse(res.getBody().isSuccess()),
                () -> assertEquals("Profile not found", res.getBody().getMessage()));
    }

    @Test
    @DisplayName("Xoá hồ sơ trả 200 và không kèm dữ liệu")
    void xoaHoSo() {
        UUID hoSoId = UUID.randomUUID();

        var res = controller.deleteProfile(toi, hoSoId);

        assertAll(
                () -> assertEquals(200, res.getStatusCode().value()),
                () -> assertTrue(res.getBody().isSuccess()),
                () -> assertNull(res.getBody().getData()));
        verify(astrologyService).deleteProfile(nguoiDung.getId(), hoSoId);
    }

    @Test
    @DisplayName("Xoá hồ sơ không thuộc về mình trả 404")
    void xoaHoSoCuaNguoiKhac() {
        UUID hoSoId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Profile not found"))
                .when(astrologyService).deleteProfile(any(), any());

        var res = controller.deleteProfile(toi, hoSoId);

        assertAll(
                () -> assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode()),
                () -> assertFalse(res.getBody().isSuccess()));
    }
}
