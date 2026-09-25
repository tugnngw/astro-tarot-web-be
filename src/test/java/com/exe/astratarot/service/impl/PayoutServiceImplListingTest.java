package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.EscrowAccount;
import com.exe.astratarot.domain.entity.EscrowTransaction;
import com.exe.astratarot.domain.entity.EscrowTransaction.Kind;
import com.exe.astratarot.domain.entity.PayoutRequest;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.PayoutStatus;
import com.exe.astratarot.repository.EscrowTransactionRepository;
import com.exe.astratarot.repository.PayoutRequestRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.VietQrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tiền ra: các màn ĐỌC — sổ ví của Reader và hàng chờ của người duyệt. Bộ kiểm
 * cũ lo luồng tạo và duyệt; phần này chưa ai chạm tới.
 *
 * <p>Hai chi tiết nhỏ mà hậu quả không nhỏ:
 *
 * <ol>
 *   <li><b>Mã QR chỉ kèm lệnh CÒN PHẢI CHI.</b> Lệnh đã chi hay đã từ chối mà
 *       vẫn kèm mã quét được là một cái bẫy chuyển nhầm tiền lần thứ hai — và
 *       người quét sẽ không nghi ngờ gì, vì mã hiện ngay cạnh lệnh.
 *   <li><b>Dữ liệu ngân hàng hỏng chỉ mất mã QR, không mất cả danh sách.</b>
 *       Người duyệt vẫn phải xem được lệnh để từ chối nó.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayoutServiceImplListingTest {

    @Mock private PayoutRequestRepository payoutRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;
    @Mock private EscrowService escrowService;
    @Mock private EscrowTransactionRepository escrowTransactionRepository;
    @Mock private NotificationService notificationService;
    @Mock private ActivityLogService activityLogService;
    @Mock private VietQrService vietQrService;

    private PayoutServiceImpl service;

    private User readerUser;
    private ReaderProfile readerProfile;

    @BeforeEach
    void setUp() {
        service = new PayoutServiceImpl(payoutRepository, readerProfileRepository, escrowService,
                escrowTransactionRepository, notificationService, activityLogService, vietQrService);

        readerUser = new User();
        readerUser.setId(UUID.randomUUID());
        readerUser.setFullName("Lê Thu Lan");
        readerUser.setEmail("lan@example.com");

        readerProfile = ReaderProfile.builder()
                .id(UUID.randomUUID()).user(readerUser).build();

        lenient().when(vietQrService.dungChuoi(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn("00020101021238...QR");
    }

    private PayoutRequest lenh(PayoutStatus tt, String bin, String soTk) {
        return PayoutRequest.builder()
                .id(UUID.randomUUID())
                .reader(readerProfile)
                .amount(500_000L)
                .bankName("Vietcombank")
                .bankAccount(soTk)
                .accountHolder("LE THU LAN")
                .bankBin(bin)
                .status(tt)
                .build();
    }

    private EscrowTransaction dongSo(Kind loai, Booking lichHen) {
        return EscrowTransaction.builder()
                .id(UUID.randomUUID())
                .account(EscrowAccount.builder().id(UUID.randomUUID()).user(readerUser).build())
                .kind(loai)
                .amount(850_000L)
                .balanceAfter(850_000L)
                .pendingAfter(0L)
                .booking(lichHen)
                .note("Buổi xem hoàn tất.")
                .build();
    }

    // =====================================================================
    // Sổ ví của Reader
    // =====================================================================

    @Test
    @DisplayName("Sổ ví mang đủ loại giao dịch, số dư sau, và lịch hẹn liên quan")
    void soVi() {
        Booking b = Booking.builder().id(UUID.randomUUID()).build();
        when(escrowTransactionRepository.findByAccountUserIdOrderByCreatedAtDesc(
                eq(readerUser.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(dongSo(Kind.RELEASE, b))));

        var trang = service.myLedger(readerUser.getId(), PageRequest.of(0, 20));
        var dong = trang.getContent().get(0);

        // Số dư sau mỗi dòng là thứ Reader dùng để tự đối chiếu; thiếu nó thì
        // sổ chỉ là một danh sách con số rời rạc.
        assertAll(
                () -> assertEquals("RELEASE", dong.getKind()),
                () -> assertEquals(850_000L, dong.getAmount()),
                () -> assertEquals(850_000L, dong.getBalanceAfter()),
                () -> assertEquals(0L, dong.getPendingAfter()),
                () -> assertEquals(b.getId(), dong.getBookingId()),
                () -> assertEquals("Buổi xem hoàn tất.", dong.getNote()));
    }

    @Test
    @DisplayName("Dòng sổ KHÔNG gắn lịch hẹn thì để trống, không nổ")
    void dongSoKhongGanLichHen() {
        when(escrowTransactionRepository.findByAccountUserIdOrderByCreatedAtDesc(
                eq(readerUser.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(dongSo(Kind.PAYOUT_RESERVE, null))));

        // Lệnh rút và tiền phạt không gắn với buổi xem nào.
        assertNull(service.myLedger(readerUser.getId(), PageRequest.of(0, 20))
                .getContent().get(0).getBookingId());
    }

    @Test
    @DisplayName("Ví chưa có giao dịch nào thì trang rỗng, không null")
    void viChuaCoGiaoDich() {
        when(escrowTransactionRepository.findByAccountUserIdOrderByCreatedAtDesc(
                eq(readerUser.getId()), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertTrue(service.myLedger(readerUser.getId(), PageRequest.of(0, 20)).isEmpty());
    }

    @Test
    @DisplayName("Danh sách lệnh rút của chính Reader che bớt số tài khoản")
    void danhSachCuaReader() {
        when(payoutRepository.findForReader(eq(readerUser.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(lenh(PayoutStatus.PENDING, "970436", "0123456789"))));

        var kq = service.listMine(readerUser.getId(), PageRequest.of(0, 20)).getContent().get(0);

        assertAll(
                () -> assertEquals("Lê Thu Lan", kq.getReaderName()),
                () -> assertEquals(500_000L, kq.getAmount()),
                () -> assertTrue(kq.getBankAccountMasked().endsWith("6789")),
                () -> assertFalse(kq.getBankAccountMasked().contains("012345")),
                // Reader tự xem sổ của mình thì không cần mã QR để chuyển cho ai.
                () -> assertNull(kq.getQrPayload()));
    }

    // =====================================================================
    // Hàng chờ của người duyệt
    // =====================================================================

    @Test
    @DisplayName("Lệnh CÒN PHẢI CHI thì kèm mã QR")
    void lenhConPhaiChiCoQr() {
        for (PayoutStatus tt : List.of(PayoutStatus.PENDING, PayoutStatus.APPROVED)) {
            when(payoutRepository.search(eq(tt), any()))
                    .thenReturn(new PageImpl<>(List.of(lenh(tt, "970436", "0123456789"))));

            var kq = service.list(tt.name(), PageRequest.of(0, 20)).getContent().get(0);
            assertNotNull(kq.getQrPayload(), "lệnh " + tt + " phải có QR");
        }
    }

    @Test
    @DisplayName("Lệnh ĐÃ CHI hoặc BỊ TỪ CHỐI thì KHÔNG kèm mã QR")
    void lenhDaXongKhongCoQr() {
        for (PayoutStatus tt : List.of(PayoutStatus.PAID, PayoutStatus.REJECTED)) {
            when(payoutRepository.search(eq(tt), any()))
                    .thenReturn(new PageImpl<>(List.of(lenh(tt, "970436", "0123456789"))));

            // Mã quét được nằm cạnh một lệnh đã chi là cái bẫy chuyển nhầm tiền
            // lần thứ hai, và người quét sẽ không nghi ngờ gì.
            assertNull(service.list(tt.name(), PageRequest.of(0, 20))
                    .getContent().get(0).getQrPayload(), "lệnh " + tt + " không được có QR");
        }
    }

    @Test
    @DisplayName("Thiếu mã ngân hàng hoặc số tài khoản thì bỏ QR, vẫn hiện lệnh")
    void thieuThongTinNganHang() {
        when(payoutRepository.search(eq(null), any())).thenReturn(new PageImpl<>(List.of(
                lenh(PayoutStatus.PENDING, null, "0123456789"),
                lenh(PayoutStatus.PENDING, "970436", null))));

        var ds = service.list(null, PageRequest.of(0, 20)).getContent();

        assertAll(
                () -> assertEquals(2, ds.size()),
                () -> assertNull(ds.get(0).getQrPayload()),
                () -> assertNull(ds.get(1).getQrPayload()));
        verify(vietQrService, never()).dungChuoi(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("Dựng QR hỏng thì chỉ mất QR, KHÔNG mất cả danh sách")
    void dungQrHong() {
        when(payoutRepository.search(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(lenh(PayoutStatus.PENDING, "xxx", "0123456789"))));
        when(vietQrService.dungChuoi(anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new IllegalArgumentException("mã ngân hàng không hợp lệ"));

        var kq = service.list(null, PageRequest.of(0, 20)).getContent().get(0);

        // Người duyệt vẫn phải xem được lệnh để từ chối nó. Ném lỗi ở đây là
        // một lệnh hỏng làm sập cả hàng chờ.
        assertAll(
                () -> assertNull(kq.getQrPayload()),
                () -> assertEquals(500_000L, kq.getAmount()));
    }

    @Test
    @DisplayName("Nội dung chuyển khoản mang tên Reader để đối soát")
    void noiDungChuyenKhoan() {
        when(payoutRepository.search(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(lenh(PayoutStatus.PENDING, "970436", "0123456789"))));

        service.list(null, PageRequest.of(0, 20));

        org.mockito.ArgumentCaptor<String> bat = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(vietQrService).dungChuoi(eq("970436"), eq("0123456789"), eq(500_000L), bat.capture());
        // Chuyển khoản không ghi tên người nhận thì sao kê ngân hàng thành một
        // cột số không truy được về ai.
        assertTrue(bat.getValue().contains("Lê Thu Lan"));
    }

    @Test
    @DisplayName("Lọc theo trạng thái nhận cả chữ thường và dấu cách thừa")
    void locTrangThai() {
        when(payoutRepository.search(eq(PayoutStatus.PENDING), any()))
                .thenReturn(new PageImpl<>(List.of()));

        assertEquals(0, service.list("  pending  ", PageRequest.of(0, 20)).getTotalElements());
    }

    @Test
    @DisplayName("Không lọc thì truyền null xuống truy vấn")
    void khongLoc() {
        when(payoutRepository.search(eq(null), any())).thenReturn(new PageImpl<>(List.of()));

        assertAll(
                () -> assertEquals(0, service.list(null, PageRequest.of(0, 20)).getTotalElements()),
                () -> assertEquals(0, service.list("  ", PageRequest.of(0, 20)).getTotalElements()));
    }

    @Test
    @DisplayName("Trạng thái không có thật thì báo lỗi, không lặng lẽ trả hết")
    void trangThaiKhongCoThat() {
        // Trả về toàn bộ hàng chờ khi tham số sai là kiểu lỗi khó thấy nhất ở
        // một màn hình chi tiền: nó trông vẫn chạy.
        var loi = assertThrows(IllegalArgumentException.class,
                () -> service.list("DA_CHUYEN_ROI", PageRequest.of(0, 20)));
        assertTrue(loi.getMessage().contains("DA_CHUYEN_ROI"));
    }

    @Test
    @DisplayName("Số tài khoản ngắn thì không che, vì che hết là không đối chiếu được")
    void soTaiKhoanNgan() {
        when(payoutRepository.search(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(lenh(PayoutStatus.PENDING, "970436", "1234"))));

        assertEquals("1234", service.list(null, PageRequest.of(0, 20))
                .getContent().get(0).getBankAccountMasked());
    }
}
