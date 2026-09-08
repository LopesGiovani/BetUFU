---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-19"
wave: 10
status: complete
completed_at: "2026-09-08"
base_commit: 2a69746
technical_commit: PENDING
documentation_commit: PENDING
requirements: [ODDS-01, ODDS-04, ODDS-08, ODDS-09, ODDS-13]
---

# Plano 04-19 - Resumo

## Entrega

- Criado o binding imutavel de roster por `market_version_id` e slot, congelando `publicationId`, `versionNo` e `knownAt` de cada participante.
- Adicionada a migration Flyway V28 com FKs de evento/equipe/publicacao, unicidade por slot/equipe, trigger de coerencia, protecao contra UPDATE/DELETE e grants de runtime.
- Exposta a porta publica `SportsRosterForOddsPort` e seu DTO minimo; o adapter interno consulta apenas a publicacao exata e nao transporta atletas ou PII.
- Implementado `RosterMarketEligibilityService` com validacao fail-closed e lock transacional no evento; correcoes posteriores continuam como novas publicacoes sem reescrever bindings existentes.
- Atualizados os testes Flyway, integracao, concorrencia e a protecao de lock do `SportsRosterService`.

## Evidencia

- Validador Node focal: `5/5` testes; candidato aprovado com `23` verificacoes.
- Maven focal/consolidado: `96/96` testes, zero falhas, erros ou skips.
- Suite Node agregada: `719/719` testes em `9` suites, zero falhas, erros, skips ou cancelamentos.
- Suite Maven agregada: `646/646` testes, `BUILD SUCCESS`, zero falhas, erros ou skips.
- Spotless: `spotless:apply` e `spotless:check` passaram.

## Escopo e limites

- A abertura real de mercado, suspensao e recalculo permanecem atribuicoes do Plano 04-05; este plano fornece o contrato e o binding atomico.
- A porta publica expoe somente identidade de equipe, publicacao, versao e tempo conhecido; membros de roster e PII ficam no modulo sports interno.
- Foram aplicadas duas correcoes de wiring Spring preexistentes e autorizadas: `@Autowired` no servico de odds, e registro por perfil no repositorio de odds/adapter.

## Git

- Commit tecnico: `PENDING` neste arquivo para evitar auto-referencia circular.
- Commit documental: `PENDING` neste arquivo para evitar auto-referencia circular.
- Integracao e push serao executados apos os commits locais.
