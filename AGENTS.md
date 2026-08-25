# Regras de colaboração da BETUFU

## Participação obrigatória do responsável

O responsável pelo projeto participa ativamente do desenvolvimento. O GSD e qualquer agente que trabalhe neste repositório devem manter o responsável consciente do que será feito e de como será feito.

- Antes de cada plano, onda ou bloco material de trabalho, apresentar objetivo, abordagem, arquivos e comandos previstos, impactos, riscos e resultado esperado.
- Obter aprovação explícita e específica antes de decisões de produto ou arquitetura, instalações ou atualizações de dependências, scaffolds, alterações de código, banco ou infraestrutura e ações externas.
- Obter aprovação separada antes de preparar stage, criar commit, fazer push, abrir ou alterar issue, pull request ou release.
- Uma aprovação vale somente para o escopo descrito. Ela não autoriza automaticamente o próximo plano, onda, alteração ou operação Git.
- Inspeções somente leitura podem preparar uma proposta, mas devem ser anunciadas e não podem alterar arquivos, dependências, repositório ou serviços externos.
- Depois de cada ação aprovada, apresentar o que mudou, evidências de verificação e eventuais desvios; então pausar para revisão antes do próximo bloco.

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
- Executar fases em ritmo controlado com `$gsd-execute-phase <fase> --wave <onda> --interactive`.
- Não usar `--auto`, `--all`, `--force` nem execução autônoma sem autorização explícita para aquele uso.
- No modo interativo, a regra de aprovação deste arquivo prevalece inclusive sobre commits automáticos previstos pelo workflow.

## Orquestração de implementação e revisão

- Antes de retomar, ler `CLAUDE.md`, `.planning/STATE.md`, o `PLAN.md` atual, seus predecessores e a validação da fase. `STATE.md` é a fonte do ponto corrente; handoffs cujo HEAD, plano ou timestamp divergirem devem ser tratados como obsoletos.
- O Codex/GSD atua como plano de controle: apresenta o briefing, cria isolamento, coordena verificações, congela o snapshot e conduz a revisão. Gemini via Antigravity ou Claude em modo EXECUTOR podem implementar somente o plano e a allowlist atribuídos.
- Usar uma worktree e uma branch neutra por plano, nunca por task. Tasks do mesmo plano são sequenciais; até três planos independentes da mesma onda podem executar em paralelo. Nunca permitir dois executores no mesmo checkout.
- Quando Antigravity for usado, seguir `.agents/antigravity-wave-orchestration.md`. O worker não executa Git, instalações, rede, Docker, Testcontainers nem suítes agregadas. Testes focais rápidos podem ser liberados somente por comando literal do `<verify>`, com aprovação específica por chamada, limite curto e registro em `tests_executed`; sem essa liberação, terminal e testes continuam proibidos.
- Teste executado pelo worker serve apenas ao ciclo de desenvolvimento. O orquestrador deve repeti-lo independentemente e continua sendo o único responsável pelos testes materiais, pela certificação focal e pelas suítes completas.
- Após a implementação, reconciliar os paths alterados com a allowlist, executar testes focais e as suítes completas exigidas, e então congelar HEAD, status e hashes para revisão Codex somente leitura. Qualquer correção invalida o parecer anterior.
- Um plano com código produz normalmente dois commits locais: implementação e formalização. Plano apenas decisório pode produzir somente o documental. Stage, cada commit, integração, limpeza e push respeitam o escopo Git explicitamente aprovado.
- Integrar planos aprovados linearmente na `master`, preservar alterações alheias e limpar apenas as branches/worktrees exatas já integradas. Nunca fazer push por inferência.
- Somente depois de toda a onda estar integrada, verificada e limpa, entregar um parecer final em linguagem acessível a desenvolvedor Java júnior: o que foi construído, como funciona, provas executadas, riscos resolvidos e pendências.

