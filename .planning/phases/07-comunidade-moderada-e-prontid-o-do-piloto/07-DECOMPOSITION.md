---
phase: 07
status: proposed-awaiting-review
created: 2026-10-04
plan_count: 35
wave_count: 17
task_count: 102
---

# Phase 7 - Revised Decomposition

A discussao incluiu o chat global no piloto e refinou moderacao, fila offline,
pausas, privacidade e recuperacao. O responsavel escolheu subdividir os planos
grandes, sem excecao ao limite de 15 paths. Esta proposta substitui a numeracao
anterior 07-01..07-10: o mesmo numero agora pode representar outra fatia.
Nenhum plano foi executado ou aprovado para implementacao por este documento.

## Dependency Waves

| Wave | Plans | Delivery |
|---|---|---|
| 1 | 07-01 | Governanca de blocos e registro de gates |
| 2 | 07-02, 07-03, 07-04, 07-28 | Preparacao juridica e institucional segregada; Pipeline real para fonte OpenAPI candidata; Canais global e por partida: schema V39; Backups automaticos completos com RPO1h |
| 3 | 07-05 | Portas publicas e leitura HTTP segura |
| 4 | 07-06, 07-07, 07-09 | Contrato gerado de leitura dos dois canais; Server push seguro e cursor de mudancas; Escrita ATIVO, limite e idempotencia: V40/backend |
| 5 | 07-08, 07-10 | Comunidade Arena e evento: tracer real de leitura; Marco definitivo e encerramento permanente: V41 |
| 6 | 07-11, 07-13 | Contrato oficial de envio e recibo; Denuncia e mute sincronizado: V42/backend |
| 7 | 07-14, 07-16 | Contrato de denuncias e mute de conta; Moderacao scoped e evidencia restrita: V43 |
| 8 | 07-17, 07-20 | Sancoes globais e revisao na conta: backend; Pausa e retomada admin-only: V44/backend |
| 9 | 07-18, 07-21, 07-24 | Contrato de moderacao com encaminhamento proposto; guards, consultas minimas e invalidacao de disponibilidade; Direitos do titular: V45/backend |
| 10 | 07-12, 07-22, 07-27 | Composer e fila volatil; contrato proposto segregando gestao admin e disponibilidade do participante; Retencao, exports e supressoes: V46 |
| 11 | 07-15, 07-25, 07-29, 07-33 | Denunciar e silenciar; contrato de Privacidade; restore; preferencia de regras backend proposta |
| 12 | 07-19, 07-34 | Console e punicoes; contrato privado gerado da preferencia de regras |
| 13 | 07-23 | Pausas operacionais e estados: tracer real |
| 14 | 07-26, 07-35 | Privacidade na conta; popup de regras e jornada multi-dispositivo |
| 15 | 07-30 | CI, publicacao e carga de referencia50 |
| 16 | 07-31 | Jornadas reais integradas e regressao do piloto |
| 17 | 07-32 | Certificacao, snapshot e parecer do responsavel |

Dependencias estao nos frontmatters; uma onda depende das ondas anteriores
indicadas, nao necessariamente de todas elas. Compartilhamento de contrato,
gerador, rotas e shell foi serializado por dependencias. Execucao preferencial
direta e inline, somente apos briefing e autorizacao especifica. Esta tabela
NAO autoriza agentes/worktrees/paralelismo; se expressamente adotados, respeitar
maximo de tres workers e allowlists sem intersecao.

## Plan Inventory

| Plan | Paths | Tasks | Requirements |
|---|---|---|---|
| 07-01 | 4 | 2 | TEST-03, UX-01, UX-02 |
| 07-02 | 6 | 2 | PRIV-03, PRIV-04, PRIV-05 |
| 07-03 | 3 | 2 | TEST-03, TEST-05 |
| 07-04 | 5 | 4 | CHAT-01, CHAT-02, RT-01 |
| 07-05 | 13 | 2 | CHAT-01, CHAT-02, RT-01 |
| 07-06 | 15 | 4 | CHAT-01, CHAT-02, TEST-03 |
| 07-07 | 9 | 2 | RT-01, CHAT-02, TEST-03 |
| 07-08 | 15 | 2 | CHAT-01, CHAT-02, RT-01, TEST-03, UX-01, UX-02 |
| 07-09 | 12 | 4 | CHAT-01, CHAT-02, RT-01 |
| 07-10 | 15 | 4 | CHAT-01, CHAT-02 |
| 07-11 | 15 | 4 | CHAT-01, TEST-03 |
| 07-12 | 9 | 2 | CHAT-01, CHAT-02, TEST-03, UX-01, UX-02 |
| 07-13 | 13 | 4 | CHAT-03, CHAT-04, CHAT-06 |
| 07-14 | 15 | 4 | CHAT-03, CHAT-04, TEST-03 |
| 07-15 | 9 | 2 | CHAT-03, CHAT-04, TEST-03, UX-01, UX-02 |
| 07-16 | 14 | 4 | CHAT-05, CHAT-06 |
| 07-17 | 12 | 2 | CHAT-05, CHAT-06, INTE-05 |
| 07-18 | 15 | 4 | CHAT-05, CHAT-06, TEST-03 |
| 07-19 | 10 | 2 | CHAT-05, CHAT-06, TEST-03, UX-01, UX-02 |
| 07-20 | 12 | 4 | INTE-05 |
| 07-21 | 15 | 2 | INTE-05, CHAT-01, TEST-03 |
| 07-22 | 15 | 4 | INTE-05, TEST-03 |
| 07-23 | 15 | 2 | INTE-05, TEST-03, UX-01, UX-02 |
| 07-24 | 12 | 4 | PRIV-05, TEST-03 |
| 07-25 | 15 | 4 | PRIV-05, TEST-03 |
| 07-26 | 10 | 2 | PRIV-05, TEST-03, UX-01, UX-02 |
| 07-27 | 14 | 4 | PRIV-05, CHAT-06 |
| 07-28 | 5 | 2 | OPS-04, TEST-03 |
| 07-29 | 6 | 2 | OPS-04, PRIV-05, TEST-03 |
| 07-30 | 12 | 2 | TEST-05, TEST-03, PRIV-03, PRIV-04, OPS-04 |
| 07-31 | 9 | 2 | CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, CHAT-06, RT-01, INTE-05, PRIV-05, TEST-03, UX-01, UX-02 |
| 07-32 | 7 | 2 | CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, CHAT-06, RT-01, INTE-05, PRIV-03, PRIV-04, PRIV-05, OPS-04, TEST-03, TEST-05, UX-01, UX-02 |
| 07-33 | 10 | 4 | CHAT-01, TEST-03, UX-01, UX-02 |
| 07-34 | 14 | 4 | CHAT-01, TEST-03, UX-01, UX-02 |
| 07-35 | 9 | 2 | CHAT-01, TEST-03, UX-01, UX-02 |

Total: 35 planos, 102 tasks, 17 ondas, no maximo 15 paths por plano.
19 planos possuem duas tasks (briefing humano + fatia). Nove planos SQL e sete
planos API possuem quatro (briefing -> candidato -> aceite humano -> publicacao).
33..35 sao adicionais propostos; storage/endpoints/falhas aguardam decisao em 33.
Nenhum aceite documental equivale a aceite da arquitetura ou execucao.
O limite conta fonte, candidatos persistentes, metadados, aprovacoes e gerados.
Inventario previsto nao substitui inventario real; qualquer arquivo adicional
exige parar e revisar/dividir a fatia antes de publicar.

## Review Reconciliation - Proposal Only

Este ajuste fecha tres lacunas documentais da revisao inline; nao autoriza
codigo, contratos governados, banco, Git ou decisao arquitetural por inferencia.
35 planos, 17 ondas e 102 tasks mantidos. Todos os contratos/paths novos abaixo
devem ser ratificados nos checkpoints dos respectivos planos.

- Encaminhamento: 16 registra fato auditavel/idempotente; 18 propoe endpoint
  escalations e cliente tipado; 19 prova moderador -> administrador pela UI.
  Encaminhar nao sanciona; terceiro/revogado/outscope nao opera.
- Disponibilidade: 20 mantem gestao admin-only e propoe GET publico exato;
  21 compoe consultas minimas pelos consumers chat/betting e invalidacao
  poscommit; 22 propoe schemas/cliente segregados; 23 bloqueia de verdade
  composer/bilhete e prova refresh/reconexao/contas A/B.
- Gate: 12/15/19/23/26/31 agora invocam --plan/--stage antes de alterar arquivos,
  alem da verificacao material. 01 conserva somente sua excecao de bootstrap.
- 31 repete ambas jornadas reais. VALIDATION acompanha comandos e evidencias
  futuras; nenhuma prova material foi executada nesta reconciliacao.
- Allowlists de 20/21/23 passam a 12/15/15. 21 inclui controllers, produtor/
  receptor de invalidacao e IT; 23 inclui store e TS/HTML/spec do bilhete.
  Se o gerador de 22 criar modelos extras pelas variantes, BLOCKED e subdivisao
  antes de publicar; nao omitir outputs nem diluir a whitelist publica.
- Schema/rotas, tipo do evento consumer-owned, replay e falha visual sao
  propostas pendentes. Preservar escolhas consolidadas, seguranca, prazo e
  facts financeiros; nao alterar UI-SPEC/RT-01 ou escolher protocolo aqui.

## Grouped Scope

- 01..08: governanca, preparacao juridica, gerador candidato, schema, leitura
  publica por partida/privada global, server push e primeira jornada de leitura.
- 09..12: escrita ATIVO, idempotencia, marco definitivo, contrato e fila volatil.
- 13..19: denuncia, mute sincronizado, moderacao escopada, sancao global somente
  administrador e revisao privada na conta.
- 20..23: pausas administrativas e guards atomicos para mensagens/novos palpites.
- 24..27: direitos na conta, respostas operacionais, exports e retencao segregada.
- 28..30: backup completo, restore com supressoes posteriores, CI e carga.
- 31..32: jornadas reais ampliadas, concorrencia/privacidade, snapshot e certificacao.
- 33..35: preferencia privada de regras/backend/API e popup multi-dispositivo;
  numeracao aditiva, ondas 11/12/14 antes da certificacao 31/32.

## Migration Inventory (Future Candidates)

- 07-04: V39__chat_channels_and_messages.sql
- 07-09: V40__chat_writing.sql
- 07-10: V41__chat_definitive_lifecycle.sql
- 07-13: V42__chat_reports_and_mutes.sql
- 07-16: V43__chat_moderation_cases.sql
- 07-20: V44__emergency_pauses.sql
- 07-24: V45__privacy_rights_cases.sql
- 07-27: V46__privacy_retention_and_suppressions.sql

- 07-33: V47__chat_rules_dismissals.sql (proposta arquitetural pendente)

V39..V47 sao propostas, nao arquivos SQL criados/aplicados. Nove schemas
separados refletem a subdivisao; nao alterar V1..V38 ou fatos financeiros.
Append-only preserva fatos, mas nao permite retencao infinita sem base juridica.

## Trade-offs And Gates

A subdivisao reduz o tamanho de cada revisao e torna contratos irreversiveis
visiveis, ao custo de mais planos, checkpoints e etapas sequenciais. Algumas
fundacoes backend/API nao sao jornadas completas: somente as fatias UI/E2E
podem provar navegacao real. Nao chamar um teste de runner de teste do navegador.

Propostas tecnicas como a extensao do gerador precisam aceite no proprio bloco;
nao foram convertidas em decisoes de produto. UI-SPEC existe como draft;
server-push foi escolhido como experiencia, nao como protocolo. RT-01/STOMP
permanece pendente. CHAT-01/05 e Out of Scope reconciliados neste bloco
documental autorizado, sem marcar requisitos entregues.

RPO <= 1h, RTO <= 4h, 50 simultaneos como referencia e bloqueio de qualquer
vulnerabilidade confirmada foram escolhidos, mas ainda nao medidos. Retencao
90/180 dias e revisao juridica, destino/chaves de backups, SLO/mix/erro de carga,
identidade institucional legitima e contrato visual continuam gates explicitos.

## Documentary Verification

Inventario atual proposto dos PLANs: 35/102/17; nenhum predecessor inexistente ou na
mesma onda/posterior; nenhuma intersecao de paths entre planos na mesma onda.
Checks de estrutura e cobertura e parecer independente devem ser registrados
em STATE apos execucao documental. Isto nao e validacao material nem autorizacao
de stage/commit/push, infraestrutura, migrations ou implementacao.

## UI Reconciliation Coverage (Current Proposal)

| Choice | Plan behavior and future evidence |
|---|---|
| UI-D01 | 08/35 preservam Arena; shell/DOM e browser desktop/320px |
| UI-D02 | 08 inclui HTML real e shell test; Arena -> Comunidade por link |
| UI-D03 | 08/35 lista compacta, metadados acima, sem baloes |
| UI-D04 | 08/35 mensagem propria discreta e rotulo Voce |
| UI-D05 | 35 dialogo fechavel/manual, Escape e retorno de foco |
| UI-D06 | 33/34/35 preferencia privada por conta, refresh e segundo contexto; 31 repete |
| UI-D07 | 08/35 visitante no jogo tem somente Regras manual; 31 repete |

Verificacao documental inline desta reconciliacao: estrutura 35/35, 102 tasks,
zero errors/warnings, allowlists <=15, mapa 102/102, nenhum predecessor invalido
ou colisao na mesma onda. Gate instalado cobre D-01..40 (40/40); UI-D01..07
verificados separadamente nesta matriz (7/7), pois o parser nao os conta.
Referencias de requisitos 16/16. Ordem V39..V47 monotona por ondas.
Nao e parecer independente, aprovacao de arquitetura, teste de negocio ou PASS
global. Parecer independente anterior abaixo continua delimitado aos 32 planos.

## Review Adjustments

A revisao delimitada encontrou dois WARNINGs, sem BLOCKER confirmado, nao um
PASS global. Adicionadas dependencias 07-10 -> 07-13 (V41 -> V42) e
07-16 -> 07-20 (V43 -> V44): ordem monotona V39..V46, sem outOfOrder.
O DAG passou a 17 ondas, mantendo 32 planos/92 tasks e nenhum conflito de paths
na mesma onda. Todos consumidores invocam validador --plan/--stage antes das
etapas materiais; bootstrap de 07-01 permanece excecao explicita. O script/CLI
continua proposta futura, nao foi implementado nesta consolidacao.

Rechecagem independente confirmou ambos WARNINGs resolvidos, sem achados
remanescentes nesses dois pontos. Escopo do parecer limitado; nao PASS global.
Verificacao documental final: estrutura 32/32, 92 tasks, referencias 16/16
requisitos e 40/40 decisoes, nenhuma colisao de paths na mesma onda. Execucao
continua pendente de revisao/autorizacao do responsavel e gates aplicaveis.
