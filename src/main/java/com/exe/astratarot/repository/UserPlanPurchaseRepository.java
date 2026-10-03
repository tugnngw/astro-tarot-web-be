package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserPlanPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPlanPurchaseRepository extends JpaRepository<UserPlanPurchase, UUID> {

    List<UserPlanPurchase> findByUserIdAndStatus(UUID userId, UserPlanPurchase.PurchaseStatus status);

    List<UserPlanPurchase> findByUserIdAndIdNotAndStatus(UUID userId, UUID id, UserPlanPurchase.PurchaseStatus status);

    List<UserPlanPurchase> findByUserId(UUID userId);

    Optional<UserPlanPurchase> findTopByUserIdAndStatusOrderByStartAtDesc(UUID userId, UserPlanPurchase.PurchaseStatus status);

    @Query("SELECT up FROM UserPlanPurchase up WHERE up.userId = :userId AND up.status = :status AND up.startAt <= :startAt AND up.endAt >= :endAt")
    List<UserPlanPurchase> findActivePurchasesInPeriod(
            @Param("userId") UUID userId,
            @Param("status") UserPlanPurchase.PurchaseStatus status,
            @Param("startAt") Timestamp startAt,
            @Param("endAt") Timestamp endAt
    );
}
