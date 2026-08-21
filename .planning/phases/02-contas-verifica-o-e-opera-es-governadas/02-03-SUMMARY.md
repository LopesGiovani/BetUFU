---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 03
subsystem: identity
tags: [profile, postgresql, problem-details, spring-security, spring-session]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Medicao A-7 do CHECK POSIX ASCII no PostgreSQL 18.4
provides:
  - Perfil BETUFU proprio com apelido editavel e persistencia governada
  - API GET e PUT em /api/v1/profile/me sem exposicao de dados privados
  - Codigos de erro estaveis e Problem Details sanitizado em MVC e Spring Security
  - Renovacao de sessao somente apos mutacao autenticada e bem-sucedida do perfil
affects: [02-04, 02-07, 02-09, 02-10, 02-14, PROF-01, PROF-02]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 1

tech-stack:
  added: []
  patterns:
    - DTO de entrada normaliza antes do Bean Validation e nunca interpola o valor rejeitado
    - DTO de saida usa allowlist explicita de campos
    - Spring Security escreve Problem Details diretamente para falhas anteriores ao MVC
    - Atividade valida exige metodo e caminho enumerados, autenticacao e resposta bem-sucedida

key-files:
  created:
    - backend/src/main/java/br/com/betufu/identity/internal/BetufuProfile.java
    - backend/src/main/java/br/com/betufu/identity/internal/BetufuProfileRepository.java
    - backend/src/main/java/br/com/betufu/identity/internal/BetufuProfileService.java
    - backend/src/main/java/br/com/betufu/identity/internal/CurrentProfileResponse.java
    - backend/src/main/java/br/com/betufu/identity/internal/ProfileController.java
    - backend/src/main/java/br/com/betufu/identity/internal/ProfileUpdateRequest.java
    - backend/src/main/java/br/com/betufu/platform/api/ApiProblemException.java
    - backend/src/main/java/br/com/betufu/platform/api/package-info.java
    - backend/src/main/resources/db/migration/V3__create_betufu_profile.sql
    - backend/src/test/java/br/com/betufu/identity/CurrentProfileResponseTest.java
    - backend/src/test/java/br/com/betufu/identity/ProfileControllerIT.java
    - backend/src/test/java/br/com/betufu/identity/ProfilePersistenceIT.java
  modified:
    - backend/src/main/java/br/com/betufu/platform/api/ApiErrorCode.java
    - backend/src/main/java/br/com/betufu/platform/api/ProblemDetailsHandler.java
    - backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java
    - backend/src/main/java/br/com/betufu/platform/session/ValidActivityFilter.java
    - backend/src/test/java/br/com/betufu/identity/LocalIdentityTracerIT.java
    - backend/src/test/java/br/com/betufu/identity/UserInfoOptOutIT.java
    - backend/src/test/java/br/com/betufu/platform/api/ProblemDetailsIT.java
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java
    - backend/src/test/java/br/com/betufu/platform/security/CsrfSecurityIT.java
    - backend/src/test/java/br/com/betufu/platform/session/RedisSessionIT.java

key-decisions:
  - "O apelido aceita ASCII fechado de 3 a 20 caracteres, preserva caixa e remove somente espacos externos."
  - "Criar o perfil nao inicia cooldown; a primeira troca real e imediata; trocas seguintes exigem trinta dias; repetir o mesmo valor e idempotente."
  - "Falhas 401 e 403 anteriores ao MVC usam o mesmo contrato publico e sanitizado de Problem Details."

patterns-established:
  - "Privacy by allowlist: resposta publica declara exatamente os campos permitidos."
  - "Validation boundary: normalizar no DTO antes das constraints e validar novamente no banco."
  - "Session activity: somente mutacao autenticada, enumerada e concluida com sucesso renova inatividade."

requirements-completed: [PROF-01]
requirements-partial: [PROF-02]

coverage:
  - id: D1
    description: Pessoa autenticada cria, consulta e altera o apelido sob as regras de idempotencia e cooldown.
    requirement: PROF-01
    verification:
      - kind: integration
        ref: "ProfileControllerIT"
        status: pass
      - kind: integration
        ref: "ProfilePersistenceIT"
        status: pass
    human_judgment: false
  - id: D2
    description: A metade negativa de PROF-02 impede e-mail, matricula, HMAC, papeis e MFA na resposta do perfil proprio.
    requirement: PROF-02
    verification:
      - kind: unit
        ref: "CurrentProfileResponseTest"
        status: pass
      - kind: integration
        ref: "ProfileControllerIT"
        status: pass
    human_judgment: false
  - id: D3
    description: PUT de perfil bem-sucedido renova a atividade; resposta 422 nao renova TTL nem instante persistido.
    requirement: PROF-01
    verification:
      - kind: integration
        ref: "RedisSessionIT#profilePutRenewsOnlyAfterSuccessfulAuthenticatedMutation"
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-21
status: complete
---

# Phase 2 Plan 03: Perfil próprio e erros estáveis

**O perfil BETUFU agora persiste e devolve somente dados públicos permitidos, aplica a regra aprovada de troca de apelido e integra erros e atividade de sessão ao contrato seguro da plataforma.**

## Performance

- **Duration:** não cronometrada; execução retomada em múltiplas sessões
- **Started:** não registrado
- **Completed:** 2026-08-21T01:45:53-03:00
- **Tasks:** 2
- **Files modified:** 22

## Accomplishments

- Criou a migration `V3`, entidade, repositório, serviço e API de perfil próprio com apelido ASCII validado também pelo PostgreSQL.
- Implementou criação sem cooldown, primeira troca imediata, intervalo de trinta dias nas trocas posteriores e repetição idempotente.
- Publicou códigos estáveis de erro e removeu dados sensíveis de respostas e logs de validação e integridade.
- Alinhou os handlers 401/403 do Spring Security ao Problem Details da aplicação.
- Provou com Redis real que somente o `PUT /api/v1/profile/me` autenticado e bem-sucedido renova a sessão.

## Task Commits

- `ea7aca0` — `feat(02-03): implementar perfil proprio e erros estaveis` — consolida as duas tasks em 22 arquivos de implementação e testes.

Este summary, `STATE.md` e `ROADMAP.md` formam o commit documental subsequente, seguindo o padrão histórico `feat` → `docs`. O hash do próprio commit não é registrado dentro dele, porque esse valor só existe depois que o conteúdo é gravado.

## Files Created/Modified

- `identity/internal/*Profile*` e `CurrentProfileResponse.java` — fatia completa de persistência e API do perfil próprio.
- `V3__create_betufu_profile.sql` — tabela, FK, unicidade por identidade e constraints de apelido.
- `ApiErrorCode`, `ApiProblemException` e `ProblemDetailsHandler` — recusas estáveis e sanitizadas.
- `SecurityConfiguration` — respostas 401/403 serializadas sem mensagem crua de exceção.
- `ValidActivityFilter` — allowlist fechada incluindo a mutação do perfil.
- ITs de perfil, migration, Problem Details, segurança e Redis — provas de banco, API, privacidade e sessão.

## Decisions Made

- O apelido não é único e preserva a caixa informada; somente espaços das pontas são removidos.
- A criação não consome cooldown. A primeira troca real é permitida imediatamente; cada troca real posterior exige trinta dias.
- Gravar o apelido vigente é inócuo e não altera carimbos.
- Respostas 401 e 403 precisam ser escritas no filtro de segurança, pois essas falhas acontecem antes do handler MVC.

## Deviations from Plan

### Auto-fixed Issues

**1. Atualização JPA precisava operar sobre a entidade gerenciada**
- **Found during:** Task 1
- **Fix:** repositório e serviço foram alinhados ao dirty checking transacional do Hibernate.
- **Verification:** testes de persistência, idempotência e cooldown passaram no PostgreSQL real.

**2. Spring Security ainda devolvia 401/403 fora do contrato publicado**
- **Found during:** revisão Codex
- **Fix:** `SecurityConfiguration` passou a serializar os códigos `UNAUTHORIZED` e `FORBIDDEN` pelo Jackson 3, sem reutilizar mensagens de exceção.
- **Verification:** `CsrfSecurityIT` e a suíte agregada passaram.

**3. Normalização ocorria tarde demais**
- **Found during:** revisão Codex final
- **Issue:** Bean Validation recusava espaços externos antes de o serviço executar `trim()`.
- **Fix:** normalização movida para o construtor canônico de `ProfileUpdateRequest`.
- **Verification:** teste observado vermelho com HTTP 400 e depois verde com persistência de `giovani_99`.

**4. A allowlist tinha somente teste estrutural**
- **Found during:** revisão Codex final
- **Fix:** `RedisSessionIT` passou a provar sucesso e recusa do path exato através de sessão Redis, OIDC, XSRF e `SecurityFilterChain` reais.
- **Verification:** resposta 422 preserva TTL/atividade; resposta 2xx renova ambos.

**Total deviations:** 4 correções necessárias para entregar o contrato já aprovado, sem dependência nova.

## Issues Encountered

- A primeira compilação do handler de segurança usou o namespace antigo do Jackson; o projeto usa Jackson 3 (`tools.jackson`). O import foi corrigido sem alterar dependências.
- Avisos de Logback, Mockito e reconexão Lettuce ocorreram no encerramento dos testes, sem falha ou recurso persistente.

## Evidências de validação

- TDD corretivo: `ProfileControllerIT` falhou primeiro com HTTP 400 para apelido com espaços externos.
- Maven focal final: 82 testes Surefire e 17 testes Failsafe, todos verdes.
- Maven agregado final: 82 testes Surefire e 143 testes Failsafe, total 225/225.
- Node agregado final: 518/518.
- Total das suítes agregadas: 743 verificações verdes.
- Três mutações discriminantes anteriores observaram vermelho e restauração: constraint SQL, campo extra no DTO e valor rejeitado exposto.
- Revisão Codex final do snapshot congelado: `CLEAN`, sem achado P0–P2.
- `git diff --check` e `git diff --cached --check`: sem erros; somente avisos esperados de LF/CRLF no Windows.

## Partial Requirement: PROF-02

`PROF-02` está **parcial por desenho**. A metade negativa está completa: a API própria não expõe e-mail, matrícula, HMAC, papéis, equipe favorita ou MFA. A metade positiva — perfil público de outra pessoa com equipe, posição, conquistas, ranking, grupos e chat — permanece distribuída entre as Fases 3, 6 e 7, onde existirão consumidores reais dessas informações.

## User Setup Required

None — nenhuma dependência ou configuração externa foi adicionada.

## Next Phase Readiness

- A Wave 1 está implementada, testada e revisada no working tree.
- Os Planos 02-04, 02-06 e 02-14 da Wave 2 estão desbloqueados tecnicamente, mas exigem apresentação e aprovação próprias.
- O commit de implementação `ea7aca0` e este commit documental existem somente na branch do worktree, prontos para integração local.
- Nenhum push ou integração na `master` foi realizado.

## Self-Check: PASSED

- Os 22 arquivos declarados existem no snapshot revisado.
- As suítes agregadas e os checks estáticos terminaram verdes.
- O snapshot de código permaneceu estável durante a formalização.
- O commit de implementação `ea7aca0` existe e contém exatamente os 22 arquivos declarados, sem artefatos `.planning`.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-21*
