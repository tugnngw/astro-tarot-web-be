package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    @Query("""
            SELECT ci FROM CartItem ci
            JOIN FETCH ci.product p
            LEFT JOIN FETCH p.category
            WHERE ci.user.id = :userId
            ORDER BY ci.createdAt ASC
            """)
    List<CartItem> findAllByUserId(@Param("userId") UUID userId);

    Optional<CartItem> findByUserIdAndProductId(UUID userId, UUID productId);

    Optional<CartItem> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
