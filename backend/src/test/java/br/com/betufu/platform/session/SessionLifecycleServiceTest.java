package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

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

  @Test
  void redirectsOperationalUsersToTheConsoleWhenNoSavedRequestExists() throws Exception {
    MockHttpServletRequest request = callbackRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    newService()
        .onAuthenticationSuccess(request, response, authentication("ROLE_ADMINISTRADOR_GERAL"));

    assertThat(response.getRedirectedUrl()).isEqualTo("/operacao/esportes");
  }

  @Test
  void redirectsModeratorsAndCompetitionAdministratorsToTheConsole() throws Exception {
    for (String role : List.of("ROLE_ADMINISTRADOR_COMPETICAO", "ROLE_MODERADOR")) {
      MockHttpServletRequest request = callbackRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();

      newService().onAuthenticationSuccess(request, response, authentication(role));

      assertThat(response.getRedirectedUrl()).isEqualTo("/operacao/esportes");
    }
  }

  @Test
  void keepsUsersWithoutOperationalRoleInThePublicArena() throws Exception {
    MockHttpServletRequest request = callbackRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    newService().onAuthenticationSuccess(request, response, authentication("ROLE_USER"));

    assertThat(response.getRedirectedUrl()).isEqualTo("/");
  }

  @Test
  void preservesAnInternalSavedRequestBeforeApplyingTheRoleFallback() throws Exception {
    MockHttpServletRequest request = callbackRequest();
    MockHttpServletRequest savedRequest =
        new MockHttpServletRequest("GET", "/operacao/odds/mercados");
    savedRequest.setSession(request.getSession());
    new HttpSessionRequestCache().saveRequest(savedRequest, new MockHttpServletResponse());
    MockHttpServletResponse response = new MockHttpServletResponse();

    newService()
        .onAuthenticationSuccess(request, response, authentication("ROLE_ADMINISTRADOR_GERAL"));

    assertThat(response.getRedirectedUrl()).startsWith("http://localhost/operacao/odds/mercados");
  }

  @Test
  void consumesContextualTargetBeforeSavedRequestAndRoleFallback() throws Exception {
    MockHttpServletRequest request = callbackRequest();
    request
        .getSession()
        .setAttribute(
            SessionLifecycleService.CONTEXTUAL_LOGIN_TARGET_ATTRIBUTE,
            "/odds/mercados/11111111-1111-4111-8111-111111111111?selection=AWAY#bilhete");
    MockHttpServletRequest savedRequest =
        new MockHttpServletRequest("GET", "/operacao/odds/mercados");
    savedRequest.setSession(request.getSession());
    new HttpSessionRequestCache().saveRequest(savedRequest, new MockHttpServletResponse());
    MockHttpServletResponse response = new MockHttpServletResponse();

    newService()
        .onAuthenticationSuccess(request, response, authentication("ROLE_ADMINISTRADOR_GERAL"));

    assertThat(response.getRedirectedUrl())
        .isEqualTo("/odds/mercados/11111111-1111-4111-8111-111111111111?selection=AWAY#bilhete");
    assertThat(
            request
                .getSession()
                .getAttribute(SessionLifecycleService.CONTEXTUAL_LOGIN_TARGET_ATTRIBUTE))
        .isNull();

    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
    newService()
        .onAuthenticationSuccess(
            request, secondResponse, authentication("ROLE_ADMINISTRADOR_GERAL"));
    assertThat(secondResponse.getRedirectedUrl()).isEqualTo("/operacao/esportes");
  }

  @Test
  void contextualTargetIsUsedOnlyOnce() throws Exception {
    MockHttpServletRequest request = callbackRequest();
    request
        .getSession()
        .setAttribute(
            SessionLifecycleService.CONTEXTUAL_LOGIN_TARGET_ATTRIBUTE,
            "/odds/mercados/11111111-1111-4111-8111-111111111111");
    SessionLifecycleService service = newService();
    MockHttpServletResponse firstResponse = new MockHttpServletResponse();

    service.onAuthenticationSuccess(request, firstResponse, authentication("ROLE_USER"));

    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
    service.onAuthenticationSuccess(request, secondResponse, authentication("ROLE_USER"));
    assertThat(firstResponse.getRedirectedUrl())
        .isEqualTo("/odds/mercados/11111111-1111-4111-8111-111111111111");
    assertThat(secondResponse.getRedirectedUrl()).isEqualTo("/");
  }

  @Test
  void invalidContextualTargetFailsClosedAndInvalidatesSession() {
    MockHttpServletRequest request = callbackRequest();
    MockHttpSession session = (MockHttpSession) request.getSession();
    session.setAttribute(
        SessionLifecycleService.CONTEXTUAL_LOGIN_TARGET_ATTRIBUTE, "https://evil.invalid/steal");

    assertThatThrownBy(
            () ->
                newService()
                    .onAuthenticationSuccess(
                        request, new MockHttpServletResponse(), authentication("ROLE_USER")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsafe contextual login target");
    assertThat(session.isInvalid()).isTrue();
  }

  private static MockHttpServletRequest callbackRequest() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/login/oauth2/code/keycloak");
    request.getSession(true).setAttribute("session-marker", "present");
    return request;
  }

  private static Authentication authentication(String role) {
    Authentication authentication = mock(Authentication.class);
    OidcUser principal = mock(OidcUser.class);
    when(authentication.getPrincipal()).thenReturn(principal);
    when(principal.getClaim("acr")).thenReturn(null);
    when(principal.getClaim("amr")).thenReturn(null);
    Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
    doReturn(authorities).when(authentication).getAuthorities();
    return authentication;
  }

  private static SessionLifecycleService newService() {
    Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    return new SessionLifecycleService(new SessionPolicy(clock), clock);
  }
}
