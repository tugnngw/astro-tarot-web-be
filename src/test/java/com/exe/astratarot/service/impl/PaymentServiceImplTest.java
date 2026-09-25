package com.exe.astratarot.service.impl;

import com.exe.astratarot.config.PayOsConfig.PayOsClient;
import com.exe.astratarot.config.PayOsProperties;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.PaymentTransaction;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.PaymentTransactionRepository;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tiền vào. Lớp này trước đây có độ phủ 23,1% — 183 dòng chưa từng được kiểm,
 * và là những dòng quyết định ai trả bao nhiêu cho ai.
 *
 * <p>Ba bất biến đáng kiểm hơn cả:
 *
 * <ol>
 *   <li><b>Không tạo hai lần một khoản nợ.</b> Khách bấm "Thanh toán" hai lần
 *       thì phải nhận lại đúng mã chuyển khoản cũ, không phải mã thứ hai — hai
 *       mã cho một buổi xem là hai lần đối soát và một lần thu trùng.
 *   <li><b>Trạng thái chỉ đi một chiều.</b> PENDING sang SUCCESS hoặc FAILED,
 *       và không quay lại. Xác nhận một giao dịch đã xác nhận là ghi ký quỹ hai
 *       lần.
 *   <li><b>Webhook lạ không được thành 5xx.</b> Đây đã là lỗi thật: PayOS gọi
 *       thử bằng orderCode không tồn tại, ta trả 500, PayOS kết luận URL hỏng
 *       và từ chối đăng ký webhook. Bộ kiểm này chốt lại hành vi ấy.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private EscrowService escrowService;
    @Mock private NotificationService notificationService;
    @Mock private ActivityLogService activityLogService;

    private final PayOsProperties payOsProperties = new PayOsProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private PaymentServiceImpl service;

    private User khach;
    private User reader;
    private Booking booking;
    private final UUID bookingId = UUID.randomUUID();

    /** Dựng service với PayOS tắt (đường chuyển khoản tay) hoặc bật. */
    private PaymentServiceImpl dungService(PayOsClient client) {
        PaymentServiceImpl s = new PaymentServiceImpl(
                transactionRepository, bookingRepository, escrowService,
                notificationService, activityLogService, client,
                payOsProperties, objectMapper);
        // @Value không chạy trong unit test — nạp tay cho giống cấu hình thật.
        ReflectionTestUtils.setField(s, "bankName", "MB Bank");
        ReflectionTestUtils.setField(s, "bankAccountNumber", "0123456789");
        ReflectionTestUtils.setField(s, "bankAccountHolder", "ASTRA TAROT");
        ReflectionTestUtils.setField(s, "frontendUrl", "https://astrotarot.date/");
        return s;
    }

    @BeforeEach
    void setUp() {
        khach = new User();
        khach.setId(UUID.randomUUID());
        khach.setFullName("Khách");
        khach.setEmail("khach@example.com");

        reader = new User();
        reader.setId(UUID.randomUUID());
        reader.setFullName("Reader");

        ReaderProfile hoSo = ReaderProfile.builder().id(UUID.randomUUID()).user(reader).build();

        booking = Booking.builder()
                .id(bookingId)
                .user(khach)
                .readerProfile(hoSo)
                .totalAmount(250_000L)
                .status(BookingStatus.PENDING)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();

        lenient().when(bookingRepository.findByIdWithParties(bookingId))
                .thenReturn(Optional.of(booking));
        lenient().when(transactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(inv -> {
                    PaymentTransaction t = inv.getArgument(0);
                    if (t.getId() == null) {
                        t.setId(UUID.randomUUID());
                    }
                    return t;
                });
        // Mặc định: mã sinh ra chưa ai dùng.
        lenient().when(transactionRepository.findByExternalTransactionId(anyString()))
                .thenReturn(Optional.empty());

        service = dungService(PayOsClient.disabled());
    }

    private PaymentTransaction giaoDich(TransactionStatus tt, String phuongThuc) {
        PaymentTransaction t = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .user(khach)
                .amount(booking.getTotalAmount())
                .paymentMethod(phuongThuc)
                .externalTransactionId("ATBXYZ1234")
                .status(tt)
                .build();
        lenient().when(transactionRepository.findById(t.getId())).thenReturn(Optional.of(t));
        return t;
    }

    // =====================================================================
    // Khách tạo yêu cầu trả tiền
    // =====================================================================

    @Nested
    @DisplayName("Tạo yêu cầu thanh toán")
    class TaoYeuCau {

        @Test
        @DisplayName("Chuyển khoản tay: sinh mã tham chiếu và trả đủ số tài khoản")
        void chuyenKhoanTay() {
            var kq = service.createPaymentIntent(khach.getId(), bookingId);

            assertAll(
                    () -> assertEquals("BANK_TRANSFER", kq.getPaymentMethod()),
                    () -> assertEquals(250_000L, kq.getAmount()),
                    () -> assertEquals("MB Bank", kq.getBankName()),
                    () -> assertEquals("0123456789", kq.getBankAccountNumber()),
                    () -> assertEquals("ASTRA TAROT", kq.getBankAccountHolder()),
                    () -> assertEquals(TransactionStatus.PENDING.name(), kq.getStatus()),
                    // Mã tham chiếu chính là thứ nhân viên dùng để đối soát —
                    // thiếu nó thì khoản chuyển vào không biết của ai.
                    () -> assertNotNull(kq.getReferenceCode()),
                    () -> assertTrue(kq.getReferenceCode().startsWith("ATB")),
                    () -> assertEquals(10, kq.getReferenceCode().length()),
                    () -> assertEquals(kq.getReferenceCode(), kq.getTransferContent()));
        }

        @Test
        @DisplayName("Bấm hai lần thì DÙNG LẠI giao dịch cũ, không sinh mã thứ hai")
        void bamHaiLanThiDungLaiMaCu() {
            PaymentTransaction cu = giaoDich(TransactionStatus.PENDING, "BANK_TRANSFER");
            when(transactionRepository.findFirstByBookingIdAndStatus(
                    bookingId, TransactionStatus.PENDING))
                    .thenReturn(Optional.of(cu));

            var kq = service.createPaymentIntent(khach.getId(), bookingId);

            // Hai mã cho một buổi xem là hai lần đối soát và rất dễ thu trùng.
            assertAll(
                    () -> assertEquals(cu.getId(), kq.getTransactionId()),
                    () -> assertEquals("ATBXYZ1234", kq.getReferenceCode()));
            verify(transactionRepository, never()).save(any(PaymentTransaction.class));
        }

        @Test
        @DisplayName("Lịch hẹn của người khác thì bị chặn")
        void lichHenCuaNguoiKhac() {
            UUID nguoiLa = UUID.randomUUID();

            // Trả tiền cho lịch hẹn của người khác không chỉ vô nghĩa — nó cho
            // người lạ biết buổi xem ấy tồn tại và giá bao nhiêu.
            assertThrows(AccessDeniedException.class,
                    () -> service.createPaymentIntent(nguoiLa, bookingId));
        }

        @Test
        @DisplayName("Lịch hẹn đã huỷ thì không thu tiền")
        void lichHenDaHuy() {
            booking.setStatus(BookingStatus.CANCELLED);

            assertThrows(IllegalArgumentException.class,
                    () -> service.createPaymentIntent(khach.getId(), bookingId));
        }

        @Test
        @DisplayName("Lịch hẹn đã trả rồi thì không thu lần nữa")
        void lichHenDaTra() {
            booking.setPaymentStatus(PaymentStatus.PAID);

            assertThrows(IllegalArgumentException.class,
                    () -> service.createPaymentIntent(khach.getId(), bookingId));
        }

        @Test
        @DisplayName("Lịch hẹn không tồn tại thì báo đúng loại lỗi")
        void lichHenKhongTonTai() {
            UUID la = UUID.randomUUID();
            when(bookingRepository.findByIdWithParties(la)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.createPaymentIntent(khach.getId(), la));
        }

        @Test
        @DisplayName("PayOS bật: trả về link thanh toán và mã QR")
        void payOsBat() {
            PayOS sdk = mock(PayOS.class, RETURNS_DEEP_STUBS);
            // Builder cua SDK danh dau non-null gan het cac truong, nen mock
            // nhe hon va chi noi ve nhung truong service thuc su doc.
            CreatePaymentLinkResponse link = mock(CreatePaymentLinkResponse.class);
            when(link.getBin()).thenReturn("970422");
            when(link.getAccountNumber()).thenReturn("999888777");
            when(link.getAccountName()).thenReturn("CONG TY ASTRA");
            when(link.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/abc");
            when(link.getQrCode()).thenReturn("00020101021238");
            when(link.getPaymentLinkId()).thenReturn("plink-1");
            when(sdk.paymentRequests().create(any())).thenReturn(link);

            var kq = dungService(new PayOsClient(sdk))
                    .createPaymentIntent(khach.getId(), bookingId);

            assertAll(
                    () -> assertEquals("PAYOS", kq.getPaymentMethod()),
                    () -> assertEquals("https://pay.payos.vn/web/abc", kq.getCheckoutUrl()),
                    () -> assertEquals("00020101021238", kq.getQrCode()),
                    // Số tài khoản của PayOS phải thắng số tài khoản cấu hình
                    // sẵn — chuyển vào tài khoản của ta thì PayOS không biết để
                    // đối soát.
                    () -> assertEquals("999888777", kq.getBankAccountNumber()),
                    () -> assertEquals("970422", kq.getBankName()));
        }

        @Test
        @DisplayName("PayOS lỗi thì báo lỗi rõ, không lặng lẽ rơi về chuyển khoản tay")
        void payOsLoi() {
            PayOS sdk = mock(PayOS.class, RETURNS_DEEP_STUBS);
            when(sdk.paymentRequests().create(any()))
                    .thenThrow(new RuntimeException("502 Bad Gateway"));

            PaymentServiceImpl s = dungService(new PayOsClient(sdk));

            // Rơi lặng lẽ về chuyển khoản tay là tệ hơn báo lỗi: khách chuyển
            // vào tài khoản ta, PayOS không biết, và không ai đối soát.
            assertThrows(IllegalStateException.class,
                    () -> s.createPaymentIntent(khach.getId(), bookingId));
        }

        @Test
        @DisplayName("Dùng lại giao dịch PayOS cũ thì đọc lại link từ metadata")
        void dungLaiGiaoDichPayOs() throws Exception {
            PaymentTransaction cu = giaoDich(TransactionStatus.PENDING, "PAYOS");
            cu.setMetadata(objectMapper.writeValueAsString(Map.of(
                    "checkoutUrl", "https://pay.payos.vn/web/xyz",
                    "qrCode", "QR",
                    "accountNumber", "111222333",
                    "accountName", "CONG TY ASTRA",
                    "bin", "970422")));
            when(transactionRepository.findFirstByBookingIdAndStatus(
                    bookingId, TransactionStatus.PENDING))
                    .thenReturn(Optional.of(cu));

            var kq = service.createPaymentIntent(khach.getId(), bookingId);

            assertAll(
                    () -> assertEquals("https://pay.payos.vn/web/xyz", kq.getCheckoutUrl()),
                    () -> assertEquals("111222333", kq.getBankAccountNumber()));
        }

        @Test
        @DisplayName("Metadata hỏng thì rơi về số tài khoản cấu hình, không nổ")
        void metadataHong() {
            PaymentTransaction cu = giaoDich(TransactionStatus.PENDING, "PAYOS");
            cu.setMetadata("{ day khong phai JSON");
            when(transactionRepository.findFirstByBookingIdAndStatus(
                    bookingId, TransactionStatus.PENDING))
                    .thenReturn(Optional.of(cu));

            // Một dòng metadata hỏng không được chặn khách trả tiền.
            var kq = service.createPaymentIntent(khach.getId(), bookingId);
            assertEquals("0123456789", kq.getBankAccountNumber());
        }
    }

    // =====================================================================
    // Webhook PayOS
    // =====================================================================

    @Nested
    @DisplayName("Webhook PayOS")
    class Webhook {

        private WebhookData goiTin(String code, Long orderCode, Long amount) {
            // Setter cua WebhookData co @NonNull nen chi set khi co gia tri.
            WebhookData d = new WebhookData();
            d.setCode(code);
            if (orderCode != null) {
                d.setOrderCode(orderCode);
            }
            if (amount != null) {
                d.setAmount(amount);
            }
            return d;
        }

        private PaymentServiceImpl voiGoiTin(WebhookData d) {
            PayOS sdk = mock(PayOS.class, RETURNS_DEEP_STUBS);
            when(sdk.webhooks().verify(any())).thenReturn(d);
            return dungService(new PayOsClient(sdk));
        }

        @Test
        @DisplayName("PayOS chưa cấu hình thì webhook bị từ chối")
        void chuaCauHinh() {
            assertThrows(IllegalStateException.class,
                    () -> service.handlePayOsWebhook("{}"));
        }

        @Test
        @DisplayName("orderCode LẠ thì báo nhận rồi thôi — không được thành 5xx")
        void orderCodeLa() {
            PaymentServiceImpl s = voiGoiTin(goiTin("00", 123L, 250_000L));
            when(transactionRepository.findByExternalTransactionId("123"))
                    .thenReturn(Optional.empty());

            // Đây là lỗi thật đã gặp: PayOS gọi thử bằng orderCode = 123 lúc
            // đăng ký webhook. Ném ngoại lệ ở đây thành HTTP 500, PayOS kết
            // luận URL hỏng và webhook chưa từng đăng ký được.
            s.handlePayOsWebhook("{}");

            verify(escrowService, never()).holdForBooking(any());
        }

        @Test
        @DisplayName("Thiếu orderCode thì từ chối")
        void thieuOrderCode() {
            PaymentServiceImpl s = voiGoiTin(goiTin("00", null, 250_000L));

            assertThrows(IllegalArgumentException.class, () -> s.handlePayOsWebhook("{}"));
        }

        @Test
        @DisplayName("verify trả null thì từ chối")
        void verifyTraNull() {
            PaymentServiceImpl s = voiGoiTin(null);

            assertThrows(IllegalArgumentException.class, () -> s.handlePayOsWebhook("{}"));
        }

        @Test
        @DisplayName("code khác 00 thì KHÔNG ghi nhận đã trả")
        void codeThatBai() {
            PaymentTransaction tx = giaoDich(TransactionStatus.PENDING, "PAYOS");
            tx.setExternalTransactionId("777");
            when(transactionRepository.findByExternalTransactionId("777"))
                    .thenReturn(Optional.of(tx));

            voiGoiTin(goiTin("01", 777L, 250_000L)).handlePayOsWebhook("{}");

            assertAll(
                    () -> assertEquals(TransactionStatus.PENDING, tx.getStatus()),
                    () -> assertEquals(PaymentStatus.UNPAID, booking.getPaymentStatus()));
            verify(escrowService, never()).holdForBooking(any());
        }

        @Test
        @DisplayName("Số tiền LỆCH thì từ chối, không ghi nhận")
        void soTienLech() {
            PaymentTransaction tx = giaoDich(TransactionStatus.PENDING, "PAYOS");
            tx.setExternalTransactionId("777");
            when(transactionRepository.findByExternalTransactionId("777"))
                    .thenReturn(Optional.of(tx));
            PaymentServiceImpl s = voiGoiTin(goiTin("00", 777L, 1_000L));

            // Ghi nhận đủ khi khách trả 1.000 đồng cho buổi 250.000 đồng là mất
            // tiền thật, và ký quỹ sẽ chi ra số không có.
            assertThrows(IllegalArgumentException.class, () -> s.handlePayOsWebhook("{}"));
            assertEquals(PaymentStatus.UNPAID, booking.getPaymentStatus());
        }

        @Test
        @DisplayName("Webhook gửi TRÙNG thì không ghi ký quỹ hai lần")
        void webhookTrung() {
            PaymentTransaction tx = giaoDich(TransactionStatus.SUCCESS, "PAYOS");
            tx.setExternalTransactionId("777");
            when(transactionRepository.findByExternalTransactionId("777"))
                    .thenReturn(Optional.of(tx));

            voiGoiTin(goiTin("00", 777L, 250_000L)).handlePayOsWebhook("{}");

            // PayOS gửi lại khi không nhận được 200. Ghi ký quỹ lần hai là cộng
            // tiền cho Reader hai lần cho một buổi xem.
            verify(escrowService, never()).holdForBooking(any());
        }

        @Test
        @DisplayName("Webhook thành công: ghi ký quỹ, báo cả khách và Reader")
        void webhookThanhCong() {
            PaymentTransaction tx = giaoDich(TransactionStatus.PENDING, "PAYOS");
            tx.setExternalTransactionId("777");
            when(transactionRepository.findByExternalTransactionId("777"))
                    .thenReturn(Optional.of(tx));

            voiGoiTin(goiTin("00", 777L, 250_000L)).handlePayOsWebhook("{}");

            assertAll(
                    () -> assertEquals(TransactionStatus.SUCCESS, tx.getStatus()),
                    () -> assertEquals(PaymentStatus.PAID, booking.getPaymentStatus()));
            verify(escrowService).holdForBooking(booking);
            verify(notificationService).push(eq(khach), anyString(), anyString(), anyString(), any());
            verify(notificationService).push(eq(reader), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Tiền về MUỘN sau khi khách đã huỷ thì vào luôn diện hoàn")
        void tienVeMuonSauKhiHuy() {
            booking.setStatus(BookingStatus.CANCELLED);
            PaymentTransaction tx = giaoDich(TransactionStatus.PENDING, "PAYOS");
            tx.setExternalTransactionId("777");
            when(transactionRepository.findByExternalTransactionId("777"))
                    .thenReturn(Optional.of(tx));
            when(transactionRepository.findByBookingIdOrderByCreatedAtDesc(bookingId))
                    .thenReturn(List.of(tx));

            voiGoiTin(goiTin("00", 777L, 250_000L)).handlePayOsWebhook("{}");

            // Khách bấm huỷ rồi ngân hàng mới đẩy tiền sang. Không tự chuyển
            // sang diện hoàn thì khoản ấy nằm im trong ký quỹ, không của ai.
            assertAll(
                    () -> assertEquals(PaymentStatus.REFUNDED, booking.getPaymentStatus()),
                    () -> assertEquals(TransactionStatus.CANCELLED, tx.getStatus()));
            verify(escrowService).refundForBooking(booking);
        }
    }

    // =====================================================================
    // Quản trị viên đối soát
    // =====================================================================

    @Nested
    @DisplayName("Đối soát tay")
    class DoiSoat {

        private final UUID adminId = UUID.randomUUID();

        @Test
        @DisplayName("Xác nhận: chuyển SUCCESS, ghi ký quỹ, ghi nhật ký")
        void xacNhan() {
            PaymentTransaction tx = giaoDich(TransactionStatus.PENDING, "BANK_TRANSFER");

            var kq = service.confirm(adminId, tx.getId());

            assertAll(
                    () -> assertEquals(TransactionStatus.SUCCESS.name(), kq.getStatus()),
                    () -> assertEquals(PaymentStatus.PAID, booking.getPaymentStatus()),
                    () -> assertEquals("Khách", kq.getPayerName()),
                    () -> assertEquals("Reader", kq.getReaderName()));
            verify(escrowService).holdForBooking(booking);
            verify(activityLogService).record(eq(adminId), anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("Xác nhận LẦN HAI thì bị chặn")
        void xacNhanLanHai() {
            PaymentTransaction tx = giaoDich(TransactionStatus.SUCCESS, "BANK_TRANSFER");

            // Không chặn thì ký quỹ cộng tiền cho Reader mỗi lần admin bấm.
            assertThrows(IllegalArgumentException.class,
                    () -> service.confirm(adminId, tx.getId()));
            verify(escrowService, never()).holdForBooking(any());
        }

        @Test
        @DisplayName("Từ chối: chuyển FAILED và nói cho khách biết lý do")
        void tuChoi() {
            PaymentTransaction tx = giaoDich(TransactionStatus.PENDING, "BANK_TRANSFER");

            var kq = service.reject(adminId, tx.getId(), "Sai nội dung chuyển khoản");

            assertEquals(TransactionStatus.FAILED.name(), kq.getStatus());
            verify(notificationService).push(eq(khach), anyString(), anyString(),
                    eq("Sai nội dung chuyển khoản"), any());
            verify(escrowService, never()).holdForBooking(any());
        }

        @Test
        @DisplayName("Từ chối không nêu lý do thì vẫn có câu giải thích cho khách")
        void tuChoiKhongLyDo() {
            PaymentTransaction tx = giaoDich(TransactionStatus.PENDING, "BANK_TRANSFER");

            service.reject(adminId, tx.getId(), "   ");

            // Báo "chưa đối soát được" mà không kèm gì thì khách không biết phải
            // làm gì tiếp.
            verify(notificationService).push(eq(khach), anyString(), anyString(),
                    eq("Chúng tôi chưa tìm thấy khoản chuyển khớp với mã của bạn. "
                            + "Kiểm tra lại giúp nhé."), any());
        }

        @Test
        @DisplayName("Từ chối giao dịch đã xử lý thì bị chặn")
        void tuChoiGiaoDichDaXuLy() {
            PaymentTransaction tx = giaoDich(TransactionStatus.FAILED, "BANK_TRANSFER");

            assertThrows(IllegalArgumentException.class,
                    () -> service.reject(adminId, tx.getId(), "lý do"));
        }

        @Test
        @DisplayName("Giao dịch không tồn tại thì báo đúng loại lỗi")
        void giaoDichKhongTonTai() {
            UUID la = UUID.randomUUID();
            when(transactionRepository.findById(la)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.confirm(adminId, la));
        }

        @Test
        @DisplayName("Xác nhận giao dịch KHÔNG gắn lịch hẹn thì bị chặn")
        void giaoDichKhongGanLichHen() {
            PaymentTransaction tx = PaymentTransaction.builder()
                    .id(UUID.randomUUID())
                    .user(khach)
                    .amount(250_000L)
                    .status(TransactionStatus.PENDING)
                    .build();
            when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

            assertThrows(IllegalArgumentException.class,
                    () -> service.confirm(adminId, tx.getId()));
        }

        @Test
        @DisplayName("Lọc danh sách theo trạng thái, chữ thường cũng nhận")
        void locTheoTrangThai() {
            PaymentTransaction tx = giaoDich(TransactionStatus.SUCCESS, "PAYOS");
            when(transactionRepository.search(eq(TransactionStatus.SUCCESS), any()))
                    .thenReturn(new PageImpl<>(List.of(tx)));

            Page<?> kq = service.list("  success  ", PageRequest.of(0, 10));
            assertEquals(1, kq.getTotalElements());
        }

        @Test
        @DisplayName("Không lọc thì truyền null xuống truy vấn")
        void khongLoc() {
            when(transactionRepository.search(eq(null), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            assertEquals(0, service.list("  ", PageRequest.of(0, 10)).getTotalElements());
            assertEquals(0, service.list(null, PageRequest.of(0, 10)).getTotalElements());
        }

        @Test
        @DisplayName("Trạng thái không có thật thì báo lỗi, không lặng lẽ trả hết")
        void trangThaiKhongCoThat() {
            // Trả về toàn bộ danh sách khi tham số sai là kiểu lỗi khó thấy
            // nhất: trang trông vẫn chạy, chỉ là hiện sai.
            assertThrows(IllegalArgumentException.class,
                    () -> service.list("DA_TRA_ROI", PageRequest.of(0, 10)));
        }
    }

    // =====================================================================
    // Hoàn tiền
    // =====================================================================

    @Nested
    @DisplayName("Hoàn tiền khi huỷ")
    class HoanTien {

        @Test
        @DisplayName("Chưa trả thì không hoàn — và tuyệt đối không gọi ký quỹ")
        void chuaTraThiKhongHoan() {
            booking.setPaymentStatus(PaymentStatus.UNPAID);

            service.refundIfPaid(booking);

            // Gọi refund cho buổi chưa trả là chi ra một khoản chưa từng thu.
            verify(escrowService, never()).refundForBooking(any());
            verify(notificationService, never()).push(any(), anyString(), anyString(), anyString(), any());
            assertEquals(PaymentStatus.UNPAID, booking.getPaymentStatus());
        }

        @Test
        @DisplayName("Đã hoàn rồi thì không hoàn lần nữa")
        void daHoanRoi() {
            booking.setPaymentStatus(PaymentStatus.REFUNDED);

            service.refundIfPaid(booking);

            verify(escrowService, never()).refundForBooking(any());
        }

        @Test
        @DisplayName("Đã trả: hoàn ký quỹ, đánh dấu REFUNDED, huỷ giao dịch SUCCESS")
        void daTraThiHoan() {
            booking.setPaymentStatus(PaymentStatus.PAID);
            PaymentTransaction thanhCong = giaoDich(TransactionStatus.SUCCESS, "PAYOS");
            PaymentTransaction thatBai = giaoDich(TransactionStatus.FAILED, "BANK_TRANSFER");
            when(transactionRepository.findByBookingIdOrderByCreatedAtDesc(bookingId))
                    .thenReturn(List.of(thatBai, thanhCong));

            service.refundIfPaid(booking);

            assertAll(
                    () -> assertEquals(PaymentStatus.REFUNDED, booking.getPaymentStatus()),
                    () -> assertEquals(TransactionStatus.CANCELLED, thanhCong.getStatus()),
                    // Giao dịch thất bại phải giữ nguyên FAILED: nó chưa từng
                    // thu được đồng nào nên không có gì để huỷ.
                    () -> assertEquals(TransactionStatus.FAILED, thatBai.getStatus()));
            verify(escrowService).refundForBooking(booking);
            verify(notificationService).push(eq(khach), anyString(), anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Không có giao dịch SUCCESS nào thì vẫn đánh dấu hoàn, không nổ")
        void khongCoGiaoDichThanhCong() {
            booking.setPaymentStatus(PaymentStatus.PAID);
            when(transactionRepository.findByBookingIdOrderByCreatedAtDesc(bookingId))
                    .thenReturn(List.of());

            service.refundIfPaid(booking);

            assertEquals(PaymentStatus.REFUNDED, booking.getPaymentStatus());
        }
    }
}
