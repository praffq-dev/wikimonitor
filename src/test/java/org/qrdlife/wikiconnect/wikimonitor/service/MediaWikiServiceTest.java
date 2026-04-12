package org.qrdlife.wikiconnect.wikimonitor.service;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.qrdlife.wikiconnect.mediawiki.client.ActionApi;
import org.qrdlife.wikiconnect.mediawiki.client.Requester;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaWikiServiceTest {

    @Mock
    private ActionApi actionApi;
    @Mock
    private Requester requester;
    @Mock
    private ResponseCacheService cacheService;

    private MediaWikiService mediaWikiService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mediaWikiService = new MediaWikiService(actionApi, cacheService);
        when(actionApi.getRequester()).thenReturn(requester);
    }

    @Test
    void getDiffHtml_Success() throws Exception {
        String responseJson = """
                {
                    "compare": {
                        "*": "<tr><td>Diff Content</td></tr>"
                    }
                }
                """;
        when(requester.get(eq("compare"), anyMap())).thenReturn(responseJson);

        // Remove serverUrl arg
        String result = mediaWikiService.getDiffHtml(100L, 101L);

        assertNotNull(result);
        assertTrue(result.contains("Diff Content"));
        assertTrue(result.contains("<table class='diff'>"));
    }

    @Test
    void getDiffHtml_Error() throws Exception {
        String responseJson = """
                {
                    "error": {
                        "code": "missingrev",
                        "info": "Revision not found"
                    }
                }
                """;
        when(requester.get(eq("compare"), anyMap())).thenReturn(responseJson);

        // Remove serverUrl arg
        String result = mediaWikiService.getDiffHtml(100L, 101L);

        assertTrue(result.contains("API Error: missingrev"));
    }

    @Test
    void loadDiff_Success() throws Exception {
        // html with ins and del
        String innerHtml = "<ins>Added</ins><del>Removed</del>";
        String responseJson = new JSONObject().put("compare", new JSONObject().put("*", innerHtml)).toString();

        when(requester.get(eq("compare"), anyMap())).thenReturn(responseJson);

        // Remove serverUrl arg
        MediaWikiService.DiffContent content = mediaWikiService.loadDiff(100L, 101L);

        assertNotNull(content);
        assertEquals("Added", content.lineAdded());
        assertEquals("Removed", content.lineRemoved());
    }

    @Test
    void getUserRights_Success() throws Exception {
        String responseJson = """
                {
                    "query": {
                        "users": [
                            {
                                "name": "User",
                                "rights": ["read", "write"]
                            }
                        ]
                    }
                }
                """;
        when(requester.get(eq("query"), anyMap())).thenReturn(responseJson);

        // Remove serverUrl arg
        List<String> rights = mediaWikiService.getUserRights("User");

        assertNotNull(rights);
        assertEquals(2, rights.size());
        assertTrue(rights.contains("read"));
        assertTrue(rights.contains("write"));
    }

    @Test
    void getUserGroups_Success() throws Exception {
        String responseJson = """
                {
                    "query": {
                        "users": [
                            {
                                "name": "User",
                                "groups": ["bot", "sysop"]
                            }
                        ]
                    }
                }
                """;
        when(requester.get(eq("query"), anyMap())).thenReturn(responseJson);

        // Remove serverUrl arg
        List<String> groups = mediaWikiService.getUserGroups("User");

        assertNotNull(groups);
        assertEquals(2, groups.size());
        assertTrue(groups.contains("bot"));
        assertTrue(groups.contains("sysop"));
    }

    @Test
    void undoEdit_Success() throws Exception {
        String responseJson = """
                {
                    "edit": {
                        "result": "Success",
                        "pageid": 123,
                        "title": "Test Page",
                        "oldrevid": 100,
                        "newrevid": 101
                    }
                }
                """;
        // Mock getToken("csrf") call
        when(actionApi.getToken("csrf")).thenReturn("fake_csrf_token");
        when(requester.post(eq("edit"), anyMap())).thenReturn(responseJson);

        // Remove ActionApi arg
        String result = mediaWikiService.undoEdit("Test Page", 100L, "Undo summary");

        assertNotNull(result);
        assertTrue(result.contains("Success"));
        verify(requester).post(eq("edit"), argThat(map -> map.get("title").equals("Test Page") &&
                map.get("undo").equals("100") &&
                map.get("token").equals("fake_csrf_token") &&
                map.get("summary").equals("Undo summary")));
    }

    @Test
    void rollbackEdit_Success() throws Exception {
        String responseJson = """
                {
                    "rollback": {
                        "title": "Test Page",
                        "user": "BadUser",
                        "summary": "Reverted edits by BadUser",
                        "old_revid": 99,
                        "new_revid": 101
                    }
                }
                """;
        // Mock getToken("rollback") call
        when(actionApi.getToken("rollback")).thenReturn("fake_rollback_token");
        when(requester.post(eq("rollback"), anyMap())).thenReturn(responseJson);

        // Remove ActionApi arg
        String result = mediaWikiService.rollbackEdit("Test Page", "BadUser");

        assertNotNull(result);
        assertTrue(result.contains("rollback"));
        verify(requester).post(eq("rollback"), argThat(map -> map.get("title").equals("Test Page") &&
                map.get("user").equals("BadUser") &&
                map.get("token").equals("fake_rollback_token")));
    }

    @Test
    void checkAnyRollbackRights_GlobalRollback() throws Exception {
        String responseJson = """
                {
                    "query": {
                        "globaluserinfo": {
                            "rights": ["checkuser", "rollback", "steward"]
                        }
                    }
                }
                """;
        when(requester.get(eq("query"), anyMap())).thenReturn(responseJson);

        boolean result = mediaWikiService.checkAnyRollbackRights("User");
        assertTrue(result);
    }

    @Test
    void checkAnyRollbackRights_LocalRollback() throws Exception {
        String responseJson = """
                {
                    "query": {
                        "globaluserinfo": {
                            "rights": ["checkuser"],
                            "merged": [
                                {
                                    "wiki": "enwiki",
                                    "groups": ["editor", "rollbacker"]
                                },
                                {
                                    "wiki": "frwiki",
                                    "groups": ["editor"]
                                }
                            ]
                        }
                    }
                }
                """;
        when(requester.get(eq("query"), anyMap())).thenReturn(responseJson);

        boolean result = mediaWikiService.checkAnyRollbackRights("User");
        assertTrue(result);
    }

    @Test
    void checkAnyRollbackRights_LocalSysop() throws Exception {
        String responseJson = """
                {
                    "query": {
                        "globaluserinfo": {
                            "merged": [
                                {
                                    "wiki": "enwiki",
                                    "groups": ["editor", "sysop"]
                                }
                            ]
                        }
                    }
                }
                """;
        when(requester.get(eq("query"), anyMap())).thenReturn(responseJson);

        boolean result = mediaWikiService.checkAnyRollbackRights("User");
        assertTrue(result);
    }

    @Test
    void checkAnyRollbackRights_NoRights() throws Exception {
        String responseJson = """
                {
                    "query": {
                        "globaluserinfo": {
                            "rights": ["patroller"],
                            "merged": [
                                {
                                    "wiki": "enwiki",
                                    "groups": ["editor"]
                                }
                            ]
                        }
                    }
                }
                """;
        when(requester.get(eq("query"), anyMap())).thenReturn(responseJson);

        boolean result = mediaWikiService.checkAnyRollbackRights("User");
        assertFalse(result);
    }
}
