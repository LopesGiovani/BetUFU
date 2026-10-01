# Fase 6 - Log da Discussao

## 2026-09-18 - Preflight

- A Fase 6 esta como `ready_for_planning` no `STATE.md`.
- O HEAD vivo e `2dcbb5d`; `STATE.md` ainda registra `b086901` como `state_head`.
- A pasta da fase nao existia e foi criada para receber os artefatos da discussao.
- Nenhum codigo, banco, servico, Docker, dependencia, stage, commit ou push foi alterado.

## 2026-09-18 - Area Correcoes

Decisoes aprovadas pelo responsavel:

1. Pagamento incorreto ja gasto recupera primeiro do saldo disponivel; o restante vira pendencia.
2. Creditos futuros quitam pendencias antes de ficarem disponiveis.
3. Pendencia bloqueia somente novas apostas; demais consultas e processamentos continuam acessiveis.
4. Pendencia nao expira, nao recebe perdao automatico e nao depende de revisao discricionaria.
5. Multiplas pendencias usam FIFO e permanecem individualmente auditaveis.
6. Ajuste administrativo exige maker-checker entre dois administradores gerais distintos.
7. Motivos permitidos formam lista fechada: correcao de liquidacao; lancamento duplicado ou ausente; falha tecnica comprovada; correcao de elegibilidade.
8. Ajustes exigem descricao, calculo reproduzivel e referencias internas; bonus, cortesia e recarga ficam proibidos.
9. O usuario ve detalhes completos e sanitizados, sem nomes de administradores, URLs internas ou evidencias operacionais sensiveis.
10. Correcao de correcao cria novo fato compensatorio e repete maker-checker; o fato original nunca e editado ou excluido.

## 2026-09-18 - Area Ranking e Matricula Expirada

Decisoes aprovadas pelo responsavel:

1. O ranking e vitalicio e preserva estatisticas feitas durante verificacao valida.
2. Matricula expirada ou revogada bloqueia novas apostas, mas nao apaga estatisticas legitimas nem remove a posicao.
3. A interface indica a matricula inativa sem revelar detalhes privados da verificacao.
4. Correcoes e reliquidacoes recompõem ranking e conquistas com base nos fatos validos atuais.
5. Acertos invalidados deixam de contar; resultados corrigidos para acerto passam a contar.
6. Fatos originais permanecem preservados e auditaveis.
7. Conquistas invalidadas ficam marcadas como revogadas, sem exclusao silenciosa.

## Artefatos produzidos

- `06-DISCUSS-CHECKPOINT.json`: decisoes estruturadas das areas Correcoes e Ranking.
- `06-CONTEXT.md`: contexto consolidado para o futuro planejamento.
- `06-DISCUSSION-LOG.md`: este registro cronologico.

## Proximas aprovacoes necessarias

- Aprovar a criacao dos planos executaveis da Fase 6.
- Aprovar separadamente qualquer alteracao em `ROADMAP.md`, `STATE.md`, `REQUIREMENTS.md`, codigo, banco, dependencias, stage, commit ou push.

## 2026-09-18 - Planejamento autorizado

- O responsavel autorizou a criacao dos planos executaveis e a reconciliacao de `ROADMAP.md`/`STATE.md`.
- A pesquisa registrou as fronteiras publicas existentes, migrations protegidas, riscos de ledger/auditoria e ausencia de implementacao em `settlement`/`ranking`.
- Foram criados oito planos em seis ondas: `06-01` a `06-08`.
- A execucao continua bloqueada ate aprovacoes especificas por plano; nenhum teste material, migration, Docker, stage, commit ou push foi executado.
