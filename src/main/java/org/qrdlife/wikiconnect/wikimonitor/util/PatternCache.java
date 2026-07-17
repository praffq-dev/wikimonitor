package org.qrdlife.wikiconnect.wikimonitor.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Bounded, TTL-based cache for compiled {@link Pattern} instances.
 * 
 * <p>Avoids the performance overhead of recompiling identical regular expressions on
 * every high-frequency event in the live stream. Two separate bounded caches with a
 * maximum size of 2000 and an idle expiration of 2 hours are maintained: one for the default
 * pattern compilation, and one with case-insensitive and dot-all flags enabled.
 * 
 * <p>Uses Caffeine to ensure the caches are bounded and prevent unbounded memory growth,
 * which would occur with a plain ConcurrentHashMap.
 */
public class PatternCache {

    private static final Cache<String, Pattern> defaultCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build();

    private static final Cache<String, Pattern> caseInsensitiveDotAllCache = Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build();

    /**
     * Gets a compiled Pattern for the default regex configuration.
     *
     * @param regex the regular expression string
     * @return the compiled Pattern
     * @throws PatternSyntaxException if the regular expression's syntax is invalid
     */
    public static Pattern getDefault(String regex) {
        if (regex == null) {
            throw new NullPointerException("regex cannot be null");
        }
        return defaultCache.get(regex, Pattern::compile);
    }

    /**
     * Gets a compiled Pattern with {@link Pattern#CASE_INSENSITIVE} and {@link Pattern#DOTALL} flags.
     *
     * @param regex the regular expression string
     * @return the compiled Pattern
     * @throws PatternSyntaxException if the regular expression's syntax is invalid
     */
    public static Pattern getCaseInsensitiveDotAll(String regex) {
        if (regex == null) {
            throw new NullPointerException("regex cannot be null");
        }
        return caseInsensitiveDotAllCache.get(regex, r -> Pattern.compile(r, Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
    }
}
