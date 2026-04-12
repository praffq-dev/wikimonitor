package org.qrdlife.wikiconnect.wikimonitor.controller;

import lombok.RequiredArgsConstructor;

import org.qrdlife.wikiconnect.wikimonitor.WikiMonitorApplication;
import org.qrdlife.wikiconnect.wikimonitor.service.MediaWikiService;
import org.qrdlife.wikiconnect.wikimonitor.service.OAuth2Service;
import org.qrdlife.wikiconnect.wikimonitor.service.ResponseCacheService;
import org.qrdlife.wikiconnect.wikimonitor.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class AuthController {

    private final UserService userService;

    private final OAuth2Service oauth2Service;

    private final WikiMonitorApplication wikiMonitorApplication;

    private final ResponseCacheService responseCacheService;

    @Value("${REQUIRE_ROLLBACK_RIGHT:true}")
    private boolean requireRollbackRight;

    @GetMapping("/login")
    public String login(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie c : cookies) {
                if ("refresh_token".equals(c.getName())) {
                    String refreshToken = c.getValue();
                    try {
                        var token = oauth2Service.refreshAccessToken(refreshToken);
                        var user = oauth2Service.getUserInfo(token);
                        var mediaWikiService = new MediaWikiService(
                                wikiMonitorApplication.getApiMediaWiki("https://meta.wikimedia.org/w/api.php"),
                                responseCacheService);
                        if (!mediaWikiService.checkAnyRollbackRights(user.getUsername())) {
                            return "login";
                        }
                        var userDetails = userService.findOrCreateUser(user.getCentralId(), user.getUsername());
                        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        org.springframework.security.core.context.SecurityContextHolder.getContext()
                                .setAuthentication(auth);
                        request.getSession().setAttribute("SPRING_SECURITY_CONTEXT",
                                org.springframework.security.core.context.SecurityContextHolder.getContext());
                        request.getSession().setAttribute(org.qrdlife.wikiconnect.wikimonitor.config.AuthConstants.SESSION_ACCESS_TOKEN, token.getAccessToken());

                        if (token.getExpiresIn() != null) {
                            request.getSession().setAttribute(org.qrdlife.wikiconnect.wikimonitor.config.AuthConstants.SESSION_ACCESS_TOKEN_EXPIRY,
                                    System.currentTimeMillis() + (token.getExpiresIn() * 1000L));
                        }

                        if (token.getRefreshToken() != null) {
                            org.springframework.http.ResponseCookie refreshCookie = org.springframework.http.ResponseCookie.from(org.qrdlife.wikiconnect.wikimonitor.config.AuthConstants.COOKIE_REFRESH_TOKEN, token.getRefreshToken())
                                    .httpOnly(true)
                                    .secure(request.isSecure())
                                    .path("/")
                                    .maxAge(org.qrdlife.wikiconnect.wikimonitor.config.AuthConstants.COOKIE_MAX_AGE_SECONDS)
                                    .sameSite("Lax")
                                    .build();
                            response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, refreshCookie.toString());
                        }

                        log.info("User logged in successfully via refresh token: {} (Local ID: {})",
                                userDetails.getUsername(), userDetails.getId());
                        return "redirect:/";
                    } catch (Exception e) {
                        log.warn("Failed to refresh access token, proceeding to normal login: {}", e.getMessage());
                        org.springframework.http.ResponseCookie clearCookie = org.springframework.http.ResponseCookie.from(org.qrdlife.wikiconnect.wikimonitor.config.AuthConstants.COOKIE_REFRESH_TOKEN, "")
                                .maxAge(0)
                                .path("/")
                                .build();
                        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, clearCookie.toString());
                    }
                }
            }
        }
        return "login";
    }

    @GetMapping("/auth/wikimedia")
    public String loginWikimedia(jakarta.servlet.http.HttpServletRequest request) {
        log.info("Redirecting to Wikimedia OAuth");
        String state = java.util.UUID.randomUUID().toString();
        request.getSession().setAttribute("OAUTH_STATE", state);
        return "redirect:" + oauth2Service.getAuthorizationUrl(state);
    }

    @GetMapping("/oauth2/callback")
    public String oauthCallback(@RequestParam String code, @RequestParam String state,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        log.info("OAuth callback received with code length: {}", code.length());

        String sessionState = (String) request.getSession().getAttribute("OAUTH_STATE");
        if (sessionState == null || !sessionState.equals(state)) {
            log.error("OAuth state mismatch! Expected: {}, Received: {}", sessionState, state);
            return "redirect:/login?error=invalid_state";
        }
        request.getSession().removeAttribute("OAUTH_STATE");

        try {
            var apiMeta = wikiMonitorApplication.getApiMediaWiki("https://meta.wikimedia.org/w/api.php");
            var mediaWikiService = new MediaWikiService(apiMeta, responseCacheService);
            var token = oauth2Service.getAccessToken(code);
            var user = oauth2Service.getUserInfo(token);
            if (requireRollbackRight && !mediaWikiService.checkAnyRollbackRights(user.getUsername())) {
                return "redirect:/login?error=no_rollback_rights";
            }

            var userDetails = userService.findOrCreateUser(user.getCentralId(), user.getUsername());

            // Manually set security context
            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            request.getSession().setAttribute("SPRING_SECURITY_CONTEXT",
                    org.springframework.security.core.context.SecurityContextHolder.getContext());
            request.getSession().setAttribute(org.qrdlife.wikiconnect.wikimonitor.config.AuthConstants.SESSION_ACCESS_TOKEN, token.getAccessToken());

            if (token.getExpiresIn() != null) {
                request.getSession().setAttribute(org.qrdlife.wikiconnect.wikimonitor.config.AuthConstants.SESSION_ACCESS_TOKEN_EXPIRY,
                        System.currentTimeMillis() + (token.getExpiresIn() * 1000L));
            }

            if (token.getRefreshToken() != null) {
                org.springframework.http.ResponseCookie refreshCookie = org.springframework.http.ResponseCookie.from(org.qrdlife.wikiconnect.wikimonitor.config.AuthConstants.COOKIE_REFRESH_TOKEN, token.getRefreshToken())
                        .httpOnly(true)
                        .secure(request.isSecure())
                        .path("/")
                        .maxAge(org.qrdlife.wikiconnect.wikimonitor.config.AuthConstants.COOKIE_MAX_AGE_SECONDS)
                        .sameSite("Lax")
                        .build();
                response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, refreshCookie.toString());
            }

            log.info("User logged in successfully: {} (Local ID: {})", userDetails.getUsername(), userDetails.getId());
            return "redirect:/";
        } catch (Exception e) {
            log.error("OAuth login failed", e);
            return "redirect:/login?error";
        }
    }
}
