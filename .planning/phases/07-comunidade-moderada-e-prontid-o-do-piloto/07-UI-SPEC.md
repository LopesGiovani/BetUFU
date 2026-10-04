---
phase: 07
slug: comunidade-moderada-e-prontid-o-do-piloto
status: draft
shadcn_initialized: false
preset: none
created: 2026-10-04
baseline: 21edd3b9d58d83b7c961cd51f218999bc3113cfb
authoring_mode: inline-documentary-only
owner_approval: pending-review-of-this-draft
checker_status: pending
ui_consideration_probe: not-run-draft
---

# Fase 7 - Contrato de Design de UI (rascunho)

## Escopo e autoridade

Este documento descreve as telas futuras, nao uma implementacao pronta.
Autorizacao inicial: criar somente este rascunho para revisao. Bloco posterior
autorizado: reconciliar planejamento/header/preferencia/requisitos, sem codigo,
banco, Docker, dependencias, stage, commit ou push. Os tres documentos do
responsavel permanecem intactos. Contrato continua draft e checker pendente.

As escolhas explicitas abaixo estao fechadas. As composicoes, rotas candidatas,
copias e tratamentos adicionais sao propostas para revisao deste contrato.
Uma especificacao completa nao significa que o gate UI, os gates juridicos ou
a execucao de qualquer plano estejam aprovados. Nenhum teste material ocorreu.

### Escolhas visuais confirmadas na conversa

| ID | Escolha confirmada | Efeito esperado |
|---|---|---|
| UI-D01 | Preservar a identidade da Arena | Mesmo tema, fontes e controles; sem redesign global |
| UI-D02 | Comunidade em pagina propria, com entrada no topo da Arena | Chat global descobrivel sem URL manual; complementa D-15/16 |
| UI-D03 | Lista compacta, estilo Discord, para global e partida | Apelido e horario acima do texto; tudo a esquerda, sem baloes |
| UI-D04 | Mensagem propria com destaque discreto | Identificar tambem por texto, sem depender da cor |
| UI-D05 | Regras em popup fechavel, nao painel lateral permanente | Botao Regras permite reabrir; fechar e Escape disponiveis |
| UI-D06 | Popup automatico na primeira visita a Comunidade, uma vez por conta | Fechamento lembrado entre dispositivos; nao somente por navegador |
| UI-D07 | Visitante do chat publico de partida usa apenas botao Regras | Sem popup automatico; visitante continua somente leitura |

Fechar regras registra dispensar um aviso, NAO aceite juridico, comprovacao de
leitura ou permissao de escrita. Nao impor checkbox, rolagem obrigatoria ou
tempo minimo. Nova versao das regras nao reabre automaticamente o popup por
inferencia; essa politica nao foi discutida. D-01..D-40 continuam em CONTEXT.

## Design System

| Propriedade | Contrato proposto / fonte existente |
|---|---|
| Framework | Angular 22 standalone, OnPush, zoneless, Signals e stores existentes |
| Biblioteca visual | Angular Material ja configurado e componentes locais; sem nova dependencia |
| Tool / preset | shadcn nao aplicavel; nenhum scaffold ou registry |
| Shell | ArenaShell para publico/Comunidade; ConsoleShell para conta/operacao |
| Fontes | Inter 400/600 e Barlow Condensed 600, auto-hospedadas |
| Icones | Material Symbols Rounded local, sempre com nome acessivel nas acoes |
| Tema | Escuro herdado, sem toggle ou nova paleta |
| Tokens | frontend/src/styles/_tokens.scss e frontend/src/styles.scss |
| Dialogos | Herdar semantica/foco de shared/confirm-dialog; popup informativo nao exige confirmar |
| Forma | Controles e campos existentes; linhas de chat sem card/balao por mensagem |

A UI de chat e uma extensao da Arena. Nao criar hero promocional, indicadores
de pessoas online, avatares/contatos privados, leitura confirmada, typing,
notificacoes, anexos ou mensagens diretas. Nenhuma promessa de premio/saldo.
O rodape independente da UFU e a navegacao existente permanecem.

## Spacing Scale

| Token existente | Valor | Uso proposto |
|---|---|---|
| --bt-space-1 | 4px | Metadados junto ao texto |
| --bt-space-2 | 8px | Nome/horario e controles relacionados |
| --bt-space-4 | 16px | Entre mensagens e padding mobile |
| --bt-space-6 | 24px | Padding desktop e secoes locais |
| --bt-space-8 | 32px | Separacao entre contexto do evento e chat |
| --bt-space-12 | 48px | Somente separacoes de pagina existentes |
| --bt-space-16 | 64px | Somente ritmo amplo existente, nao por mensagem |

Sem passos novos. Alvos 44x44px e outline 3px com offset 3px sao excecoes
funcionais ja existentes, nao novos espacamentos decorativos. Conteudo maximo
1240px e formularios/dialogos ate 640px, limitados pela largura disponivel.

## Typography

| Papel | Fonte / token | Tamanho | Peso | Entrelinha |
|---|---|---|---|---|
| Corpo e mensagem | Inter / --bt-type-body | 16px | 400 | 1.5 |
| Label, apelido, horario | Inter / --bt-type-label | 14px | 400/600 | 1.5 |
| Titulos | Barlow Condensed / --bt-type-heading | clamp(1.5rem, 4vw, 3.5rem) | 600 | Herdar tratamento existente; proposta 1.2 |
| Display Arena | Barlow Condensed / --bt-type-display | clamp(4.75rem, 13vw, 11.5rem) | 600 | Nao usar em chat/dialogos |

Apelido com peso 600, horario secundario sem diminuir abaixo de 14px. Texto
de mensagem nao e truncado. Horario em time com data/hora acessivel; formato
local de exibicao nao altera o instante autoritativo. Copy final em pt-BR com
acentuacao; este rascunho segue o ASCII dos documentos atuais da Fase 7.

## Color

| Papel | Token / valor existente | Uso |
|---|---|---|
| Dominante, aproximadamente 60% | --bt-canvas #120b12 | Fundo herdado e area de leitura |
| Secundario, aproximadamente 30% | --bt-surface #171018; --bt-surface-raised #21131d | Formularios/dialogos/contexto |
| Destaque, ate 10% | --bt-signal #ff626b; --bt-signal-soft #ffb4b8 | CTA principal, selecao atual, marcador proprio discreto |
| Texto | --bt-text #fff4f4; --bt-text-muted #cdbfc5 | Texto e metadados, inclusive dentro de popup/campo |
| Foco | --bt-focus #ffd2d4 | Outline visivel, nunca removido |
| Estados | --bt-success #72d6a0; --bt-warning #f5c95c; --bt-danger #ff7a82 | Feedback acompanhado de palavras; danger em acao destrutiva/erro |
| Estrutura | --bt-border / --bt-border-strong | Divisores discretos; contraste de controles deve ser medido |

Proporcoes sao orientacao, nao medicao de pixels. Nao usar destaque em todos
os nomes/acoes. Fundo vinho nao recebe texto escuro por heranca acidental.
CTA preenchido usa par de texto/superficie do controle existente, medido.
Texto normal >=4.5:1, texto grande e controles/foco >=3:1. A heranca de tokens
nao certifica contraste: medir combinacoes reais e estados disabled/hover.

## Navegacao e composicao propostas

| Superficie | Entrada descobrivel | Rota candidata / contexto | Permissao |
|---|---|---|---|
| Comunidade global | Comunidade no topo desktop e mobile da Arena | /comunidade | Sessao para ler; ATIVO para escrever |
| Chat de partida | Agenda -> competicao/evento -> secao Chat da partida | /eventos/:eventId existente, no proprio evento | Leitura publica de partida publicada; escrita ATIVO |
| Minhas restricoes de chat | Minha conta -> Restricoes de chat | /conta/punicoes, candidata | Somente titular; consulta/revisao inclusive conta pausada |
| Moderacao | Console/Painel operacional -> Moderacao do chat | /operacao/chat, candidata | Moderador no escopo ou administrador geral |
| Pausas de emergencia | Console/Painel operacional -> Pausas de emergencia | /operacao/pausas, candidata | Somente administrador geral |
| Privacidade | Minha conta -> Privacidade | /conta/privacidade, candidata | Titular autenticado; procedimento sem conta ainda pendente |
| Operacao de direitos | Painel operacional -> Pedidos de privacidade | /operacao/privacidade, candidata | Autoridade a ratificar juridicamente; nao inventar papel |

Rotas novas sao propostas, nao URLs implementadas ou aprovadas por esta escrita.
Conservar login contextual seguro e guards da Fase 05.1. Link Comunidade pode
ser visto pelo visitante, mas conduz ao login antes de qualquer leitura global.
Nao buscar historico global antecipadamente, nem guarda-lo em cache publico.
Nenhuma jornada depende de digitar UUID; eventos vem dos links da agenda.
Manter caminho de retorno a Arena, evento e conta, refresh e voltar/avancar.
Nao adicionar outro main: paginas filhas usam section dentro do shell existente.

### Comunidade e chat de partida

Composicao proposta em uma coluna: contexto/titulo e Regras; status do canal;
lista cronologica; Novas mensagens quando aplicavel; formulario ou explicacao
textual de somente leitura. Na partida, preservar resumo/placar e CTA de odds;
chat nao substitui evento ou caminho de palpite. No global, nao simular contexto
de partida nem fechamento por resultado esportivo.

Cada linha inclui apelido publico permitido, horario, texto integral e acoes
permitidas. Mensagem propria inclui Voce como identificacao adicional. Nao
mostrar email, matricula, HMAC, identidade OIDC ou permissao privada do autor.
Usar lista semantica, quebras de linha seguras e quebra de texto longo. Exibir
texto literalmente: nada de innerHTML, scripts ou links executaveis inferidos.

Carregar historico por paginas sem duplicar mensagens. Novas mensagens so
rolam automaticamente quando a pessoa ja esta no fim (D-26). Lendo mensagens
antigas, preservar posicao e oferecer Novas mensagens; o clique leva ao fim
sem tirar o foco do acionador. Historico anterior tambem preserva ancoragem.
Atualizacao de moderacao substitui texto antigo pelo marcador, inclusive em
paginas ja carregadas; original removido nao permanece escondido no DOM.

### Popup de regras

UI-D05..07 substituem a ideia anterior de painel lateral permanente. O popup
tem titulo Regras da comunidade, texto curto e Fechar regras. Regras permanece
disponivel fora do popup, no global e na partida. Escape fecha; foco fica no
dialogo enquanto aberto e retorna ao acionador. Na abertura automatica sem
acionador, destino de retorno proposto e o botao Regras. Usar aria-modal,
nome acessivel, fundo nao interativo e conteudo legivel/rolavel em tela curta.

Primeira visita significa primeira entrada autenticada em Comunidade para
uma conta sem fechamento registrado. Fechar ou Escape registra dispensa, sem
bloquear chat. Reabertura manual nao transforma dispensa em aceite juridico.
Estado por conta deve sobreviver a refresh, logout/login e outro dispositivo;
troca de persona nao herda a preferencia anterior. Visitante da partida nunca
abre automaticamente; abertura manual nao cria conta nem preferencia global.

Nao escolher endpoint, modulo, migration ou storage aqui. A persistencia por
conta exige planejamento backend/contrato; localStorage sozinho nao satisfaz
UI-D06. Falha de leitura/gravar dispensa precisa tratamento explicito antes da
implementacao; proposta: manter Regras acessivel, permitir fechar localmente
e avisar quando nao foi possivel salvar, sem alegar sincronizacao concluida.

### Formulario e continuidade de sessao

Proposta: textarea com label Mensagem, contador ate 280 e botao Enviar mensagem.
Enter mantem quebra de linha; envio pelo botao evita submissao acidental e
atalho novo nao aprovado. Regra de caracteres depende de unidade Unicode
ratificada; o contador e o backend precisam contar a mesma coisa.

ATIVO nao basta se canal encerrou/pausou, conta pausou ou chat foi suspenso.
Backend e autoridade a cada envio; Angular mostra bloqueios conhecidos sem
conceder autorizacao por estado local. Enquanto verificando acesso, nao enviar.
Encerramento permanente nao e escondido por pausa temporaria. Mostrar todos
os impedimentos pertinentes, sem divulgar motivos privados a outros leitores.

Fila offline e somente memoria da aba (D-28/29). Aguardando conexao e distinto
de Enviado; retry preserva ator/chave/payload e respeita autorizacao e limite
de tres segundos. Erro terminal nao reenvia infinitamente. Refresh/fechar aba
perde pendentes; historico confirmado e recuperado. Logout/troca de conta
limpa fila, preferencias privadas, callbacks e stream. Global nao conserva
mensagens privadas na tela apos perda da sessao. Protocolo push ainda pendente.

### Denuncia e silenciamento pessoal

Denunciar mensagem abre dialogo nomeado com contexto seguro, motivo fechado
e Enviar denuncia/Cancelar. Motivos: DISCURSO_DE_ODIO, SPAM, OFENSA, ASSEDIO,
FRAUDE, com labels pt-BR. Visitante faz login contextual; autenticado com acesso
ao canal pode denunciar mesmo inativo ou suspenso da escrita (D-20).
Nao expor denunciante ou promessa de punicao; denuncia nao pune sozinha.

Silenciar participante muda apenas a visao do titular imediatamente, sem
notificar alvo. Proposta: local acessivel para Desfazer silenciamento; lista
de silenciados, alcance entre canais e conflito/sincronizacao ainda requerem
decisao. Falha de salvar nao mostra sucesso falso; logout elimina cache privado.

### Moderacao, restricoes e pausas

Proposta de console: contexto do canal e lista de casos -> detalhe -> acao.
Moderador scoped remove/analisa/encaminha; nao recebe suspensao global nem
pausa operacional. Original so e obtido por consulta privilegiada explicita
e auditada, nunca por DTO comum ou HTML escondido (D-36).
Remover mensagem exige motivo/confirmacao e preserva o original restrito.

Administrador geral confere caso e confirma sugestao 24h/7dias/banimento do
chat. Nao executar sancao automaticamente por numero de denuncias. Mostrar
impacto: impede escrever em todos os chats, nao altera saldo/ranking/historico.
Conta mostra motivo/duracao/explicacao sanitizados e Solicitar revisao.
Revisao pendente nao desbloqueia escrita; manter/anular registra nova decisao.

Pausas exigem alvo nomeado (conta/partida/mercado/canal), motivo e confirmacao
que descreve efeito de D-22..25. Consultas/rights/revisao ficam acessiveis;
liquidacao/correcao de palpites aceitos nao para. Retomar nao reabre deadline,
canal encerrado ou sancao vigente. Erro/conflito exige atualizar o estado,
nao declarar operacao concluida antes de resposta autoritativa.

### Privacidade

Proposta: pedidos do titular e progresso na conta; operacao separada verifica
titularidade, analisa e responde conforme politica juridica ratificada.
Solicitar exclusao nao exclui automaticamente conta, saldo ou historico.
Acesso/correcao/exclusao/oposicao/pseudonimizacao dependem de definicoes legais;
nao inventar prazo, aprovacao automatica ou canal externo. Titular sem conta,
export seguro e autoridade operacional permanecem gates, nao telas prontas.

## Copywriting Contract

Copias propostas para revisao, sem detalhes internos ou promessa de resultado.

| Elemento / estado | Copy | Acao segura |
|---|---|---|
| Popup | Regras da comunidade. Converse com respeito. Nao publique dados pessoais nem conteudo ofensivo. Use Denunciar mensagem para avisar a moderacao. | Fechar regras |
| Carregamento | Carregando mensagens | Nao habilitar envio durante verificacao |
| Vazio / pode escrever | Ainda nao ha mensagens. Comece uma conversa sobre o esporte. | Enviar mensagem |
| Vazio / somente leitura | Ainda nao ha mensagens neste canal. | Regras |
| Falha de historico | Nao foi possivel carregar as mensagens. Confira a conexao e tente novamente. | Recarregar mensagens |
| Visitante da partida | Entre na sua conta para participar da conversa. | Entrar para conversar |
| Global sem sessao | Entre na sua conta para acessar a Comunidade. | Entrar, com retorno contextual |
| Sem matricula ativa | Voce pode ler. Para enviar mensagens, sua matricula precisa estar ativa. | Ver minha matricula |
| Canal encerrado | Este chat foi encerrado para novas mensagens. O historico continua disponivel. | Ver partida / Regras |
| Canal pausado | Novos envios estao pausados neste chat. Voce pode continuar lendo. | Regras |
| Conta pausada | Sua conta esta pausada para novas mensagens e palpites. Suas consultas continuam disponiveis. | Minha conta |
| Suspensao de chat | Voce esta impedido de enviar mensagens no chat. Consulte o motivo e a duracao na sua conta. | Ver restricoes de chat |
| Limite de frequencia | Aguarde antes de enviar outra mensagem. | Preservar rascunho e usar tempo informado pelo servidor |
| Texto invalido | Escreva uma mensagem de ate 280 caracteres. | Corrigir mensagem |
| Offline pendente | Aguardando conexao. Esta mensagem ainda nao foi enviada. | Nao marcar como enviada |
| Aviso de fila volatil | Mensagens aguardando conexao serao perdidas se voce recarregar ou fechar esta aba. | Continuar na pagina |
| Falha terminal de envio | Nao foi possivel enviar esta mensagem. Confira o aviso de acesso ao chat. | Nao repetir automaticamente |
| Sessao expirada | Sua sessao terminou. Entre novamente para continuar. | Entrar novamente, sem replay de outra persona |
| Mensagem moderada | Mensagem removida pela moderacao. | Sem acesso ao original na interface comum |
| Denuncia registrada | Denuncia registrada para analise. | Fechar |
| Falha de denuncia | Nao foi possivel registrar a denuncia. Tente novamente. | Tentar novamente, sem criar duplicidade |
| Dispensa nao salva | Nao foi possivel salvar sua preferencia de regras. | Fechar localmente; comportamento de sincronizacao pendente |
| Moderacao vazia | Nenhuma denuncia disponivel no seu escopo agora. | Voltar ao painel |
| Restricoes vazias | Voce nao tem restricoes de chat registradas. | Voltar a Comunidade |
| Privacidade vazia | Voce ainda nao registrou pedidos de privacidade. | Escolher pedido conforme politica aprovada |
| Erro operacional | Nao foi possivel concluir a acao. Atualize o estado antes de tentar novamente. | Atualizar estado |
| Acesso privado negado / inexistente | Nao foi possivel acessar este recurso. | Voltar a Arena; nao confirmar existencia privada |

Confirmacoes propostas: Remover mensagem / Manter mensagem; Suspender chat /
Voltar ao caso; Pausar alvo / Manter ativo; Retomar alvo / Manter pausado.
Nomear alvo e impacto sem PII alheia; suspensao temporaria informa duracao.
Fechar regras nao usa cor destrutiva. Pedido de exclusao explica que sera
analisado e nunca diz Dados excluidos antes de execucao real comprovada.

## Responsividade e acessibilidade

- Uma coluna desde 320px; header com Comunidade acessivel tambem no mobile.
  Reutilizar breakpoints 599/600/899/900px. Nenhuma rolagem horizontal da pagina.
- Mensagens longas, nomes extensos e sequencias sem espaco quebram dentro da
  largura. Controles nao ficam disponiveis apenas por hover. Contexto pode
  abreviar apelido visualmente apenas se o nome seguro completo for acessivel.
- Texto editavel 16px; alvo 44x44px; zoom 200%, landscape e teclado virtual
  nao ocultam fechar popup, status ou botao de envio. Evitar dois scrolls
  verticais concorrentes; historico e foco precisam preservar ancoragem.
- Popup cabe na tela; corpo pode rolar sem esconder titulo/fechamento. Tab e
  Shift+Tab ficam no dialogo aberto; Escape fecha e devolve foco. Nao prender
  foco fora de um modal, nem impor leitura completa para fechar.
- Ordem de headings, labels de textarea/motivo, time semantico e botoes
  nativos. Menus/dialogos nao reutilizam IDs duplicados entre dois chats.
- aria-busy para leitura; role=status/aria-live polite para mudancas uteis.
  Nao reler o historico inteiro a cada push. Anunciar Novas mensagens ou
  bloqueio sem mover foco; erros acionaveis ligados ao campo/acao relevante.
- Movimento apenas de feedback necessario, sem piscar mensagens ou simular
  notificacoes. prefers-reduced-motion elimina deslocamentos animados.

## UI Considerations

Derivacao manual de contrato em rascunho, nao saida do probe nem teste passado.
covered abaixo significa escolha explicita especificada; proposta/unresolved
nao pode virar aceite silencioso. Revisao formal e probe apos aprovacao do
contrato continuam pendentes, sem auto-resolucao ou descarte de categorias.

| Categoria | Elemento | Status | Resolucao / evidencia futura |
|---|---|---|---|
| populated / alignment | Lista de mensagens | covered, UI-D03/04 | Nome/horario acima do texto, tudo a esquerda, propria identificada sem baloes |
| first-run / dismiss | Regras global | covered, UI-D05/06 | Uma vez por conta apos fechamento, dispositivos/refresh; backend ainda a planejar |
| anonymous / first-run | Regras de partida | covered, UI-D07 | Visitante nao recebe popup automatico; botao sempre disponivel |
| permission / zero access | Global/partida | covered, D-01/15/20 | Global sem sessao nao entrega dados; partida publicada aceita leitura publica |
| loading / empty / error / populated | Historico/composer | proposta | Composicao e Copywriting Contract; testar API real vazia/falha/recuperacao |
| partial / pagination / update | Historico | covered, D-26/27 | Posicao preservada, Novas mensagens, marcador substitui item antigo; prova futura |
| pending / failure / refresh | Envio offline | covered, D-28/29 | ACK antes de enviado; fila volatil; limpar por persona; unidade/transportes pendentes |
| long-text / overflow / zero-one-many | Mensagens e casos | proposta | Wrap, paginacao e viewport 320px sem clipping; nao considerar visualmente testado |
| readonly / paused / closed / suspended | Chat e bilhete | covered, D-17/23..25/30/31 | Estados autoritativos, sem reabertura nem punicao financeira |
| dialog / focus / keyboard | Regras/denuncia/confirmacao | proposta | Escape, retorno de foco, sem armadilha externa; futura prova manual/Playwright |
| conflict / sync failure | Dispensa de regras e mute | unresolved | Ratificar leitura/gravar/retry entre dispositivos sem sucesso falso |
| form / confirmation / case status | Moderacao/pausas/direitos | proposta | Acao separada da leitura, confirmacao/impacto; nenhuma autoridade extra |
| retention / access / stale content | Texto e evidencia | unresolved | Politica juridica, supressao e export; original nunca no DTO comum |

## Registry Safety

| Fonte | Uso | Gate |
|---|---|---|
| Componentes/tokens/fontes locais BETUFU | Reuso do sistema existente | Inspecao local; sem instalacao neste bloco |
| Angular Material ja configurado | Reuso conforme versao aprovada | Nao atualizar dependencia ou adicionar scaffold |
| shadcn / registry externo | Nenhum | Nao aplicavel; nenhum download/registry para implementar a UI |

## Integracao com planos e decisoes abertas

1. 07-08, 07-12, 07-15, 07-19, 07-23, 07-26 e 07-35 sao consumidores visuais;
   07-31/32 certificam jornadas. Nenhum plano aprovado para execucao.
2. Reconciliacao inclui HTML/teste reais do header em 07-08 (15 paths) e
   propostas 07-33/34/35 de persistencia/API/popup (10/14/9 paths).
   Primeiro checkpoint de 33 aprova arquitetura; 35 prova duas contas/contextos.
   Nao esconder novos arquivos em foundation ou gerados.
3. Ratificar rotas novas, copias e composicoes propostas; popup informativo
   nao deve herdar a dupla confirmacao de um dialogo destrutivo sem necessidade.
4. Ratificar falhas de sincronizacao da dispensa, atualizacao de regras e
   primeiro acesso autenticado ao chat de partida (UI-D06 so define Comunidade).
5. Unidade Unicode, alcance do limiter, mute entre canais, caso de infracao,
   lifecycle/cancelamento e compensacao pendente continuam gates D-03/21/30.
6. Server-push continua sem protocolo escolhido; RT-01/STOMP e IDEN-04 precisam
   reconciliacao documental. Nao incluir dependencia/transporte por inferencia.
7. Reconciliacao documental autorizada atualizou CHAT-01/05 e Out of Scope:
   global moderado incluido; escrita ATIVO; sancoes globais admin-only.
   Nenhum requisito marcado concluido. RT-01/STOMP permanece pendente.
8. Juridico/retencao/titular sem conta/autoridade de direitos permanecem
   pendentes. RPO/RTO, destino/chaves de backup e SLO/carga nao sao decisoes de UI
   nem evidencias que este documento possa certificar.

## Verificacao futura das jornadas

Playwright real, sem mocks de negocio, somente em recursos isolados aprovados:

- Arena -> Comunidade -> login -> mesmo contexto; sessao/saldo/topo coerentes;
  nenhuma mensagem global aparece antes do login ou apos logout/expiracao.
- Agenda -> competicao -> partida publicada -> chat publico; visitante le e
  abre Regras manualmente, sem popup automatico. Login retorna a partida.
- Conta A fecha regras; refresh e segundo contexto/dispositivo da mesma conta
  nao reabrem automaticamente. Conta B independente ainda ve primeira abertura.
  Regras reabre manualmente; Escape e retorno de foco funcionam em ambos.
- ATIVO envia; inativo/admin sem ATIVO nao ganha escrita. Prazo/pausa/sancao
  bloqueiam e sao textuais; retry/ACK perdido nao duplicam mensagem.
- Historico lendo mensagens antigas nao pula ao fim; nova mensagem e remocao
  atualizam com posicao/foco preservados e sem original no DOM/HTTP comum.
- Denuncia/mute em sessoes separadas, moderador fora do escopo, admin confirma
  sancao/pausa e titular consulta/revisa mesmo pausado; saldo/ranking intactos.
- 320px e desktop, teclado/zoom/alto contraste, longo texto, loading/vazio/erro,
  conexao interrompida, refresh/voltar/avancar e troca de conta.

Comandos/specs seguem 07-VALIDATION e <verify> do plano autorizado; scripts
ainda ausentes nao sao testes passados. Nenhum Maven, Angular, navegador,
scanner, migration ou teste de negocio e executado para escrever este rascunho.

## Checker Sign-Off

Revisao documental inline de coerencia, nao checker independente nem PASS
global. O fluxo gsd-ui-phase foi limitado a rascunho conforme a autorizacao:
sem delegacao, auto-advance, probe pos-aprovacao, commit ou atualizacao de STATE.
As skills de design/acessibilidade preservam a identidade existente e nao
autorizam PRODUCT.md, DESIGN.md ou novos arquivos fora deste escopo.

| Dimensao | Revisao do rascunho | Gate |
|---|---|---|
| Copywriting | Copias acionaveis, seguro/privado distinto | Revisao do responsavel pendente |
| Visuals | Lista escolhida, entradas global/evento e popup | Revisao e evidencia de telas pendentes |
| Color | Tokens reais e limites de contraste especificados | Medicao de tela pendente |
| Typography | Fontes locais, 16/14px, titulos herdados | Revisao de legibilidade pendente |
| Spacing | Escala real e reflow 320px | Evidencia responsiva pendente |
| Registry Safety | Sem biblioteca/fontes/registry novo | Nenhuma instalacao autorizada |

**Approval:** pending. Criacao deste arquivo nao aprova o contrato nem execucao.
**Probe:** nao executado; previsto apos revisao do contrato, nao substituido
por tabela manual rotulada como saida automatizada ou criterio passed.

## Canonical References

- AGENTS.md
- .planning/STATE.md
- .planning/ROADMAP.md
- .planning/REQUIREMENTS.md
- .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-CONTEXT.md
- .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-RESEARCH.md
- .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-VALIDATION.md
- .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-DECOMPOSITION.md
- .planning/phases/06-resultados-confirmados-rankings-e-grupos/06-UI-SPEC.md
- frontend/src/styles/_tokens.scss
- frontend/src/styles/_fonts.scss
- frontend/src/styles.scss
- frontend/src/app/app.routes.ts
- frontend/src/app/layout/arena-shell.component.html
- frontend/src/app/layout/console-shell.component.html
- frontend/src/app/layout/console-shell.component.ts
- frontend/src/app/shared/confirm-dialog/confirm-dialog.component.ts

Escolhas UI-D01..07 vieram da conversa posterior ao CONTEXT atual. Este
rascunho captura o delta, sem reescrever aquele arquivo ou inventar aceite.
