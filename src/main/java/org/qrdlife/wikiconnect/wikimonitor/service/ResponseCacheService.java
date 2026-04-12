package org.qrdlife.wikiconnect.wikimonitor.service;

/**
 * Generic key-value cache abstraction for API response caching.
 * Backed by Redis to allow sharing across multiple server instances.
 */
public interface ResponseCacheService {

    /**
     * Retrieves a cached value.
     *
     * @param key  the cache key
     * @param type the expected value type
     * @return the cached value, or {@code null} on miss or error
     */
    <T> T get(String key, Class<T> type);

    /**
     * Stores a value in the cache with a time-to-live.
     *
     * @param key        the cache key
     * @param value      the value to cache
     * @param ttlSeconds seconds until the entry expires
     */
    void put(String key, Object value, long ttlSeconds);

    /**
     * Stores a value in the cache with the default configured TTL.
     *
     * @param key   the cache key
     * @param value the value to cache
     */
    void put(String key, Object value);
}
