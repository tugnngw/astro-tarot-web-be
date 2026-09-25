package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.payout.CreatePayoutRequest;
import com.exe.astratarot.domain.entity.EscrowAccount;
import com.exe.astratarot.domain.entity.PayoutRequest;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.PayoutStatus;
import com.exe.astratarot.exception.ReaderNotVerifiedException;
import com.exe.astratarot.exception.ResourceNotFoundException;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Chi tiền RA khỏi hệ thống. Lớp này trước đây có độ phủ 0,9%.
 *
 * <p>Ba bất biến ở đây, mỗi cái vỡ là mất tiền thật:
 *
 * <ol>
 *   <li><b>Trừ số dư ngay lúc TẠO lệnh, không phải lúc duyệt.</b> Chỉ kiểm tra
 *       mà chưa trừ thì Reader tạo mười lệnh rút cùng một khoản, quản trị viên
 *       duyệt lần lượt, và sàn chi thừa chín lần.</li>
 *   <li><b>Từ chối thì phải TRẢ tiền về.</b> Tiền đã bị trừ từ lúc tạo; quên
 *       trả là nuốt mất tiền của Reader mà không ai thấy.</li>
 *   <li><b>Không đánh dấu "đã chi" nếu chưa duyệt.</b> Bỏ qua bước duyệt nghĩa
 *       là tiền ra khỏi tài khoản mà không ai ký.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PayoutServiceImplTest {

    @Mock private PayoutRequestRepository payoutRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;
    @Mock private EscrowService escrowService;
    @Mock private EscrowTransactionRepository escrowTransactionRepository;
    @Mock private NotificationService notificationService;
    @Mock private ActivityLogService activityLogService;
    @Mock private VietQrService vietQrService;

    private PayoutServiceImpl service;

    private final UUID readerUserId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID payoutId = UUID.randomUUID();

    private User readerUser;
    private ReaderProfile readerProfile;

    @BeforeEach
    void setUp() {
        service = new PayoutServiceImpl(
                payoutRepository, readerProfileRepository, escrowService,
                escrowTransactionRepository, notificationService,
                activityLogService, vietQrService);

        readerUser = new User();
        readerUser.setId(readerUserId);
        readerUser.setFullName("Reader");

        readerProfile = new ReaderProfile();
        readerProfile.setId(UUID.randomUUID());
        readerProfile.setUser(readerUser);

        lenient().when(payoutRepository.save(any(PayoutRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private CreatePayoutRequest yeuCau(long soTien) {
        CreatePayoutRequest r = new CreatePayoutRequest();
        r.setAmount(soTien);
        r.setBankName("  Vietcombank  ");
        r.setBankAccount("  0123456789  ");
        r.setAccountHolder("  TRAN DUY DAT  ");
        return r;
    }

    private PayoutRequest lenh(PayoutStatus tt, long soTien) {
        return PayoutRequest.builder()
                .id(payoutId)
                .reader(readerProfile)
                .amount(soTien)
                .bankName("Vietcombank")
                .bankAccount("0123456789")
                .accountHolder("TRAN DUY DAT")
                .status(tt)
                .build();
    }

    // =====================================================================

    @Test
    @DisplayName("Chưa có hồ sơ Reader thì không tạo được lệnh rút")
    void chuaCoHoSoThiKhongRutDuoc() {
        when(readerProfileRepository.findByUserId(readerUserId))
                .thenReturn(Optional.empty());

        assertThrows(ReaderNotVerifiedException.class,
                () -> service.create(readerUserId, yeuCau(100_000L)));

        verify(escrowService, never()).reserveForPayout(any(), anyLong());
    }

    @Test
    @DisplayName("Dưới mức tối thiểu thì từ chối và KHÔNG trừ số dư")
    void duoiMucToiThieu() {
        when(readerProfileRepository.findByUserId(readerUserId))
                .thenReturn(Optional.of(readerProfile));

        assertThrows(IllegalArgumentException.class,
                () -> service.create(readerUserId, yeuCau(49_999L)));

        // Trừ tiền rồi mới phát hiện dưới ngưỡng là treo tiền của Reader.
        assertAll(
                () -> verify(escrowService, never()).reserveForPayout(any(), anyLong()),
                () -> verify(payoutRepository, never()).save(any()));
    }

    @Test
    @DisplayName("Tạo lệnh thì TRỪ số dư ngay, trước khi lưu lệnh")
    void truSoDuNgayKhiTao() {
        when(readerProfileRepository.findByUserId(readerUserId))
                .thenReturn(Optional.of(readerProfile));

        service.create(readerUserId, yeuCau(100_000L));

        // Thứ tự quan trọng: trừ trước, lưu sau. Lưu trước mà trừ hỏng thì có
        // một lệnh rút không có tiền đối ứng.
        InOrder thuTu = inOrder(escrowService, payoutRepository);
        thuTu.verify(escrowService).reserveForPayout(readerUserId, 100_000L);
        thuTu.verify(payoutRepository).save(any(PayoutRequest.class));
    }

    @Test
    @DisplayName("Thông tin ngân hàng được cắt khoảng trắng thừa")
    void catKhoangTrang() {
        when(readerProfileRepository.findByUserId(readerUserId))
                .thenReturn(Optional.of(readerProfile));

        var kq = service.create(readerUserId, yeuCau(100_000L));

        // Số tài khoản dính khoảng trắng là chuyển khoản hỏng, mà lỗi chỉ lộ
        // ra ở ngân hàng chứ không phải ở đây.
        assertAll(
                () -> assertNotNull(kq),
                () -> verify(payoutRepository).save(org.mockito.ArgumentMatchers
                        .argThat(p -> "Vietcombank".equals(p.getBankName())
                                && "0123456789".equals(p.getBankAccount())
                                && "TRAN DUY DAT".equals(p.getAccountHolder()))));
    }

    @Test
    @DisplayName("Không tìm thấy lệnh thì báo đúng loại lỗi")
    void khongTimThayLenh() {
        when(payoutRepository.findByIdWithReader(payoutId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.approve(actorId, payoutId));
    }

    @Test
    @DisplayName("Chỉ duyệt được lệnh đang chờ")
    void chiDuyetDuocLenhDangCho() {
        when(payoutRepository.findByIdWithReader(payoutId))
                .thenReturn(Optional.of(lenh(PayoutStatus.APPROVED, 100_000L)));

        assertThrows(IllegalArgumentException.class,
                () -> service.approve(actorId, payoutId));
    }

    @Test
    @DisplayName("Duyệt: đổi trạng thái, ghi nhật ký, báo cho Reader")
    void duyetLenh() {
        PayoutRequest p = lenh(PayoutStatus.PENDING, 100_000L);
        when(payoutRepository.findByIdWithReader(payoutId)).thenReturn(Optional.of(p));

        service.approve(actorId, payoutId);

        assertAll(
                () -> assertEquals(PayoutStatus.APPROVED, p.getStatus()),
                // Duyệt CHƯA phải chi tiền, nên chưa được đụng vào ký quỹ.
                () -> verify(escrowService, never()).settlePayout(any(), anyLong()),
                () -> verify(activityLogService).record(eq(actorId), anyString(),
                        anyString(), eq(payoutId), any()),
                () -> verify(notificationService).push(eq(readerUser), anyString(),
                        anyString(), anyString(), any()));
    }

    @Test
    @DisplayName("Từ chối: TRẢ tiền về số dư")
    void tuChoiThiTraTienVe() {
        PayoutRequest p = lenh(PayoutStatus.PENDING, 100_000L);
        when(payoutRepository.findByIdWithReader(payoutId)).thenReturn(Optional.of(p));

        service.reject(actorId, payoutId, "  Sai số tài khoản  ");

        // Tiền đã bị trừ từ lúc tạo lệnh. Quên trả về là nuốt mất tiền của
        // Reader, và không có gì trong hệ thống báo cho ai biết.
        assertAll(
                () -> assertEquals(PayoutStatus.REJECTED, p.getStatus()),
                () -> verify(escrowService).returnRejectedPayout(readerUserId, 100_000L),
                () -> assertEquals("Sai số tài khoản", p.getRejectReason()),
                () -> assertNotNull(p.getProcessedAt()));
    }

    @Test
    @DisplayName("Từ chối không kèm lý do thì để trống, không lưu chuỗi rỗng")
    void tuChoiKhongLyDo() {
        PayoutRequest p = lenh(PayoutStatus.PENDING, 100_000L);
        when(payoutRepository.findByIdWithReader(payoutId)).thenReturn(Optional.of(p));

        service.reject(actorId, payoutId, "   ");

        org.junit.jupiter.api.Assertions.assertNull(p.getRejectReason());
    }

    @Test
    @DisplayName("KHÔNG đánh dấu đã chi khi lệnh chưa được duyệt")
    void khongChiKhiChuaDuyet() {
        when(payoutRepository.findByIdWithReader(payoutId))
                .thenReturn(Optional.of(lenh(PayoutStatus.PENDING, 100_000L)));

        // Bỏ qua bước duyệt nghĩa là tiền ra khỏi tài khoản mà không ai ký.
        assertThrows(IllegalArgumentException.class,
                () -> service.markPaid(actorId, payoutId));

        verify(escrowService, never()).settlePayout(any(), anyLong());
    }

    @Test
    @DisplayName("Đánh dấu đã chi: quyết toán ký quỹ và ghi thời điểm")
    void danhDauDaChi() {
        PayoutRequest p = lenh(PayoutStatus.APPROVED, 100_000L);
        when(payoutRepository.findByIdWithReader(payoutId)).thenReturn(Optional.of(p));

        service.markPaid(actorId, payoutId);

        assertAll(
                () -> assertEquals(PayoutStatus.PAID, p.getStatus()),
                () -> assertNotNull(p.getProcessedAt()),
                () -> verify(escrowService).settlePayout(readerUserId, 100_000L),
                () -> verify(notificationService).push(eq(readerUser), anyString(),
                        anyString(), anyString(), any()));
    }

    @Test
    @DisplayName("Đã chi rồi thì không chi lại được")
    void khongChiHaiLan() {
        when(payoutRepository.findByIdWithReader(payoutId))
                .thenReturn(Optional.of(lenh(PayoutStatus.PAID, 100_000L)));

        assertThrows(IllegalArgumentException.class,
                () -> service.markPaid(actorId, payoutId));
        verify(escrowService, never()).settlePayout(any(), anyLong());
    }

    @Test
    @DisplayName("Bảng số dư trả đúng mức rút tối thiểu cho giao diện")
    void bangSoDu() {
        EscrowAccount tk = new EscrowAccount();
        tk.setBalance(120_000L);
        tk.setPendingBalance(30_000L);
        tk.setTotalEarned(500_000L);
        tk.setTotalWithdrawn(350_000L);
        tk.setPenaltyOwed(0L);
        when(escrowService.getOrCreate(readerUserId)).thenReturn(tk);

        var kq = service.mySummary(readerUserId);

        // Giao diện dùng con số này để khoá nút rút. Không trả về thì nó khoá
        // nhầm, hoặc mở cho một lệnh chắc chắn bị từ chối.
        assertAll(
                () -> assertEquals(120_000L, kq.getBalance()),
                () -> assertEquals(30_000L, kq.getPendingBalance()),
                () -> assertEquals(50_000L, kq.getMinimumPayout()));
    }
}
