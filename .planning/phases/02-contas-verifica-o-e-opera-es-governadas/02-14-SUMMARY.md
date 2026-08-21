---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 14
subsystem: api
tags: [openapi, angular, typescript, codegen, privacy]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Endpoints e DTOs Java de perfil próprio entregues pelo Plano 02-03
provides:
  - Contrato OpenAPI para consultar e atualizar o perfil próprio
  - Cliente Angular gerado para os endpoints de perfil
  - Manifesto de 23 arquivos gerados protegido por digest aprovado
affects: [02-05, PROF-01, PROF-02, PRIV-01]

actuals:
  tokens: 4828
  tasks: 3
  commits: 1

tech-stack:
  added: []
  patterns:
    - OpenAPI como fonte única dos tipos e serviços HTTP do frontend
    - Árvore gerada protegida por allowlist e digest SHA-256

key-files:
  created:
    - frontend/src/app/api/generated/api/profile.service.ts
    - frontend/src/app/api/generated/model/current-profile-response.ts
    - frontend/src/app/api/generated/model/profile-update-request.ts
  modified:
    - backend/openapi/betufu.yaml
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/api/generated/api/api.ts
    - frontend/src/app/api/generated/model/models.ts
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-14-PLAN.md

key-decisions:
  - "O contrato público do perfil contém somente identityId, nickname, nicknameUpdatedAt e matriculaStatus."
  - "O manifesto aprovado contém 23 arquivos e digest 0D73D00945E6E573441857114D43E08047C059BA91F380A06D08F4F7C080E220."
  - "A allowlist e o digest pertencem a frontend/scripts/generate-api.mjs; o validador apenas importa esse contrato."

patterns-established:
  - "Generated-only: arquivos sob frontend/src/app/api/generated são produzidos pelo gerador fixado e nunca editados à mão."
  - "Privacy by schema: additionalProperties false e allowlist explícita impedem que campos privados virem contrato público."

requirements-completed: [PROF-01, PROF-02, PRIV-01]

coverage:
  - id: D1
    description: O OpenAPI publica GET e PUT de /api/v1/profile/me em paridade com os records Java.
    requirement: PROF-01
    verification:
      - kind: other
        ref: "node scripts/validate-phase1.mjs --contract"
        status: pass
    human_judgment: false
  - id: D2
    description: O cliente Angular de perfil é reproduzível e protegido pelo manifesto de 23 arquivos e pelo digest aprovado.
    requirement: PROF-01
    verification:
      - kind: unit
        ref: "node --test frontend/scripts/generate-api.test.mjs"
        status: pass
      - kind: other
        ref: "node frontend/scripts/generate-api.mjs --check"
        status: pass
      - kind: other
        ref: "node scripts/validate-angular-route-boundary.mjs"
        status: pass
    human_judgment: false
  - id: D3
    description: O schema e os tipos gerados não expõem e-mail, número de matrícula, HMAC, papéis, equipe favorita ou MFA.
    requirement: PRIV-01
    verification:
      - kind: other
        ref: "inspeção de CurrentProfileResponse e ProfileUpdateRequest, confirmada pelos gates de contrato e geração"
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-21
status: complete
---

# Phase 2 Plan 14: Contrato e cliente de perfil

**O backend e o frontend agora compartilham um contrato OpenAPI reproduzível para o perfil próprio, com tipos Angular gerados e uma fronteira pública que não inclui dados privados.**

## Performance

- **Duration:** não cronometrada; execução atravessou a ausência do responsável
- **Started:** 2026-08-21, horário não registrado
- **Completed:** 2026-08-21T03:01:25-03:00
- **Tasks:** 3
- **Files modified:** 9, incluindo este summary e a correção do plano

## Accomplishments

- Publicou `GET` e `PUT /api/v1/profile/me` com request e response equivalentes aos records Java do Plano 02-03.
- Regenerou o cliente Angular sem edição manual, criando `ProfileService` e os dois modelos de perfil.
- Fixou a árvore de 23 arquivos no digest `0D73D00945E6E573441857114D43E08047C059BA91F380A06D08F4F7C080E220`.
- Provou que o contrato público contém somente identificador opaco, apelido, instante da troca e estado da matrícula — nunca o número da matrícula.

## Task Commits

- `9d2a470` — `feat(02-14): publicar contrato e cliente de perfil` — contém exatamente os sete arquivos de contrato, gerador e cliente TypeScript.

Este summary e a correção do plano formam o commit documental subsequente. O hash desse próprio commit não é registrado dentro dele, porque o valor só existe depois que o conteúdo é gravado.

## Files Created/Modified

- `backend/openapi/betufu.yaml` — paths, operações e schemas públicos do perfil.
- `frontend/scripts/generate-api.mjs` — allowlist de 23 arquivos e digest aprovado.
- `frontend/src/app/api/generated/api/profile.service.ts` — serviço Angular com `getMyProfile` e `putMyProfile`.
- `frontend/src/app/api/generated/model/current-profile-response.ts` — projeção pública tipada do perfil.
- `frontend/src/app/api/generated/model/profile-update-request.ts` — request tipado de apelido.
- `frontend/src/app/api/generated/api/api.ts` e `model/models.ts` — exportam o novo serviço e os modelos.
- `02-14-PLAN.md` — corrige o proprietário real da allowlist e do digest.

## Decisions Made

- `matriculaStatus` permanece público porque revela apenas o estado da verificação; o número da matrícula continua ausente.
- O inventário foi aceito sob a delegação explícita dada pelo responsável em 2026-08-21 para concluir tecnicamente todo o Plano 02-14 durante sua ausência. O responsável não é apresentado como tendo conferido pessoalmente o hash durante a execução.
- Nenhum pacote foi instalado ou atualizado. O JAR 7.24.0 já fixado foi reutilizado após confirmação de tamanho e SHA-256.

## Inventário gerado aprovado

O manifesto contém 23 caminhos: `api.base.service.ts`, `api.module.ts`, `api/api.ts`, `api/diagnostic.service.ts`, `api/identity.service.ts`, `api/profile.service.ts`, `api/session.service.ts`, `configuration.ts`, `encoder.ts`, `index.ts`, `model/current-device-logout-response.ts`, `model/current-identity-response.ts`, `model/current-profile-response.ts`, `model/diagnostic-component.ts`, `model/diagnostic-response.ts`, `model/diagnostic-status.ts`, `model/models.ts`, `model/profile-update-request.ts`, `model/session-state-response.ts`, `param.ts`, `provide-api.ts`, `query.params.ts` e `variables.ts`.

Nesta execução, foram criados os três arquivos de perfil e alterados somente os índices `api/api.ts` e `model/models.ts` dentro da árvore gerada. Não houve remoções.

## Deviations from Plan

### Auto-fixed Issues

**1. O plano apontava para o proprietário errado do digest**

- **Found during:** Task 1, quando o gerador recusou o manifesto antigo.
- **Issue:** o plano mandava editar `scripts/validate-angular-route-boundary.mjs`, mas as constantes são exportadas por `frontend/scripts/generate-api.mjs` e apenas importadas pelo validador.
- **Fix:** corrigido o allowlist do plano e atualizado somente o verdadeiro contrato do gerador.
- **Verification:** geração determinística, validador de fronteira e testes do gerador passaram.

**2. O adaptador headless do Antigravity não concluiu comandos**

- **Found during:** Task 1 com Gemini 3.7.
- **Issue:** o Gemini pediu a aprovação prevista em `AGENTS.md`; o modo print pulou a pergunta e negou o comando do gerador, devolvendo envelope `ERROR` apesar de ter escrito somente o OpenAPI.
- **Fix:** o OpenAPI produzido pelo Gemini foi auditado; o orquestrador executou o mesmo gerador fixado no worktree e reconciliou o inventário.
- **Verification:** nenhuma escrita ocorreu fora do allowlist corrigido e todos os gates passaram.

**3. O OpenAPI validava o valor bruto, enquanto Java valida depois de `trim()`**

- **Found during:** primeira revisão Codex final (P1).
- **Issue:** `" giovani_99 "` é aceito e normalizado pelo record Java, mas era recusado pelo pattern e pelo `maxLength` do schema.
- **Fix:** o pattern passou a aceitar nas pontas exatamente a faixa removida por `String.trim()` e o limite do valor bruto foi retirado; o núcleo normalizado continua fechado em 3 a 20 caracteres.
- **Verification:** paridade, regeneração determinística e todas as suítes passaram.

**4. A resposta anônima estava documentada como 401**

- **Found during:** primeira revisão Codex final (P2).
- **Issue:** a cadeia BFF redireciona GET e PUT anônimos ao OIDC, e `ProfileControllerIT` prova resposta 3xx.
- **Fix:** ambos os endpoints agora publicam `302` e o cabeçalho `Location`.
- **Verification:** o contrato regenerado passou nos gates e o Maven completo repetiu 225/225.

**5. A âncora ECMAScript `$` admitia terminadores que Java rejeita**

- **Found during:** segunda revisão Codex final (P2).
- **Issue:** em ECMAScript, `$` pode casar antes de `U+2028` ou `U+2029`; `String.trim()` não remove esses caracteres e o pattern Java os rejeita.
- **Fix:** a terminação do schema passou a usar `(?![\s\S])`, que exige fim real da string.
- **Verification:** probes Node e JShell concordaram (`outer-space=true`, `u2028=false`, `u2029=false`); digest permaneceu idêntico e todas as suítes passaram novamente.

**Total deviations:** 5 correções bloqueantes ou de fidelidade contratual, sem dependência ou ampliação funcional.

## Issues Encountered

- A execução fail-first observou a paridade dos endpoints passar e os gates falharem somente pelo manifesto antigo, como esperado.
- Uma primeira tentativa de executar dois gates em paralelo recebeu `LOCK_LIVE`; isso demonstrou que a trava contra duas gerações simultâneas funciona. O gate foi repetido sequencialmente e passou.
- Avisos de Logback e uma reconexão Lettuce apareceram no encerramento dos containers, sem falhas de teste.
- A revisão encontrou uma mensagem cosmética antiga em `validate-angular-route-boundary.mjs` que diz “13 caminhos”. O guard usa a allowlist real de 23 e passou; a mensagem ficou para um bloco futuro porque esse arquivo está fora do allowlist corrigido do plano.

## Evidências de validação

- `node frontend/scripts/generate-api.mjs --check`: passou sem drift.
- `node scripts/validate-phase1.mjs --contract`: passou com todos os endpoints em paridade.
- `node scripts/validate-angular-route-boundary.mjs`: passou.
- Probes ECMAScript e Java para normalização: espaços externos aceitos; `U+2028` e `U+2029` recusados por ambos.
- `node --test frontend/scripts/generate-api.test.mjs`: 67/67.
- `node --test scripts/*.test.mjs`: 518/518.
- `node scripts/maven.mjs -f backend/pom.xml verify`: repetido após as correções da revisão; `BUILD SUCCESS`, 82/82 Surefire e 143/143 Failsafe.
- Total das três suítes de testes distintas: 810/810.
- `git diff --check`: sem erros; apenas aviso esperado de LF/CRLF no Windows.
- Revisão Codex conclusiva do snapshot congelado: `CLEAN`, sem achados P0–P2.

## Escopo dos requisitos

Este plano conclui a representação contratual das partes de `PROF-01`, `PROF-02` e `PRIV-01` atribuídas a ele. Ele não adiciona equipe favorita nem o perfil público de outra pessoa; essas capacidades continuam nos planos consumidores posteriores da fase.

## User Setup Required

None — nenhuma configuração externa ou dependência nova foi adicionada.

## Next Phase Readiness

- O Plano 02-14 está tecnicamente implementado e testado no worktree isolado `02-14`.
- O cliente gerado está pronto para o adaptador Angular do Plano 02-05.
- O commit técnico `9d2a470` está formalizado; integração na `master`, push e limpeza do worktree ainda não foram realizados.
- Os demais planos da Wave 2 não foram iniciados por esta execução.

## Self-Check: PASSED

- Os nove arquivos declarados existem no worktree.
- As suítes agregadas e os gates de contrato terminaram verdes.
- O commit `9d2a470` contém exatamente os sete arquivos técnicos aprovados.
- A primeira revisão Codex encontrou P1/P2 e a segunda encontrou uma borda P2 na âncora ECMAScript; todos foram corrigidos, retestados e encerrados por um terceiro parecer `CLEAN`.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-21*
