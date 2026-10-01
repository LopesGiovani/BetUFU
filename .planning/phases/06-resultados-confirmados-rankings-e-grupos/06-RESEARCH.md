# Fase 6 - Pesquisa de Planejamento

## Resumo

A Fase 6 parte de um backend que ja possui resultado esportivo versionado, ledger imutavel, aceite idempotente de palpites e modulos vazios de `settlement` e `ranking`. O trabalho deve adicionar fatos de liquidacao e leituras derivadas sem editar apostas, odds, resultados ou lancamentos existentes.

## Evidencias consultadas

- `SportsResultForOddsPort` expoe resultados confirmados e vigentes por uma porta publica; implementacoes permanecem no modulo `sports`.
- `ConfirmedSportsResultForOdds` distingue `CONFIRMADO` e `CORRIGIDO` e carrega apenas a projecao esportiva necessaria.
- `CreditLedgerPort` oferece saldo, debito e compensacao, mas ainda nao modela pagamento/devolucao de liquidacao, pendencia de recuperacao ou alocacao FIFO. A V29 tambem restringe tipos, direcoes e fontes; o 06-02 deve ampliar o contrato e as constraints por V34.
- `CreditLedgerService` concentra mutacao transacional do ledger e possui complexidade media; novas regras devem entrar por servicos/portas coesos.
- `PredictionTicket` preserva odd, stake, selecao, retorno potencial, lucro e estado do palpite; a tabela `prediction` e imutavel.
- `OddsOverrideService` demonstra um padrao de maker-checker com proposta e aprovacao separadas.
- `AuditEntry` e amplamente reutilizado e e um hotspot; a extensao deve adicionar vocabulario e eventos, sem alterar fatos anteriores.
- `V27` congela fatos e cabecas de resultado esportivo; `V29` congela o ledger; `V30` congela palpites. As migrations aplicadas nao devem ser editadas.
- `ranking` e `settlement` possuem apenas `package-info.java`, portanto novas fronteiras publicas e testes de Modulith precisam ser definidos desde o primeiro plano.

## Consequencias para o desenho

1. Liquidação deve consumir portas publicas de resultado e de palpite; nenhum modulo pode importar `*.internal.*` de outro modulo.
2. Fatos de liquidacao, correcao, ranking e conquista devem ser append-only ou reconstruiveis. Reprocessamento deve usar chaves idempotentes.
3. O estado de processamento precisa ser duravel, com lease/retomada e unidades pequenas de trabalho para evitar apresentar execucao parcial como concluida.
4. O ranking deve ser uma leitura derivada das liquidacoes, nunca uma nova fonte de verdade.
5. Grupos devem delegar as mesmas metricas ao servico de ranking, sem duplicar regra de desempate.
6. Conquistas devem ser derivadas de liquidacoes e odds aceitas e possuir revogacao explicita quando uma correcao mudar o fato de origem.
7. O frontend deve consumir OpenAPI gerado; nenhum arquivo em `frontend/src/app/api/generated/` pode ser editado manualmente.
8. Autorizacao de liquidacao precisa ser um fato duravel ligado a versao confirmada exata, diferente da confirmacao esportiva e do processamento.
9. A alocacao FIFO deve ficar em componente interno compartilhado do ledger, sem injetar `CreditCorrectionService` e `CreditLedgerService` mutuamente.

## Riscos e controles

| Risco | Controle planejado |
|---|---|
| Resultado corrigido liquida duas vezes | cabeca/versionamento autoritativo, chave de unidade e testes de retry |
| Saldo negativo durante recuperacao | lock de conta, FIFO e constraints de saldo |
| Auto-aprovacao administrativa | ator proponente distinto do aprovador, teste de autorizacao |
| Ranking divergente de grupos | `RankingQueryPort` unico e testes de equivalencia |
| PII em historico publico | DTOs allowlist e testes de privacidade |
| Migration aplicada alterada | somente novas migrations V33 em diante |
| Fronteira Modulith quebrada | portas consumer-owned, ArchUnit e `ModulithArchitectureTests` |
| UI promete estado futuro | estados loading, vazio, erro, inativo, corrigido e revogado especificados |

## Estrategia de ondas

- Onda 1: contratos publicos e schema de liquidacao.
- Onda 2: motor de liquidacao idempotente e retomavel.
- Onda 3: correcoes de ledger e ranking reconstruivel.
- Onda 4: grupos privados e conquistas.
- Onda 5: contratos HTTP e UI integrada.
- Onda 6: concorrencia, Playwright e certificacao agregada.

## Contratos agora exigidos pelos planos

- `06-01` fecha enums de estado/motivo e armazena a autorizacao de versao; `06-02` separa estado de negocio do progresso operacional e exige teste de lease configuravel, expiracao e retomada.
- `06-07` fecha DTOs por allowlist e exige inventario/digest do candidato OpenAPI antes de qualquer arquivo gerado. Paths novos fora da lista precisam de reconciliacao e aprovacao especifica.
- `06-UI-SPEC.md` registra a escolha visual do responsavel: estados de correcao/revogacao explicitos e sanitizados na Arena existente. A revisao formal do contrato de UI permanece um checkpoint distinto.
- `06-08` deve provar que a correcao esportiva muda dados futuros do modelo de odds sem reescrever replays historicos, para cobrir `SETL-11`.
