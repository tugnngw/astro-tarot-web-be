package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.EscrowAccount;
import com.exe.astratarot.domain.entity.EscrowTransaction;
import com.exe.astratarot.domain.entity.EscrowTransaction.Kind;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.Report;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.EscrowAccountRepository;
import com.exe.astratarot.repository.EscrowTransactionRepository;
import com.exe.astratarot.repository.UserRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sổ cái ký quỹ — phần bộ kiểm cũ chưa chạm tới: tiền phạt, thu nợ, và ba bước
 * của một lệnh rút.
 *
 * <p>Bất biến bao trùm: <b>không số nào được âm, ở bất kỳ đường nào</b>. Ký quỹ
 * giữ tiền thật của người khác, nên một số âm ở đây không phải lỗi hiển thị —
 * nó là tiền đã đi đâu mất.
 *
 * <p>Hai quy tắc con, cả hai đều dễ bị viết ngược:
 *
 * <ol>
 *   <li><b>Phạt không được đụng vào {@code pendingBalance}.</b> Đó là tiền của
 *       những buổi xem chưa xong, còn có thể phải hoàn cho khách. Lấy nó đi để
 *       trả nợ phạt là lấy tiền của người thứ ba.
 *   <li><b>Lệnh rút trừ tiền NGAY lúc tạo.</b> Chỉ kiểm số dư mà chưa trừ thì
 *       Reader tạo mười lệnh rút cùng một khoản, và quản trị viên duyệt lần
 *       lượt là chi thừa chín lần.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EscrowServiceImplLedgerTest {

    @Mock private EscrowAccountRepository escrowAccountRepository;
    @Mock private EscrowTransactionRepository escrowTransactionRepository;
    @Mock private UserRepository userRepository;

    private EscrowServiceImpl service;

    private User readerUser;
    private EscrowAccount viKyQuy;
    private final List<EscrowTransaction> soCai = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new EscrowServiceImpl(escrowAccountRepository, escrowTransactionRepository,
                userRepository);

        readerUser = new User();
        readerUser.setId(UUID.randomUUID());
        readerUser.setFullName("Reader");

        viKyQuy = EscrowAccount.builder()
                .id(UUID.randomUUID())
                .user(readerUser)
                .balance(0L)
                .pendingBalance(0L)
                .totalEarned(0L)
                .totalWithdrawn(0L)
                .penaltyOwed(0L)
                .build();

        soCai.clear();
        lenient().when(escrowAccountRepository.findByUserId(readerUser.getId()))
                .thenReturn(Optional.of(viKyQuy));
        lenient().when(userRepository.findById(readerUser.getId()))
                .thenReturn(Optional.of(readerUser));
        lenient().when(escrowAccountRepository.save(any(EscrowAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(escrowTransactionRepository.save(any(EscrowTransaction.class)))
                .thenAnswer(inv -> {
                    soCai.add(inv.getArgument(0));
                    return inv.getArgument(0);
                });
        lenient().when(escrowTransactionRepository.existsByBookingIdAndKind(any(), any()))
                .thenReturn(false);
    }

    private Booking lichHen(long tien) {
        ReaderProfile hoSo = ReaderProfile.builder()
                .id(UUID.randomUUID()).user(readerUser).build();
        return Booking.builder()
                .id(UUID.randomUUID())
                .readerProfile(hoSo)
                .totalAmount(tien)
                .build();
    }

    private List<Kind> cacLoaiDaGhi() {
        return soCai.stream().map(EscrowTransaction::getKind).toList();
    }

    // =====================================================================
    // Mở ví
    // =====================================================================

    @Nested
    @DisplayName("Mở ví ký quỹ")
    class MoVi {

        @Test
        @DisplayName("Chưa có ví thì tạo mới với mọi số bằng 0")
        void chuaCoViThiTao() {
            User moi = new User();
            moi.setId(UUID.randomUUID());
            when(escrowAccountRepository.findByUserId(moi.getId())).thenReturn(Optional.empty());

            var vi = service.getOrCreate(moi);

            // @Builder.Default là bắt buộc ở các trường này; thiếu nó thì mọi
            // số về null và phép cộng đầu tiên là NullPointerException.
            assertAll(
                    () -> assertEquals(0L, vi.getBalance()),
                    () -> assertEquals(0L, vi.getPendingBalance()),
                    () -> assertEquals(0L, vi.getPenaltyOwed()));
        }

        @Test
        @DisplayName("Đã có ví thì dùng lại, không tạo ví thứ hai")
        void daCoViThiDungLai() {
            assertEquals(viKyQuy, service.getOrCreate(readerUser));
            verify(escrowAccountRepository, never()).save(any(EscrowAccount.class));
        }

        @Test
        @DisplayName("Tài khoản không tồn tại thì báo đúng loại lỗi")
        void taiKhoanKhongTonTai() {
            UUID la = UUID.randomUUID();
            when(userRepository.findById(la)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.getOrCreate(la));
        }
    }

    // =====================================================================
    // Giữ và nhả
    // =====================================================================

    @Nested
    @DisplayName("Giữ và nhả tiền buổi xem")
    class GiuVaNha {

        @Test
        @DisplayName("Giữ tiền: vào phần ĐANG GIỮ, không vào phần rút được")
        void giuTien() {
            service.holdForBooking(lichHen(1_000_000L), 1_000_000L);

            // Vào thẳng balance là Reader rút được tiền của buổi xem chưa diễn
            // ra, và khách huỷ thì không còn gì để hoàn.
            assertAll(
                    () -> assertEquals(1_000_000L, viKyQuy.getPendingBalance()),
                    () -> assertEquals(0L, viKyQuy.getBalance()),
                    () -> assertEquals(List.of(Kind.HOLD), cacLoaiDaGhi()));
        }

        @Test
        @DisplayName("Nhả tiền: trừ 15% phí, chuyển phần còn lại sang rút được")
        void nhaTien() {
            viKyQuy.setPendingBalance(1_000_000L);

            service.releaseForBooking(lichHen(1_000_000L));

            assertAll(
                    () -> assertEquals(0L, viKyQuy.getPendingBalance()),
                    () -> assertEquals(850_000L, viKyQuy.getBalance()),
                    () -> assertEquals(850_000L, viKyQuy.getTotalEarned()));
        }

        @Test
        @DisplayName("Dòng sổ ghi số dư SAU khi đã cập nhật, không phải trước")
        void dongSoGhiSoDuSau() {
            viKyQuy.setPendingBalance(1_000_000L);

            service.releaseForBooking(lichHen(1_000_000L));

            // Ghi trước khi cập nhật thì dòng sổ nói dối, và sổ cái là thứ dùng
            // để đối soát khi có tranh chấp.
            EscrowTransaction dong = soCai.get(0);
            assertAll(
                    () -> assertEquals(850_000L, dong.getBalanceAfter()),
                    () -> assertEquals(0L, dong.getPendingAfter()));
        }

        @Test
        @DisplayName("Phần đang giữ KHÔNG đủ thì dừng, không đẩy xuống âm")
        void phanDangGiuKhongDu() {
            viKyQuy.setPendingBalance(500_000L);

            // Dữ liệu lệch do sự cố cũ. Đẩy xuống âm là giấu vấn đề đi; dừng
            // lại buộc người ta đối soát tay.
            assertAll(
                    () -> assertThrows(IllegalStateException.class,
                            () -> service.releaseForBooking(lichHen(1_000_000L))),
                    () -> assertThrows(IllegalStateException.class,
                            () -> service.refundForBooking(lichHen(1_000_000L), 1_000_000L)));
            assertEquals(500_000L, viKyQuy.getPendingBalance());
        }

        @Test
        @DisplayName("Nhả tiền HAI LẦN cho một buổi xem thì lần hai bị bỏ qua")
        void nhaTienHaiLan() {
            when(escrowTransactionRepository.existsByBookingIdAndKind(any(), org.mockito.ArgumentMatchers.eq(Kind.RELEASE)))
                    .thenReturn(true);
            viKyQuy.setPendingBalance(1_000_000L);

            service.releaseForBooking(lichHen(1_000_000L));

            // Webhook PayOS gửi lại, admin bấm hai lần, hàng đợi thử lại — cả
            // ba đều dẫn tới đây. Cộng tiền lần hai là chi thừa một buổi xem.
            assertAll(
                    () -> assertEquals(0L, viKyQuy.getBalance()),
                    () -> assertEquals(1_000_000L, viKyQuy.getPendingBalance()),
                    () -> assertTrue(soCai.isEmpty()));
        }

        @Test
        @DisplayName("Hoàn tiền: gỡ khỏi phần đang giữ, KHÔNG đụng phần rút được")
        void hoanTien() {
            viKyQuy.setPendingBalance(1_000_000L);
            viKyQuy.setBalance(300_000L);

            service.refundForBooking(lichHen(1_000_000L), 1_000_000L);

            assertAll(
                    () -> assertEquals(0L, viKyQuy.getPendingBalance()),
                    () -> assertEquals(300_000L, viKyQuy.getBalance()),
                    () -> assertEquals(List.of(Kind.REFUND), cacLoaiDaGhi()));
        }
    }

    // =====================================================================
    // Tiền phạt
    // =====================================================================

    @Nested
    @DisplayName("Tiền phạt và nợ phạt")
    class TienPhat {

        private Report baoCao(String loai) {
            Report r = new Report();
            r.setId(UUID.randomUUID());
            r.setReportType(loai);
            return r;
        }

        @Test
        @DisplayName("Đủ số dư: trừ hết ngay, không ghi nợ")
        void duSoDu() {
            viKyQuy.setBalance(500_000L);

            long daTru = service.applyPenalty(readerUser.getId(), 200_000L, baoCao("SPAM"));

            assertAll(
                    () -> assertEquals(200_000L, daTru),
                    () -> assertEquals(300_000L, viKyQuy.getBalance()),
                    () -> assertEquals(0L, viKyQuy.getPenaltyOwed()),
                    () -> assertEquals(List.of(Kind.PENALTY), cacLoaiDaGhi()));
        }

        @Test
        @DisplayName("Không đủ số dư: trừ được bao nhiêu trừ, phần còn lại GHI NỢ")
        void khongDuSoDu() {
            viKyQuy.setBalance(50_000L);

            long daTru = service.applyPenalty(readerUser.getId(), 200_000L, baoCao("ABUSE"));

            // Bỏ qua phần thiếu là tha nợ; chặn cả lệnh phạt là không phạt được
            // ai đang hết tiền. Ghi nợ giữ được cả hai.
            assertAll(
                    () -> assertEquals(50_000L, daTru),
                    () -> assertEquals(0L, viKyQuy.getBalance()),
                    () -> assertEquals(150_000L, viKyQuy.getPenaltyOwed()),
                    () -> assertEquals(List.of(Kind.PENALTY, Kind.PENALTY_DEBT), cacLoaiDaGhi()));
        }

        @Test
        @DisplayName("Số dư bằng 0: ghi nợ toàn bộ, KHÔNG ghi dòng trừ tiền 0 đồng")
        void soDuBangKhong() {
            long daTru = service.applyPenalty(readerUser.getId(), 200_000L, baoCao("ABUSE"));

            // Một dòng sổ "đã trừ 0 đ" chỉ làm sổ cái khó đọc.
            assertAll(
                    () -> assertEquals(0L, daTru),
                    () -> assertEquals(200_000L, viKyQuy.getPenaltyOwed()),
                    () -> assertEquals(List.of(Kind.PENALTY_DEBT), cacLoaiDaGhi()));
        }

        @Test
        @DisplayName("Phạt KHÔNG đụng tới phần đang giữ")
        void phatKhongDungPhanDangGiu() {
            viKyQuy.setBalance(0L);
            viKyQuy.setPendingBalance(5_000_000L);

            service.applyPenalty(readerUser.getId(), 1_000_000L, baoCao("ABUSE"));

            // Đó là tiền của những buổi xem chưa xong, còn có thể phải hoàn cho
            // khách. Lấy đi để trả nợ phạt là lấy tiền của người thứ ba.
            assertEquals(5_000_000L, viKyQuy.getPendingBalance());
        }

        @Test
        @DisplayName("Phạt 0 hoặc số âm thì không làm gì cả")
        void phatSoKhongHopLe() {
            viKyQuy.setBalance(500_000L);

            assertAll(
                    () -> assertEquals(0L, service.applyPenalty(readerUser.getId(), 0L, null)),
                    () -> assertEquals(0L, service.applyPenalty(readerUser.getId(), -100L, null)),
                    () -> assertEquals(500_000L, viKyQuy.getBalance()));
            assertTrue(soCai.isEmpty());
        }

        @Test
        @DisplayName("Báo cáo thiếu loại vi phạm thì ghi chú vẫn đọc được")
        void baoCaoThieuLoai() {
            viKyQuy.setBalance(500_000L);

            service.applyPenalty(readerUser.getId(), 100_000L, baoCao("  "));
            service.applyPenalty(readerUser.getId(), 100_000L, null);

            // Ghi chú sổ cái là thứ người đối soát đọc; để trống hay ghi "null"
            // đều làm họ phải đi hỏi.
            soCai.forEach(d -> assertTrue(d.getNote() != null && !d.getNote().isBlank()));
        }

        @Test
        @DisplayName("Nhả tiền xong thì THU NỢ phạt ngay, trước khi Reader kịp rút")
        void thuNoNgayKhiNhaTien() {
            viKyQuy.setPendingBalance(1_000_000L);
            viKyQuy.setPenaltyOwed(300_000L);

            service.releaseForBooking(lichHen(1_000_000L));

            // Đợi tới lúc họ xin rút thì khoản nợ chỉ được thu khi chính người
            // nợ tự nguyện đụng vào ví.
            assertAll(
                    () -> assertEquals(550_000L, viKyQuy.getBalance()),
                    () -> assertEquals(0L, viKyQuy.getPenaltyOwed()),
                    () -> assertEquals(List.of(Kind.RELEASE, Kind.DEBT_COLLECTED), cacLoaiDaGhi()));
        }

        @Test
        @DisplayName("Nợ lớn hơn số vừa nhả thì thu một phần, phần còn lại giữ nợ")
        void thuMotPhanNo() {
            viKyQuy.setPendingBalance(100_000L);
            viKyQuy.setPenaltyOwed(500_000L);

            service.releaseForBooking(lichHen(100_000L));

            // Nhả 85.000 sau phí, thu hết vào nợ, còn nợ 415.000.
            assertAll(
                    () -> assertEquals(0L, viKyQuy.getBalance()),
                    () -> assertEquals(415_000L, viKyQuy.getPenaltyOwed()),
                    () -> assertTrue(soCai.get(1).getNote().contains("415000")));
        }

        @Test
        @DisplayName("Không nợ gì thì không ghi dòng thu nợ")
        void khongNoThiKhongGhi() {
            viKyQuy.setPendingBalance(1_000_000L);

            service.releaseForBooking(lichHen(1_000_000L));

            assertEquals(List.of(Kind.RELEASE), cacLoaiDaGhi());
        }
    }

    // =====================================================================
    // Lệnh rút
    // =====================================================================

    @Nested
    @DisplayName("Ba bước của một lệnh rút")
    class LenhRut {

        @Test
        @DisplayName("Tạo lệnh: trừ tiền NGAY, không đợi tới lúc duyệt")
        void taoLenhTruNgay() {
            viKyQuy.setBalance(1_000_000L);

            service.reserveForPayout(readerUser.getId(), 400_000L);

            // Chỉ kiểm số dư mà chưa trừ thì Reader tạo mười lệnh rút cùng một
            // khoản, và quản trị viên duyệt lần lượt là chi thừa chín lần.
            assertAll(
                    () -> assertEquals(600_000L, viKyQuy.getBalance()),
                    () -> assertEquals(List.of(Kind.PAYOUT_RESERVE), cacLoaiDaGhi()));
        }

        @Test
        @DisplayName("Số dư không đủ thì không tạo được lệnh")
        void soDuKhongDu() {
            viKyQuy.setBalance(100_000L);

            assertThrows(IllegalArgumentException.class,
                    () -> service.reserveForPayout(readerUser.getId(), 400_000L));
            assertEquals(100_000L, viKyQuy.getBalance());
        }

        @Test
        @DisplayName("Rút đúng bằng toàn bộ số dư thì được, về 0 chứ không âm")
        void rutDungToanBoSoDu() {
            viKyQuy.setBalance(400_000L);

            service.reserveForPayout(readerUser.getId(), 400_000L);

            assertEquals(0L, viKyQuy.getBalance());
        }

        @Test
        @DisplayName("Từ chối lệnh: trả tiền về số dư")
        void tuChoiLenh() {
            viKyQuy.setBalance(600_000L);

            service.returnRejectedPayout(readerUser.getId(), 400_000L);

            assertAll(
                    () -> assertEquals(1_000_000L, viKyQuy.getBalance()),
                    () -> assertEquals(List.of(Kind.PAYOUT_RETURN), cacLoaiDaGhi()));
        }

        @Test
        @DisplayName("Chi tiền xong: chỉ cộng tổng đã rút, KHÔNG trừ số dư lần nữa")
        void chiTienXong() {
            viKyQuy.setBalance(600_000L);
            viKyQuy.setTotalWithdrawn(0L);

            service.settlePayout(readerUser.getId(), 400_000L);

            // Tiền đã bị trừ từ lúc tạo lệnh. Trừ thêm ở đây là trừ hai lần cho
            // một khoản rút.
            assertAll(
                    () -> assertEquals(600_000L, viKyQuy.getBalance()),
                    () -> assertEquals(400_000L, viKyQuy.getTotalWithdrawn()),
                    () -> assertEquals(List.of(Kind.PAYOUT_SETTLE), cacLoaiDaGhi()));
        }

        @Test
        @DisplayName("Một vòng đầy đủ: tạo rồi từ chối thì số dư về đúng ban đầu")
        void motVongDayDu() {
            viKyQuy.setBalance(1_000_000L);

            service.reserveForPayout(readerUser.getId(), 400_000L);
            service.returnRejectedPayout(readerUser.getId(), 400_000L);

            assertEquals(1_000_000L, viKyQuy.getBalance());
        }

        @Test
        @DisplayName("Mọi dòng sổ đều mang số dư sau, không dòng nào âm")
        void moiDongMangSoDuSau() {
            viKyQuy.setBalance(1_000_000L);

            service.reserveForPayout(readerUser.getId(), 400_000L);
            service.settlePayout(readerUser.getId(), 400_000L);

            ArgumentCaptor<EscrowTransaction> bat =
                    ArgumentCaptor.forClass(EscrowTransaction.class);
            verify(escrowTransactionRepository, org.mockito.Mockito.times(2)).save(bat.capture());
            bat.getAllValues().forEach(d -> assertAll(
                    () -> assertTrue(d.getBalanceAfter() >= 0, "số dư âm trong sổ: " + d.getKind()),
                    () -> assertTrue(d.getPendingAfter() >= 0)));
        }
    }
}
