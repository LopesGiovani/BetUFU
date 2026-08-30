---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 04
wave: 5
status: complete-pending-git
completed: 2026-08-29
---

# Plano 03-04 - Resumo

## Resultado

O plano entregou a expansao V10 do catalogo esportivo e as superficies O1/O2
do piloto. A migracao preserva a hierarquia Esporte -> Competicao -> Edicao ->
Categoria -> Fase -> Evento, locais reutilizaveis com espacos internos e o
estado literal `LOCAL_A_DEFINIR`. O formato `HEAD_TO_HEAD` foi preservado.

O1 seleciona somente competicoes retornadas pelo servidor e autorizadas por
nome. O2 usa capabilities fechadas, bootstrap exclusivo de administrador geral,
catalogo escopado, optimistic locking e conflito HTTP 409. Nenhum UUID ou
`scopeRef` digitavel funciona como autoridade.

## Entrega tecnica

- V10 criou as tabelas de esporte, categoria, fase, local, espaco interno e
  vinculacoes de evento, com FKs para a hierarquia e regras de unicidade.
- Comandos de catalogo derivam a autoridade do recurso persistido e validam o
  escopo antes de gravar; versoes base impedem overwrite silencioso.
- Capabilities distinguem administrador geral, operador escopado e leituras
  publicas; bootstrap so aparece apos capability valida e confirmacao de zero
  competicoes pelo backend.
- O contrato OpenAPI foi produzido como candidato, aprovado pelo digest
  `21C94593F724A3478B866C4F0F4402B0FACA4EE92FD265A9B9CDEDD6BE5E5F37` e
  consumido somente depois da geracao governada. Os hashes governados antes e
  depois permaneceram iguais em
  `0B631F67A415BEE7BC95ECF209BD240C922A93ED47B69385B969B093D464AB95`.
- O ConsoleShell, o store de escopo e o catalogo operacional foram entregues
  em Angular standalone, zoneless e Signals, sem React ou nova dependencia
  visual.
- A Arena publica recebeu filtros reais de esporte, categoria, fase e estado
  do local, mantendo URL compartilhavel e o estado `LOCAL_A_DEFINIR`.

## Validacao

- Backend focal anterior da implementacao: ITs de catalogo, capabilities e
  Flyway aprovados; suite registrada com 24 testes de integracao e 251 testes
  unitarios, sem falhas.
- Prova focal Angular: 19/19.
- Suite frontend completa: 248/248 em 26 arquivos.
- `generate-api --check`: aprovado.
- Testes Node do gerador: 72/72.
- Build development: aprovado.
- Build production: aprovado, com avisos de budget de estilos ja existentes e
  o novo catalogo acima do limite de 4 kB; nenhum erro de compilacao.
- Playwright painel operacional: 1/1 apos mock autorizado de
  `GET /api/v1/profile/me` no setup do teste.
- Playwright Arena esportiva: 2/2.
- `git diff --check`: aprovado; os avisos LF -> CRLF sao efeito da configuracao
  do checkout Windows e nao falhas de conteudo.

## Ratificacao de escopo

Durante a reconciliacao foi identificado que a implementacao precisava tocar
alguns paths que nao estavam enumerados na allowlist original do plano. A
expansao foi apresentada ao responsavel e autorizada em 2026-08-29. Ela cobre:

- `backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerController.java`
- `backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerModels.java`
- `backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerRepository.java`
- `backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerService.java`
- `backend/src/test/java/br/com/betufu/platform/operations/OperationalCapabilitiesResponseTest.java`
- modelos e servicos gerados adicionais exigidos pelos schemas e filtros
  publicados no OpenAPI
- `frontend/src/app/features/sports/public-agenda/public-agenda-page.component.html`
- `frontend/e2e/operations-panel.spec.ts`, que recebeu somente o mock de perfil
  necessario para atravessar a guard de completude.

Esses paths sustentam a mesma fatia funcional V10/O1/O2 e nao introduzem regra
de negocio independente. O teste E2E foi a unica correcao feita apos o
checkpoint do candidato.

## Estado Git e proximo gate

- Worktree: `C:/Users/giova/Desktop/BetUFU-worktree-agent-03-04`.
- Branch: `worktree-agent-03-04`.
- Base preservada: `master` e `origin/master` em `357ecbc`.
- Nenhum stage, commit, integracao, limpeza ou push foi executado.
- O resumo esta pronto para revisao e para uma autorizacao Git especifica.
