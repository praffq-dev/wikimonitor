package org.qrdlife.wikiconnect.wikimonitor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.qrdlife.wikiconnect.wikimonitor.WikiMonitorApplication;

import java.util.Map;

@Slf4j
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecentChange {

    /* EventStreams fields */
    private Long id;
    private String type;
    private Integer namespace;
    private String title;

    @JsonProperty("page_id")
    private Long pageId;

    @JsonProperty("title_url")
    private String titleUrl;

    private String user;
    private String comment;
    private String parsedcomment;
    private Long timestamp;
    private String wiki;

    private boolean bot;
    private boolean minor;
    private boolean patrolled;

    @JsonProperty("notify_url")
    private String notifyUrl;

    @JsonProperty("server_url")
    private String serverUrl;

    @JsonProperty("server_name")
    private String serverName;

    @JsonProperty("server_script_path")
    private String serverScriptPath;

    private Map<String, Integer> length;
    private Map<String, Long> revision;

    /* Internal fields */
    private boolean flagged;
    private String abuseType;

    /* Diff cache */
    @JsonIgnore
    private transient String lineAdded;

    @JsonIgnore
    private transient String lineRemoved;

    @JsonIgnore
    private transient boolean diffLoaded = false;

    /* Snake_case aliases for SpEL */
    @JsonIgnore
    public String getServer_name() {
        return serverName;
    }

    @JsonIgnore
    public String getServer_url() {
        return serverUrl;
    }

    @JsonIgnore
    public String getServer_script_path() {
        return serverScriptPath;
    }

    @JsonIgnore
    public String getTitle_url() {
        return titleUrl;
    }

    @JsonIgnore
    public String getNotify_url() {
        return notifyUrl;
    }

    /* Diff loading */
    @JsonIgnore
    private synchronized void loadDiffIfNeeded() {
        if (diffLoaded) {
            return;
        }

        try {
            if (!WikiMonitorApplication.isContextInitialized()) {
                this.lineAdded = "";
                this.lineRemoved = "";
                this.diffLoaded = true;
                return;
            }

            // Some event types (e.g. log entries, new-page creations) have no
            // previous revision. Skip the API call rather than passing null into
            // Map.of(), which forbids null values and throws NPE.
            if (revision == null || revision.get("old") == null || revision.get("new") == null) {
                log.debug("Skipping diff load – revision map incomplete: {}", revision);
                this.lineAdded = "";
                this.lineRemoved = "";
                this.diffLoaded = true;
                return;
            }

            var api = WikiMonitorApplication.getInstance().getApiMediaWiki(serverUrl);
            var cacheService = WikiMonitorApplication.getBean(
                    org.qrdlife.wikiconnect.wikimonitor.service.ResponseCacheService.class);
            var service = new org.qrdlife.wikiconnect.wikimonitor.service.MediaWikiService(api, cacheService);
            var diffContent = service.loadDiff(revision.get("old"), revision.get("new"));

            this.lineAdded = diffContent.lineAdded();
            this.lineRemoved = diffContent.lineRemoved();
            this.diffLoaded = true;

        } catch (Exception e) {
            log.error("Failed to load diff", e);
            this.lineAdded = "";
            this.lineRemoved = "";
            this.diffLoaded = true;
        }
    }

    /* Public getters */
    @JsonIgnore
    public String getLineAdded() {
        loadDiffIfNeeded();
        return lineAdded;
    }

    @JsonIgnore
    public String getLineRemoved() {
        loadDiffIfNeeded();
        return lineRemoved;
    }

    /* SpEL Aliases & Helpers */

    @JsonIgnore
    public String getAdded_lines() {
        return getLineAdded();
    }

    @JsonIgnore
    public String getRemoved_lines() {
        return getLineRemoved();
    }

    @JsonIgnore
    public String getUser_name() {
        return user;
    }

    @JsonIgnore
    public Integer getPage_namespace() {
        return namespace;
    }

    @JsonIgnore
    public long getOld_size() {
        if (length != null && length.containsKey("old")) {
            return length.get("old");
        }
        return 0L;
    }

    @JsonIgnore
    public synchronized java.util.List<String> getUser_rights() {
        try {
            if (!WikiMonitorApplication.isContextInitialized()) {
                return java.util.Collections.emptyList();
            }
            var api = WikiMonitorApplication.getInstance().getApiMediaWiki(serverUrl);
            var cacheService = WikiMonitorApplication.getBean(
                    org.qrdlife.wikiconnect.wikimonitor.service.ResponseCacheService.class);
            var service = new org.qrdlife.wikiconnect.wikimonitor.service.MediaWikiService(api, cacheService);
            return service.getUserRights(user);
        } catch (Exception e) {
            log.error("Failed to load user rights", e);
            return java.util.Collections.emptyList();
        }
    }

    /* SpEL Functions */

    @JsonIgnore
    public synchronized java.util.List<String> getUser_groups() {
        try {
            if (!WikiMonitorApplication.isContextInitialized()) {
                return java.util.Collections.emptyList();
            }
            var api = WikiMonitorApplication.getInstance().getApiMediaWiki(serverUrl);
            var cacheService = WikiMonitorApplication.getBean(
                    org.qrdlife.wikiconnect.wikimonitor.service.ResponseCacheService.class);
            var service = new org.qrdlife.wikiconnect.wikimonitor.service.MediaWikiService(api, cacheService);
            return service.getUserGroups(user);
        } catch (Exception e) {
            log.error("Failed to load user groups", e);
            return java.util.Collections.emptyList();
        }
    }

    @JsonIgnore
    public int rcount(String regex, String text) {
        if (text == null || regex == null)
            return 0;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex,
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    @JsonIgnore
    public int count(String needle, String haystack) {
        if (haystack == null || needle == null || needle.isEmpty())
            return 0;
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
