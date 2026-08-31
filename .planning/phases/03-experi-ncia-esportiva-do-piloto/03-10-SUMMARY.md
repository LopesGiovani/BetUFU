---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 10
wave: 11
status: complete
completed: 2026-08-31
subsystem: sports-event-result
tags: [spring-boot, postgresql, flyway, openapi, angular, signals, optimistic-locking]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: catalogo, organizacoes, equipes, atletas, elencos versionados e escopo operacional autorizado
provides:
  - lifecycle versionado de eventos e resultados com fontes oficiais e auditoria
  - conflitos de concorrencia protegidos por versao base e respostas HTTP 409
  - cliente OpenAPI governado e workspace operacional O6 em Angular Signals
affects: [03-11, 03-16, sports-lifecycle, public-arena]

actuals:
  tokens: 86034
  tasks: 5
  commits: 3

tech-stack:
  added: []
  patterns:
    - migration Flyway append-only com tabelas de evento, resultado e versoes de resultado
    - comandos de lifecycle com optimistic locking e erro 409 sem overwrite
    - candidato OpenAPI aprovado por digest antes da geracao governada do cliente
    - adapter Angular sobre cliente gerado, store privado em signal/computed e componente sem Observable HTTP

key-files:
  created:
    - backend/src/main/resources/db/migration/V16__create_event_result_versions.sql
    - backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventService.java
    - backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventResultController.java
    - backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java
    - backend/src/test/java/br/com/betufu/sports/SportsEventResultIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsEventResultConcurrencyIT.java
    - frontend/src/app/api/generated/api/sports-event-result.service.ts
    - frontend/src/app/features/sports/operations/events-results/sports-event-result.store.ts
    - frontend/src/app/features/sports/operations/events-results/sports-event-result-page.component.ts
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerController.java
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/api/generated-client.contract.spec.ts
    - frontend/src/app/features/sports/operations/events-results/sports-event-result-page.component.scss
    - .planning/phases/03-experi-ncia-esportiva-do-piloto/03-10-PLAN.md

key-decisions:
  - Evento e resultado usam maquinas de estado explicitas, com historico append-only e publicacao somente nos estados autorizados.
  - Confirmacao exige fonte oficial; registro, confirmacao e autorizacao permanecem como atores distintos e nenhum credito e movimentado.
  - Concorrencia usa versao base persistida; conflito retorna 409 e preserva a versao vencedora para recarga explicita.
  - O6 permanece operacional; a pagina publica P3 e a expansao da Arena continuam reservadas ao plano 03-11.
  - O endpoint antigo de criacao de evento foi removido do tracer para evitar dois handlers Spring na mesma rota assumida pelo novo controller.

patterns-established:
  - "Lifecycle one-way: transicoes invalidas nao gravam e transicoes validas criam versao/auditoria."
  - "Fail-closed no frontend: 401/403 limpam formulario, fonte, motivo, preview, versao base, timeline e confirmacao."
  - "OpenAPI candidate -> aprovacao humana -> geracao governada -> consumo Angular."

requirements-completed: [SPRT-10, SPRT-11]

coverage:
  - id: D1
    description: "V16 persiste lifecycle de eventos/resultados, fontes, atores e versoes; comandos validam transicoes e optimistic locking."
    requirement: SPRT-10
    verification:
      - kind: integration
        ref: "Maven focal: Surefire 266/266; Failsafe 16/16 em FlywayMigrationIT, SportsEventResultIT e SportsEventResultConcurrencyIT"
        status: pass
      - kind: other
        ref: "Flyway aplicou 17 migrations ate v16 e validou a migration em banco PostgreSQL Testcontainers"
        status: pass
    human_judgment: false
  - id: D2
    description: "Contrato OpenAPI de eventos/resultados foi aprovado e o cliente gerado corresponde ao digest governado."
    requirement: SPRT-10
    verification:
      - kind: other
        ref: "Candidato 03-10 aprovado: 92DF0E9705C4A87771353804E181E1B5B24EA9ED3CE98DEC6D3B5B29A65C4210"
        status: pass
      - kind: other
        ref: "governed-before/governed-after iguais: 615A3CA508A62B125671AB5EF3F46648303D470D2E7EB9D9150B893D5F42B1EC"
        status: pass
      - kind: unit
        ref: "node --test scripts/*.test.mjs: 72/72; node frontend/scripts/generate-api.mjs --check"
        status: pass
    human_judgment: true
    rationale: "A inspecao final dos campos internos e da fronteira publico/operacional ainda e uma decisao humana de contrato."
  - id: D3
    description: "O6 opera eventos e resultados por adapter, store Signals e componente, incluindo formularios, preview/diff, timeline e tratamento de 409."
    requirement: SPRT-11
    verification:
      - kind: unit
        ref: "Teste focal Angular de store e componente: 10/10"
        status: pass
      - kind: unit
        ref: "Suite Angular completa: 39 arquivos e 301/301 testes"
        status: pass
      - kind: other
        ref: "Builds development, homolog e production: sucesso"
        status: pass
    human_judgment: true
    rationale: "A validacao automatica cobre comportamento e compilacao, mas a avaliacao visual responsiva do workspace requer revisao humana."
  - id: D4
    description: "O componente O6 respeita o budget de homologacao apos consolidacao dos seletores SCSS."
    requirement: SPRT-11
    verification:
      - kind: other
        ref: "build:homolog: SCSS do O6 em 7,86 kB, abaixo do limite de 8,00 kB"
        status: pass
    human_judgment: false

deviations:
  - "A primeira execucao Maven falhou porque as variaveis HMAC obrigatorias nao estavam no ambiente; a repeticao usou chave descartavel somente no processo e passou."
  - "A chamada inicial do wrapper com -Dit.test foi fragmentada pelo node em -Dit e .test=...; a prova foi repetida com MAVEN_OPTS e executou os tres ITs previstos."
  - "O Antigravity reduziu o SCSS do O6 de 9,23 kB para 7,86 kB em quatro ciclos; o worker nao executou build ou testes, e a certificacao foi feita independentemente pelo orquestrador."
  - "A allowlist foi reconciliada para incluir os dois modelos gerados usados pelo contrato, o tracer que perdeu o handler duplicado e o proprio plano que documenta a correcao."

validation:
  - "Maven focal com HMAC descartavel: Surefire 266/266 e Failsafe 16/16, BUILD SUCCESS."
  - "Angular focal: 10/10; Angular completo: 301/301."
  - "Node scripts: 72/72; generate-api --check: OK."
  - "Builds development, homolog e production: verdes; NG8102 e budgets globais permanecem warnings informativos."
  - "git diff --check: sem erros; allowlist: 36 declarados, 31 paths alterados, unexpected=0, missing=0."

issues:
  - "Warnings de teardown Redis/Testcontainers, Mockito/ByteBuddy e conversao LF/CRLF nao alteraram o resultado dos testes."
  - "Um container preexistente de infraestrutura (decolua/9router) e processos do Antigravity continuam ativos; nenhum servico de desenvolvimento foi iniciado pela wave."

git:
  status: complete
  implementation_commit: cf478b1
  documentation_commit: 8e7109d
  integration: fast-forward completed
  cleanup: worktree-agent-03-10 and branch removed
  push: origin/master synchronized

next-phase-readiness:
  - "O contrato e a persistencia de eventos/resultados estao prontos para o plano 03-11, que entrega a superficie publica P3."
  - "A fase esta pronta para o plano 03-11/W12, que entrega a superficie publica P3."
---

# Fase 3 Plano 03-10: Lifecycle esportivo e O6

**Lifecycle versionado de eventos e resultados com fonte oficial, conflitos 409 e workspace operacional O6 em Angular Signals**

## Resultado

V16 entrega as maquinas de estado de evento e resultado, historico append-only, fontes oficiais, atores separados e optimistic locking. O6 consome o cliente OpenAPI governado por meio de adapter e store Signals, sem antecipar a pagina publica P3.

## Operacoes Git

Os commits tecnico `cf478b1` e documental `8e7109d` foram integrados por fast-forward na `master`. O tracking foi formalizado no commit final da wave; a worktree e a branch temporarias foram removidas e `origin/master` foi sincronizada.

## Limitacoes conhecidas

Os warnings de `NG8102`, budgets globais, teardown de containers, Mockito/ByteBuddy e LF/CRLF sao informativos. A avaliacao visual final do O6 permanece como julgamento humano separado; nao ha falha de regra de negocio conhecida.

## Self-check

- Os 36 paths declarados no plano cobrem a uniao dos 31 paths alterados.
- O candidato aprovado preserva hashes governed-before/governed-after iguais.
- A implementacao nao recebeu edicoes depois das provas finais; a unica alteracao posterior foi documental, para registrar a correcao de allowlist.
- Maven focal, builds Angular, suite Angular, Node e `git diff --check` passaram.
- O fechamento esta `complete`; nao ha operacoes Git pendentes para esta wave.
