package org.qrdlife.wikiconnect.wikimonitor.model;

import java.util.List;
import java.util.Map;

public record StreamEventDTO(
    String user,
    String title,
    String comment,
    String wiki,
    String server_url,
    Integer namespace,
    String server_name,
    Map<String, Long> revision,
    Map<String, Integer> length,
    boolean flagged,
    List<String> matchedFilters,
    Long timestamp
) {
    public static StreamEventDTO fromRecentChange(RecentChange rc, List<String> matchedFilters) {
        return new StreamEventDTO(
            rc.getUser(),
            rc.getTitle(),
            rc.getComment(),
            rc.getWiki(),
            rc.getServerUrl(),
            rc.getNamespace(),
            rc.getServer_name(),
            rc.getRevision(),
            rc.getLength(),
            true, // flagged
            matchedFilters,
            rc.getTimestamp()
        );
    }
}