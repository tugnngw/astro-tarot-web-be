package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("""
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.items
            WHERE o.id = :id AND o.user.id = :userId
            """)
    Optional<Order> findByIdAndUserIdWithItems(@Param("id") UUID id, @Param("userId") UUID userId);

    boolean existsByOrderCode(String orderCode);
}
