package org.qrdlife.wikiconnect.wikimonitor.util;

import org.junit.jupiter.api.Test;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import static org.junit.jupiter.api.Assertions.*;

class PatternCacheTest {

    @Test
    void testGetDefault_CacheReuse() {
        Pattern p1 = PatternCache.getDefault("abc");
        Pattern p2 = PatternCache.getDefault("abc");
        assertSame(p1, p2, "Default pattern cache should reuse identical compiled pattern instances");
    }

    @Test
    void testGetCaseInsensitiveDotAll_CacheReuse() {
        Pattern p1 = PatternCache.getCaseInsensitiveDotAll("abc");
        Pattern p2 = PatternCache.getCaseInsensitiveDotAll("abc");
        assertSame(p1, p2, "Case-insensitive/Dotall pattern cache should reuse identical compiled pattern instances");
        
        // Assert flags are present
        assertEquals(Pattern.CASE_INSENSITIVE | Pattern.DOTALL, p1.flags());
    }

    @Test
    void testPropagatesPatternSyntaxException() {
        assertThrows(PatternSyntaxException.class, () -> PatternCache.getDefault("[invalid regex"));
        assertThrows(PatternSyntaxException.class, () -> PatternCache.getCaseInsensitiveDotAll("[invalid regex"));
    }
    
    @Test
    void testNullRegex() {
        assertThrows(NullPointerException.class, () -> PatternCache.getDefault(null));
        assertThrows(NullPointerException.class, () -> PatternCache.getCaseInsensitiveDotAll(null));
    }
}
