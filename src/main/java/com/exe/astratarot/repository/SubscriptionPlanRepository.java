package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    List<SubscriptionPlan> findByIsActiveTrue();

    /** Số gói đang bày bán — cho màn Tổng quan của quản trị. */
    long countByIsActiveTrue();

    Optional<SubscriptionPlan> findByPlanTypeAndIsActiveTrue(SubscriptionPlan.PlanType planType);

    List<SubscriptionPlan> findByPlanType(SubscriptionPlan.PlanType planType);
}