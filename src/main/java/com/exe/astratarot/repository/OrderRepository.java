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

    long countByUserId(UUID userId);

    /** Tổng tiền đã chi, bỏ đơn đã huỷ. COALESCE để user chưa mua gì trả 0 chứ không null. */
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.user.id = :userId AND o.status <> com.exe.astratarot.domain.enums.OrderStatus.CANCELLED
            """)
    long sumTotalAmountByUserId(@Param("userId") UUID userId);

}
