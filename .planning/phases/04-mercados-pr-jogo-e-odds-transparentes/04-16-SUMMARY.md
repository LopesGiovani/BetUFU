---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "16"
wave: 6
status: complete
completed_at: "2026-09-08"
base_commit: e31aa8a
technical_commit: db5aab9
documentation_commit: 1005b0b
requirements: [ODDS-01, ODDS-04, ODDS-08, ODDS-13]
---

# Plano 04-16 - Resumo

## Entrega

- Criada a migration Flyway `V27__create_sports_outcome_authority.sql`, sucessora ratificada de V26, com fatos append-only, payload esportivo referenciando a versão exata de V16, payload administrativo sem score e um head corrente único por evento.
- Aplicadas chaves compostas, constraints, triggers de imutabilidade e coerência de payload/head; V16 e V24 não foram alteradas.
- Criados os records, repositório, serviço e controller administrativos em `sports.internal.outcome`, com identidade operacional reconsultada no servidor, escopo de competição, lock pessimista e compare-and-set de `lock_version`.
- Integrados os caminhos de confirmação/correção esportiva existentes à autoridade V27 sem duplicar scores: o payload aponta para a versão V16 e a projeção legada `confirmed_version_id` continua compatível.
- Fatos administrativos W.O. e abandono limpam `confirmed_version_id`, não recebem score e preservam a linhagem por `supersedes_fact_id`.

## Evidência

- T1 focal: `38/38` testes, zero falhas, erros ou skips.
- T2 focal: `2/2` testes, zero falhas, erros ou skips.
- T3 focal consolidado: `20/20` testes, zero falhas, erros ou skips.
- Suíte Node agregada: `699/699` em 9 suites, zero falhas, erros, skips ou cancelamentos.
- Suíte Maven agregada: `594/594`, `BUILD SUCCESS`, zero falhas, erros ou skips.
- `git diff --check`: passou.
- Spotless global continua limitado por 19 divergências históricas de finais de linha fora da allowlist; não houve alteração desses arquivos nesta Wave.

## Reconciliacao de escopo

- Os 12 caminhos alterados coincidem com a allowlist efetivamente executada; não houve dependências, OpenAPI, frontend, infraestrutura, V16, V24 ou push remoto.
- O filename ratificado foi usado literalmente: `V27__create_sports_outcome_authority.sql`.
- A reconstrução canônica preservou a árvore final de `64fba75` e consolidou a sequência a partir de `0daddee`; a worktree e a branch temporárias foram removidas após a integração.

## Limites e follow-ups

- O contrato de auditoria existente em V26 não contém uma ação específica de outcome; por isso não foi inventado vocabulário fora da allowlist. A ligação formal a `AuditRecorder` deve ser tratada em um plano que ratifique a nova ação.
- O serviço administrativo ainda não possui recibo persistente de comando que permita replay retornar o mesmo resultado; a proteção atual é transacional e usa o lock esperado. Idempotência completa deve ser adicionada junto ao contrato de comando ratificado.
- `SportsOutcomeConcurrencyIT` cobre a taxonomia e as invariantes de modelo; a corrida real entre comandos administrativos e esportivos permanece dependente das suítes de resultado existentes e merece um teste dedicado de Testcontainers no próximo plano de integração.
- Replay/as-of temporal não é exposto por este serviço; o schema preserva `known_at`, `sport_time` e supersessão para o leitor futuro.

## Snapshot SHA-256

- `backend/src/main/resources/db/migration/V27__create_sports_outcome_authority.sql`: `6BEA5B46E11A52294809EF21FE6F8DB1B542651FC8F0303C1B9F834C607CA9F9`
- `backend/src/main/java/br/com/betufu/sports/internal/outcome/SportsOutcomeModels.java`: `FBA4B453BF1BEDF10F2A4F6163B9AD1DB1552B6E6756FBBA07F22886405D8EF0`
- `backend/src/main/java/br/com/betufu/sports/internal/outcome/SportsOutcomeRepository.java`: `287F4355B59A1C2F9F4D196582B563C4CBCA3A3814A126DFDCE5A96BF66CCFBC`
- `backend/src/main/java/br/com/betufu/sports/internal/outcome/SportsOutcomeService.java`: `56DB906C1D481EA595AD0629672CC534BB5CDE6FDD8933C76524EB054840A5FF`
- `backend/src/main/java/br/com/betufu/sports/internal/outcome/SportsAdministrativeOutcomeController.java`: `BCA9F8BE3B561CACFF603B6A197305844B7C027B46F9BF520D4856C7715C28C1`
- `backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultRepository.java`: `6D4D3EAEBE532979265EBBDE42154360F82468F3AAD964DC04037161960A6D1D`
- `backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java`: `A50DDA64C7E9D59CDB5CC0F1E17902FDC286F5CA8D011F3E664321CF1271C5F0`
- `backend/src/test/java/br/com/betufu/sports/SportsOutcomeAuthorityIT.java`: `D877A3A64E28A6EAC5146A5F4BF823381859DDD637B24FDF757C24CBE8CD3EDB`
- `backend/src/test/java/br/com/betufu/sports/SportsAdministrativeOutcomeIT.java`: `C70AA6FA6C7F8C6FE64F3B04D9D2084A3260D3416AE8A75396A34C8D43839104`
- `backend/src/test/java/br/com/betufu/sports/SportsOutcomeConcurrencyIT.java`: `AF1042FC104CA0DA21D79D924B554740537A5E0FA4908949820D000A5DCDB74F`
- `backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java`: `976B719B236B832C9C3EA08B70B32EED22F15C73EC588D0EFEAB8D2BB6072D7A`
- `backend/src/test/java/br/com/betufu/platform/persistence/FlywayChecksumIT.java`: `9DE9A44B0396496B3C762CDB658753C048245C1D93FD897CD49E4554EA8E42EE`

## Git

- Commit tecnico: `db5aab9`.
- Integracao fast-forward e limpeza da worktree/branch exatas concluidas localmente.
- Nenhum push remoto foi executado.

