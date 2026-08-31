---
phase: 3
slug: experi-ncia-esportiva-do-piloto
status: approved
shadcn_initialized: false
preset: none
created: 2026-08-26
---

# Fase 3 — Contrato de Design de UI

> Contrato visual e de interação para a experiência esportiva pública, o perfil esportivo da conta e a administração escopada do piloto.
> Este documento estende `01-UI-SPEC.md` e `02-UI-SPEC.md`; não os substitui. Onde este contrato cala, valem os contratos anteriores.

---

## Fonte visual e decisões de origem

| Fonte | Contrato executável |
|---|---|
| Variante V | `.planning/sketches/004-identity-lab/index.html`, SHA-256 `6C422384EE8DA0A91ACDA9397A9415413A0E3FF36F0EEF82A00F8D923F556F98`. É a referência definitiva; o HTML é um sketch, não código para copiar. |
| Identidade | Uma BETUFU, com os modos coordenados **Arena pública** e **Console operacional**. D-81–D-89 prevalecem sobre a direção visual anterior desta fase. |
| Produto | `/` mostra agenda publicada antes de sessão. Catálogo continua genérico; rascunhos, PII, matrícula, HMAC, capacidades e escopos internos não atravessam a fronteira pública. |
| Implementação | Angular 22 standalone/zoneless, Signals, Reactive Forms tipados, HTML semântico e estilos locais. Sem React, registry, scaffold, pacote visual ou asset remoto. |
| Dados | Todo número, evento, time, estado e contagem vem do contrato real. Fixtures pertencem a testes; a aplicação não usa conteúdo fixo para esconder lacunas. |

Este contrato preserva D-53–D-80, AUTH-01 e SPRT-01–SPRT-16. Ele muda a linguagem visual e a composição, não as regras esportivas, de publicação, segurança ou privacidade.

---

## Tese visual

### Princípios

1. **Transmissão, não dashboard genérico.** A Arena pública usa hierarquia de placar/editorial: tipografia condensada, horário, confronto, data e faixa de programação formam a imagem principal.
2. **Uma identidade, dois ritmos.** Arena é expansiva e dramática; Console é preciso e denso. Cor, tipografia, raios, foco, estados e voz permanecem compartilhados.
3. **O dado esportivo é o espetáculo.** Evento real, horário, fase, local e estado ocupam o protagonismo. Efeito visual nunca compete com leitura ou inventa conteúdo.
4. **Coral é sinal, vermelho profundo é superfície.** O coral marca ação, seleção, horário e detalhes; `#910008` sustenta faixas e planos vermelhos de impacto, enquanto ink/canvas preservam contraste e profundidade.
5. **Movimento explica mudança.** Entrada, troca do hero e atualização de estado têm intenção. Nenhuma animação é requisito para compreender, navegar ou agir.
6. **Honestidade operacional.** Loading, vazio, parcial, stale, conflito e 403 possuem composição própria; nenhum deles se parece com sucesso.

### Anti-princípios

- não usar azul corporativo, roxo genérico, verde como identidade, gradiente arco-íris ou várias cores neon concorrentes;
- não transformar toda superfície em vidro, glow ou card flutuante;
- não repetir um hero promocional vazio em páginas internas;
- não usar WebGL no Console operacional, em formulários, diálogos ou por trás de texto longo;
- não comprimir tabelas em miniatura, esconder ações em ícones sem rótulo ou depender de hover;
- não reproduzir APIs/componentes React; traduzir responsabilidades para Angular e CSS locais;
- não usar eventos, placares, contagens ou entidades fixas fora de fixtures determinísticas.

---

## Tokens semânticos

Os nomes são o contrato. Os planos podem adequar a sintaxe ao `_tokens.scss`, mas não criar uma paleta paralela por rota.

### Cor

| Token | Valor | Uso |
|---|---|---|
| `--bt-ink` | `#09080D` | canvas principal, header e texto sobre coral |
| `--bt-canvas` | `#120B12` | fundo contínuo da aplicação |
| `--bt-surface` | `#171018` | painéis, filtros, linhas de agenda e workspace |
| `--bt-surface-raised` | `#21131D` | hover, seleção, diálogo e painel elevado |
| `--bt-wine` | `#741D42` | profundidade editorial e apoio ao Grainient; nunca texto sozinho |
| `--bt-signal` | `#FF626B` | CTA primário, item ativo, horário e faixa de destaque |
| `--bt-signal-deep` | `#910008` | superfícies vermelhas de impacto, beam do hero, fitas e lockups; não substitui canvas/surface em toda a aplicação |
| `--bt-signal-soft` | `#FFB4B8` | metadado editorial e borda enfatizada |
| `--bt-text` | `#FFF4F4` | texto principal |
| `--bt-display` | `#FFC4C4` | títulos editoriais da agenda, incluindo `Jogos de hoje` e nomes de eventos sobre fundo escuro |
| `--bt-hero-cream` | `#E8E6E3` | títulos principais do hero sobre ink/canvas, como `Hoje em` |
| `--bt-hero-on-deep` | `#E1DFDB` | display e texto de alto destaque sobre `--bt-signal-deep`, como `Quadra.`, horário e `BRT` |
| `--bt-hero-muted` | `#B5AEA5` | rótulos, data, contexto e texto auxiliar do hero sobre fundo escuro |
| `--bt-text-muted` | `#CDBFC5` | texto secundário que ainda precisa passar AA |
| `--bt-border` | `rgba(255,244,244,.18)` | divisores e campos em repouso |
| `--bt-border-strong` | `rgba(255,180,184,.38)` | seleção, agrupamento e foco adjacente |
| `--bt-focus` | `#FFD2D4` | outline de teclado independente do acento |
| `--bt-success` | `#72D6A0` | publicado/confirmado, sempre com texto/ícone |
| `--bt-warning` | `#F5C95C` | rascunho, revisão, adiado e stale |
| `--bt-danger` | `#FF7A82` | erro, rejeição e ação destrutiva |

Combinações obrigatórias: `--bt-text` sobre `--bt-ink|canvas|surface`; `--bt-ink` sobre `--bt-signal`; `--bt-hero-cream|hero-muted|display` sobre `--bt-ink|canvas`; `--bt-hero-on-deep` sobre `--bt-signal-deep`; `--bt-focus` como outline de 3px. Texto comum alcança 4,5:1 e texto grande/gráfico 3:1. Cor nunca é o único sinal.

Distribuição visual aproximada: **60%** `ink/canvas` como campo dominante, **30%** `surface/wine/signal-deep` para estrutura, profundidade e grandes recortes editoriais e **10%** `signal/signal-soft/display/hero-*` para ação, leitura e ênfase. `success`, `warning` e `danger` são exceções semânticas de estado e não entram como decoração na proporção.

### Tipografia

Fontes locais existentes: Barlow Condensed 600 para display e Inter 400/600 para interface. Sem CDN ou terceira família.

| Papel | Escala | Linha | Uso |
|---|---|---|---|
| `display` | `clamp(4.75rem, 13vw, 11.5rem)` | `.72–.82` | hero, manifesto e títulos editoriais públicos; reduzir por contêiner, não criar quinto token |
| `heading` | `clamp(1.5rem, 4vw, 3.5rem)` | `.9–1.15` | `h1` interno, `h2`, confronto, card importante e diálogo |
| `body` | `1rem` | `1.5` | instrução, valor, mensagem e texto de controle principal |
| `label` | `.875rem` | `1.3` | botão, filtro, cabeçalho, kicker e metadado; kicker pode usar caixa alta/tracking, não tamanho extra |

Os quatro papéis são semânticos: variações ópticas responsivas por contêiner e breakpoint são permitidas dentro do mesmo papel, mas não criam um quinto nível ou token ad hoc. Display usa tracking negativo leve; kicker usa tracking positivo. Nomes essenciais quebram linha e nunca são truncados. Números de horário/placar usam `font-variant-numeric: tabular-nums`.

### Espaço, grid, raio e profundidade

| Token | Valor | Contrato |
|---|---:|---|
| `space-1/2/4/6/8/12/16` | `4/8/16/24/32/48/64px` | única escala para `margin`, `padding` e `gap`; qualquer valor intermediário nesses usos deve ser arredondado para esta grade |
| `container` | `min(calc(100% - 32px), 1240px)` | 16px laterais em mobile; desktop pode usar padding interno de 24px ou 32px |
| `form-max` | `640px` | formulários e leitura longa |
| `radius-control` | `999px` | botões, tabs segmentadas, chips e filtros compactos |
| `radius-field` | `16px` | input, select, textarea e pequenos painéis |
| `radius-card` | `16px` | cards públicos e operacionais |
| `radius-feature` | `24px` | faixa esportiva, painel de próximo evento e diálogo |

Profundidade vem primeiro de contraste de superfícies e borda. Sombra padrão: `0 18px 52px rgba(9,8,13,.36)` apenas em elemento destacado; glow coral só em indicador pequeno. Blur de fundo não é base do sistema.

---

## Dois modos da mesma identidade

| Aspecto | Arena pública | Console (pessoal e operacional) |
|---|---|---|
| Objetivo | orientar, envolver e facilitar descoberta | permitir autocuidado claro na conta e decisão segura no trabalho operacional |
| Composição | broadside assimétrico, data/horário grandes, ticker e linhas de evento | shell contido; variante pessoal mais acolhedora e variante operacional mais densa, sem trocar de identidade |
| Display | token `display` no hero e nos títulos editoriais principais | token `heading`; sem manifesto gigante |
| Coral | faixa, horário, seleção e CTA | CTA, foco adjacente e seleção; áreas menores |
| Grainient | permitido somente na home/agenda, atrás de camada de contraste | proibido |
| Movimento | hero/ticker/entrada controlados | feedback curto de opacidade/transform; sem loop decorativo |
| Densidade | respiro editorial e eventos em linhas largas | pessoal com leitura guiada e formulários até 640px; operacional com tabelas responsivas e ações próximas do objeto |

Não existem temas separados nem troca de marca. Um usuário que sai da Arena para o Console reconhece imediatamente cor, tipografia, controles, foco, estados e voz.

---

## Componentes e padrões

Os nomes abaixo indicam responsabilidades; nomes de arquivos/classes serão definidos pelos PLANs.

### Shell e navegação

- `ArenaShell`: header preto, marca circular coral, links `Competições`, `Equipes`, `Atletas` e ação pill `Entrar`; sessão opcional nunca bloqueia conteúdo público.
- `ConsoleShell`: mesma marca/paleta, título do contexto, retorno `Arena` e duas variantes semânticas. A variante pessoal organiza conta/perfil/matrícula; a operacional apresenta destinos por capability e escopo. Não exibe rota sem autorização nem cria um terceiro tema.
- `SportRail`: lista horizontal de modalidades com `aria-pressed`; overflow rolável dentro do componente, não na página.
- `SkipLink`, foco pós-rota no `h1` e rodapé de independência são compartilhados.

Quando um destino ainda não existir na rota ou a API ainda não fornecer modalidades, o shell e o rail não criam links mortos nem rótulos fictícios: exibem somente `Competições`/`Entrar` e o contexto estático disponível. No 03-03, `SportRibbon` mantém a marca, a contagem e o estado da publicação; modalidades e detalhes ausentes ficam reservados aos planos que sustentam esses campos.

### Hero e sinalização esportiva

- `AutoEventHero`: duas composições do mesmo próximo evento, sem setas ou dots. Troca a cada ~5,2s com fade/clip curto, pausa em hover, foco, aba oculta e fora da viewport; com movimento reduzido fixa a primeira composição. Conteúdo é uma única fonte real, não dois eventos divergentes.
- `HighlightMotionControl`: controle pill visível de no mínimo 44px, próximo do hero, alterna `Pausar destaques`/`Retomar destaques`, informa `aria-pressed` e persiste a escolha durante a sessão da página. Pausa simultaneamente hero e ticker para teclado, toque e tecnologia assistiva; não é seta nem paginação.
- `BroadcastTicker`: próximos itens publicados em texto, subordinado ao mesmo `HighlightMotionControl`; com pausa ou movimento reduzido permanece estático e não é a única forma de acessar esses eventos.
- `SportRibbon`: bloco integrado de 84px com marca, modalidades e estado textual da agenda. Não pode sobrepor hero ou agenda em 320px.
- `DateLockup`: dia grande + mês/ano, associado semanticamente ao grupo de eventos.

### Controles

- Botão primário: fundo coral, texto ink, pill, mínimo 44px; hover escurece levemente e foco usa outline independente.
- Secundário: superfície/contorno, texto claro; terciário é link sublinhado ou button sem preenchimento.
- Destrutivo usa `--bt-danger`, verbo específico e confirmação contextual; nunca reutiliza coral de marca sem rótulo.
- `PeriodSegment` agrupa `Hoje` e `Próximos` com `aria-pressed`, rótulo principal e contagem auxiliar.
- `AgendaFilterDisclosure` é um botão pill com ícone+rótulo+badge; abre formulário rotulado, fecha com Escape e restaura foco.
- Controles disabled preservam legibilidade; loading mantém largura e informa verbo (`Salvando…`).

### Conteúdo e dados

- `EventRow` desktop: horário, contexto, confronto/nome, local e ação circular; em mobile vira grade de três colunas sem esconder nome/estado.
- `EventCard` é permitido em grades de entidade, mas a home usa linhas editoriais para preservar a assinatura V.
- `StatePanel` cobre loading, vazio, erro, desautorizado, conflito e stale com título, corpo e próxima ação.
- `StatusChip`, `SourceHistory`, `PublicationBanner`, `ResponsiveDataView`, `BracketView`, `ScopeSelector`, `WorkspaceSubnav`, `AthletePhotoField` e `SafeExternalLink` permanecem compartilhados.
- Tabela usa caption, header persistente e linhas com divisores; abaixo de 600px vira coleção de cards rotulados quando não puder refluir sem perda.

### Formulários e feedback

- Reactive Forms tipados; label acima, ajuda antes do erro, erro ligado por `aria-describedby`, foco no primeiro inválido após submit.
- Campos usam superfície escura, borda visível, raio 16px e altura mínima 44px. Placeholder nunca substitui label.
- Diálogo usa o componente acessível existente: foco inicial na ação segura, armadilha, Escape equivalente a cancelar e restauração ao gatilho.
- Sucesso é persistente e próximo da superfície com `aria-live="polite"`; toast temporário não é a única confirmação.
- Recarregar preserva snapshot válido e marca stale. Mutação bloqueia apenas seus controles e não recebe retry automático.

---

## Grainient/WebGL e fallback

O efeito aprovado é uma responsabilidade Angular isolada, não um componente React portado. Deve receber somente um host/canvas e configuração interna fechada.

- uso permitido: fundo da agenda pública na home, abaixo de overlays que garantem contraste;
- paleta: coral `#FF626B`, vinho `#741D42` e ink `#09080D`; velocidade baixa, grain discreto e saturação controlada;
- limite de 30 fps e DPR calculado para não ultrapassar aproximadamente 900 mil pixels; `powerPreference: low-power`;
- pausar quando fora da viewport, aba oculta ou `prefers-reduced-motion: reduce`; movimento reduzido desenha frame estático ou usa fallback;
- `ResizeObserver` e `IntersectionObserver` desconectados no destroy; listeners removidos; RAF cancelado; program/VAO/shaders liberados;
- `webglcontextlost` chama `preventDefault`, cancela loop e ativa fallback; restore reconstrói recursos sem log ruidoso;
- sem WebGL2, compilação falha ou contexto perdido: gradientes radiais/lineares CSS equivalentes mantêm leitura e identidade;
- canvas é `aria-hidden`, `pointer-events:none` e nunca contém informação, interação ou critério de sucesso.

---

## Arquitetura de informação e rotas

| Rota/superfície | Modo | Contrato |
|---|---|---|
| `/` | Arena pública | hero V + listagem real mínima; sem guard de sessão/perfil/capability |
| competição, evento, organização, equipe, atleta, chaveamento e classificação | Arena pública | deep links publicados, mesmo shell e intensidade editorial reduzida |
| `/conta/...` | Console pessoal | protegido por sessão/perfil; sem WebGL |
| `/operacao/...` | Console operacional | protegido por capabilities e escopo do BFF; sem rota inferida por papel |

Paths finais além de `/` são definidos nos PLANs. Nenhuma matrícula/PII entra em path/query/fragment. Links públicos são estáveis; conta/operação permanecem protegidas; UUID ou `scopeRef` nunca são digitados.

### Cabeçalho responsivo

- desktop: marca à esquerda, navegação pública central e ação de sessão à direita, todos alinhados na mesma linha;
- mobile: marca e `Entrar`/`Conta` ficam visíveis; links secundários migram para disclosure textual acessível, não desaparecem sem alternativa;
- `Operação` só existe com capability válida. Payload ausente/malformado falha fechado;
- perfil incompleto mantém o gate da Fase 2 apenas em rotas protegidas;
- primeiro foco é `Pular para o conteúdo principal`; menus fecham com Escape e restauram foco.

### Convergência obrigatória das rotas existentes

| Rota | Variante | Preservar sem alteração | Mudança visual obrigatória |
|---|---|---|---|
| `/conta/perfil/completar` | Console pessoal | gate de perfil, sessão, logout e validações | fluxo guiado, hierarquia de formulário, feedback e estados do contrato V |
| `/conta/perfil` | Console pessoal | `ProfileStore`, adapter, favorito e regras owner-only | composição de conta, seções responsivas e componentes compartilhados |
| `/conta/matricula` | Console pessoal | `EnrollmentStore`, adapter, normalização e mensagens funcionais | formulário, status e ajuda alinhados ao mesmo shell/tokens |
| `/operacao/painel` | Console operacional | capability do BFF, store, estados 401/403 e links autorizados | painel denso, contexto operacional e navegação consistente |
| `/operacao/papeis` | Console operacional | matriz de autorização, comandos, conflitos e auditoria | administração responsiva com formulários/tabela-cartões e feedback próximo |
| `/operacao/matriculas/corrigir` | Console operacional | permissão, validação, correção e trilha auditável | fluxo de correção seguro, legível e responsivo no Console |

As seis rotas não autorizam novos endpoints, dados, regras ou dependências. `AuthenticatedLayoutComponent` é uma ponte temporária: sua remoção pertence ao corte final de rotas e só ocorre com prova de equivalência de sessão, foco e navegação. `tracer` e `diagnostic` permanecem development-only e fora deste inventário de produto.

---

## Superfícies públicas

### P1 — Agenda pública em `/`

1. O `h1` visual usa a composição aprovada `Hoje em quadra.`. O nome acessível pode incluir contexto adicional, mas não duplicar outro `h1`. Se não existir próximo evento publicado, o hero vira um estado editorial honesto e não mostra horário/confronto fictício.
2. O `AutoEventHero` recebe o próximo evento real e alterna apenas a composição. Uma versão mostra data, contagem e contexto; a outra é mais direta. Ambas apontam ao mesmo evento e mantêm um único link `Abrir evento`.
3. A ordem de DOM é header → modalidades → hero → ticker acessível → faixa esportiva → data/título da agenda → período/filtros → resultados. Transformações visuais não alteram essa ordem.
4. A faixa mostra `Futsal · Vôlei · Xadrez` somente quando essas modalidades existem no recorte publicado; caso contrário, deriva rótulos reais ou usa o texto genérico `Competições do campus em um só lugar`. O status `Agenda publicada` exige resposta pública válida.
5. `Hoje` e `Próximos` formam `PeriodSegment`. `Filtrar agenda` abre `Data`, `Esporte`, `Competição e edição`, `Categoria`, `Fase` e `Estado`; opções vêm do catálogo e nenhum UUID é digitado.
6. `Aplicar filtros` é primário; `Limpar filtros` é secundário. Filtros ativos geram badge e URL compartilhável sem dado privado. Ao aplicar, anunciar a contagem e mover foco para o título/contagem dos resultados.
7. Resultados agrupam por data e usam `EventRow`: horário com fuso, estado textual, participantes/nome, esporte/categoria/fase, competição+edição, local/espaço ou `Local a definir` e link nomeado. O primeiro evento pode receber destaque sem parecer publicado/confirmado por isso.
8. Modalidade ainda não detalhada usa `Agenda básica` e não inventa placar, chave, pódio, raias, marcas ou estatística.
9. Atualização preserva o último snapshot válido. Falha posterior marca `Agenda da última atualização · não foi possível atualizar` e oferece `Tentar novamente`.

Estado vazio inicial: `Nenhum evento publicado` / `Ainda não há eventos publicados para acompanhar.`
Estado vazio filtrado: `Nenhum evento encontrado` / `Ajuste ou limpe os filtros para ver outros eventos.`

### P2 — Competição e edição

- Cabeçalho identifica separadamente competição e edição; seletor de edição usa nomes/anos publicados, não IDs.
- Resumo da competição é estável; a edição organiza `Agenda`, `Categorias e fases`, `Organizações e equipes`, `Chaveamento` e `Classificação` como seções/deep links reutilizáveis. Se uma seção não se aplica, ela é omitida, não desabilitada.
- Nunca construir uma “página gigante”: acima da dobra há identidade+próximos eventos; listas completas têm paginação ou carregamento explícito conforme decisão do planejador.
- Estado parcial nomeia ausência: `Chaveamento ainda não publicado`, `Classificação ainda não publicada` ou `Nenhuma equipe publicada nesta edição`; não inventa conteúdo.

### P3 — Evento

- `h1` com participantes ou nome público do evento; competição/edição, esporte, categoria e fase permanecem visíveis.
- Mostrar horário canônico formatado no fuso do evento, estado textual, local hierárquico (`Local → Espaço interno`) e observação pública. Endereço/mapa só aparecem quando presentes e seguros.
- Confronto direto mostra placar somente quando publicado/confirmado e identifica `Resultado confirmado`; resultado aguardando revisão/rascunho nunca vaza ao público.
- Fonte vigente aparece junto do resultado: título/órgão, data informada, momento de registro e link seguro quando houver. `Ver histórico de fontes` expande uma lista cronológica, mantendo fontes substituídas e o motivo público da correção.
- O histórico de fontes é uma subcoleção explícita: vazio usa `Nenhuma fonte anterior` e `A fonte atual é a única fonte publicada para este evento.`; uma entrada usa `1 fonte anterior`; múltiplas usam `{n} fontes anteriores`. O estado vazio não remove a fonte vigente nem transforma ausência de histórico em erro.
- `Adiado`, `Suspenso` e `Cancelado` são texto+ícone e explicação curta. Horário antigo não aparece como se vigente; remarcação publicada indica o novo horário.
- Participante vindo de vencedor anterior usa `Vencedor de {evento}` até estar definido. `Bye` é `Folga na rodada`, não placar 0×0.

### P4 — Organização esportiva

- Nome, avatar/monograma local, descrição pública quando houver e ação autenticada `Salvar organização favorita`; visitante recebe `Entre para escolher sua organização favorita` sem bloquear a página.
- Seções: equipes por edição/modalidade/categoria, elenco público autorizado, próximos eventos e resultados recentes. Favorito vale para a organização inteira; não escrever “equipe favorita”.
- Coleções usam quantidade consciente: `1 equipe`/`{n} equipes`, `1 evento`/`{n} eventos` e `1 resultado`/`{n} resultados`. Zero usa as cópias literais de **Vazios e erros**, nunca `0 equipes` como único conteúdo.
- Sem equipes, mostrar `Nenhuma equipe publicada` e a ação condicional `Ver outras edições` apenas quando existir outra edição publicada; sem alternativa real, o estado é informativo e não possui CTA. Elenco, próximos eventos e resultados possuem vazios próprios na tabela de cópia.
- Seleção salva apresenta confirmação persistente `Organização favorita atualizada.` Remoção usa ação secundária `Remover dos favoritos` com confirmação explícita do objeto.

### P5 — Equipe

- Nome da equipe, organização, edição, esporte e categoria formam o cabeçalho.
- Exibe elenco publicado, próximos eventos e resultados. Rascunho, motivo interno, matrícula, período privado e ator operacional não aparecem.
- Coleções usam `1 atleta`/`{n} atletas`, `1 evento`/`{n} eventos` e `1 resultado`/`{n} resultados`. Elenco, próximos eventos e resultados vazios usam as cópias literais da tabela; estados públicos somente leitura sem alternativa real não exibem CTA artificial.
- Um atleta pode aparecer em várias equipes/edições sem duplicar o perfil público; link leva à mesma identidade esportiva pública.

### P6 — Atleta

- Avatar padrão ou foto processada pela BETUFU, nome esportivo, curso/biografia/redes somente quando o atleta vinculado os publicou.
- Exibe equipes, modalidades, participações e resultados públicos. Estatísticas permitidas: participações, vitórias, derrotas e resultados disponíveis, sempre nomeadas e sem sugerir completude quando a fonte for parcial.
- Coleções usam `1 equipe`/`{n} equipes`, `1 participação`/`{n} participações` e `1 resultado`/`{n} resultados`. Ausências usam as cópias literais da tabela; sem conteúdo relacionado publicável, não há CTA porque o visitante não pode criar nem corrigir registro.
- Não exibe matrícula, HMAC, e-mail, UUID interno, identidade civil, autenticação, estado de proposta ou selo de UFU. Não usar `Verificado pela UFU`, brasão ou marca universitária.
- Biografia é texto simples com quebras preservadas; HTML não é interpretado. Links sociais exibem domínio/rótulo compreensível, não a URL inteira como única explicação.

### P7 — Chaveamento responsivo

- Desktop/tablet: uma coluna por rodada, da esquerda para a direita; cada rodada tem `h2`, cada confronto é um artigo/link com participantes, estado, horário e placar confirmado. Conectores são decorativos e não carregam informação exclusiva.
- Mobile `<600px`: uma rodada por vez, escolhida por controle nativo rotulado `Rodada do chaveamento`; botões `Rodada anterior`/`Próxima rodada` mantêm 44px. Não reduzir cartões até ficarem ilegíveis e não exigir arrasto lateral.
- Ordem do DOM segue rodadas e confrontos; leitor de tela compreende tudo sem os conectores. `Bye` e participante dependente são frases completas.
- A página inteira nunca rola horizontalmente em 320px. Se uma visão ampliada for oferecida em desktop, o overflow fica dentro de uma região `Chaveamento completo`, com instrução acessível, sem substituir a visão mobile contratada.
- O chaveamento é somente derivado dos eventos ligados. Não há edição direta de participante/placar na visualização nem botão de sorteio.

### P8 — Classificação e histórico de fonte

- Em `≥600px`, usar `<table>` com `caption`, `<thead>`, escopo de cabeçalho e colunas explicitamente fornecidas pela fonte. Não inventar pontos ou critério de desempate.
- Em `<600px`, a mesma coleção vira cartões rotulados; cada valor repete o nome da coluna (`Posição`, `Equipe`, etc.). Ordem e conteúdo devem ser equivalentes à tabela.
- Cabeçalho indica `Classificação transcrita da fonte oficial` e `Atualizada em {data/hora}`. Se houver nova fonte de correção, marcar a versão vigente sem apagar as anteriores.
- Fonte ausente bloqueia publicação de classificação/resultado; a UI operacional explica a correção. No público, ausência mostra o estado vazio, não erro técnico.

---

## Superfícies de conta

### C1 — Organização favorita

- Incorporar ao perfil ou subseção de conta: combobox/lista pesquisável de organizações publicadas por nome. Teclado percorre opções; resultado anuncia contagem; UUID nunca é exibido ou digitado.
- Estado sem favorito: `Nenhuma organização favorita escolhida.` CTA `Escolher organização favorita`.
- Salvar troca exige nome visível da organização na revisão. Remover usa confirmação `Remover {organização} dos favoritos? Você poderá escolher novamente depois.` e ação secundária `Manter como favorita`.
- Reconciliar o contrato público antigo `favoriteTeamId` sem mostrar a nomenclatura técnica; migration/API é gate do planejador.

### C2 — Proposta de vínculo com atleta

- Cartão aparece somente à conta afetada e contém nome esportivo, organização/equipe/contexto público suficiente para reconhecer a pessoa e aviso: `A coincidência de matrícula indica apenas que os dois cadastros informaram o mesmo valor. Ela não comprova vínculo oficial com a UFU nem titularidade.`
- Ações: `Aceitar vínculo` (primária), `Rejeitar vínculo` (destrutiva) e `Contestar vínculo` (secundária). Aceite confirma exatamente qual perfil será controlado. Rejeição abre diálogo com ação secundária `Manter proposta` e não é disparada por um único clique acidental.
- Ambiguidade, candidato já vinculado ou múltiplos candidatos não lista pessoas nem dados correlacionáveis. Mostrar `Não foi possível concluir o vínculo automaticamente.` e `Solicitar revisão` quando o backend autorizar.
- Sucessos persistentes: `Vínculo com atleta aceito.`; `Proposta de vínculo rejeitada.`; `Contestação enviada para revisão.`
- A matrícula digitada em outro fluxo nunca reaparece aqui, nem parcialmente.

### C3 — Perfil esportivo controlado pelo atleta

- Disponível somente após vínculo aceito. Campos opcionais: `Foto`, `Curso`, `Biografia` e `Redes sociais`. Equipes/modalidades/resultados são somente leitura porque vêm do cadastro esportivo.
- As três coleções somente leitura resolvem zero/um/muitos separadamente: equipes usam `Nenhuma equipe publicada`, `1 equipe` ou `{n} equipes`; modalidades usam `Nenhuma modalidade publicada`, `1 modalidade` ou `{n} modalidades`; resultados usam `Nenhum resultado publicado`, `1 resultado` ou `{n} resultados`. Ausência nessas coleções não habilita edição nem sugere falha do vínculo.
- Foto: seletor de arquivo nativo, preview local seguro, ações `Escolher foto`, `Enviar foto` e `Remover foto`. Exibir formatos/tamanho aceitos antes da seleção; valores exatos, transformação, varredura e política de cache são gates do planejador. Não aceitar URL remota nem hotlink.
- Após upload, usar somente a versão processada/servida pela BETUFU; remover metadados e ignorar nome original no endereço público. Falha preserva a foto vigente. Texto alternativo é `Foto de {nome esportivo}`; avatar padrão é decorativo se o nome já estiver adjacente.
- Estados de falha da foto usam literalmente estas cópias e nunca repetem nome de arquivo, metadado ou detalhe interno:

  | Falha | Título | Corpo | Próxima ação |
  |---|---|---|---|
  | Seleção inválida | `Foto não selecionada` | `Escolha uma imagem nos formatos e limites informados. Sua foto pública atual não foi alterada.` | `Escolher outra foto` |
  | Envio | `Não foi possível enviar a foto` | `Sua foto pública atual foi mantida. Verifique a conexão e tente enviar novamente.` | `Tentar enviar novamente` |
  | Processamento | `Não foi possível processar a foto` | `O arquivo não foi publicado e sua foto pública atual foi mantida. Escolha outra imagem nos formatos informados.` | `Escolher outra foto` |
  | Remoção | `Não foi possível remover a foto` | `Sua foto pública continua disponível. Tente remover novamente.` | `Tentar remover novamente` |

  A confirmação de remoção usa ação secundária `Manter foto`; falha sem foto vigente troca apenas `foto pública atual` por `avatar padrão atual`, sem afirmar que uma imagem existe.
- `Curso` é texto simples; `Biografia` tem contador não-live e ajuda persistente; limites exatos são decididos no planejamento e validados no servidor. `Redes sociais` usam rótulo de plataforma + URL HTTPS validada; não aceitar `javascript:`, `data:`, HTML ou markup.
- CTA `Salvar perfil esportivo`, desabilitado quando intocado/inválido. Remover qualquer campo opcional é possível. `Perfil esportivo atualizado.` é anunciado em `aria-live="polite"`.

---

## Workspace operacional escopado

### O1 — Seleção de escopo e estrutura

- Entrada mostra `h1` `Operação esportiva` e seletor `Competição`. As opções são somente competições autorizadas, com nome e edições disponíveis; nunca caixa de UUID, `scopeRef`, autocomplete arbitrário ou valor aceito da URL sem revalidação do BFF.
- Um único escopo pode ser pré-selecionado; anunciar `Competição {nome} selecionada.` Vários escopos exigem escolha e CTA `Abrir competição`. Um administrador de competição comum sem escopo autorizado vê `Nenhuma competição atribuída` e não recebe subnavegação, ferramentas nem CTA `Cadastrar competição`; esse estado não serve de bootstrap e não permite criar ou escolher escopo por conta própria.
- Escopo ativo permanece visível no cabeçalho do workspace. Troca com formulário sujo pede confirmação `Trocar de competição e descartar alterações não salvas?` e ação secundária `Continuar nesta competição`.
- Subnavegação: `Visão geral`, `Catálogo`, `Organizações e equipes`, `Atletas e elencos`, `Importar CSV`, `Eventos e resultados`, `Chaveamento e classificação`, `Revisões de vínculo`. Item existe somente se a capacidade autorizar.
- Cada subseção tem rota/deep link ou estado navegável próprio e carregamento independente. Nunca renderizar todas as ferramentas em uma página gigante.

### O2 — Catálogo

- O bootstrap da primeira competição vive em uma visão de plataforma `Competições`, anterior ao seletor O1 e acessível pelo menu `Operação` somente quando o BFF retornar uma capacidade fechada que autorize a função de `ADMINISTRADOR_GERAL`. O cliente não infere papel nem recebe/mapeia uma string de papel; nome técnico da capacidade e rota são gates do planejador. A visão carrega a lista de competições por nome. Somente uma resposta válida com zero itens mostra o bootstrap; falha de carga nunca mostra formulário de criação.
- Vazio do bootstrap: título `Nenhuma competição cadastrada`; corpo `Cadastre a primeira competição para iniciar o catálogo esportivo. O identificador e o escopo serão gerados pela plataforma.`; CTA `Cadastrar competição`. O CTA abre um formulário nomeado, sem campo de UUID ou `scopeRef`; ao salvar, o servidor gera o identificador e a resposta adiciona a competição à lista. A atribuição de administradores permanece uma operação autorizada separada e também seleciona a competição por nome.
- Erro bloqueante do bootstrap: título `Não foi possível carregar as competições`; corpo `Não foi possível confirmar se já existe uma competição cadastrada. Tente carregar novamente antes de iniciar o catálogo.`; ação `Tentar carregar competições`. Enquanto esse erro estiver ativo, `Cadastrar competição` não existe no DOM.
- Listas de esporte, competição, edição, categoria, fase e local usam busca, ordenação estável e ação explícita `Novo {objeto}`. Formulário hierárquico apresenta contexto pai em texto e seleção por nome.
- Local permite espaço interno, endereço, mapa e observação opcionais; `Local a definir` é estado válido do evento, não cadastro fictício.
- Registros exibem chips `Rascunho`/`Publicado`, versão e atualização. Ações principais: `Salvar rascunho`, `Pré-visualizar {objeto}` e `Publicar {objeto}`.
- `Catálogo esportivo ainda não iniciado` / `Cadastre o primeiro esporte para começar a estrutura esportiva desta competição.` / ação `Cadastrar esporte` é o vazio inicial. Quando já há esporte, os vazios dependentes são literais: `Nenhuma edição cadastrada` / `Cadastre uma edição para organizar categorias, fases e eventos.` / `Cadastrar edição`; `Nenhuma categoria cadastrada` / `Cadastre a primeira categoria desta edição antes de organizar fases e eventos.` / `Cadastrar categoria`; `Nenhuma fase cadastrada` / `Cadastre a primeira fase para organizar os eventos desta categoria.` / `Cadastrar fase`; `Nenhum local cadastrado` / `Cadastre um local reutilizável ou mantenha o evento como Local a definir.` / `Cadastrar local`.
- Erro bloqueante: título `Não foi possível carregar o catálogo`; corpo `Os cadastros esportivos desta competição não estão disponíveis agora. Tente carregar novamente antes de criar ou publicar registros.`; ação `Tentar carregar catálogo`.
- `Publicar {objeto}` abre revisão que nomeia objeto, escopo e versão; a ação secundária é `Voltar à edição`. Não há exclusão física contratada nesta fase.

### O3 — Organizações e equipes

- Organização é permanente; equipe exige edição, modalidade e categoria selecionadas. A interface mostra essa diferença antes de salvar.
- Página de organização reutiliza subseções, não duplica cadastro em cada edição. Página de equipe mostra elenco da edição e estado de publicação.
- Não permitir trocar organização/equipe por digitação de identificador. Seletores carregam opções do escopo e recusam referência inexistente sem criar implicitamente.
- Vazio sem organizações: título `Nenhuma organização cadastrada`; corpo `Cadastre a primeira organização esportiva antes de criar equipes para esta edição.`; ação `Cadastrar organização`. Vazio com organização, mas sem equipe: título `Nenhuma equipe cadastrada nesta edição`; corpo `Crie a primeira equipe escolhendo organização, esporte e categoria já cadastrados.`; ação `Cadastrar equipe`.
- Erro bloqueante: título `Não foi possível carregar organizações e equipes`; corpo `Os cadastros deste escopo não estão disponíveis agora. Tente novamente antes de criar ou alterar uma equipe.`; ação `Tentar carregar organizações e equipes`.

### O4 — Atletas e elencos

- Cadastro individual coleta nome esportivo e matrícula em campo transitório marcado `A matrícula será usada somente para gerar uma impressão protegida e não será exibida ou armazenada de forma consultável.` Sem comprovante, upload documental ou selo.
- Após envio, limpar o campo de matrícula e não repetir seu valor em resumo, erro, histórico, URL ou DOM persistente. Erro referencia o campo e a ação, não ecoa o número.
- Elenco seleciona atleta, equipe, modalidade, categoria e período por controles nomeados. Não há `Titular`, `Reserva` ou escalação por partida.
- Elenco publicado fica em leitura primeiro. Alteração tardia pede `Motivo da alteração` e confirmação que nomeia atleta/equipe, com ação secundária `Manter elenco atual`; histórico anterior permanece acessível a papel autorizado.
- Vazio sem atletas: título `Nenhum atleta cadastrado`; corpo `Cadastre o primeiro atleta individualmente ou use o modelo CSV BETUFU para criar rascunhos em lote.`; ações `Cadastrar atleta` e `Importar CSV`. Vazio sem elenco na equipe escolhida: título `Nenhum atleta no elenco desta equipe`; corpo `Adicione um atleta cadastrado e informe modalidade, categoria e período do vínculo.`; ação `Adicionar atleta ao elenco`.
- Erro bloqueante: título `Não foi possível carregar atletas e elencos`; corpo `Os atletas e vínculos deste escopo não estão disponíveis agora. Tente novamente antes de cadastrar ou alterar um elenco.`; ação `Tentar carregar atletas e elencos`.

### O5 — Importação tabular atômica (CSV e XLSX)

1. Exibir downloads `Baixar modelo CSV BETUFU` e `Baixar modelo XLSX BETUFU`; texto obrigatório: `Use um dos templates oficiais CSV UTF-8 ou XLSX. Planilhas flexíveis não são aceitas.`
2. Seletor aceita somente `.csv` ou `.xlsx`; não fazer upload automático ao escolher. Mostrar somente nome seguro do arquivo, formato detectado e ação `Validar arquivo`.
3. Estado de validação mostra contagem de linhas, referências reconhecidas e erros. Não ecoar matrícula ou conteúdo bruto sensível.
4. Havendo qualquer erro, banner `Arquivo não importado` / `Corrija todas as linhas indicadas e valide novamente. Nenhum registro foi salvo.` CTA de importação fica ausente/desabilitado.
5. Em `≥600px`, erros usam tabela com `Linha`, `Campo` e `Problema`; em `<600px`, cartões `Linha {n}` repetem esses rótulos. Mensagem nunca inclui stack, payload ou matrícula.
6. Arquivo totalmente válido mostra `Arquivo pronto para importar` e resumo por organização/equipe/categoria. CTA `Importar rascunhos` exige confirmação com ação secundária `Revisar arquivo`; sucesso: `{n} atletas e vínculos foram criados como rascunho.`
7. Uma falha de transporte depois de acionar `Importar rascunhos` e antes de receber resposta entra no estado indeterminado: título `Estado da importação não confirmado`; corpo `A conexão foi interrompida antes de a BETUFU confirmar o resultado. Não envie o arquivo novamente. Consulte o estado desta solicitação para saber se todos os rascunhos foram criados ou se nenhum registro foi salvo.` O arquivo selecionado e o resumo validado permanecem visíveis, sem qualquer contagem apresentada como concluída.
8. Quando houver consulta idempotente de estado, a ação primária é `Consultar estado da importação`. Ela é não mutável, nunca reenvia o CSV e pode resultar somente em: todos os rascunhos confirmados, nenhum registro salvo ou processamento ainda não concluído. A interface nunca apresenta “algumas linhas importadas”.
9. Se a consulta de estado estiver indisponível, manter o mesmo título e usar o corpo `Não foi possível consultar o estado da importação agora. O arquivo selecionado e o resumo foram mantidos nesta tela. Não reenvie o arquivo até que o resultado possa ser verificado.` Não há retry automático nem novo CTA de importação. Oferecer as ações não mutáveis `Copiar informações para suporte` e `Voltar ao painel operacional`; a cópia contém somente código de referência, competição e horário da tentativa, quando disponíveis, nunca conteúdo de linha, matrícula ou HMAC.
10. Esse estado indeterminado só existe depois do envio de um arquivo totalmente validado. Ele não substitui nem conflita com o erro de validação do item 4: `Arquivo não importado` continua sendo resultado definitivo de zero salvamentos e permite corrigir o arquivo antes de uma nova validação.

### O6 — Eventos e resultados

- Formulário de evento seleciona formato, edição, categoria, fase, participantes, horário, fuso, local/espaço e fonte. Para formato não detalhado, oferece apenas agenda básica.
- Lista operacional distingue `Rascunho`, `Publicado`, `Suspenso`, `Adiado`, `Cancelado`, `Aguardando revisão` e `Confirmado` por texto+ícone.
- Resultado exige fonte oficial registrada. Ações: `Salvar rascunho`, `Confirmar resultado` ou `Enviar para revisão`. O mesmo administrador pode confirmar no MVP; a UI não exige segundo aprovador.
- Correção abre nova versão e exige fonte substituta/corretiva ou motivo. Comparação mostra `Versão pública atual` e `Nova versão`; publicar não apaga a anterior.
- `Cancelar evento` é destrutiva e exige diálogo com nome, data, consequência pública e ação secundária `Manter evento`. Não movimenta créditos nesta fase e a interface não menciona liquidação.
- Vazio sem eventos: título `Nenhum evento cadastrado`; corpo `Cadastre o primeiro evento escolhendo edição, categoria, fase, participantes, horário, fuso e fonte.`; ação `Cadastrar evento`. Vazio sem resultado: título `Nenhum resultado aguardando registro`; corpo `Quando um evento publicado terminar, abra-o para registrar o resultado com a fonte oficial.`; ação `Abrir eventos sem resultado`.
- Erro bloqueante: título `Não foi possível carregar eventos e resultados`; corpo `Os eventos e resultados deste escopo não estão disponíveis agora. Tente novamente antes de registrar, confirmar ou corrigir um resultado.`; ação `Tentar carregar eventos e resultados`.

### O7 — Chaveamento e classificação

- Administração do chaveamento liga eventos existentes às rodadas e dependências; não edita cópia de participante ou placar. A ação `Pré-visualizar chaveamento` usa o mesmo `BracketView` público com rótulo `Pré-visualização não publicada`.
- Permite `bye`, disputa de terceiro lugar e vencedor de evento anterior. Não existe ação `Sortear chave`.
- Classificação é entrada tabular transcrita da fonte; colunas devem ser declaradas pelo contrato de dados da fase. A tela identifica claramente que não calcula desempate.
- Vazio do chaveamento: título `Nenhum chaveamento configurado`; corpo `Ligue eventos existentes às rodadas e dependências antes de publicar o chaveamento.`; ação `Configurar chaveamento`. Vazio da classificação: título `Nenhuma classificação transcrita`; corpo `Transcreva a classificação do boletim oficial e informe a fonte antes de publicar.`; ação `Transcrever classificação`.
- Erro bloqueante: título `Não foi possível carregar chaveamento e classificação`; corpo `As estruturas esportivas desta edição não estão disponíveis agora. Tente novamente antes de transcrever ou publicar.`; ação `Tentar carregar chaveamento e classificação`.
- Publicação exige fonte e versão, com CTAs `Pré-visualizar chaveamento`/`Publicar chaveamento` ou `Pré-visualizar classificação`/`Publicar classificação`; sucesso oferece `Ver versão pública` e a revisão usa ação secundária `Voltar à edição`.

### O8 — Fila de revisão de vínculos

- Lista apenas casos que o servidor autorizar, com motivo seguro (`Ambiguidade`, `Já vinculado`, `Contestação`) e contexto mínimo. Não exibe HMAC, matrícula, e-mail ou dados de outro candidato.
- Estado vazio: `Nenhum vínculo aguardando revisão` / `Não há propostas contestadas ou ambíguas neste escopo.`
- A ação abre detalhe auditado, nunca resolve na linha por clique único. Decisões disponíveis e dados necessários devem vir do contrato backend planejado; a UI não inventa associação manual por UUID.

---

## Contrato de estados, rede e concorrência

| Estado | Contrato visual e de interação |
|---|---|
| Loading inicial | Skeleton apenas para geometria previsível e texto `Carregando…` em `role="status"`; não anunciar cada cartão. Sem dados falsos. |
| Recarregando | Preservar conteúdo anterior, desabilitar somente o gatilho e usar rótulo verbal no gerúndio (`Atualizando…`, `Validando…`, `Publicando…`). |
| Vazio | Diferenciar ausência real de filtro sem resultado. Mostrar título, explicação e próximo passo definidos neste documento. |
| Populado | Ordenação estável, identidade da competição/edição e fonte/versão visíveis onde afetam interpretação. |
| Parcial | Manter blocos válidos; bloco ausente recebe `Indisponível` ou cópia específica. Dados opcionais ausentes são omitidos ou recebem fallback contratado (`Local a definir`, avatar padrão). |
| Stale/desatualizado | Preservar último snapshot, exibir horário e banner `Dados da última atualização; nova consulta não concluída.` Nunca parecer atual. |
| Erro sem dados | Painel bloqueante com problema seguro, `Tentar novamente` e `{correlationId}` quando fornecido. Não renderizar coleção vazia como sucesso. |
| Não autenticado | Conteúdo público continua. Ação protegida leva a `Entrar para continuar` com retorno seguro; não serializar formulário sensível na URL. |
| Não autorizado | `Você não tem acesso a esta área.` Sem revelar escopos, capacidades, existência de registros ou regra interna. Voltar à agenda ou ao escopo autorizado. |
| Sessão expirada | Em área protegida, limpar conteúdo privado e oferecer `Entrar novamente`; em página pública, manter agenda e mostrar aviso não bloqueante. |
| Conflito de versão | Bloquear publicação/salvamento e mostrar `Este registro foi alterado por outra pessoa. Recarregue para comparar a versão atual antes de tentar novamente.` Ações: `Recarregar versão atual` e `Copiar minhas alterações` quando seguro; nunca reenviar automaticamente. |
| Sucesso | Mensagem persistente próxima da superfície e `aria-live="polite"`; não depender de toast temporário. |

GETs públicos/operacionais podem ter retry limitado conforme padrão do projeto; mutações nunca recebem retry automático. Botão de mutação impede acionamento duplicado e o servidor continua autoritativo por idempotência/versão. Ao navegar com formulário sujo, confirmar descarte.

---

## Contrato de cópia

### CTAs primários

| Superfície | CTA |
|---|---|
| Agenda | `Aplicar filtros` |
| Organização favorita | `Salvar organização favorita` |
| Proposta atleta | `Aceitar vínculo` |
| Perfil esportivo | `Salvar perfil esportivo` |
| Escopo operacional | `Abrir competição` |
| Cadastro operacional | `Salvar rascunho` |
| CSV/XLSX válido | `Importar rascunhos` |
| Publicação | `Publicar {objeto}` |
| Resultado | `Confirmar resultado` |

### Vazios e erros

| Elemento | Cópia obrigatória |
|---|---|
| Agenda vazia | `Nenhum evento publicado` — `Ainda não há eventos publicados para acompanhar.` |
| Filtros sem resultado | `Nenhum evento encontrado` — `Ajuste ou limpe os filtros para ver outros eventos.` |
| Organização sem equipes | `Nenhuma equipe publicada` — `Esta organização ainda não possui equipes publicadas nesta edição.` — `Ver outras edições` somente quando houver outra edição publicada; caso contrário, sem CTA por ser leitura pública final. |
| Organização sem elenco público | `Nenhum elenco publicado` — `Ainda não há atletas publicados para as equipes desta organização nesta edição.` — sem CTA; visitante não administra elenco. |
| Organização sem próximos eventos | `Nenhum próximo evento` — `Não há eventos publicados para esta organização no período atual.` — `Ver agenda da competição` quando o contexto possuir agenda publicada; caso contrário, sem CTA. |
| Organização sem resultados | `Nenhum resultado publicado` — `Esta organização ainda não possui resultados publicados nesta edição.` — `Ver próximos eventos` somente quando a coleção existir; caso contrário, sem CTA. |
| Equipe sem elenco | `Elenco ainda não publicado` — `Nenhum atleta foi publicado para esta equipe nesta edição.` — sem CTA; visitante não administra elenco. |
| Equipe sem próximos eventos | `Nenhum próximo evento` — `Não há eventos publicados para esta equipe no período atual.` — `Ver agenda da competição` quando disponível; caso contrário, sem CTA. |
| Equipe sem resultados | `Nenhum resultado publicado` — `Esta equipe ainda não possui resultados publicados nesta edição.` — sem CTA quando não houver outro conteúdo relacionado. |
| Atleta sem equipes | `Nenhuma equipe publicada` — `Este atleta ainda não aparece em uma equipe publicada.` — sem CTA; o vínculo esportivo é cadastrado pela operação. |
| Atleta sem participações | `Nenhuma participação publicada` — `Ainda não há participações públicas registradas para este atleta.` — sem CTA; ausência é informação final, não erro. |
| Atleta sem resultados | `Nenhum resultado publicado` — `Ainda não há resultados públicos associados a este atleta.` — sem CTA quando não houver participação navegável. |
| Bootstrap da primeira competição | `Nenhuma competição cadastrada` — `Cadastre a primeira competição para iniciar o catálogo esportivo. O identificador e o escopo serão gerados pela plataforma.` — `Cadastrar competição`. Em falha: `Não foi possível carregar as competições` — `Não foi possível confirmar se já existe uma competição cadastrada. Tente carregar novamente antes de iniciar o catálogo.` — `Tentar carregar competições`. |
| Vazios operacionais O2–O4, O6 e O7 | Usar literalmente os títulos, corpos e próximas ações definidos nas respectivas subseções. Não reutilizar a cópia pública `Nenhum {objeto} publicado` em administração. |
| Erro público | `Não foi possível carregar estes dados. Verifique a conexão e tente novamente. Código de referência: {correlationId}.` |
| Erros bloqueantes O2–O4, O6 e O7 | Usar literalmente o erro e a ação contextual definidos na subseção; acrescentar `Código de referência: {correlationId}.` somente quando o BFF o fornecer. |
| Falhas da foto C3 | Usar literalmente `Foto não selecionada`, `Não foi possível enviar a foto`, `Não foi possível processar a foto` ou `Não foi possível remover a foto`, com corpo e próxima ação definidos em C3. |
| Fonte inválida/ausente | `Informe uma fonte oficial válida antes de publicar.` |
| CSV/XLSX inválido | `Arquivo não importado` — `Corrija todas as linhas indicadas e valide novamente. Nenhum registro foi salvo.` |
| CSV/XLSX com resultado não confirmado | `Estado da importação não confirmado` e os corpos literais de O5. Usar `Consultar estado da importação` quando disponível; sem consulta, preservar arquivo/resumo, proibir reenvio automático e oferecer `Copiar informações para suporte` e `Voltar ao painel operacional`. |
| Sem escopo | `Nenhuma competição atribuída` — `Sua conta não possui um escopo de competição disponível para operação.` |
| Vínculo ambíguo | `Não foi possível concluir o vínculo automaticamente.` — `Solicite revisão sem enviar novamente sua matrícula.` |
| Concorrência | `Este registro foi alterado por outra pessoa. Recarregue para comparar a versão atual antes de tentar novamente.` |

### Confirmações destrutivas ou de alto impacto

| Ação | Confirmação | Ação secundária com foco inicial |
|---|---|---|
| Rejeitar vínculo | `Rejeitar vínculo com {atleta}? Você não controlará este perfil. Se a proposta estiver incorreta, também poderá contestá-la.` | `Manter proposta` |
| Remover favorito | `Remover {organização} dos favoritos? Você poderá escolher novamente depois.` | `Manter como favorita` |
| Remover foto | `Remover sua foto pública? O avatar padrão será exibido.` | `Manter foto` |
| Trocar escopo sujo | `Trocar de competição e descartar alterações não salvas?` | `Continuar nesta competição` |
| Alterar elenco publicado | `Alterar o elenco publicado de {equipe}? Informe o motivo; a versão anterior permanecerá no histórico.` | `Manter elenco atual` |
| Importar CSV/XLSX válido | `Importar {n} atletas e vínculos como rascunho? Nenhum item ficará público nesta ação.` | `Revisar arquivo` |
| Cancelar evento | `Cancelar {evento} em {data}? A agenda pública será atualizada e a versão anterior permanecerá no histórico.` | `Manter evento` |
| Publicar objeto | `Publicar {objeto} na edição {edição}? Esta versão ficará disponível ao público.` | `Voltar à edição` |
| Conceder papel | Herdar título, corpo, ação primária, encerramento de sessões e auditoria do contrato da Fase 2. | `Manter papéis atuais` |
| Revogar papel | Herdar título, corpo, ação primária, encerramento de sessões e auditoria do contrato da Fase 2. | `Manter papel atual` |
| Revogar o próprio papel | Herdar título, corpo, ação primária, encerramento da sessão e aviso de recuperação do contrato da Fase 2. | `Manter meu acesso` |
| Corrigir matrícula | Herdar título, corpo, ação primária, motivo e auditoria do contrato da Fase 2. | `Manter matrícula atual` |

As quatro linhas herdadas acima substituem somente o rótulo visual secundário `Cancelar`; comportamento, ação primária, autorização, auditoria e encerramento de sessão permanecem semanticamente iguais. Botão destrutivo usa vermelho e nome específico. A ação secundária contextual recebe foco inicial; `Escape` produz o mesmo resultado e restaura o foco ao gatilho. Não existe fallback visual genérico para ação material/sensível, nem `Excluir competição`, `Excluir atleta` ou deleção de histórico neste contrato.

---

## Acessibilidade, teclado e movimento

- WCAG 2.1 AA é o baseline obrigatório; aplicar também alvo de 44px e foco não encoberto de WCAG 2.2 quando compatível. Contraste, zoom/reflow, ordem do DOM, nome/estado/valor e erros associados são medidos.
- Foco global: `outline: 3px solid #FFD2D4; outline-offset: 3px`. Não remover sem substituição equivalente. Coral de item ativo não substitui foco.
- Ordem na home: skip link → cabeçalho → modalidades → hero → ticker/faixa → agenda → filtros → eventos. A rota leva foco ao título; filtro aplicado leva ao título/contagem dos resultados.
- Combobox/select, disclosures, menus, diálogo e bracket funcionam com Tab/Shift+Tab, Enter, Espaço, setas quando o padrão nativo exigir e Escape. Não implementar clique em `<div>`.
- Tabelas têm `caption`; cartões mobile repetem rótulos. Placar lido como frase (`Equipe A 2, Equipe B 1`), não como sequência ambígua de números.
- `aria-live="polite"` para sucesso, filtros, upload e recarga; `role="alert"` somente para erro bloqueante/recusa. Não anunciar contadores a cada tecla.
- Foto possui alternativa; ícone decorativo fica oculto. Chaveamento não depende de linhas/conectores ou posição visual.
- Slides inativos do hero usam `aria-hidden="true"` e `inert`; ambos representam o mesmo evento. O ciclo pausa enquanto ponteiro/foco está no componente, fora da viewport ou com a aba oculta. `Pausar destaques` oferece pausa persistente independente dessas condições e controla também o ticker.
- Controles usam opacidade/transform de 120–200ms. A troca do hero pode durar até 780ms e o ticker pode ter loop linear lento; nenhum movimento altera layout ou é necessário para leitura.
- `prefers-reduced-motion: reduce` fixa o primeiro hero, remove ticker/entradas e desenha Grainient estático ou fallback. Sem parallax, scroll animation, blur animado ou reação obrigatória ao ponteiro.
- Conteúdo funciona a 200% de zoom e 320 CSS px sem perda, sobreposição, faixa solta ou scroll horizontal de página. Canvas, quadra decorativa, relógio fantasma e glow ficam fora da árvore acessível.

---

## Privacidade, mídia, fontes e links

- Matrícula é entrada transitória apenas em formulário autorizado. Não aparece em preview, mensagem, CSV de erro, DOM posterior, URL, analytics, storage do navegador ou log comum. HMAC nunca aparece.
- Público recebe somente registros publicados. Rascunho, versão privada, motivo interno, ator, correlation ID de terceiros e fila de vínculo não atravessam a fronteira pública.
- A BETUFU não afirma vínculo oficial com a UFU. Não usar “login UFU”, “SSO UFU”, “atleta verificado pela UFU”, brasão, selo ou identidade que sugira endosso.
- Foto é mídia própria processada e servida pela BETUFU; avatar padrão é local. Sem URL externa de imagem, CDN nova, pixel, iframe, script, asset remoto ou imagem gerada.
- Biografia é texto, nunca `innerHTML`. Link social/fonte/mapa passa por allowlist de esquema (`https` e, quando explicitamente suportado, rota interna); rejeitar `javascript:`, `data:`, credenciais embutidas e domínio vazio.
- Link externo identifica destino e abre de modo seguro (`rel="noopener noreferrer"` quando nova aba). A UI não busca preview, favicon ou Open Graph de terceiro.
- Fonte mostra título/rótulo, origem e data; URL longa pode quebrar com `overflow-wrap:anywhere`, mas o texto do link continua compreensível. Fonte substituída permanece no histórico e recebe `Substituída`/`Corrige versão anterior` em texto.
- Nenhum campo da fase usa comprovante, documento ou upload genérico. O input de foto e a importação tabular CSV/XLSX têm contratos e endpoints separados.

---

## Publicação, versão e feedback de concorrência

1. Todo editor começa em `Rascunho`; `Salvar rascunho` não publica.
2. `Pré-visualizar {objeto}` usa os componentes públicos, com banner persistente `Pré-visualização não publicada` e sem URL pública indexável.
3. Revisão de publicação exibe entidade, competição/edição, fonte quando obrigatória e versão base. O CTA é `Publicar {objeto}` e a ação secundária é `Voltar à edição`.
4. Sucesso exibe `Publicado em {data/hora}` e `Ver versão pública`.
5. Correção cria nova versão, identifica a vigente e exige fonte corretiva/substitutiva ou motivo, conforme o tipo. Histórico permanece navegável.
6. Toda resposta de conflito conserva a edição local em memória enquanto a pessoa compara ou copia; nunca mescla ou sobrescreve automaticamente.
7. Resultado `Aguardando revisão` não é público como confirmado. A fila permite que outro administrador ajude, mas não cria aprovação dupla obrigatória.
8. Timestamps exibidos ao público têm fuso compreensível; timestamps operacionais incluem data, hora e fuso. O planejamento escolhe formato técnico, não a omissão do fuso.

---

## Responsividade e densidade

| Superfície | 320–599px | 600–899px | ≥900px |
|---|---|---|---|
| Hero V | uma coluna; título 76–104px; painel do evento abaixo; faixa sem sobreposição | uma coluna larga ou grid equilibrado conforme conteúdo | manifesto em duas colunas; horário e confronto alinhados pela mesma grade |
| Agenda | date lockup 92px + título; filtros em disclosure; `EventRow` 92px/1fr/48px, com ação integralmente contida | filtros em duas colunas; evento conserva local quando couber | date lockup 160px; evento 160px/1fr/230px/52px |
| Cabeçalho | marca + `Entrar`/`Conta`; navegação secundária em disclosure textual | uma linha quando couber | marca, links e ação na mesma linha |
| Faixa esportiva | grid marca+texto; status pode ir para segunda linha, nunca sair do contêiner | três colunas compactas | três colunas, até 1240px |
| Competição/entidade | seções empilhadas e links de salto | grade 2 | resumo + grade, até 1200px |
| Classificação | cartões rotulados | tabela semântica | tabela semântica |
| Erros CSV/XLSX | cartões por linha | tabela | tabela |
| Chaveamento | uma rodada por vez | colunas quando couber ou seletor | colunas por rodada dentro do contêiner |
| Workspace | seletor + subnav envolvente/vertical; uma subseção | duas colunas em formulários compatíveis | conteúdo e painel auxiliar; nunca página gigante |
| Console pessoal | título/contexto e ações em uma coluna; navegação de conta em disclosure ou rail envolvente | conteúdo principal até 640px e resumo lateral quando útil | shell contido, navegação persistente e leitura guiada sem densidade operacional artificial |
| Operação legada | cards rotulados e ações empilháveis; nenhuma tabela força overflow de página | tabela ou cards conforme conteúdo | tabela/painel denso com ação junto ao registro e contexto de capability visível |
| Formulário | uma coluna, ações empilháveis | até 640px | até 640px centralizado/alinhado ao conteúdo |

Breakpoints são dirigidos por conteúdo; os intervalos acima são testes mínimos. Textos, chips e ações podem envolver. Somente metadado redundante pode truncar visualmente, mantendo nome acessível completo; entidade, estado, fonte, placar e erro nunca são truncados. Elementos decorativos usam `max-width`, `overflow: clip/hidden` local e nunca definem `min-width` da página.

---

## Mapa de aplicação por plano

| Plano | Superfície/modo | Contrato visual consumido |
|---|---|---|
| 03-03 | `/`, Arena | tokens base, ArenaShell, hero V, faixa, período/filtros, `EventRow`, Grainient/fallback e estados da listagem real mínima |
| 03-04 | Console O1/O2 | ConsoleShell, escopo, catálogo/local, formulário, vazio/erro e capability fechada; sem WebGL |
| 03-05 | público + conta + Console O2 | organização/equipe, favorito e formulários operacionais compartilhados |
| 03-06 | público + Console O3 | atleta global, seam transitório de matrícula e estados privados sem exposição pública |
| 03-07 | conta + Console O8 | proposta, confirmação segura, contestação, ambiguidade e fila de revisão |
| 03-08 | público + Console O4 | elenco publicado/rascunho, versão, publicação e correção |
| 03-09 | Console O5 | upload CSV/XLSX separado, templates fechados, preview, erros responsivos, atomicidade e estado não confirmado |
| 03-10 | público + Console O6 | evento/resultado, lifecycle, fonte, concorrência e feedback de versão |
| 03-11 | Arena interna P1–P6 | expansão das leituras públicas usando o mesmo shell, cards e estados, sem repetir o hero da home |
| 03-12 | público + Console O7 | `BracketView` responsivo e derivado |
| 03-13 | público + Console O7 | classificação tabela/cards, fonte e versão |
| 03-14 | público + conta | perfil esportivo textual/social, owner e links seguros |
| 03-15 | público + conta | `AthletePhotoField`, mídia processada, fallback e falhas preservadoras |
| 03-16 | Console O1–O8 | capabilities, navegação, escopo persistente e ausência de destinos não autorizados |
| 03-19 | Console pessoal | convergência de perfil incompleto, perfil e matrícula; mesma lógica/stores/guards, sem WebGL |
| 03-20 | Console operacional legado | convergência de painel, papéis e correção de matrícula; corte de rotas e aposentadoria comprovada do `AuthenticatedLayoutComponent` |
| 03-17 | todos | convergência das 26 superfícies, browser real, acessibilidade, privacidade, performance e regressão visual |

03-03 é o único plano que inaugura a home V e sua listagem real mínima. 03-04 concentra a expansão completa O1. Planos posteriores estendem componentes e rotas sem criar uma segunda linguagem visual.

---

## Expectativas de aceitação em navegador real

O plano deve reservar checkpoint humano/versionado após testes automatizados. Registrar no artefato de evidência: commit/HEAD, build, navegador e versão exata, viewport, rota, identidade/capacidade usada e resultado.

Provas mínimas:

1. Chrome/Chromium real suportado, versão exata registrada, em 320×800 e 1280×800; ao menos uma repetição em outro navegador suportado definida pelo planejamento.
2. Visitante abre `/`, filtra agenda e percorre competição, evento, organização, equipe, atleta, classificação e chaveamento sem login e sem request protegido bloqueando a pintura pública.
3. Pessoa autenticada vê `Conta`; `Operação` aparece somente por capacidade. 401/403 e payload malformado falham fechados sem vazar rota/escopo.
4. Percurso somente por teclado em agenda, filtros, menus, combobox, diálogo, tabela/cartões, bracket mobile, CSV e publicação; foco sempre visível e restaurado.
5. Nenhuma rolagem horizontal da página em 320px; classificação e erros CSV/XLSX viram cartões; bracket troca rodada sem arrasto obrigatório.
6. Loading, vazio, populado, parcial, stale, erro, não autenticado, não autorizado e conflito de versão são exercitados com fixtures determinísticas.
7. CSV e XLSX com uma linha inválida provam zero salvamentos e ausência de matrícula no DOM/mensagem; arquivos válidos criam somente rascunhos.
8. Upload rejeita tipo/tamanho inválido, mantém foto anterior em falha e entrega somente mídia processada sem metadado público.
9. Fonte/link malicioso (`javascript:`, `data:`, credencial embutida) não vira link; biografia não executa HTML.
10. Publicação/concorrência em duas sessões prova que a segunda não sobrescreve a primeira e recebe orientação de comparação.
11. Inspeção de DOM/storage/network confirma ausência de matrícula, HMAC, token e rascunho em superfície pública.
12. O hero alterna somente duas composições do mesmo evento, pausa em hover/foco/offscreen/aba oculta e fixa a primeira com movimento reduzido; slides inativos não entram na ordem de foco.
13. WebGL ativo respeita limite de fps/DPR; WebGL indisponível, compilação falha e perda/restauração de contexto mantêm fallback legível, sem warning recorrente e sem recursos/listeners após destroy.
14. Arena e Console compartilham tokens, controles, foco e estados; o Console não carrega canvas, ticker ou animação em loop.
15. `prefers-reduced-motion`, zoom 200%, foco de 3px, alvos 44px e contraste WCAG 2.1 AA são medidos, não inferidos apenas por screenshot.
16. As três rotas `/conta/...` preservam guards, stores, validações e resultados funcionais enquanto usam a variante pessoal do `ConsoleShell` em 320px e desktop.
17. As três rotas `/operacao/...` preservam capabilities e falha fechada; navegação oculta não substitui 403 do servidor e nenhum estado privilegiado reaparece após perda de autorização.
18. Nenhuma route table importa ou monta `AuthenticatedLayoutComponent` após o corte; development mantém `tracer`/`diagnostic`, production mantém ambos ausentes e os estados de sessão continuam cobertos.

Screenshot isolado não prova teclado, privacidade, atomicidade ou concorrência. Testes de worker não substituem a repetição independente do orquestrador exigida por `AGENTS.md`.

---

## Decisões arquiteturais para o planejamento

O visual e a interação acima são fechados; os itens seguintes devem ser decididos tecnicamente pelo planejador, sem mudar o resultado de UI:

| Gate | O planejador deve definir |
|---|---|
| Branch de rotas | Como separar shell/rotas públicas dos guards atuais e como compor development/production sem duplicação. |
| URLs públicas | Paths finais, slug/ID público e canonical/deep-link; somente `/` já está fechado. |
| Navegação do workspace | Subrotas, componentes reutilizáveis e capacidade necessária por subseção. |
| Estado e cache | Stores, paginação, ordenação, query de filtros, política de stale e retry apenas para GET. |
| Concorrência | Campo de versão/ETag, resposta Problem Details e fluxo de comparação/cópia segura. |
| Mídia | Limites de tipo/tamanho/dimensão, pipeline de decode/reencode, remoção de metadados, URL pública e deleção/substituição. |
| CSV/XLSX | Templates fechados, limites de arquivo/linha/célula, encoding/BOM, tipos/fórmulas/ZIP seguro, contrato de erro, idempotência e progresso sem sucesso parcial. |
| Fontes/links | Schema, allowlist de esquemas/domínios quando necessária e política de abertura externa. |
| Tabelas/classificação | Colunas genéricas permitidas pela fonte sem codificar regra esportiva ou desempate. |
| Aceitação | Browsers/versões suportados e fixtures determinísticas para todos os estados. |
| Efeito Angular | API mínima do host de Grainient, estratégia de fallback, lifecycle e teste de cleanup sem expor uma API React. |
| Tokens | Como reconciliar tokens herdados sem manter simultaneamente a paleta azul/laranja antiga nas superfícies migradas. |
| Convergência legada | Como derivar a variante pessoal/operacional do `ConsoleShell`, migrar as seis rotas sem tocar stores/adapters/guards e retirar `AuthenticatedLayoutComponent` somente após prova de equivalência. |

Nenhum gate autoriza adicionar dependência, criar component library, gerar imagem ou reabrir D-53–D-94 sem aprovação específica. Ajustar os tokens existentes para este contrato faz parte da migração D-88; manter duas paletas concorrentes não é uma alternativa válida.

---

## UI Considerations

Inventário para o checker fresco da reabertura do 03-18: **26 superfícies** e os **6 tipos de elemento** (`form`, `list-collection`, `nav`, `media`, `interactive-control` e `static-content`). O parecer de 20 superfícies continua histórico, mas não certifica a ampliação D-95–D-100.

IDs: `E` vazio, `L` loading, `R` erro, `P` populado, `A` parcial, `O` overflow, `Z` zero/um/muitos e `T` texto longo. Elementos: `form`, `list`, `nav`, `media`, `control`, `static`. Todas as linhas abaixo são **explicit**; não existe backstop implícito.

| # | Superfície | Elementos | IDs aplicáveis | Referência contratada | Dispensa explícita |
|---:|---|---|---|---|---|
| 1 | Shell/navegação | `nav, control, static` | `L,R,P,A,O,T` | Shell, cabeçalho responsivo, sessão/capability e foco | `E,Z`: o shell sempre possui marca/main; destinos ausentes são `A`, não coleção vazia |
| 2 | Agenda `/` | `form,list,nav,media,control,static` | `E,L,R,P,A,O,Z,T` | P1, hero/faixa/filtros, estados, responsividade | — |
| 3 | Competição/edição | `list,nav,media,control,static` | `E,L,R,P,A,O,Z,T` | P2 e estados públicos | — |
| 4 | Evento | `list,media,control,static` | `E,L,R,P,A,O,Z,T` | P3, fonte, histórico de fontes, lifecycle e concorrência | A entidade é singular; `E/Z` pertencem à subcoleção cronológica de fontes |
| 5 | Organização | `list,media,control,static` | `E,L,R,P,A,O,Z,T` | P4, favorito e coleções por edição | — |
| 6 | Equipe | `list,media,control,static` | `E,L,R,P,A,O,Z,T` | P5, elenco/eventos/resultados | — |
| 7 | Atleta | `list,media,control,static` | `E,L,R,P,A,O,Z,T` | P6, avatar/foto e privacidade | — |
| 8 | Chaveamento público | `list,nav,control,static` | `E,L,R,P,A,O,Z,T` | P7 e `BracketView` | — |
| 9 | Classificação/fontes | `list,control,static` | `E,L,R,P,A,O,Z,T` | P8, `ResponsiveDataView` e histórico | — |
| 10 | Favorito | `form,list,control,static` | `E,L,R,P,A,O,Z,T` | C1 e confirmações | — |
| 11 | Proposta de vínculo | `list,control,static` | `E,L,R,P,A,O,Z,T` | C2, ambiguidade e owner | — |
| 12 | Perfil esportivo/foto | `form,list,media,control,static` | `E,L,R,P,A,O,Z,T` | C3, equipes/modalidades/resultados somente leitura, falhas de foto e links seguros | O formulário é singular; `Z` pertence às três subcoleções somente leitura |
| 13 | Seleção de escopo | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | O1 e capability fechada | — |
| 14 | Catálogo | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | O2 e bootstrap seguro | — |
| 15 | Organizações/equipes operacionais | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | O3, publicação e erros contextuais | — |
| 16 | Atletas/elencos operacionais | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | O4, versões e propostas | — |
| 17 | Importação CSV/XLSX | `form,list,control,static` | `E,L,R,P,A,O,Z,T` | O5, templates fechados, preview, erros e estado não confirmado | — |
| 18 | Eventos/resultados operacionais | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | O6, lifecycle, fonte e conflito | — |
| 19 | Chaveamento/classificação operacional | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | O7, derivação/transcrição e versão | — |
| 20 | Fila de revisão | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | O8, dados mínimos e decisão auditada | — |
| 21 | Perfil incompleto | `form,nav,control,static` | `L,R,P,A,O,T` | `/conta/perfil/completar`, gate e Console pessoal | `E,Z`: o fluxo existe porque o perfil está incompleto; ausência encerra o gate |
| 22 | Perfil da conta | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | `/conta/perfil`, identidade/favorito e Console pessoal | — |
| 23 | Matrícula pessoal | `form,nav,control,static` | `E,L,R,P,A,O,T` | `/conta/matricula`, status e validação preservada | `Z`: entidade singular; ausência usa `E` |
| 24 | Painel operacional legado | `list,nav,control,static` | `E,L,R,P,A,O,Z,T` | `/operacao/painel`, capability e Console operacional | — |
| 25 | Administração de papéis | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | `/operacao/papeis`, autorização e feedback auditável | — |
| 26 | Correção de matrícula | `form,list,nav,control,static` | `E,L,R,P,A,O,Z,T` | `/operacao/matriculas/corrigir`, validação e correção segura | — |

Critérios transversais `L/R/O/T` são verificados em todas as 26 superfícies; as dispensas acima são semânticas, não atalhos visuais. Planos consumidores devem citar a linha e a seção de estado correspondente em seus testes/aceitação.

---

## Segurança de registro

| Registro | Blocos usados | Safety Gate |
|---|---|---|
| shadcn official | nenhum | Não aplicável — stack Angular e `components.json` ausente, confirmado em 2026-08-26. |
| Terceiros | nenhum | Nenhum registry, bloco, dependência, asset remoto ou vetting pendente. |
| Componentes BETUFU | shell, diálogo, tokens e padrões standalone já existentes | Código local inspecionado em 2026-08-26; reutilização sem instalação. |

---

## Checker Sign-Off

- [x] Dimensão 1 Copywriting: PASS
- [x] Dimensão 2 Visuals: PASS
- [x] Dimensão 3 Color: PASS
- [x] Dimensão 4 Typography: PASS
- [x] Dimensão 5 Spacing: PASS
- [x] Dimensão 6 Registry Safety: PASS

**Approval anterior:** o gate A de 20 superfícies foi aprovado em 2026-08-28, ledger `7 → 2 → 0` em `03-VALIDATION.md`.

**Estado da reabertura:** PASS A2 — o `gsd-ui-checker` fresco aprovou as 26 superfícies e as decisões D-95–D-100 com `actionable_count: 0` (`BLOCK=0`, `FLAG=0`). O parecer anterior de 20 superfícies permanece apenas histórico; B2/C2 foram fechados posteriormente em `03-VALIDATION.md`.

**Refinamento do 03-03:** a implementação da Arena V preserva o contrato estrutural e acrescenta os papéis cromáticos `signal-deep`, `display` e `hero-*` registrados acima. O responsável aprovou visualmente essa versão em 2026-08-29. A auditoria fresca final do snapshot implementado retornou `PASS`, sem achados P0/P1/P2; evidências, limitações e suítes estão registradas em `03-VALIDATION.md`.
