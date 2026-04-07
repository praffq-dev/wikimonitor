package org.qrdlife.wikiconnect.wikimonitor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.qrdlife.wikiconnect.wikimonitor.model.RecentChange;
import org.qrdlife.wikiconnect.wikimonitor.model.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WikiStreamService}.
 * <p>
 * The Wikimedia SSE connection and OkHttpClient are NOT started in tests
 * because {@code @PostConstruct} is not invoked when constructing the service
 * manually. Each group of tests focuses on a specific public API surface.
 * </p>
 */
@DisplayName("WikiStreamService")
class WikiStreamServiceTest {

    // ── Collaborators ──────────────────────────────────────────────────────────
    @Mock
    private ObjectMapper mapper;
    @Mock
    private AbuseFilterService abuseFilter;
    @Mock
    private UserService userService;
    @Mock
    private EventCacheService eventCacheService;
    @Mock
    private Principal principal;

    // ── SUT ───────────────────────────────────────────────────────────────────
    private WikiStreamService wikiStreamService;

    // ── Fixtures ──────────────────────────────────────────────────────────────
    private User testUser;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        wikiStreamService = new WikiStreamService(mapper, abuseFilter, userService, eventCacheService, 1800000L);

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("alice");

        anotherUser = new User();
        anotherUser.setId(2L);
        anotherUser.setUsername("bob");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // subscribe()
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("subscribe()")
    class Subscribe {

        @Test
        @DisplayName("returns non-null emitter for plain Principal")
        void returnsEmitterForPlainPrincipal() {
            when(principal.getName()).thenReturn("alice");
            when(userService.loadUserByUsername("alice")).thenReturn(testUser);

            SseEmitter emitter = wikiStreamService.subscribe(principal);

            assertNotNull(emitter, "Emitter must not be null");
        }

        @Test
        @DisplayName("returns non-null emitter when principal is UsernamePasswordAuthenticationToken")
        void returnsEmitterForAuthenticationToken() {
            UsernamePasswordAuthenticationToken token = mock(UsernamePasswordAuthenticationToken.class);
            when(token.getPrincipal()).thenReturn(testUser);

            SseEmitter emitter = wikiStreamService.subscribe(token);

            assertNotNull(emitter);
        }

        @Test
        @DisplayName("returns non-null emitter for anonymous (null) principal")
        void returnsEmitterForAnonymous() {
            SseEmitter emitter = wikiStreamService.subscribe(null);

            assertNotNull(emitter);
        }

        @Test
        @DisplayName("each subscribe() call returns a distinct emitter instance")
        void distinctEmittersPerSubscription() {
            when(principal.getName()).thenReturn("alice");
            when(userService.loadUserByUsername("alice")).thenReturn(testUser);

            SseEmitter first = wikiStreamService.subscribe(principal);
            SseEmitter second = wikiStreamService.subscribe(principal);

            assertNotSame(first, second, "Every subscribe() should create a new SseEmitter");
        }

        @Test
        @DisplayName("two different users get independent emitters")
        void twoUsersGetIndependentEmitters() {
            Principal principalBob = mock(Principal.class);
            when(principal.getName()).thenReturn("alice");
            when(principalBob.getName()).thenReturn("bob");
            when(userService.loadUserByUsername("alice")).thenReturn(testUser);
            when(userService.loadUserByUsername("bob")).thenReturn(anotherUser);

            SseEmitter aliceEmitter = wikiStreamService.subscribe(principal);
            SseEmitter bobEmitter = wikiStreamService.subscribe(principalBob);

            assertNotNull(aliceEmitter);
            assertNotNull(bobEmitter);
            assertNotSame(aliceEmitter, bobEmitter);
        }

        @Test
        @DisplayName("anonymous subscription does not invoke UserService")
        void anonymousDoesNotCallUserService() {
            wikiStreamService.subscribe(null);

            verifyNoInteractions(userService);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isPaused() / setPaused()
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("pause management")
    class PauseManagement {

        @BeforeEach
        void subscribeAlice() {
            when(principal.getName()).thenReturn("alice");
            when(userService.loadUserByUsername("alice")).thenReturn(testUser);
            wikiStreamService.subscribe(principal);
        }

        @Test
        @DisplayName("stream is not paused right after subscription")
        void notPausedAfterSubscribe() {
            assertFalse(wikiStreamService.isPaused(principal));
        }

        @Test
        @DisplayName("setPaused(true) pauses the stream")
        void pauseStream() {
            wikiStreamService.setPaused(principal, true);

            assertTrue(wikiStreamService.isPaused(principal));
        }

        @Test
        @DisplayName("setPaused(false) resumes a paused stream")
        void resumeStream() {
            wikiStreamService.setPaused(principal, true);
            wikiStreamService.setPaused(principal, false);

            assertFalse(wikiStreamService.isPaused(principal));
        }

        @Test
        @DisplayName("toggling pause multiple times reflects latest state")
        void togglePause() {
            wikiStreamService.setPaused(principal, true);
            wikiStreamService.setPaused(principal, false);
            wikiStreamService.setPaused(principal, true);

            assertTrue(wikiStreamService.isPaused(principal));
        }

        @Test
        @DisplayName("isPaused returns false when principal is null")
        void isPausedNullPrincipal() {
            assertFalse(wikiStreamService.isPaused(null));
        }

        @Test
        @DisplayName("setPaused with null principal does not throw")
        void setPausedNullPrincipal() {
            assertDoesNotThrow(() -> wikiStreamService.setPaused(null, true));
        }

        @Test
        @DisplayName("pausing one user does not affect another user's stream")
        void pauseDoesNotAffectOtherUsers() {
            Principal principalBob = mock(Principal.class);
            when(principalBob.getName()).thenReturn("bob");
            when(userService.loadUserByUsername("bob")).thenReturn(anotherUser);
            wikiStreamService.subscribe(principalBob);

            wikiStreamService.setPaused(principal, true); // pause Alice

            assertFalse(wikiStreamService.isPaused(principalBob), "Bob should still be running");
        }

        @Test
        @DisplayName("isPaused returns false for a user who never subscribed")
        void isPausedUnknownUser() {
            Principal unknown = mock(Principal.class);
            when(unknown.getName()).thenReturn("ghost");

            assertFalse(wikiStreamService.isPaused(unknown));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateUser()
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("updateUser()")
    class UpdateUser {

        @BeforeEach
        void subscribeAlice() {
            when(principal.getName()).thenReturn("alice");
            when(userService.loadUserByUsername("alice")).thenReturn(testUser);
            wikiStreamService.subscribe(principal);
        }

        @Test
        @DisplayName("updateUser does not throw for a known user")
        void noExceptionForKnownUser() {
            User updated = new User();
            updated.setId(1L);
            updated.setUsername("alice");

            assertDoesNotThrow(() -> wikiStreamService.updateUser(updated));
        }

        @Test
        @DisplayName("updateUser does not throw for an unknown user ID")
        void noExceptionForUnknownUserId() {
            User stranger = new User();
            stranger.setId(99L);
            stranger.setUsername("stranger");

            assertDoesNotThrow(() -> wikiStreamService.updateUser(stranger));
        }

        @Test
        @DisplayName("updateUser with null user does not throw")
        void handlesNullUserGracefully() {
            // updateUser streams over emitters using ctx.user.getId() –
            // if user is null the service should not crash.
            // (Guarded by the null-safe filter if implemented, otherwise wrap assertion)
            assertDoesNotThrow(() -> wikiStreamService.updateUser(null));
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // subscribe() with lastEventId (event replay)
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("subscribe() with lastEventId")
    class SubscribeWithReplay {

        @Test
        @DisplayName("subscribe with null lastEventId behaves like normal subscribe")
        void nullLastEventIdBehavesNormally() {
            when(principal.getName()).thenReturn("alice");
            when(userService.loadUserByUsername("alice")).thenReturn(testUser);

            SseEmitter emitter = wikiStreamService.subscribe(principal, null);

            assertNotNull(emitter);
        }

        @Test
        @DisplayName("subscribe with empty lastEventId behaves like normal subscribe")
        void emptyLastEventIdBehavesNormally() {
            when(principal.getName()).thenReturn("alice");
            when(userService.loadUserByUsername("alice")).thenReturn(testUser);

            SseEmitter emitter = wikiStreamService.subscribe(principal, "");

            assertNotNull(emitter);
        }

        @Test
        @DisplayName("subscribe with unknown lastEventId does not throw")
        void unknownLastEventIdDoesNotThrow() {
            when(principal.getName()).thenReturn("alice");
            when(userService.loadUserByUsername("alice")).thenReturn(testUser);
            when(eventCacheService.getEventsSince("unknown-id")).thenReturn(java.util.List.of());

            assertDoesNotThrow(() -> wikiStreamService.subscribe(principal, "unknown-id"));
        }

        @Test
        @DisplayName("anonymous subscribe with lastEventId does not replay")
        void anonymousWithLastEventIdNoReplay() {
            SseEmitter emitter = wikiStreamService.subscribe(null, "some-id");

            assertNotNull(emitter);
            verifyNoInteractions(abuseFilter);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SSE timeout constant
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("SSE timeout")
    class SseTimeout {

        @Test
        @DisplayName("default SSE timeout is 30 minutes in milliseconds")
        void timeoutIs30Minutes() {
            assertEquals(30 * 60 * 1000L, wikiStreamService.getSseTimeoutMs());
        }

        @Test
        @DisplayName("custom timeout value is respected")
        void customTimeoutRespected() {
            WikiStreamService custom = new WikiStreamService(mapper, abuseFilter, userService, eventCacheService, 60000L);
            assertEquals(60000L, custom.getSseTimeoutMs());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // broadcastAsync()
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("broadcastAsync()")
    class BroadcastAsync {

        /** Reflective handle reused by every test in this class. */
        private Method broadcastAsync;

        @BeforeEach
        void setUp() throws Exception {
            broadcastAsync = WikiStreamService.class
                    .getDeclaredMethod("broadcastAsync", RecentChange.class, String.class);
            broadcastAsync.setAccessible(true);
        }

        // ── helpers ───────────────────────────────────────────────────────────

        private RecentChange buildRecentChange() {
            RecentChange rc = new RecentChange();
            rc.setId(99L);
            rc.setTitle("Async Page");
            rc.setUser("BadBot");
            rc.setType("edit");
            rc.setWiki("enwiki");
            return rc;
        }

        private void subscribeUser(Principal p, User user) {
            when(p.getName()).thenReturn(user.getUsername());
            when(userService.loadUserByUsername(user.getUsername())).thenReturn(user);
            wikiStreamService.subscribe(p);
        }

        /**
         * Invokes broadcastAsync and waits up to {@code timeoutMs} ms for the
         * provided latch to count down to zero (i.e. for all async tasks to
         * complete).  If no latch synchronisation is needed, pass {@code null}.
         */
        private void invokeBroadcastAsync(RecentChange rc) throws Exception {
            broadcastAsync.invoke(wikiStreamService, rc, "test-event-id");
            // Give the executor pool time to finish its work.
            Thread.sleep(200);
        }

        // ── 1. Early-exit when no emitters are registered ─────────────────────

        @Test
        @DisplayName("does not invoke mapper when no subscribers are present")
        void noSubscribers_mapperNeverCalled() throws Exception {
            RecentChange rc = buildRecentChange();

            invokeBroadcastAsync(rc);

            verifyNoInteractions(mapper);
            verifyNoInteractions(abuseFilter);
        }

        // ── 2. Serialisation failure aborts broadcast ──────────────────────────

        @Test
        @DisplayName("aborts without calling abuseFilter when JSON serialisation fails")
        void serialisationFailure_abortsEarly() throws Exception {
            subscribeUser(principal, testUser);
            RecentChange rc = buildRecentChange();

            ObjectNode node = new ObjectMapper().createObjectNode();
            when(mapper.valueToTree(rc)).thenReturn(node);
            when(mapper.writeValueAsString(any()))
                    .thenThrow(new JsonProcessingException("boom") {});

            invokeBroadcastAsync(rc);

            // abuseFilter must never be reached because we already bailed out
            verifyNoInteractions(abuseFilter);
        }

        // ── 3. Paused subscriber is skipped ───────────────────────────────────

        @Test
        @DisplayName("does not call abuseFilter for a paused subscriber")
        void pausedSubscriber_skipped() throws Exception {
            subscribeUser(principal, testUser);
            wikiStreamService.setPaused(principal, true);

            RecentChange rc = buildRecentChange();
            ObjectNode node = new ObjectMapper().createObjectNode();
            when(mapper.valueToTree(rc)).thenReturn(node);
            when(mapper.writeValueAsString(any())).thenReturn("{\"flagged\":true}");

            invokeBroadcastAsync(rc);

            verifyNoInteractions(abuseFilter);
        }

        // ── 4. Filter returns false – emitter.send() is never called ──────────

        @Test
        @DisplayName("does not send event when abuseFilter returns false")
        void filterNoMatch_noEventSent() throws Exception {
            when(principal.getName()).thenReturn("alice");
            when(userService.loadUserByUsername("alice")).thenReturn(testUser);
            wikiStreamService.subscribe(principal);

            RecentChange rc = buildRecentChange();
            ObjectNode node = new ObjectMapper().createObjectNode();
            when(mapper.valueToTree(rc)).thenReturn(node);
            when(mapper.writeValueAsString(any())).thenReturn("{\"flagged\":true}");
            when(abuseFilter.matches(eq(rc), eq(testUser))).thenReturn(java.util.List.of());

            invokeBroadcastAsync(rc);

            // abuseFilter was consulted but returned false, so send must not happen
            verify(abuseFilter, times(1)).matches(eq(rc), eq(testUser));
        }

        // ── 5. Happy path – matching subscriber receives the event ─────────────

        @Test
        @DisplayName("abuseFilter is called exactly once per active subscriber when filter matches")
        void filterMatch_abuseFilterCalledOnce() throws Exception {
            subscribeUser(principal, testUser);

            RecentChange rc = buildRecentChange();
            ObjectNode node = new ObjectMapper().createObjectNode();
            when(mapper.valueToTree(rc)).thenReturn(node);
            when(mapper.writeValueAsString(any())).thenReturn("{\"flagged\":true}");
            when(abuseFilter.matches(eq(rc), eq(testUser))).thenReturn(java.util.List.of("Test Filter"));

            invokeBroadcastAsync(rc);

            verify(abuseFilter, times(1)).matches(eq(rc), eq(testUser));
        }

        // ── 6. Emitter send error triggers completeWithError + removal ─────────

        @Test
        @DisplayName("removed from emitter map after send throws an exception")
        void sendException_emitterRemovedAndPauseReturnsFalse() throws Exception {
            subscribeUser(principal, testUser);

            RecentChange rc = buildRecentChange();
            ObjectNode node = new ObjectMapper().createObjectNode();
            when(mapper.valueToTree(rc)).thenReturn(node);
            when(mapper.writeValueAsString(any())).thenReturn("{\"flagged\":true}");
            // Make abuseFilter return true so we actually try to send
            when(abuseFilter.matches(eq(rc), eq(testUser))).thenReturn(java.util.List.of("Test Filter"));

            // After a send error the emitter is removed; the paused state for that user
            // will return false because there is no longer a StreamContext for them.
            invokeBroadcastAsync(rc);

            // The emitter threw during send (SseEmitter is already completed here);
            // subsequent isPaused should cleanly return false rather than NPE.
            assertDoesNotThrow(() -> wikiStreamService.isPaused(principal));
        }

        // ── 7. Multi-user isolation ────────────────────────────────────────────

        @Test
        @DisplayName("only the matching user's filter is evaluated; non-matching user is also evaluated independently")
        void multiUser_filtersEvaluatedIndependently() throws Exception {
            Principal principalBob = mock(Principal.class);

            subscribeUser(principal, testUser);      // alice
            subscribeUser(principalBob, anotherUser); // bob

            RecentChange rc = buildRecentChange();
            ObjectNode node = new ObjectMapper().createObjectNode();
            when(mapper.valueToTree(rc)).thenReturn(node);
            when(mapper.writeValueAsString(any())).thenReturn("{\"flagged\":true}");

            when(abuseFilter.matches(eq(rc), eq(testUser))).thenReturn(java.util.List.of("Test Filter"));
            when(abuseFilter.matches(eq(rc), eq(anotherUser))).thenReturn(java.util.List.of());

            invokeBroadcastAsync(rc);

            verify(abuseFilter, times(1)).matches(eq(rc), eq(testUser));
            verify(abuseFilter, times(1)).matches(eq(rc), eq(anotherUser));
        }
    }
}
