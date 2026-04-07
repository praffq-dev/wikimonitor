package org.qrdlife.wikiconnect.wikimonitor.controller;

import lombok.RequiredArgsConstructor;
import org.qrdlife.wikiconnect.wikimonitor.service.WikiStreamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class MonitorController {

    private final WikiStreamService streamService;

    @GetMapping("/stream")
    public SseEmitter stream(java.security.Principal principal,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        log.info("New stream subscription request from user: {}, lastEventId: {}",
                principal != null ? principal.getName() : "Anonymous", lastEventId);
        return streamService.subscribe(principal, lastEventId);
    }

    @PostMapping("/api/pause")
    public void pause(java.security.Principal principal) {
        log.info("Pausing stream for user: {}", principal != null ? principal.getName() : "Anonymous");
        streamService.setPaused(principal, true);
    }

    @PostMapping("/api/resume")
    public void resume(java.security.Principal principal) {
        log.info("Resuming stream for user: {}", principal != null ? principal.getName() : "Anonymous");
        streamService.setPaused(principal, false);
    }

    @GetMapping("/api/status")
    public Map<String, Boolean> status(java.security.Principal principal) {
        return Map.of("paused", streamService.isPaused(principal));
    }
}
