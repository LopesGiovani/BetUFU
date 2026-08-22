# API Coverage — Keycloak 26.7.1 Event Listener e entrega interna D-40

> Cobertura total por padrão. Cada exclusão é uma decisão explícita e motivada para a D-40.

| capability | decision | reason |
|---|---|---|
| EventListenerProvider user callback UPDATE_CREDENTIAL password | INTEGRATE | |
| Deprecated user event UPDATE_PASSWORD | OPT-OUT | Deprecated in Keycloak 26.7.1 and duplicates the canonical UPDATE_CREDENTIAL emission. |
| Other Keycloak user events | OPT-OUT | Outside D-40; they do not represent a password reset. |
| AdminEvent USER ACTION users/{id}/reset-password | INTEGRATE | |
| Other Keycloak admin events | OPT-OUT | Outside D-40 and must not trigger BETUFU session revocation. |
| Keycloak Admin REST API | OPT-OUT | D-40 is event-driven and grants no administrative API capability. |
| EventListenerProviderFactory and ServiceLoader registration | INTEGRATE | |
| Provider lifecycle and realm listener enablement | INTEGRATE | |
| Keycloak transaction enlistment and origin outbox | INTEGRATE | |
| Internal Keycloak outbox delivery worker | INTEGRATE | |
| Signed internal HTTPS ingestion endpoint | INTEGRATE | |
| BETUFU durable inbox and idempotent consumption | INTEGRATE | |
| HMAC key rotation and replay protection | INTEGRATE | |
| Keycloak global logout or back-channel logout | OPT-OUT | A-3 disproved reset-triggered logout and D-40 must not terminate third-party sessions. |
| External message broker | OPT-OUT | The ratified architecture uses the two databases and direct authenticated HTTPS delivery. |

## Boundary notes

- The user-event callback accepts only `EventType.UPDATE_CREDENTIAL` when the credential type is exactly `password`; `UPDATE_PASSWORD` is intentionally ignored even when Keycloak emits it for the same operation.
- The administrative callback accepts only `ResourceType.USER`, `OperationType.ACTION`, and the exact resource path `users/{targetUserId}/reset-password`. Target extraction comes from the exact path, never from `AdminEvent.getResourceId()`.
- The receiver terminates only BETUFU sessions by calling `IdentitySessionTerminationService.terminateAllSessionsFor(subject, SessionTerminationReason.SECURITY)`. It does not invoke Keycloak global logout and does not touch sessions belonging to other OIDC clients.
