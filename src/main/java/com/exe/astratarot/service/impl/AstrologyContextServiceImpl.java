package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.entity.UserAstrologicalData;
import com.exe.astratarot.repository.UserAstrologicalDataRepository;
import com.exe.astratarot.service.AstrologyContextService;
import com.exe.astratarot.service.DataEncryptionService;
import com.exe.astratarot.util.ZodiacCalculator;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of AstrologyContextService.
 *
 * Fetches the user's primary astrology profile and populates AstrologyContextDTO
 * with birth data and deterministic zodiac metadata (sun sign, element, modality).
 *
 * MVP Scope: Only birth data and deterministic metadata are populated.
 * Future fields (moon sign, rising sign, planetary positions, aspects) are NULL.
 *
 * Caching:
 * - Cache name: astrology-context
 * - Cache key: userId
 * - TTL: 24 hours
 * - Serialization: JSON (no Java native serialization)
 * - Cache miss: DB → build context → cache → return
 * - Cache hit: Redis → return (no DB access)
 *
 * Failure Handling:
 * - No primary profile → returns Optional.empty() (reading continues without context)
 * - Decryption failure → logs warning, returns Optional.empty() (reading continues)
 * - Any unexpected error → logs error, returns Optional.empty() (reading continues)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AstrologyContextServiceImpl implements AstrologyContextService {

    private final UserAstrologicalDataRepository userAstrologicalDataRepository;
    private final DataEncryptionService dataEncryptionService;

    @Override
    @Cacheable(cacheNames = "astrology-context", key = "#userId", unless = "#result == null || (#result.birthDate == null && #result.birthPlace == null)")
    public Optional<AstrologyContextDTO> getAstrologyContext(UUID userId) {
        if (userId == null) {
            log.warn("Cannot fetch astrology context: userId is null");
            return Optional.empty();
        }

        try {
            return userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(userId)
                    .map(this::mapToAstrologyContext);
        } catch (Exception e) {
            log.warn("Failed to fetch astrology context for user: {}", userId, e);
            return Optional.empty();
        }
    }

    /**
     * Maps UserAstrologicalData entity to AstrologyContextDTO.
     * Decrypts sensitive fields using existing DataEncryptionService pattern.
     * Populates deterministic zodiac metadata using ZodiacCalculator.
     */
    private AstrologyContextDTO mapToAstrologyContext(UserAstrologicalData entity) {
        // Decrypt sensitive fields with fallback to plain-text columns
        DecryptedData decrypted = decryptSensitiveFields(entity);

        // Calculate deterministic zodiac metadata from birth date
        String sunSign = ZodiacCalculator.calculateSunSign(entity.getBirthDate());
        String element = ZodiacCalculator.calculateElement(sunSign);
        String modality = ZodiacCalculator.calculateModality(sunSign);

        return AstrologyContextDTO.builder()
                // Birth data (plain-text + decrypted)
                .birthDate(entity.getBirthDate())
                .birthTime(decrypted.birthTime)
                .birthPlace(decrypted.birthPlace)
                .latitude(decrypted.latitude)
                .longitude(decrypted.longitude)
                .timezone(entity.getTimezone())
                // Deterministic zodiac metadata (MVP only)
                .sunSign(sunSign)
                .element(element)
                .modality(modality)
                // Future fields (NULL in MVP, Milestone 2+ with Swiss Ephemeris)
                .moonSign(null)
                .risingSign(null)
                .natalPlanetPositions(null)
                .natalAspects(null)
                .transitPlanetPositions(null)
                .transitAspects(null)
                .currentTransit(null)
                .ascendantDegree(null)
                .midheavenDegree(null)
                .build();
    }

    /**
     * Decrypts sensitive fields from the encrypted payload.
     * Falls back to plain-text columns if decryption fails.
     */
    private DecryptedData decryptSensitiveFields(UserAstrologicalData entity) {
        DecryptedData data = new DecryptedData();

        // Attempt decryption if encrypted data is available
        if (entity.getEncryptedData() != null && entity.getEncryptionIv() != null) {
            try {
                DataEncryptionService.EncryptedDataWrapper wrapper =
                        new DataEncryptionService.EncryptedDataWrapper(
                                entity.getEncryptedData(), entity.getEncryptionIv());

                JsonNode sensitiveData = dataEncryptionService.decryptJson(wrapper);

                data.birthTime = parseLocalTimeSafely(sensitiveData.path("birthTime").asText(null));
                data.birthPlace = sensitiveData.path("birthPlace").asText(null);
                data.latitude = parseBigDecimalSafely(sensitiveData.path("latitude").asText(null));
                data.longitude = parseBigDecimalSafely(sensitiveData.path("longitude").asText(null));

                return data;

            } catch (DataEncryptionService.DecryptionException e) {
                log.warn("Failed to decrypt sensitive data for profile {}, falling back to plain-text",
                        entity.getId(), e);
            }
        }

        // Fallback to plain-text columns
        data.birthTime = entity.getBirthTime();
        data.birthPlace = entity.getBirthPlace();
        data.latitude = entity.getLatitude();
        data.longitude = entity.getLongitude();

        return data;
    }

    /**
     * Parses LocalTime from string safely, returns null on failure.
     */
    private LocalTime parseLocalTimeSafely(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse birth time: {}", value);
            return null;
        }
    }

    /**
     * Parses BigDecimal from string safely, returns null on failure.
     */
    private BigDecimal parseBigDecimalSafely(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse decimal value: {}", value);
            return null;
        }
    }

    /**
     * Inner class to hold decrypted sensitive data.
     */
    private static class DecryptedData {
        LocalTime birthTime;
        String birthPlace;
        BigDecimal latitude;
        BigDecimal longitude;
    }
}
