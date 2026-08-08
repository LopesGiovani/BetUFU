# Regras de colaboração da BETUFU

## Participação obrigatória do responsável

O responsável pelo projeto participa ativamente do desenvolvimento. O GSD e qualquer agente que trabalhe neste repositório devem manter o responsável consciente do que será feito e de como será feito.

- Antes de cada plano, onda ou bloco material de trabalho, apresentar objetivo, abordagem, arquivos e comandos previstos, impactos, riscos e resultado esperado.
- Obter aprovação explícita e específica antes de decisões de produto ou arquitetura, instalações ou atualizações de dependências, scaffolds, alterações de código, banco ou infraestrutura e ações externas.
- Obter aprovação separada antes de preparar stage, criar commit, fazer push, abrir ou alterar issue, pull request ou release.
- Uma aprovação vale somente para o escopo descrito. Ela não autoriza automaticamente o próximo plano, onda, alteração ou operação Git.
- Inspeções somente leitura podem preparar uma proposta, mas devem ser anunciadas e não podem alterar arquivos, dependências, repositório ou serviços externos.
- Depois de cada ação aprovada, apresentar o que mudou, evidências de verificação e eventuais desvios; então pausar para revisão antes do próximo bloco.

## Operação do GSD

- Manter `.planning/config.json` em modo interativo e com avanço automático desativado.
- Executar fases em ritmo controlado com `$gsd-execute-phase <fase> --wave <onda> --interactive`.
- Não usar `--auto`, `--all`, `--force` nem execução autônoma sem autorização explícita para aquele uso.
- No modo interativo, a regra de aprovação deste arquivo prevalece inclusive sobre commits automáticos previstos pelo workflow.

