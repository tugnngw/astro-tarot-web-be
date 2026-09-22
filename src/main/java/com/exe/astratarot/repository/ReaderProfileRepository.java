package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ReaderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReaderProfileRepository extends JpaRepository<ReaderProfile, UUID> {
    
    Optional<ReaderProfile> findByUserId(UUID userId);
    
    boolean existsByUserId(UUID userId);

    /**
     * Reader hiện trên danh sách công khai.
     *
     * <p>Ba điều kiện, và điều kiện GIÁ là thứ vừa được thêm: đã duyệt, đang
     * mở nhận khách, và có ít nhất một mức giá.
     *
     * <p>Trước đây chỉ lọc theo verifiedAt, nên một Reader chưa đặt giá nào
     * vẫn nằm trong danh sách. Khách bấm vào, chọn khung giờ, rồi mới nhận
     * được câu "Reader chưa đặt giá cho mốc 30 phút". Backend trả lời đúng,
     * nhưng người dùng đã đi hết ba bước mới biết ngõ cụt. Chưa có giá nghĩa
     * là chưa sẵn sàng nhận khách — đừng bày ra.
     *
     * <p>{@code available} cũng từng bị bỏ qua: Reader tự tắt nhận khách mà
     * vẫn hiện, và họ không hiểu vì sao cái công tắc đó chẳng có tác dụng gì.
     */
    @Query("""
            select p from ReaderProfile p
             where p.verifiedAt is not null
               and (p.available is null or p.available = true)
               and (p.pricePer15m is not null
                 or p.pricePer30m is not null
                 or p.pricePer60m is not null)
            """)
    List<ReaderProfile> findBookable();
}