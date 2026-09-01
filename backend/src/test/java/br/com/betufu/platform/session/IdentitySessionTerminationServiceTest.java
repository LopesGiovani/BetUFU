package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

class IdentitySessionTerminationServiceTest {

  private static final String PRINCIPAL_NAME = "target-user-sub-123";
  private static final String REGISTRATION_ID = "keycloak";

  private FindByIndexNameSessionRepository<Session> sessionRepository;
  private ObjectProvider<FindByIndexNameSessionRepository<? extends Session>>
      sessionRepositoryProvider;
  private SessionInvalidationListener invalidationListener;
  private OidcSessionRevocationService revocationService;
  private IdentitySessionTerminationService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    sessionRepository = mock(FindByIndexNameSessionRepository.class);
    sessionRepositoryProvider = mock(ObjectProvider.class);
    doReturn(sessionRepository).when(sessionRepositoryProvider).getIfAvailable();
    invalidationListener = mock(SessionInvalidationListener.class);
    revocationService = mock(OidcSessionRevocationService.class);
    service =
        new IdentitySessionTerminationService(
            sessionRepositoryProvider, invalidationListener, revocationService);
  }

  @Test
  @DisplayName(
      "Deletes session first before notifying listener and returns clean summary on REVOKED outcome")
  void deletesSessionBeforeNotifyingListenerAndCapturesRevokedOutcome() {
    MapSession session = new MapSession("session-abc");
    when(sessionRepository.findByPrincipalName(PRINCIPAL_NAME))
        .thenReturn(Map.of("session-abc", session));
    when(invalidationListener.onSessionInvalidated(any()))
        .thenReturn(OidcSessionRevocationService.Outcome.REVOKED);

    IdentitySessionTerminationService.TerminationSummary summary =
        service.terminateAllSessionsFor(PRINCIPAL_NAME, SessionTerminationReason.ADMINISTRATIVE);

    InOrder inOrder = inOrder(sessionRepository, invalidationListener);
    inOrder.verify(sessionRepository).deleteById("session-abc");
    inOrder
        .verify(invalidationListener)
        .onSessionInvalidated(
            new SessionInvalidationListener.SessionInvalidation(
                "session-abc",
                REGISTRATION_ID,
                PRINCIPAL_NAME,
                SessionTerminationReason.ADMINISTRATIVE));

    assertThat(summary.totalTerminated()).isEqualTo(1);
    assertThat(summary.unrevokedOutcomes()).isEmpty();
    assertThat(summary.isFullyRevoked()).isTrue();
    assertThat(summary.oidcGrantRevocationConfirmed()).isTrue();
  }

  @Test
  @DisplayName("Deletes two local sessions but revokes their single identity grant only once")
  void multipleSessionsShareOneIdentityGrantRevocation() {
    MapSession s1 = new MapSession("session-1");
    MapSession s2 = new MapSession("session-2");
    Map<String, Session> sessions = new LinkedHashMap<>();
    sessions.put("session-1", s1);
    sessions.put("session-2", s2);

    when(sessionRepository.findByPrincipalName(PRINCIPAL_NAME)).thenReturn(sessions);
    when(invalidationListener.onSessionInvalidated(any()))
        .thenReturn(OidcSessionRevocationService.Outcome.REVOKED);

    IdentitySessionTerminationService.TerminationSummary summary =
        service.terminateAllSessionsFor(PRINCIPAL_NAME, SessionTerminationReason.SECURITY);

    InOrder inOrder = inOrder(sessionRepository, invalidationListener);
    inOrder.verify(sessionRepository).deleteById("session-1");
    inOrder.verify(sessionRepository).deleteById("session-2");
    inOrder
        .verify(invalidationListener)
        .onSessionInvalidated(
            new SessionInvalidationListener.SessionInvalidation(
                "session-1", REGISTRATION_ID, PRINCIPAL_NAME, SessionTerminationReason.SECURITY));
    verify(invalidationListener).onSessionInvalidated(any());

    assertThat(summary.totalTerminated()).isEqualTo(2);
    assertThat(summary.unrevokedOutcomes()).isEmpty();
    assertThat(summary.revocationFailures()).isEmpty();
    assertThat(summary.isFullyRevoked()).isTrue();
    assertThat(summary.oidcGrantRevocationConfirmed()).isTrue();
  }

  @Test
  @DisplayName("Local listener RuntimeException is recorded after every local session deletion")
  void localListenerExceptionIsRecordedAfterAllLocalDeletions() {
    MapSession s1 = new MapSession("session-1");
    MapSession s2 = new MapSession("session-2");
    Map<String, Session> sessions = new LinkedHashMap<>();
    sessions.put("session-1", s1);
    sessions.put("session-2", s2);

    when(sessionRepository.findByPrincipalName(PRINCIPAL_NAME)).thenReturn(sessions);
    when(invalidationListener.onSessionInvalidated(
            new SessionInvalidationListener.SessionInvalidation(
                "session-1",
                REGISTRATION_ID,
                PRINCIPAL_NAME,
                SessionTerminationReason.ADMINISTRATIVE)))
        .thenThrow(new RuntimeException("Local OAuth2AuthorizedClientService failure"));
    IdentitySessionTerminationService.TerminationSummary summary =
        service.terminateAllSessionsFor(PRINCIPAL_NAME, SessionTerminationReason.ADMINISTRATIVE);

    InOrder inOrder = inOrder(sessionRepository, invalidationListener);
    inOrder.verify(sessionRepository).deleteById("session-1");
    inOrder.verify(sessionRepository).deleteById("session-2");
    inOrder
        .verify(invalidationListener)
        .onSessionInvalidated(
            new SessionInvalidationListener.SessionInvalidation(
                "session-1",
                REGISTRATION_ID,
                PRINCIPAL_NAME,
                SessionTerminationReason.ADMINISTRATIVE));
    verify(invalidationListener).onSessionInvalidated(any());

    assertThat(summary.totalTerminated()).isEqualTo(2);
    assertThat(summary.unrevokedOutcomes()).isEmpty();
    assertThat(summary.hasFailures()).isTrue();
    assertThat(summary.revocationFailures()).hasSize(1);
    assertThat(summary.revocationFailures().get(0).classification())
        .isEqualTo(IdentitySessionTerminationService.TechnicalFailure.LOCAL_LISTENER_FAILURE);
    assertThat(summary.isFullyRevoked()).isFalse();
  }

  @Test
  @DisplayName("Identity without active sessions is a valid, harmless no-op returning 0 terminated")
  void identityWithoutActiveSessionsIsNoOp() {
    when(sessionRepository.findByPrincipalName(PRINCIPAL_NAME)).thenReturn(Map.of());
    when(revocationService.retryPendingGrant(
            REGISTRATION_ID, PRINCIPAL_NAME, SessionTerminationReason.ADMINISTRATIVE))
        .thenReturn(Optional.empty());

    IdentitySessionTerminationService.TerminationSummary summary =
        service.terminateAllSessionsFor(PRINCIPAL_NAME, SessionTerminationReason.ADMINISTRATIVE);

    verify(sessionRepository, never()).deleteById(any());
    verify(invalidationListener, never()).onSessionInvalidated(any());
    verify(revocationService)
        .retryPendingGrant(
            REGISTRATION_ID, PRINCIPAL_NAME, SessionTerminationReason.ADMINISTRATIVE);

    assertThat(summary.totalTerminated()).isZero();
    assertThat(summary.unrevokedOutcomes()).isEmpty();
    assertThat(summary.isFullyRevoked()).isTrue();
    assertThat(summary.oidcGrantRevocationConfirmed()).isFalse();
  }

  @Test
  @DisplayName("A later pass with no local sessions retries the retained grant revocation")
  void noLocalSessionsRetriesPendingGrantRevocation() {
    when(sessionRepository.findByPrincipalName(PRINCIPAL_NAME)).thenReturn(Map.of());
    when(revocationService.retryPendingGrant(
            REGISTRATION_ID, PRINCIPAL_NAME, SessionTerminationReason.SECURITY))
        .thenReturn(Optional.of(OidcSessionRevocationService.Outcome.REVOKED));

    IdentitySessionTerminationService.TerminationSummary summary =
        service.terminateAllSessionsFor(PRINCIPAL_NAME, SessionTerminationReason.SECURITY);

    verify(sessionRepository, never()).deleteById(any());
    verify(invalidationListener, never()).onSessionInvalidated(any());
    verify(revocationService)
        .retryPendingGrant(REGISTRATION_ID, PRINCIPAL_NAME, SessionTerminationReason.SECURITY);
    assertThat(summary.totalTerminated()).isZero();
    assertThat(summary.unrevokedOutcomes()).isEmpty();
    assertThat(summary.isFullyRevoked()).isTrue();
    assertThat(summary.oidcGrantRevocationConfirmed()).isTrue();
  }

  @Test
  @DisplayName("A pending grant that still fails remotely remains explicitly non-terminal")
  void noLocalSessionsKeepsRemoteFailureNonTerminal() {
    when(sessionRepository.findByPrincipalName(PRINCIPAL_NAME)).thenReturn(Map.of());
    when(revocationService.retryPendingGrant(
            REGISTRATION_ID, PRINCIPAL_NAME, SessionTerminationReason.SECURITY))
        .thenReturn(Optional.of(OidcSessionRevocationService.Outcome.REMOTE_FAILURE));

    IdentitySessionTerminationService.TerminationSummary summary =
        service.terminateAllSessionsFor(PRINCIPAL_NAME, SessionTerminationReason.SECURITY);

    assertThat(summary.unrevokedOutcomes())
        .containsExactly(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
    assertThat(summary.isFullyRevoked()).isFalse();
    assertThat(summary.oidcGrantRevocationConfirmed()).isFalse();
  }

  @Test
  @DisplayName("Every local delete may fail, but the shared provider grant is still revoked once")
  void allLocalDeletionFailuresStillRevokeSharedGrantOnce() {
    MapSession first = new MapSession("session-1");
    MapSession second = new MapSession("session-2");
    Map<String, Session> sessions = new LinkedHashMap<>();
    sessions.put("session-1", first);
    sessions.put("session-2", second);
    when(sessionRepository.findByPrincipalName(PRINCIPAL_NAME)).thenReturn(sessions);
    org.mockito.Mockito.doThrow(new RuntimeException("Redis unavailable"))
        .when(sessionRepository)
        .deleteById(any());
    when(invalidationListener.onSessionInvalidated(any()))
        .thenReturn(OidcSessionRevocationService.Outcome.REVOKED);

    IdentitySessionTerminationService.TerminationSummary summary =
        service.terminateAllSessionsFor(PRINCIPAL_NAME, SessionTerminationReason.SECURITY);

    InOrder inOrder = inOrder(sessionRepository, invalidationListener);
    inOrder.verify(sessionRepository).deleteById("session-1");
    inOrder.verify(sessionRepository).deleteById("session-2");
    inOrder
        .verify(invalidationListener)
        .onSessionInvalidated(
            new SessionInvalidationListener.SessionInvalidation(
                "session-1", REGISTRATION_ID, PRINCIPAL_NAME, SessionTerminationReason.SECURITY));
    verify(invalidationListener).onSessionInvalidated(any());
    assertThat(summary.totalTerminated()).isZero();
    assertThat(summary.unrevokedOutcomes()).isEmpty();
    assertThat(summary.revocationFailures()).hasSize(2);
    assertThat(summary.isFullyRevoked()).isFalse();
  }

  @Test
  @DisplayName("Remote failure never revives session and never throws exception to caller")
  void remoteFailureNeverRevivesSessionAndNeverThrows() {
    MapSession session = new MapSession("session-remote-fail");
    when(sessionRepository.findByPrincipalName(PRINCIPAL_NAME))
        .thenReturn(Map.of("session-remote-fail", session));
    when(invalidationListener.onSessionInvalidated(any()))
        .thenReturn(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);

    IdentitySessionTerminationService.TerminationSummary summary =
        service.terminateAllSessionsFor(PRINCIPAL_NAME, SessionTerminationReason.ADMINISTRATIVE);

    verify(sessionRepository).deleteById("session-remote-fail");
    assertThat(summary.totalTerminated()).isEqualTo(1);
    assertThat(summary.unrevokedOutcomes())
        .containsExactly(OidcSessionRevocationService.Outcome.REMOTE_FAILURE);
    assertThat(summary.isFullyRevoked()).isFalse();
    assertThat(summary.oidcGrantRevocationConfirmed()).isFalse();
  }

  @Test
  @DisplayName(
      "Deletion failure on first session does not prevent second deletion; listener is not notified for failed session; summary reports deletion failure and totalTerminated=1")
  void deletionFailureDoesNotPreventOtherDeletionsAndSkipsListenerForFailedId() {
    MapSession s1 = new MapSession("session-1");
    MapSession s2 = new MapSession("session-2");
    Map<String, Session> sessions = new LinkedHashMap<>();
    sessions.put("session-1", s1);
    sessions.put("session-2", s2);

    when(sessionRepository.findByPrincipalName(PRINCIPAL_NAME)).thenReturn(sessions);
    org.mockito.Mockito.doThrow(new RuntimeException("Simulated Redis deletion failure"))
        .when(sessionRepository)
        .deleteById("session-1");
    when(invalidationListener.onSessionInvalidated(
            new SessionInvalidationListener.SessionInvalidation(
                "session-2",
                REGISTRATION_ID,
                PRINCIPAL_NAME,
                SessionTerminationReason.ADMINISTRATIVE)))
        .thenReturn(OidcSessionRevocationService.Outcome.REVOKED);

    IdentitySessionTerminationService.TerminationSummary summary =
        service.terminateAllSessionsFor(PRINCIPAL_NAME, SessionTerminationReason.ADMINISTRATIVE);

    InOrder inOrder = inOrder(sessionRepository, invalidationListener);
    inOrder.verify(sessionRepository).deleteById("session-1");
    inOrder.verify(sessionRepository).deleteById("session-2");
    inOrder
        .verify(invalidationListener)
        .onSessionInvalidated(
            new SessionInvalidationListener.SessionInvalidation(
                "session-2",
                REGISTRATION_ID,
                PRINCIPAL_NAME,
                SessionTerminationReason.ADMINISTRATIVE));

    verify(invalidationListener, never())
        .onSessionInvalidated(
            new SessionInvalidationListener.SessionInvalidation(
                "session-1",
                REGISTRATION_ID,
                PRINCIPAL_NAME,
                SessionTerminationReason.ADMINISTRATIVE));

    assertThat(summary.totalTerminated()).isEqualTo(1);
    assertThat(summary.unrevokedOutcomes()).isEmpty();
    assertThat(summary.hasFailures()).isTrue();
    assertThat(summary.revocationFailures()).hasSize(1);
    assertThat(summary.revocationFailures().get(0).classification())
        .isEqualTo(IdentitySessionTerminationService.TechnicalFailure.LOCAL_DELETION_FAILURE);
    assertThat(summary.isFullyRevoked()).isFalse();
  }

  @Test
  @DisplayName(
      "Fails closed with IllegalStateException when indexed session repository is not available")
  void failsClosedWhenIndexedSessionRepositoryIsUnavailable() {
    when(sessionRepositoryProvider.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(
            () ->
                service.terminateAllSessionsFor(
                    PRINCIPAL_NAME, SessionTerminationReason.ADMINISTRATIVE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Indexed session repository is not available");

    verify(invalidationListener, never()).onSessionInvalidated(any());
  }

  @Test
  @DisplayName("Validates non-null and non-blank arguments")
  void validatesArguments() {
    assertThatThrownBy(
            () ->
                new IdentitySessionTerminationService(
                    null, invalidationListener, revocationService))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new IdentitySessionTerminationService(
                    sessionRepositoryProvider, null, revocationService))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new IdentitySessionTerminationService(
                    sessionRepositoryProvider, invalidationListener, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> service.terminateAllSessionsFor(null, SessionTerminationReason.ADMINISTRATIVE))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () -> service.terminateAllSessionsFor("  ", SessionTerminationReason.ADMINISTRATIVE))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.terminateAllSessionsFor(PRINCIPAL_NAME, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                service.terminateAllSessionsFor(
                    PRINCIPAL_NAME, " ", SessionTerminationReason.ADMINISTRATIVE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
