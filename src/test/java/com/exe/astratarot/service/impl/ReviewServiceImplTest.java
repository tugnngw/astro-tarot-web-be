package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.review.CreateReviewRequest;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.Review;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReviewRepository;
import com.exe.astratarot.service.NotificationService;
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
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đánh giá Reader. Lớp này trước đây phủ 2,5%.
 *
 * <p>Điểm số là thứ khách dựa vào để chọn Reader và trả tiền, nên ba ràng buộc
 * dưới đây là thứ duy nhất giữ cho nó có nghĩa:
 *
 * <ol>
 *   <li><b>Khoá theo buổi xem.</b> Một buổi một đánh giá. Bỏ ràng buộc này là
 *       một người chấm được mười lần.
 *   <li><b>Chỉ chính khách của buổi xem đó.</b> Không thì ai cũng chấm điểm cho
 *       Reader mình chưa từng gặp.
 *   <li><b>Chỉ sau khi buổi xem hoàn tất.</b> Chấm điểm cho việc chưa xảy ra là
 *       chấm điểm cho một lời hứa.
 * </ol>
 *
 * <p>Và một điểm nữa: điểm trung bình phải tính lại từ toàn bộ bảng, không cộng
 * dồn. Cộng dồn nhanh hơn nhưng sai vĩnh viễn khi một đánh giá bị gỡ — số đã
 * cộng vào không lấy ra được.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceImplTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;
    @Mock private NotificationService notificationService;

    private ReviewServiceImpl service;

    private User khach;
    private User readerUser;
    private ReaderProfile reader;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new ReviewServiceImpl(reviewRepository, bookingRepository,
                readerProfileRepository, notificationService);

        khach = new User();
        khach.setId(UUID.randomUUID());
        khach.setFullName("Khách");
        khach.setAvatar("https://cdn/khach.jpg");

        readerUser = new User();
        readerUser.setId(UUID.randomUUID());
        readerUser.setFullName("Reader");

        reader = ReaderProfile.builder()
                .id(UUID.randomUUID())
                .user(readerUser)
                .rating(BigDecimal.ZERO)
                .totalReviews(0)
                .build();

        booking = Booking.builder()
                .id(UUID.randomUUID())
                .user(khach)
                .readerProfile(reader)
                .startTime(Instant.now().minus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().minus(2, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES))
                .status(BookingStatus.COMPLETED)
                .build();

        lenient().when(bookingRepository.findByIdWithParties(booking.getId()))
                .thenReturn(Optional.of(booking));
        lenient().when(reviewRepository.existsByBookingId(booking.getId())).thenReturn(false);
        lenient().when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        lenient().when(reviewRepository.averageRating(reader.getId())).thenReturn(0.0);
        lenient().when(reviewRepository.countByReaderProfileId(reader.getId())).thenReturn(0L);
    }

    private CreateReviewRequest danhGia(int sao, String loi) {
        CreateReviewRequest r = new CreateReviewRequest();
        r.setRating(sao);
        r.setComment(loi);
        return r;
    }

    // =====================================================================

    @Test
    @DisplayName("Đánh giá thành công: lưu, tính lại điểm Reader, báo Reader")
    void danhGiaThanhCong() {
        when(reviewRepository.averageRating(reader.getId())).thenReturn(4.5);
        when(reviewRepository.countByReaderProfileId(reader.getId())).thenReturn(2L);

        var kq = service.create(khach.getId(), booking.getId(), danhGia(5, "  Rất hài lòng  "));

        assertAll(
                () -> assertEquals(5, kq.getRating()),
                () -> assertEquals("Rất hài lòng", kq.getComment()),
                () -> assertEquals("Khách", kq.getAuthorName()),
                () -> assertEquals("https://cdn/khach.jpg", kq.getAuthorAvatar()),
                () -> assertEquals(new BigDecimal("4.50"), reader.getRating()),
                () -> assertEquals(2, reader.getTotalReviews()));
        verify(notificationService).push(eq(readerUser), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Điểm trung bình làm tròn 2 chữ số, làm tròn NỬA LÊN")
    void lamTronDiem() {
        when(reviewRepository.averageRating(reader.getId())).thenReturn(4.666666);
        when(reviewRepository.countByReaderProfileId(reader.getId())).thenReturn(3L);

        service.create(khach.getId(), booking.getId(), danhGia(5, null));

        // Hiển thị "4.67" chứ không phải "4.666666" ở thẻ Reader.
        assertEquals(new BigDecimal("4.67"), reader.getRating());
    }

    @Test
    @DisplayName("Nhận xét rỗng thì lưu null, không lưu chuỗi trắng")
    void nhanXetRong() {
        var kq = service.create(khach.getId(), booking.getId(), danhGia(4, "   "));

        // Chuỗi trắng lọt vào thì giao diện hiện một khối nhận xét trống dưới
        // mỗi ngôi sao.
        assertNull(kq.getComment());
    }

    @Test
    @DisplayName("Không có nhận xét cũng đánh giá được")
    void khongCoNhanXet() {
        assertNull(service.create(khach.getId(), booking.getId(), danhGia(3, null)).getComment());
    }

    @Test
    @DisplayName("Người KHÔNG phải khách của buổi xem thì không đánh giá được")
    void khongPhaiKhach() {
        // Bỏ ràng buộc này là ai cũng chấm điểm được cho Reader mình chưa từng
        // gặp, và điểm số mất hết ý nghĩa.
        assertAll(
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.create(UUID.randomUUID(), booking.getId(), danhGia(1, "dở"))),
                // Kể cả chính Reader cũng không tự chấm cho mình.
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.create(readerUser.getId(), booking.getId(), danhGia(5, "tốt"))));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Buổi xem CHƯA hoàn tất thì chưa đánh giá được")
    void chuaHoanTat() {
        for (BookingStatus tt : List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED,
                BookingStatus.CANCELLED)) {
            booking.setStatus(tt);
            assertThrows(IllegalArgumentException.class,
                    () -> service.create(khach.getId(), booking.getId(), danhGia(5, "hay")),
                    "trạng thái " + tt + " phải bị chặn");
        }
    }

    @Test
    @DisplayName("Đánh giá HAI LẦN một buổi xem thì bị chặn")
    void danhGiaHaiLan() {
        when(reviewRepository.existsByBookingId(booking.getId())).thenReturn(true);

        // Một buổi một đánh giá. Không khoá thì một người chấm được mười lần.
        assertThrows(IllegalArgumentException.class,
                () -> service.create(khach.getId(), booking.getId(), danhGia(5, "lần hai")));
        verify(readerProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lịch hẹn không tồn tại thì báo đúng loại lỗi")
    void lichHenKhongTonTai() {
        UUID la = UUID.randomUUID();
        when(bookingRepository.findByIdWithParties(la)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(khach.getId(), la, danhGia(5, "x")));
    }

    @Test
    @DisplayName("Chưa có đánh giá nào thì điểm về 0, không nổ vì null")
    void chuaCoDanhGiaNao() {
        when(reviewRepository.averageRating(reader.getId())).thenReturn(0.0);
        when(reviewRepository.countByReaderProfileId(reader.getId())).thenReturn(1L);

        service.create(khach.getId(), booking.getId(), danhGia(1, null));

        assertEquals(new BigDecimal("0.00"), reader.getRating());
    }

    @Test
    @DisplayName("Danh sách đánh giá của một Reader")
    void danhSachCuaReader() {
        Review r = Review.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .user(khach)
                .readerProfile(reader)
                .rating(5)
                .comment("Tốt")
                .build();
        when(reviewRepository.findByReaderProfile(eq(reader.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(r)));

        var trang = service.listForReader(reader.getId(), PageRequest.of(0, 10));

        assertAll(
                () -> assertEquals(1, trang.getTotalElements()),
                () -> assertEquals("Khách", trang.getContent().get(0).getAuthorName()),
                () -> assertEquals(booking.getId(), trang.getContent().get(0).getBookingId()));
    }
}
