package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.MarketingEvent;
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
}
