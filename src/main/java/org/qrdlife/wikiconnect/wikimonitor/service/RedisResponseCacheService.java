package org.qrdlife.wikiconnect.wikimonitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed implementation of {@link ResponseCacheService}.
 * <p>
 * Stores serialised JSON values as Redis Strings with a configurable TTL.
 * Gracefully returns {@code null} on any Redis or deserialisation error
 * so that callers fall back to a fresh API call.
 * </p>
 */
@Slf4j
@Service
public class RedisResponseCacheService implements ResponseCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper mapper;
    private final String keyPrefix;
    private final long defaultTtlSeconds;

    public RedisResponseCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper mapper,
            @Value("${sse.redis.key-prefix:wikimonitor}") String keyPrefix,
            @Value("${response-cache.ttl.seconds:10}") long defaultTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.keyPrefix = keyPrefix + ":response-cache:";
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(keyPrefix + key);
            if (json == null) {
                return null;
            }
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Redis cache read error for key [{}]: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        try {
            String json = mapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(keyPrefix + key, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis cache write error for key [{}]: {}", key, e.getMessage());
        }
    }

    /**
     * Convenience: stores with the default TTL from configuration.
     */
    public void put(String key, Object value) {
        put(key, value, defaultTtlSeconds);
    }

    long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }
}
