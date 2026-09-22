package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.BookingMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface BookingMessageRepository extends JpaRepository<BookingMessage, UUID> {

    @Query("""
            select m from BookingMessage m
            join fetch m.sender
            where m.booking.id = :bookingId
            order by m.createdAt desc
            """)
    Page<BookingMessage> findForBooking(@Param("bookingId") UUID bookingId, Pageable pageable);

    /**
     * Đánh dấu đã đọc mọi tin của PHÍA BÊN KIA trong một booking.
     *
     * <p>Điều kiện {@code sender.id <> :readerId} là bắt buộc: không có nó thì
     * mở hội thoại sẽ đánh dấu luôn tin của chính mình là "đã đọc", và phía
     * bên kia thấy tin mình gửi bỗng có dấu đã xem dù họ chưa mở.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BookingMessage m
               set m.readAt = :now
             where m.booking.id = :bookingId
               and m.sender.id <> :viewerId
               and m.readAt is null
            """)
    int markRead(@Param("bookingId") UUID bookingId,
                 @Param("viewerId") UUID viewerId,
                 @Param("now") Instant now);

    @Query("""
            select count(m) from BookingMessage m
             where m.booking.id = :bookingId
               and m.sender.id <> :viewerId
               and m.readAt is null
            """)
    long countUnread(@Param("bookingId") UUID bookingId, @Param("viewerId") UUID viewerId);
}
