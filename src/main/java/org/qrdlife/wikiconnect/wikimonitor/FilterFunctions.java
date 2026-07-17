package org.qrdlife.wikiconnect.wikimonitor;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.qrdlife.wikiconnect.wikimonitor.util.PatternCache;

public class FilterFunctions {

    private final org.qrdlife.wikiconnect.wikimonitor.model.RecentChange rc;

    public FilterFunctions(org.qrdlife.wikiconnect.wikimonitor.model.RecentChange rc) {
        this.rc = rc;
    }

    /*
     * =========================
     * Test Function
     * =========================
     */

    public boolean test() {
        return true;
    }

    /*
     * =========================
     * RecentChange Delegation
     * =========================
     */

    public Long getId() {
        return rc.getId();
    }

    public String getType() {
        return rc.getType();
    }

    public Integer getNamespace() {
        return rc.getNamespace();
    }

    public String getTitle() {
        return rc.getTitle();
    }

    public Long getPageId() {
        return rc.getPageId();
    } // Use getPageId() from RC

    public String getTitleUrl() {
        return rc.getTitleUrl();
    } // Use getTitleUrl() from RC

    public String getUser() {
        return rc.getUser();
    }

    public String getComment() {
        return rc.getComment();
    }

    public String getParsedcomment() {
        return rc.getParsedcomment();
    }

    public Long getTimestamp() {
        return rc.getTimestamp();
    }

    public String getWiki() {
        return rc.getWiki();
    }

    public boolean isBot() {
        return rc.isBot();
    }

    public boolean isMinor() {
        return rc.isMinor();
    }

    public boolean isPatrolled() {
        return rc.isPatrolled();
    }

    public String getNotifyUrl() {
        return rc.getNotifyUrl();
    } // Use getNotifyUrl() from RC

    public String getServerUrl() {
        return rc.getServerUrl();
    } // Use getServerUrl() from RC

    public String getServerName() {
        return rc.getServerName();
    } // Use getServerName() from RC

    public String getServerScriptPath() {
        return rc.getServerScriptPath();
    } // Use getServerScriptPath() from RC

    // SpEL aliases from RecentChange, exposed here
    public String getServer_name() {
        return rc.getServer_name();
    }

    public String getServer_url() {
        return rc.getServer_url();
    }

    public String getServer_script_path() {
        return rc.getServer_script_path();
    }

    public String getTitle_url() {
        return rc.getTitle_url();
    }

    public String getNotify_url() {
        return rc.getNotify_url();
    }

    public String getAdded_lines() {
        return rc.getAdded_lines();
    }

    public String getRemoved_lines() {
        return rc.getRemoved_lines();
    }

    public String getUser_name() {
        return rc.getUser_name();
    }

    public Integer getPage_namespace() {
        return rc.getPage_namespace();
    }

    public long getOld_size() {
        return rc.getOld_size();
    }

    public java.util.List<String> getUser_rights() {
        return rc.getUser_rights();
    }

    public java.util.List<String> getUser_groups() {
        return rc.getUser_groups();
    }

    /*
     * =========================
     * String matching functions
     * =========================
     */

    public boolean contains(String text, String value) {
        return text != null && value != null && text.contains(value);
    }

    public boolean containsIgnoreCase(String text, String value) {
        return text != null && value != null &&
                text.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    public boolean startsWith(String text, String value) {
        return text != null && value != null && text.startsWith(value);
    }

    public boolean endsWith(String text, String value) {
        return text != null && value != null && text.endsWith(value);
    }

    public boolean equals(String text, String value) {
        return text != null && text.equals(value);
    }

    public boolean equalsIgnoreCase(String text, String value) {
        return text != null && text.equalsIgnoreCase(value);
    }

    /*
     * =========================
     * String inspection
     * =========================
     */

    public int length(String text) {
        return text == null ? 0 : text.length();
    }

    public boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }

    public boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /*
     * =========================
     * Regex utilities
     * =========================
     */

    public boolean matches(String text, String regex) {
        if (text == null || regex == null) {
            return false;
        }
        try {
            return PatternCache.getDefault(regex).matcher(text).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    public int regexCount(String text, String regex) {
        if (text == null || regex == null) {
            return 0;
        }
        return rc.rcount(regex, text);
    }

    public int rcount(String text, String regex) {
        return regexCount(text, regex);
    }

    // Delegate count, ensuring subject first: count(text, needle)
    public int count(String text, String needle) {
        return rc.count(needle, text);
    }

    /*
     * =========================
     * Normalization
     * =========================
     */

    public String lower(String text) {
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    public String upper(String text) {
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    public String trim(String text) {
        return text == null ? null : text.trim();
    }

    public String removeWhitespace(String text) {
        return text == null ? null : text.replaceAll("\\s+", "");
    }

    public String normalizeArabic(String text) {
        if (text == null) {
            return null;
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");

        return normalized
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ى', 'ي')
                .replace('ؤ', 'و')
                .replace('ئ', 'ي');
    }

    /*
     * =========================
     * List helpers
     * =========================
     */

    public boolean in(String value, List<String> list) {
        return value != null && list != null && list.contains(value);
    }

    public boolean anyContains(String text, List<String> list) {
        if (text == null || list == null) return false;
        return list.stream()
                .filter(Objects::nonNull)
                .anyMatch(text::contains);
    }

    public boolean allContains(String text, List<String> list) {
        if (text == null || list == null || list.isEmpty()) return false;
        return list.stream()
                .filter(Objects::nonNull)
                .allMatch(text::contains);
    }

    /*
     * =========================
     * Time helpers (optional)
     * =========================
     */

    public int hour(LocalDateTime time) {
        return time == null ? -1 : time.getHour();
    }

    public DayOfWeek dayOfWeek(LocalDateTime time) {
        return time == null ? null : time.getDayOfWeek();
    }

    public boolean isNightTime(LocalDateTime time, int startHour, int endHour) {
        if (time == null) {
            return false;
        }

        int hour = time.getHour();
        return startHour <= endHour
                ? hour >= startHour && hour < endHour
                : hour >= startHour || hour < endHour;
    }
}
