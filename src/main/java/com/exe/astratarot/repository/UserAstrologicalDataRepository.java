package com.exe.astratarot.repository;

import com.exe.astratarot.domain.entity.UserAstrologicalData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for UserAstrologicalData entity.
 *
 * Handles CRUD operations for user astrology profiles.
 * All queries are scoped to userId for access control.
 */
@Repository
public interface UserAstrologicalDataRepository extends JpaRepository<UserAstrologicalData, UUID> {

    /**
     * Find all astrology profiles for a user.
     *
     * @param userId the user ID
     * @return list of profiles, or empty list if none found
     */
    List<UserAstrologicalData> findAllByUserId(UUID userId);

    /**
     * Find a specific astrology profile by user ID and profile ID.
     *
     * @param userId the user ID
     * @param profileId the profile ID
     * @return the profile if found
     */
    Optional<UserAstrologicalData> findByUserIdAndId(UUID userId, UUID profileId);

    /**
     * Find the primary astrology profile for a user.
     *
     * @param userId the user ID
     * @return the primary profile if found
     */
    Optional<UserAstrologicalData> findByUserIdAndPrimaryTrue(UUID userId);

    /**
     * Delete a specific astrology profile by user ID and profile ID.
     *
     * @param userId the user ID
     * @param profileId the profile ID
     */
    void deleteByUserIdAndId(UUID userId, UUID profileId);
}
