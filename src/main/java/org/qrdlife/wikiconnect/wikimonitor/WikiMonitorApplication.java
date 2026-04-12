package org.qrdlife.wikiconnect.wikimonitor;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.qrdlife.wikiconnect.mediawiki.client.ActionApi;
import org.qrdlife.wikiconnect.mediawiki.client.Auth.OAuthOwnerConsumer;
import org.qrdlife.wikiconnect.wikimonitor.OAuth2.MediaWikiApi20;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@EnableScheduling
@lombok.extern.slf4j.Slf4j
public class WikiMonitorApplication {
    public static String version = "1.0.0";
    public static String userAgent = "WikiMonitor/" + version;

    @Value("${app.version}")
    private String appVersion;

    @Value("${ACCESS_TOKEN}")
    private String accessToken;

    @Value("${MEDIAWIKI_CLIENT_ID}")
    private String mediaWikiClientId;

    @Value("${MEDIAWIKI_CLIENT_SECRET}")
    private String mediaWikiClientSecret;

    @jakarta.annotation.PostConstruct
    public void init() {
        version = appVersion;
        userAgent = "WikiMonitor/" + version
                + " (https://phabricator.wikimedia.org/project/profile/8514/; gergesshamon@toolforge.org)";
    }

    private final Map<String, ActionApi> actionApis = new ConcurrentHashMap<>();
    private static ConfigurableApplicationContext context;

    public static void main(String[] args) throws Exception {
        context = SpringApplication.run(WikiMonitorApplication.class, args);
    }

    public static boolean isContextInitialized() {
        return context != null;
    }

    public static WikiMonitorApplication getInstance() {
        return context.getBean(WikiMonitorApplication.class);
    }

    public static <T> T getBean(Class<T> beanClass) {
        return context.getBean(beanClass);
    }

    public ActionApi getApiMediaWiki(String serverUrl) {
        return actionApis.computeIfAbsent(serverUrl, url -> {
            try {
                if (accessToken == null || accessToken.isEmpty()) {
                    throw new IllegalStateException("ACCESS_TOKEN is missing");
                }

                ActionApi api = new ActionApi(url + "/w/api.php");
                api.setFileCookie(new File(".cookies"));
                api.setUserAgent(userAgent);
                api.build();

                OAuthOwnerConsumer auth = new OAuthOwnerConsumer(accessToken, api);
                auth.login();

                return api;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize MediaWiki API", e);
            }
        });
    }

    public OAuth20Service getOAuth20Service() {
        OAuth20Service service = new ServiceBuilder(mediaWikiClientId)
                .apiSecret(mediaWikiClientSecret)
                .userAgent(WikiMonitorApplication.userAgent)
                .build(MediaWikiApi20.instance());
        return service;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("WikiMonitor Application started successfully.");
        log.info("User Agent: {}", userAgent);
    }

}
