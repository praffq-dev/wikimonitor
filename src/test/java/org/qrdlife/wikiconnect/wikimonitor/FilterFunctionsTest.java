package org.qrdlife.wikiconnect.wikimonitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qrdlife.wikiconnect.wikimonitor.model.RecentChange;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FilterFunctionsTest {

    private FilterFunctions functions;
    private RecentChange rc;

    @BeforeEach
    void setUp() {
        rc = new RecentChange();
        rc.setTitle("Test Page");
        rc.setUser("WikiUser");
        rc.setBot(false);
        rc.setTimestamp(System.currentTimeMillis() / 1000); // Unix timestamp

        functions = new FilterFunctions(rc);
    }

    @Test
    void testBasicStringFunctions() {
        assertTrue(functions.contains("Hello World", "World"));
        assertFalse(functions.contains("Hello World", "Universe"));

        assertTrue(functions.containsIgnoreCase("Hello World", "world"));
        assertTrue(functions.startsWith("Hello World", "Hello"));
        assertTrue(functions.endsWith("Hello World", "World"));
    }

    @Test
    void testListFunctions() {
        assertTrue(functions.in("a", Arrays.asList("a", "b", "c")));
        assertFalse(functions.in("d", Arrays.asList("a", "b", "c")));

        assertTrue(functions.anyContains("test string", Arrays.asList("test", "foo")));
        assertFalse(functions.anyContains("bar string", Arrays.asList("test", "foo")));
    }

    @Test
    void testMatches() {
        assertTrue(functions.matches("Hello 123", ".*\\d+.*"));
        assertFalse(functions.matches("Hello World", ".*\\d+.*"));
        assertFalse(functions.matches(null, ".*\\d+.*"));
        assertFalse(functions.matches("Hello 123", null));
        assertFalse(functions.matches(null, null));
        assertFalse(functions.matches("Hello 123", "[invalid regex"));
    }

    @Test
    void testRegex() {
        assertEquals(2, functions.regexCount("test test", "test"));
        assertEquals(0, functions.regexCount("test test", "[invalid regex"));
        assertEquals(0, functions.rcount("test test", "[invalid regex"));
    }

    @Test
    void testNormalization() {
        assertEquals("test", functions.lower("TEST"));
        assertEquals("TEST", functions.upper("test"));
        assertEquals("test", functions.trim(" test "));
    }

    @Test
    void testArabicNormalization() {
        // "أحمد" -> "احمد"
        assertEquals("احمد", functions.normalizeArabic("أحمد"));
    }

    @Test
    void testRecentChangeDelegation() {
        assertEquals("Test Page", functions.getTitle());
        assertEquals("WikiUser", functions.getUser());
        assertFalse(functions.isBot());
    }

    @Test
    void testAddedLinesAccess() {
        rc.setLineAdded("Added content");
        rc.setDiffLoaded(true);
        // Note: In real app diff is loaded on demand, here we simulate it

        assertEquals("Added content", functions.getAdded_lines());
    }

    @Test
    void testTestFunction() {
        // Test the new dummy function
        assertTrue(functions.test());
    }
}
