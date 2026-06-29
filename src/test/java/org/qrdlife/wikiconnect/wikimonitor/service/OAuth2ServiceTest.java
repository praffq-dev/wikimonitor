package org.qrdlife.wikiconnect.wikimonitor.service;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.oauth.OAuth20Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.qrdlife.wikiconnect.MediaWiki4J.client.ActionApi;
import org.qrdlife.wikiconnect.MediaWiki4J.client.Requester;
import org.qrdlife.wikiconnect.wikimonitor.WikiMonitorApplication;
import org.qrdlife.wikiconnect.wikimonitor.model.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OAuth2ServiceTest {

    @Mock
    private OAuth20Service oauth20Service;
    @Mock
    private ActionApi actionApi;
    @Mock
    private Requester requester;
    @Mock
    private WikiMonitorApplication wikiMonitorApplication;

    private OAuth2Service oauth2Service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        when(wikiMonitorApplication.getOAuth20Service()).thenReturn(oauth20Service);

        oauth2Service = spy(new OAuth2Service(wikiMonitorApplication));
    }

    @Test
    void getAuthorizationUrl_Success() {
        String url = "http://auth.url";
        when(oauth20Service.getAuthorizationUrl()).thenReturn(url);

        String result = oauth2Service.getAuthorizationUrl();

        assertEquals(url, result);
        verify(oauth20Service).getAuthorizationUrl();
    }

    @Test
    void getAuthorizationUrl_WithState_Success() {
        String url = "http://auth.url?state=test";
        String state = "test";
        when(oauth20Service.getAuthorizationUrl(state)).thenReturn(url);

        String result = oauth2Service.getAuthorizationUrl(state);

        assertEquals(url, result);
        verify(oauth20Service).getAuthorizationUrl(state);
    }

    @Test
    void getAccessToken_Success() throws Exception {
        OAuth2AccessToken token = new OAuth2AccessToken("access_token");
        when(oauth20Service.getAccessToken("code")).thenReturn(token);

        OAuth2AccessToken result = oauth2Service.getAccessToken("code");

        assertEquals(token, result);
        verify(oauth20Service).getAccessToken("code");
    }

    @Test
    void getUserInfo_Success() throws Exception {
        OAuth2AccessToken token = new OAuth2AccessToken("access_token");

        // Mock getActionApi to return our mock ActionApi
        doReturn(actionApi).when(oauth2Service).getActionApi(anyString());
        when(actionApi.getRequester()).thenReturn(requester);

        String responseJson = """
                {
                    "query": {
                        "userinfo": {
                            "name": "TestUser",
                            "id": 100,
                            "centralids": {
                                "CentralAuth": 999
                            }
                        }
                    }
                }
                """;
        when(requester.get(eq("query"), any())).thenReturn(responseJson);

        User user = oauth2Service.getUserInfo(token);

        assertNotNull(user);
        assertEquals("TestUser", user.getUsername());
        assertEquals(999L, user.getCentralId()); // verify central ID is used
    }
}
