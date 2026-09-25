package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.domain.dto.reader.AddUnavailableDateRequest;
import com.exe.astratarot.domain.dto.reader.ApplyReaderRequest;
import com.exe.astratarot.domain.dto.reader.CreateAvailabilityRequest;
import com.exe.astratarot.domain.dto.reader.ReaderApplicationResponse;
import com.exe.astratarot.domain.dto.reader.ReaderProfileResponse;
import com.exe.astratarot.domain.dto.reader.UpdateProfileRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.ReaderAvailabilityService;
import com.exe.astratarot.service.ReaderProfileService;
import com.exe.astratarot.service.ReaderService;
import com.exe.astratarot.service.ReaderUnavailableDateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cửa HTTP của khu Reader. Lớp này trước đây phủ 0,0%.
 *
 * <p>Điểm đáng kiểm nhất là <b>câu trả lời sau khi nộp đơn</b>. Nhân viên có hồ
 * sơ ngay, người ngoài vào hàng chờ duyệt. Trả cùng một câu cho cả hai là để
 * nhân viên ngồi đợi một lần duyệt không bao giờ tới — họ không làm gì sai, chỉ
 * là app nói sai với họ.
 *
 * <p>Điểm thứ hai: <b>chưa nộp đơn lần nào trả data rỗng, không trả 404</b>.
 * Đó là trạng thái bình thường của mọi tài khoản mới; coi là lỗi thì giao diện
 * hiện màn hình hỏng cho đúng những người chưa làm gì cả.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReaderControllerTest {

    @Mock private ReaderService readerService;
    @Mock private ReaderProfileService readerProfileService;
    @Mock private ReaderAvailabilityService readerAvailabilityService;
    @Mock private ReaderUnavailableDateService readerUnavailableDateService;

    private ReaderController controller;

    private User nguoiDung;
    private CustomUserDetails toi;

    @BeforeEach
    void setUp() {
        controller = new ReaderController(readerService, readerProfileService,
                readerAvailabilityService, readerUnavailableDateService);

        nguoiDung = new User();
        nguoiDung.setId(UUID.randomUUID());
        nguoiDung.setEmail("reader@example.com");
        nguoiDung.setPasswordHash("x");
        nguoiDung.setRole(UserRole.USER);
        toi = new CustomUserDetails(nguoiDung);

        lenient().when(readerService.myApplication(any())).thenReturn(Optional.empty());
        lenient().when(readerProfileService.getMyProfile(any()))
                .thenReturn(ApiResponse.success(ReaderProfileResponse.builder().build()));
        lenient().when(readerProfileService.getAllVerifiedReaders())
                .thenReturn(ApiResponse.success(List.of()));
        lenient().when(readerProfileService.getReaderById(any()))
                .thenReturn(ApiResponse.success(ReaderProfileResponse.builder().build()));
        lenient().when(readerAvailabilityService.getWeeklySchedule(any()))
                .thenReturn(ApiResponse.success(List.of()));
        lenient().when(readerUnavailableDateService.getUnavailableDates(any()))
                .thenReturn(ApiResponse.success(List.of()));
    }

    // =====================================================================

    @Test
    @DisplayName("Nhân viên nộp đơn: báo ĐÃ CÓ hồ sơ, không bảo chờ duyệt")
    void nhanVienNopDon() {
        when(readerService.apply(any(), any()))
                .thenReturn(ReaderService.ApplyOutcome.APPROVED_IMMEDIATELY);

        var res = controller.apply(toi, new ApplyReaderRequest());

        // Bảo nhân viên chờ duyệt là để họ ngồi đợi một lần duyệt không bao giờ
        // tới. Họ không làm gì sai — chỉ là app nói sai với họ.
        assertAll(
                () -> assertTrue(res.getBody().getMessage().contains("Đã tạo hồ sơ Reader")),
                () -> assertTrue(res.getBody().getMessage().contains("khung giờ rảnh")),
                () -> assertEquals("APPROVED_IMMEDIATELY", res.getBody().getData()));
    }

    @Test
    @DisplayName("Người ngoài nộp đơn: báo ĐÃ GỬI, sẽ có người duyệt")
    void nguoiNgoaiNopDon() {
        when(readerService.apply(any(), any())).thenReturn(ReaderService.ApplyOutcome.SUBMITTED);

        var res = controller.apply(toi, new ApplyReaderRequest());

        assertAll(
                () -> assertTrue(res.getBody().getMessage().contains("Đã gửi hồ sơ")),
                () -> assertTrue(res.getBody().getMessage().contains("duyệt")),
                () -> assertEquals("SUBMITTED", res.getBody().getData()));
    }

    @Test
    @DisplayName("Nộp đơn gắn đúng người từ token, không nhận id từ ngoài")
    void nopDonGanDungNguoi() {
        ApplyReaderRequest r = new ApplyReaderRequest();
        when(readerService.apply(any(), any())).thenReturn(ReaderService.ApplyOutcome.SUBMITTED);

        controller.apply(toi, r);

        verify(readerService).apply(eq(nguoiDung.getId()), eq(r));
    }

    @Test
    @DisplayName("Chưa nộp đơn lần nào thì data RỖNG, không phải lỗi 404")
    void chuaNopDonLanNao() {
        var res = controller.myApplication(toi);

        // Đây là trạng thái bình thường của mọi tài khoản mới. Coi là lỗi thì
        // giao diện hiện màn hình hỏng cho đúng những người chưa làm gì cả.
        assertAll(
                () -> assertEquals(200, res.getStatusCode().value()),
                () -> assertTrue(res.getBody().isSuccess()),
                () -> assertNull(res.getBody().getData()));
    }

    @Test
    @DisplayName("Đã nộp đơn thì trả đúng đơn của CHÍNH MÌNH")
    void daNopDon() {
        ReaderApplicationResponse don = ReaderApplicationResponse.builder().build();
        when(readerService.myApplication(nguoiDung.getId())).thenReturn(Optional.of(don));

        assertEquals(don, controller.myApplication(toi).getBody().getData());
        verify(readerService).myApplication(nguoiDung.getId());
    }

    @Test
    @DisplayName("Sửa hồ sơ Reader gắn đúng người")
    void suaHoSo() {
        UpdateProfileRequest r = new UpdateProfileRequest();

        var res = controller.updateProfile(toi, r);

        assertAll(
                () -> assertEquals(200, res.getStatusCode().value()),
                () -> verify(readerProfileService).updateProfile(eq(nguoiDung.getId()), eq(r)));
    }

    @Test
    @DisplayName("Xem hồ sơ của mình lấy id từ token")
    void xemHoSoCuaMinh() {
        controller.getMyProfile(toi);

        // Nhận id từ tham số là cho bất kỳ ai xem hồ sơ nội bộ của Reader khác,
        // gồm cả giá và lịch làm việc chưa công bố.
        verify(readerProfileService).getMyProfile(nguoiDung.getId());
    }

    @Test
    @DisplayName("Hai endpoint công khai không cần người đăng nhập")
    void endpointCongKhai() {
        UUID readerId = UUID.randomUUID();

        controller.getAllVerifiedReaders();
        controller.getReaderById(readerId);

        // Khách chưa đăng nhập phải xem được danh sách Reader — đó là cửa vào
        // của cả sản phẩm.
        assertAll(
                () -> verify(readerProfileService).getAllVerifiedReaders(),
                () -> verify(readerProfileService).getReaderById(readerId));
    }

    @Test
    @DisplayName("Ba thao tác lịch làm việc đều gắn đúng người")
    void thaoTacLichLamViec() {
        CreateAvailabilityRequest r = new CreateAvailabilityRequest();
        UUID khungId = UUID.randomUUID();

        controller.createAvailability(toi, r);
        controller.getWeeklySchedule(toi);
        controller.deleteAvailability(toi, khungId);

        assertAll(
                () -> verify(readerAvailabilityService).create(eq(nguoiDung.getId()), eq(r)),
                () -> verify(readerAvailabilityService).getWeeklySchedule(nguoiDung.getId()),
                // Truyền id người thao tác xuống service là hàng rào chặn việc
                // xoá khung giờ của Reader khác.
                () -> verify(readerAvailabilityService).delete(nguoiDung.getId(), khungId));
    }

    @Test
    @DisplayName("Ba thao tác ngày nghỉ đều gắn đúng người")
    void thaoTacNgayNghi() {
        AddUnavailableDateRequest r = new AddUnavailableDateRequest();
        UUID ngayId = UUID.randomUUID();

        controller.addUnavailableDate(toi, r);
        controller.getUnavailableDates(toi);
        controller.removeUnavailableDate(toi, ngayId);

        assertAll(
                () -> verify(readerUnavailableDateService).addDate(eq(nguoiDung.getId()), eq(r)),
                () -> verify(readerUnavailableDateService).getUnavailableDates(nguoiDung.getId()),
                () -> verify(readerUnavailableDateService).removeDate(nguoiDung.getId(), ngayId));
    }
}
