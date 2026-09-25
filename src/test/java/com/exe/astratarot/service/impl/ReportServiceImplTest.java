package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.report.CreateReportRequest;
import com.exe.astratarot.domain.dto.report.HandleReportRequest;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.Report;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.ReportStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.ReportRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tố cáo vi phạm và phạt tiền. Lớp này trước đây có độ phủ 1,1%.
 *
 * <p>Bất biến đắt nhất ở đây: <b>chỉ trừ tiền khi kết luận là RESOLVED.</b>
 * "REVIEWED" mới là đã xem, "REJECTED" là bác đơn — phạt tiền ở hai trạng
 * thái đó là lấy tiền của người chưa bị kết luận sai, và đó là loại lỗi
 * không sửa lại được bằng một bản vá.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private NotificationService notificationService;
    @Mock private ActivityLogService activityLogService;
    @Mock private EscrowService escrowService;

    private ReportServiceImpl service;

    private User nguoiToCao;
    private User nguoiBiToCao;
    private User quanTri;
    private final UUID reportId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(reportRepository, userRepository,
                bookingRepository, notificationService, activityLogService,
                escrowService);

        nguoiToCao = ai("Người tố");
        nguoiBiToCao = ai("Người bị tố");
        quanTri = ai("Quản trị");

        lenient().when(reportRepository.save(any(Report.class)))
                .thenAnswer(inv -> {
                    Report r = inv.getArgument(0);
                    if (r.getId() == null) r.setId(reportId);
                    return r;
                });
    }

    private User ai(String ten) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFullName(ten);
        lenient().when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private CreateReportRequest yeuCau(UUID biToCao, UUID bookingId) {
        CreateReportRequest r = new CreateReportRequest();
        r.setReportedUserId(biToCao);
        r.setReportType("  QUAY_ROI  ");
        r.setDescription("  Nội dung  ");
        r.setBookingId(bookingId);
        return r;
    }

    private Report baoCao(ReportStatus tt) {
        Report r = Report.builder()
                .id(reportId)
                .reporterUser(nguoiToCao)
                .reportedUser(nguoiBiToCao)
                .reportType("QUAY_ROI")
                .status(tt)
                .build();
        lenient().when(reportRepository.findByIdWithParties(reportId))
                .thenReturn(Optional.of(r));
        return r;
    }

    private HandleReportRequest ketLuan(String tt, Long phat) {
        HandleReportRequest r = new HandleReportRequest();
        r.setStatus(tt);
        r.setResolutionNote("  Kết luận  ");
        r.setPenaltyAmount(phat);
        return r;
    }

    // =====================================================================

    @Test
    @DisplayName("Không tự báo cáo chính mình được")
    void khongTuToCaoChinhMinh() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(nguoiToCao.getId(),
                        yeuCau(nguoiToCao.getId(), null)));
    }

    @Test
    @DisplayName("Đã có đơn đang chờ với cùng người thì không gửi thêm")
    void khongToCaoTrungLap() {
        when(reportRepository.existsByReporterUserIdAndReportedUserIdAndStatus(
                nguoiToCao.getId(), nguoiBiToCao.getId(), ReportStatus.PENDING))
                .thenReturn(true);

        // Tố cùng một người nhiều lần khi việc cũ chưa xong chỉ làm hàng chờ
        // phình ra mà không thêm thông tin gì.
        assertThrows(IllegalArgumentException.class,
                () -> service.create(nguoiToCao.getId(),
                        yeuCau(nguoiBiToCao.getId(), null)));
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chỉ gắn được lịch hẹn mà mình có liên quan")
    void khongGanDuocBookingCuaNguoiLa() {
        UUID bookingId = UUID.randomUUID();
        User nguoiLa = ai("Người lạ");
        User readerLa = ai("Reader lạ");

        ReaderProfile hoSo = new ReaderProfile();
        hoSo.setUser(readerLa);
        Booking b = new Booking();
        b.setId(bookingId);
        b.setUser(nguoiLa);
        b.setReaderProfile(hoSo);
        when(bookingRepository.findByIdWithParties(bookingId))
                .thenReturn(Optional.of(b));

        // Không chặn thì ai cũng đính kèm booking của người lạ để lấy cớ.
        assertThrows(AccessDeniedException.class,
                () -> service.create(nguoiToCao.getId(),
                        yeuCau(nguoiBiToCao.getId(), bookingId)));
    }

    @Test
    @DisplayName("Tạo đơn: trạng thái chờ, cắt khoảng trắng, KHÔNG báo người bị tố")
    void taoDon() {
        var kq = service.create(nguoiToCao.getId(), yeuCau(nguoiBiToCao.getId(), null));

        assertAll(
                // ReportResponse trả trạng thái dạng CHUỖI, không phải enum.
                () -> assertEquals(ReportStatus.PENDING.name(), kq.getStatus()),
                () -> assertEquals("QUAY_ROI", kq.getReportType()),
                // Người bị tố chỉ biết khi có kết luận, và không biết ai đã tố.
                // Báo ngay lúc này là mở đường cho trả đũa.
                () -> verify(notificationService, never())
                        .push(any(), anyString(), anyString(), anyString(), any()));
    }

    @Test
    @DisplayName("Đơn đã có kết luận thì không xử lại")
    void khongXuLai() {
        baoCao(ReportStatus.RESOLVED);

        assertThrows(IllegalArgumentException.class,
                () -> service.handle(quanTri.getId(), reportId,
                        ketLuan("REJECTED", null)));
    }

    @Test
    @DisplayName("Kết luận không hợp lệ thì từ chối")
    void ketLuanKhongHopLe() {
        baoCao(ReportStatus.PENDING);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.handle(quanTri.getId(), reportId,
                                ketLuan("KHONG_BIET", null))),
                // PENDING không phải một kết luận — để lọt thì đơn quay về
                // hàng chờ mà nhật ký lại ghi là "đã xử".
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.handle(quanTri.getId(), reportId,
                                ketLuan("PENDING", null))));
    }

    @Test
    @DisplayName("REJECTED kèm tiền phạt thì KHÔNG trừ đồng nào")
    void bacDonThiKhongPhat() {
        Report r = baoCao(ReportStatus.PENDING);

        service.handle(quanTri.getId(), reportId, ketLuan("REJECTED", 500_000L));

        // Bác đơn tố cáo mà vẫn trừ tiền là lấy tiền của người vừa được minh
        // oan. Đây là bất biến đắt nhất của lớp này.
        assertAll(
                () -> assertEquals(ReportStatus.REJECTED, r.getStatus()),
                () -> verify(escrowService, never()).applyPenalty(any(), anyLong(), any()),
                // penaltyAmount mặc định là 0, không phải null — kiểm bằng 0
                // mới đúng ý "không bị phạt đồng nào".
                () -> assertEquals(0L, r.getPenaltyAmount()));
    }

    @Test
    @DisplayName("REVIEWED kèm tiền phạt cũng KHÔNG trừ — mới chỉ là đã xem")
    void daXemThiChuaPhat() {
        Report r = baoCao(ReportStatus.PENDING);

        service.handle(quanTri.getId(), reportId, ketLuan("REVIEWED", 500_000L));

        assertAll(
                () -> assertEquals(ReportStatus.REVIEWED, r.getStatus()),
                () -> verify(escrowService, never()).applyPenalty(any(), anyLong(), any()));
    }

    @Test
    @DisplayName("RESOLVED có phạt: trừ tiền và BÁO cho người bị phạt")
    void xacNhanViPhamThiPhat() {
        Report r = baoCao(ReportStatus.PENDING);
        when(escrowService.applyPenalty(eq(nguoiBiToCao.getId()), eq(500_000L), any()))
                .thenReturn(500_000L);

        service.handle(quanTri.getId(), reportId, ketLuan("RESOLVED", 500_000L));

        assertAll(
                () -> assertEquals(ReportStatus.RESOLVED, r.getStatus()),
                () -> assertEquals(500_000L, r.getPenaltyAmount()),
                () -> assertEquals("Kết luận", r.getResolutionNote()),
                () -> assertNotNull(r.getHandledAt()),
                // Trừ tiền của ai đó mà không nói với họ là cách nhanh nhất để
                // biến một hình phạt đúng thành một tranh cãi.
                () -> verify(notificationService).push(eq(nguoiBiToCao), anyString(),
                        anyString(), anyString(), any()));
    }

    @Test
    @DisplayName("RESOLVED không kèm tiền phạt thì không đụng tới ký quỹ")
    void xacNhanNhungKhongPhat() {
        Report r = baoCao(ReportStatus.PENDING);

        service.handle(quanTri.getId(), reportId, ketLuan("RESOLVED", 0L));

        assertAll(
                () -> assertEquals(ReportStatus.RESOLVED, r.getStatus()),
                () -> verify(escrowService, never()).applyPenalty(any(), anyLong(), any()));
    }

    @Test
    @DisplayName("Số dư không đủ: báo rõ phần còn thiếu sẽ trừ vào thu nhập sau")
    void soDuKhongDu() {
        baoCao(ReportStatus.PENDING);
        // Trừ được 200k trên 500k.
        when(escrowService.applyPenalty(eq(nguoiBiToCao.getId()), eq(500_000L), any()))
                .thenReturn(200_000L);

        service.handle(quanTri.getId(), reportId, ketLuan("RESOLVED", 500_000L));

        // Nói "đã trừ 500k" trong khi mới trừ được 200k là sai sự thật, và
        // người bị phạt sẽ bất ngờ khi khoản thu tiếp theo cũng bị trừ.
        verify(notificationService).push(eq(nguoiBiToCao), anyString(), anyString(),
                org.mockito.ArgumentMatchers.contains("300000"), any());
    }

    @Test
    @DisplayName("Báo cáo không tồn tại thì báo đúng loại lỗi")
    void baoCaoKhongTonTai() {
        when(reportRepository.findByIdWithParties(reportId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.handle(quanTri.getId(), reportId,
                        ketLuan("RESOLVED", null)));
    }
}
