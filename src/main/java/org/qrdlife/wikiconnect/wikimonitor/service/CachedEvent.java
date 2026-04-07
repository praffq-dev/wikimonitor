package org.qrdlife.wikiconnect.wikimonitor.service;

import org.qrdlife.wikiconnect.wikimonitor.model.RecentChange;

public record CachedEvent(String id, RecentChange recentChange) {
}
