---
phase: 03-experiencia-esportiva-do-piloto
plan: 07
wave: 8
status: complete-pending-git
completed: 2026-08-30
---

# Plano 03-07 - Resumo

## Resultado

O plano entregou propostas explicitas de vinculo entre atleta e identidade,
decisao owner-only e a fila operacional O8 para excecoes e contestacoes. O
vinculo nunca nasce somente de uma coincidencia de matricula: uma coincidencia
unica cria uma proposta `PENDENTE`, e somente a pessoa alvo pode aceitar,
rejeitar ou contestar essa proposta.

A reconciliacao funciona nos dois sentidos. O cadastro de atleta publica
`SportsAthleteCreated(athleteId)` depois do commit, e a verificacao de
matricula publica `EnrollmentVerified(identityId)` depois do commit. Os
eventos carregam somente identificadores opacos; o reconciliador consulta a
porta publica de fingerprint e os repositories do modulo esportivo, tolera
retry e nao enumera matricula, HMAC ou candidatos na API.

## Entrega tecnica

- V13 criou `sports_athlete_link_proposal` e `sports_athlete_link_review`, com
  FKs para atleta, identidade e proposta, checks de estado, timestamps e
  `lock_version` para optimistic locking.
- Indices parciais impedem dois vinculos aceitos para o mesmo atleta ou
  identidade, duas propostas pendentes para o mesmo par e revisoes duplicadas
  para a mesma causa.
- V14 adicionou expiracao, metadados de decisao e checks que mantem a
  transicao `PENDENTE -> ACEITA|REJEITADA|EXPIRADA|CONTESTADA` consistente.
- `AthleteLinkService` usa atualizacao condicional por estado e versao; uma
  corrida ou versao obsoleta retorna HTTP 409. Replay do mesmo estado terminal
  devolve a representacao persistida, sem repetir a associacao.
- `CONTEST` move a proposta para `CONTESTADA` e cria uma unica revisao O8 com
  `CONTESTED`. Casos `DIVERGENT_DATA`, `MULTIPLE_CANDIDATES` e `ALREADY_LINKED`
  criam revisao idempotente com motivo allowlist e contexto minimo.
- O acesso owner deriva a identidade dos claims `iss`/`sub` e nao aceita UUID
  livre como autoridade. A fila O8 exige `ADMINISTRADOR_GERAL` global, sem
  aceitar `scopeRef` digitavel para ampliar permissao.
- O contrato OpenAPI aprovado contem somente a proposta owner, a decisao
  owner e a listagem O8. O modelo nomeado `AthleteLinkReviewSummary` evita
  expor entidades internas ou dados de matricula.
- O candidato `03-07` foi aprovado com digest
  `B75187EFECB25E0D4809F6ED5D0354D329D9175DB9ACE3BCEB1BBA4ECDB7A8B2`.
  Os hashes `governed-before` e `governed-after` permaneceram iguais em
  `DB17FF4C4B1058BED0836C51F1922922D621E78CC3DFA35C001C682ADED5DC24`.
- O cliente TypeScript foi gerado somente depois da aprovacao. Os adapters e
  stores Angular mantem estado privado em Signals, separam owner de O8,
  removem estado sensivel em 401/403 e nao usam retry em mutacoes.

## Validacao

- Maven completo: `251` Surefire + `374` Failsafe = `625` testes Java, todos
  verdes; `BUILD SUCCESS`, incluindo Flyway ate V14 e os ITs funcionais,
  concorrentes e de persistencia do vinculo.
- Node completo: `625/625`, incluindo gerador OpenAPI, guardas de rotas,
  contratos de seguranca, runtime e mutacoes negativas.
- Foco Angular do plano: `4` arquivos e `10/10` testes verdes.
- `node frontend/scripts/generate-api.mjs --check`: aprovado.
- Build Angular production: aprovado. Permanecem avisos informativos de
  budget SCSS ja existentes em outras telas.
- Playwright esportivo: `2/2`, cobrindo desktop 1280px e viewport 320px,
  agenda publica, filtros, acessibilidade e ausencia de sessao.
- `git diff --check`: aprovado; os avisos LF -> CRLF sao do checkout Windows.
- Suíte Angular agregada: `272/277`; as 5 falhas estao exclusivamente em
  `role-admin-page.component.spec.ts`, fora da allowlist e reproduzem a lacuna
  historica registrada nas waves anteriores. Nenhum teste foi removido,
  alterado ou mascarado para obter o resultado do plano.

## Desvios e limites

- A primeira tentativa de Maven falhou por ausencia das variaveis de HMAC;
  a segunda recebeu chaves descartaveis apenas no processo e terminou verde.
- O comando Node com glob Unix nao foi expandido pelo PowerShell; a mesma
  suite foi repetida com os arquivos `.test.mjs` enumerados e passou.
- A primeira chamada Playwright usou caminho de config relativo incorreto;
  apos corrigir para `frontend/playwright.sports.config.ts`, os dois testes
  passaram. O diretorio temporario de saida foi removido ao final.
- A allowlist documental foi ampliada para refletir V14, a auditoria, o
  modelo nomeado de revisao e a compatibilidade do tracer. Ela declara 45
  paths; 40 aparecem no status Git porque quatro metadados em `frontend/target`
  sao ignorados intencionalmente e o proprio PLAN e documental.
- Os avisos de teardown Redis/Hikari/Testcontainers ocorreram durante o
  encerramento de recursos, depois de todos os testes passarem, e nao
  alteraram o codigo de saida do Maven.
- Bloqueio identificado para a proxima wave: este plano usa
  `V14__harden_athlete_link_proposals.sql`, enquanto `03-08-PLAN.md` ainda
  declara `V14__create_sports_rosters.sql`. Flyway nao aceita duas migrations
  com a mesma versao; o proximo plano precisa receber uma versao posterior
  (ou outra decisao de sequenciamento) antes de executar. Nenhum arquivo de
  `03-08` foi alterado por inferencia.

## Estado Git e proximo gate

- Worktree: `C:/Users/giova/gsd-workspaces/phase-03/03-07`.
- Branch: `worktree-agent-03-07`.
- HEAD congelado para revisao: `11fd3b8f509208451a582ac2f8082049a740ffc5`.
- `origin/master` observado no mesmo commit `11fd3b8f509208451a582ac2f8082049a740ffc5`.
- Worktree principal permanece separada em `C:/Users/giova/Desktop/BetUFU`.
- Nenhum stage, commit, merge, limpeza ou push foi executado nesta wave.
- O proximo gate operacional e a revisao Codex do snapshot e, conforme a
  autorizacao Git especifica, os commits tecnico/documental, integracao,
  limpeza da worktree exata e push.
