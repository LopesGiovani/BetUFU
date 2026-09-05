# Phase 4: Mercados Pre-jogo e Odds Transparentes - Context

**Gathered:** 2026-09-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega mercados simples de confronto direto antes do jogo e odds
automaticas, explicaveis e versionadas para o piloto. Um administrador com o
escopo correto pode abrir e governar o mercado; qualquer visitante pode
consultar a odd atual, o historico, a metodologia, as entradas e o nivel de
confianca. O preco nasce de um modelo esportivo reproduzivel (Elo, com
tratamento explicito de empate quando o regulamento permitir) e de uma margem
configuravel, sem depender do volume de palpites.

O dominio continua generico para varias competicoes, edicoes, modalidades e
categorias, mas a operacao detalhada do MVP fica restrita a eventos de
confronto direto. A fase nao entrega creditos ou apostas (Phase 5),
liquidacao/reliquidacao (Phase 6), mercados de varios competidores, estatisticas
especificas por modalidade, precificacao comercial movida por exposicao real,
ou uma integracao recorrente com a raspagem da LAUFU.

</domain>

<decisions>
## Implementation Decisions

As decisoes D-95 a D-100 da Fase 3 continuam preservadas como contrato
herdado. Em especial, a convergencia visual/estrutural nao autoriza alterar
APIs, autorizacao, stores, adapters, validacoes, capabilities ou regras de
negocio. As decisoes abaixo sao o registro da discussao especifica da Fase 4.

### Modelo de forca, recencia e edicoes

- **D-101:** O rating inicial e `1500`, separado por modalidade e categoria,
  configuravel e versionado. Nao existe um rating global que misture esportes
  com escalas ou distribuicoes diferentes.
- **D-102:** Equipes provisoriamente usam `K=32`; depois de um limiar de
  experiencia passam a `K=16`. O limiar exato deve ser escolhido por
  backtest temporal e documentado pelo plano, nao inventado como uma decisao
  de produto.
- **D-103:** O processamento e cronologico e deve ordenar os eventos por
  instante esportivo canonico. Nao sera aplicada uma meia-vida mensal
  arbitraria; qualquer tratamento de recencia precisa ser reproduzivel e
  justificado pelos dados.
- **D-104:** Ao atravessar edicoes, o rating sofre regressao de 50% em direcao
  a `1500`. A regressao e versionada para que um backtest possa ser repetido.
- **D-105:** O rating so consome resultado esportivo confirmado e vigente,
  referenciado pela cadeia de versoes da Fase 3. Uma correcao futura produz
  calculo/versao posterior; nao reescreve historico nem odds ja aceitas.

### Empate, dados escassos e fontes

- **D-106:** Quando o regulamento ou a fase tornam o empate impossivel, o
  mercado usa dois resultados. Quando o empate no tempo regulamentar e
  possivel, usa tres resultados com uma extensao Davidson explicita.
- **D-107:** A tendencia de empate comeca compartilhada e conservadora entre
  pools compativeis. Especializacao por modalidade ou categoria so entra
  depois que houver amostra suficiente e evidencia de backtest.
- **D-108:** Mercados podem ser publicados desde o inicio, mesmo com poucos
  dados, mas a estimativa neutra deve trazer indicador visivel de baixa
  confianca. Baixa confianca nao libera odd manual rotineira e nao aumenta a
  margem automaticamente.
- **D-109:** O W.O. e uma vitoria administrativa sem placar esportivo. Nao se
  deve transformar um W.O. em placar sintetico (por exemplo, `20 x 0`) para
  alimentar Elo ou Davidson; ele fica fora da calibragem esportiva inicial e
  recebe tratamento de estado/settlement separado.
- **D-110:** Cancelamento, W.O. ou abandono sem resultado esportivo valido
  tornam o mercado anulavel e devolvem os creditos quando a camada de apostas
  existir. O evento cancelado nao altera o modelo.
- **D-111:** Adiamento suspende o mercado. Uma remarcacao cria uma nova versao
  do preco/mercado, preservando a versao anterior e a trilha de auditoria.

### Mercado, margem e imutabilidade

- **D-112:** O MVP usa templates simples de confronto direto e abre apenas
  mercados pre-jogo. O volume e a distribuicao das apostas dos usuarios nao
  alteram probabilidades nem odds (`ODDS-05`).
- **D-113:** A margem inicial e fixa em 5%, mas permanece configuravel,
  explicita e versionada. A aplicacao e o arredondamento precisam ser
  deterministas e auditaveis.
- **D-114:** Odds publicadas aparecem com duas casas decimais. Depois de
  publicada, a odd e imutavel; uma aposta futura referencia exatamente o
  valor/versao que o usuario viu e aceitou, sem recalculo retroativo.
- **D-115:** Probabilidade justa (antes da margem) e odd publicada sao
  registros distintos. Isso permite explicar a margem e deixa aberta uma
  evolucao posterior sem esconder o valor estatistico original.

### Governanca, explicacao e confronto direto

- **D-116:** Override e excecao, nao rotina. Antes de propor um override, o
  administrador deve corrigir a fonte ou recalcular; o mercado fica suspenso
  e a proposta inclui motivo, evidencia, comparacao com o modelo,
  probabilidades e delta visivel.
- **D-117:** O override exige dois administradores distintos, auditoria e nova
  versao imutavel. Nao pode ocorrer depois do encerramento definitivo
  (`CLOSED`/`LOCKED`) e nunca altera uma odd ja aceita.
- **D-118:** A explicacao publica tem duas camadas: um resumo simples para
  qualquer pessoa e um detalhe auditavel com algoritmo/versao, entradas,
  probabilidades sem margem, margem, arredondamento, confianca, timestamp e
  historico de alteracoes.
- **D-119:** Confrontos diretos podem aparecer na explicacao como evidencia
  contextual, mas nao recebem bonus automatico na odd do MVP. Um ajuste H2H
  so deve ser considerado apos varias edicoes e backtest que demonstre ganho
  real.

### Dados LAUFU e caminho futuro

- **D-120:** A extracao LAUFU e uma amostra exploratoria para pesquisa e
  backtests, nao uma fonte de producao nem um contrato permanente da BETUFU.
  Os artefatos nao rastreados da worktree principal nao entram nesta fase por
  inferencia e nao devem ser copiados para a worktree de planejamento.
- **D-121:** A direcao futura pode aproximar-se de sportsbooks tradicionais,
  mantendo a probabilidade justa separada e permitindo ajustes comerciais por
  mercado/exposicao somente quando houver liquidez, antifraude, auditoria e
  revisao formal de `ODDS-05`. Esse comportamento fica adiado e nao altera o
  MVP atual.

### the agent's Discretion

O agente e o plano podem escolher estruturas de dados, nomes internos,
algoritmos numericamente equivalentes, estrategia de cache e detalhes de UI
que preservem as decisoes acima, os requisitos do roadmap e os limites de
modulos. O limiar que troca `K=32` por `K=16`, a parametrizacao numerica do
Davidson e os testes de calibracao devem ser propostos com evidencia, sem
serem tratados como escolhas silenciosas do responsavel.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Produto, requisitos e limites de fase

- `.planning/PROJECT.md` - limites do MVP, margem inicial, odds automaticas,
  override excepcional, imutabilidade e separacao entre fases.
- `.planning/REQUIREMENTS.md` - `ODDS-01` a `ODDS-13` como escopo desta fase;
  `ODDS-14` pertence a Phase 5 e `ODDS-15` pertence a Phase 6.
- `.planning/ROADMAP.md` - goal, dependencia da Phase 3, success criteria e
  fronteiras da Phase 4.
- `.planning/STATE.md` - posicao atual, bloqueios herdados e continuidade da
  sessao.

### Contratos esportivos herdados

- `.planning/phases/03-experi-ncia-esportiva-do-piloto/03-CONTEXT.md` -
  hierarquia generica, fontes oficiais, elenco publicado, resultado
  confirmado, `confirmedVersionId`, W.O. e decisoes D-95 a D-100.
- `.planning/phases/03-experi-ncia-esportiva-do-piloto/03-RESULT-VERSION-DECISION.md` -
  cadeia append-only de resultados, supersessao, atores separados e
  referencia exata que a liquidacao futura devera consumir.
- `.planning/phases/03-experi-ncia-esportiva-do-piloto/03-SOURCE-AUDIT.md` -
  cobertura dos contratos de evento, fonte, resultado, correcao e auditoria.
- `.planning/phases/03-experi-ncia-esportiva-do-piloto/03-20-SUMMARY.md` -
  dependencia concluida da Wave 19/20 e preservacao de D-95 a D-100.

### Fronteiras e padroes de codigo

- `backend/src/main/java/br/com/betufu/odds/package-info.java` - modulo
  `odds` ja declarado como fronteira Spring Modulith.
- `backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventRepository.java` -
  escopo de evento, status, participantes e versoes de publicacao.
- `backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java` -
  informacao, confirmacao e correcao de resultados versionados.
- `backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultRepository.java` -
  persistencia e consulta da cadeia de resultados.
- `backend/src/main/java/br/com/betufu/audit/AuditRecorder.java` - porta de
  auditoria para operacoes governadas.
- `backend/src/main/java/br/com/betufu/identity/OperationalRole.java` -
  papel operacional e escopo de autoridade.
- `frontend/src/app/layout/console-shell.component.ts` - shell compartilhado
  para futuras superficies publicas e operacionais.
- `frontend/src/app/features/operations/operations-api.adapter.ts` - leitura
  de capabilities para conveniencia de navegacao; autorizacao permanece no
  backend.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- O modulo `br.com.betufu.odds` ja tem fronteira declarada; o planejamento
  deve encaixar o dominio sem criar ciclo com identidade, esportes ou ledger.
- Eventos e resultados esportivos ja possuem escopo, estados, fontes e cadeia
  de versoes; o motor deve consumir a versao confirmada em vez de duplicar o
  cadastro do placar.
- `AuditRecorder` e a porta existente para registrar publicacao, correcao,
  aprovacao e override sem acoplar o dominio a uma implementacao de log.
- `ConsoleShellComponent`, suas variantes e o adapter de capabilities oferecem
  uma superficie Angular compartilhada para consulta e operacao.

### Established Patterns

- Spring Modulith e testes arquiteturais mantem limites explicitos e impedem
  ciclos entre modulos.
- PostgreSQL/Flyway usa constraints, chaves de negocio e versoes; registros
  imutaveis sao corrigidos por novos fatos, nao por sobrescrita.
- REST `/api/v1`, OpenAPI e cliente Angular gerado evoluem juntos; arquivos
  gerados nao devem ser editados manualmente.
- Operacoes mutaveis usam autorizacao no servidor, concorrencia otimista,
  idempotencia quando necessaria, auditoria e testes discriminantes.
- Angular 22 zoneless usa standalone components, Signals, Reactive Forms
  tipados e capacidades do BFF apenas como filtro de navegacao.

### Integration Points

- O motor recebe evento, modalidade, categoria, equipes e a versao de
  resultado confirmada do modulo esportivo.
- O modulo de identidade fornece o administrador escopado; o cliente pode
  esconder acoes por capability, mas nao decide autorizacao.
- A consulta publica alimenta paginas de evento/agenda e a futura tela de
  mercado; Phase 5 consumira a versao exata da odd e Phase 6 consumira
  `confirmedVersionId`.
- A auditoria deve ligar calculo, publicacao, suspensao, override, aprovacao e
  mudancas de fonte sem registrar segredos ou matriculas.

</code_context>

<specifics>
## Specific Ideas

- A experiencia deve ser generica para varias competicoes e edicoes, como
  plataformas esportivas que separam campeonatos, e nao uma tela fixa da
  Olimpíada 2025/2026.
- A amostra LAUFU atualmente tem 671 partidas (326 regulares, 299 agendadas,
  46 W.O.) e 8 empates em 127 jogos de Futsal/Handebol; esses numeros ajudam
  a explorar o modelo, mas uma unica edicao incompleta nao sustenta calibracao
  definitiva.
- O W.O. nao deve aparecer como placar esportivo sintetico. A explicacao deve
  deixar claro quando um evento foi administrativo, cancelado ou suspenso.
- A prioridade do MVP e transparencia e auditabilidade; sofisticação de
  precificacao comercial fica para depois da existencia de dados, liquidez e
  controles adequados.

</specifics>

<deferred>
## Deferred Ideas

- Ajuste H2H com peso positivo na odd somente se varias edicoes e backtests
  comprovarem melhoria.
- Precificacao inspirada em sportsbook que mova o preco por exposicao,
  liquidez ou comportamento dos apostadores; exige revisao formal de
  `ODDS-05`, antifraude e governanca.
- Mercados de varios competidores, acumuladas, estatisticas especificas e
  liquidacao/reliquidacao pertencem a fases posteriores.
- Endurecimento do extrator LAUFU (cache com validade, robots.txt, identidade
  canonica, separacao de placares e testes do parser) deve ser tratado como
  trabalho de dados separado antes de qualquer uso recorrente.

None - no other ideas were accepted into this phase.

</deferred>

---

*Phase: 4-Mercados Pre-jogo e Odds Transparentes*
*Context gathered: 2026-09-02*
