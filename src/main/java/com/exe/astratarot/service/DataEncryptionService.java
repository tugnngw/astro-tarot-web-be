package com.exe.astratarot.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Domain-agnostic encryption/decryption service.
 *
 * Reusable for:
 * - Astrology data
 * - Payments
 * - Chat
 * - User PII
 *
 * Uses AES-256-GCM with random IV per record.
 */
public interface DataEncryptionService {

    /**
     * Encrypts a plaintext string using AES-256-GCM.
     *
     * @param plaintext the data to encrypt
     * @return encrypted data wrapper containing ciphertext and IV
     * @throws EncryptionException if encryption fails
     */
    EncryptedDataWrapper encrypt(String plaintext) throws EncryptionException;

    /**
     * Decrypts data using AES-256-GCM.
     *
     * @param wrapper the encrypted data wrapper with ciphertext and IV
     * @return the decrypted plaintext
     * @throws DecryptionException if decryption fails or authentication fails
     */
    String decrypt(EncryptedDataWrapper wrapper) throws DecryptionException;

    /**
     * Encrypts a JSON object using AES-256-GCM.
     *
     * @param jsonNode the JSON object to encrypt
     * @return encrypted data wrapper
     * @throws EncryptionException if encryption fails
     */
    EncryptedDataWrapper encryptJson(JsonNode jsonNode) throws EncryptionException;

    /**
     * Decrypts data and returns as a JSON object.
     *
     * @param wrapper the encrypted data wrapper
     * @return the decrypted JSON object
     * @throws DecryptionException if decryption fails
     */
    JsonNode decryptJson(EncryptedDataWrapper wrapper) throws DecryptionException;

    /**
     * Exception thrown when encryption fails.
     */
    class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when decryption fails.
     */
    class DecryptionException extends RuntimeException {
        public DecryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Wrapper for encrypted data (ciphertext + IV).
     */
    class EncryptedDataWrapper {
        private final byte[] ciphertext;
        private final byte[] iv;

        public EncryptedDataWrapper(byte[] ciphertext, byte[] iv) {
            this.ciphertext = ciphertext;
            this.iv = iv;
        }

        public byte[] getCiphertext() {
            return ciphertext;
        }

        public byte[] getIv() {
            return iv;
        }
    }
}
