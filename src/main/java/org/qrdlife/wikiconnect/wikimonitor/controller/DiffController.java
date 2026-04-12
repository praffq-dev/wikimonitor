package org.qrdlife.wikiconnect.wikimonitor.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class DiffController {

    private final org.qrdlife.wikiconnect.wikimonitor.WikiMonitorApplication wikiMonitorApplication;
    private final org.qrdlife.wikiconnect.wikimonitor.service.ResponseCacheService responseCacheService;

    @GetMapping("/api/diff")
    public String getDiff(
            @RequestParam("server") String server,
            @RequestParam("old") Long oldRev,
            @RequestParam("new") Long newRev) {

        log.info("Diff request for server: {} revisions {} -> {}", server, oldRev, newRev);
        var api = wikiMonitorApplication.getApiMediaWiki("https://" + server + "/w/api.php");
        var mediaWikiService = new org.qrdlife.wikiconnect.wikimonitor.service.MediaWikiService(api, responseCacheService);
        return mediaWikiService.getDiffHtml(oldRev, newRev);
    }
}
