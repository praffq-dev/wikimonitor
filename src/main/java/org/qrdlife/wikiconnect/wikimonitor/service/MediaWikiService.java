package org.qrdlife.wikiconnect.wikimonitor.service;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.qrdlife.wikiconnect.mediawiki.client.ActionApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with MediaWiki API.
 * Handles diff loading, user rights, and user groups queries.
 */
@Slf4j
public class MediaWikiService {

    private final ActionApi api;
    private final ResponseCacheService cacheService;

    public MediaWikiService(ActionApi api, ResponseCacheService cacheService) {
        this.api = api;
        this.cacheService = cacheService;
        log.info("MediaWikiService initialized with ActionApi instance");
    }

    private String getCacheKey(String method, Object... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.identityHashCode(api)).append(":");
        sb.append(method);
        for (Object arg : args) {
            sb.append(":").append(arg);
        }
        return sb.toString();
    }

    public String getDiffHtml(long oldRev, long newRev) {
        String key = getCacheKey("diffHtml", oldRev, newRev);
        String cached = cacheService.get(key, String.class);
        if (cached != null) {
            log.debug("Cache hit for diffHtml: {}", key);
            return cached;
        }

        log.info("Requesting diff HTML (table) from {} to {}", oldRev, newRev);

        try {
            Map<String, Object> params = Map.of(
                    "fromrev", oldRev,
                    "torev", newRev,
                    "difftype", "table",
                    "format", "json");

            log.debug("Compare params: {}", params);
            String response = api.getRequester().get("compare", params);
            log.trace("Raw API response: {}", response);

            JSONObject json = new JSONObject(response);

            if (json.has("error")) {
                JSONObject error = json.getJSONObject("error");
                log.warn("API error while fetching diff: {} - {}",
                        error.optString("code"),
                        error.optString("info"));
                return String.format(
                        "<div style='color:red'>API Error: %s - %s</div>",
                        error.optString("code"),
                        error.optString("info"));
            }

            JSONObject compare = json.optJSONObject("compare");
            if (compare == null) {
                log.warn("Compare object missing in API response");
                return "<div>No diff content returned.</div>";
            }

            String diffBody = compare.optString("*");
            if (diffBody == null || diffBody.isBlank()) {
                log.info("No differences found between revisions {} and {}", oldRev, newRev);
                String result = "<div>No differences found.</div>";
                cacheService.put(key, result);
                return result;
            }

            log.debug("Diff HTML successfully generated");
            String finalHtml = "<table class='diff'>" +
                    "<colgroup>" +
                    "<col class='diff-marker'>" +
                    "<col class='diff-content'>" +
                    "<col class='diff-marker'>" +
                    "<col class='diff-content'>" +
                    "</colgroup>" +
                    "<tbody>" +
                    diffBody +
                    "</tbody>" +
                    "</table>";
            cacheService.put(key, finalHtml);
            return finalHtml;

        } catch (Exception e) {
            log.error("Exception while fetching diff HTML", e);
            return "<div style='color:red'>Error fetching diff.</div>";
        }
    }

    public DiffContent loadDiff(Long oldRevision, Long newRevision) {
        if (oldRevision == null || newRevision == null) {
            log.debug("loadDiff called with null revision(s): old={}, new={} — returning empty diff", oldRevision, newRevision);
            return new DiffContent("", "");
        }

        String key = getCacheKey("loadDiff", oldRevision, newRevision);
        DiffContent cached = cacheService.get(key, DiffContent.class);
        if (cached != null) {
            log.debug("Cache hit for loadDiff: {}", key);
            return cached;
        }

        log.info("Loading inline diff from {} to {}", oldRevision, newRevision);

        try {
            Map<String, Object> params = Map.of(
                    "fromrev", oldRevision,
                    "torev", newRevision,
                    "difftype", "inline",
                    "utf8", 1,
                    "uselang", "en",
                    "format", "json");

            log.debug("Inline diff params: {}", params);
            String response = api.getRequester().get("compare", params);
            log.trace("Raw API response: {}", response);

            JSONObject json = new JSONObject(response);
            JSONObject compare = json.optJSONObject("compare");

            if (compare == null || !compare.has("*")) {
                log.warn("Inline diff HTML missing in API response");
                DiffContent empty = new DiffContent("", "");
                cacheService.put(key, empty);
                return empty;
            }

            String html = compare.getString("*");
            Document doc = Jsoup.parse(html);

            Elements insNodes = doc.select("ins");
            Elements delNodes = doc.select("del");

            StringBuilder added = new StringBuilder();
            StringBuilder removed = new StringBuilder();

            insNodes.forEach(e -> added.append(e.text()));
            delNodes.forEach(e -> removed.append(e.text()));

            log.debug("Inline diff parsed: addedChars={}, removedChars={}",
                    added.length(), removed.length());

            DiffContent result = new DiffContent(added.toString(), removed.toString());
            cacheService.put(key, result);
            return result;

        } catch (Exception e) {
            log.error("Failed to load inline diff", e);
            return new DiffContent("", "");
        }
    }

    public List<String> getUserRights(String username) {
        String key = getCacheKey("userRights", username);
        String[] cached = cacheService.get(key, String[].class);
        if (cached != null) {
            log.debug("Cache hit for userRights: {}", key);
            return Arrays.asList(cached);
        }

        log.info("Fetching user rights for user={}", username);

        try {
            Map<String, Object> params = Map.of(
                    "list", "users",
                    "ususers", username,
                    "usprop", "rights",
                    "format", "json");

            String response = api.getRequester().get("query", params);
            JSONObject json = new JSONObject(response);

            JSONArray rightsArray = json.getJSONObject("query")
                    .getJSONArray("users")
                    .getJSONObject(0)
                    .optJSONArray("rights");

            if (rightsArray == null) {
                log.warn("No rights returned for user={}", username);
                return Collections.emptyList();
            }

            List<String> rights = new ArrayList<>();
            for (int i = 0; i < rightsArray.length(); i++) {
                rights.add(rightsArray.getString(i));
            }

            log.debug("User {} has {} rights", username, rights.size());
            List<String> result = Collections.unmodifiableList(rights);
            cacheService.put(key, result);            return result;

        } catch (Exception e) {
            log.error("Failed to fetch user rights for user={}", username, e);
            return Collections.emptyList();
        }
    }

    public List<String> getUserGroups(String username) {
        String key = getCacheKey("userGroups", username);
        String[] cached = cacheService.get(key, String[].class);
        if (cached != null) {
            log.debug("Cache hit for userGroups: {}", key);
            return Arrays.asList(cached);
        }

        log.info("Fetching user groups for user={}", username);

        try {
            Map<String, Object> params = Map.of(
                    "list", "users",
                    "ususers", username,
                    "usprop", "groups",
                    "format", "json");

            String response = api.getRequester().get("query", params);
            JSONObject json = new JSONObject(response);

            JSONArray groupsArray = json.getJSONObject("query")
                    .getJSONArray("users")
                    .getJSONObject(0)
                    .optJSONArray("groups");

            if (groupsArray == null) {
                log.warn("No groups returned for user={}", username);
                return Collections.emptyList();
            }

            List<String> groups = new ArrayList<>();
            for (int i = 0; i < groupsArray.length(); i++) {
                groups.add(groupsArray.getString(i));
            }

            log.debug("User {} belongs to {} groups", username, groups.size());
            List<String> result = Collections.unmodifiableList(groups);
            cacheService.put(key, result);
            return result;

        } catch (Exception e) {
            log.error("Failed to fetch user groups for user={}", username, e);
            return Collections.emptyList();
        }
    }

    public String undoEdit(String title, long revision, String summary) throws Exception {
        log.info("Undo requested: title={}, revision={}", title, revision);

        Map<String, Object> undoParams = new java.util.HashMap<>();
        undoParams.put("title", title);
        undoParams.put("undo", String.valueOf(revision));
        undoParams.put("token", api.getToken("csrf"));
        undoParams.put("format", "json");

        if (summary != null && !summary.isBlank()) {
            undoParams.put("summary", summary);
        }

        log.debug("Undo params prepared (token hidden)");
        String response = api.getRequester().post("edit", undoParams);
        log.trace("Undo API response: {}", response);

        return response;
    }

    public String rollbackEdit(String title, String user) throws Exception {
        log.info("Rollback requested: title={}, user={}", title, user);

        Map<String, Object> rollbackParams = Map.of(
                "title", title != null ? title : "",
                "user", user,
                "token", api.getToken("rollback"),
                "format", "json");

        log.debug("Rollback params prepared (token hidden)");
        String response = api.getRequester().post("rollback", rollbackParams);
        log.trace("Rollback API response: {}", response);

        return response;
    }

    public boolean checkAnyRollbackRights(String username) throws Exception {
        log.info("Checking global and local rollback rights for user={}", username);

        Map<String, Object> params = Map.of(
                "meta", "globaluserinfo",
                "guiuser", username,
                "guiprop", "merged|rights",
                "format", "json");

        log.debug("Global user info params prepared");
        String response = api.getRequester().get("query", params);
        JSONObject json = new JSONObject(response);

        JSONObject globalUserInfo = json.getJSONObject("query").getJSONObject("globaluserinfo");

        JSONArray globalRightsArray = globalUserInfo.optJSONArray("rights");
        if (globalRightsArray != null) {
            for (int i = 0; i < globalRightsArray.length(); i++) {
                if ("rollback".equals(globalRightsArray.getString(i))) {
                    log.debug("User {} has global rollback rights", username);
                    return true;
                }
            }
        }

        JSONArray mergedArray = globalUserInfo.optJSONArray("merged");
        if (mergedArray != null) {
            for (int i = 0; i < mergedArray.length(); i++) {
                JSONObject siteInfo = mergedArray.getJSONObject(i);
                JSONArray localGroups = siteInfo.optJSONArray("groups");

                if (localGroups != null) {
                    for (int j = 0; j < localGroups.length(); j++) {
                        String group = localGroups.getString(j);
                        if ("rollbacker".equals(group) || "sysop".equals(group)) {
                            log.debug("User {} has local {} rights on dataset={}", username, group,
                                    siteInfo.optString("wiki", "unknown"));
                            return true;
                        }
                    }
                }
            }
        }

        log.debug("User {} does not have any rollback rights", username);
        return false;
    }

    public record DiffContent(String lineAdded, String lineRemoved) {
    }
}
