package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.AiUsageDaily;
import com.exe.astratarot.domain.entity.AiUsageDailyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiUsageDailyRepository extends JpaRepository<AiUsageDaily, AiUsageDailyId> {

    Optional<AiUsageDaily> findByUserIdAndUsageDate(UUID userId, LocalDate usageDate);

    List<AiUsageDaily> findByUserId(UUID userId);

    List<AiUsageDaily> findByUsageDate(LocalDate date);
}
