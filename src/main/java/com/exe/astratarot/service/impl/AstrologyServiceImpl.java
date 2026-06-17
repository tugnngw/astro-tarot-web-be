package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyProfileDTO;
import com.exe.astratarot.domain.dto.astrology.CreateAstrologyProfileRequest;
import com.exe.astratarot.domain.dto.astrology.UpdateAstrologyProfileRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserAstrologicalData;
import com.exe.astratarot.domain.enums.ProfileType;
import com.exe.astratarot.repository.UserAstrologicalDataRepository;
import com.exe.astratarot.service.AstrologyService;
import com.exe.astratarot.service.DataEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing user astrology profiles.
 *
 * Responsibilities:
 * - CRUD operations for user astrological data.
 * - Encryption/Decryption of sensitive birth data using DataEncryptionService.
 * - Access control: Ensures users can only manage their own profiles.
 *
 * Sensitive fields stored in encrypted payload (JSON with versioning):
 * {
 *   "version": 1,
 *   "birthTime": "HH:mm:ss",
 *   "birthPlace": "city, country",
 *   "latitude": 21.0285,
 *   "longitude": 105.8542
 * }
 *
 * Non-sensitive fields stored as plain-text:
 * - birthDate (used for future zodiac calculations)
 * - timezone (used for time conversions)
 * - profileType, title, targetName, isPrimary
 *
 * Dual-write strategy for Milestone 1:
 * Sensitive fields are written to BOTH plain-text columns AND the encrypted payload.
 * This ensures backward compatibility and a safer rollout.
 *
 * Future Scope (Post-Milestone 1):
 * - Remove redundant plain-text columns after stability.
 * - Integrate Swiss Ephemeris for calculations.
 * - Populate AstrologyContextDTO with calculated data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AstrologyServiceImpl implements AstrologyService {

    private final UserAstrologicalDataRepository userAstrologicalDataRepository;
    private final DataEncryptionService dataEncryptionService;
    private final ObjectMapper objectMapper;

    // Constants for encrypted payload structure
    private static final String ENCRYPTED_PAYLOAD_VERSION_KEY = "version";
    private static final int CURRENT_PAYLOAD_VERSION = 1;

    // --- CREATE PROFILE ---

    @Override
    @Transactional
    public AstrologyProfileDTO createProfile(UUID userId, CreateAstrologyProfileRequest request) {
        UserAstrologicalData entity = new UserAstrologicalData();
        entity.setUser(User.builder().id(userId).build()); // Set user via ID

        // Map request data, encrypt sensitive fields, and perform dual-write
        mapRequestToEntity(entity, request);

        // Set non-sensitive fields
        entity.setTitle(request.getTitle());
        entity.setTargetName(request.getTargetName());
        entity.setBirthDate(request.getBirthDate()); // Plain text
        entity.setTimezone(request.getTimezone());
        entity.setProfileType(request.getProfileType());
        entity.setPrimary(request.getIsPrimary() != null ? request.getIsPrimary() : false);

        // Business rule: only one primary profile per user
        // Note: ensureSinglePrimaryProfile is called BEFORE save — the new entity's ID is null
        if (Boolean.TRUE.equals(entity.getPrimary())) {
            ensureSinglePrimaryProfile(userId, null);
        }

        UserAstrologicalData savedEntity = userAstrologicalDataRepository.save(entity);
        log.debug("Created astrology profile with ID: {}", savedEntity.getId());

        return mapToDTO(savedEntity);
    }

    // --- READ PROFILES ---

    @Override
    public List<AstrologyProfileDTO> getProfiles(UUID userId) {
        List<UserAstrologicalData> entities = userAstrologicalDataRepository.findAllByUserId(userId);
        return entities.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<AstrologyProfileDTO> getPrimaryProfile(UUID userId) {
        return userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(userId)
                .map(this::mapToDTO);
    }

    // --- UPDATE PROFILE ---

    @Override
    @Transactional
    public AstrologyProfileDTO updateProfile(UUID userId, UUID profileId, UpdateAstrologyProfileRequest request) {
        UserAstrologicalData entity = userAstrologicalDataRepository.findByUserIdAndId(userId, profileId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for user or ID mismatch."));

        // Update entity with provided values, applying encryption for sensitive data
        mapRequestToEntity(entity, request);

        // Update plain-text fields if provided
        if (request.getTitle() != null) entity.setTitle(request.getTitle());
        if (request.getTargetName() != null) entity.setTargetName(request.getTargetName());
        if (request.getBirthDate() != null) entity.setBirthDate(request.getBirthDate());
        if (request.getTimezone() != null) entity.setTimezone(request.getTimezone());
        if (request.getProfileType() != null) entity.setProfileType(request.getProfileType());
        if (request.getIsPrimary() != null) entity.setPrimary(request.getIsPrimary());

        // Business rule: only one primary profile per user
        // Exclude the current profile from demotion — it should remain primary
        if (Boolean.TRUE.equals(entity.getPrimary())) {
            ensureSinglePrimaryProfile(userId, profileId);
        }

        UserAstrologicalData updatedEntity = userAstrologicalDataRepository.save(entity);
        log.debug("Updated astrology profile with ID: {}", updatedEntity.getId());

        return mapToDTO(updatedEntity);
    }

    // --- DELETE PROFILE ---

    @Override
    @Transactional
    public void deleteProfile(UUID userId, UUID profileId) {
        // Check existence before deletion to provide a specific error message
        userAstrologicalDataRepository.findByUserIdAndId(userId, profileId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found for user or ID mismatch."));

        userAstrologicalDataRepository.deleteByUserIdAndId(userId, profileId);
        log.debug("Deleted astrology profile with ID: {} for user: {}", profileId, userId);
    }

    // --- BUSINESS RULES ---

    /**
     * Ensures only one profile is marked as primary for a user.
     *
     * When a profile is set as primary, all other profiles belonging to
     * the same user are demoted to non-primary.
     * The current profile (by ID) is excluded from demotion.
     * This runs inside the existing @Transactional boundary.
     */
    private void ensureSinglePrimaryProfile(UUID userId, UUID excludeProfileId) {
        List<UserAstrologicalData> allProfiles = userAstrologicalDataRepository.findAllByUserId(userId);
        for (UserAstrologicalData profile : allProfiles) {
            if (Boolean.TRUE.equals(profile.getPrimary())
                    && !profile.getId().equals(excludeProfileId)) {
                profile.setPrimary(false);
                userAstrologicalDataRepository.save(profile);
            }
        }
    }

    // --- MAPPING METHODS ---

    /**
     * Maps CreateAstrologyProfileRequest data to UserAstrologicalData entity.
     * Handles encryption of sensitive fields and dual-write to plain-text columns.
     */
    private void mapRequestToEntity(UserAstrologicalData entity, CreateAstrologyProfileRequest request) {
        encryptAndSetSensitiveData(
            entity,
            request.getBirthTime(),
            request.getBirthPlace(),
            request.getLatitude(),
            request.getLongitude()
        );
    }

    /**
     * Maps UpdateAstrologyProfileRequest data to UserAstrologicalData entity.
     * Handles encryption of sensitive fields if provided in the update request.
     * Performs dual-write to plain-text columns.
     */
    private void mapRequestToEntity(UserAstrologicalData entity, UpdateAstrologyProfileRequest request) {
        // Only encrypt and update if new sensitive data is provided
        if (request.getBirthTime() != null || request.getBirthPlace() != null ||
            request.getLatitude() != null || request.getLongitude() != null) {

            encryptAndSetSensitiveData(
                entity,
                request.getBirthTime(), // Use null if not provided, handle in encryption
                request.getBirthPlace(),
                request.getLatitude(),
                request.getLongitude()
            );
        }
    }

    /**
     * Encrypts sensitive data and sets it on the entity (encrypted_data, encryption_iv).
     * Also performs dual-write to plain-text columns (birthTime, birthPlace, latitude, longitude).
     */
    private void encryptAndSetSensitiveData(UserAstrologicalData entity, LocalTime birthTime, String birthPlace, BigDecimal latitude, BigDecimal longitude) {
        try {
            ObjectNode sensitiveDataNode = objectMapper.createObjectNode();
            sensitiveDataNode.put(ENCRYPTED_PAYLOAD_VERSION_KEY, CURRENT_PAYLOAD_VERSION);

            // Add fields only if they are not null/empty
            if (birthTime != null) {
                sensitiveDataNode.put("birthTime", birthTime.format(DateTimeFormatter.ISO_LOCAL_TIME));
            }
            if (birthPlace != null && !birthPlace.isBlank()) {
                sensitiveDataNode.put("birthPlace", birthPlace);
            }
            if (latitude != null) {
                sensitiveDataNode.put("latitude", latitude);
            }
            if (longitude != null) {
                sensitiveDataNode.put("longitude", longitude);
            }

            // Encrypt the sensitive data JSON
            DataEncryptionService.EncryptedDataWrapper encryptedWrapper = dataEncryptionService.encryptJson(sensitiveDataNode);
            entity.setEncryptedData(encryptedWrapper.getCiphertext());
            entity.setEncryptionIv(encryptedWrapper.getIv());

            // *** Dual-write: Update plain text columns as well ***
            // This is for backward compatibility and safer rollout. Plain text fields
            // are kept in sync with the content that SHOULD be in the encrypted payload.
            entity.setBirthTime(birthTime);
            entity.setBirthPlace(birthPlace);
            entity.setLatitude(latitude);
            entity.setLongitude(longitude);

        } catch (DataEncryptionService.EncryptionException e) {
            log.error("Failed to encrypt sensitive data for profile. User data may be inconsistent.", e);
            throw new RuntimeException("Error encrypting sensitive data", e);
        } catch (Exception e) {
            log.error("Unexpected error processing sensitive data", e);
            throw new RuntimeException("Error processing sensitive data", e);
        }
    }

    /**
     * Maps UserAstrologicalData entity to AstrologyProfileDTO.
     * Decrypts sensitive fields for presentation.
     */
    private AstrologyProfileDTO mapToDTO(UserAstrologicalData entity) {
        AstrologyProfileDTO dto = AstrologyProfileDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .targetName(entity.getTargetName())
                .birthDate(entity.getBirthDate())
                .timezone(entity.getTimezone())
                .profileType(entity.getProfileType())
                .isPrimary(entity.getPrimary())
                .build();

        // Decrypt sensitive data if available
        if (entity.getEncryptedData() != null && entity.getEncryptionIv() != null) {
            try {
                DataEncryptionService.EncryptedDataWrapper wrapper = new DataEncryptionService.EncryptedDataWrapper(
                        entity.getEncryptedData(), entity.getEncryptionIv());

                JsonNode sensitiveData = dataEncryptionService.decryptJson(wrapper);

                // Map decrypted fields to DTO, falling back to plain-text columns if decryption fails or data is missing.
                // Priority: Decrypted > Plain-text fallback

                // Birth Time
                LocalTime decryptedBirthTime = parseLocalTime(sensitiveData.path("birthTime").asText(null));
                dto.setBirthTime(decryptedBirthTime != null ? decryptedBirthTime : entity.getBirthTime());

                // Birth Place
                String decryptedBirthPlace = sensitiveData.path("birthPlace").asText(null);
                dto.setBirthPlace(decryptedBirthPlace != null ? decryptedBirthPlace : entity.getBirthPlace());

                // Latitude
                BigDecimal decryptedLatitude = sensitiveData.path("latitude").decimalValue();
                dto.setLatitude(decryptedLatitude != null ? decryptedLatitude : entity.getLatitude());

                // Longitude
                BigDecimal decryptedLongitude = sensitiveData.path("longitude").decimalValue();
                dto.setLongitude(decryptedLongitude != null ? decryptedLongitude : entity.getLongitude());

            } catch (DataEncryptionService.DecryptionException e) {
                log.error("Failed to decrypt sensitive data for profile ID: {}. Falling back to plain-text columns.", entity.getId(), e);
                // Fallback to plain-text columns if decryption fails
                dto.setBirthTime(entity.getBirthTime());
                dto.setBirthPlace(entity.getBirthPlace());
                dto.setLatitude(entity.getLatitude());
                dto.setLongitude(entity.getLongitude());
            } catch (Exception e) {
                log.error("Unexpected error during DTO mapping for profile ID: {}. Falling back to plain-text columns.", entity.getId(), e);
                // Fallback for any other unexpected mapping errors
                dto.setBirthTime(entity.getBirthTime());
                dto.setBirthPlace(entity.getBirthPlace());
                dto.setLatitude(entity.getLatitude());
                dto.setLongitude(entity.getLongitude());
            }
        } else {
            // If encrypted data is null, use plain-text columns directly
            log.warn("Encrypted data or IV is null for profile ID: {}. Using plain-text columns.", entity.getId());
            dto.setBirthTime(entity.getBirthTime());
            dto.setBirthPlace(entity.getBirthPlace());
            dto.setLatitude(entity.getLatitude());
            dto.setLongitude(entity.getLongitude());
        }

        return dto;
    }

    // --- HELPER METHODS ---

    private LocalTime parseLocalTime(String timeString) {
        if (timeString == null || timeString.isBlank()) {
            return null;
        }
        try {
            // Expecting HH:mm:ss format from ISO_LOCAL_TIME
            return LocalTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse birth time string: {}", timeString, e);
            return null; // Or handle as error, depending on strictness
        }
    }
}
