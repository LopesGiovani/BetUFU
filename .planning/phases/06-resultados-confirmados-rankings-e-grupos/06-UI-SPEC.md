---
phase: 06
slug: resultados-confirmados-rankings-e-grupos
status: approved
shadcn_initialized: false
preset: none
created: 2026-09-18
reviewed_at: 2026-10-01
checker: approved-5-pass-1-flag
inline_review: passed-6-of-6
owner_approved_at: 2026-10-01
---

# Fase 6 - Contrato de Design de UI

> Contrato para historico de liquidacoes, ranking vitalicio, grupos privados e
> conquistas. A fase continua o shell da Arena e os tokens aprovados nas Fases
> 1 a 5; nao cria um segundo tema nem uma area financeira separada.

## Decisoes aprovadas

O responsavel aceitou o contrato revisado em 2026-10-01. A revisao inline
passou nas seis dimensoes; o checker independente do GSD aprovou o contrato
com cinco PASS e uma FLAG nao bloqueante: explicitar que nao ha registries
de terceiros e que o sistema visual usa os tokens existentes. A execucao da
UI ainda depende de aprovacao especifica.

- Direcao visual: continuar a linguagem da Arena, com fundo escuro, Inter para
  interface, Barlow Condensed para titulos e os tokens existentes em
  `frontend/src/styles/_tokens.scss`.
- Correcoes e conquistas revogadas: mostrar status explicito e sanitizado por
  padrao; nao ocultar o fato importante atras de cor, toast ou texto tecnico.
- Nao expor matricula, HMAC, e-mail, identidade do provedor, nomes de
  administradores, URLs internas, chaves de idempotencia ou dados de terceiros.

## Sistema visual

| Propriedade | Contrato |
|---|---|
| Framework | Angular 22 standalone, Signals e zoneless; sem biblioteca visual nova |
| Shell | `ArenaShell` e `ConsoleShell` existentes; ranking publico descobrivel na Arena, historico/grupos/conquistas na area pessoal e autorizacao somente no console operacional autorizado |
| Fontes | Inter local para corpo/controles; Barlow Condensed local para titulos e numeros de destaque |
| Tema | Escuro herdado da Arena; nenhum toggle claro/escuro nesta fase |
| Cor de acao | `--bt-signal` somente para CTA principal e item ativo; `--bt-focus` para foco; `--bt-danger` somente para acao destrutiva confirmada |
| Distribuicao | Aproximadamente 60% canvas escuro, 30% superficies elevadas e ate 10% destaque/estados; o destaque nao preenche todos os controles |
| Tipo | Herdar apenas `--bt-type-display`, `--bt-type-heading`, `--bt-type-body` e `--bt-type-label` de `frontend/src/styles.scss`; display fica reservado a Arena, com pesos 400/600 e entrelinha 1.5 no corpo |
| Espaco | Herdar `--bt-space-1/2/4/6/8/12/16` (4/8/16/24/32/48/64 px); nao introduzir passos intermediarios |
| Estados | Texto, titulo, icone/controle e estrutura semantica; cor nunca e o unico sinal |
| Movimento | Transicoes curtas; `prefers-reduced-motion` remove animacoes e deslocamentos |

## Hierarquia e navegacao

| Superficie | Primeiro foco visual | Contexto e acao |
|---|---|---|
| Historico | Estado do palpite e resultado utilizado | Regra, calculo e lancamentos aparecem na sequencia; correcao fica junto do fato afetado |
| Ranking | Posicao, acertos definitivos e taxa | Desempate e matricula atual inativa sao secundarios, mas sempre textuais |
| Grupo | Nome do grupo e metricas dos membros | Convite e gestao ficam em acoes separadas da leitura |
| Conquistas | Nome, regra e estado concedida/revogada | Edicao e origem sanitizada acompanham o item |
| Operacao de liquidacao | Evento, resultado confirmado e versao exata | Conferencia antecede o CTA de autorizacao; progresso e conflito aparecem no mesmo contexto |

Visitante encontra o ranking pela Arena sem passar por login. Pessoa autenticada
encontra historico, grupos e conquistas na area pessoal; links privados usam o
retorno contextual ja existente. Somente quem recebe a capacidade operacional
existente encontra a autorizacao no console; o backend revalida papel, escopo e
versao mesmo se a rota for acessada diretamente. O topo nao revela operacoes a
quem nao tem permissao.

## Superficies e jornadas

### Historico de liquidacoes

Rota protegida e descobrivel pela conta. Cada item mostra estado pendente,
ganho, perdido, reembolsado ou anulado, resultado confirmado, regra usada,
calculo, odd/stake aceitos e lancamentos de saldo relacionados. Uma correcao
aparece no mesmo fluxo com `valor recuperado`, `valor pendente`, motivo em
linguagem comum e codigo de referencia sanitizado. O estado nao promete
liquidacao futura quando o backend ainda estiver pendente.

Para um palpite ainda pendente, mostrar `Aguardando resultado confirmado` sem
inventar placar, calculo definitivo ou lancamento. Para um caso corrigido,
mostrar o estado atual e o anterior como fatos distintos, sem apagar o
historico. O CTA de retorno e `Ver partida` quando houver evento publico.

### Ranking vitalicio

Pagina publica para leitura, com ordenacao visivel por acertos definitivos,
taxa de acerto e desempate documentado. A matricula inativa e indicada sem
remover estatisticas historicas e sem revelar o motivo privado da inatividade.
O ranking nao usa saldo, odd ou stake como multiplicador de acertos.

### Grupos privados

Pagina protegida para criar, entrar, sair e encerrar grupos. O convite/codigo e
tratado como acao explicita e mensagens de grupo inexistente, expirado ou sem
permissao sao sanitizadas. Somente membros veem metricas; grupo nao cria moeda,
premio, odds ou ranking alternativo.

`Sair do grupo`, `Remover integrante` e `Encerrar grupo` exigem confirmacao com
nome do grupo, consequencia em linguagem comum e foco contido no dialogo.
Oferecer `Continuar no grupo` ou `Manter grupo` para cancelar sem efeito. A
confirmacao destrutiva usa texto mais `--bt-danger`, nunca apenas cor.

### Conquistas

Perfil e pagina de conquistas mostram nome, descricao, regra, edicao, origem
sanitizada e estado concedida ou revogada. Uma conquista revogada permanece
visivel com explicacao curta; nunca desaparece silenciosamente.

### Operacao de liquidacao

O console operacional mostra casos autorizaveis sem UUID manual. Antes de
`Autorizar liquidacao`, o administrador confere evento, fonte, resultado,
versao vigente e regra. Uma etapa de confirmacao repete a versao exata e a
consequencia de iniciar a liquidacao; `Voltar a conferencia` cancela sem
efeito. Apos autorizar, mostrar `Autorizacao registrada` e progresso real,
nunca apresentar trabalho parcial como concluido. Se a versao mudar entre
leitura e confirmacao, mostrar conflito e `Atualizar resultado`; nao tentar
autorizar silenciosamente a nova versao. A tela nao exibe nomes de outros
administradores nem referencias internas sensiveis.

## Estados obrigatorios

| Estado | Tratamento |
|---|---|
| Carregando | Skeleton ou texto estavel com `aria-busy`; nenhum CTA mutavel acionavel |
| Vazio | Usa as mensagens e acoes literais da tabela de copia abaixo; nao usar apenas `Nenhum dado` |
| 401 | Redireciona para login contextual preservando apenas URL interna validada |
| 403 | Mensagem sanitizada sem revelar papel, escopo ou existencia privada |
| 404 | Pagina acessivel com retorno para a Arena, sem capturar callback tecnico |
| Matricula inativa | Sinal textual no ranking/historico; historico legitimo permanece |
| Correcao pendente | Mostra recuperado, pendente, motivo e referencia; bloqueio de nova aposta fica no bilhete |
| Correcao quitada | Mostra encerramento da pendencia sem apagar o caso original |
| Conquista revogada | Mantem o item, marca `Revogada` e explica que a regra deixou de ser satisfeita |
| Erro de transporte | Mensagem especifica do recurso e acao de nova leitura da tabela de copia; nao inventar saldo, ranking ou liquidacao |
| Versao desatualizada | Operacao de liquidacao preserva a selecao antiga somente para explicacao, bloqueia o CTA e pede nova conferencia |

## Copia minima

- Historico: `Historico de liquidacoes`, `Resultado confirmado`, `Calculo`,
  `Movimentacoes de saldo`, `Pendente`, `Ganho`, `Perdido`, `Reembolsado`,
  `Anulado`.
- Correcao: `Correcao de saldo`, `Valor recuperado`, `Valor pendente`,
  `Motivo`, `Codigo de referencia`.
- Ranking: `Ranking vitalicio`, `Acertos definitivos`, `Taxa de acerto`,
  `Matricula atual inativa`.
- Grupos: `Meus grupos`, `Criar grupo`, `Entrar com convite`, `Sair do grupo`.
- Conquistas: `Conquistas`, `Regra`, `Edicao`, `Concedida`, `Revogada`.
- Operacao: `Resultados para autorizar`, `Autorizar liquidacao`,
  `Voltar a conferencia`, `Autorizacao registrada`, `Atualizar resultado`.

| Superficie | Vazio | Acao segura | Erro de leitura | Acao de erro |
|---|---|---|---|---|
| Historico | `Seu historico de palpites ainda esta vazio. Explore a agenda para encontrar uma partida.` | `Ver agenda` | `Nao foi possivel carregar seu historico. Confira a conexao e tente novamente.` | `Recarregar historico` |
| Ranking | `Ainda nao ha palpites com resultado definitivo para classificar.` | `Voltar a Arena` | `Nao foi possivel carregar o ranking. Confira a conexao e tente novamente.` | `Recarregar ranking` |
| Grupos | `Voce ainda nao participa de grupos. Crie um grupo ou entre com convite.` | `Criar grupo` / `Entrar com convite` | `Nao foi possivel carregar seus grupos. Confira a conexao e tente novamente.` | `Recarregar grupos` |
| Conquistas | `Voce ainda nao recebeu conquistas. Veja as regras para saber como obte-las.` | `Ver regras das conquistas` | `Nao foi possivel carregar suas conquistas. Confira a conexao e tente novamente.` | `Recarregar conquistas` |
| Operacao | `Nenhum resultado aguarda autorizacao agora.` | `Ver eventos` | `Nao foi possivel carregar os resultados para autorizacao. Confira a conexao e tente novamente.` | `Recarregar resultados` |

Convite invalido, expirado ou sem acesso recebe a mesma mensagem sanitizada:
`Nao foi possivel entrar neste grupo com esse convite. Confira o codigo ou
solicite outro convite.` A acao e `Tentar outro convite`. Um 403 privado nao
confirma a existencia do grupo.

Evitar linguagem de dinheiro real, premio garantido, ganho certo ou promessa
de resultado. Mensagens devem ser curtas, acionaveis e sem culpa.

## Responsividade e acessibilidade

- Em 320 px, usar uma coluna, cards empilhados e nenhuma rolagem horizontal da
  pagina; tabelas podem usar um wrapper focavel com cabecalho preservado.
- Em desktop, limitar o conteudo a 1240 px e manter contexto antes da acao.
- Reutilizar o unico `main` fornecido pelo `ArenaShell` ou `ConsoleShell`;
  paginas filhas usam `section`/`article`, sem `main` aninhado. Usar headings
  em ordem, `table` semantica quando houver dados
  tabulares, `dl` para calculos/correcoes, `time` para instantes e
  `aria-live`/`role=status` para transicoes.
- Foco visivel com `--bt-focus`, alvo minimo de 44x44 px e navegacao integral
  por teclado. Zoom 200% e alto contraste nao podem ocultar status.
- Validar contraste minimo de 4.5:1 para texto comum e 3:1 para texto grande,
  bordas de controle e foco; medir combinacoes reais dos tokens, inclusive
  `--bt-danger`, em vez de assumir que a cor herdada passa.
- Testar Tab, Shift+Tab, Enter, Space e Escape em convites, confirmacoes e
  autorizacao; foco retorna ao acionador ao fechar dialogos. Mensagens de
  sucesso, conflito e erro sao anunciadas sem roubar o foco da leitura.

## UI Considerations

| Elemento | Consideracao resolvida |
|---|---|
| Historico | loading, vazio, erro, estados de liquidacao e correcao sao estados do dominio e recebem composicao textual explicita |
| Ranking | estado inativo nao remove historico; ordenacao e desempate aparecem como contexto legivel |
| Grupo | acesso negado e convite invalido nao revelam existencia de grupo privado |
| Conquista | revogacao e exibida como estado historico, nao como exclusao |
| Navegacao | rotas privadas reutilizam o guard e o retorno contextual da Fase 05.1 |
| Operacao | versao esportiva exata e confirmada antes da autorizacao; conflito exige atualizar e conferir novamente |

## Fora do contrato

Nao desenhar chat, notificacoes, contestacao formal, premios, recarga,
transferencia, ranking por dinheiro real ou qualquer feature reservada a fases
posteriores.
