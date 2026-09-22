package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.booking.BookingMessageResponse;
import com.exe.astratarot.domain.dto.booking.CallSignalMessage;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.BookingMessage;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.BookingMessageRepository;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Nhắn tin và gọi giữa khách và Reader, gắn với một buổi đã đặt.
 *
 * <h3>Hai câu hỏi mà mọi thứ ở đây xoay quanh</h3>
 *
 * <p><b>Ai được nói với ai?</b> Đúng hai người: chủ booking và tài khoản của
 * Reader nhận booking đó. Kiểm tra ở MỘT chỗ ({@link #participants}) rồi mọi
 * lối vào — REST, STOMP, tín hiệu cuộc gọi — đều đi qua nó. Nếu mỗi lối tự
 * kiểm tra lấy thì sớm muộn có lối quên, và quên ở đây nghĩa là người lạ đọc
 * được hội thoại riêng.
 *
 * <p><b>Khi nào được nói?</b> Chỉ khi đã trả tiền. Đây là quyết định về doanh
 * thu chứ không phải kỹ thuật: sàn sống bằng hoa hồng trên tiền ký quỹ, mà
 * cho nhắn tự do trước khi thanh toán là mở sẵn đường để hai bên trao số
 * điện thoại rồi hẹn riêng bên ngoài. Mở sau khi trả tiền, đóng lại một thời
 * gian sau khi buổi kết thúc.
 *
 * <p>Tín hiệu cuộc gọi đi qua đây nhưng KHÔNG được lưu: nó chỉ có nghĩa trong
 * vài giây bắt tay. Tiếng và hình đi thẳng giữa hai trình duyệt (WebRTC), máy
 * chủ không đụng tới — nên hộp Render 512MB không phải là nút thắt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingChatService {

    private static final String CHAT_QUEUE = "/queue/booking-chat";
    private static final String CALL_QUEUE = "/queue/booking-call";

    private final BookingRepository bookingRepository;
    private final BookingMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Số ngày còn nhắn được sau khi buổi tư vấn kết thúc.
     *
     * <p>Không đóng ngay lúc COMPLETED: khách hay nhắn lại hỏi thêm ngay sau
     * buổi, và Reader còn gửi nốt ghi chú. Đóng phắt là đẩy cả hai sang Zalo,
     * đúng thứ ta đang tránh.
     */
    @Value("${app.booking-chat.grace-days:7}")
    private long graceDays;

    /** Hai người trong một buổi, kèm chính booking đó. */
    public record Participants(Booking booking, UUID viewerId, UUID otherUserId) {}

    /**
     * Xác nhận người này thuộc buổi đó, và trả về phía bên kia là ai.
     *
     * @throws AccessDeniedException nếu không phải khách cũng không phải Reader
     */
    @Transactional(readOnly = true)
    public Participants participants(UUID bookingId, UUID viewerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy buổi tư vấn: " + bookingId));

        UUID customerId = booking.getUser().getId();
        UUID readerUserId = booking.getReaderProfile().getUser().getId();

        if (viewerId.equals(customerId)) {
            return new Participants(booking, viewerId, readerUserId);
        }
        if (viewerId.equals(readerUserId)) {
            return new Participants(booking, viewerId, customerId);
        }
        // Không nói rõ "buổi này có thật nhưng bạn không thuộc về nó" — như vậy
        // là xác nhận id có tồn tại cho người đang dò.
        throw new AccessDeniedException("Bạn không thuộc buổi tư vấn này");
    }

    /**
     * Hội thoại có đang mở không.
     *
     * <p>Tách riêng khỏi {@link #participants} vì ĐỌC LẠI lịch sử thì luôn cho
     * phép, chỉ GỬI mới bị chặn. Đóng hội thoại mà xoá luôn quyền xem những gì
     * đã trao đổi là lấy mất của người dùng thứ họ đã trả tiền để có.
     */
    public boolean chatOpen(Booking booking) {
        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            return false;
        }
        return switch (booking.getStatus()) {
            case CONFIRMED -> true;
            case COMPLETED -> booking.getEndTime() != null
                    && Instant.now().isBefore(
                            booking.getEndTime().plus(Duration.ofDays(graceDays)));
            case PENDING, CANCELLED -> false;
        };
    }

    private void assertChatOpen(Booking booking) {
        if (chatOpen(booking)) {
            return;
        }
        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException(
                    "Cần thanh toán xong mới nhắn tin được với Reader.");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Buổi tư vấn đã huỷ, không nhắn tin được nữa.");
        }
        throw new IllegalStateException(
                "Hội thoại đã đóng sau " + graceDays + " ngày kể từ khi buổi kết thúc.");
    }

    @Transactional(readOnly = true)
    public Page<BookingMessageResponse> history(UUID bookingId, UUID viewerId, Pageable pageable) {
        participants(bookingId, viewerId);
        return messageRepository.findForBooking(bookingId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID bookingId, UUID viewerId) {
        participants(bookingId, viewerId);
        return messageRepository.countUnread(bookingId, viewerId);
    }

    @Transactional
    public int markRead(UUID bookingId, UUID viewerId) {
        participants(bookingId, viewerId);
        return messageRepository.markRead(bookingId, viewerId, Instant.now());
    }

    /**
     * Lưu tin rồi đẩy cho CẢ HAI phía.
     *
     * <p>Người gửi cũng nhận lại tin của chính mình: họ có thể đang mở hội
     * thoại trên điện thoại lẫn máy tính, và chỉ vẽ tin lên màn hình nơi vừa
     * gõ sẽ làm hai thiết bị lệch nhau.
     */
    @Transactional
    public BookingMessageResponse send(UUID bookingId, UUID senderId, String body) {
        Participants p = participants(bookingId, senderId);
        assertChatOpen(p.booking());

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        // saveAndFlush chứ không phải save, và đây không phải thói quen thừa.
        //
        // Id là @GeneratedValue(UUID) nên Hibernate sinh được ngay trong bộ nhớ,
        // không cần hỏi cơ sở dữ liệu — và vì thế nó HOÃN câu INSERT tới lúc
        // flush cuối giao dịch. Mà @CreationTimestamp chỉ được gán đúng lúc
        // INSERT chạy. Không flush thì createdAt của thực thể vừa save vẫn là
        // null, DTO dựng từ nó mang null đi, và gói đẩy realtime tới hai trình
        // duyệt không có thời gian.
        //
        // Đọc lại bằng REST thì lại đúng, vì lúc đó dữ liệu đã nằm trong bảng.
        // Nên lỗi chỉ hiện ở tin nhắn VỪA gửi và tự lành khi tải lại trang —
        // đúng kiểu dễ bị bỏ qua nhất. Bắt được trên production: tin mới hiện
        // "08:00 01-01" (new Date(null) ở múi giờ Asia/Saigon), F5 phát thì
        // thành "13:40 22-09".
        BookingMessage saved = messageRepository.saveAndFlush(BookingMessage.builder()
                .booking(p.booking())
                .sender(sender)
                .body(body.trim())
                .build());

        BookingMessageResponse dto = toResponse(saved);
        push(CHAT_QUEUE, p.otherUserId(), dto);
        push(CHAT_QUEUE, senderId, dto);
        return dto;
    }

    /**
     * Chuyển tiếp một gói tín hiệu WebRTC sang phía bên kia.
     *
     * <p>Không lưu, không đọc nội dung. Việc duy nhất máy chủ làm là xác nhận
     * người gửi thuộc buổi này và GHI ĐÈ danh tính người gửi — nếu tin lời
     * client tự khai {@code fromUserId} thì ai cũng giả danh được người khác
     * để gọi tới nạn nhân.
     */
    @Transactional(readOnly = true)
    public void relaySignal(UUID bookingId, UUID fromUserId, CallSignalMessage signal) {
        Participants p = participants(bookingId, fromUserId);
        assertChatOpen(p.booking());

        User from = userRepository.findById(fromUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        signal.setBookingId(bookingId);
        signal.setFromUserId(fromUserId);
        signal.setFromName(from.getFullName());
        push(CALL_QUEUE, p.otherUserId(), signal);
    }

    private void push(String queue, UUID userId, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), queue, payload);
        } catch (Exception e) {
            // Đẩy realtime hỏng không được làm hỏng việc đã ghi xong vào DB.
            log.warn("Không đẩy {} cho user {}: {}", queue, userId, e.getMessage());
        }
    }

    private BookingMessageResponse toResponse(BookingMessage m) {
        return BookingMessageResponse.builder()
                .id(m.getId())
                .bookingId(m.getBooking().getId())
                .senderId(m.getSender().getId())
                .senderName(m.getSender().getFullName())
                .body(m.getBody())
                .readAt(m.getReadAt())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
