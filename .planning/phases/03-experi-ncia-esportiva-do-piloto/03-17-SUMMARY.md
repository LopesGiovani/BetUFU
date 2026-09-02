---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 17
wave: 20
status: complete
completed: 2026-09-02
---

# Wave 20 - Plano 03-17

## Resultado

Certificacao automatizada final da Fase 3 concluida na worktree `worktree-agent-03-17`. A cobertura inclui arquitetura/privacidade, matriz Chromium + Firefox, responsividade em 320 px e 200%, foco/alvos/contraste, mutacoes negativas discriminantes e as suites agregadas. O checkpoint funcional foi aprovado pelo responsavel; os commits tecnico e documental foram integrados por fast-forward e a worktree/branch foram removidas.

## Tasks

- T1: 18 testes focais de arquitetura/privacidade passaram.
- T2: Firefox foi instalado usando a versao Playwright ja fixada; nenhum manifest ou lockfile mudou.
- T3: matriz focal `@03-17-visual-matrix` passou 6/6 em Chromium e Firefox.
- T4: harness passou 6/6; M1, M2 e M3 exigiram vermelho esperado e restauracao por SHA-256.
- T5: snapshot funcional revisado e aprovado; o ciclo Git da wave foi executado em bloco separado, com a sincronizacao remota reconciliada apos a formalizacao documental.

## Gates

| Gate | Resultado |
|---|---|
| Maven | Surefire 270/270 + Failsafe 441/441; 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS` |
| Node | 631/631 pass em 8 suites |
| Angular | 57 arquivos; 342/342 pass |
| Playwright | 20/20 pass, 10 por projeto (Chromium e Firefox) |
| Generator | `generate-api --check` passou sem mutacao |

A primeira execucao Maven sem HMAC falhou em 67 testes por ausencia das variaveis obrigatorias. A repeticao usou chave aleatoria de 32 bytes somente em memoria e passou integralmente. Avisos de Angular/SCSS e teardown de containers/JVM foram nao bloqueantes.

## Integridade

Os alvos temporarios foram restaurados e coincidem com `HEAD`:

- M1 `frontend/src/app/shared/sports/auto-event-hero.component.ts`: `4C9698C576F329D7DFDF9521C10850B13E7C559851F0BE76463F27F464384CF5`
- M2 `backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java`: `5E06793BA9E251CDBC9A1C2D082906796831135C3025CA379E5937C3D4088F6C`
- M3 `backend/openapi/betufu.yaml`: `1B24D2A9711686F2C20DF7395838957B38E1E0EBDD96D9522AFEAD097DAC3DD8`

`frontend/scripts/generate-api.mjs` foi incluido na allowlist por decisao autorizada, permitindo que `--check` execute `assertPublicSchemaAllowlist`. As decisoes D-95 a D-100 permanecem preservadas.

## Desvios

- No Windows, `npm --prefix frontend exec ... --config playwright.sports.config.ts` resolveu o config a partir da raiz; a execucao equivalente foi repetida com `frontend` como diretorio atual.
- O script novo de mutacoes nao estava no indice RepoWise durante a execucao; suas conclusoes foram confirmadas diretamente no arquivo vivo e pelas suites executadas. O indice estava fresco no `HEAD` de execucao.

## Proximo passo

A proxima frente e discutir e planejar a Fase 4; a divida de manutenibilidade desta certificacao permanece como follow-up separado.
