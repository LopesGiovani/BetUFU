---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 29
subsystem: keycloak-password-reset
tags: [password-reset, keycloak, outbox, hmac, bearer, rollout, reconciliation]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: outbox endurecida do 02-28 e inbox/recovery durável do 02-31
provides:
  - Imagem Keycloak 26.7.1 candidata com o provider D-40 compilado em Java 21
  - Cadeia real Keycloak, outbox, dispatcher HTTPS/HMAC, inbox e encerramento de sessões
  - Validação criptográfica do Bearer contra Keycloak real com audience realm-management
  - Listener fail-closed e reconciliação não destrutiva do realm persistido
  - Configuração governada de rollout, TLS e segredos externos sem material sensível no Git
affects: [AUTH-04, IDEN-05, OPER-06, PRIV-01]

actuals:
  tokens: not-recorded
  tasks: 3
  commits: 2

tech-stack:
  added: []
  patterns:
    - Imagem multi-stage com base Keycloak pinada por digest e somente o JAR do provider no runtime
    - Listener inerte antes da criação do store quando a ativação fail-closed não passa
    - Reconciliação GET/PUT/GET com dry-run, confirmação, idempotência e tentativa de rollback
    - Revogação OIDC por geração de login e confirmação positiva antes de declarar DELIVERED

key-files:
  created:
    - infra/keycloak/Dockerfile
    - backend/src/test/java/br/com/betufu/platform/session/PasswordResetSessionTerminationIT.java
    - scripts/reconcile-keycloak-event-listeners.mjs
    - scripts/reconcile-keycloak-event-listeners.test.mjs
  modified:
    - infra/compose.yaml
    - infra/keycloak/realm-betufu.json
    - keycloak-password-event-listener/src/main/java/br/com/betufu/keycloak/password/BetufuPasswordEventListener.java
    - keycloak-password-event-listener/src/main/java/br/com/betufu/keycloak/password/PasswordResetOutboxRecoveryResourceProvider.java
    - backend/src/main/java/br/com/betufu/platform/session/IdentitySessionTerminationService.java
    - backend/src/main/java/br/com/betufu/platform/session/OidcSessionRevocationService.java
    - backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventEndpoint.java
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-KEYCLOAK-OPERACAO.md

key-decisions:
  - "A origem usa uma chave HMAC por execução; a aceitação current+previous pertence ao destino e é provada reinicializando a origem."
  - "HTTP 2xx só confirma estado terminal DELIVERED/PROCESSED; falha ou estado não terminal responde 503 e preserva o retry da outbox."
  - "O Bearer administrativo exige audience realm-management e o papel real do cliente realm-management, nunca papel de realm homônimo."
  - "O realm persistido é atualizado pela Admin API sem reset destrutivo e o tráfego permanece bloqueado até confirmação e repetição UNCHANGED."
  - "A frase literal de rollout autoriza as mudanças de configuração, mas segredos e a flag efetiva de ambiente permanecem externos ao Git."

requirements-completed: [AUTH-04, IDEN-05, OPER-06, PRIV-01]

coverage:
  - id: D1
    description: A cadeia real entrega o evento uma vez, encerra as sessões do alvo e preserva a sessão de controle.
    requirement: IDEN-05
    verification:
      - kind: integration
        ref: PasswordResetSessionTerminationIT 16/16
        status: pass
    human_judgment: false
  - id: D2
    description: Assinatura, expiração, issuer, audience e origem do papel são validados separadamente contra Keycloak real.
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: PasswordResetSessionTerminationIT e PasswordResetProviderTracerIT
        status: pass
    human_judgment: false
  - id: D3
    description: Listener desabilitado não inicializa o store nem cria outbox; configuração válida captura exatamente um evento.
    requirement: PRIV-01
    verification:
      - kind: integration
        ref: PasswordResetProviderTracerIT
        status: pass
    human_judgment: false
  - id: D4
    description: Realm persistido preserva campos e listeners, aplica a união requerida e permanece idempotente após restart.
    requirement: OPER-06
    verification:
      - kind: integration
        ref: PasswordResetSessionTerminationIT
        status: pass
      - kind: unit
        ref: reconcile-keycloak-event-listeners 19/19
        status: pass
    human_judgment: false
---

# Plano 02-29 — Ativação governada da cadeia D-40

## Verification Evidence

- Backend completo com `clean`: Surefire `228/228` em 20 classes e Failsafe `297/297` em 39 classes, sem falhas ou erros.
- Módulo do provider Keycloak: `59/59` em 2 classes; validadores Node: `615/615` em 8 suítes.
- O E2E focal `PasswordResetSessionTerminationIT` terminou `16/16` e atravessou Keycloak candidato, listener, outbox e dispatcher reais, HTTPS/HMAC, endpoint Spring, inbox e encerramento de sessões.
- Os gates `validate-runtime-prerequisites`, `validate-keycloak-realm`, `validate-password-reset-recovery` e `docker compose config --quiet` passaram.
- A imagem candidata final é `sha256:165f3d4f0c102bdfc7ab9adb1e726fe30734c5833107efd04571f1b3615e62c8`. A inspeção confirmou somente o JAR BETUFU no diretório de providers, sem segredo no ambiente ou nas camadas adicionadas.
- A revisão independente final concluiu `PASS`, com P0=P1=P2=0. Permaneceu um P3 não bloqueante sobre resposta ambígua ao primeiro `PUT` do reconciliador; nessa situação o comando falha e mantém o tráfego bloqueado.

## Decisions Made

- O flag `enabled` governa a captura antes da criação do `PasswordResetOutboxStore`. Com configuração ausente ou inválida, o listener retorna sem tocar no provider JPA e sem persistir evento.
- O destino só confirma a origem com 2xx quando o inbox chegou a estado terminal. `FAILED`, `PENDING`, `CLAIMED` ou exceção de efeito retornam 503 sanitizado para manter a entrega elegível a retry.
- A revogação remota é agrupada por `(registrationId, principalName)` e por geração opaca de login. Um login concorrente não é apagado sem ser observado, e ausência de estado volátil após restart não é tratada como prova de revogação.
- A recuperação administrativa usa `BearerTokenAuthenticator` com audience explícita `realm-management`. Os testes reais isolam assinatura forjada, expiração, issuer, audience, papel homônimo de realm e o papel verdadeiro do cliente.
- O reconciliador de realm nasce em dry-run, recebe credenciais apenas pelo ambiente e executa leitura completa, união estável dos listeners, escrita, releitura e confirmação. Divergência aciona tentativa de restauração do documento anterior.
- A comparação de listeners considera a lista como conjunto, porque o Keycloak pode reordená-la sem alterar sua semântica. Os demais campos precisam permanecer estruturalmente idênticos.
- A configuração versionada inclui a imagem candidata e o listener, mas `BETUFU_PASSWORD_RESET_ENABLED` continua `false` por padrão. A ativação efetiva em um ambiente exige decisão operacional e segredos externos.

## Deviations from Plan

- O Dockerfile e o E2E foram criados, não estendidos: ambos estavam ausentes apesar de o texto inicial tratá-los como arquivos existentes.
- A auditoria da cadeia de suprimento usou wrapper Maven pinado, árvore de dependências, digest da base, conteúdo da imagem e `docker history`; `slopcheck` foi removido porque não existe no ambiente nem fazia parte dos controles disponíveis do projeto.
- A allowlist foi ratificada para os 26 caminhos realmente necessários, incluindo o listener fail-closed, o reconciliador, a semântica de revogação e os dois runbooks.
- A frase literal `ativar rollout D-40 02-29` foi fornecida pelo responsável antes das mudanças de realm/compose. Isso autorizou a configuração do rollout; não inseriu valores reais de produção nem fez implantação externa.

## Issues Encountered

- A primeira execução do E2E não alcançou os cenários porque a fixture registrava `PasswordResetEventProperties` duas vezes. A duplicidade foi removida e o teste focal passou `16/16`.
- O primeiro desenho do listener ainda construía o store quando desabilitado. A correção moveu o fail-closed para antes da inicialização JPA e acrescentou provas de zero escrita para flag falsa e configuração inválida.
- O realm importado não atualiza instalações que já possuem estado no PostgreSQL. Foi criado o reconciliador da Admin API e o E2E agora altera um realm legado, exige `APPLIED`, repete até `UNCHANGED`, reinicia o candidato com o mesmo banco e confirma persistência.
- A imagem multi-stage fixa as coordenadas e descarta o estágio de build, mas o Maven do estágio construtor baixa dependências. Portanto, o build é auditável e pinado, porém não hermético/offline.
- O Keycloak sinaliza que os SPIs usados são internos e uma API de autenticação está depreciada. A integração permanece mais fortemente presa à versão 26.7.1 e precisa de revalidação antes de qualquer upgrade.
- O Failsafe terminou verde, mas precisou forçar o encerramento de uma JVM fork por threads de limpeza remanescentes. É dívida de higiene da suíte, não falha funcional dos testes.
- P3 da revisão: se o primeiro `PUT` chegar ao servidor e a resposta se perder, o cliente não consegue distinguir com certeza “não aplicado” de “aplicado sem confirmação”. O reconciliador retorna erro, não libera tráfego e exige diagnóstico/reexecução segura do operador.

## User Setup Required

- Não registrar no Git chaves HMAC, senhas administrativas, truststore ou senha de truststore. Esses valores devem vir do cofre/ambiente protegido descrito nos runbooks.
- Para uma implantação real, fornecer `BETUFU_PASSWORD_RESET_ENDPOINT`, `BETUFU_PASSWORD_RESET_HMAC_KEY`, `BETUFU_PASSWORD_RESET_KEY_ID`, as duas versões/chaves do destino, o truststore da JVM e as credenciais administrativas temporárias do reconciliador.
- Manter `BETUFU_PASSWORD_RESET_ENABLED=false` até o Keycloak estar saudável. Depois, executar o reconciliador, aceitar somente `APPLIED` ou `UNCHANGED`, repetir e exigir `UNCHANGED` antes de liberar tráfego.
- A frase de rollout já foi recebida e registrada, mas nenhum valor secreto de produção foi colocado no repositório.

## Next Phase Readiness

- Os dois gates transferidos da Wave 7 foram fechados: a cadeia D-40 real e a validação criptográfica do Bearer foram executadas contra Keycloak real, não por simulação ou inspeção estática.
- Realm, Compose, listener e runbooks estão preparados para operação governada e fail-closed. A configuração versionada não equivale a uma implantação externa: cada ambiente ainda precisa receber seus segredos e cumprir o gate pós-health.
- A próxima onda pode partir da cadeia D-40 integrada. Antes de atualizar o Keycloak, deve tratar como obrigatórias a revalidação dos SPIs internos/depreciados e a auditoria da nova imagem.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-24*
