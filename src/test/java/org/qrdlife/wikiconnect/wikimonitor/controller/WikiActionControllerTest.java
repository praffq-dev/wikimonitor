package org.qrdlife.wikiconnect.wikimonitor.controller;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.qrdlife.wikiconnect.MediaWiki4J.client.ActionApi;
import org.qrdlife.wikiconnect.wikimonitor.config.SecurityConfig;
import org.qrdlife.wikiconnect.wikimonitor.service.MediaWikiService;
import org.qrdlife.wikiconnect.wikimonitor.service.OAuth2Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@WebMvcTest(WikiActionController.class)
@Import({ SecurityConfig.class, WikiActionControllerTest.TestConfig.class })
public class WikiActionControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuth2Service oauth2Service;

    // We use the real ObjectMapper provided by Spring Boot Test context
    // @Autowired, if needed, but the controller uses it and we don't mock it
    // explicitly
    // so Spring Boot should inject a real one.

    @Test
    public void testUndo() throws Exception {
        // Prepare session
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("ACCESS_TOKEN", "fake-token");

        // Mock OAuth2Service to return a mock ActionApi
        ActionApi mockApi = mock(ActionApi.class);
        when(oauth2Service.getActionApi(anyString(), anyString())).thenReturn(mockApi);

        // Return a valid JSON string from MediaWikiService
        String jsonResponse = "{\"success\":1}";

        try (MockedConstruction<MediaWikiService> mockedMediaWikiService = mockConstruction(MediaWikiService.class,
                (mock, context) -> {
                    when(mock.undoEdit(anyString(), anyLong(), anyString())).thenReturn(jsonResponse);
                })) {

            mockMvc.perform(post("/api/action/undo")
                    .param("serverName", "en.wikipedia.org")
                    .param("title", "Test Page")
                    .param("revision", "12345")
                    .param("summary", "Reverting vandalism")
                    .session(session)
                    .with(csrf())
                    .with(user("user").roles("USER")))
                    .andExpect(status().isOk());

            // Verify our mocks were called
            verify(oauth2Service).getActionApi(eq("fake-token"), eq("https://en.wikipedia.org/w/api.php"));
        }
    }

    @Test
    public void testRollback() throws Exception {
        // Prepare session
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("ACCESS_TOKEN", "fake-token");

        // Mock OAuth2Service
        ActionApi mockApi = mock(ActionApi.class);
        when(oauth2Service.getActionApi(anyString(), anyString())).thenReturn(mockApi);

        // Return a valid JSON string
        String jsonResponse = "{\"success\":1}";

        try (MockedConstruction<MediaWikiService> mockedMediaWikiService = mockConstruction(MediaWikiService.class,
                (mock, context) -> {
                    when(mock.rollbackEdit(anyString(), anyString())).thenReturn(jsonResponse);
                })) {

            mockMvc.perform(post("/api/action/rollback")
                    .param("serverName", "en.wikipedia.org")
                    .param("title", "Test Page")
                    .param("user", "BadUser")
                    .session(session)
                    .with(csrf())
                    .with(user("user").roles("USER")))
                    .andExpect(status().isOk());

            // Verify our mocks were called
            verify(oauth2Service).getActionApi(eq("fake-token"), eq("https://en.wikipedia.org/w/api.php"));
        }
    }

    @Test
    public void testUndoWithInvalidServer() throws Exception {
        // Prepare session
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("ACCESS_TOKEN", "fake-token");

        mockMvc.perform(post("/api/action/undo")
                .param("serverName", "invalid-domain.com")
                .param("title", "Test Page")
                .param("revision", "12345")
                .param("summary", "Reverting vandalism")
                .session(session)
                .with(csrf())
                .with(user("user").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value("Invalid or disallowed serverName"));

        // Verify that oauth2Service is not called
        verifyNoInteractions(oauth2Service);
    }

    @Test
    public void testRollbackWithInvalidServer() throws Exception {
        // Prepare session
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("ACCESS_TOKEN", "fake-token");

        mockMvc.perform(post("/api/action/rollback")
                .param("serverName", "en.wikipedia.org/malicious")
                .param("title", "Test Page")
                .param("user", "BadUser")
                .session(session)
                .with(csrf())
                .with(user("user").roles("USER")))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                        .value("Invalid or disallowed serverName"));

        // Verify that oauth2Service is not called
        verifyNoInteractions(oauth2Service);
    }
}
