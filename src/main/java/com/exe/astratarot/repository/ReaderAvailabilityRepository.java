package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.ReaderAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReaderAvailabilityRepository extends JpaRepository<ReaderAvailability, UUID> {
    List<ReaderAvailability> findByReaderId(UUID readerId);

    /** Khung giờ đang bật của một ngày trong tuần. 0 = Chủ nhật, theo cột day_of_week. */
    List<ReaderAvailability> findByReaderIdAndDayOfWeekAndActiveTrue(UUID readerId, Short dayOfWeek);
}