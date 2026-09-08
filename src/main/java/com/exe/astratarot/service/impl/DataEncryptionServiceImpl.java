package com.exe.astratarot.service.impl;

import com.exe.astratarot.service.DataEncryptionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption/decryption service implementation.
 *
 * Domain-agnostic and reusable for:
 * - Astrology data
 * - Payments
 * - Chat
 * - User PII
 *
 * Encryption details:
 * - Algorithm: AES/GCM/NoPadding
 * - IV: Random 12 bytes (96 bits) per record
 * - Authentication tag: 128 bits
 * - Key: 256 bits (32 bytes)
 * - Key source: ASTRO_ENCRYPTION_KEY environment variable (Base64-encoded)
 *
 * Payload structure:
 * - JSON format with "version" field for future compatibility.
 * - Sensitive fields serialized into JSON, then encrypted.
 */
@Service
@Slf4j
public class DataEncryptionServiceImpl implements DataEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BITS = 128;
    public static final int IV_LENGTH_BYTES = 12; // 96 bits standard for GCM
    private static final String ENCRYPTION_KEY_PROPERTY = "astro.encryption-key";

    private final byte[] encryptionKey;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public DataEncryptionServiceImpl(
            @Value("${" + ENCRYPTION_KEY_PROPERTY + "}") String encryptionKeyBase64,
            ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;
        this.secureRandom = new SecureRandom();

        try {
            // Decode Base64 encryption key from environment variable
            this.encryptionKey = Base64.getDecoder().decode(encryptionKeyBase64);

            // Validate key length (must be 32 bytes for AES-256)
            if (this.encryptionKey.length != 32) {
                throw new IllegalArgumentException(
                    String.format("Encryption key must be 256 bits (32 bytes), got %d bytes",
                        this.encryptionKey.length)
                );
            }

            log.info("DataEncryptionService initialized with AES-256-GCM");
        } catch (IllegalArgumentException e) {
            log.error("Failed to initialize encryption key: Invalid key format or length. Ensure ASTRO_ENCRYPTION_KEY is set and Base64 encoded.", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error initializing DataEncryptionService", e);
            throw new RuntimeException("Failed to initialize encryption service", e);
        }
    }

    @Override
    public EncryptedDataWrapper encrypt(String plaintext) throws EncryptionException {
        if (plaintext == null) {
            return null; // Or throw an exception, depending on desired behavior for null input
        }
        try {
            // Generate random IV
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            // Encrypt plaintext
            byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plainBytes);

            log.debug("Encrypted {} bytes of data", plainBytes.length);
            return new EncryptedDataWrapper(ciphertext, iv);

        } catch (Exception e) {
            log.error("AES-256-GCM encryption failed", e);
            throw new EncryptionException("AES-256-GCM encryption failed", e);
        }
    }

    @Override
    public String decrypt(EncryptedDataWrapper wrapper) throws DecryptionException {
        if (wrapper == null || wrapper.getCiphertext() == null || wrapper.getIv() == null) {
            throw new DecryptionException("Encrypted data wrapper or its components are null", null);
        }
        try {
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, wrapper.getIv());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // Decrypt ciphertext
            byte[] plainBytes = cipher.doFinal(wrapper.getCiphertext());
            String plaintext = new String(plainBytes, StandardCharsets.UTF_8);

            log.debug("Decrypted {} bytes of data", plainBytes.length);
            return plaintext;

        } catch (Exception e) {
            log.error("AES-256-GCM decryption failed - authentication tag may be invalid or wrong key", e);
            throw new DecryptionException("AES-256-GCM decryption failed", e);
        }
    }

    @Override
    public EncryptedDataWrapper encryptJson(JsonNode jsonNode) throws EncryptionException {
        if (jsonNode == null) {
            return null;
        }
        try {
            // Add version to JSON payload
            ObjectNode nodeWithVersion = objectMapper.createObjectNode();
            nodeWithVersion.set("version", objectMapper.valueToTree(1)); // Payload version 1
            nodeWithVersion.set("payload", jsonNode); // Original JSON as payload

            String jsonString = objectMapper.writeValueAsString(nodeWithVersion);
            return encrypt(jsonString);
        } catch (Exception e) {
            log.error("Failed to serialize JSON or encrypt", e);
            throw new EncryptionException("JSON serialization or encryption failed", e);
        }
    }

    @Override
    public JsonNode decryptJson(EncryptedDataWrapper wrapper) throws DecryptionException {
        if (wrapper == null || wrapper.getCiphertext() == null || wrapper.getIv() == null) {
            throw new DecryptionException("Encrypted data wrapper or its components are null", null);
        }
        try {
            String plaintext = decrypt(wrapper);
            // Deserialize JSON, respecting the version field if necessary
            JsonNode rootNode = objectMapper.readTree(plaintext);

            // TODO: Implement version handling if payload structure changes in future versions
            // For now, assume version 1 and extract the actual payload
            JsonNode payloadNode = rootNode.path("payload");
            if (payloadNode.isMissingNode() || payloadNode.isNull()) {
                 // This could happen if the entire payload was just the simple data without versioning yet
                 // Or if the JSON structure is unexpected. For now, let's treat it as an error.
                log.warn("Decrypted JSON payload is missing or null, or does not contain a 'payload' field. Attempting to parse raw JSON.");
                // Fallback: Try to parse the raw JSON if it's not versioned or structure is unexpected
                try {
                    return objectMapper.readTree(plaintext);
                } catch (Exception parseEx) {
                    log.error("Failed to parse decrypted JSON after fallback", parseEx);
                    throw new DecryptionException("Failed to parse decrypted JSON", parseEx);
                }
            }
            return payloadNode;

        } catch (DecryptionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to deserialize JSON after decryption", e);
            throw new DecryptionException("JSON deserialization failed", e);
        }
    }
}
