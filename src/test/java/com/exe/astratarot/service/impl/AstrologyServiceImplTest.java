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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AstrologyServiceImplTest {

    private final ObjectMapper realObjectMapper = new ObjectMapper();

    @Mock
    private UserAstrologicalDataRepository userAstrologicalDataRepository;

    @Mock
    private DataEncryptionService dataEncryptionService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AstrologyServiceImpl astrologyService;

    private UUID userId;
    private User testUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder().id(userId).build();

        lenient().when(objectMapper.createObjectNode()).thenReturn(realObjectMapper.createObjectNode());
        try {
            lenient().when(objectMapper.writeValueAsString(any(JsonNode.class)))
                     .thenReturn("{}");
            ObjectNode expectedNode = realObjectMapper.createObjectNode();
            expectedNode.put("version", 1);
            lenient().when(objectMapper.readTree(anyString())).thenReturn(expectedNode);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testCreateProfile() {
        CreateAstrologyProfileRequest request = CreateAstrologyProfileRequest.builder()
                .title("My Natal Chart")
                .birthDate(LocalDate.of(1990, 6, 15))
                .birthTime(LocalTime.of(14, 30))
                .birthPlace("Hà Nội")
                .latitude(BigDecimal.valueOf(21.0285))
                .longitude(BigDecimal.valueOf(105.8542))
                .timezone("Asia/Ho_Chi_Minh")
                .profileType(ProfileType.SELF)
                .isPrimary(true)
                .build();

        byte[] mockCiphertext = "mockCiphertext".getBytes(StandardCharsets.UTF_8);
        byte[] mockIv = new byte[12];
        new SecureRandom().nextBytes(mockIv);
        DataEncryptionService.EncryptedDataWrapper mockEncryptedWrapper =
            new DataEncryptionService.EncryptedDataWrapper(mockCiphertext, mockIv);

        when(dataEncryptionService.encryptJson(any(JsonNode.class))).thenReturn(mockEncryptedWrapper);

        UserAstrologicalData savedEntity = UserAstrologicalData.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title(request.getTitle())
                .birthDate(request.getBirthDate())
                .birthTime(request.getBirthTime())
                .birthPlace(request.getBirthPlace())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .timezone(request.getTimezone())
                .profileType(request.getProfileType())
                .primary(request.getIsPrimary())
                .encryptedData(mockCiphertext)
                .encryptionIv(mockIv)
                .build();
        when(userAstrologicalDataRepository.save(any(UserAstrologicalData.class))).thenReturn(savedEntity);

        AstrologyProfileDTO resultDTO = astrologyService.createProfile(userId, request);

        assertNotNull(resultDTO);
        assertEquals("My Natal Chart", resultDTO.getTitle());
        assertEquals(LocalDate.of(1990, 6, 15), resultDTO.getBirthDate());
        assertEquals(LocalTime.of(14, 30), resultDTO.getBirthTime());
        assertEquals("Hà Nội", resultDTO.getBirthPlace());
        assertTrue(resultDTO.getIsPrimary());

        verify(dataEncryptionService).encryptJson(any(JsonNode.class));
        verify(userAstrologicalDataRepository).save(any(UserAstrologicalData.class));
    }

    @Test
    void testGetProfiles() throws Exception {
        ObjectNode sensitiveDataNode = realObjectMapper.createObjectNode();
        sensitiveDataNode.put("version", 1);
        sensitiveDataNode.put("birthTime", "14:30:00");
        sensitiveDataNode.put("birthPlace", "Hà Nội");
        sensitiveDataNode.put("latitude", 21.0285);
        sensitiveDataNode.put("longitude", 105.8542);

        byte[] mockCiphertext = "mock".getBytes(StandardCharsets.UTF_8);
        byte[] mockIv = new byte[12];
        new SecureRandom().nextBytes(mockIv);

        when(dataEncryptionService.decryptJson(any())).thenReturn(sensitiveDataNode);

        UserAstrologicalData entity1 = UserAstrologicalData.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Profile 1")
                .birthDate(LocalDate.of(1990, 1, 1))
                .birthTime(LocalTime.of(14, 30))
                .birthPlace("Hà Nội")
                .latitude(BigDecimal.valueOf(21.0285))
                .longitude(BigDecimal.valueOf(105.8542))
                .timezone("UTC")
                .profileType(ProfileType.SELF)
                .primary(true)
                .encryptedData(mockCiphertext)
                .encryptionIv(mockIv)
                .build();

        when(userAstrologicalDataRepository.findAllByUserId(userId)).thenReturn(List.of(entity1));

        List<AstrologyProfileDTO> resultDTOs = astrologyService.getProfiles(userId);

        assertNotNull(resultDTOs);
        assertEquals(1, resultDTOs.size());
        assertEquals("Profile 1", resultDTOs.get(0).getTitle());
        assertEquals(LocalTime.of(14, 30), resultDTOs.get(0).getBirthTime());
        assertEquals("Hà Nội", resultDTOs.get(0).getBirthPlace());
    }

    @Test
    void testGetProfilesEmpty() {
        when(userAstrologicalDataRepository.findAllByUserId(userId)).thenReturn(Collections.emptyList());

        List<AstrologyProfileDTO> resultDTOs = astrologyService.getProfiles(userId);

        assertNotNull(resultDTOs);
        assertTrue(resultDTOs.isEmpty());
    }

    @Test
    void testGetPrimaryProfile() throws Exception {
        ObjectNode sensitiveDataNode = realObjectMapper.createObjectNode();
        sensitiveDataNode.put("version", 1);
        sensitiveDataNode.put("birthTime", "10:00:00");
        sensitiveDataNode.put("birthPlace", "Saigon");

        byte[] mockCiphertext = "cipher".getBytes(StandardCharsets.UTF_8);
        byte[] mockIv = new byte[12];
        new SecureRandom().nextBytes(mockIv);

        when(dataEncryptionService.decryptJson(any())).thenReturn(sensitiveDataNode);

        UserAstrologicalData primaryEntity = UserAstrologicalData.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .title("Primary")
                .birthDate(LocalDate.of(2000, 10, 10))
                .birthTime(LocalTime.of(10, 0))
                .birthPlace("Saigon")
                .profileType(ProfileType.SELF)
                .primary(true)
                .encryptedData(mockCiphertext)
                .encryptionIv(mockIv)
                .build();

        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(userId))
            .thenReturn(Optional.of(primaryEntity));

        Optional<AstrologyProfileDTO> resultDTO = astrologyService.getPrimaryProfile(userId);

        assertTrue(resultDTO.isPresent());
        assertEquals("Primary", resultDTO.get().getTitle());
    }

    @Test
    void testGetPrimaryProfileNotFound() {
        when(userAstrologicalDataRepository.findByUserIdAndPrimaryTrue(userId))
            .thenReturn(Optional.empty());

        Optional<AstrologyProfileDTO> resultDTO = astrologyService.getPrimaryProfile(userId);

        assertFalse(resultDTO.isPresent());
    }

    @Test
    void testUpdateProfile() {
        UUID profileId = UUID.randomUUID();
        UserAstrologicalData existingEntity = UserAstrologicalData.builder()
                .id(profileId).user(testUser).title("Old Title")
                .birthDate(LocalDate.of(1990, 1, 1))
                .birthTime(LocalTime.of(10, 0)).birthPlace("Old Place")
                .latitude(BigDecimal.ONE).longitude(BigDecimal.ONE)
                .profileType(ProfileType.SELF).primary(false)
                .encryptedData(new byte[]{1,2,3}).encryptionIv(new byte[]{4,5,6})
                .build();

        UpdateAstrologyProfileRequest updateRequest = UpdateAstrologyProfileRequest.builder()
                .title("Updated Title").build();

        byte[] updatedCiphertext = "updated".getBytes(StandardCharsets.UTF_8);
        byte[] updatedIv = new byte[12];
        new SecureRandom().nextBytes(updatedIv);
        DataEncryptionService.EncryptedDataWrapper mockEncryptedWrapper =
            new DataEncryptionService.EncryptedDataWrapper(updatedCiphertext, updatedIv);
        lenient().when(dataEncryptionService.encryptJson(any(JsonNode.class))).thenReturn(mockEncryptedWrapper);

        lenient().when(userAstrologicalDataRepository.findByUserIdAndId(userId, profileId))
            .thenReturn(Optional.of(existingEntity));

        UserAstrologicalData updatedEntity = UserAstrologicalData.builder()
                .id(profileId).user(testUser).title("Updated Title")
                .encryptedData(updatedCiphertext).encryptionIv(updatedIv)
                .build();
        when(userAstrologicalDataRepository.save(any(UserAstrologicalData.class))).thenReturn(updatedEntity);

        AstrologyProfileDTO resultDTO = astrologyService.updateProfile(userId, profileId, updateRequest);

        assertNotNull(resultDTO);
        assertEquals("Updated Title", resultDTO.getTitle());
    }

    @Test
    void testCreatePrimaryProfileDemotesOldPrimary() {
        // Existing primary profile
        UUID oldPrimaryId = UUID.randomUUID();
        UserAstrologicalData oldPrimary = UserAstrologicalData.builder()
                .id(oldPrimaryId).user(testUser).title("Old Primary")
                .birthDate(LocalDate.of(1990, 1, 1))
                .birthTime(LocalTime.of(10, 0)).birthPlace("Old Place")
                .latitude(BigDecimal.ONE).longitude(BigDecimal.ONE)
                .profileType(ProfileType.SELF).primary(true)
                .encryptedData(new byte[]{1,2,3}).encryptionIv(new byte[]{4,5,6})
                .build();

        when(userAstrologicalDataRepository.findAllByUserId(userId))
            .thenReturn(List.of(oldPrimary));

        byte[] mockCiphertext = "mock".getBytes(StandardCharsets.UTF_8);
        byte[] mockIv = new byte[12];
        new SecureRandom().nextBytes(mockIv);
        var mockEncrypted = new DataEncryptionService.EncryptedDataWrapper(mockCiphertext, mockIv);
        when(dataEncryptionService.encryptJson(any(JsonNode.class))).thenReturn(mockEncrypted);

        UUID newProfileId = UUID.randomUUID();
        UserAstrologicalData savedNewProfile = UserAstrologicalData.builder()
                .id(newProfileId).user(testUser).title("New Primary")
                .birthDate(LocalDate.of(2000, 1, 1))
                .birthTime(LocalTime.of(14, 0)).birthPlace("New Place")
                .latitude(BigDecimal.TEN).longitude(BigDecimal.TEN)
                .profileType(ProfileType.SELF).primary(true)
                .encryptedData(mockCiphertext).encryptionIv(mockIv)
                .build();
        when(userAstrologicalDataRepository.save(any(UserAstrologicalData.class)))
            .thenReturn(savedNewProfile);

        CreateAstrologyProfileRequest request = CreateAstrologyProfileRequest.builder()
                .title("New Primary")
                .birthDate(LocalDate.of(2000, 1, 1))
                .birthTime(LocalTime.of(14, 0))
                .birthPlace("New Place")
                .latitude(BigDecimal.TEN)
                .longitude(BigDecimal.TEN)
                .profileType(ProfileType.SELF)
                .isPrimary(true)
                .build();

        AstrologyProfileDTO result = astrologyService.createProfile(userId, request);

        assertNotNull(result);
        assertTrue(result.getIsPrimary());

        // Verify old primary was demoted: save was called 2 times (1 demotion + 1 new profile)
        verify(userAstrologicalDataRepository).findAllByUserId(userId);
        verify(userAstrologicalDataRepository, times(2)).save(any(UserAstrologicalData.class));
    }

    @Test
    void testUpdateProfileSetsPrimaryDemotesOthers() {
        UUID profileId = UUID.randomUUID();
        UUID otherProfileId = UUID.randomUUID();
        UserAstrologicalData existingEntity = UserAstrologicalData.builder()
                .id(profileId).user(testUser).title("Target Profile")
                .birthDate(LocalDate.of(1990, 1, 1))
                .birthTime(LocalTime.of(10, 0)).birthPlace("Old Place")
                .latitude(BigDecimal.ONE).longitude(BigDecimal.ONE)
                .profileType(ProfileType.SELF).primary(false)
                .encryptedData(new byte[]{1,2,3}).encryptionIv(new byte[]{4,5,6})
                .build();

        UserAstrologicalData otherPrimary = UserAstrologicalData.builder()
                .id(otherProfileId).user(testUser).title("Other Primary")
                .birthDate(LocalDate.of(1995, 5, 5))
                .birthTime(LocalTime.of(12, 0)).birthPlace("Other Place")
                .latitude(BigDecimal.ONE).longitude(BigDecimal.ONE)
                .profileType(ProfileType.SELF).primary(true)
                .encryptedData(new byte[]{7,8,9}).encryptionIv(new byte[]{10,11,12})
                .build();

        UpdateAstrologyProfileRequest request = UpdateAstrologyProfileRequest.builder()
                .isPrimary(true)
                .build();

        when(userAstrologicalDataRepository.findByUserIdAndId(userId, profileId))
            .thenReturn(Optional.of(existingEntity));
        when(userAstrologicalDataRepository.findAllByUserId(userId))
            .thenReturn(List.of(otherPrimary));
        UserAstrologicalData updatedEntity = UserAstrologicalData.builder()
                .id(profileId).user(testUser).title("Target Profile").primary(true)
                .encryptedData(new byte[]{1,2,3}).encryptionIv(new byte[]{4,5,6})
                .build();
        when(userAstrologicalDataRepository.save(any(UserAstrologicalData.class)))
            .thenReturn(updatedEntity);

        AstrologyProfileDTO result = astrologyService.updateProfile(userId, profileId, request);

        assertNotNull(result);
        assertTrue(result.getIsPrimary());
        verify(userAstrologicalDataRepository).findAllByUserId(userId);
    }

    @Test
    void testDeleteProfile() {
        UUID profileId = UUID.randomUUID();
        UserAstrologicalData entityToDelete = UserAstrologicalData.builder()
                .id(profileId).user(testUser).build();
        when(userAstrologicalDataRepository.findByUserIdAndId(userId, profileId))
            .thenReturn(Optional.of(entityToDelete));

        astrologyService.deleteProfile(userId, profileId);
        verify(userAstrologicalDataRepository).deleteByUserIdAndId(userId, profileId);
    }

    @Test
    void testDeleteNonExistentProfile() {
        UUID nonExistentId = UUID.randomUUID();
        when(userAstrologicalDataRepository.findByUserIdAndId(userId, nonExistentId))
            .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
            () -> astrologyService.deleteProfile(userId, nonExistentId));
        verify(userAstrologicalDataRepository, never())
            .deleteByUserIdAndId(any(UUID.class), any(UUID.class));
    }
}
