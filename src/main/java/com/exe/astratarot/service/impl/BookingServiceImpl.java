package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.booking.BookingResponse;
import com.exe.astratarot.domain.dto.booking.CreateBookingRequest;
import com.exe.astratarot.domain.dto.booking.SlotResponse;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.ReaderAvailability;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.ReaderAvailabilityRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.ReaderUnavailableDateRepository;
import com.exe.astratarot.repository.ReviewRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.BookingService;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.NotificationTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final ReaderAvailabilityRepository availabilityRepository;
    private final ReaderUnavailableDateRepository unavailableDateRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.exe.astratarot.service.EscrowService escrowService;
    private final com.exe.astratarot.service.PaymentService paymentService;

    /**
     * Lịch rảnh của Reader khai báo bằng giờ địa phương (LocalTime + thứ trong
     * tuần), còn booking lưu bằng Instant. Phải có một múi giờ để quy đổi, và
     * nó phải cố định: lấy múi giờ của máy chủ thì cùng một khung giờ sẽ nhảy
     * chỗ khi triển khai lên máy khác.
     */
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Đúng ba mốc Reader khai giá. Không nhận số phút tuỳ ý. */
    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(15, 30, 60);

    /** Bước nhảy khi sinh khung giờ gợi ý. */
    private static final int SLOT_STEP_MINUTES = 15;

    // =========================================================
    // Khung giờ trống
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    /**
     * Dò từng ngày cho tới khi thấy ngày còn khung trống.
     *
     * Mỗi ngày là vài truy vấn nhỏ, và thực tế hầu như dừng ngay ở ngày đầu
     * hoặc ngày thứ hai. Chặn trên horizonDays để một Reader không khai lịch
     * bao giờ cũng không kéo dài vòng lặp vô ích.
     */
    public java.util.Optional<LocalDate> nextAvailableDate(
            UUID readerProfileId, LocalDate from, int durationMinutes, int horizonDays) {
        requireAllowedDuration(durationMinutes);
        for (int i = 0; i < horizonDays; i++) {
            LocalDate day = from.plusDays(i);
            if (!availableSlots(readerProfileId, day, durationMinutes).isEmpty()) {
                return java.util.Optional.of(day);
            }
        }
        return java.util.Optional.empty();
    }

    public List<SlotResponse> availableSlots(UUID readerProfileId, LocalDate date, int durationMinutes) {
        requireAllowedDuration(durationMinutes);
        ReaderProfile reader = findReader(readerProfileId);
        long price = priceFor(reader, durationMinutes);

        // Ngày Reader báo bận thì không sinh khung nào, kể cả khi lịch tuần có.
        if (unavailableDateRepository.existsByReaderIdAndUnavailableDate(readerProfileId, date)) {
            return List.of();
        }

        // ReaderAvailability dùng 0 = Chủ nhật (quy ước của JS/Postgres), còn
        // java.time.DayOfWeek đánh 7 cho Chủ nhật — nên phải lấy phần dư.
        short dayOfWeek = (short) (date.getDayOfWeek().getValue() % 7);

        List<ReaderAvailability> windows = availabilityRepository
                .findByReaderIdAndDayOfWeekAndActiveTrue(readerProfileId, dayOfWeek);
        if (windows.isEmpty()) {
            return List.of();
        }

        Instant dayStart = date.atStartOfDay(ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<Booking> taken = bookingRepository.findOverlapping(readerProfileId, dayStart, dayEnd);

        Instant now = Instant.now();
        List<SlotResponse> slots = new ArrayList<>();

        for (ReaderAvailability window : windows) {
            LocalTime cursor = window.getStartTime();
            while (!cursor.plusMinutes(durationMinutes).isAfter(window.getEndTime())) {
                Instant start = LocalDateTime.of(date, cursor).atZone(ZONE).toInstant();
                Instant end = start.plus(durationMinutes, ChronoUnit.MINUTES);

                // Khung đã qua thì không gợi ý nữa — hiện ra chỉ để người dùng
                // bấm vào rồi nhận lỗi.
                boolean inFuture = start.isAfter(now);
                boolean free = taken.stream().noneMatch(b -> overlaps(b, start, end));
                if (inFuture && free) {
                    slots.add(SlotResponse.builder().startTime(start).endTime(end).price(price).build());
                }
                cursor = cursor.plusMinutes(SLOT_STEP_MINUTES);
            }
        }
        return slots;
    }

    // =========================================================
    // Tạo
    // =========================================================

    @Override
    @Transactional
    public BookingResponse create(UUID customerId, CreateBookingRequest request) {
        int duration = request.getDurationMinutes();
        requireAllowedDuration(duration);

        User customer = findUser(customerId);
        ReaderProfile reader = findReader(request.getReaderProfileId());

        if (reader.getUser().getId().equals(customerId)) {
            throw new IllegalArgumentException("Bạn không đặt lịch với chính mình được");
        }
        if (reader.getVerifiedAt() == null) {
            throw new IllegalArgumentException("Reader này chưa được duyệt");
        }
        if (!Boolean.TRUE.equals(reader.getAvailable())) {
            throw new IllegalArgumentException("Reader này đang tạm ngưng nhận lịch");
        }

        Instant start = request.getStartTime();
        Instant end = start.plus(duration, ChronoUnit.MINUTES);
        if (!start.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Không đặt được lịch ở thời điểm đã qua");
        }

        LocalDate date = start.atZone(ZONE).toLocalDate();
        if (unavailableDateRepository.existsByReaderIdAndUnavailableDate(reader.getId(), date)) {
            throw new IllegalArgumentException("Reader bận nguyên ngày này");
        }
        if (!fitsWeeklyAvailability(reader.getId(), start, end)) {
            throw new IllegalArgumentException("Khung giờ này nằm ngoài lịch làm việc của Reader");
        }

        // Kiểm tra chồng lấn NGAY TRƯỚC khi ghi. Hai người bấm cùng lúc vẫn có
        // thể lọt qua khe này; hàng rào cuối cùng phải là một ràng buộc ở tầng
        // database — ghi lại ở README để không quên.
        if (!bookingRepository.findOverlapping(reader.getId(), start, end).isEmpty()) {
            throw new IllegalArgumentException("Khung giờ này vừa có người đặt mất rồi");
        }

        Booking booking = bookingRepository.save(Booking.builder()
                .user(customer)
                .readerProfile(reader)
                .startTime(start)
                .endTime(end)
                .totalAmount(priceFor(reader, duration))
                .status(BookingStatus.PENDING)
                .build());

        notificationService.push(reader.getUser(), NotificationTypes.BOOKING_CREATED,
                "Có lịch hẹn mới",
                customer.getFullName() + " vừa đặt một buổi xem " + duration + " phút.",
                Map.of("bookingId", booking.getId().toString()));

        log.info("Booking {} : {} đặt lịch với reader {}", booking.getId(), customerId, reader.getId());
        return toResponse(booking, reviewRepository.existsByBookingId(booking.getId()));
    }

    // =========================================================
    // Đọc
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> listForCustomer(UUID customerId, String status, Pageable pageable) {
        return withReviewFlags(bookingRepository.findForUser(customerId, parseStatus(status), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> listForReader(UUID readerUserId, String status, Pageable pageable) {
        return withReviewFlags(bookingRepository.findForReader(readerUserId, parseStatus(status), pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse get(UUID actorId, UUID bookingId) {
        Booking b = findBooking(bookingId);
        requireParty(b, actorId);
        return toResponse(b, reviewRepository.existsByBookingId(bookingId));
    }

    // =========================================================
    // Đổi trạng thái
    // =========================================================

    @Override
    @Transactional
    public BookingResponse confirm(UUID readerUserId, UUID bookingId) {
        Booking b = findBooking(bookingId);
        requireReader(b, readerUserId);
        requireStatus(b, BookingStatus.PENDING, "Chỉ nhận được lịch đang chờ xác nhận");

        b.setStatus(BookingStatus.CONFIRMED);
        notificationService.push(b.getUser(), NotificationTypes.BOOKING_CONFIRMED,
                "Lịch hẹn đã được nhận",
                b.getReaderProfile().getUser().getFullName() + " đã xác nhận buổi xem của bạn.",
                Map.of("bookingId", b.getId().toString()));
        return toResponse(b, false);
    }

    @Override
    @Transactional
    public BookingResponse complete(UUID readerUserId, UUID bookingId) {
        Booking b = findBooking(bookingId);
        requireReader(b, readerUserId);
        requireStatus(b, BookingStatus.CONFIRMED, "Chỉ hoàn tất được lịch đã xác nhận");

        // Không cho đánh dấu hoàn tất trước giờ hẹn: đó là con đường để nhận
        // tiền cho một buổi xem chưa diễn ra.
        if (Instant.now().isBefore(b.getStartTime())) {
            throw new IllegalArgumentException("Buổi xem chưa tới giờ, chưa hoàn tất được");
        }

        b.setStatus(BookingStatus.COMPLETED);

        // Nhả tiền cho Reader ở đúng thời điểm này, không sớm hơn. Chỉ nhả khi
        // khách đã trả — buổi xem chưa thanh toán thì không có gì trong ký quỹ.
        if (b.getPaymentStatus() == com.exe.astratarot.domain.enums.PaymentStatus.PAID) {
            escrowService.releaseForBooking(b);
        }
        notificationService.push(b.getUser(), NotificationTypes.BOOKING_COMPLETED,
                "Buổi xem đã hoàn tất",
                "Bạn có thể để lại đánh giá cho "
                        + b.getReaderProfile().getUser().getFullName() + ".",
                Map.of("bookingId", b.getId().toString()));
        return toResponse(b, false);
    }

    /**
     * Reader viết (hoặc sửa) ghi chú buổi xem.
     *
     * <p>Chỉ sau giờ hẹn. Ghi chú là bản tường thuật một buổi đã diễn ra; viết
     * trước giờ hẹn thì nó là một lời hứa, không phải bản tường thuật, và
     * chính nó sẽ trở thành bằng chứng sai lệch nếu buổi xem bị huỷ.
     *
     * <p>Cho sửa lại về sau chứ không khoá sau lần ghi đầu: sửa chính tả hay
     * viết thêm một ý vừa nhớ ra là chuyện bình thường của người viết tay.
     */
    @Override
    @Transactional
    public BookingResponse saveReaderNote(UUID readerUserId, UUID bookingId, String note) {
        Booking b = findBooking(bookingId);
        requireReader(b, readerUserId);

        if (b.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Buổi xem đã huỷ, không ghi chú được");
        }
        if (Instant.now().isBefore(b.getStartTime())) {
            throw new IllegalArgumentException("Buổi xem chưa diễn ra, chưa ghi chú được");
        }

        String sach = note == null || note.isBlank() ? null : note.trim();
        b.setReaderNote(sach);
        b.setReaderNoteAt(sach == null ? null : Instant.now());

        // Chỉ báo cho khách khi thật sự có nội dung. Xoá ghi chú mà vẫn gửi
        // thông báo "Reader đã gửi ghi chú" là nói dối họ.
        if (sach != null) {
            notificationService.push(b.getUser(), NotificationTypes.BOOKING_COMPLETED,
                    "Reader đã gửi ghi chú buổi xem",
                    b.getReaderProfile().getUser().getFullName()
                            + " vừa ghi lại nội dung buổi xem của bạn.",
                    Map.of("bookingId", b.getId().toString()));
        }
        return toResponse(b, false);
    }

    @Override
    @Transactional
    public BookingResponse cancel(UUID actorId, UUID bookingId, String reason) {
        Booking b = findBooking(bookingId);
        requireParty(b, actorId);

        if (b.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Buổi xem đã hoàn tất, không huỷ được");
        }
        if (b.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Lịch hẹn này đã huỷ rồi");
        }

        b.setStatus(BookingStatus.CANCELLED);

        // Đã trả tiền thì gỡ khỏi ký quỹ và đánh dấu chờ hoàn. Không làm gì nếu
        // chưa trả.
        paymentService.refundIfPaid(b);

        b.setCancelReason(reason == null || reason.isBlank() ? null : reason.trim());

        // Báo cho BÊN KIA, không phải cho người vừa bấm huỷ.
        boolean cancelledByCustomer = b.getUser().getId().equals(actorId);
        User recipient = cancelledByCustomer ? b.getReaderProfile().getUser() : b.getUser();
        String who = cancelledByCustomer
                ? b.getUser().getFullName()
                : b.getReaderProfile().getUser().getFullName();
        notificationService.push(recipient, NotificationTypes.BOOKING_CANCELLED,
                "Lịch hẹn đã bị huỷ",
                who + " đã huỷ buổi xem"
                        + (b.getCancelReason() == null ? "." : ": " + b.getCancelReason()),
                Map.of("bookingId", b.getId().toString()));

        return toResponse(b, false);
    }

    // =========================================================
    // Luật
    // =========================================================

    private static void requireAllowedDuration(int minutes) {
        if (!ALLOWED_DURATIONS.contains(minutes)) {
            throw new IllegalArgumentException("Thời lượng chỉ nhận 15, 30 hoặc 60 phút");
        }
    }

    private static long priceFor(ReaderProfile reader, int minutes) {
        Long price = switch (minutes) {
            case 15 -> reader.getPricePer15m();
            case 30 -> reader.getPricePer30m();
            default -> reader.getPricePer60m();
        };
        if (price == null || price <= 0) {
            throw new IllegalArgumentException("Reader chưa đặt giá cho mốc " + minutes + " phút");
        }
        return price;
    }

    private boolean fitsWeeklyAvailability(UUID readerProfileId, Instant start, Instant end) {
        LocalDateTime localStart = LocalDateTime.ofInstant(start, ZONE);
        LocalDateTime localEnd = LocalDateTime.ofInstant(end, ZONE);

        // Buổi xem vắt qua nửa đêm thì không có khung nào chứa nổi, và cũng
        // không ai đặt lịch kiểu đó.
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            return false;
        }
        short dayOfWeek = (short) (localStart.getDayOfWeek().getValue() % 7);
        return availabilityRepository
                .findByReaderIdAndDayOfWeekAndActiveTrue(readerProfileId, dayOfWeek)
                .stream()
                .anyMatch(w -> !localStart.toLocalTime().isBefore(w.getStartTime())
                        && !localEnd.toLocalTime().isAfter(w.getEndTime()));
    }

    private static boolean overlaps(Booking b, Instant start, Instant end) {
        return b.getStartTime().isBefore(end) && b.getEndTime().isAfter(start);
    }

    private static void requireParty(Booking b, UUID actorId) {
        boolean isCustomer = b.getUser().getId().equals(actorId);
        boolean isReader = b.getReaderProfile().getUser().getId().equals(actorId);
        if (!isCustomer && !isReader) {
            throw new AccessDeniedException("Lịch hẹn này không liên quan tới bạn");
        }
    }

    private static void requireReader(Booking b, UUID readerUserId) {
        if (!b.getReaderProfile().getUser().getId().equals(readerUserId)) {
            throw new AccessDeniedException("Chỉ Reader của buổi xem này mới thao tác được");
        }
    }

    private static void requireStatus(Booking b, BookingStatus expected, String message) {
        if (b.getStatus() != expected) {
            throw new IllegalArgumentException(message);
        }
    }

    private static BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + status);
        }
    }

    // =========================================================
    // Tiện ích
    // =========================================================

    private Page<BookingResponse> withReviewFlags(Page<Booking> page) {
        List<UUID> ids = page.getContent().stream().map(Booking::getId).toList();
        Set<UUID> reviewed = ids.isEmpty() ? Set.of() : reviewRepository.findReviewedBookingIds(ids);
        return page.map(b -> toResponse(b, reviewed.contains(b.getId())));
    }

    private ReaderProfile findReader(UUID readerProfileId) {
        return readerProfileRepository.findById(readerProfileId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy Reader"));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

    private Booking findBooking(UUID id) {
        return bookingRepository.findByIdWithParties(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn"));
    }

    private static BookingResponse toResponse(Booking b, boolean reviewed) {
        User reader = b.getReaderProfile().getUser();
        User customer = b.getUser();
        return BookingResponse.builder()
                .id(b.getId())
                .readerProfileId(b.getReaderProfile().getId())
                .readerUserId(reader.getId())
                .readerName(reader.getFullName())
                .readerAvatar(reader.getAvatar())
                .customerId(customer.getId())
                .customerName(customer.getFullName())
                .customerAvatar(customer.getAvatar())
                .startTime(b.getStartTime())
                .endTime(b.getEndTime())
                .durationMinutes((int) Duration.between(b.getStartTime(), b.getEndTime()).toMinutes())
                .totalAmount(b.getTotalAmount())
                .status(b.getStatus().name())
                .paymentStatus(b.getPaymentStatus().name())
                .cancelReason(b.getCancelReason())
                .reviewed(reviewed)
                .readerNote(b.getReaderNote())
                .readerNoteAt(b.getReaderNoteAt())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
