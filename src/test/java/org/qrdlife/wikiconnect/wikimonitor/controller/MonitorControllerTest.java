package org.qrdlife.wikiconnect.wikimonitor.controller;

import org.junit.jupiter.api.Test;
import org.qrdlife.wikiconnect.wikimonitor.config.SecurityConfig;
import org.qrdlife.wikiconnect.wikimonitor.service.WikiStreamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonitorController.class)
@Import(SecurityConfig.class)
public class MonitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WikiStreamService streamService;

    @Test
    public void testStream() throws Exception {
        when(streamService.subscribe(any(), any())).thenReturn(new SseEmitter());

        mockMvc.perform(get("/stream")
                .with(user("user").roles("USER")))
                .andExpect(status().isOk());

        verify(streamService).subscribe(any(), any());
    }

    @Test
    public void testStreamWithLastEventIdHeader() throws Exception {
        when(streamService.subscribe(any(), eq("evt-456"))).thenReturn(new SseEmitter());

        mockMvc.perform(get("/stream")
                .header("Last-Event-ID", "evt-456")
                .with(user("user").roles("USER")))
                .andExpect(status().isOk());

        verify(streamService).subscribe(any(), eq("evt-456"));
    }

    @Test
    public void testPause() throws Exception {
        mockMvc.perform(post("/api/pause")
                .with(csrf())
                .with(user("user").roles("USER")))
                .andExpect(status().isOk());

        verify(streamService).setPaused(any(), eq(true));
    }

    @Test
    public void testResume() throws Exception {
        mockMvc.perform(post("/api/resume")
                .with(csrf())
                .with(user("user").roles("USER")))
                .andExpect(status().isOk());

        verify(streamService).setPaused(any(), eq(false));
    }

    @Test
    public void testStatus() throws Exception {
        when(streamService.isPaused(any())).thenReturn(true);

        mockMvc.perform(get("/api/status")
                .with(user("user").roles("USER")))
                .andExpect(status().isOk());

        verify(streamService).isPaused(any());
    }
}
