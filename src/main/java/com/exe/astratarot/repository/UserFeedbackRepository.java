package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, UUID> {

    long countByUserId(UUID userId);

    Optional<UserFeedback> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT AVG(f.rating) FROM UserFeedback f WHERE f.rating IS NOT NULL")
    Double getAverageRating();

    @Query("SELECT AVG(f.nps) FROM UserFeedback f")
    Double getAverageNps();

    @Query("SELECT f.rating, COUNT(f) FROM UserFeedback f WHERE f.rating IS NOT NULL GROUP BY f.rating")
    List<Object[]> countByRatingGroup();

    @Query("SELECT f.source, COUNT(f) FROM UserFeedback f GROUP BY f.source")
    List<Object[]> countBySourceGroup();

    @Query("SELECT f FROM UserFeedback f LEFT JOIN f.user u WHERE " +
            "(:rating IS NULL OR f.rating = :rating) AND " +
            "(:source IS NULL OR f.source = :source) AND " +
            "(:keyword IS NULL OR LOWER(f.comment) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "(u IS NOT NULL AND (" +
            "LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')))))")
    Page<UserFeedback> findAdminFeedback(
            @Param("rating") Integer rating,
            @Param("source") String source,
            @Param("keyword") String keyword,
            Pageable pageable);
}
