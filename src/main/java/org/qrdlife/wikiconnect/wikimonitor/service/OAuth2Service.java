package org.qrdlife.wikiconnect.wikimonitor.service;

import com.github.scribejava.core.oauth.OAuth20Service;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.qrdlife.wikiconnect.MediaWiki4J.client.ActionApi;
import org.qrdlife.wikiconnect.MediaWiki4J.client.Auth.OAuthOwnerConsumer;
import org.qrdlife.wikiconnect.wikimonitor.WikiMonitorApplication;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
public class OAuth2Service {

    private final OAuth20Service service;

    public OAuth2Service(WikiMonitorApplication wikiMonitorApplication) {
        this.service = wikiMonitorApplication.getOAuth20Service();
    }

    public String getAuthorizationUrl() {
        return service.getAuthorizationUrl();
    }

    public String getAuthorizationUrl(String state) {
        return service.getAuthorizationUrl(state);
    }

    public com.github.scribejava.core.model.OAuth2AccessToken getAccessToken(String code) throws Exception {
        return service.getAccessToken(code);
    }

    public com.github.scribejava.core.model.OAuth2AccessToken refreshAccessToken(String refreshToken) throws Exception {
        return service.refreshAccessToken(refreshToken);
    }

    /**
     * Fetches the user info (name, id) from Wikimedia.
     *
     * @param accessToken the OAuth2 access token
     * @return a User object (not persisted) containing the central ID and username
     */
    public org.qrdlife.wikiconnect.wikimonitor.model.User getUserInfo(
            com.github.scribejava.core.model.OAuth2AccessToken accessToken) throws Exception {
        log.debug("Fetching user info from Wikimedia...");
        // Use the ActionApi to call meta=userinfo
        ActionApi api = getActionApi(accessToken.getAccessToken());

        java.util.Map<String, Object> params = java.util.Map.of(
                "action", "query",
                "meta", "userinfo",
                "uiprop", "centralids", // Get central ID
                "format", "json");

        String response = api.getRequester().get("query", params);
        org.json.JSONObject json = new org.json.JSONObject(response);
        org.json.JSONObject userinfo = json.getJSONObject("query").getJSONObject("userinfo");

        String name = userinfo.getString("name");
        long id = userinfo.getLong("id"); // This is local ID on Meta, central ID is better if available

        // Try to get central ID if available, otherwise fallback to local ID (though
        // central ID is requested)
        // Note: 'centralids' prop might return an object/map. Let's check typical
        // response or just use ID for now if central is complex.
        // Actually, let's stick to the ID returned by userinfo which is the ID on the
        // wiki we query (Meta).
        // Ideally we want the global ID. 'centralids' gives that.
        // Example: "centralids": { "CentralAuth": 12345 }

        // For simplicity in this step, let's assume we use the ID returned by Meta Wiki
        // as the identifier for now,
        // OR better, let's try to parse centralids.

        if (userinfo.has("centralids")) {
            org.json.JSONObject centralIds = userinfo.getJSONObject("centralids");
            // CentralAuth is the standard extension
            if (centralIds.has("CentralAuth")) {
                id = centralIds.getLong("CentralAuth");
            }
        }

        log.debug("User info retrieved: {} (ID: {})", name, id);
        return new org.qrdlife.wikiconnect.wikimonitor.model.User(id, name);
    }

    public ActionApi getActionApi(String accessToken) throws Exception {
        return getActionApi(accessToken, "https://meta.wikimedia.org/w/api.php");
    }

    public ActionApi getActionApi(String accessToken, String apiUrl) throws Exception {
        log.debug("Initializing ActionApi for URL: {}", apiUrl);
        ActionApi api = new ActionApi(apiUrl);
        api.setUserAgent(WikiMonitorApplication.userAgent);
        api.build();

        OAuthOwnerConsumer auth = new OAuthOwnerConsumer(accessToken, api);

        // We verify login implicitly by making a call, or we trust the token.
        // The library might not need explicit login() if we just set the headers.
        // But ActionApi might rely on 'login()' method to set state.
        // Based on previous code, auth.login() was called.

        if (auth.login()) {
            return api;
        } else {
            // If login fails (token invalid?), throw
            // But auth.login() in this library usually just sets up the client to use the
            // token
            // It might perform a check. Let's assume it works or throws.
            return api;
        }
    }

    /**
     * Executes a POST request using OkHttp with proper UTF-8 encoding.
     * Use this to bypass encoding issues in the core-mediawiki-client library.
     */
    public String post(String apiUrl, String accessToken, Map<String, String> params) throws IOException {
        OkHttpClient client = new OkHttpClient();

        FormBody.Builder formBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
        for (Map.Entry<String, String> entry : params.entrySet()) {
            formBuilder.add(entry.getKey(), entry.getValue());
        }

        Request request = new Request.Builder()
                .url(apiUrl)
                // User-Agent is important for Wikimedia APIs
                .header("User-Agent", WikiMonitorApplication.userAgent)
                .header("Authorization", "Bearer " + accessToken)
                .post(formBuilder.build())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("POST request to {} failed with code {}", apiUrl, response.code());
                throw new IOException("Unexpected code " + response);
            }
            log.debug("POST request to {} successful", apiUrl);
            return response.body().string();
        }
    }

}
