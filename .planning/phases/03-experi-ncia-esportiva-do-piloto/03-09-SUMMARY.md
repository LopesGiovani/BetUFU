---
phase: 03-experiencia-esportiva-do-piloto
plan: 09
wave: 10
status: complete-pending-git
completed: 2026-08-31
subsystem: sports-tabular-import
tags: [spring-boot, postgresql, flyway, openapi, angular, signals, csv, xlsx]

requires:
  - phase: 03-experiencia-esportiva-do-piloto
    provides: equipes, atletas, elencos versionados e autorizacao esportiva escopada
provides:
  - importacao atomica de elencos por CSV UTF-8 e XLSX OOXML
  - preview seguro, confirmacao unica e consulta de estado indeterminado no O5
  - cliente OpenAPI governado e rota operacional de importacao
affects: [03-10, 03-11, 03-16, sports-import, sports-rosters]

actuals:
  tasks: 5
  commits: 0

tech-stack:
  added: [Apache Commons CSV 1.14.1, Apache POI 5.5.1]
  patterns:
    - parsers CSV e XLSX separados compartilhando a mesma linha logica de validacao
    - validacao completa antes da transacao all-or-zero
    - cliente gerado apos candidato OpenAPI aprovado por digest
    - adapter Angular sobre servico gerado e store Signal fail-closed

key-files:
  created:
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/ImportedRosterRow.java
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsCsvParser.java
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsXlsxParser.java
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsTabularImportService.java
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsTabularImportController.java
    - backend/src/test/java/br/com/betufu/sports/SportsCsvParserTest.java
    - backend/src/test/java/br/com/betufu/sports/SportsXlsxParserTest.java
    - backend/src/test/java/br/com/betufu/sports/SportsTabularImportIT.java
    - frontend/src/app/features/sports/operations/tabular-import/sports-tabular-import-api.adapter.ts
    - frontend/src/app/features/sports/operations/tabular-import/sports-tabular-import.store.ts
    - frontend/src/app/features/sports/operations/tabular-import/sports-tabular-import-page.component.ts
  modified:
    - backend/pom.xml
    - backend/openapi/betufu.yaml
    - frontend/scripts/generate-api.mjs
    - frontend/scripts/generate-api.test.mjs
    - frontend/src/app/api/generated/api/api.ts
    - frontend/src/app/api/generated/model/models.ts
    - frontend/src/app/app.routes.development.ts
    - frontend/src/app/app.routes.production.ts
    - scripts/validate-angular-route-boundary.mjs
    - scripts/validate-angular-route-boundary.test.mjs

key-decisions:
  - CSV e XLSX aceitam somente templates fixos; formulas, macros, celulas mescladas, abas ou cabecalhos fora do contrato sao rejeitados.
  - Matricula XLSX e lida como texto para preservar zeros a esquerda; limites de bytes, linhas, colunas e celulas evitam abuso de parser.
  - A importacao resolve referencias persistidas e grava tudo ou nada; nenhuma equipe, atleta ou competicao e criada implicitamente.
  - Validate e commit nao fazem retry automatico; consulta de status e a unica recuperacao idempotente apos resultado indeterminado.
  - 401/403 limpam arquivo, preview, resumo, token e importId da memoria e revogam object URL quando existente.

requirements-completed: [SPRT-02]

coverage:
  - id: D1
    description: "Parsers CSV/XLSX e importacao atomica validam limites, referencias, duplicatas, formulas e erro tardio sem escrita parcial."
    requirement: SPRT-02
    verification:
      - kind: focal
        ref: "SportsCsvParserTest 8/8, SportsXlsxParserTest 7/7, SportsTabularImportIT 8/8"
        status: pass
      - kind: integration
        ref: "Maven completo: Surefire 266/266, Failsafe 391/391, total 657, sem falhas ou erros"
        status: pass
    human_judgment: false
  - id: D2
    description: "O contrato OpenAPI de importacao foi aprovado e consumido somente apos geracao governada."
    verification:
      - kind: other
        ref: "Candidato 03-09 aprovado: C1B4B3F7F37E84EF63911A6BE31A2B36C949DDC0FB63B84C193D72F19B3D3BB8"
        status: pass
      - kind: other
        ref: "governed-before/governed-after iguais; node frontend/scripts/generate-api.mjs --check"
        status: pass
      - kind: unit
        ref: "Node completo 625/625"
        status: pass
    human_judgment: true
  - id: D3
    description: "O5 oferece selecao sem upload automatico, preview seguro, confirmacao unica, estado indeterminado e limpeza fail-closed."
    verification:
      - kind: unit
        ref: "Angular focal do store e componente O5: 7/7"
        status: pass
      - kind: build
        ref: "Angular development, homolog e production: BUILD verde"
        status: pass
      - kind: automated_ui
        ref: "Playwright esportivo: 2/2 em desktop e viewport de 320px"
        status: pass
    human_judgment: false

deviations:
  - "A primeira execucao Maven sem variaveis falhou em 4 smoke tests por chave HMAC ausente; a repeticao com a chave de teste ja padronizada no repositorio passou 657/657. Nenhum segredo foi persistido."
  - "O filtro de dependencia literal previsto com * foi recusado pelo wrapper Maven; a arvore completa sem curinga confirmou as mesmas versoes e ausencia de SNAPSHOT/Apache HTTP."
  - "A allowlist inicial nao enumerava seis paths necessarios para os dois modelos auxiliares, as rotas de importacao e a guarda de rotas; o PLAN foi reconciliado para 56 paths declarados."
  - "A suite Angular completa permanece em 285/290 por cinco falhas historicas de role-admin-page.component.spec.ts, fora da allowlist e fora deste plano."

validation:
  - "Dependency tree: versoes exigidas presentes; nenhum SNAPSHOT ou modulo Apache HTTP."
  - "Node completo: 625/625."
  - "Maven completo: Surefire 266/266 e Failsafe 391/391."
  - "Angular focal O5: 7/7; Angular completo: 285/290, somente cinco falhas historicas fora do escopo."
  - "Builds development/homolog/production: verdes; avisos de budget SCSS permanecem informativos."
  - "Playwright esportivo: 2/2; fronteira Angular: verde; candidato e gerador: verdes; git diff --check: verde."

git:
  status: complete-pending-git
  implementation_commit: pending-authorization
  documentation_commit: pending-authorization
  integration: pending-authorization
  cleanup: pending-authorization
  push: pending-authorization

next-phase-readiness:
  - "A worktree esta pronta para revisao Codex somente leitura e para as operacoes Git separadas."
  - "O proximo plano tecnico da fase e 03-10/W11, dependente da integracao aprovada deste plano."

---

# Fase 3 Plano 03-09: Importacao tabular CSV/XLSX e O5

## Resultado

O plano entrega importacao de elencos em CSV UTF-8 e XLSX OOXML com templates fixos, validacao completa, referencias existentes e transacao all-or-zero. O5 usa o cliente OpenAPI aprovado, mantem arquivo e identificadores somente em memoria e trata falha de transporte sem reenviar mutacoes.

## Operacoes Git

Nenhum stage, commit, integracao, limpeza ou push foi executado. Essas operacoes permanecem aguardando autorizacao especifica.

## Limitacoes conhecidas

Os avisos de teardown Redis/Hikari/Testcontainers, alertas de agente Java, conversao LF/CRLF e as cinco falhas historicas da suite Angular nao invalidaram os gates verdes do plano. A worktree principal ainda precisa ter removidas somente as duas alteracoes acidentais da allowlist; a alteracao documental intencional em `AGENTS.md` deve ser preservada.

## Self-check

- Os 56 paths declarados no PLAN coincidem com a uniao das listas de tasks.
- O candidato aprovado possui hashes governed-before/after iguais.
- A implementacao nao recebeu edicoes depois das provas finais.
- O fechamento continua `complete-pending-git` ate a revisao e as autorizacoes Git do responsavel.
