---
phase: 03-experiencia-esportiva-do-piloto
plan: 05
wave: 6
status: complete-pending-git
completed: 2026-08-30
---

# Plano 03-05 - Resumo

## Resultado

O plano entregou organizacoes permanentes, equipes vinculadas a edicao,
modalidade e categoria, favorito por organizacao e a superficie operacional O3.
A migration V11 e one-way, preserva o perfil existente quando o mapeamento e
inequivoco e deixa o favorito nulo quando nao ha relacao segura. Nenhuma regra
foi inferida por nome, slug, UUID livre ou scopeRef digitavel.

O perfil agora publica `favoriteOrganizationId` nullable e nao publica mais
`favoriteTeamId`. O3 deriva o escopo do recurso persistido, oferece preview e
publicacao, trata conflitos de versao com HTTP 409 e mantem a separacao entre
Console operacional e Arena publica. Paginas publicas completas de organizacao,
equipe e atleta continuam reservadas ao plano 03-11.

## Entrega tecnica

- V11 criou organizacoes permanentes, equipes por edicao/modalidade/categoria,
  FKs para a hierarquia esportiva e unicidades para evitar duplicatas no mesmo
  escopo.
- Comandos de equipe derivam `competitionId` pela edicao persistida e recusam
  acesso entre escopos; optimistic locking impede overwrite silencioso.
- Perfil e favorito usam somente `organizationId` validado; o estado vazio e
  honesto quando nao existe organizacao publicada.
- O candidato OpenAPI foi aprovado pelo responsavel com o digest
  `12B2982C70B98D02BD6609BA95AAE350E2BF65999E57F5D16183B65DABBA7201`.
  Os hashes governados before/after permaneceram iguais em
  `21C94593F724A3478B866C4F0F4402B0FACA4EE92FD265A9B9CDEDD6BE5E5F37`.
- O cliente Angular foi gerado somente depois da aprovacao; `generate-api
  --check` confirmou a equivalencia.
- O guard Node foi reconciliado com a Arena lazy e os shells do Console,
  mantendo imports e rotas fechados. A fixture E2E do painel recebeu apenas o
  novo campo nullable exigido pelo contrato V11.

## Validacao

- Node completo: `625/625`, incluindo mutacoes negativas do guard.
- Maven completo: `350/350`, `BUILD SUCCESS`; Flyway aplicou V11 e os ITs de
  organizacao, perfil e concorrencia passaram.
- `npm --prefix frontend run generate:api:check`: aprovado.
- Angular completo: `248/253`; as cinco falhas restantes estao somente em
  `role-admin-page.component.spec.ts`, fora da allowlist deste plano e sem
  alteracao correspondente nesta worktree.
- Build Angular development: aprovado.
- Build Angular production: aprovado; permanecem avisos de budget SCSS,
  inclusive catalogo e agenda acima do limite informativo de 4 kB.
- Playwright Arena: `2/2` em desktop e 320 px.
- Playwright Console operacional: `1/1` apos a fixture de perfil V11.
- `git diff --check`: aprovado; avisos LF -> CRLF sao efeito do checkout
  Windows.

## Ratificacao de escopo

O responsavel aprovou todas as tasks do 03-05, o candidato OpenAPI e a
ampliacao para perfil, rotas e guard Node. Durante a certificacao, autorizou
explicitamente a atualizacao minima de `frontend/e2e/operations-panel.spec.ts`
para incluir `favoriteOrganizationId: null`, pois a fixture antiga impedia a
rota de atravessar a validacao estrita do perfil. O path foi acrescentado a
allowlist do plano; nenhum outro E2E foi alterado.

## Riscos e pendencias

- As cinco falhas Angular do administrador de papeis continuam uma lacuna
  historica fora deste plano; nao foram mascaradas nem corrigidas por inferencia.
- Os avisos de Logback/Mockito, teardown Redis/Hikari e budgets SCSS nao
  invalidaram as provas, mas merecem acompanhamento antes da ampliacao visual.
- A allowlist contem 52 paths do plano, incluindo quatro metadados candidatos
  ignorados pelo Git; 47 paths reais foram reconciliados, e o proprio PLAN e um
  artefato documental esperado da formalizacao.

## Estado Git e proximo gate

- Worktree: `C:/Users/giova/Desktop/BetUFU-worktree-agent-03-05`.
- Branch: `worktree-agent-03-05`.
- Base preservada: `master` e `origin/master` em `72d8854`.
- Nenhum stage, commit, integracao, limpeza ou push foi executado.
- Proximo gate: congelar HEAD/status/hashes para revisao Codex somente leitura;
  depois executar os commits tecnico e documental autorizados, integrar e
  limpar somente a branch/worktree exatas. Push continua nao autorizado.
