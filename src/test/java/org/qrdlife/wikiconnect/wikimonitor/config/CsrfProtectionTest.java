package org.qrdlife.wikiconnect.wikimonitor.config;

import org.junit.jupiter.api.Test;
import org.qrdlife.wikiconnect.wikimonitor.controller.WikiActionController;
import org.qrdlife.wikiconnect.wikimonitor.service.OAuth2Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(WikiActionController.class)
@Import(SecurityConfig.class)
public class CsrfProtectionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuth2Service oauth2Service;

    @MockitoBean
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockitoBean
    private org.qrdlife.wikiconnect.wikimonitor.service.ResponseCacheService responseCacheService;

    @Test
    @WithMockUser
    public void postRequest_withoutCsrfToken_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/action/undo"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    public void postRequest_withCsrfToken_shouldNotReturn403() throws Exception {
        // We expect anything but 403.
        // If we get 302 (Redirect) or 401, it means CSRF check passed.
        mockMvc.perform(post("/api/action/undo").with(csrf()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("CSRF Token was rejected (403 Forbidden)");
                    }
                });
    }

    @Test
    @WithMockUser
    public void postRequest_withInvalidCsrfToken_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/action/undo").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    public void rollback_withoutCsrfToken_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/action/rollback"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    public void rollback_withCsrfToken_shouldSucceed() throws Exception {
        // Expecting 401 or 302 (Not 403)
        mockMvc.perform(post("/api/action/rollback").with(csrf()))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("Rollback: CSRF Token was rejected (403 Forbidden)");
                    }
                });
    }
}
