package com.exe.astratarot.service.impl;

import com.exe.astratarot.service.DataEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataEncryptionService Tests")
class DataEncryptionServiceImplTest {

    private DataEncryptionService encryptionService;
    private ObjectMapper objectMapper;

    // 32-byte key valid for AES-256, properly Base64-encoded
    private final String TEST_ENCRYPTION_KEY_BASE64 = Base64.getEncoder().encodeToString(
        "abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8)
    );
    private final String TEST_PLAINTEXT_STRING = "This is a secret message to encrypt.";
    private final LocalTime TEST_BIRTH_TIME = LocalTime.of(14, 30, 0);
    private final String TEST_BIRTH_PLACE = "Hà Nội, Vietnam";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        encryptionService = new DataEncryptionServiceImpl(TEST_ENCRYPTION_KEY_BASE64, objectMapper);
    }

    @Test
    @DisplayName("Should encrypt and decrypt a plaintext string")
    void testEncryptDecryptString() {
        DataEncryptionService.EncryptedDataWrapper encrypted = encryptionService.encrypt(TEST_PLAINTEXT_STRING);
        String decrypted = encryptionService.decrypt(encrypted);

        assertNotNull(encrypted);
        assertNotNull(encrypted.getCiphertext());
        assertNotNull(encrypted.getIv());
        assertEquals(TEST_PLAINTEXT_STRING, decrypted);
    }

    @Test
    @DisplayName("Should generate random IV for each string encryption")
    void testRandomIvGenerationForString() {
        DataEncryptionService.EncryptedDataWrapper e1 = encryptionService.encrypt(TEST_PLAINTEXT_STRING);
        DataEncryptionService.EncryptedDataWrapper e2 = encryptionService.encrypt(TEST_PLAINTEXT_STRING);

        assertNotEquals(
            Base64.getEncoder().encodeToString(e1.getIv()),
            Base64.getEncoder().encodeToString(e2.getIv())
        );
    }

    @Test
    @DisplayName("Should throw DecryptionException on corrupted ciphertext")
    void testDecryptionFailureOnCorruptedData() {
        DataEncryptionService.EncryptedDataWrapper encrypted = encryptionService.encrypt(TEST_PLAINTEXT_STRING);
        byte[] corruptedCiphertext = encrypted.getCiphertext().clone();
        corruptedCiphertext[0] ^= 0xFF;

        DataEncryptionService.EncryptedDataWrapper corruptedData =
            new DataEncryptionService.EncryptedDataWrapper(corruptedCiphertext, encrypted.getIv());

        assertThrows(
            DataEncryptionService.DecryptionException.class,
            () -> encryptionService.decrypt(corruptedData)
        );
    }

    @Test
    @DisplayName("Should encrypt and decrypt JSON payload")
    void testEncryptDecryptJsonWithVersioning() throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("birthTime", TEST_BIRTH_TIME.format(DateTimeFormatter.ISO_LOCAL_TIME));
        node.put("birthPlace", TEST_BIRTH_PLACE);

        DataEncryptionService.EncryptedDataWrapper encrypted = encryptionService.encryptJson(node);
        JsonNode decrypted = encryptionService.decryptJson(encrypted);

        assertNotNull(encrypted.getCiphertext());
        assertNotNull(encrypted.getIv());
        assertEquals(TEST_BIRTH_PLACE, decrypted.path("birthPlace").asText());
    }

    @Test
    @DisplayName("Should handle null plaintext during encryption")
    void testEncryptNullPlaintext() {
        assertNull(encryptionService.encrypt(null));
    }

    @Test
    @DisplayName("Should handle empty string encryption/decryption")
    void testEncryptDecryptEmptyString() {
        DataEncryptionService.EncryptedDataWrapper encrypted = encryptionService.encrypt("");
        String decrypted = encryptionService.decrypt(encrypted);

        assertNotNull(encrypted.getCiphertext());
        assertNotNull(encrypted.getIv());
        assertEquals("", decrypted);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid Base64 key")
    void testInvalidBase64KeyHandling() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DataEncryptionServiceImpl("not-base64!!!", objectMapper)
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for short key")
    void testIncorrectKeyLengthHandling() {
        String shortKey = Base64.getEncoder().encodeToString("short".getBytes());
        assertThrows(
            IllegalArgumentException.class,
            () -> new DataEncryptionServiceImpl(shortKey, objectMapper)
        );
    }

    @Test
    @DisplayName("Should throw DecryptionException for null ciphertext")
    void testDecryptNullCiphertext() {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        var wrapper = new DataEncryptionService.EncryptedDataWrapper(null, iv);

        assertThrows(
            DataEncryptionService.DecryptionException.class,
            () -> encryptionService.decrypt(wrapper)
        );
    }

    @Test
    @DisplayName("Should throw DecryptionException for null IV")
    void testDecryptNullIV() {
        byte[] ct = "dummy".getBytes(StandardCharsets.UTF_8);
        var wrapper = new DataEncryptionService.EncryptedDataWrapper(ct, null);

        assertThrows(
            DataEncryptionService.DecryptionException.class,
            () -> encryptionService.decrypt(wrapper)
        );
    }
}
