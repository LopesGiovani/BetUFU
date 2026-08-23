---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 28
subsystem: identity-outbox
tags: [keycloak, spi, outbox, password-reset, hmac, liquibase]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: outbox transacional e changelog do Plano 02-24
provides:
  - Worker interno com claim/lease transacional, timeouts, backoff com jitter e teto
  - Alerta minimizado na quinta falha, estado FAILED preservado e retenção de 30 dias
  - Recuperação realm-local autenticada pelo BearerTokenAuthenticator do Keycloak
  - Gate de ativação fail-closed por habilitação, HTTPS, host, path exato e chave válida
affects: [02-29, 02-30, 02-31, AUTH-04, IDEN-05, OPER-06, PRIV-01]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 1

tech-stack:
  added: [keycloak-services 26.7.1 provided]
  patterns:
    - Ativação governada por conjunção fail-closed, nunca por flag isolada
    - Autenticação delegada à API suportada do Keycloak, sem parsing próprio de JWT
    - Alerta e DTO operacional carregam correlação e estado, nunca dado do titular

key-files:
  created: []
  modified:
    - keycloak-password-event-listener/pom.xml
    - keycloak-password-event-listener/src/main/java/br/com/betufu/keycloak/password/BetufuPasswordEventListenerFactory.java
    - keycloak-password-event-listener/src/main/java/br/com/betufu/keycloak/password/PasswordResetOutboxDispatcher.java
    - keycloak-password-event-listener/src/main/java/br/com/betufu/keycloak/password/PasswordResetOutboxRecoveryResourceProvider.java
    - keycloak-password-event-listener/src/main/resources/META-INF/betufu-password-reset-outbox-changelog.xml
    - keycloak-password-event-listener/src/test/java/br/com/betufu/keycloak/password/PasswordResetDeliveryRecoveryTest.java

key-decisions:
  - "A integração nasce desabilitada; ativar exige conjunção de habilitação explícita, HTTPS com host e sem userInfo, path exato do contrato sem query nem fragment, key-id e chave Base64 com ao menos 32 bytes."
  - "Base64 inválido não derruba o boot do Keycloak: impede a ativação e nada mais."
  - "A autoridade da recuperação é o papel manage-realm do cliente realm-management; papel de realm homônimo não concede nada."
  - "O timestamp viaja como string porque o destino é autoritativo e recusa qualquer valor que não seja VALUE_STRING."
  - "O envelope usa os quatro headers do contrato: X-Key-Id, X-Timestamp, X-Signature e X-Source-Event-Id, sobre o path /api/v1/internal/events/password-reset."
  - "A chave HMAC é sempre Base64 e decodificada uma única vez na construção do dispatcher; não há fallback para bytes UTF-8 crus, que faria a mesma string configurada resolver para duas chaves diferentes."
  - "Coluna ausente entra por changeset novo; o changeset já aplicado nunca é editado."

requirements-completed: [AUTH-04, IDEN-05, OPER-06, PRIV-01]

coverage:
  - id: D1
    description: A factory nasce desabilitada e recusa ativação sem HTTPS, host, path exato, key-id ou chave de tamanho mínimo.
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: PasswordResetDeliveryRecoveryTest#factory_failClosed_* (a..h)
        status: pass
      - kind: other
        ref: mutações M1, M2, M3, M4, M7, M18, M20 e M21 observadas vermelhas
        status: pass
    human_judgment: false
  - id: D2
    description: A recuperação autentica exclusivamente pelo BearerTokenAuthenticator e autoriza somente pelo papel do cliente realm-management.
    requirement: OPER-06
    verification:
      - kind: unit
        ref: PasswordResetDeliveryRecoveryTest#recoveryProvider_* (401 por exceção, 401 por usuário nulo, homônimo recusado, papel de cliente aceito, bytecode)
        status: pass
      - kind: other
        ref: mutações M16, M17, M19c e M19d observadas vermelhas
        status: pass
    human_judgment: false
  - id: D3
    description: O changelog Liquibase real cria toda coluna mapeada pela entidade, inclusive next_attempt_at.
    requirement: IDEN-05
    verification:
      - kind: integration
        ref: PasswordResetDeliveryRecoveryTest#liquibaseChangelog_createsEveryColumnMappedByEntity_includingNextAttemptAt
        status: pass
      - kind: other
        ref: mutação M8 removendo o changeset observada vermelha
        status: pass
    human_judgment: false
  - id: D4
    description: Origem e destino assinam a mesma string e o payload respeita o contrato de tipos do destino.
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: canonicalização literal e payload com timestamp textual
        status: pass
      - kind: other
        ref: mutações M6 e M9 observadas vermelhas
        status: pass
    human_judgment: false
  - id: D5
    description: Alerta e resumo operacional não transportam sujeito, realm, tipo do evento nem mensagem crua de exceção.
    requirement: PRIV-01
    verification:
      - kind: unit
        ref: guard de propriedade sobre os valores reais da fixture
        status: pass
      - kind: other
        ref: mutações M10 e M11 observadas vermelhas
        status: pass
    human_judgment: false

---

# Plano 02-28 — Entrega e recuperação da outbox de redefinição de senha

## Verification Evidence

- Módulo completo na árvore deste commit: `56/56`.
- Árvore combinada da onda: backend Surefire `201/201`, Failsafe `241/241`, módulo `56/56`, Node `540/540`.
- Dezoito mutações discriminantes cobrem este plano: M1–M11 e M16–M21. Cada uma foi conferida quanto a **ter compilado** antes de o resultado ser lido, e cada restauração foi verificada por SHA-256 contra cópia pristina, com `touch` forçado.
- A mutação M5 foi confirmada por bytecode: 8 referências a `TokenVerifier` na classe compilada durante a mutação, 0 após a restauração.
- Hash final da factory: `989777A11FB18A44766F7882641649D3C19164826CDD59AA436CBC28D6B93712`.
- Hash final do provider: `D327F825614909DC53B94860B4965F4A8A9CC7A0B9153C987EB12CE69208EED6`.
- Hash final do teste: `1915C2FC6A516850BD6A35691CC052B9351CB232F7426A3C79B11AE412BAE1A9`.

## Registro de um erro de processo

A primeira versão deste plano declarou, na mensagem de commit, que a factory nascia desabilitada e que o fallback de Bearer havia sido removido. **Nenhuma das duas afirmações era verdadeira:** `enabled` continuava `true` em dois pontos e o bloco do `TokenVerifier` seguia no código. As frases vieram da instrução dada ao worker, não de medição no arquivo, e atravessaram 41 testes verdes porque nenhum deles cobria esses comportamentos. A revisão externa pegou.

As correções foram dobradas nesta mesma entrega, e a mensagem de commit foi reescrita antes de qualquer push, de modo que a afirmação falsa não permanece no histórico. A regra adotada a partir daqui: **nenhuma afirmação em commit ou SUMMARY sem verificação direta no arquivo.**

## Decisions Made

- O fallback de Bearer removido **não era um bypass**. Medido por execução: `TokenVerifier.create(t, AccessToken.class).withChecks(IS_ACTIVE).verify()`, sem `SignatureVerifierContext`, lança `VerificationException("Secret key not set")`. Ele lançava sempre; era código morto que se lia como caminho de autenticação. O P1 da revisão descrevia a estrutura corretamente e superestimava o impacto.
- `authenticatedUser()` é `protected` como seam de teste, para injetar usuário autenticado sem subir um Keycloak. Produção não tem outro caminho, e há prova de bytecode disso. O revisor aceitou explicitamente o seam.
- `keycloak-services` passou a ser declarado como `provided`. A classe já chegava por transitividade de `keycloak-model-jpa`, mas compilar contra dependência não declarada quebraria sem aviso numa troca de versão daquele artefato.

## Deviations from Plan

- **Duas emendas de allowlist, ratificadas antes da edição:** `META-INF/betufu-password-reset-outbox-changelog.xml`, que pertencia ao 02-24, e `keycloak-password-event-listener/pom.xml`.
- **Desvio de arranjo, deliberado e autorizado:** as correções mecânicas foram escritas pelo orquestrador, não pelo worker Gemini, com base no histórico medido desta onda. O worker escreveu as oito provas do Grupo A e errou exatamente um import.
- Três rodadas de revisão foram necessárias sobre o gate de ativação. Cada correção fechou uma borda e revelou a seguinte: primeiro ele aceitava qualquer coisa, depois qualquer HTTPS, depois qualquer HTTPS com o path certo. É o comportamento esperado de uma conjunção construída por partes, e a razão pela qual "corrigi o que foi apontado" não equivale a "o gate está correto".

## Issues Encountered

- **Quatro provas vazias foram encontradas e corrigidas.** `hasMessageContaining("Invalid Bearer token")` passava com e sem o fallback, porque o caminho do fallback lança a mesma substring mais um sufixo; um teste **assertava o vazamento**, exigindo o subject do titular no resumo operacional; o guard antivazamento do alerta checava palavras genéricas que a fixture nunca continha; e o teste do inbox exigia a exceção crua persistida.
- **Duas mutações não discriminaram e o achado foi tratado.** A medição mostrou que, no teste de 401, o resultado vinha do `catch` e o ramo `user == null` não tinha cobertura nenhuma. A prova específica desse ramo foi acrescentada, e só então cada caminho passou a ter mutação que o derruba.
- **Duas mutações falharam por causa mecânica, não por resultado:** uma quebrou a compilação e outra nem aplicou, por colapso de escape no heredoc. Conferir se a mutação compilou passou a ser parte do procedimento.
- A quota do Antigravity esgotou no meio da onda e explicou três falhas do worker que haviam sido atribuídas a erro de conteúdo.

## User Setup Required

Nenhuma. Realm, compose e imagem de produção permanecem desabilitados; a ativação pertence ao Plano 02-29.

## Next Phase Readiness

- **Evidência deferida, formalizada pelo revisor:** a integração com a API autenticadora suportada foi verificada estruturalmente. A validação real de assinatura, expiração, issuer e audience não foi comprovada nesta onda e permanece como gate bloqueante do 02-29.
- O 02-29 deve provar, com Keycloak real: assinatura forjada, token expirado, issuer incorreto, audience incorreta, papel global homônimo recusado e `realm-management/manage-realm` aceito.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-23*
