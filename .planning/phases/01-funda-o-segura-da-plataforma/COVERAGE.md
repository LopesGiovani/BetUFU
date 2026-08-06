# API Coverage — Keycloak/OIDC

> Full coverage by default. Opt-outs são decisões explícitas e motivadas. Esta matriz cobre a superfície OIDC externa integrada nesta fase. O contrato interno BETUFU `/api/v1` é coberto por API-01/API-02 e não é tratado como serviço externo. O Keycloak Admin REST API também não é integrado: o bootstrap usa import/configuração local determinística.

| capability | decision | reason |
|---|---|---|
| `openid-configuration` | INTEGRATE | Descoberta do issuer e dos endpoints OIDC pelo BFF. |
| `authorization_endpoint` | INTEGRATE | Authorization Code hospedado para login, recuperação, verificação de e-mail e MFA nativos; o E2E Keycloak real também prova D-15 sem Direct Grant. |
| `token_endpoint` | INTEGRATE | Troca server-side do authorization code; tokens permanecem no BFF. |
| `jwks_uri` | INTEGRATE | Validação de assinatura e rotação de chaves publicada pelo issuer. |
| `userinfo_endpoint` | OPT-OUT | Claims `iss`, `sub`, `preferred_username`, `acr` e `amr` vêm do ID token/OidcUser validado; `UserInfoOptOutIT` prova zero chamadas com o endpoint indisponível. |
| `end_session_endpoint` | INTEGRATE | Logout OIDC do navegador corrente, coordenado com o encerramento da sessão BFF atual. |
| `revocation_endpoint` | INTEGRATE | Descoberto via OIDC, revoga o refresh token server-side com `token_type_hint=refresh_token`; falhas não reativam a sessão e o teste confirma `invalid_grant`. |
| `introspection_endpoint` | OPT-OUT | O BFF usa Authorization Code, discovery/JWKS e sessão server-side; não aceita bearer tokens externos que exijam introspecção. |
| `registration_endpoint` | OPT-OUT | O cliente confidencial é provisionado de forma determinística pelo realm import; dynamic client registration está fora do contrato. |
| `device_authorization_endpoint` | OPT-OUT | A superfície aprovada é navegador+BFF, não device flow. |
| `pushed_authorization_request_endpoint` | OPT-OUT | O escopo aprovado exige Authorization Code com `state`, `nonce` e PKCE; PAR não foi requerido para o piloto local. |
| `backchannel_authentication_endpoint` | OPT-OUT | CIBA não pertence aos fluxos de navegador e sessão BFF aprovados. |
| `direct_access_grants` | OPT-OUT | Password/direct grant faria a BETUFU receber credenciais e contraria IDEN-01/IDEN-03. |
| `implicit_flow` | OPT-OUT | Entregaria tokens ao navegador e contraria o BFF aprovado. |

## Cobertura dos fluxos nativos

Login, recuperação de acesso, verificação de e-mail e MFA administrativo são capacidades nativas alcançadas por `authorization_endpoint`; a BETUFU apenas aplica o tema mínimo aprovado e não replica formulários ou lógica de credenciais no Angular.

## Evidência executável

- `userinfo_endpoint` — **OPT-OUT**, 01-07: `UserInfoOptOutIT` mantém login, projeção allowlist e persistência `(iss, sub)` com endpoint 500/inalcançável e contador de chamadas igual a zero.
- `authorization_endpoint` — **INTEGRATE**, 01-21/01-16: `keycloak-login-validation.spec.ts` percorre Authorization Code/BFF real e prova campos obrigatórios, preservação apenas de identificador não sensível, erro geral sem enumeração e foco, sem formulário Angular.
- `revocation_endpoint` — **INTEGRATE**, 01-07: `OidcRevocationContractTest` verifica discovery, refresh token, hint, idempotência, falha/replay e redaction; `OidcRevocationKeycloakIT` repete a revogação e exige `invalid_grant` no refresh grant posterior. Não há garantia de rejeição imediata do access JWT já emitido.
- `end_session_endpoint` — **INTEGRATE**, 01-22: `CurrentDeviceLogoutIT` encerra somente a sessão atual, preserva a sessão paralela e mantém tokens fora do browser.

O gate 01-16 executa nominalmente essas três provas. Nenhuma decisão desta matriz é condicional a uma futura implementação da Fase 1.
