package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.PlanChangeAuditLog;
import com.exe.astratarot.domain.enums.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PlanChangeAuditLogRepository extends JpaRepository<PlanChangeAuditLog, UUID> {
    List<PlanChangeAuditLog> findByTargetTypeAndTargetId(TargetType targetType, UUID targetId);
    List<PlanChangeAuditLog> findByChangedBy(UUID changedBy);
}
