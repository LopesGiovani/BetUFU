---
phase: 01-funda-o-segura-da-plataforma
plan: "20"
subsystem: api-contract
tags: [openapi, typescript-angular, generated-client, deterministic-generation, bff, xsrf]

requires:
  - phase: 01-04
    provides: "Workspace Angular 22/TypeScript 6, baseline npm e builds reproduzíveis"
  - phase: 01-19
    provides: "Host Angular neutro, HttpClient/XSRF same-origin e validador de fronteira"
provides:
  - "Contrato OpenAPI 3.1 inicial para PUT /api/v1/tracer/identity/current"
  - "Cliente TypeScript Angular machine-owned, determinístico e limitado a uma árvore aprovada"
  - "Runner generate/check recuperável, com lock, journal, rollback, digest e fallback same-origin"
affects: [01-05, 01-08, 01-16, 01-21, 01-26, api, frontend, backend, bff, ci, security]

actuals:
  tasks: 3
  commits: 2

key-decisions:
  - "O contrato inicial expõe somente PUT /api/v1/tracer/identity/current; não introduz OIDC, Keycloak ou esquema Bearer."
  - "provideApi('') materializa base vazia, preservando request relativo, sessão BFF same-origin e XSRF nativo."
  - "A publicação do cliente exige allowlist exata de 13 paths e manifesto SHA-256; qualquer byte, path ou arquivo extra bloqueia e restaura a última árvore válida."
  - "A configuração .gitattributes whitespace=-blank-at-eol foi limitada ao necessário, sem alterar o digest aprovado."

requirements-completed: [API-01, API-02]
completed: 2026-08-12
status: complete
---

# Phase 1 Plan 20: OpenAPI inicial e cliente Angular determinístico Summary

**Contrato OpenAPI mínimo e cliente Angular gerado de forma determinística, recuperável e compatível com a fronteira BFF/XSRF.**

## Desempenho

- **Conclusão:** 2026-08-12
- **Tasks planejadas:** 3
- **Commits de implementação:** 2
- **Dependências de entrada:** Plano 01-04 (workspace e baseline) e Plano 01-19 (host, XSRF e fronteira Angular).

## Entregas

- `backend/openapi/betufu.yaml` define o contrato OpenAPI 3.1 inicial com somente `PUT /api/v1/tracer/identity/current`.
- `frontend/scripts/generate-api.mjs` implementa geração e verificação recuperáveis: lock, journal, rollback, normalização LF, análise fail-closed e publicação transacional.
- O cliente em `frontend/src/app/api/generated/` é machine-owned e tem árvore exatamente limitada a 13 arquivos TypeScript aprovados.
- `provideApi('')` em `app.config.ts` mantém a base vazia: a chamada gerada é relativa, usa a sessão BFF same-origin e deixa o XSRF para o `HttpClient` nativo, sem construir `Authorization`, `Cookie` ou `withCredentials` manual.
- O validador da fronteira Angular e o teste de contrato do cliente protegem o uso do cliente gerado contra drift arquitetural.

## Commits das tarefas

1. **Task 1 — pipeline recuperável do contrato e geração:** `2d474b8` (`feat(01-20): add recoverable OpenAPI generation pipeline`). Incluiu `.gitattributes`, a fonte OpenAPI, a configuração do gerador e o runner/testes iniciais.
2. **Tasks 2 e 3 — grafo aprovado, cliente determinístico e hardening:** `168fd8e` (`feat(01-20): add deterministic OpenAPI Angular client`). Incluiu o lockfile, o cliente gerado, a prova Angular, `provideApi('')`, o hardening do runner e o validador da fronteira.

## Evidências de verificação

| Prova | Resultado |
|---|---|
| Geração Java/JAR real | PASS — duas gerações consecutivas produziram os mesmos 13 paths e o digest `78E92BB17EBC45E25F86CA3D23C66958BDCD1786983DEFDC2DD1050BE8D13D5F`. |
| `generate:api:check` | PASS — terminou em código 0, sem drift byte a byte. |
| Fake runner | PASS — `67/67` testes, incluindo lock, journal, rollback, normalização e janela de publicação. |
| Fronteira Angular | PASS — `110/110` regressões e validador canônico em código 0. |
| Contrato Angular focal | PASS — `2/2` cenários, com e sem `provideApi('')`, preservando PUT relativo, XSRF nativo e ausência de autenticação/credencial manual ativa. |
| Builds Angular | PASS — development, homolog e production, todos em código 0. |
| Imutabilidade do grafo npm | PASS — `package.json`: `B33EB0627A4EA71809D15DCFC6DF1232BE570490E648D80FE162B9295F7226C7`; `package-lock.json`: `99A856C8C635EB4FB9845DEE6F1F2E7A2A4E8576BF80654B9695B5E6D6C4F035`. |
| Motor OpenAPI | PASS — JAR de 31.904.922 bytes, SHA-256 `4B83CCC6FD43056C8C631CD0195E5100BD0550912502527BAB09AC76152DAB0C`. |
| Higiene | PASS — zero lock/journal/operação residual, árvore final somente TypeScript e `git diff --check` em código 0. |

## Árvore gerada aprovada

O manifesto canônico protege exatamente estes 13 paths relativos sob `frontend/src/app/api/generated/`:

1. `api.base.service.ts`
2. `api.module.ts`
3. `api/api.ts`
4. `api/identity.service.ts`
5. `configuration.ts`
6. `encoder.ts`
7. `index.ts`
8. `model/current-identity-response.ts`
9. `model/models.ts`
10. `param.ts`
11. `provide-api.ts`
12. `query.params.ts`
13. `variables.ts`

O digest histórico `701CCD54E5DD910F5AF2E4FCDAE9492CE89A6DAC3BD25731FB48268A1476A6D6` permanece apenas para rastreabilidade: a revisão constatou que ele não era reproduzível por `readTreeManifest`. O valor canônico que substitui essa evidência é `78E92BB17EBC45E25F86CA3D23C66958BDCD1786983DEFDC2DD1050BE8D13D5F`.

## Desvios e correções

- O fallback same-origin foi materializado explicitamente como base vazia. Isso cobre o caso sem `provideApi('')` e impede que uma URL HTTP(S) absoluta seja publicada ou validada.
- A revisão final endureceu a análise para que `//` dentro de regex, URL absoluta montada por concatenação de literais e arquivos/serviços extras não escapem da proteção.
- A publicação passou a reler os mesmos bytes imediatamente antes do rename e novamente no destino instalado, fechando a janela TOCTOU (tempo entre a verificação e o uso); divergências provocam rollback seguro.
- `.gitattributes` recebeu somente a regra `whitespace=-blank-at-eol` necessária para a árvore gerada, sem mudança no digest canônico.

## Riscos residuais aceitos

- O advisory `GHSA-frvp-7c67-39w9` permanece aceito temporariamente; qualquer novo achado high/critical ou mudança do advisory reabre a decisão humana.
- O Java local foi verificado como specification 25, version 25.0.4 e vendor Eclipse Adoptium, mas ainda é resolvido pelo `PATH`. Fixar uma distribuição JDK para CI é uma decisão futura.
- A prova integral de ausência futura de tracer/diagnóstico no bundle e no navegador continua com os Planos 01-21 e 01-28.

## Requisitos e próximo passo

`API-01` e `API-02` estão cobertos localmente por este plano. `REQUIREMENTS.md` permanece globalmente como `Pending`, pois os requisitos da Fase 1 só são encerrados quando as entregas compartilhadas estiverem completas.

O próximo plano é o **01-05**, responsável por implementar no backend Spring/PostgreSQL o endpoint `PUT /api/v1/tracer/identity/current` já contratado aqui.

## Autoavaliação: APROVADA

- [x] O contrato inicial, o runner e o cliente gerado foram registrados em dois commits auditáveis.
- [x] A árvore gerada é limitada a 13 paths e a um digest reproduzido por duas gerações reais.
- [x] Generate/check, testes Node, testes Angular e três builds passaram.
- [x] A fronteira BFF/XSRF e o fallback same-origin permanecem preservados.
- [x] Os riscos GHSA e Java via PATH estão explicitamente registrados, sem serem confundidos com aprovação automática futura.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Completed: 2026-08-12*
