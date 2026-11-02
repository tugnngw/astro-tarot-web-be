package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.astrology.NguoiThuHaiDTO;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for retrieving and populating astrological context for user profiles.
 *
 * MVP Scope: Populates birth data fields and deterministic zodiac metadata
 * (sun sign, element, modality) from birth date only.
 * All calculated fields (moon sign, rising sign, planetary positions, aspects)
 * are NULL in MVP (Milestone 1).
 *
 * Future Enhancement: In Milestone 2+, will be extended with Swiss Ephemeris
 * calculations to populate sign and planet data.
 *
 * Data Flow:
 * UserAstrologicalData (entity with birth data)
 *   ↓ (decrypt sensitive fields)
 * AstrologyContextDTO (DTO with birth data + deterministic zodiac metadata)
 *   ↓ (passed to PromptBuilder)
 * Prompt string with context section
 */
public interface AstrologyContextService {

    /**
     * Retrieves astrological context for a user's primary astrology profile.
     *
     * MVP Behavior:
     * - Fetches the user's primary UserAstrologicalData profile (via findByUserIdAndPrimaryTrue)
     * - Decrypts sensitive fields (birthTime, birthPlace, latitude, longitude)
     * - Populates AstrologyContextDTO with birth data and deterministic zodiac metadata
     * - Returns Optional.empty() if no primary profile found or belongs to different user
     *
     * Future Behavior (Milestone 2+):
     * - Will call Swiss Ephemeris to calculate planetary positions
     * - Will populate sunSign, moonSign, risingSign
     * - Will populate natalPlanetPositions and natalAspects
     * - Will calculate transit data if needed
     *
     * @param userId Authenticated user ID (from JWT)
     * @return Optional<AstrologyContextDTO> with available fields populated,
     *         or Optional.empty() if primary profile not found
     * @throws IllegalArgumentException if userId is null
     */
    Optional<AstrologyContextDTO> getAstrologyContext(UUID userId);

    /**
     * Dựng ngữ cảnh chiêm tinh cho người thứ hai của một lượt xem đôi, từ dữ
     * liệu sinh đi kèm yêu cầu — không đụng tới cơ sở dữ liệu.
     *
     * Người thứ hai không phải người dùng của hệ thống nên không có hồ sơ để
     * tra. Tính cùng một bộ công thức với người thứ nhất, để hai bên của lời
     * giải đứng trên cùng một thước đo.
     *
     * @param nguoi dữ liệu sinh của người thứ hai, có thể null
     * @return Optional.empty() nếu null hoặc chưa có ngày sinh
     */
    Optional<AstrologyContextDTO> nguCanhNguoiThuHai(NguoiThuHaiDTO nguoi);
}
