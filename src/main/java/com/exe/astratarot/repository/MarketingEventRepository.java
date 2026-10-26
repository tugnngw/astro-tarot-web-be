package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.MarketingEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MarketingEventRepository extends JpaRepository<MarketingEvent, UUID> {

    @Query("""
            SELECT e.eventName, COUNT(e) FROM MarketingEvent e
            WHERE e.createdAt >= :since
            GROUP BY e.eventName
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> countByEventSince(@Param("since") Instant since);

    /**
     * Khách đến từ đâu, rồi LÀM GÌ.
     *
     * <p>Trả về từng bộ ba {@code (utm_source, eventName, số lượt)}. Đây là
     * thứ bảng đếm theo tên sự kiện ở trên không trả lời được: nó cho biết có
     * 40 lượt bấm "đặt lịch", nhưng không cho biết 40 lượt ấy đến từ TikTok
     * hay từ người tự gõ địa chỉ.
     *
     * <p>Nguồn rỗng gộp thành một nhóm riêng ở tầng dịch vụ, không gộp ở đây,
     * để chỗ gọi tự quyết định gọi nó là gì.
     */
    @Query("""
            SELECT e.utmSource, e.eventName, COUNT(e)
            FROM MarketingEvent e
            WHERE e.createdAt >= :since
            GROUP BY e.utmSource, e.eventName
            ORDER BY COUNT(e) DESC
            """)
    List<Object[]> countBySourceAndEventSince(@Param("since") Instant since);

    /**
     * Số NGƯỜI theo từng nguồn, không phải số lượt.
     *
     * <p>Một người vào từ Facebook rồi bấm bảy nút vẫn là một người. Thiếu
     * con số này thì bảng trên dễ đọc thành "Facebook mang về 40 khách" trong
     * khi sự thật có thể là 3 khách bấm nhiều.
     *
     * <p>Chỉ đếm được người ĐÃ ĐĂNG NHẬP — khách vãng lai không có user_id.
     */
    @Query("""
            SELECT e.utmSource, COUNT(DISTINCT e.user.id)
            FROM MarketingEvent e
            WHERE e.createdAt >= :since AND e.user IS NOT NULL
            GROUP BY e.utmSource
            """)
    List<Object[]> countUsersBySourceSince(@Param("since") Instant since);

    /**
     * Mã của những khách gần đây nhất, mới nhất trước.
     *
     * <p>Lấy mã trước rồi mới lấy hành động của đúng những mã ấy. Kéo thẳng
     * toàn bộ sự kiện rồi gộp ở Java thì mỗi lần mở màn quản trị là tải cả
     * bảng về — hôm nay vài trăm dòng thì không sao, nhưng nó chỉ lớn lên.
     */
    @Query("SELECT e.visitorId FROM MarketingEvent e "
            + "WHERE e.visitorId IS NOT NULL AND e.createdAt >= :since "
            + "GROUP BY e.visitorId ORDER BY MAX(e.createdAt) DESC")
    List<String> maKhachGanDay(@Param("since") Instant since, Pageable pageable);

    /**
     * Toàn bộ hành động của một nhóm khách, theo đúng thứ tự xảy ra.
     *
     * <p>{@code LEFT JOIN} người dùng: phần lớn hành trình là của khách chưa
     * đăng nhập, dùng {@code JOIN} thường thì họ biến mất khỏi kết quả — mà
     * họ mới chính là nhóm cần nhìn.
     */
    @Query("SELECT e.visitorId, e.eventName, e.path, e.createdAt, "
            + "e.utmSource, e.utmMedium, e.utmCampaign, u.fullName, e.metadata "
            + "FROM MarketingEvent e LEFT JOIN e.user u "
            + "WHERE e.visitorId IN :maKhach "
            + "ORDER BY e.visitorId, e.createdAt")
    List<Object[]> hanhDongCuaKhach(@Param("maKhach") List<String> maKhach);
}
