package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.booking.BookingResponse;
import com.exe.astratarot.domain.dto.booking.CalendarSlotResponse;
import com.exe.astratarot.domain.dto.booking.CreateBookingRequest;
import com.exe.astratarot.domain.dto.booking.DayCalendarResponse;
import com.exe.astratarot.domain.dto.booking.MonthCalendarResponse;
import com.exe.astratarot.domain.dto.booking.SlotResponse;
import com.exe.astratarot.domain.enums.CalendarDayKind;
import com.exe.astratarot.domain.enums.CalendarSlotState;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.ReaderAvailability;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.ReaderUnavailableDate;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentPhase;
import com.exe.astratarot.domain.enums.PaymentStatus;
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
import com.exe.astratarot.service.BookingService.ActorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final com.exe.astratarot.service.BookingChatService bookingChatService;

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

    /** Mốc 12 giờ trước giờ hẹn — dùng chung cho cả hạn nộp tiền và hủy miễn phí. */
    private static final int CANCELLATION_DEADLINE_HOURS = 12;

    // =========================================================
    // Khung giờ trống
    // =========================================================

    @Override
    @Transactional(readOnly = true)
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

    /** Khách đặt xem được tháng này và hai tháng tới. Xa hơn thì lịch Reader còn đổi. */
    private static final int PUBLIC_MONTHS_AHEAD = 2;

    /** Lịch riêng (của khách hoặc của Reader) nhìn lại một năm và tới nửa năm. */
    private static final int PRIVATE_MONTHS_BACK = 12;
    private static final int PRIVATE_MONTHS_AHEAD = 6;

    @Override
    @Transactional(readOnly = true)
    public MonthCalendarResponse monthCalendar(UUID readerProfileId, int year, int month, int durationMinutes) {
        requireAllowedDuration(durationMinutes);
        YearMonth requested = requireMonth(year, month, 0, PUBLIC_MONTHS_AHEAD);

        ReaderProfile reader = findReader(readerProfileId);
        long price = priceFor(reader, durationMinutes);

        LocalDate first = requested.atDay(1);
        LocalDate last = requested.atEndOfMonth();

        Map<Short, List<ReaderAvailability>> byDow = availabilityRepository
                .findByReaderIdAndActiveTrue(readerProfileId)
                .stream()
                .collect(Collectors.groupingBy(ReaderAvailability::getDayOfWeek));

        Set<LocalDate> off = unavailableDateRepository
                .findByReaderIdAndUnavailableDateBetween(readerProfileId, first, last)
                .stream()
                .map(ReaderUnavailableDate::getUnavailableDate)
                .collect(Collectors.toSet());

        Instant rangeStart = first.atStartOfDay(ZONE).toInstant();
        Instant rangeEnd = last.plusDays(1).atStartOfDay(ZONE).toInstant();
        // Một truy vấn cho cả tháng. Khung đã huỷ không nằm trong kết quả,
        // nên huỷ xong thì ô đó mở lại cho người khác ngay lần xem kế tiếp.
        List<Booking> taken = bookingRepository.findOverlapping(readerProfileId, rangeStart, rangeEnd);

        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZONE);
        List<DayCalendarResponse> days = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            days.add(buildDay(date, today, now, durationMinutes, price, byDow, off, taken));
        }
        return MonthCalendarResponse.builder()
                .year(year)
                .month(month)
                .durationMinutes(durationMinutes)
                .days(days)
                .build();
    }

    private DayCalendarResponse buildDay(
            LocalDate date,
            LocalDate today,
            Instant now,
            int durationMinutes,
            long price,
            Map<Short, List<ReaderAvailability>> byDow,
            Set<LocalDate> off,
            List<Booking> taken) {
        if (off.contains(date)) {
            return DayCalendarResponse.builder()
                    .date(date).kind(CalendarDayKind.OFF).slots(List.of()).build();
        }
        short dow = (short) (date.getDayOfWeek().getValue() % 7);
        List<ReaderAvailability> windows = byDow.getOrDefault(dow, List.of());
        if (windows.isEmpty()) {
            return DayCalendarResponse.builder()
                    .date(date).kind(CalendarDayKind.CLOSED).slots(List.of()).build();
        }
        if (date.isBefore(today)) {
            return DayCalendarResponse.builder()
                    .date(date).kind(CalendarDayKind.PAST).slots(List.of()).build();
        }

        List<CalendarSlotResponse> slots = new ArrayList<>();
        boolean anyFree = false;
        boolean anyTakenAhead = false;
        for (ReaderAvailability window : windows) {
            LocalTime cursor = window.getStartTime();
            while (!cursor.plusMinutes(durationMinutes).isAfter(window.getEndTime())) {
                Instant start = LocalDateTime.of(date, cursor).atZone(ZONE).toInstant();
                Instant end = start.plus(durationMinutes, ChronoUnit.MINUTES);
                CalendarSlotState state;
                if (!start.isAfter(now)) {
                    state = CalendarSlotState.PAST;
                } else if (taken.stream().anyMatch(b -> overlaps(b, start, end))) {
                    state = CalendarSlotState.TAKEN;
                    anyTakenAhead = true;
                } else {
                    state = CalendarSlotState.FREE;
                    anyFree = true;
                }
                slots.add(CalendarSlotResponse.builder()
                        .startTime(start).endTime(end).price(price).state(state).build());
                cursor = cursor.plusMinutes(SLOT_STEP_MINUTES);
            }
        }

        CalendarDayKind kind = anyFree
                ? CalendarDayKind.OPEN
                : anyTakenAhead ? CalendarDayKind.FULL : CalendarDayKind.OVER;
        return DayCalendarResponse.builder().date(date).kind(kind).slots(slots).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> listForCustomerInMonth(UUID customerId, int year, int month) {
        YearMonth requested = requireMonth(year, month, PRIVATE_MONTHS_BACK, PRIVATE_MONTHS_AHEAD);
        Instant start = requested.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant end = requested.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        return withReviewFlags(bookingRepository.findForUserBetween(customerId, start, end));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> listForReaderInMonth(UUID readerUserId, int year, int month) {
        YearMonth requested = requireMonth(year, month, PRIVATE_MONTHS_BACK, PRIVATE_MONTHS_AHEAD);
        Instant start = requested.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant end = requested.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        return withReviewFlags(bookingRepository.findForReaderBetween(readerUserId, start, end));
    }

    private static YearMonth requireMonth(int year, int month, int monthsBack, int monthsAhead) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Tháng không hợp lệ");
        }
        YearMonth requested;
        try {
            requested = YearMonth.of(year, month);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Tháng không hợp lệ");
        }
        YearMonth now = YearMonth.now(ZONE);
        if (requested.isBefore(now.minusMonths(monthsBack)) || requested.isAfter(now.plusMonths(monthsAhead))) {
            throw new IllegalArgumentException("Tháng này nằm ngoài khoảng xem được");
        }
        return requested;
    }

    public List<SlotResponse> availableSlots(UUID readerProfileId, LocalDate date, int durationMinutes) {
        requireAllowedDuration(durationMinutes);
        ReaderProfile reader = findReader(readerProfileId);
        long price = priceFor(reader, durationMinutes);

        if (unavailableDateRepository.existsByReaderIdAndUnavailableDate(readerProfileId, date)) {
            return List.of();
        }

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

        if (!bookingRepository.findOverlapping(reader.getId(), start, end).isEmpty()) {
            throw new IllegalArgumentException("Khung giờ này vừa có người đặt mất rồi");
        }

        long totalAmount = priceFor(reader, duration);
        long depositAmount = totalAmount / 2;
        long remainingAmount = totalAmount - depositAmount;

        // Xác định payment_deadline và paymentPhase
        Instant paymentDeadline = null;
        boolean withinDeadline = true;
        Instant twelveHoursBeforeStart = start.minus(CANCELLATION_DEADLINE_HOURS, ChronoUnit.HOURS);
        if (Instant.now().isAfter(twelveHoursBeforeStart)) {
            // Còn < 12h → không có deadline, đặt cọc không áp dụng
            withinDeadline = false;
            paymentDeadline = null;
        } else {
            paymentDeadline = twelveHoursBeforeStart;
        }

        Booking booking = bookingRepository.save(Booking.builder()
                .user(customer)
                .readerProfile(reader)
                .startTime(start)
                .endTime(end)
                .totalAmount(totalAmount)
                .depositAmount(depositAmount)
                .remainingAmount(remainingAmount)
                .paymentDeadline(paymentDeadline)
                .status(BookingStatus.PENDING)
                .paymentStatus(withinDeadline ? PaymentStatus.UNPAID : PaymentStatus.UNPAID)
                // Lưu ý: paymentStatus vẫn là UNPAID; phase được xác định khi tạo intent
                .build());

        notificationService.push(reader.getUser(), NotificationTypes.BOOKING_CREATED,
                "Có lịch hẹn mới",
                customer.getFullName() + " vừa đặt một buổi xem " + duration + " phút.",
                Map.of("bookingId", booking.getId().toString(), NotificationTypes.SIDE, NotificationTypes.SIDE_READER));

        log.info("Booking {} : {} đặt lịch với reader {}, depositAmount={}, remainingAmount={}, withinDeadline={}",
                booking.getId(), customerId, reader.getId(), depositAmount, remainingAmount, withinDeadline);
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
                Map.of("bookingId", b.getId().toString(), NotificationTypes.SIDE, NotificationTypes.SIDE_CUSTOMER));
        return toResponse(b, false);
    }

    @Override
    @Transactional
    public BookingResponse complete(UUID readerUserId, UUID bookingId) {
        Booking b = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi xem"));
        requireReader(b, readerUserId);
        requireStatus(b, BookingStatus.CONFIRMED, "Chỉ hoàn tất được lịch đã xác nhận");

        if (Instant.now().isBefore(b.getStartTime())) {
            throw new IllegalArgumentException("Buổi xem chưa tới giờ, chưa hoàn tất được");
        }

        b.setStatus(BookingStatus.COMPLETED);

        // Chỉ nhả escrow khi PAID (đã trả đủ 100%)
        if (b.getPaymentStatus() == PaymentStatus.PAID) {
            escrowService.releaseForBooking(b);
        } else {
            log.warn("Booking {} ở trạng thái paymentStatus={}, không nhả escrow khi complete",
                    bookingId, b.getPaymentStatus());
        }
        notificationService.push(b.getUser(), NotificationTypes.BOOKING_COMPLETED,
                "Buổi xem đã hoàn tất",
                "Bạn có thể để lại đánh giá cho "
                        + b.getReaderProfile().getUser().getFullName() + ".",
                Map.of("bookingId", b.getId().toString(),
                        NotificationTypes.SIDE, NotificationTypes.SIDE_CUSTOMER));
        return toResponse(b, false);
    }

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

        if (sach != null) {
            notificationService.push(b.getUser(), NotificationTypes.BOOKING_COMPLETED,
                    "Reader đã gửi ghi chú buổi xem",
                    b.getReaderProfile().getUser().getFullName()
                            + " vừa ghi lại nội dung buổi xem của bạn.",
                    Map.of("bookingId", b.getId().toString(), NotificationTypes.SIDE, NotificationTypes.SIDE_CUSTOMER));
        }
        return toResponse(b, false);
    }

    /**
     * Huỷ booking với chính sách mới.
     *
     * <p>Bảng quy tắc:
     * <ul>
     *   <li>Reader/Admin hủy (bất kỳ lúc nào): hoàn 100%</li>
     *   <li>User hủy ≥ 12h trước giờ hẹn: hoàn 100%</li>
     *   <li>User hủy &lt; 12h trước giờ hẹn: mất cọc, hoàn (đã trả - cọc)</li>
     *   <li>SYSTEM (quá hạn paymentDeadline): mất cọc</li>
     * </ul>
     */
    @Override
    @Transactional
    public BookingResponse cancel(UUID actorId, UUID bookingId, String reason, ActorType actorType) {
        Booking b = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy buổi xem"));
        if (actorType != ActorType.SYSTEM) {
            requireParty(b, actorId);
        }

        if (b.getStatus() == BookingStatus.COMPLETED) {
            throw new IllegalArgumentException("Buổi xem đã hoàn tất, không huỷ được");
        }
        if (b.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalArgumentException("Lịch hẹn này đã huỷ rồi");
        }

        b.setStatus(BookingStatus.CANCELLED);

        long hoursUntilStart = Duration.between(Instant.now(), b.getStartTime()).toHours();
        boolean isEarlyCancel = hoursUntilStart >= CANCELLATION_DEADLINE_HOURS;
        // API huỷ luôn gắn ActorType.USER, kể cả khi người bấm là Reader.
        // Nhận Reader theo buổi hẹn, không theo nhãn, kẻo Reader huỷ bị tính
        // là khách huỷ muộn và khách mất cọc.
        boolean isSystem = actorType == ActorType.SYSTEM;
        boolean isReaderOrAdminCancel = !isSystem && (
                actorType == ActorType.READER
                        || actorType == ActorType.ADMIN
                        || b.getReaderProfile().getUser().getId().equals(actorId));
        boolean isUserCancel = !isSystem && !isReaderOrAdminCancel;

        if (isReaderOrAdminCancel) {
            // Reader hoặc admin hủy → hoàn 100% số đã trả
            long amountToRefund = getAmountPaid(b);
            paymentService.refund(b, amountToRefund);
            log.info("Booking {} bị hủy bởi {}. Hoàn 100% ({})", bookingId, actorType, amountToRefund);
        } else if (isUserCancel && isEarlyCancel) {
            // User hủy sớm (≥ 12h) → hoàn 100%
            long amountToRefund = getAmountPaid(b);
            paymentService.refund(b, amountToRefund);
            log.info("Booking {} bị user hủy sớm ({}h). Hoàn 100% ({})", bookingId, hoursUntilStart, amountToRefund);
        } else if (isUserCancel && !isEarlyCancel) {
            // User hủy muộn (< 12h) → mất cọc, hoàn (đã trả - cọc)
            long depositAmount = b.getDepositAmount();
            long paidSoFar = getAmountPaid(b);
            long refundAmount = Math.max(0, paidSoFar - depositAmount);
            paymentService.refund(b, refundAmount);
            paymentService.forfeitDeposit(b);
            log.info("Booking {} bị user hủy muộn ({}h). Mất cọc {}, hoàn {}", bookingId, hoursUntilStart, depositAmount, refundAmount);
        } else {
            // SYSTEM (quá hạn) hoặc trường hợp khác → mất cọc
            paymentService.forfeitDeposit(b);
            log.info("Booking {} bị hủy do quá hạn. Mất cọc {}", bookingId, b.getDepositAmount());
        }

        b.setCancelReason(reason == null || reason.isBlank() ? null : reason.trim());

        if (actorType == ActorType.SYSTEM) {
            notificationService.push(b.getUser(), NotificationTypes.BOOKING_CANCELLED,
                    "Quá hạn trả nốt",
                    "Bạn chưa trả nốt trước buổi xem 12 tiếng nên lịch bị huỷ và khoản đặt cọc không được hoàn.",
                    Map.of("bookingId", b.getId().toString(),
                            NotificationTypes.SIDE, NotificationTypes.SIDE_CUSTOMER));
            notificationService.push(b.getReaderProfile().getUser(), NotificationTypes.BOOKING_CANCELLED,
                    "Khách quá hạn trả nốt",
                    "Lịch hẹn đã tự huỷ vì khách không trả nốt đúng hạn. Tiền đặt cọc được chuyển cho bạn.",
                    Map.of("bookingId", b.getId().toString(),
                            NotificationTypes.SIDE, NotificationTypes.SIDE_READER));
        } else {
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
                    Map.of("bookingId", b.getId().toString(),
                            NotificationTypes.SIDE,
                            cancelledByCustomer
                                    ? NotificationTypes.SIDE_READER
                                    : NotificationTypes.SIDE_CUSTOMER));
        }

        return toResponse(b, false);
    }

    /** Lấy tổng số tiền đã thanh toán của booking. */
    private long getAmountPaid(Booking b) {
        if (b.getPaymentStatus() == PaymentStatus.PAID) {
            return b.getTotalAmount();
        } else if (b.getPaymentStatus() == PaymentStatus.DEPOSIT_PAID) {
            return b.getDepositAmount();
        }
        return 0L;
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

    private List<BookingResponse> withReviewFlags(List<Booking> rows) {
        List<UUID> ids = rows.stream().map(Booking::getId).toList();
        Set<UUID> reviewed = ids.isEmpty() ? Set.of() : reviewRepository.findReviewedBookingIds(ids);
        return rows.stream().map(b -> toResponse(b, reviewed.contains(b.getId()))).toList();
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

    private BookingResponse toResponse(Booking b, boolean reviewed) {
        User reader = b.getReaderProfile().getUser();
        User customer = b.getUser();
        return BookingResponse.builder()
                .id(b.getId())
                .status(b.getStatus().name())
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
                .depositAmount(b.getDepositAmount())
                .remainingAmount(b.getRemainingAmount())
                .paymentDeadline(b.getPaymentDeadline())
                .paymentStatus(b.getPaymentStatus().name())
                .forfeitedAmount(b.getForfeitedAmount())
                .cancelReason(b.getCancelReason())
                .reviewed(reviewed)
                .readerNote(b.getReaderNote())
                .readerNoteAt(b.getReaderNoteAt())
                .chatOpen(bookingChatService.chatOpen(b))
                .createdAt(b.getCreatedAt())
                .build();
    }
}
