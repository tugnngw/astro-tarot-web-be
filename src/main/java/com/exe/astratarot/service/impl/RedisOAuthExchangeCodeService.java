package com.exe.astratarot.service.impl;

import com.exe.astratarot.exception.InvalidTokenException;
import com.exe.astratarot.service.OAuthExchangeCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RedisOAuthExchangeCodeService implements OAuthExchangeCodeService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BYTES = 32;
    private static final Duration CODE_TTL = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "oauth:exchange:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String createCode(UUID userId) {
        String code = generateCode();
        String key = keyFor(code);
        OAuthExchangeData data = new OAuthExchangeData(userId, Instant.now());

        try {
            Boolean stored = redisTemplate.opsForValue().setIfAbsent(key, objectMapper.writeValueAsString(data), CODE_TTL);
            if (!Boolean.TRUE.equals(stored)) {
                throw new IllegalStateException("Unable to store OAuth exchange code");
            }
            return code;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create OAuth exchange code", ex);
        }
    }

    @Override
    public UUID exchange(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidTokenException("OAuth exchange code is required");
        }

        String value = redisTemplate.opsForValue().getAndDelete(keyFor(code));
        if (value == null) {
            throw new InvalidTokenException("OAuth exchange code is invalid or expired");
        }

        try {
            return objectMapper.readValue(value, OAuthExchangeData.class).userId();
        } catch (Exception ex) {
            throw new InvalidTokenException("OAuth exchange code is invalid");
        }
    }

    private String generateCode() {
        byte[] bytes = new byte[CODE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String keyFor(String code) {
        return KEY_PREFIX + DigestUtils.sha256Hex(code);
    }

    private record OAuthExchangeData(UUID userId, Instant issuedAt) {
    }
}
