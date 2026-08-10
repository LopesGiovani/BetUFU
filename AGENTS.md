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

