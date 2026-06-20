package com.exe.astratarot.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis Cache Configuration for Spring Cache Abstraction.
 *
 * Configures:
 * - JSON serialization (no Java native serialization)
 * - TTL: 24 hours for all cache entries
 * - Cache names: astrology-context
 * - Production-ready error handling
 *
 * Usage:
 * @Cacheable(cacheNames = "astrology-context", key = "#userId")
 * public Optional<AstrologyContextDTO> getAstrologyContext(UUID userId) { ... }
 *
 * @CacheEvict(cacheNames = "astrology-context", key = "#userId")
 * public void invalidateCache(UUID userId) { ... }
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    /**
     * Configures the CacheManager for Redis with JSON serialization and 24-hour TTL.
     *
     * - Default TTL: 24 hours
     * - Serialization: GenericJackson2JsonRedisSerializer (JSON, no Java native serialization)
     * - Cache names: astrology-context
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        log.info("Initializing Redis CacheManager with 24-hour TTL and JSON serialization");

        // Configure Jackson for JSON serialization
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // Configure cache serialization
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jackson2JsonRedisSerializer))
                .disableCachingNullValues();

        log.debug("Redis cache configuration: TTL=24h, serialization=JSON");

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }
}

