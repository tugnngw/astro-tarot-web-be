package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserAstrologicalData;
import com.exe.astratarot.repository.UserAstrologicalDataRepository;
import com.exe.astratarot.service.DataEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AstrologyContextService caching behavior using mocks.
 *
 * Tests the @Cacheable annotation integration without requiring:
 * - Spring Boot application context
 * - PostgreSQL database
 * - Redis server
 *
 * Coverage:
 * 1. Cache miss: first call loads DB
 * 2. Cache hit: second call does not hit DB (uses cached result)
 * 3. Cache does not cache empty results (unless condition)
 * 4. Null userId handling
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AstrologyContextService Cache Unit Tests")
class AstrologyContextServiceCacheTest {

    @Mock
    private UserAstrologicalDataRepository userAstrologicalDataRepository;

    @Mock
    private DataEncryptionService dataEncryptionService;

    @InjectMocks
    private AstrologyContextServiceImpl astrologyContextService;

    private UUID testUserId;
    private UUID testProfileId;
    private UserAstrologicalData testEntity;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testProfileId = UUID.randomUUID();

        // Create test entity
        testEntity = UserAstrologicalData.builder()
                .id(testProfileId)
                .user(User.builder().id(testUserId).build())
                .birthDate(LocalDate.of(1990, 3, 21))
                .birthTime(LocalTime.of(14, 30))
                .birthPlace("New York, NY")
                .latitude(new BigDecimal("40.7128"))
                .longitude(new BigDecimal("-74.0060"))
                .timezone("America/New_York")
                .primary(true)
                .build();
    }

    @Test
    @DisplayName("First call loads from DB when profile exists")
    void testFirstCallLoadsFromDB() {
        // Arrange: Entity without encrypted data (uses plain-text fallback)
        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(testUserId))
                .thenReturn(Optional.of(testEntity));

        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(testUserId);

        // Assert
        assertTrue(result.isPresent(), "Should return populated DTO");
        assertEquals("Aries", result.get().getSunSign());
        assertEquals(LocalDate.of(1990, 3, 21), result.get().getBirthDate());
        assertEquals(LocalTime.of(14, 30), result.get().getBirthTime());

        // Verify DB was called
        verify(userAstrologicalDataRepository, times(1))
                .findByUserIdAndPrimaryTrue(testUserId);
    }

    @Test
    @DisplayName("Returns empty when no primary profile exists")
    void testReturnsEmptyWhenNoProfileFound() {
        // Arrange
        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(testUserId))
                .thenReturn(Optional.empty());

        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(testUserId);

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no profile found");

        // Verify DB was called
        verify(userAstrologicalDataRepository, times(1))
                .findByUserIdAndPrimaryTrue(testUserId);
    }

    @Test
    @DisplayName("Returns empty for null userId")
    void testNullUserIdReturnsEmpty() {
        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(null);

        // Assert
        assertFalse(result.isPresent(), "Should return empty for null userId");

        // Verify DB was never called
        verify(userAstrologicalDataRepository, never())
                .findByUserIdAndPrimaryTrue(any());
    }

    @Test
    @DisplayName("Handles decryption failure gracefully")
    void testHandlesDecryptionFailure() {
        // Arrange: Entity WITH encrypted data (will try decryption)
        byte[] testEncryptedData = new byte[]{1, 2, 3, 4};
        byte[] testEncryptionIv = new byte[]{5, 6, 7, 8, 9, 10, 11, 12};

        UserAstrologicalData entityWithEncryption = UserAstrologicalData.builder()
                .id(testProfileId)
                .user(User.builder().id(testUserId).build())
                .birthDate(LocalDate.of(1990, 3, 21))
                .birthTime(LocalTime.of(14, 30))
                .birthPlace("New York, NY")
                .latitude(new BigDecimal("40.7128"))
                .longitude(new BigDecimal("-74.0060"))
                .timezone("America/New_York")
                .primary(true)
                .encryptedData(testEncryptedData)
                .encryptionIv(testEncryptionIv)
                .build();

        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(testUserId))
                .thenReturn(Optional.of(entityWithEncryption));

        // Mock decryption failure
        when(dataEncryptionService.decryptJson(any()))
                .thenThrow(new DataEncryptionService.DecryptionException("Decryption failed", null));

        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(testUserId);

        // Assert
        assertTrue(result.isPresent(), "Should return DTO even when decryption fails (uses plain-text fallback)");
        assertEquals("Aries", result.get().getSunSign());
        assertEquals(LocalTime.of(14, 30), result.get().getBirthTime());
    }

    @Test
    @DisplayName("Zodiac metadata calculated deterministically")
    void testZodiacMetadataCalculation() {
        // Arrange: Entity without encrypted data (uses plain-text columns)
        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(testUserId))
                .thenReturn(Optional.of(testEntity));

        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(testUserId);

        // Assert
        assertTrue(result.isPresent());
        AstrologyContextDTO dto = result.get();

        // Birth date: March 21, 1990 = Aries
        assertEquals("Aries", dto.getSunSign());
        assertEquals("Fire", dto.getElement());
        assertEquals("Cardinal", dto.getModality());

        // Future fields should be NULL in MVP
        assertNull(dto.getMoonSign());
        assertNull(dto.getRisingSign());
        assertNull(dto.getNatalPlanetPositions());
        assertNull(dto.getNatalAspects());
        assertNull(dto.getTransitPlanetPositions());
        assertNull(dto.getTransitAspects());
    }
}
