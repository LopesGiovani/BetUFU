package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Focal proof that {@code SessionLifecycleService.currentState} never creates a session.
 *
 * <p>Neither integration test can distinguish {@code getSession(false)} from {@code
 * getSession(true)} there: the anonymous path short-circuits in {@code SessionController} before
 * reaching this method, and on the authenticated path a session always exists, so both forms behave
 * identically. That was measured, not assumed — the first form of mutation M3 flipped the argument
 * and the whole suite stayed green.
 *
 * <p>The guarantee still matters, because the method is public: it holds for future callers, not
 * for the single current one. These tests make it falsifiable by driving the one input the
 * integration tests cannot produce — an authenticated-looking call with no session behind it.
 */
class SessionLifecycleServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-08-15T12:00:00Z");

  @Test
  void currentStateReturnsNullAndNeverCreatesASessionWhenNoneExists() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getSession(false)).thenReturn(null);

    assertThat(newService().currentState(request)).isNull();

    verify(request).getSession(false);
    verify(request, never()).getSession(true);
    verify(request, never()).getSession();
  }

  @Test
  void currentStateReturnsNullForASessionWithoutLifecycleStateWithoutCreatingAnother() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttributeNames()).thenReturn(Collections.enumeration(List.of()));

    assertThat(newService().currentState(request)).isNull();

    verify(request, never()).getSession(true);
    verify(request, never()).getSession();
  }

  @Test
  void currentStateReadsTheStoredStateWithoutCreatingOrRenewingASession() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpSession session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(session.getId()).thenReturn("session-under-test");
    when(session.getAttributeNames())
        .thenReturn(
            Collections.enumeration(
                List.of(
                    SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE,
                    SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE,
                    SelectiveTouchSessionRepository.AUTHENTICATION_AMR_COUNT_ATTRIBUTE)));
    when(session.getAttribute(SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE))
        .thenReturn(FIXED_NOW.getEpochSecond());
    when(session.getAttribute(SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE))
        .thenReturn(FIXED_NOW.getNano());
    when(session.getAttribute(SelectiveTouchSessionRepository.AUTHENTICATION_AMR_COUNT_ATTRIBUTE))
        .thenReturn(0);

    SessionPolicy.SessionState state = newService().currentState(request);

    assertThat(state).isNotNull();
    assertThat(state.sessionId()).isEqualTo("session-under-test");
    assertThat(state.lastValidActivityAt()).isEqualTo(FIXED_NOW);
    assertThat(state.terminationReason()).isNull();

    verify(request, never()).getSession(true);
    verify(request, never()).getSession();
    // A pure read must not write anything back onto the session.
    verify(session, never())
        .setAttribute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    verify(session, never()).invalidate();
  }

  private static SessionLifecycleService newService() {
    Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    return new SessionLifecycleService(new SessionPolicy(clock), clock);
  }
}
