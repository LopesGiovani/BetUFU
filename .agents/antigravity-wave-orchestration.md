# Orquestração de ondas com Antigravity

Este runbook define como a BETUFU usa Gemini 3.7 para implementação paralela e
Codex para revisão por onda, preservando os gates humanos de `AGENTS.md`.

## Papéis e isolamento

O Codex/GSD é o plano de controle: apresenta o escopo, solicita aprovações,
cria worktrees, executa verificações e congela o snapshot para revisão. Cada
processo Gemini é um worker de uma única task e escreve somente em sua worktree.
Nunca execute dois workers no mesmo checkout.

O worker recebe `AGENTS.md`, o `PLAN.md`, a task e sua allowlist. A
invocação usa `agy`, modelo `gemini-3.7-flash-high`, `accept-edits`, sandbox e
resposta estruturada. Git, instalações, rede, Docker, Testcontainers, suítes
agregadas e decisões de produto ou arquitetura ficam proibidos no prompt. Um
worker pode executar no máximo dois testes focais rápidos quando cada comando
for literal do `<verify>` e tiver sido aprovado para aquela chamada por
`--worker-test`. Sem essa flag, terminal e testes permanecem proibidos. A prova
do worker ajuda o desenvolvimento; somente a repetição pelo orquestrador vale
como certificação.

## Procedimento detalhado da onda

1. Use `$gsd-execute-phase <fase> --wave <onda> --interactive` somente para preparar
   o briefing. Apresente objetivos, planos, arquivos, comandos, impactos e
   riscos; aguarde aprovação específica da onda. Ele não despacha implementação;
   toda execução material passa pelo `node .agents/agy-wave.mjs`.
2. Gere e revise o dry-run:

   ```bash
   node .agents/agy-wave.mjs --phase 2 --wave 1 --dry-run
   ```

3. Crie uma worktree manifestada por plano. O caminho deve ficar sob a raiz
   aprovada de workspaces e a branch temporária deve nascer do mesmo SHA base.
4. Depois de obter aprovação específica para a chamada externa, execute uma
   única task informando a raiz de confinamento, worktree, branch e SHA exatos:

   ```powershell
   node .agents/agy-wave.mjs `
     --phase 2 --wave 1 --execute-task --task 02-01-T1 `
     --workspace-root C:\Users\giova\gsd-workspaces\phase-02 `
     --worktree C:\Users\giova\gsd-workspaces\phase-02\02-01 `
     --expected-base <SHA_COMPLETO> `
     --expected-branch worktree-agent-02-01 `
     --worker-test "node --test scripts/validador-focal.test.mjs"
   ```

   `--worker-test` é repetível no máximo duas vezes, aceita somente classes
   rápidas previstas pelo adaptador, deve coincidir byte a byte com um
   `<automated>` da task e limita cada comando a 120 segundos. Maven completo,
   build Angular, Docker, curingas, composição de shell e comandos fora do
   `<verify>` são recusados. Dependências anteriores exigem flags repetíveis `--satisfied-plan`,
   `--satisfied-task` e `--evidence`; declarar a flag não substitui a prova
   apresentada ao responsável. Checkpoints humanos são sempre recusados.
5. Dispare no máximo três workers Gemini. Tasks do mesmo plano são sequenciais;
   dependências e checkpoints permanecem bloqueantes.
6. Após cada retorno, compare arquivos modificados e não rastreados com
   `files_modified` do plano e `<files>` da task. Qualquer path extra interrompe
   a onda. Execute os testes focais da task em uma única fila de verificação.
7. Apresente diff e evidências. Peça aprovações separadas para stage, commit e
   integração. Nenhuma dessas ações é implícita na aprovação de implementação.
8. Na branch de integração da fase, execute obrigatoriamente:

   ```bash
   node scripts/maven.mjs -f backend/pom.xml verify
   node --test scripts/*.test.mjs
   ```

9. Congele HEAD, refs, status e hashes. Um novo Codex revisa somente leitura o
   intervalo entre a base e o HEAD da onda. P0, P1 ou P2 devolvem a onda ao
   executor; qualquer correção invalida o parecer anterior e exige nova revisão.
10. Prepare os revisores especializados com o coordenador read-only:

   ```js
   import { prepareReview } from './.agents/agy-review.mjs';

   const prepared = await prepareReview({
     projectRoot,
     worktree,
     workspaceRoot,
     reviewType: 'verification', // security, ui ou verification
     planId,
     expectedBase,
     expectedHead,
     evidence,
     executionResult,
   }, { runProcess });
   ```

   `prepareReview` captura o snapshot, verifica worktree/HEAD/ancestralidade,
   allowlist, diff-check, evidências, agregados e contrato OpenAPI. Gates
   ausentes ficam `BLOCKED`; eles não são convertidos em sucesso por omissão.
   O perfil selecionado é sempre `plan` + `readonly` e devolve somente o
   schema `.agents/agy-review-result.schema.json`.

   O mesmo preflight pode ser executado pela CLI, sem chamada externa:

   ```powershell
   node .agents/agy-review.mjs `
     --prepare --reviewer verification --plan 03-14 `
     --worktree C:\Users\giova\gsd-workspaces\phase-03\03-14 `
     --workspace-root C:\Users\giova\gsd-workspaces\phase-03 `
     --expected-base <SHA_COMPLETO> --expected-head <SHA_COMPLETO> `
     --evidence-file <evidence.json> --execution-result-file <result.json>
   ```

   Troque `verification` por `security` ou `ui` conforme o risco. O modo
   `--prepare` apenas captura e avalia gates; ele nunca executa o revisor.
11. Uma chamada externa só pode ocorrer após aprovação específica para aquele
    revisor e usa `invokeReviewer({ ...options, allowExternalCall: true })`.
    Sem essa flag, o coordenador recusa a chamada. O comando gerado usa
    `--mode plan`, `--sandbox` e o schema local; o revisor não pode editar,
    executar Git mutável, instalar dependências, usar rede ou alterar a
    worktree. O resultado é rejeitado se base, HEAD, paths, gates ou findings
    divergirem do snapshot.
12. Com `PASS`, apresente o handoff e pause. A próxima onda requer nova aprovação.

## Contrato operacional S0-S9

O ciclo acima é interpretado pelos seguintes estados obrigatórios:

- **S0 Preflight:** ler `AGENTS.md`, `STATE.md`, plano ativo, predecessores,
  validações e manifesto; comparar handoff, branch, HEAD, base, dependências,
  checkpoints e allowlists. Divergência é `STALE`/`BLOCKED`.
- **S1 Briefing/aprovação:** apresentar objetivo, abordagem, arquivos, comandos,
  riscos, impacto, base SHA, branch, worktree e resultado esperado. Cada teste,
  chamada externa e operação Git exige autorização própria.
- **S2 Dry-run:** executar `node .agents/agy-wave.mjs --phase <fase> --wave <onda>
  --dry-run`; o comando apenas valida e produz JSON, sem worktree, worker, teste
  ou mutação Git.
- **S3 Isolamento:** criar uma worktree e branch neutra por plano, a partir da
  mesma base; nunca usar `master`/`main` para worker e nunca compartilhar checkout.
- **S4 Dispatch:** chamar `--execute-task` para uma única task. O worker é
  efêmero, usa somente a allowlist e devolve JSON; não faz Git mutável,
  instalações, rede, Docker, Testcontainers ou suítes agregadas.
- **S5 Auditoria:** comparar HEAD, branch, status, paths, `files_changed`,
  `tests_requested` e `tests_executed`; registrar somente JSON sanitizado em
  `.git/gsd/agy` e interromper em qualquer drift.
- **S6 Verificação:** o Codex repete focais e executa as suítes exigidas em fila
  independente; a prova do worker nunca substitui a certificação.
- **S7 Snapshot/revisão:** congelar refs, hashes, status e evidências; preparar
  revisão `plan`/`readonly`. Qualquer correção invalida o snapshot.
- **S8 Git/integração:** stage, commit, integração, testes pós-merge, cleanup e
  push são aprovações separadas.
- **S9 Handoff/pausa:** entregar handoff compacto e encerrar a sessão antes de
  20 mil tokens (teto absoluto de 25 mil). A próxima onda exige nova aprovação.

`--continue-task` é reservado à correção da mesma task com snapshot, baseline,
arquivos de correção, arquivos congelados e feedback. Não é atalho para a task
seguinte. Como o launcher atual exige worktree limpa no `--execute-task` e o
worker não cria commits, planos com múltiplas tasks materiais ficam bloqueados
após a primeira até existir um modo seguro de baseline aceita.

## Particularidade da onda 1

`02-01-T1` e `02-02-T1` podem iniciar em paralelo. `02-03-T1` só é liberada
quando `02-01-T1` estiver GREEN e A-7 estiver registrada em
`02-MEASUREMENTS.md`. As tasks seguintes permanecem sequenciais em suas lanes,
e a decisão OQ-8 pausa a execução para o responsável.

## Recuperação e limites

Logs e manifests de execução devem ficar no diretório Git comum, sem serem
versionados e sem segredos. Falha do processo, JSON inválido, drift do SHA base,
conflito de integração, teste vermelho ou saída da allowlist retém a worktree
para diagnóstico e bloqueia a onda. Nunca use `--dangerously-skip-permissions`,
`--auto`, `--all`, `--force` ou limpeza inferida por glob.

O adaptador não executa testes automaticamente. `tests_executed` deve coincidir
exatamente com os comandos liberados por `--worker-test`; `tests_requested` é
comparado com os comandos do plano e entregue ao Codex. A fila de verificação
repete os focais e continua sendo a única autorizada a executar testes materiais
e as suítes completas.
