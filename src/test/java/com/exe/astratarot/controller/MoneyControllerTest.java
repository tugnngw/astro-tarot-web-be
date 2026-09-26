package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.payment.RejectRequest;
import com.exe.astratarot.domain.dto.payout.CreatePayoutRequest;
import com.exe.astratarot.domain.dto.report.CreateReportRequest;
import com.exe.astratarot.domain.dto.report.HandleReportRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.service.PaymentService;
import com.exe.astratarot.service.PayoutService;
import com.exe.astratarot.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cửa HTTP của ba luồng tiền: khách trả, Reader rút, và báo cáo vi phạm. Lớp
 * này trước đây phủ 0,0%.
 *
 * <p>Controller ở đây rất mỏng, nhưng ba việc nó làm đều đáng chốt lại, vì mỗi
 * việc sai một lần là sai ở mọi endpoint:
 *
 * <ol>
 *   <li><b>ID người thao tác lấy từ token, KHÔNG lấy từ thân yêu cầu.</b> Nếu
 *       nhận từ client thì bất kỳ ai cũng gửi lên id của người khác và rút tiền
 *       của họ. Đây là lý do mọi phương thức đều nhận {@code @AuthenticationPrincipal}.
 *   <li><b>Thân yêu cầu có thể vắng.</b> Từ chối không nêu lý do là hợp lệ, và
 *       {@code request.getReason()} trên null là 500 cho một thao tác đúng.
 *   <li><b>Phân trang có giá trị mặc định.</b> Không có mặc định thì thiếu
 *       tham số là 400, và giao diện phải nhớ gửi đủ ở mọi lời gọi.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MoneyControllerTest {

    @Mock private PaymentService paymentService;
    @Mock private PayoutService payoutService;
    @Mock private ReportService reportService;

    private MoneyController controller;

    private User nguoiDung;
    private CustomUserDetails toi;

    @BeforeEach
    void setUp() {
        controller = new MoneyController(paymentService, payoutService, reportService);

        nguoiDung = new User();
        nguoiDung.setId(UUID.randomUUID());
        nguoiDung.setEmail("khach@example.com");
        nguoiDung.setPasswordHash("x");
        nguoiDung.setRole(UserRole.USER);
        toi = new CustomUserDetails(nguoiDung);

        lenient().when(payoutService.myLedger(any(), any())).thenReturn(new PageImpl<>(List.of()));
        lenient().when(payoutService.listMine(any(), any())).thenReturn(new PageImpl<>(List.of()));
        lenient().when(payoutService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));
        lenient().when(paymentService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));
        lenient().when(reportService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));
    }

    private static RejectRequest lyDo(String noiDung) {
        RejectRequest r = new RejectRequest();
        r.setReason(noiDung);
        return r;
    }

    // =====================================================================

    @Nested
    @DisplayName("Khách trả tiền")
    class KhachTraTien {

        @Test
        @DisplayName("PayOS: lời nhắn bảo mở link, không bảo chuyển khoản tay")
        void payOs() {
            when(paymentService.createPaymentIntent(any(), any()))
                    .thenReturn(PaymentInstructionResponse.builder()
                            .paymentMethod("PAYOS").checkoutUrl("https://pay.payos.vn/x").build());

            var res = controller.pay(toi, UUID.randomUUID());

            // Hai phương thức trả tiền cần hai câu hướng dẫn khác hẳn nhau;
            // dùng chung một câu là dẫn khách làm sai một trong hai.
            assertAll(
                    () -> assertEquals(200, res.getStatusCode().value()),
                    () -> assertTrue(res.getBody().isSuccess()),
                    () -> assertTrue(res.getBody().getMessage().contains("link PayOS")));
        }

        @Test
        @DisplayName("Chuyển khoản tay: lời nhắn bảo chuyển theo hướng dẫn")
        void chuyenKhoanTay() {
            when(paymentService.createPaymentIntent(any(), any()))
                    .thenReturn(PaymentInstructionResponse.builder()
                            .paymentMethod("BANK_TRANSFER").build());

            assertTrue(controller.pay(toi, UUID.randomUUID()).getBody()
                    .getMessage().contains("Chuyển khoản"));
        }

        @Test
        @DisplayName("ID người trả lấy từ TOKEN, không từ đường dẫn hay thân yêu cầu")
        void idLayTuToken() {
            UUID bookingId = UUID.randomUUID();
            when(paymentService.createPaymentIntent(any(), any()))
                    .thenReturn(PaymentInstructionResponse.builder().paymentMethod("BANK_TRANSFER").build());

            controller.pay(toi, bookingId);

            // Nhận id từ client là cho bất kỳ ai trả tiền hộ — hoặc xem hướng
            // dẫn thanh toán của buổi xem người khác.
            verify(paymentService).createPaymentIntent(eq(nguoiDung.getId()), eq(bookingId));
        }
    }

    @Nested
    @DisplayName("Đối soát thanh toán")
    class DoiSoat {

        @Test
        @DisplayName("Danh sách nhận tham số lọc và phân trang")
        void danhSach() {
            controller.payments("PENDING", 2, 50);

            ArgumentCaptor<Pageable> bat = ArgumentCaptor.forClass(Pageable.class);
            verify(paymentService).list(eq("PENDING"), bat.capture());
            assertAll(
                    () -> assertEquals(2, bat.getValue().getPageNumber()),
                    () -> assertEquals(50, bat.getValue().getPageSize()));
        }

        @Test
        @DisplayName("Xác nhận và từ chối đều ghi ID người thao tác từ token")
        void ghiNguoiThaoTac() {
            UUID txId = UUID.randomUUID();

            controller.confirmPayment(toi, txId);
            controller.rejectPayment(toi, txId, lyDo("Sai nội dung"));

            // Không ghi ai xác nhận thì về sau không truy được khoản tiền này
            // do ai duyệt.
            assertAll(
                    () -> verify(paymentService).confirm(eq(nguoiDung.getId()), eq(txId)),
                    () -> verify(paymentService).reject(eq(nguoiDung.getId()), eq(txId),
                            eq("Sai nội dung")));
        }

        @Test
        @DisplayName("Từ chối KHÔNG kèm thân yêu cầu thì truyền null, không nổ")
        void tuChoiKhongKemThan() {
            UUID txId = UUID.randomUUID();

            // request.getReason() trên null là 500 cho một thao tác hoàn toàn
            // đúng — từ chối không nêu lý do là hợp lệ.
            controller.rejectPayment(toi, txId, null);

            verify(paymentService).reject(eq(nguoiDung.getId()), eq(txId), isNull());
        }
    }

    @Nested
    @DisplayName("Ký quỹ và lệnh rút")
    class KyQuyVaLenhRut {

        @Test
        @DisplayName("Sổ ví và danh sách lệnh rút đều chỉ đọc của CHÍNH MÌNH")
        void chiDocCuaChinhMinh() {
            controller.myEscrow(toi);
            controller.myLedger(toi, 0, 10);
            controller.myPayouts(toi, 0, 20);

            // Ba endpoint này không nhận id từ đâu khác ngoài token, nên không
            // có đường nào để xem ví của Reader khác.
            assertAll(
                    () -> verify(payoutService).mySummary(nguoiDung.getId()),
                    () -> verify(payoutService).myLedger(eq(nguoiDung.getId()), any()),
                    () -> verify(payoutService).listMine(eq(nguoiDung.getId()), any()));
        }

        @Test
        @DisplayName("Sổ ví mặc định 10 dòng, danh sách lệnh rút mặc định 20")
        void phanTrangMacDinh() {
            controller.myLedger(toi, 0, 10);
            controller.myPayouts(toi, 0, 20);

            ArgumentCaptor<Pageable> so = ArgumentCaptor.forClass(Pageable.class);
            ArgumentCaptor<Pageable> lenh = ArgumentCaptor.forClass(Pageable.class);
            verify(payoutService).myLedger(any(), so.capture());
            verify(payoutService).listMine(any(), lenh.capture());
            assertAll(
                    () -> assertEquals(10, so.getValue().getPageSize()),
                    () -> assertEquals(20, lenh.getValue().getPageSize()));
        }

        @Test
        @DisplayName("Tạo lệnh rút gắn đúng người xin")
        void taoLenhRut() {
            CreatePayoutRequest r = new CreatePayoutRequest();

            var res = controller.createPayout(toi, r);

            assertAll(
                    () -> assertTrue(res.getBody().getMessage().contains("Đã gửi lệnh rút")),
                    () -> verify(payoutService).create(eq(nguoiDung.getId()), eq(r)));
        }

        @Test
        @DisplayName("Ba bước duyệt lệnh rút là ba endpoint tách biệt")
        void baBuocDuyet() {
            UUID id = UUID.randomUUID();

            controller.approvePayout(toi, id);
            controller.rejectPayout(toi, id, lyDo("Sai số tài khoản"));
            controller.markPayoutPaid(toi, id);

            // Duyệt và "đã chuyển khoản" tách nhau có chủ ý: duyệt là đồng ý,
            // còn chuyển khoản là việc xảy ra ở ngân hàng sau đó.
            assertAll(
                    () -> verify(payoutService).approve(nguoiDung.getId(), id),
                    () -> verify(payoutService).reject(nguoiDung.getId(), id, "Sai số tài khoản"),
                    () -> verify(payoutService).markPaid(nguoiDung.getId(), id));
        }

        @Test
        @DisplayName("Từ chối lệnh rút không kèm lý do thì truyền null")
        void tuChoiLenhRutKhongLyDo() {
            UUID id = UUID.randomUUID();

            controller.rejectPayout(toi, id, null);

            verify(payoutService).reject(eq(nguoiDung.getId()), eq(id), isNull());
        }

        @Test
        @DisplayName("Hàng chờ duyệt nhận tham số lọc trạng thái")
        void hangChoDuyet() {
            controller.payouts("PENDING", 0, 20);

            verify(payoutService).list(eq("PENDING"), any());
        }
    }

    @Nested
    @DisplayName("Báo cáo vi phạm")
    class BaoCaoViPham {

        @Test
        @DisplayName("Gửi báo cáo gắn đúng người gửi và trả lời nhắn trấn an")
        void guiBaoCao() {
            CreateReportRequest r = new CreateReportRequest();

            var res = controller.report(toi, r);

            // Người vừa báo cáo một vi phạm cần biết nó đã tới nơi, không phải
            // một chữ "OK".
            assertAll(
                    () -> assertTrue(res.getBody().getMessage().contains("Đã ghi nhận")),
                    () -> verify(reportService).create(eq(nguoiDung.getId()), eq(r)));
        }

        @Test
        @DisplayName("Số báo cáo đang chờ trả về dưới khoá 'count'")
        void soBaoCaoDangCho() {
            when(reportService.pendingCount()).thenReturn(7L);

            var res = controller.pendingReports();

            // Giao diện đọc đúng khoá này để hiện chấm đỏ trên menu quản trị.
            assertEquals(7L, res.getBody().getData().get("count"));
        }

        @Test
        @DisplayName("Hàng chờ báo cáo nhận lọc trạng thái, và xử lý ghi người kết luận")
        void hangChoVaXuLy() {
            UUID id = UUID.randomUUID();
            HandleReportRequest r = new HandleReportRequest();

            controller.reports("PENDING", 0, 20);
            controller.handleReport(toi, id, r);

            assertAll(
                    () -> verify(reportService).list(eq("PENDING"), any()),
                    () -> verify(reportService).handle(eq(nguoiDung.getId()), eq(id), eq(r)));
        }
    }
}
