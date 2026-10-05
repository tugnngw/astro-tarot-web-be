package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.AiContentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AiContentReportRepository extends JpaRepository<AiContentReport, UUID> {

    long countByTrangThai(String trangThai);

    /** Người này đã báo lượt trải bài này chưa — chặn bấm nhầm hai lần. */
    boolean existsByReporterUserIdAndReadingId(UUID reporterUserId, UUID readingId);
}
