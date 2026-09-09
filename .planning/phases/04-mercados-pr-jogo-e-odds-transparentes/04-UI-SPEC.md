---
phase: 4
slug: mercados-pr-jogo-e-odds-transparentes
status: draft
shadcn_initialized: false
preset: none
created: 2026-09-08
reviewed_at: 2026-09-08
checker: gsd-ui-checker
---

# Fase 4 - Contrato de Design de UI

> Contrato visual e de interacao para mercados pre-jogo de confronto direto,
> consulta publica de odds e explicacao auditavel, alem da governanca
> operacional de suspensao e override. Este documento estende os contratos
> aprovados de Fases 1, 2 e 3; onde ele nao divergir, aqueles contratos valem.

## Escopo e fonte de decisoes

| Fonte | Decisao executavel usada neste contrato |
|---|---|
| `04-CONTEXT.md` D-101..D-121 | H2H pre-jogo; empate somente quando aplicavel; odds automaticas, versionadas e imutaveis; margem inicial de 5%; baixa confianca visivel; W.O./cancelamento/adiamento com tratamento separado; override excepcional, suspenso, auditado e por dois administradores distintos. |
| `REQUIREMENTS.md` ODDS-01..ODDS-13 | Superficies e estados cobrem criacao, calculo, explicacao publica, historico, correcao/recalculo, proposta, aprovacao e bloqueios CLOSED/LOCKED. ODDS-14/15 ficam fora desta fase. |
| `04-13-PLAN.md` | Superficie Arena publica: consulta anonima de mercado, odd atual, historico e explicacao, sem dados privados. |
| `04-14-PLAN.md` | Superficie Console operacional: lifecycle, tentativa de correcao/recalculo, suspensao, proposta e aprovacao maker-checker; E2E em Chromium/Firefox. |
| `03-UI-SPEC.md` | Identidade V, dois modos Arena/Console, tokens semanticos, estados explicitos, tabelas/cards responsivos e componentes standalone Angular. |
| `frontend/src/styles/_tokens.scss` | Valores vivos dos tokens BETUFU; esta especificacao nao cria uma segunda paleta. |

Fase 4 nao inclui creditos, apostas, bilhetes, settlement, reliquidacao,
mercados multi-competidor ou precificacao movida por volume/liquidez/exposicao.
Numeros, equipes, eventos, versoes e estados exibidos vem da API; fixtures sao
permitidas somente nos testes.

## Sistema de design

| Propriedade | Valor |
|---|---|
| Ferramenta | Angular 22 standalone/zoneless, HTML semantico e composicao SCSS local; nenhum pacote visual novo |
| Preset | Nao aplicavel; `components.json` ausente e shadcn nao e usado |
| Biblioteca de componentes | Componentes locais realmente presentes: `ConsoleShell`, `ArenaShell`, `ResponsiveDataView` e `ConfirmDialog`; demais blocos sao elementos HTML/SCSS da propria feature |
| Icones | Nenhuma biblioteca de icones adicional contratada; usar texto, CSS ou recurso local ja existente, sempre com nome acessivel |
| Fontes | Inter local (400/600) para interface e Barlow Condensed local (600) para headings/display; nenhuma CDN |
| Movimento | Fade/transform curto para atualizacao de dados; sem loop decorativo no Console; `prefers-reduced-motion` fixa o estado e remove transicoes |

### Tese visual da fase

- Arena apresenta o mercado como uma extensao da transmissao: confronto e
  horario primeiro, odd em destaque coral, contexto e metodologia logo abaixo.
- Console apresenta fatos e decisoes em densidade controlada: estado do mercado,
  versao, fonte, tentativa de correcao, proposta e auditoria ficam proximos do
  registro que governam.
- A transparencia e visual, nao promocional: mostrar probabilidade justa,
  margem, odd publicada, confianca e versao sem sugerir garantia de resultado.
- H2H pode aparecer como evidencia contextual, mas nunca como bonus automatico.

## Escala de espacamento

Valores declarados, sempre multiplos de 4 e derivados de `_tokens.scss`:

| Token | Valor | Uso |
|---|---:|---|
| `space-1` | 4px | lacuna icone-texto, divisores e ajustes inline |
| `space-2` | 8px | chips, metadados compactos e grupos curtos |
| `space-4` | 16px | espacamento padrao entre controles, linhas e conteudo |
| `space-6` | 24px | padding de card, grupos relacionados e painel de explicacao |
| `space-8` | 32px | padding lateral desktop e separacao de secoes |
| `space-12` | 48px | separacao de blocos publicos/operacionais |
| `space-16` | 64px | respiro de pagina e transicao entre areas |

Excecoes: alvo acionavel minimo de 44x44px; icones de 20px inline ou 24px em
controle; outline de foco de 3px com offset de 3px. Nao usar margem/gap
arbitrario fora da escala.

## Tipografia

| Papel | Fonte | Tamanho | Peso | Altura de linha | Uso |
|---|---|---:|---:|---:|---|
| Body | Inter | 16px | 400 | 1.5 | explicacoes, mensagens, contexto e valores nao numericos |
| Label | Inter | 14px | 600 | 1.3 | labels, chips, filtros, cabecalhos, versao e CTAs |
| Heading | Barlow Condensed | clamp(24px, 4vw, 56px) | 600 | 0.9-1.15 | titulo da pagina, confronto e secoes |
| Display | Barlow Condensed | clamp(76px, 13vw, 184px) | 600 | 0.72-0.82 | somente Arena/hero; nao usar no Console |

Numeros de odd, probabilidade, margem e versao usam `font-variant-numeric:
tabular-nums`; odds publicadas sempre mostram duas casas decimais. Nomes de
equipes, estados, fontes e erros quebram linha e nunca sao truncados.

## Cor

| Papel | Valor | Uso |
|---|---|---|
| Dominante (60%) | `#120B12` (`--bt-canvas`) e `#09080D` (`--bt-ink`) | fundo continuo, header e texto sobre coral |
| Secundaria (30%) | `#171018` (`--bt-surface`), `#21131D` (`--bt-surface-raised`), `#741D42` (`--bt-wine`) e `#910008` (`--bt-signal-deep`) | cards, filtros, tabelas, dialogos e faixas editoriais |
| Accent (10%) | `#FF626B` (`--bt-signal`) | odd atual selecionada, CTA primario, horario, item ativo, borda de destaque e foco adjacente |
| Accent soft | `#FFB4B8` (`--bt-signal-soft`) | metadado editorial, headings de explicacao e borda enfatizada |
| Texto | `#FFF4F4` (`--bt-text`), `#CDBFC5` (`--bt-text-muted`) | texto principal e secundario que ainda passa AA |
| Foco | `#FFD2D4` (`--bt-focus`) | outline de teclado independente do accent |
| Publicado/confirmado | `#72D6A0` (`--bt-success`) | status acompanhado de texto e icone |
| Baixa confianca/stale/adiado | `#F5C95C` (`--bt-warning`) | alerta acompanhado de texto explicativo |
| Erro/rejeicao | `#FF7A82` (`--bt-danger`) | erro, bloqueio e acao destrutiva; nunca identidade |

Accent reservado explicitamente para: odd vigente e selecao do mercado,
CTA `Ver explicacao completa`/`Abrir mercado`, CTA de correcao ou suspensao
quando permitido pela capability, horario do evento, item de navegacao ativo,
indicador de foco e destaques de leitura. Links informativos usam texto claro
com sublinhado; status usam as cores semanticas e sempre tambem palavra/icone.

Contraste minimo: 4.5:1 para texto comum, 3:1 para texto grande/grafico e
`#09080D` sobre `#FF626B` em botoes primarios. Nao comunicar confianca,
suspensao, erro ou publicacao somente por cor.

## Componentes e arquitetura de informacao

### P1 - Mercado publico (Arena)

1. **Cabecalho/contexto:** `ArenaShell` compartilhado, marca BETUFU, caminho
   de retorno para competicao/evento e acao `Entrar`; sessao nao bloqueia GET
   publico. Nao mostrar capability, escopo interno, ator ou rascunho.
2. **Resumo do confronto:** evento, competicao/edicao, modalidade/categoria,
   horario/fuso e estado do mercado. Equipes e resultado possivel sao o foco;
   empate aparece somente quando o contrato do mercado permitir.
3. **Odd atual:** cada selecao tem nome textual, probabilidade publica quando
   prevista pelo contrato, odd em duas casas, versao e timestamp. A odd
   publicada e apresentada como fato imutavel, nao como promessa ou sugestao de
   aposta (nao ha aposta nesta fase).
4. **Confianca e dados:** badge textual `Confianca baixa`, `Confianca media` ou
   `Confianca alta` conforme API, com explicacao de amostra/limite. Dados
   escassos exibem estimativa neutra e nunca controle de override.
5. **Explicacao em duas camadas:** resumo legivel fica visivel e o detalhe
   auditavel inicia fechado em um disclosure com o rotulo `Ver explicacao
   completa`; ao abrir, mostra algoritmo/versao, entradas
   permitidas, probabilidades sem margem, margem, arredondamento, confianca,
   timestamp e historico. Nunca mostrar IDs de ator, evidencia operacional,
   matricula, HMAC, payload de fonte privada ou volume de apostas.
6. **Historico:** lista cronologica de publicacoes imutaveis; cada linha mostra
   versao, odd, estado, motivo publico e data/fuso. Uma correcao cria nova linha;
   a anterior continua consultavel e marcada como anterior, nunca sobrescrita.
7. **Contexto esportivo:** H2H e fonte publicada podem ser cards secundarios
   somente como evidencia contextual. W.O., cancelamento e adiamento usam
   texto de estado (sem placar sintetico); adiamento informa que o preco sera
   versionado na remarcacao.

### P2 - Operacao de mercado (Console)

1. `ConsoleShell` variante operacional, sem Grainient/ticker/hero em loop. Menu
   e destino aparecem apenas com capability retornada pelo servidor; esconder
   menu nao substitui 401/403.
2. Workspace mostra identificacao do mercado, competencia/edicao, evento,
   lifecycle e versao atual em etiqueta de estado local; controles mutaveis ficam junto ao
   objeto e sao desabilitados em `CLOSED`/`LOCKED`.
3. **Correcao antes de override:** acao `Corrigir fonte` ou `Recalcular` abre
   formulario tipado com fonte/motivo e evidencia permitida. O resultado da
   tentativa fica em painel persistente `Tentativa registrada`; checkbox local
   nunca conta como prova. Enquanto a distorcao persiste, mostrar `Suspender
   mercado` com confirmacao contextual.
4. **Proposta de override:** somente em mercado suspenso; formulario exige
   motivo, evidencia, comparacao odd do modelo x proposta, probabilidades e
   delta visivel. Exibir aviso de excecao e nova versao esperada. Nao oferecer
   acao se mercado CLOSED/LOCKED ou se nao houver capability.
5. **Maker-checker:** a pessoa proponente ve `Aguardando segundo administrador`;
   a mesma identidade nao recebe CTA de aprovar. Segundo administrador do mesmo
   escopo ve resumo somente leitura, evidencia autorizada, delta, versao base e
   CTA `Aprovar override` ou `Rejeitar proposta`. A aprovacao cria nova versao;
   a tela confirma imutabilidade e auditoria.
6. **Concorrencia:** 401/403/409 mostram um painel de estado HTML/SCSS proximo do registro,
   preservam a leitura local para comparacao e orientam recarregar/comparar;
   nenhuma resposta de conflito sobrescreve formulario ou envia retry
   automatico. Apos perda de capability, comandos desaparecem e estado
   privilegiado nao volta ao DOM.

### Componentes e padroes obrigatorios

- Painel de estado HTML/SCSS da feature para loading, vazio, parcial, erro, nao
  autenticado, nao autorizado, stale e conflito; sucesso persistente com
  `aria-live="polite"`.
- Etiqueta de estado local sempre combina cor, icone/texto e palavra
  (`Publicado`, `Suspenso`, `Baixa confianca`, `CLOSED`, `LOCKED`).
- `ResponsiveDataView` ou tabela semantica para historico desktop; em largura
  menor que 600px converter cada versao em card com labels completas.
- Disclosure de explicacao fecha com Escape e restaura foco; `ConfirmDialog`
  existente prende foco, inicia no botao seguro e devolve foco ao gatilho.
- Reactive Forms tipados, label acima do campo, ajuda antes do erro,
  `aria-describedby` e foco no primeiro invalido apos submit.
- Cliente Angular e DTOs sao os gerados pelo contrato OpenAPI ratificado; nao
  editar `frontend/src/app/api/generated/**` manualmente.

### Matriz de requisitos e evidencia

| Requisito | Superficie/estado UI | Assertion verificavel | Evidencia esperada |
|---|---|---|---|
| ODDS-01 | Console: mercado ja criado; Arena: mercado populado | Criacao/template H2H e regra de empate pertencem ao backend e ficam fora da UI dos planos 04-13/04-14; o Console apenas exibe o mercado e seu estado quando a criacao ja ocorreu, sem inventar rota/formulario; Arena mostra selecoes reais e empate somente quando aplicavel | Spec Angular/E2E para leitura e resumo; criacao, elegibilidade e template no IT backend |
| ODDS-02 | Arena: explicacao detalhada | Exibe algoritmo e versao do calculo quando presentes no DTO; nao recalcula no browser | Contrato OpenAPI/Angular + auditoria do calculo versionado |
| ODDS-03 | Arena: detalhe e evidencia H2H | H2H e rotulado como contexto, sem bonus visual/promocional ou ajuste automatico | Browser confere texto/contexto; golden/backtest backend prova recencia/regressao |
| ODDS-04 | Arena e Console: cancelado, W.O., adiado | Estados administrativos sao textuais e nao exibem placar sintetico; mercado nao sugere recalculo indevido | E2E de estados + IT de taxonomia/modelo |
| ODDS-05 | Arena: projecao publica | Nenhum volume, distribuicao, liquidez ou exposicao de apostas aparece no DOM, URL ou rede | Inspecao DOM/network/privacy; teste de independencia no backend |
| ODDS-06 | Arena: explicacao detalhada; Console: leitura de versao | Margem e arredondamento aparecem como fatos versionados; odd mostra duas casas | Contract spec + auditoria de publicacao e golden vectors |
| ODDS-07 | Arena: baixa confianca | Badge textual, explicacao de amostra e estimativa neutra; nenhum CTA de override/aposta | E2E/Angular de baixa confianca; calibracao backend |
| ODDS-08 | Arena: detalhe/historico; Console: auditoria | Cada versao renderizada mostra algoritmo, entradas publicas, probabilidade justa, margem, arredondamento e timestamp permitidos | Contract spec, leitura de historico e auditoria append-only |
| ODDS-09 | Arena: resumo, historico e disclosure | Visitante anonimo acessa odd atual, versoes e metodologia sem sessao | Playwright anonimo em Chromium/Firefox + IT de projecao publica |
| ODDS-10 | Console: correcao/recalculo e suspensao | A ordem visual e corrigir/recalcular, registrar tentativa e somente depois suspender/propor; sem checkbox como prova | Spec de fluxo e E2E; IT de precondicao e no-write |
| ODDS-11 | Console: proposta | Formulario exige motivo/evidencia e mostra odd do modelo, proposta, probabilidades e delta lado a lado | Spec de formulario/E2E; IT de proposta persistida |
| ODDS-12 | Console: aguardando/aprovacao | Proponente nao ve aprovar; segundo administrador ve CTA contextual e confirmacao de nova versao imutavel | E2E de duas identidades; IT de escopo, ator distinto e auditoria |
| ODDS-13 | Console: suspenso vs CLOSED/LOCKED | Override disponivel somente suspenso; CLOSED/LOCKED e odd aceita ficam somente leitura com copy explicativa | E2E de estados/409; IT de bloqueio e preservacao historica |

ODDS-02..06 e parte de calculo, calibracao e persistencia; a UI apenas exibe
fatos governados e nao pode ser usada como prova unica. Browser prova composicao,
privacidade e caminho de interacao; auditoria/IT prova autorizacao,
imutabilidade, concorrencia e invariantes numericas.

## Estados e comportamento

| Estado | Arena publica | Console operacional |
|---|---|---|
| Loading | skeleton curto para resumo/odd; nao usar spinner infinito isolado | skeleton do registro; comandos nao ficam acionaveis |
| Vazio/inexistente | `Mercado ainda nao publicado` + voltar ao evento/agenda | Ver tabela de estados Console abaixo; nunca inferir criacao |
| Populado | odd atual, estado, confianca, explicacao e historico | lifecycle, versao, capability, acoes permitidas e auditoria |
| Parcial | manter blocos validos e marcar o bloco ausente; nunca preencher com zero ficticio | separar falha de leitura e bloquear apenas comandos dependentes |
| Baixa confianca | badge `Confianca baixa` + texto de estimativa neutra; sem override publico | mostrar indicador, sem liberar odd manual rotineira |
| Stale | banner `Dados podem estar desatualizados` + `Atualizar leitura`; GET pode retry manual | preservar snapshot, exigir recarga antes de mutacao; sem retry automatico |
| Suspenso | odd/historico continuam legiveis, CTA de aposta inexistente; explicar motivo publico permitido | permitir correcao/proposta somente conforme capability e precondicoes |
| Adiado/cancelado/W.O. | estado textual, sem placar sintetico; explicar efeito sobre modelo/mercado | lifecycle e proxima acao; nao recalcular sem contrato |
| CLOSED/LOCKED | leitura historica completa; nenhuma acao de override | comandos ocultos/desabilitados e motivo explicito; 409 fail-closed |
| 401/403 | GET publico permanece anonimo; erros de endpoint protegido nunca bloqueiam pintura publica | Ver tabela de estados Console abaixo; zero escrita |
| 409/conflito | nao aplicavel a leitura pura; preservar snapshot se ocorrer | Ver tabela de estados Console abaixo; zero sobrescrita |
| Erro | `Nao foi possivel carregar as odds` + tentar novamente/voltar | problema sanitizado + proxima acao; nao expor stack, token ou evidencia |

### Estados Console literais

Cada estado abaixo e uma composicao distinta de titulo, corpo e acao. O CTA
deve permanecer contextual, nunca um rotulo generico de saida.

| Estado | Titulo | Corpo | CTA primario | Caminho seguro |
|---|---|---|---|---|
| Vazio do escopo | `Nenhum mercado neste escopo` | `Nao ha mercados pre-jogo publicados para a competicao e edicao selecionadas.` | `Selecionar outro escopo` | Abre o seletor de competencia/edicao sem criar recurso |
| Recurso inexistente | `Mercado nao encontrado` | `O identificador nao corresponde a um mercado visivel para esta sessao.` | `Ir para o painel operacional` | Navega ao painel permitido pela capability |
| 401 | `Sessao necessaria` | `Sua sessao expirou antes desta operacao. Nenhuma alteracao foi aplicada.` | `Entrar novamente` | Fluxo existente de login; nao repetir mutacao |
| 403 | `Operacao nao autorizada` | `Sua sessao nao permite operar este mercado. Nenhuma alteracao foi aplicada.` | `Retornar ao painel autorizado` | Retorna ao ultimo painel autorizado; nao revela regra interna |
| Transporte/servidor | `Servico indisponivel` | `Nao foi possivel consultar o servidor agora. O estado local nao foi enviado.` | `Tentar leitura novamente` | Refaz somente GET; mutacoes permanecem inativas |
| 409 | `Versao concorrente detectada` | `Outra sessao alterou este mercado. Compare a versao local com a versao atual antes de continuar.` | `Carregar versao atual para comparar` | Preserva formulario/local e carrega snapshot novo sem sobrescrever |

## Responsividade

| Faixa | Contrato |
|---|---|
| 320-599px | container com 16px laterais; resumo em uma coluna; selecoes de odd empilhadas; historico em cards; explicacao em disclosure; nenhuma rolagem horizontal de pagina; acoes operacionais empilhadas |
| 600-899px | container com 24px laterais; resumo e explicacao em duas colunas quando couber; historico pode ser tabela com wrapper focavel; formularios ate 640px |
| 900px+ | container maximo 1240px e 32px laterais; Arena pode usar composicao assimetrica; Console usa painel principal + auxiliar, com acoes junto ao registro; tabela semantica |

Breakpoints sao dirigidos por conteudo. Texto de equipe, estado, fonte, odd,
versao e erro nunca e truncado. Chips e acoes podem envolver; somente
metadado redundante pode sofrer truncamento visual com nome acessivel completo.
Testar pelo menos 320x800 e 1280x800 em Chromium e Firefox, conforme o plano
04-14; teclado nao pode depender de hover ou arrasto.

## Acessibilidade e conteudo

- HTML semantico: `main`, headings em ordem, `table` com `caption`/`th scope`,
  `time datetime`, `dl` para pares de metodologia e `role="status"`/`alert`.
- Skip link e foco pos-rota no heading/main; outline `--bt-focus` de 3px sempre
  visivel, inclusive em chips e disclosure.
- Toda mudanca de loading, stale, erro, suspensao ou aprovacao anuncia texto
  curto via live region sem mover foco inesperadamente.
- Zoom de 200%, modo de alto contraste do navegador e `prefers-reduced-motion`
  preservam leitura; canvas decorativo e `aria-hidden`.
- Copy evita promessa: usar `Odd publicada`, `Probabilidade justa antes da
  margem`, `Confianca baseada nos dados disponiveis` e `Versao imutavel`.
- Links externos de fonte usam validacao local de esquema, apenas protocolos
  permitidos, `target="_blank"` com `rel="noopener noreferrer"` quando
  aplicavel; HTML de fonte/justificativa nunca e executado.

## Seguranca de interface e autorizacao

- GET publico de odds nao exige sessao e usa somente a projecao publica. DOM,
  storage, URL e rede nao podem conter ator, evidencia privada, matricula,
  HMAC, token, ledger, settlement, volume de apostas ou credenciais.
- Console usa sessao BFF/OIDC existente. Capability filtra navegacao, mas cada
  comando depende da autorizacao server-side e do escopo persistido; UUID
  fornecido pelo cliente nunca prova autoridade.
- Mutacoes usam cliente gerado, CSRF/XSRF e `Idempotency-Key` quando o contrato
  exigir. A UI trata 401/403/409 sem repetir comando e sem transformar erro em
  sucesso.
- Override exige dois administradores distintos no mesmo escopo; a UI nao
  permite auto-aprovacao, mas a garantia final e do backend. CLOSED/LOCKED e
  odd ja aceita sao somente leitura.
- Nao registrar segredo ou evidencia operacional em analytics, console, query
  string ou mensagem de erro. Fixtures deterministicas nao sao dados de
  producao.

## Copywriting Contract

| Elemento | Copy obrigatoria |
|---|---|
| CTA publico primario | `Ver explicacao completa` |
| CTA publico secundario | `Ver historico de versoes` |
| CTA operacional de correcao | `Corrigir fonte e recalcular` |
| CTA operacional de suspensao | `Suspender mercado` |
| CTA operacional de proposta | `Solicitar override excepcional` |
| CTA operacional de aprovacao | `Aprovar override` |
| Vazio publico | Titulo: `Mercado ainda nao publicado`. Corpo: `Este confronto ainda nao possui uma odd publica. Volte ao evento para acompanhar atualizacoes.` |
| Erro publico | `Nao foi possivel carregar as odds agora. Tente novamente ou volte ao evento.` |
| Baixa confianca | `Confianca baixa: os dados disponiveis sao escassos. A estimativa permanece neutra; nenhuma odd manual e aplicada.` |
| Suspenso | `Mercado suspenso para verificacao. A versao anterior permanece registrada e nenhuma alteracao retroativa e feita.` |
| Conflito | `Esta versao mudou em outra sessao. Compare os dados e recarregue antes de tentar novamente.` |
| Aprovacao pendente | `Aguardando segundo administrador do mesmo escopo. A pessoa que propôs nao pode aprovar.` |
| Fechamento definitivo | `Mercado encerrado (CLOSED/LOCKED). Overrides e alteracoes nao estao disponiveis.` |
| Confirmacao destrutiva | `Suspender este mercado? A consulta publica continuara mostrando a versao registrada, mas novas operacoes de mercado ficarao bloqueadas ate a revisao.` Botoes: `Manter mercado ativo` / `Suspender mercado`. |
| Confirmacao de override | `Aprovar override e publicar uma nova versao imutavel? A odd ja aceita nunca sera alterada.` Botoes: `Nao publicar esta proposta` / `Aprovar override`. |

Mensagens devem ser em portugues claro, especificar o proximo passo e nao
atribuir culpa. `Salvando...`, `Recalculando...` e `Aprovando...` preservam a
largura do controle e bloqueiam somente a acao em andamento.

## UI Considerations

Probe runtime: 21 consideracoes aplicaveis, todas cobertas explicitamente; 0
unresolved. Os estados de negocio (baixa confianca, suspenso, CLOSED/LOCKED,
W.O./cancelamento, stale, conflito e aprovacao duplicada) permanecem definidos
nas secoes de superficie, estados e copy acima.

| Categoria | Elemento(s) | Status | Resolucao / razao |
|---|---|---|---|
| empty | list-collection, form, static-content | covered | A ausencia de mercado, historico ou dados de formulario usa o estado e copy documentados, com retorno seguro ao contexto. |
| loading | list-collection, form, nav, interactive-control | covered | Durante leitura ou submissao, skeleton/painel de carregamento preserva a altura e desativa somente a acao em andamento. |
| error | list-collection, form, nav, interactive-control, static-content | covered | Falha de leitura ou submissao mostra painel sanitizado com a mensagem e CTA de recuperacao definidos no contrato. |
| populated | list-collection, static-content | covered | Com dados, a superficie mostra odd, selecoes, versao, confianca, explicacao e historico conforme a hierarquia Arena/Console. |
| partial | list-collection, form | covered | Dados incompletos mantem blocos validos visiveis e marca explicitamente o bloco ausente, sem placeholders enganosos. |
| overflow | list-collection, nav, static-content | covered | Conteudo excedente reflui em 320px, quebra textos e troca historico/tabela por cards abaixo de 600px, sem truncar estado ou erro. |
| zero-one-many | list-collection | covered | Historico e colecoes distinguem zero, uma e muitas versoes com copy singular/plural e espacamento consistente. |
| long-text | form, nav, interactive-control, static-content | covered | Motivos, evidencias, nomes e mensagens quebram linha e refluem; texto essencial de estado/erro/fonte nao e truncado. |

Backstops adicionais, fora da taxonomia fechada do probe:

| Backstop | Elemento(s) | Evidencia obrigatoria |
|---|---|---|
| audit-lineage | list-collection, static-content | Teste de contrato confere que cada versao renderizada possui algoritmo, entradas permitidas, margem, arredondamento e timestamp coerentes com o DTO; ausencia bloqueia a certificacao. |
| browser-matrix | nav, form, list-collection | Playwright de 04-14 exercita Arena/Console em Chromium e Firefox, 320px e desktop, incluindo estados finais; ausencia de browser/servico e SKIP bloqueante. |

As linhas acima devem ser levantadas para os must-haves dos planos 04-13/04-14.
Nenhuma dispensa semantica remove a verificacao de loading, erro, overflow,
texto longo ou privacidade.

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|---|---|---|
| shadcn official | nenhum | Nao aplicavel: stack Angular, `components.json` ausente. |
| Terceiros | nenhum | Nenhum registry, bloco, dependencia, asset remoto ou vetting pendente. |
| BETUFU local | `ArenaShell`, `ConsoleShell`, `ConfirmDialog`, `ResponsiveDataView`, classes HTML/SCSS de estado, tokens e fontes locais | Inspecionado em 2026-09-08; reutilizacao sem instalacao ou rede. |

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS

**Approval:** approved 2026-09-08
