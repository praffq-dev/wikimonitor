package org.qrdlife.wikiconnect.wikimonitor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qrdlife.wikiconnect.wikimonitor.service.MediaWikiService;
import org.qrdlife.wikiconnect.wikimonitor.service.OAuth2Service;
import org.qrdlife.wikiconnect.wikimonitor.service.ResponseCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Controller for handling Wiki actions such as undo and rollback.
 * Uses MediaWiki API via OAuth2Service.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class WikiActionController {

    private final OAuth2Service oauth2Service;
    private final ObjectMapper objectMapper;
    private final ResponseCacheService responseCacheService;
    private static final List<Pattern> ALLOWED_HOST_PATTERNS = List.of(
            Pattern.compile("^([a-z0-9-]+\\.)?wikibooks\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?wikidata\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?wikinews\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?wikipedia\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?wikiquote\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?wikisource\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?wikiversity\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?wikivoyage\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?wiktionary\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?wikifunctions\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^([a-z0-9-]+\\.)?mediawiki\\.org$", Pattern.CASE_INSENSITIVE),
            // Explicit wikimedia.org allowlist (no wildcard!)
            Pattern.compile("^commons\\.wikimedia\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^meta\\.wikimedia\\.org$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^wikitech\\.wikimedia\\.org$", Pattern.CASE_INSENSITIVE)
        );

    /**
     * Validates whether a given server name belongs to an allowed Wikimedia domain.
     * Prevents malicious inputs such as protocol injections, paths, or arbitrary
     * domains.
     *
     * @param serverName The server domain to validate (e.g., "en.wikipedia.org").
     * @return {@code true} if the server name is valid and allowed, {@code false}
     *         otherwise.
     */
    private boolean isAllowedServer(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return false;
        }

        String host = serverName.toLowerCase().trim();

        // Prevent protocol injection or paths
        if (host.contains("/") || host.contains(":")) {
            return false;
        }

        return ALLOWED_HOST_PATTERNS.stream()
                .anyMatch(p -> p.matcher(host).matches());
    }

    /**
     * Performs an undo action on a specific revision.
     *
     * @param serverName The server domain (e.g., en.wikipedia.org).
     * @param title      The title of the page.
     * @param revision   The revision ID to undo.
     * @param summary    Optional summary for the undo action.
     * @param session    The HTTP session containing the access token.
     * @return A ResponseEntity containing the JSON response from the MediaWiki API.
     */
    @PostMapping("/api/action/undo")
    public ResponseEntity<?> undo(
            @RequestParam String serverName,
            @RequestParam String title,
            @RequestParam long revision,
            @RequestParam(required = false) String summary,
            HttpSession session) {

        log.info("Undo requested for title: {}, revision: {}, server: {}, summary: {}", title, revision, serverName,
                summary);
        if (!isAllowedServer(serverName)) {
            log.warn("Invalid or disallowed serverName: {}", serverName);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or disallowed serverName"));
        }
        String token = (String) session.getAttribute("ACCESS_TOKEN");
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        try {
            String apiUrl = "https://" + serverName + "/w/api.php";
            var api = oauth2Service.getActionApi(token, apiUrl);
            var mediaWikiService = new MediaWikiService(api, responseCacheService);

            String editResponse = mediaWikiService.undoEdit(title, revision, summary);

            log.info("Undo successful for title: {}, response: {}", title, editResponse);
            JsonNode jsonResponse = objectMapper.readTree(editResponse);
            return ResponseEntity.ok(jsonResponse);

        } catch (Exception e) {
            log.error("Error performing undo for title: " + title, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Performs a rollback action for a user's edits on a page.
     *
     * @param serverName The server domain.
     * @param title      The title of the page.
     * @param user       The user whose edits are to be rolled back.
     * @param session    The HTTP session containing the access token.
     * @return A ResponseEntity containing the JSON response from the MediaWiki API.
     */
    @PostMapping("/api/action/rollback")
    public ResponseEntity<?> rollback(
            @RequestParam String serverName,
            @RequestParam(required = false) String title,
            @RequestParam String user,
            HttpSession session) {

        log.info("Rollback requested for title: {}, user: {}, server: {}", title, user, serverName);
        if (!isAllowedServer(serverName)) {
            log.warn("Invalid or disallowed serverName: {}", serverName);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid or disallowed serverName"));
        }
        String token = (String) session.getAttribute("ACCESS_TOKEN");
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        try {
            String apiUrl = "https://" + serverName + "/w/api.php";
            var api = oauth2Service.getActionApi(token, apiUrl);
            var mediaWikiService = new MediaWikiService(api, responseCacheService);

            String rbResponse = mediaWikiService.rollbackEdit(title, user);

            log.info("Rollback successful for title: {}, response: {}", title, rbResponse);
            JsonNode jsonResponse = objectMapper.readTree(rbResponse);
            return ResponseEntity.ok(jsonResponse);
        } catch (Exception e) {
            log.error("Error performing rollback for title: " + title, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
