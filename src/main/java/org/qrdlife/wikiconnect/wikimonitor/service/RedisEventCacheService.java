package org.qrdlife.wikiconnect.wikimonitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.qrdlife.wikiconnect.wikimonitor.model.RecentChange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis-backed implementation of {@link EventCacheService}.
 * <p>
 * Uses a Redis List to store the most recent events in chronological order.
 * On Toolforge the shared Redis instance is at
 * {@code redis.svc.tools.eqiad1.wikimedia.cloud:6379}.
 * </p>
 */
@Slf4j
@Service
public class RedisEventCacheService implements EventCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper mapper;
    private final String cacheKey;
    private final int maxSize;

    public RedisEventCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper mapper,
            @Value("${sse.redis.key-prefix:wikimonitor}") String keyPrefix,
            @Value("${sse.event-cache.max-size:1000}") int maxSize) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.cacheKey = keyPrefix + ":sse:event-cache";
        this.maxSize = maxSize;
    }

    @Override
    public void addEvent(String id, RecentChange rc) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", id);
            node.set("data", mapper.valueToTree(rc));
            String json = mapper.writeValueAsString(node);

            redisTemplate.opsForList().rightPush(cacheKey, json);
            redisTemplate.opsForList().trim(cacheKey, -maxSize, -1);
        } catch (Exception e) {
            log.error("Error adding event to Redis cache: {}", e.getMessage());
        }
    }

    @Override
    public List<CachedEvent> getEventsSince(String lastEventId) {
        List<String> entries;
        try {
            entries = redisTemplate.opsForList().range(cacheKey, 0, -1);
        } catch (Exception e) {
            log.error("Error reading Redis cache: {}", e.getMessage());
            return List.of();
        }

        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<CachedEvent> result = new ArrayList<>();
        boolean found = false;

        for (String json : entries) {
            try {
                JsonNode node = mapper.readTree(json);
                String id = node.get("id").asText();

                if (!found) {
                    if (lastEventId.equals(id)) {
                        found = true;
                    }
                    continue;
                }

                RecentChange rc = mapper.treeToValue(node.get("data"), RecentChange.class);
                result.add(new CachedEvent(id, rc));
            } catch (Exception e) {
                log.error("Error deserializing cached event: {}", e.getMessage());
            }
        }

        if (!found) {
            log.warn("Client's last event ID [{}] not found in Redis cache; gap in event coverage possible",
                    lastEventId);
        }

        return result;
    }
}
