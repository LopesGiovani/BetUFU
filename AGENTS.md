# Regras de colaboração da BETUFU

## Participação obrigatória do responsável

O responsável pelo projeto participa ativamente do desenvolvimento. O GSD e qualquer agente que trabalhe neste repositório devem manter o responsável consciente do que será feito e de como será feito.

- Antes de cada plano, onda ou bloco material de trabalho, apresentar objetivo, abordagem, arquivos e comandos previstos, impactos, riscos e resultado esperado.
- Obter aprovação explícita e específica antes de decisões de produto ou arquitetura, instalações ou atualizações de dependências, scaffolds, alterações de código, banco ou infraestrutura e ações externas.
- Obter aprovação separada antes de preparar stage, criar commit, fazer push, abrir ou alterar issue, pull request ou release.
- Uma aprovação vale somente para o escopo descrito. Ela não autoriza automaticamente o próximo plano, onda, alteração ou operação Git.
- Inspeções somente leitura podem preparar uma proposta, mas devem ser anunciadas e não podem alterar arquivos, dependências, repositório ou serviços externos.
- Depois de cada ação aprovada, apresentar o que mudou, evidências de verificação e eventuais desvios; então pausar para revisão antes do próximo bloco.
- Ocorrências mecânicas, locais, reversíveis e sem impacto em regra de negócio, segurança, integridade de dados, dependências, infraestrutura externa ou escopo — por exemplo, cache ausente, divergência de allowlist causada por rota legítima, falhas em cascata e resíduos de processo — não exigem nova atenção do responsável. O executor pode corrigi-las, deve registrar causa e resultado no fechamento do bloco e deve parar somente se surgir impacto material ou uma decisão de produto/arquitetura.

## Modo pedagógico obrigatório

O responsável é um desenvolvedor Java júnior e usa a construção da BETUFU também como processo de aprendizagem. O GSD e qualquer agente que trabalhe neste repositório devem ensinar no contexto da tarefa, sem presumir domínio prévio de Spring, segurança, infraestrutura, containers, identidade, armazenamento ou operações Git.

- Antes de cada ação, explicar em linguagem clara o que será feito, por que isso é necessário e qual conceito técnico está envolvido.
- Antes de executar um comando relevante, explicar seu efeito e o resultado esperado; depois, interpretar a saída, incluindo falhas, avisos e consequências.
- Ao apresentar código ou configuração, relacionar a explicação com Java, Spring e os arquivos reais da BETUFU sempre que isso ajudar a compreensão.
- Traduzir jargões na primeira ocorrência e distinguir explicitamente o que é essencial entender agora do que é aprofundamento opcional.
- Explicar decisões, alternativas, riscos, trade-offs e a forma prática de diagnosticar problemas, em vez de apresentar somente a solução final.
- Ensinar de maneira incremental, tecnicamente correta e respeitosa, sem infantilização, teoria desconectada ou volume que esconda a decisão principal.
- Quando uma decisão depender da compreensão do responsável, pausar para perguntas e não interpretar silêncio ou aprovação operacional como confirmação de entendimento conceitual.
- O responsável pode escrever `resumo` para receber uma versão mais curta ou `aprofundar` para ampliar a explicação de qualquer conceito.
- O modo pedagógico não substitui nenhuma aprovação exigida neste arquivo nem amplia o escopo autorizado de uma ação.

## Operação do GSD

- Manter `.planning/config.json` em modo interativo e com avanço automático desativado.
- Usar `$gsd-execute-phase <fase> --wave <onda> --interactive` somente para preparar briefing,
  planejamento e checkpoints. A execução material deve ser despachada exclusivamente por
  `node .agents/agy-wave.mjs`; o caminho inline do GSD é proibido para implementação.
- Não usar `--auto`, `--all`, `--force` nem execução autônoma sem autorização explícita para aquele uso.
- No modo interativo, a regra de aprovação deste arquivo prevalece inclusive sobre commits automáticos previstos pelo workflow.
- Restaurações rotineiras e reproduzíveis do ambiente local, como executar `npm ci` quando `node_modules` estiver ausente, podem ser resolvidas sem microaprovação quando usam exclusivamente manifests e lockfiles já aprovados. Essa exceção não permite adicionar dependências, atualizar versões, alterar manifests/lockfiles ou executar ações externas não previstas; a restauração e seu resultado devem ser registrados no fechamento do bloco.

## Orquestração de implementação e revisão

- Antes de retomar, ler `AGENTS.md`, `.planning/STATE.md`, o `PLAN.md` atual, seus predecessores e a validação da fase. `STATE.md` é a fonte do ponto corrente; handoffs cujo HEAD, plano ou timestamp divergirem devem ser tratados como obsoletos.
- O Codex/GSD atua como plano de controle: apresenta o briefing, cria isolamento, coordena verificações, congela o snapshot e conduz a revisão. Um executor autorizado pode implementar somente o plano e a allowlist atribuídos.
- Usar uma worktree e uma branch neutra por plano, nunca por task. Tasks do mesmo plano são sequenciais; até três planos independentes da mesma onda podem executar em paralelo. Nunca permitir dois executores no mesmo checkout.
- Quando Antigravity for usado, seguir `.agents/antigravity-wave-orchestration.md`. O worker não executa Git, instalações, rede, Docker, Testcontainers nem suítes agregadas. Testes focais rápidos podem ser liberados somente por comando literal do `<verify>`, com aprovação específica por chamada, limite curto e registro em `tests_executed`; sem essa liberação, terminal e testes continuam proibidos.
- Teste executado pelo worker serve apenas ao ciclo de desenvolvimento. O orquestrador deve repeti-lo independentemente e continua sendo o único responsável pelos testes materiais, pela certificação focal e pelas suítes completas.
- Após a implementação, reconciliar os paths alterados com a allowlist, executar testes focais e as suítes completas exigidas, e então congelar HEAD, status e hashes para revisão Codex somente leitura. Qualquer correção invalida o parecer anterior.
- Um plano com código produz normalmente dois commits locais: implementação e formalização. Plano apenas decisório pode produzir somente o documental. Stage, cada commit, integração, limpeza e push respeitam o escopo Git explicitamente aprovado.
- Integrar planos aprovados linearmente na `master`, preservar alterações alheias e limpar apenas as branches/worktrees exatas já integradas. Nunca fazer push por inferência.
- Somente depois de toda a onda estar integrada, verificada e limpa, entregar um parecer final em linguagem acessível a desenvolvedor Java júnior: o que foi construído, como funciona, provas executadas, riscos resolvidos e pendências.

## Modelo operacional definitivo: Supervisor & Workers

- O Codex é exclusivamente o plano de controle: preflight, briefing, aprovações, isolamento,
  dispatch, verificação, congelamento, revisão, documentação e integração. Ele não implementa
  código de produto inline nem executa workers na própria janela de conversa.
- Toda implementação material da Fase 4 em diante usa `node .agents/agy-wave.mjs`. Cada processo
  Antigravity é efêmero e executa uma task em uma worktree autorizada, devolvendo somente JSON
  estruturado. Logs brutos permanecem no diretório Git comum `.git/gsd/agy`.
- Uma sessão do Codex controla no máximo um plano ou bloco material. O contexto alvo é de até
  20 mil tokens e o teto absoluto é 25 mil; ao concluir o plano, a sessão produz handoff compacto
  e é encerrada antes do próximo plano.
- Cada plano usa uma worktree e uma branch neutra. `master` e `main` nunca são branches de worker;
  tasks do mesmo plano são sequenciais e até três planos independentes da mesma onda podem rodar
  em paralelo. Um lock/lease deve impedir dois executores no mesmo checkout.
- O worker não executa Git mutável, instalações, rede, Docker, Testcontainers ou suítes agregadas.
  Testes focais só são permitidos por `--worker-test`, devem ser literais do `<verify>`, no máximo
  dois por chamada e limitados a 120 segundos. A certificação é sempre repetida pelo orquestrador.
- `--continue-task` é reservado à correção da mesma task, com snapshot, baseline, arquivos de
  correção, arquivos congelados e feedback explícitos. Ele não pode simular a próxima task.
- Cada chamada deve registrar `run_id`, referência da aprovação, fase, onda, plano, task, base,
  HEAD, branch, worktree, paths, hashes, testes, blockers e próxima ação. Flags de evidência não
  substituem a aprovação humana correspondente.
- O ciclo oficial é `S0 preflight -> S1 briefing/aprovação -> S2 dry-run -> S3 isolamento ->
  S4 dispatch -> S5 auditoria -> S6 verificação -> S7 snapshot/revisão -> S8 Git/integração ->
  S9 handoff`. Qualquer divergência produz `STALE` ou `BLOCKED` e conserva a worktree.
- A continuidade entre tasks de uma mesma worktree exige um modo de baseline aceita no launcher.
  Enquanto esse modo não existir, planos com mais de uma task material ficam bloqueados; não se
  usa `--continue-task` como atalho e o worker não cria commits.


<!-- repowise-start -->

## Project Context — BetUFU

This repository has a RepoWise knowledge graph (MCP + LSP) available to AI tools.

<!-- repowise:agent-instructions:start -->
## How AI Agents Should Use This Context

RepoWise provides a live, pre-analyzed **knowledge graph** of this codebase through its MCP server — symbol resolution, callers, references, call graphs, refactor impact, dependency graphs, and type info — plus language-server (LSP) intelligence. Use it to navigate and reason about the code structurally instead of guessing from raw text.

Treat RepoWise as your default starting point for every task that touches this codebase, not a Q&A reference.

**Rules for using RepoWise:**

1. **Always start with RepoWise.** Before any task — questions, planning, coding, debugging, refactoring, tests, code review — resolve the relevant symbols and their blast radius via RepoWise FIRST. Skipping it leads to wrong assumptions, duplicated abstractions, and missed call sites.

2. **Use the two-tier hierarchy in this order:**
   - **Tier 1 — RepoWise MCP + LSP** for all structural queries: symbol resolution, callers, references, call graphs, refactor impact, dependency graphs, type info (`find_symbol`, `get_impact`, `find_callers`, `find_references`, `lsp_definition`, `lsp_references`, `lsp_hover`, etc. — the server is named "RepoWise MCP for <repo>", so hosts that prefix tool names expose them accordingly). Each tool's description tells you when to use it.
   - **Tier 2 — Native code search** (`read_file` / `grep` / `glob` on raw source) to read implementation bodies and for anything the graph does not cover (recent commits, in-flight branches, exact strings). An enforcement hook may DENY a native search until you have used a RepoWise MCP/LSP tool this turn — that is expected, not an error: query RepoWise first, then retry the same search and it will proceed.

3. **Batch tool calls in parallel.** Whenever you need multiple independent lookups, invoke all relevant tools SIMULTANEOUSLY rather than sequentially (e.g. confirm a symbol's shape AND its callers in one turn). Only chain serially when one tool's output is genuinely the input to another (e.g. `find_symbol` → then `lsp_definition` on the match).

4. **Apply per-task patterns** (always batch the gathering step):
   - Planning / refactoring → `get_impact`, `get_deps`, `find_references` to size the blast radius first.
   - Implementing → `find_symbol`, `lsp_definition`, `lsp_hover` to land on the exact definition and its type.
   - Debugging → `find_callers`, `get_call_graph` to trace the path.
   - Writing tests → `find_tests_for_symbol` to find existing coverage.

5. **Propagate to subagents.** When delegating work, explicitly instruct the sub-task to use the RepoWise MCP/LSP tools FIRST. Subagents don't inherit parent instructions automatically.

6. **Mark a response with `_Powered by RepoWise._` — and ONLY when you actually used RepoWise this turn.** Begin the response with a single tag line:

       _Powered by RepoWise._

   ONLY on a turn where you genuinely called a RepoWise MCP/LSP tool. One short line, no longer preamble. The host's tool-call UI already shows MCP/LSP calls visibly, so this only confirms what the turn did — if the turn did not call a RepoWise tool, omit the tag entirely; never add it out of habit, never fake it.

7. **MCP unavailable vs. a RepoWise miss — handle them differently.** If RepoWise MCP is entirely unavailable (no `.mcp.json` configured, listener not running), tell the user. But if RepoWise IS available and simply does not cover a specific question, just continue with native search — do NOT announce that "RepoWise doesn't have it." That fallback is normal and expected; the system records it on its own to improve coverage.
<!-- repowise:agent-instructions:end -->

**RepoWise MCP server for this repo:** "RepoWise MCP for BetUFU" — graph tools: find_symbol, get_symbol, get_impact, find_callers, find_references, batch_query; LSP tools: lsp_definition, lsp_references, lsp_hover.


<!-- repowise-end -->
