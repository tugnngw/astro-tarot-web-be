package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.astrology.AstrologyContextDTO;
import com.exe.astratarot.domain.dto.astrology.PlanetPositionDTO;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserAstrologicalData;
import com.exe.astratarot.repository.UserAstrologicalDataRepository;
import com.exe.astratarot.service.AstrologyContextService;
import com.exe.astratarot.service.DataEncryptionService;
import com.exe.astratarot.util.ZodiacCalculator;
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
 * Tests for AstrologyContextServiceImpl.
 *
 * Coverage:
 * - Successful context retrieval with decryption
 * - Missing primary profile returns empty
 * - Decryption failure falls back to plain-text
 * - Null userId handling
 * - Zodiac calculation verification
 * - All MVP fields populated correctly
 * - Future fields remain NULL
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AstrologyContextServiceImpl Tests")
class AstrologyContextServiceImplTest {

    @Mock
    private UserAstrologicalDataRepository userAstrologicalDataRepository;

    @Mock
    private DataEncryptionService dataEncryptionService;

    @InjectMocks
    private AstrologyContextServiceImpl astrologyContextService;

    private UUID testUserId;
    private UUID testProfileId;
    private UserAstrologicalData mockEntity;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testProfileId = UUID.randomUUID();

        // CORRECTED: Use User entity builder instead of non-existent userId
        // Set encryptedData and encryptionIv to trigger decryption code path
        byte[] testEncryptedData = new byte[]{1, 2, 3, 4};
        byte[] testEncryptionIv = new byte[]{5, 6, 7, 8, 9, 10, 11, 12};

        mockEntity = UserAstrologicalData.builder()
                .id(testProfileId)
                .user(User.builder().id(testUserId).build())
                .birthDate(LocalDate.of(1990, 3, 21))
                .birthTime(LocalTime.of(14, 30))
                .birthPlace("New York, NY")
                .latitude(new BigDecimal("40.7128"))
                .longitude(new BigDecimal("-74.0060"))
                .timezone("America/New_York")
                .encryptedData(testEncryptedData)
                .encryptionIv(testEncryptionIv)
                .build();
    }

    @Test
    @DisplayName("Should return populated DTO when primary profile exists and decrypts successfully")
    void testGetAstrologyContext_SuccessWithDecryption() {
        // Arrange
        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(testUserId))
                .thenReturn(Optional.of(mockEntity));

        // Mock decrypted sensitive data
        ObjectNode sensitiveData = new ObjectMapper().createObjectNode();
        sensitiveData.put("birthTime", "14:30:00");
        sensitiveData.put("birthPlace", "New York, NY");
        sensitiveData.put("latitude", "40.7128");
        sensitiveData.put("longitude", "-74.0060");
        JsonNode jsonNode = sensitiveData;

        when(dataEncryptionService.decryptJson(any()))
                .thenReturn(jsonNode);

        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(testUserId);

        // Assert
        assertTrue(result.isPresent(), "Should return populated DTO");
        AstrologyContextDTO dto = result.get();

        // Birth data
        assertEquals(LocalDate.of(1990, 3, 21), dto.getBirthDate());
        assertEquals(LocalTime.of(14, 30), dto.getBirthTime());
        assertEquals("New York, NY", dto.getBirthPlace());
        assertEquals(new BigDecimal("40.7128"), dto.getLatitude());
        assertEquals(new BigDecimal("-74.0060"), dto.getLongitude());
        assertEquals("America/New_York", dto.getTimezone());

        // Deterministic zodiac metadata (MVP)
        assertEquals("Aries", dto.getSunSign());
        assertEquals("Fire", dto.getElement());
        assertEquals("Cardinal", dto.getModality());

        // Future fields (NULL in MVP)
        assertNull(dto.getMoonSign());
        assertNull(dto.getRisingSign());
        assertNull(dto.getNatalPlanetPositions());
        assertNull(dto.getNatalAspects());
        assertNull(dto.getTransitPlanetPositions());
        assertNull(dto.getTransitAspects());
        assertNull(dto.getCurrentTransit());
        assertNull(dto.getAscendantDegree());
        assertNull(dto.getMidheavenDegree());
    }

    @Test
    @DisplayName("Should return empty when no primary profile exists")
    void testGetAstrologyContext_NoPrimaryProfile() {
        // Arrange
        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(testUserId))
                .thenReturn(Optional.empty());

        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(testUserId);

        // Assert
        assertFalse(result.isPresent(), "Should return empty when no primary profile");
        verify(userAstrologicalDataRepository, times(1)).findByUserIdAndPrimaryTrue(testUserId);
        verify(dataEncryptionService, never()).decryptJson(any());
    }

    @Test
    @DisplayName("Should fall back to plain-text when decryption fails")
    void testGetAstrologyContext_DecryptionFallback() {
        // Arrange
        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(testUserId))
                .thenReturn(Optional.of(mockEntity));

        // Mock decryption failure
        when(dataEncryptionService.decryptJson(any()))
                .thenThrow(new DataEncryptionService.DecryptionException("Decryption failed", null));

        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(testUserId);

        // Assert
        assertTrue(result.isPresent(), "Should return DTO even when decryption fails (plain-text fallback)");
        AstrologyContextDTO dto = result.get();

        // Should use plain-text columns
        assertEquals(LocalDate.of(1990, 3, 21), dto.getBirthDate());
        assertEquals(LocalTime.of(14, 30), dto.getBirthTime());
        assertEquals("New York, NY", dto.getBirthPlace());
        assertEquals(new BigDecimal("40.7128"), dto.getLatitude());
        assertEquals(new BigDecimal("-74.0060"), dto.getLongitude());
        assertEquals("America/New_York", dto.getTimezone());

        // Zodiac should still calculate correctly
        assertEquals("Aries", dto.getSunSign());
        assertEquals("Fire", dto.getElement());
        assertEquals("Cardinal", dto.getModality());
    }


@Test
    @DisplayName("Should handle null userId gracefully")
    void testGetAstrologyContext_NullUserId() {
        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(null);

        // Assert
        assertFalse(result.isPresent(), "Should return empty for null userId");
        verify(userAstrologicalDataRepository, never()).findByUserIdAndPrimaryTrue(any());
        verify(dataEncryptionService, never()).decryptJson(any());
    }

    @Test
    @DisplayName("Should log warning and return empty on unexpected exception")
    void testGetAstrologyContext_UnexpectedException() {
        // Arrange
        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(testUserId))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        Optional<AstrologyContextDTO> result = astrologyContextService.getAstrologyContext(testUserId);

        // Assert
        assertFalse(result.isPresent(), "Should return empty on unexpected exception");
        verify(userAstrologicalDataRepository, times(1)).findByUserIdAndPrimaryTrue(testUserId);
        verify(dataEncryptionService, never()).decryptJson(any());
    }
}
