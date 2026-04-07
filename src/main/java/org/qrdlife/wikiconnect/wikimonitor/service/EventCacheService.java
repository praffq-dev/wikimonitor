package org.qrdlife.wikiconnect.wikimonitor.service;

import org.qrdlife.wikiconnect.wikimonitor.model.RecentChange;

import java.util.List;

/**
 * Abstraction for caching recent SSE events so that reconnecting clients
 * can catch up on events they missed during a brief disconnection window.
 */
public interface EventCacheService {

    /**
     * Stores an event in the cache.
     *
     * @param id the Wikimedia event stream ID
     * @param rc the parsed RecentChange event
     */
    void addEvent(String id, RecentChange rc);

    /**
     * Returns all cached events that arrived <em>after</em> the given event ID.
     * If the ID is not found in the cache the returned list is empty.
     *
     * @param lastEventId the last event ID the client successfully received
     * @return events the client missed (in chronological order), or empty list
     */
    List<CachedEvent> getEventsSince(String lastEventId);
}
