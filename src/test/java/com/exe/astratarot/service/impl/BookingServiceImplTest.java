package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.booking.CreateBookingRequest;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.ReaderAvailability;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.CalendarDayKind;
import com.exe.astratarot.domain.enums.CalendarSlotState;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.ReaderAvailabilityRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReaderUnavailableDateRepository;
import com.exe.astratarot.repository.ReviewRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.BookingChatService;
import com.exe.astratarot.service.BookingService.ActorType;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.NotificationTypes;
import com.exe.astratarot.service.PaymentService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lịch hẹn: toàn bộ luật về ai được đặt gì, khi nào, và ai được đổi trạng thái.
 *
 * <p>Lớp này trước đây phủ 32,2%. Bộ kiểm sẵn có chỉ nói về khoá bi quan khi hai
 * người bấm cùng lúc; 137 dòng luật còn lại chưa ai kiểm.
 *
 * <p>Ba chỗ đáng kiểm nhất, vì cả ba đều là đường dẫn tới tiền:
 *
 * <ol>
 *   <li><b>Hoàn tất trước giờ hẹn.</b> Đánh dấu COMPLETED là lệnh nhả ký quỹ
 *       cho Reader. Nếu làm được trước giờ hẹn thì đó là cách nhận tiền cho một
 *       buổi xem chưa từng diễn ra.
 *   <li><b>Huỷ phải đi qua hoàn tiền.</b> Huỷ mà không gọi refundIfPaid thì
 *       khoản khách đã trả nằm lại trong ký quỹ, không của ai.
 *   <li><b>Múi giờ cố định.</b> Reader khai lịch bằng giờ Việt Nam, booking lưu
 *       bằng Instant. Lấy múi giờ của máy chủ thì cùng một khung giờ sẽ nhảy chỗ
 *       khi đổi máy triển khai — nên kiểm thẳng bằng Asia/Ho_Chi_Minh.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceImplTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private BookingRepository bookingRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;
    @Mock private ReaderAvailabilityRepository availabilityRepository;
    @Mock private ReaderUnavailableDateRepository unavailableDateRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private EscrowService escrowService;
    @Mock private PaymentService paymentService;
    @Mock private BookingChatService bookingChatService;

    private BookingServiceImpl service;

    private User khach;
    private User readerUser;
    private ReaderProfile reader;

    /** Một ngày trong tương lai, cố định là thứ Tư để lịch tuần khỏi phụ thuộc hôm nay. */
    private LocalDate ngayMai;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(
                bookingRepository, readerProfileRepository, availabilityRepository,
                unavailableDateRepository, reviewRepository, userRepository,
                notificationService, escrowService, paymentService, bookingChatService);

        khach = new User();
        khach.setId(UUID.randomUUID());
        khach.setFullName("Khách");

        readerUser = new User();
        readerUser.setId(UUID.randomUUID());
        readerUser.setFullName("Reader");

        reader = ReaderProfile.builder()
                .id(UUID.randomUUID())
                .user(readerUser)
                .pricePer15m(100_000L)
                .pricePer30m(180_000L)
                .pricePer60m(320_000L)
                .available(true)
                .verifiedAt(Instant.now().minus(30, ChronoUnit.DAYS))
                .build();

        ngayMai = LocalDate.now(VN).plusDays(1);

        lenient().when(userRepository.findById(khach.getId())).thenReturn(Optional.of(khach));
        lenient().when(readerProfileRepository.findById(reader.getId())).thenReturn(Optional.of(reader));
        lenient().when(bookingRepository.findOverlapping(any(), any(), any())).thenReturn(List.of());
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(UUID.randomUUID());
            }
            return b;
        });
    }

    /** Reader làm việc 09:00–17:00 vào đúng ngày truyền vào. */
    private void moLichTuan(LocalDate ngay) {
        moLichTuan(ngay, LocalTime.of(9, 0), LocalTime.of(17, 0));
    }

    private void moLichTuan(LocalDate ngay, LocalTime tu, LocalTime den) {
        short thu = (short) (ngay.getDayOfWeek().getValue() % 7);
        lenient().when(availabilityRepository
                        .findByReaderIdAndDayOfWeekAndActiveTrue(reader.getId(), thu))
                .thenReturn(List.of(khungTuan(ngay, tu, den)));
    }

    /** Lịch tháng đọc cả tuần một lần, khác với từng-thứ của khung giờ một ngày. */
    private void moCaTuan(LocalDate ngay, LocalTime tu, LocalTime den) {
        lenient().when(availabilityRepository.findByReaderIdAndActiveTrue(reader.getId()))
                .thenReturn(List.of(khungTuan(ngay, tu, den)));
    }

    private ReaderAvailability khungTuan(LocalDate ngay, LocalTime tu, LocalTime den) {
        short thu = (short) (ngay.getDayOfWeek().getValue() % 7);
        return ReaderAvailability.builder()
                .id(UUID.randomUUID())
                .reader(reader)
                .dayOfWeek(thu)
                .startTime(tu)
                .endTime(den)
                .active(true)
                .build();
    }

    private Instant gio(LocalDate ngay, int h, int m) {
        return ngay.atTime(h, m).atZone(VN).toInstant();
    }

    /**
     * Metadata của thông báo đã gửi tới [nguoiNhan].
     *
     * <p>Đọc thẳng đối số thật thay vì {@code any()}: một thông báo gửi đúng
     * người nhưng thiếu khoá "side" vẫn qua được mọi phép kiểm trước đó, và đó
     * chính là cách lỗi này lọt ra tới người dùng.
     */
    @SuppressWarnings("unchecked")
    private Map<String, ?> metaGuiCho(User nguoiNhan) {
        ArgumentCaptor<Map<String, ?>> bat = ArgumentCaptor.forClass(Map.class);
        verify(notificationService)
                .push(eq(nguoiNhan), anyString(), anyString(), anyString(), bat.capture());
        return bat.getValue();
    }

    private CreateBookingRequest yeuCau(Instant batDau, int phut) {
        CreateBookingRequest r = new CreateBookingRequest();
        r.setReaderProfileId(reader.getId());
        r.setStartTime(batDau);
        r.setDurationMinutes(phut);
        return r;
    }

    private Booking lichHen(BookingStatus tt, PaymentStatus tra, Instant batDau) {
        Booking b = Booking.builder()
                .id(UUID.randomUUID())
                .user(khach)
                .readerProfile(reader)
                .startTime(batDau)
                .endTime(batDau.plus(30, ChronoUnit.MINUTES))
                .totalAmount(180_000L)
                // Cọc bằng nửa tổng, đúng như create() tính. Cột này
                // `nullable = false` nên để trống là dựng một dòng không tồn
                // tại ngoài đời — và nhánh huỷ muộn đọc thẳng vào nó.
                .depositAmount(90_000L)
                .remainingAmount(90_000L)
                .status(tt)
                .paymentStatus(tra)
                .build();
        lenient().when(bookingRepository.findByIdWithParties(b.getId())).thenReturn(Optional.of(b));
        lenient().when(bookingRepository.findByIdForUpdate(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    // =====================================================================
    // Khung giờ trống
    // =====================================================================

    @Nested
    @DisplayName("Khung giờ trống")
    class KhungGio {

        @Test
        @DisplayName("Sinh khung theo bước 15 phút trong lịch tuần")
        void sinhKhung() {
            LocalDate ngay = LocalDate.now(VN).plusDays(3);
            moLichTuan(ngay, LocalTime.of(9, 0), LocalTime.of(10, 0));

            var khung = service.availableSlots(reader.getId(), ngay, 30);

            // 09:00, 09:15, 09:30 — 09:45 + 30 phút vượt 10:00 nên không tính.
            assertAll(
                    () -> assertEquals(3, khung.size()),
                    () -> assertEquals(gio(ngay, 9, 0), khung.get(0).getStartTime()),
                    () -> assertEquals(gio(ngay, 9, 30), khung.get(0).getEndTime()),
                    () -> assertEquals(180_000L, khung.get(0).getPrice()),
                    () -> assertEquals(gio(ngay, 9, 30), khung.get(2).getStartTime()));
        }

        @Test
        @DisplayName("Ngày Reader báo bận thì không sinh khung nào, kể cả khi lịch tuần có")
        void ngayBaoBan() {
            LocalDate ngay = LocalDate.now(VN).plusDays(3);
            moLichTuan(ngay);
            when(unavailableDateRepository
                    .existsByReaderIdAndUnavailableDate(reader.getId(), ngay))
                    .thenReturn(true);

            // Nghỉ một ngày cụ thể phải thắng lịch tuần, không thì Reader không
            // có cách nào nghỉ mà không xoá cả lịch làm việc.
            assertTrue(service.availableSlots(reader.getId(), ngay, 30).isEmpty());
        }

        @Test
        @DisplayName("Không khai lịch ngày đó thì rỗng, không truy vấn booking")
        void khongKhaiLich() {
            LocalDate ngay = LocalDate.now(VN).plusDays(3);

            assertTrue(service.availableSlots(reader.getId(), ngay, 30).isEmpty());
            verify(bookingRepository, never()).findOverlapping(any(), any(), any());
        }

        @Test
        @DisplayName("Khung đã có người đặt thì bị loại khỏi danh sách gợi ý")
        void khungDaCoNguoiDat() {
            LocalDate ngay = LocalDate.now(VN).plusDays(3);
            moLichTuan(ngay, LocalTime.of(9, 0), LocalTime.of(10, 0));
            when(bookingRepository.findOverlapping(eq(reader.getId()), any(), any()))
                    .thenReturn(List.of(Booking.builder()
                            .startTime(gio(ngay, 9, 0))
                            .endTime(gio(ngay, 9, 30))
                            .build()));

            var khung = service.availableSlots(reader.getId(), ngay, 30);

            // 09:00 và 09:15 chồng lấn buổi đã đặt; chỉ 09:30 còn trống.
            assertAll(
                    () -> assertEquals(1, khung.size()),
                    () -> assertEquals(gio(ngay, 9, 30), khung.get(0).getStartTime()));
        }

        @Test
        @DisplayName("Khung đã QUA trong hôm nay thì không gợi ý")
        void khungDaQua() {
            LocalDate homNay = LocalDate.now(VN);
            // Cả ngày hôm nay tính từ 00:00 tới 00:30 — chắc chắn đã qua.
            moLichTuan(homNay, LocalTime.of(0, 0), LocalTime.of(0, 30));

            // Hiện ra khung đã qua chỉ để người dùng bấm vào rồi nhận lỗi.
            assertTrue(service.availableSlots(reader.getId(), homNay, 30).isEmpty());
        }

        @Test
        @DisplayName("Thời lượng lạ thì từ chối ngay, không dò ngày nào")
        void thoiLuongLa() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.availableSlots(reader.getId(), ngayMai, 45)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.availableSlots(reader.getId(), ngayMai, 0)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.nextAvailableDate(reader.getId(), ngayMai, 20, 14)));
            verify(readerProfileRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Reader chưa đặt giá cho mốc đó thì báo rõ mốc nào")
        void chuaDatGia() {
            reader.setPricePer60m(null);
            moLichTuan(ngayMai);

            var loi = assertThrows(IllegalArgumentException.class,
                    () -> service.availableSlots(reader.getId(), ngayMai, 60));
            assertTrue(loi.getMessage().contains("60"));
        }

        @Test
        @DisplayName("Giá 0 cũng là chưa đặt giá")
        void giaBangKhong() {
            reader.setPricePer15m(0L);
            moLichTuan(ngayMai);

            // Giá 0 lọt qua thì cả buổi xem thành miễn phí mà không ai cố ý.
            assertThrows(IllegalArgumentException.class,
                    () -> service.availableSlots(reader.getId(), ngayMai, 15));
        }

        @Test
        @DisplayName("Ngày trống gần nhất: nhảy qua ngày Reader không làm")
        void ngayTrongGanNhat() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay);

            var kq = service.nextAvailableDate(reader.getId(), LocalDate.now(VN), 30, 14);

            assertEquals(Optional.of(ngay), kq);
        }

        @Test
        @DisplayName("Reader không khai lịch bao giờ thì trả rỗng, không lặp vô ích")
        void khongCoNgayNaoTrong() {
            assertTrue(service.nextAvailableDate(reader.getId(), LocalDate.now(VN), 30, 14).isEmpty());
        }

        @Test
        @DisplayName("Reader không tồn tại thì báo đúng loại lỗi")
        void readerKhongTonTai() {
            UUID la = UUID.randomUUID();
            when(readerProfileRepository.findById(la)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.availableSlots(la, ngayMai, 30));
        }
    }

    @Nested
    @DisplayName("Lịch tháng")
    class LichThang {

        @Test
        @DisplayName("Khung đã có người đặt hiện TAKEN, không bị xoá khỏi ngày")
        void khungDaDatVanHien() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moCaTuan(ngay, LocalTime.of(9, 0), LocalTime.of(10, 0));
            when(bookingRepository.findOverlapping(eq(reader.getId()), any(), any()))
                    .thenReturn(List.of(Booking.builder()
                            .startTime(gio(ngay, 9, 0))
                            .endTime(gio(ngay, 9, 30))
                            .build()));

            var lich = service.monthCalendar(
                    reader.getId(), ngay.getYear(), ngay.getMonthValue(), 30);
            var hom = lich.getDays().stream()
                    .filter(d -> d.getDate().equals(ngay)).findFirst().orElseThrow();

            assertAll(
                    () -> assertEquals(CalendarDayKind.OPEN, hom.getKind()),
                    () -> assertEquals(CalendarSlotState.TAKEN, hom.getSlots().get(0).getState()),
                    () -> assertEquals(CalendarSlotState.TAKEN, hom.getSlots().get(1).getState()),
                    () -> assertEquals(CalendarSlotState.FREE, hom.getSlots().get(2).getState()),
                    () -> assertEquals(gio(ngay, 9, 30), hom.getSlots().get(2).getStartTime()));
            // Cả tháng chỉ một lần chồng lấn, không phải một lần mỗi ngày.
            verify(bookingRepository, times(1)).findOverlapping(eq(reader.getId()), any(), any());
        }

        @Test
        @DisplayName("Ngày nghỉ và ngày không làm việc là hai loại khác nhau")
        void nghiKhacKhongLam() {
            LocalDate lam = LocalDate.now(VN).plusDays(2);
            moCaTuan(lam, LocalTime.of(9, 0), LocalTime.of(10, 0));
            when(unavailableDateRepository.findByReaderIdAndUnavailableDateBetween(
                    eq(reader.getId()), any(), any()))
                    .thenReturn(List.of(com.exe.astratarot.domain.entity.ReaderUnavailableDate.builder()
                            .unavailableDate(lam)
                            .build()));

            var lich = service.monthCalendar(reader.getId(), lam.getYear(), lam.getMonthValue(), 30);
            var nghi = lich.getDays().stream()
                    .filter(d -> d.getDate().equals(lam)).findFirst().orElseThrow();
            assertEquals(CalendarDayKind.OFF, nghi.getKind());
            assertTrue(nghi.getSlots().isEmpty());

            lich.getDays().stream()
                    .filter(d -> !d.getDate().isBefore(LocalDate.now(VN)))
                    .filter(d -> d.getDate().getDayOfWeek() != lam.getDayOfWeek())
                    .findFirst()
                    .ifPresent(d -> assertEquals(CalendarDayKind.CLOSED, d.getKind()));
        }

        @Test
        @DisplayName("Kín cả khung còn lại thì là FULL, hết giờ hôm nay thì là OVER")
        void kinVaHetGio() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moCaTuan(ngay, LocalTime.of(9, 0), LocalTime.of(10, 0));
            when(bookingRepository.findOverlapping(eq(reader.getId()), any(), any()))
                    .thenReturn(List.of(Booking.builder()
                            .startTime(gio(ngay, 9, 0))
                            .endTime(gio(ngay, 10, 0))
                            .build()));

            var kin = service.monthCalendar(reader.getId(), ngay.getYear(), ngay.getMonthValue(), 30)
                    .getDays().stream().filter(d -> d.getDate().equals(ngay)).findFirst().orElseThrow();
            assertEquals(CalendarDayKind.FULL, kin.getKind());

            LocalDate homNay = LocalDate.now(VN);
            moCaTuan(homNay, LocalTime.of(0, 0), LocalTime.of(0, 30));
            when(bookingRepository.findOverlapping(eq(reader.getId()), any(), any()))
                    .thenReturn(List.of());
            var het = service.monthCalendar(reader.getId(), homNay.getYear(), homNay.getMonthValue(), 30)
                    .getDays().stream().filter(d -> d.getDate().equals(homNay)).findFirst().orElseThrow();
            assertEquals(CalendarDayKind.OVER, het.getKind());
            assertTrue(het.getSlots().stream().allMatch(s -> s.getState() == CalendarSlotState.PAST));
        }

        @Test
        @DisplayName("Tháng quá xa hoặc thời lượng lạ thì từ chối, không đụng dữ liệu")
        void ngoaiKhoang() {
            YearMonth xa = YearMonth.now(VN).plusMonths(4);
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.monthCalendar(reader.getId(), xa.getYear(), xa.getMonthValue(), 30)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.monthCalendar(reader.getId(), YearMonth.now(VN).getYear(), 0, 30)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.monthCalendar(reader.getId(), YearMonth.now(VN).getYear(),
                                    YearMonth.now(VN).getMonthValue(), 45)));
            verify(availabilityRepository, never()).findByReaderIdAndActiveTrue(any());
        }
    }

    // =====================================================================
    // Đặt lịch
    // =====================================================================

    @Nested
    @DisplayName("Đặt lịch")
    class DatLich {

        @Test
        @DisplayName("Đặt thành công: giá theo mốc, PENDING, và Reader được báo")
        void datThanhCong() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay);

            var kq = service.create(khach.getId(), yeuCau(gio(ngay, 10, 0), 30));

            assertAll(
                    () -> assertEquals(BookingStatus.PENDING.name(), kq.getStatus()),
                    () -> assertEquals(PaymentStatus.UNPAID.name(), kq.getPaymentStatus()),
                    () -> assertEquals(180_000L, kq.getTotalAmount()),
                    () -> assertEquals(90_000L, kq.getDepositAmount()),
                    () -> assertEquals(90_000L, kq.getRemainingAmount()),
                    () -> assertEquals(
                            kq.getStartTime().minus(12, ChronoUnit.HOURS),
                            kq.getPaymentDeadline()),
                    () -> assertEquals(30, kq.getDurationMinutes()),
                    () -> assertEquals("Reader", kq.getReaderName()),
                    () -> assertEquals("Khách", kq.getCustomerName()),
                    () -> assertFalse(kq.getReviewed()));
            verify(notificationService).push(eq(readerUser), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Thông báo đặt lịch nói rõ nó thuộc phía READER")
        void thongBaoDatLichGhiPhiaReader() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay);

            service.create(khach.getId(), yeuCau(gio(ngay, 10, 0), 30));

            // Một buổi xem có hai người và HAI danh sách khác nhau trên giao
            // diện. Nếu thông báo không nói mình thuộc phía nào thì đầu bên kia
            // phải đoán theo loại — và nó đoán sai đúng ở đây: Reader bấm vào
            // "Có lịch hẹn mới" rồi bị đưa sang danh sách phía KHÁCH, nơi trống
            // rỗng một cách hoàn toàn đúng đắn vì chính họ không đặt gì cả.
            assertEquals(NotificationTypes.SIDE_READER,
                    metaGuiCho(readerUser).get(NotificationTypes.SIDE));
        }

        @Test
        @DisplayName("Không đặt lịch với chính mình")
        void datVoiChinhMinh() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay);
            when(userRepository.findById(readerUser.getId())).thenReturn(Optional.of(readerUser));

            assertThrows(IllegalArgumentException.class,
                    () -> service.create(readerUser.getId(), yeuCau(gio(ngay, 10, 0), 30)));
        }

        @Test
        @DisplayName("Reader chưa được duyệt thì không nhận lịch")
        void readerChuaDuyet() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay);
            reader.setVerifiedAt(null);

            // Chưa duyệt nghĩa là chưa ai kiểm người này. Nhận tiền của khách
            // cho một buổi xem với người chưa kiểm là chuyện khác hẳn.
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(khach.getId(), yeuCau(gio(ngay, 10, 0), 30)));
        }

        @Test
        @DisplayName("Reader đang tạm ngưng thì không nhận lịch")
        void readerTamNgung() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay);
            reader.setAvailable(false);

            assertThrows(IllegalArgumentException.class,
                    () -> service.create(khach.getId(), yeuCau(gio(ngay, 10, 0), 30)));
        }

        @Test
        @DisplayName("Không đặt được ở thời điểm đã qua")
        void thoiDiemDaQua() {
            LocalDate homQua = LocalDate.now(VN).minusDays(1);
            moLichTuan(homQua);

            assertThrows(IllegalArgumentException.class,
                    () -> service.create(khach.getId(), yeuCau(gio(homQua, 10, 0), 30)));
        }

        @Test
        @DisplayName("Ngoài lịch làm việc thì từ chối")
        void ngoaiLichLamViec() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay, LocalTime.of(9, 0), LocalTime.of(11, 0));

            // 10:45 + 30 phút vượt quá 11:00 — nửa buổi xem nằm ngoài giờ làm.
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(khach.getId(), yeuCau(gio(ngay, 10, 45), 30)));
        }

        @Test
        @DisplayName("Buổi xem vắt qua nửa đêm thì từ chối")
        void vatQuaNuaDem() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay, LocalTime.of(23, 0), LocalTime.of(23, 59));
            // Ngày hôm sau cũng mở lịch, để chắc chắn lời từ chối đến từ luật
            // "vắt qua nửa đêm" chứ không phải vì hôm sau Reader nghỉ.
            moLichTuan(ngay.plusDays(1), LocalTime.of(0, 0), LocalTime.of(23, 59));

            assertThrows(IllegalArgumentException.class,
                    () -> service.create(khach.getId(), yeuCau(gio(ngay, 23, 45), 30)));
        }

        @Test
        @DisplayName("Ngày Reader báo bận thì từ chối")
        void ngayBaoBan() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay);
            when(unavailableDateRepository
                    .existsByReaderIdAndUnavailableDate(reader.getId(), ngay))
                    .thenReturn(true);

            assertThrows(IllegalArgumentException.class,
                    () -> service.create(khach.getId(), yeuCau(gio(ngay, 10, 0), 30)));
        }

        @Test
        @DisplayName("Khung vừa có người đặt mất thì từ chối, không ghi đè")
        void khungVuaCoNguoiDat() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay);
            when(bookingRepository.findOverlapping(eq(reader.getId()), any(), any()))
                    .thenReturn(List.of(Booking.builder().build()));

            assertThrows(IllegalArgumentException.class,
                    () -> service.create(khach.getId(), yeuCau(gio(ngay, 10, 0), 30)));
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tài khoản đã xoá mềm thì không đặt được lịch")
        void taiKhoanDaXoa() {
            LocalDate ngay = LocalDate.now(VN).plusDays(2);
            moLichTuan(ngay);
            khach.setDeletedAt(Instant.now());

            // Xoá mềm mà vẫn đặt được lịch thì "xoá" không có nghĩa gì.
            assertThrows(ResourceNotFoundException.class,
                    () -> service.create(khach.getId(), yeuCau(gio(ngay, 10, 0), 30)));
        }
    }

    // =====================================================================
    // Đổi trạng thái
    // =====================================================================

    @Nested
    @DisplayName("Đổi trạng thái")
    class DoiTrangThai {

        @Test
        @DisplayName("Reader nhận lịch: PENDING sang CONFIRMED và báo khách")
        void readerNhanLich() {
            Booking b = lichHen(BookingStatus.PENDING, PaymentStatus.UNPAID,
                    Instant.now().plus(2, ChronoUnit.DAYS));

            var kq = service.confirm(readerUser.getId(), b.getId());

            assertEquals(BookingStatus.CONFIRMED.name(), kq.getStatus());
            verify(notificationService).push(eq(khach), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Khách KHÔNG tự nhận lịch của mình được")
        void khachKhongTuNhanLich() {
            Booking b = lichHen(BookingStatus.PENDING, PaymentStatus.UNPAID,
                    Instant.now().plus(2, ChronoUnit.DAYS));

            assertThrows(AccessDeniedException.class,
                    () -> service.confirm(khach.getId(), b.getId()));
        }

        @Test
        @DisplayName("Lịch đã xác nhận thì không xác nhận lần nữa")
        void nhanLichHaiLan() {
            Booking b = lichHen(BookingStatus.CONFIRMED, PaymentStatus.UNPAID,
                    Instant.now().plus(2, ChronoUnit.DAYS));

            assertThrows(IllegalArgumentException.class,
                    () -> service.confirm(readerUser.getId(), b.getId()));
        }

        @Test
        @DisplayName("Hoàn tất TRƯỚC giờ hẹn thì bị chặn — và ký quỹ không nhả")
        void hoanTatTruocGioHen() {
            Booking b = lichHen(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                    Instant.now().plus(2, ChronoUnit.HOURS));

            // Đánh dấu COMPLETED là lệnh nhả tiền. Làm được trước giờ hẹn thì
            // đó là cách nhận tiền cho một buổi xem chưa từng diễn ra.
            assertThrows(IllegalArgumentException.class,
                    () -> service.complete(readerUser.getId(), b.getId()));
            verify(escrowService, never()).releaseForBooking(any());
        }

        @Test
        @DisplayName("Hoàn tất sau giờ hẹn khi ĐÃ trả: nhả ký quỹ cho Reader")
        void hoanTatVaNhaTien() {
            Booking b = lichHen(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                    Instant.now().minus(2, ChronoUnit.HOURS));

            var kq = service.complete(readerUser.getId(), b.getId());

            assertEquals(BookingStatus.COMPLETED.name(), kq.getStatus());
            verify(escrowService).releaseForBooking(b);
            verify(notificationService).push(eq(khach), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Hoàn tất khi CHƯA trả: vẫn COMPLETED nhưng không nhả gì")
        void hoanTatKhiChuaTra() {
            Booking b = lichHen(BookingStatus.CONFIRMED, PaymentStatus.UNPAID,
                    Instant.now().minus(2, ChronoUnit.HOURS));

            service.complete(readerUser.getId(), b.getId());

            // Buổi chưa thanh toán thì trong ký quỹ không có gì để nhả. Gọi
            // release ở đây là chi ra một khoản chưa từng thu.
            assertEquals(BookingStatus.COMPLETED, b.getStatus());
            verify(escrowService, never()).releaseForBooking(any());
        }

        @Test
        @DisplayName("Chỉ hoàn tất được lịch ĐÃ xác nhận")
        void hoanTatLichChuaXacNhan() {
            Booking b = lichHen(BookingStatus.PENDING, PaymentStatus.PAID,
                    Instant.now().minus(2, ChronoUnit.HOURS));

            assertThrows(IllegalArgumentException.class,
                    () -> service.complete(readerUser.getId(), b.getId()));
        }

        @Test
        @DisplayName("Lịch hẹn không tồn tại thì báo đúng loại lỗi")
        void lichHenKhongTonTai() {
            UUID la = UUID.randomUUID();
            when(bookingRepository.findByIdForUpdate(la)).thenReturn(Optional.empty());
            when(bookingRepository.findByIdWithParties(la)).thenReturn(Optional.empty());

            assertAll(
                    () -> assertThrows(ResourceNotFoundException.class,
                            () -> service.complete(readerUser.getId(), la)),
                    () -> assertThrows(ResourceNotFoundException.class,
                            () -> service.cancel(khach.getId(), la, "lý do", ActorType.USER)),
                    () -> assertThrows(ResourceNotFoundException.class,
                            () -> service.confirm(readerUser.getId(), la)));
        }
    }

    // =====================================================================
    // Huỷ
    // =====================================================================

    @Nested
    @DisplayName("Huỷ lịch")
    class Huy {

        @Test
        @DisplayName("Khách huỷ: gọi hoàn tiền, và báo cho READER chứ không báo lại khách")
        void khachHuy() {
            Booking b = lichHen(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                    Instant.now().plus(1, ChronoUnit.DAYS));

            var kq = service.cancel(khach.getId(), b.getId(), "  Có việc gấp  ", ActorType.USER);

            assertAll(
                    () -> assertEquals(BookingStatus.CANCELLED.name(), kq.getStatus()),
                    () -> assertEquals("Có việc gấp", kq.getCancelReason()));
            // Huỷ mà không đi qua hoàn tiền thì khoản khách đã trả nằm lại trong
            // ký quỹ, không của ai. Huỷ trước hạn 12h thì hoàn TRỌN số đã trả —
            // kiểm cả con số, vì hoàn thiếu cũng là gọi đúng hàm.
            verify(paymentService).refund(b, 180_000L);
            verify(paymentService, never()).forfeitDeposit(any());
            verify(notificationService).push(eq(readerUser), anyString(), anyString(), anyString(), any());
            verify(notificationService, never()).push(eq(khach), anyString(), anyString(), anyString(), any());
            // Khách huỷ → người nhận đang đứng ở vai Reader. Đây là loại thông
            // báo đi được CẢ HAI chiều, nên phía phải do người gửi nói ra.
            assertEquals(NotificationTypes.SIDE_READER,
                    metaGuiCho(readerUser).get(NotificationTypes.SIDE));
        }

        @Test
        @DisplayName("Reader huỷ: báo cho KHÁCH")
        void readerHuy() {
            Booking b = lichHen(BookingStatus.CONFIRMED, PaymentStatus.UNPAID,
                    Instant.now().plus(1, ChronoUnit.DAYS));

            service.cancel(readerUser.getId(), b.getId(), null, ActorType.READER);

            assertNull(b.getCancelReason());
            verify(notificationService).push(eq(khach), anyString(), anyString(), anyString(), any());
            verify(notificationService, never()).push(eq(readerUser), anyString(), anyString(), anyString(), any());
            // Cùng một loại thông báo, phía ngược lại hẳn so với khi khách huỷ.
            assertEquals(NotificationTypes.SIDE_CUSTOMER,
                    metaGuiCho(khach).get(NotificationTypes.SIDE));
        }

        @Test
        @DisplayName("Lý do chỉ có dấu cách thì coi như không có lý do")
        void lyDoRong() {
            Booking b = lichHen(BookingStatus.PENDING, PaymentStatus.UNPAID,
                    Instant.now().plus(1, ChronoUnit.DAYS));

            service.cancel(khach.getId(), b.getId(), "   ", ActorType.USER);

            assertNull(b.getCancelReason());
        }

        @Test
        @DisplayName("Buổi đã hoàn tất thì không huỷ được")
        void daHoanTat() {
            Booking b = lichHen(BookingStatus.COMPLETED, PaymentStatus.PAID,
                    Instant.now().minus(1, ChronoUnit.DAYS));

            // Huỷ sau khi đã nhả tiền cho Reader là đòi lại khoản đã chi.
            assertThrows(IllegalArgumentException.class,
                    () -> service.cancel(khach.getId(), b.getId(), "đổi ý", ActorType.USER));
            verify(paymentService, never()).refund(any(), anyLong());
        }

        @Test
        @DisplayName("Huỷ hai lần thì bị chặn — không gọi hoàn tiền hai lần")
        void huyHaiLan() {
            Booking b = lichHen(BookingStatus.CANCELLED, PaymentStatus.REFUNDED,
                    Instant.now().plus(1, ChronoUnit.DAYS));

            assertThrows(IllegalArgumentException.class,
                    () -> service.cancel(khach.getId(), b.getId(), "lại huỷ", ActorType.USER));
            verify(paymentService, never()).refund(any(), anyLong());
        }

        @Test
        @DisplayName("Người ngoài không huỷ được lịch của người khác")
        void nguoiNgoaiHuy() {
            Booking b = lichHen(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                    Instant.now().plus(1, ChronoUnit.DAYS));

            assertThrows(AccessDeniedException.class,
                    () -> service.cancel(UUID.randomUUID(), b.getId(), "tôi thích", ActorType.USER));
        }
    }

    // =====================================================================
    // Ghi chú buổi xem
    // =====================================================================

    @Nested
    @DisplayName("Ghi chú của Reader")
    class GhiChu {

        @Test
        @DisplayName("Ghi chú sau buổi xem: lưu bản đã trim và báo khách")
        void ghiChuSauBuoiXem() {
            Booking b = lichHen(BookingStatus.COMPLETED, PaymentStatus.PAID,
                    Instant.now().minus(2, ChronoUnit.HOURS));

            var kq = service.saveReaderNote(readerUser.getId(), b.getId(), "  Ba lá Thần Tháp  ");

            assertAll(
                    () -> assertEquals("Ba lá Thần Tháp", kq.getReaderNote()),
                    () -> assertTrue(kq.getReaderNoteAt() != null));
            verify(notificationService).push(eq(khach), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Ghi chú TRƯỚC giờ hẹn thì bị chặn")
        void ghiChuTruocGioHen() {
            Booking b = lichHen(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                    Instant.now().plus(2, ChronoUnit.HOURS));

            // Ghi chú là bản tường thuật một buổi đã diễn ra. Viết trước giờ hẹn
            // thì nó là một lời hứa, và sẽ thành bằng chứng sai lệch nếu buổi
            // xem bị huỷ.
            assertThrows(IllegalArgumentException.class,
                    () -> service.saveReaderNote(readerUser.getId(), b.getId(), "sẽ rất tốt"));
        }

        @Test
        @DisplayName("Buổi đã huỷ thì không ghi chú được")
        void buoiDaHuy() {
            Booking b = lichHen(BookingStatus.CANCELLED, PaymentStatus.REFUNDED,
                    Instant.now().minus(2, ChronoUnit.HOURS));

            assertThrows(IllegalArgumentException.class,
                    () -> service.saveReaderNote(readerUser.getId(), b.getId(), "vẫn ghi"));
        }

        @Test
        @DisplayName("Xoá ghi chú thì KHÔNG gửi thông báo đã gửi ghi chú")
        void xoaGhiChu() {
            Booking b = lichHen(BookingStatus.COMPLETED, PaymentStatus.PAID,
                    Instant.now().minus(2, ChronoUnit.HOURS));
            b.setReaderNote("nội dung cũ");
            b.setReaderNoteAt(Instant.now().minus(1, ChronoUnit.HOURS));

            var kq = service.saveReaderNote(readerUser.getId(), b.getId(), "   ");

            // Xoá ghi chú mà vẫn gửi "Reader đã gửi ghi chú" là nói dối khách.
            assertAll(
                    () -> assertNull(kq.getReaderNote()),
                    () -> assertNull(kq.getReaderNoteAt()));
            verify(notificationService, never()).push(any(), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Khách không ghi chú thay Reader được")
        void khachKhongGhiChu() {
            Booking b = lichHen(BookingStatus.COMPLETED, PaymentStatus.PAID,
                    Instant.now().minus(2, ChronoUnit.HOURS));

            assertThrows(AccessDeniedException.class,
                    () -> service.saveReaderNote(khach.getId(), b.getId(), "tôi tự ghi"));
        }
    }

    // =====================================================================
    // Đọc danh sách
    // =====================================================================

    @Nested
    @DisplayName("Đọc danh sách")
    class DocDanhSach {

        @Test
        @DisplayName("Danh sách của khách mang đúng cờ đã đánh giá")
        void danhSachKhach() {
            Booking daDanhGia = lichHen(BookingStatus.COMPLETED, PaymentStatus.PAID,
                    Instant.now().minus(3, ChronoUnit.DAYS));
            Booking chuaDanhGia = lichHen(BookingStatus.COMPLETED, PaymentStatus.PAID,
                    Instant.now().minus(2, ChronoUnit.DAYS));
            when(bookingRepository.findForUser(eq(khach.getId()), eq(null), any()))
                    .thenReturn(new PageImpl<>(List.of(daDanhGia, chuaDanhGia)));
            when(reviewRepository.findReviewedBookingIds(any()))
                    .thenReturn(Set.of(daDanhGia.getId()));

            var trang = service.listForCustomer(khach.getId(), null, PageRequest.of(0, 10));

            // Một câu hỏi cho cả trang, không phải một câu cho từng dòng.
            assertAll(
                    () -> assertEquals(2, trang.getTotalElements()),
                    () -> assertTrue(trang.getContent().get(0).getReviewed()),
                    () -> assertFalse(trang.getContent().get(1).getReviewed()));
        }

        @Test
        @DisplayName("Trang rỗng thì không hỏi bảng đánh giá")
        void trangRong() {
            when(bookingRepository.findForReader(eq(readerUser.getId()), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            assertEquals(0, service.listForReader(readerUser.getId(), "PENDING",
                    PageRequest.of(0, 10)).getTotalElements());
            verify(reviewRepository, never()).findReviewedBookingIds(any());
        }

        @Test
        @DisplayName("Lọc theo trạng thái nhận cả chữ thường")
        void locChuThuong() {
            when(bookingRepository.findForUser(eq(khach.getId()), eq(BookingStatus.CONFIRMED), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            assertEquals(0, service.listForCustomer(khach.getId(), "  confirmed  ",
                    PageRequest.of(0, 10)).getTotalElements());
        }

        @Test
        @DisplayName("Trạng thái không có thật thì báo lỗi, không lặng lẽ trả hết")
        void trangThaiKhongCoThat() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.listForCustomer(khach.getId(), "DA_XONG_ROI", PageRequest.of(0, 10)));
        }

        @Test
        @DisplayName("Xem chi tiết: chỉ hai bên của buổi xem")
        void xemChiTiet() {
            Booking b = lichHen(BookingStatus.CONFIRMED, PaymentStatus.PAID,
                    Instant.now().plus(1, ChronoUnit.DAYS));

            assertAll(
                    () -> assertEquals(b.getId(), service.get(khach.getId(), b.getId()).getId()),
                    () -> assertEquals(b.getId(), service.get(readerUser.getId(), b.getId()).getId()),
                    () -> assertThrows(AccessDeniedException.class,
                            () -> service.get(UUID.randomUUID(), b.getId())));
        }
    }
}
