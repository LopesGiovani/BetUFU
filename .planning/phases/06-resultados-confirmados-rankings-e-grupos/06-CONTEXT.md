# Fase 6 - Contexto Consolidado

## Estado da discussao

- Fase: `06 - Resultados Confirmados, Rankings e Grupos`
- Estado: discussao de produto consolidada; oito planos de execucao criados e em revisao estrutural.
- Baseline documental consultada: `2dcbb5d`.
- `STATE.md` foi reconciliado com o HEAD `2dcbb5d`; conferir novamente o HEAD vivo antes de stage ou commit documental.
- Alteracoes preexistentes em `docs/development.md`, `docs/local-seed.md` e `docs/manual-testing.md` devem ser preservadas.

## Objetivo da fase

Transformar resultados esportivos confirmados em liquidacoes explicaveis e recuperaveis, recompor ledger, ranking e conquistas de forma idempotente e oferecer uma competicao universitaria vitalicia e justa entre participantes verificados.

## Requisitos da fase

- Creditos e apostas: `CRED-07`, `CRED-09`, `PRED-09`, `ODDS-15`.
- Liquidacao: `SETL-01` a `SETL-11`.
- Ranking: `RANK-01` a `RANK-08`.
- Grupos: `POOL-01` a `POOL-04`.
- Conquistas: `ACHV-01` a `ACHV-03`.
- Qualidade: `TEST-04`.

## Decisoes herdadas

- Somente resultado esportivo publicado, confirmado e vigente autoriza liquidacao.
- No MVP, a mesma pessoa pode registrar, confirmar e autorizar o resultado; os atores devem continuar persistidos separadamente.
- O ledger e imutavel. Correcoes criam novos fatos e lancamentos compensatorios; nenhum fato original e editado ou excluido.
- O saldo nunca pode ficar negativo.
- Ranking: total de acertos definitivos, depois taxa de acerto; empates restantes compartilham a mesma posicao.
- Stake e odd nao multiplicam acertos.
- Anulacoes, devolucoes e cancelamentos nao contam como acerto, erro ou denominador.
- Estatisticas consideram somente palpites feitos enquanto a verificacao/matricula era valida.
- Grupos privados usam as mesmas metricas, periodo vitalicio e desempates do ranking principal.
- Notificacoes, contestacao formal, recarga, transferencia, recuperacao discricionaria, chat e comunidade permanecem fora do MVP desta fase.

## Decisoes de correcoes

### Pagamento incorreto ja gasto

1. Recuperar primeiro o saldo disponivel.
2. Registrar o restante como correcao pendente.
3. Aplicar creditos futuros a pendencias antes de libera-los para uso.
4. Bloquear somente novas apostas enquanto existir pendencia.
5. Manter perfil, saldo, historico, ranking, grupos, liquidacoes e correcoes acessiveis.
6. A pendencia persiste ate quitacao total, sem expiracao, perdao automatico ou revisao discricionaria.

### Multiplas pendencias

- Aplicar creditos em ordem FIFO, da pendencia mais antiga para a mais nova.
- Manter cada correcao individualmente auditavel.

### Ajuste administrativo

- Exigir maker-checker.
- Um administrador geral propoe e outro administrador geral distinto aprova.
- Ninguem aprova a propria proposta.
- Motivos permitidos: correcao de liquidacao; lancamento duplicado ou ausente; falha tecnica comprovada; correcao de elegibilidade.
- Exigir descricao, calculo reproduzivel e referencias aos registros internos afetados.
- Nao permitir ajuste de bonus, cortesia ou recarga.

### Visibilidade para o usuario

Exibir de forma completa, compreensivel e sanitizada:

- lancamento original;
- correcao;
- valor recuperado;
- valor pendente;
- motivo;
- registros relacionados;
- codigo de referencia.

Ocultar nomes de administradores, URLs internas e evidencias operacionais sensiveis.

### Correcao de uma correcao

- Criar novo fato e novo lancamento compensatorio.
- Repetir o maker-checker.
- Nunca editar ou excluir o fato original.

## Decisoes de ranking e matricula expirada

- O ranking principal e vitalicio no MVP.
- Somente palpites feitos durante verificacao valida alimentam as estatisticas.
- Matricula expirada ou revogada bloqueia novas apostas.
- A expiracao ou revogacao nao remove estatisticas legitimas nem a posicao no ranking.
- A interface informa claramente que a matricula atual esta inativa, sem expor detalhes privados da verificacao.
- Correcoes e reliquidacoes recompõem ranking e conquistas a partir dos fatos validos atuais.
- Um acerto invalidado deixa de contar; um resultado corrigido para acerto passa a contar.
- Fatos anteriores continuam preservados e auditaveis.
- Conquistas que deixarem de ser validas ficam marcadas como revogadas, sem desaparecer silenciosamente.

## Invariantes para o planejamento

- Toda liquidacao, devolucao, anulacao, reversao e reliquidacao deve ser idempotente e retomavel.
- Nenhuma operacao pode duplicar debito, pagamento, devolucao, conquista ou efeito de ranking.
- O ranking deve ser reconstruivel a partir de fatos de liquidacao sem alterar apostas, odds ou ledger.
- A autorizacao deve usar a versao exata do resultado esportivo publicada e confirmada.
- Estados parciais nunca podem ser apresentados como conclusao.
- Historico visivel ao usuario deve explicar calculo e movimentacoes sem expor PII ou detalhes operacionais sensiveis.
- As fronteiras Spring Modulith existentes devem ser respeitadas; consumidores usam portas publicas, nunca pacotes `internal` de outro modulo.

## Limites desta consolidacao

- Implementacao de codigo, migrations, OpenAPI ou componentes Angular.
- Aprovacao de execucao de planos, escolha de infraestrutura externa ou operacoes Git.
- Liquidacao detalhada, grupos, conquistas e telas especificas; estes temas ainda precisam de decomposicao tecnica depois da aprovacao do planejamento.
- Alteracao de `REQUIREMENTS.md` nesta etapa; roadmap e estado registram apenas planejamento.

## Planejamento criado

O planejamento executavel foi criado em oito planos (`06-01` a `06-08`) e seis ondas. A divergencia entre `STATE.md` e o HEAD vivo foi reconciliada no controle documental; a execucao permanece sujeita a aprovacao especifica por plano.
