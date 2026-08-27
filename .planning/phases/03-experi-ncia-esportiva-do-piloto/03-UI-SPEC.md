---
phase: 3
slug: experi-ncia-esportiva-do-piloto
status: draft
shadcn_initialized: false
preset: none
created: 2026-08-26
---

# Fase 3 — Contrato de Design de UI

> Contrato visual e de interação para a experiência esportiva pública, o perfil esportivo da conta e a administração escopada do piloto.
> Este documento estende `01-UI-SPEC.md` e `02-UI-SPEC.md`; não os substitui. Onde este contrato cala, valem os contratos anteriores.

---

## Escopo e decisões de origem

| Decisão | Contrato executável | Fonte |
|---|---|---|
| Entrada pública | `/` renderiza a agenda antes de qualquer barreira de sessão, perfil ou operação. Visitante sem conta pode navegar por todo dado publicado. | `03-CONTEXT.md` D-62; `AUTH-01`; direção aprovada |
| Catálogo genérico | A interface apresenta sempre `Esporte → Competição → Edição → Categoria → Fase → Evento`; “Olimpíada Universitária UFU 2026” é a primeira instância, não a estrutura da aplicação. | D-53–D-55 |
| Organização permanente | Favorito aponta para a organização esportiva; equipes e elencos pertencem à edição. Atleta é global e pode participar de vários elencos. | D-56–D-58; `SPRT-13`, `SPRT-15` |
| Publicação | Rascunho e pré-visualização são privados; dado público exige publicação explícita. Correção exige fonte ou motivo, preserva versões e nunca sobrescreve concorrência silenciosamente. | D-60–D-61; `SPRT-09`, `SPRT-11` |
| Administração escopada | A pessoa escolhe uma competição dentre os escopos autorizados e trabalha em subseções reutilizáveis. UUID e `scopeRef` nunca são digitados. Visibilidade por capacidade é conveniência; autorização do BFF prevalece. | D-59; `OPER-02`; direção aprovada |
| Chaveamento e classificação | Chaveamento é leitura derivada dos eventos, inclui rodadas, `bye`, terceiro lugar e vencedor de evento anterior. Classificação é transcrição do boletim, não cálculo automático. | D-64–D-65; `SPRT-14` |
| Resultado e fonte | Resultado público indica fonte vigente e histórico. Aguardando revisão é opcional; confirmação atualiza agenda e chaveamento, sem crédito, odd ou liquidação nesta fase. | D-66–D-68; `SPRT-11` |
| Vínculo atleta–conta | Coincidência de HMAC cria proposta, não vínculo automático nem verificação oficial. Aceite, rejeição e contestação são explícitos; ambiguidades vão para revisão autorizada. | D-69–D-71; `SPRT-04`–`SPRT-08` |
| Elenco e CSV | Elenco é rascunho publicável. Importação aceita apenas CSV UTF-8 do modelo BETUFU, valida tudo e salva zero se qualquer linha falhar. Não cria referências implícitas. | D-72–D-75; `SPRT-02`, `SPRT-09` |
| Perfil do atleta | Após o aceite, somente o atleta altera foto, curso, biografia e redes sociais. Perfil público não expõe matrícula, HMAC, e-mail, autenticação ou identificador interno. | D-76–D-78; `SPRT-16` |
| Fora da fase | Não renderizar apostas, odds, liquidação, comprovantes, escalações, XLSX, estatísticas específicas por modalidade, sorteio de chave ou motor multi-competidor automático. | Limite da fase; D-54, D-73, D-77, D-79–D-80 |

Não há `RESEARCH.md` para a fase. O stack e os padrões deste contrato foram pré-populados do código atual, de `_tokens.scss`, dos UI-SPECs anteriores e de `03-CONTEXT.md`. Nenhuma decisão D-53–D-80 é reaberta.

---

## Sistema de design

| Propriedade | Valor |
|---|---|
| Ferramenta | Nenhuma. Projeto Angular; o gate shadcn não se aplica e `components.json` está ausente. |
| Preset | Não aplicável. |
| Biblioteca de componentes | Nenhuma biblioteca externa. Reutilizar componentes standalone atuais e HTML semântico; não instalar biblioteca, scaffold ou registry. |
| Ícones | Material Symbols Rounded auto-hospedados. Ícone decorativo usa `aria-hidden="true"`; ação mantém texto visível ou nome acessível completo. |
| Fontes | Inter 400/600 para interface; Barlow Condensed 600 para títulos. Arquivos locais existentes; sem CDN ou nova família. |
| Tema | Escuro único, mesma identidade BETUFU para visitante e pessoa autenticada. |
| Estado | Stores por feature com `signal`/`computed`, adaptadores sobre o cliente OpenAPI e conteúdo anterior preservado durante recarga, conforme o padrão forte do painel operacional da Fase 2. |
| Formulários e diálogo | Reactive Forms tipados; rótulo persistente, erro ancorado ao campo, foco no primeiro inválido. Reutilizar o diálogo acessível atual para confirmações compatíveis, com foco inicial na ação secundária contextual (`Manter proposta`, `Manter foto`, `Continuar nesta competição`, `Manter elenco atual`, `Manter evento`, `Voltar à edição` ou o equivalente literal contratado), `Escape` com o mesmo efeito dessa ação, armadilha e restauração de foco. Ação material/sensível não usa rótulo secundário genérico. |

Padrões visuais reutilizados: cartões com borda e raio de 8px; estado por texto+ícone+cor; erro bloqueante em `role="alert"`; sucesso persistente em `aria-live="polite"`; código de referência selecionável; listas operacionais que preservam dados anteriores e os marcam como desatualizados quando a recarga falha.

### Componentes que esta fase precisa

Os nomes são responsabilidades visuais, não nomes de classes obrigatórios:

- `PublicShell` e `AuthenticatedShell`: compartilham marca, tokens, rodapé de independência e `main`; diferem apenas nas ações autorizadas do cabeçalho.
- `AgendaFilters`, `EventCard` e `CatalogCard`: filtros e cartões públicos reutilizados em agenda, competição, organização, equipe e atleta.
- `StatePanel`: loading, vazio, erro, desautorizado e dado anterior; não criar uma variante visual incompatível por feature.
- `StatusChip`, `SourceHistory` e `PublicationBanner`: estado textual, fonte vigente/histórica e situação de rascunho/publicação/versão.
- `ResponsiveDataView`: uma única coleção semântica com tabela em `≥600px` e cartões rotulados em `<600px`; obrigatório para classificação e erros de CSV.
- `BracketView`: apresentação dedicada e derivada dos eventos, com leitura acessível independente dos conectores visuais.
- `ScopeSelector` e `WorkspaceSubnav`: seleção de competição por nome e navegação pelas subseções operacionais.
- `AthletePhotoField` e `SafeExternalLink`: upload/remoção de mídia própria e links validados, sem hotlink de imagem externa.

---

## Escala de espaçamento

Valores declarados, todos já existentes em `frontend/src/styles/_tokens.scss`:

| Token | Valor | Uso obrigatório nesta fase |
|---|---:|---|
| `$space-1` | 4px | vão de ícone e texto, metadado compacto |
| `$space-2` | 8px | filtros compactos, chips, linhas de fonte e ações relacionadas |
| `$space-4` | 16px | controles, cartões mobile e recuo lateral 320–599px |
| `$space-6` | 24px | padding de cartão, grupo de formulário e recuo 600–899px |
| `$space-8` | 32px | grade desktop e recuo lateral a partir de 900px |
| `$space-12` | 48px | separação entre blocos principais de uma página |
| `$space-16` | 64px | respiro de página e estados centrais |

Exceções permitidas: alvo mínimo `$min-touch-target` de 44×44px; ícone inline de 20px ou em controle de 24px; foco de 3px com deslocamento de 3px. Não criar espaçamento arbitrário. `$container-max-width: 1200px` limita páginas; `$form-max-width: 640px` limita formulários. Uma grade larga nunca amplia o viewport da página.

| Faixa | Contrato |
|---|---|
| Mobile | 320–599px; 16px laterais; uma coluna; filtros em disclosure/formulário vertical; tabela vira cartões; cabeçalho mantém rótulos legíveis; nenhuma rolagem horizontal da página. |
| Tablet | 600–899px; 24px laterais; grades de duas colunas quando houver espaço; tabelas semânticas permitidas. |
| Desktop | ≥900px; contêiner até 1200px com 32px laterais; agenda e catálogo em grade; workspace pode usar subnavegação horizontal envolvente, nunca sidebar obrigatória. |

---

## Tipografia

Exatamente quatro tamanhos e dois pesos, herdados. Não introduzir 12px, 15px, 18px, 28px, peso 500/700 ou nova família nas superfícies da Fase 3.

| Papel | Fonte | Tamanho | Peso | Altura de linha | Uso |
|---|---|---:|---:|---:|---|
| Rótulo | Inter | 14px | 600 | 1.3 | botões, filtros, chips, cabeçalho de tabela, metadado nomeado |
| Corpo | Inter | 16px | 400 | 1.5 | valores, instruções, erros, biografia, fontes e cartões |
| Título | Barlow Condensed | 24px | 600 | 1.2 | `h2`, título de cartão importante e diálogo |
| Destaque | Barlow Condensed | 32px | 600 | 1.2 | `h1`, placar confirmado e título curto de competição |

Nomes extensos de organização, equipe, local e competição quebram linha; não são truncados onde distinguem a entidade. Metadados podem ocupar duas linhas. Identificadores técnicos autorizados usam `monospace` genérico e `overflow-wrap:anywhere`, nunca uma fonte remota.

---

## Cor

| Papel | Token | Valor | Uso nesta fase |
|---|---|---|---|
| Dominante (60%) | `$color-canvas` | `#0B1220` | fundo da aplicação e espaço entre superfícies |
| Secundária (30%) | `$color-surface` | `#142033` | cabeçalho, filtros, cartões, tabela e workspace |
| Elevada | `$color-surface-raised` | `#1C2C42` | hover/foco, seleção de escopo e diálogo |
| Acento (10%) | `$color-accent` | `#FF8A3D` | somente CTAs primários, item ativo, foco e marca |
| Sobre acento | `$color-on-accent` | `#0B1220` | texto/ícone sobre CTA laranja |
| Texto | `$color-text` | `#F8FAFC` | texto primário e links sublinhados |
| Texto secundário | `$color-text-muted` | `#C5D1E0` | ajuda e metadado não essencial |
| Borda | `$color-border` | `#52657D` | divisores, campos e conectores do chaveamento |
| Foco | `$color-focus` | `#FFC08A` | contorno visível |
| Positivo | `$color-available` | `#56D19C` | publicado, confirmado e importação válida |
| Atenção | `$color-degraded` | `#F6C453` | rascunho, revisão, adiado e dado desatualizado |
| Destrutiva/erro | `$color-unavailable` | `#FF6B6B` | cancelamento, recusa, erro e ação destrutiva |

O laranja fica reservado para: `Aplicar filtros`, `Salvar organização favorita`, `Aceitar vínculo`, `Salvar perfil esportivo`, `Abrir competição`, `Salvar rascunho`, `Importar rascunhos`, `Publicar {objeto}`, foco, item ativo e wordmark. Não usar laranja em placar, campeão, favorito, chip de estado, links comuns, classificação ou conectores. Cor nunca é o único sinal: todo estado tem palavra e, quando compacto, ícone.

Contraste mínimo: 4,5:1 para texto normal e 3:1 para texto grande ou gráfico informativo. CTA primário usa obrigatoriamente `#0B1220` sobre `#FF8A3D`.

---

## Arquitetura de informação e rotas

### Contrato de rota fechado

| Rota | Status | Contrato |
|---|---|---|
| `/` | **Obrigatória** | Agenda pública como primeira experiência. Não passa por guard de sessão, perfil ou capacidade. |
| `/conta/perfil`, `/conta/matricula`, `/operacao/painel`, `/operacao/papeis`, `/operacao/matriculas/corrigir` | **Existentes e protegidas** | Continuam sob sessão/perfil e autorização já implementadas. A Fase 3 não as torna públicas. |

### Exemplos de rota, não decisões arquiteturais

`/competicoes/{slug}`, `/eventos/{id-ou-slug}`, `/organizacoes/{slug}`, `/equipes/{slug}`, `/atletas/{slug}`, `/conta/vinculo-atleta` e `/operacao/esportes/...` são exemplos legíveis de URL. O planejador deve decidir os paths, identificadores públicos, lazy loading, separação de route arrays e estratégia de subrotas. A decisão deve preservar:

1. agenda pública em `/` e todas as superfícies públicas fora dos guards;
2. nenhuma informação privada ou matrícula em path, query ou fragment;
3. deep links públicos estáveis e compartilháveis;
4. conta e operação sempre protegidas;
5. seleção de escopo por opção autorizada, jamais por UUID digitado ou `scopeRef` fornecido pelo cliente.

### Cabeçalho e shell

- Marca `BETUFU` e badge `MVP` à esquerda; rodapé único: `A BETUFU é uma plataforma independente e não possui vínculo oficial com a UFU.`
- Visitante: ação `Entrar`. A agenda aparece mesmo enquanto o estado de sessão opcional é consultado ou falha.
- Pessoa autenticada com perfil completo: controles separados `Conta` e `Operação`. `Conta` agrupa perfil, matrícula, favorito/vínculo quando aplicável e `Sair`; `Operação` aparece somente com capacidades reconhecidas e lista destinos autorizados.
- Pessoa autenticada sem capacidade: `Operação` não existe no DOM. Payload ausente, malformado ou desconhecido falha fechado.
- Perfil incompleto mantém o gate da Fase 2. Abrir um deep link protegido redireciona ao gate; abrir uma rota pública nunca o faz.
- Em 320px os controles continuam com texto, podem envolver em duas linhas e mantêm 44px. Não substituir a navegação inteira por ícones sem rótulo.
- `Pular para o conteúdo principal` é o primeiro foco. Após navegação, foco programático vai ao `main`/`h1`; menus fecham com `Escape` e devolvem foco ao gatilho.

---

## Superfícies públicas

### P1 — Agenda pública em `/`

1. `h1` `Agenda esportiva`; texto curto: `Acompanhe eventos publicados de diferentes competições e edições.`
2. A hierarquia visual é fechada: os resultados — começando pelo grupo `Hoje` ou pelo próximo evento publicado — são o ponto focal primário; a superfície de filtros é secundária e compacta; metadados auxiliares são terciários. A ordem espacial e de DOM permanece `h1`/contexto → filtros → título e contagem dos resultados → cartões, mas tamanho, elevação e cor dos filtros nunca podem competir com o título do grupo e o primeiro `EventCard`; somente `Aplicar filtros` usa laranja nessa superfície.
3. Atalhos de data `Hoje` e `Próximos`, seguidos dos filtros `Data`, `Esporte`, `Competição e edição`, `Categoria`, `Fase` e `Estado`. Opções vêm do catálogo publicado; não há campo livre de UUID.
4. O formulário aplica todos os filtros por `Aplicar filtros`; `Limpar filtros` é secundário. Estado filtrado deve ser representável em URL compartilhável sem dados privados; formato exato da query é decisão do planejador.
5. Resultados agrupam por data, em ordem canônica estável. Cada `EventCard` mostra horário com fuso/abreviação, estado por texto, participantes ou descrição pública do evento básico, esporte/categoria/fase, competição+edição, local/espaço ou `Local a definir` e link `Ver evento`.
6. Evento de modalidade ainda não detalhada usa etiqueta `Agenda básica` e não cria placar, bracket, pódio, raias, marcas ou estatística falsa.
7. Atualização conserva o último resultado visível. Falha posterior marca `Agenda da última atualização · não foi possível atualizar` e oferece `Tentar novamente`.

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
- `Adiado`, `Suspenso` e `Cancelado` são texto+ícone e explicação curta. Horário antigo não aparece como se vigente; remarcação publicada indica o novo horário.
- Participante vindo de vencedor anterior usa `Vencedor de {evento}` até estar definido. `Bye` é `Folga na rodada`, não placar 0×0.

### P4 — Organização esportiva

- Nome, avatar/monograma local, descrição pública quando houver e ação autenticada `Salvar organização favorita`; visitante recebe `Entre para escolher sua organização favorita` sem bloquear a página.
- Seções: equipes por edição/modalidade/categoria, elenco público autorizado, próximos eventos e resultados recentes. Favorito vale para a organização inteira; não escrever “equipe favorita”.
- Seleção salva apresenta confirmação persistente `Organização favorita atualizada.` Remoção usa ação secundária `Remover dos favoritos` com confirmação explícita do objeto.

### P5 — Equipe

- Nome da equipe, organização, edição, esporte e categoria formam o cabeçalho.
- Exibe elenco publicado, próximos eventos e resultados. Rascunho, motivo interno, matrícula, período privado e ator operacional não aparecem.
- Um atleta pode aparecer em várias equipes/edições sem duplicar o perfil público; link leva à mesma identidade esportiva pública.

### P6 — Atleta

- Avatar padrão ou foto processada pela BETUFU, nome esportivo, curso/biografia/redes somente quando o atleta vinculado os publicou.
- Exibe equipes, modalidades, participações e resultados públicos. Estatísticas permitidas: participações, vitórias, derrotas e resultados disponíveis, sempre nomeadas e sem sugerir completude quando a fonte for parcial.
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

### O5 — Importação CSV atômica

1. Exibir instrução e download `Baixar modelo CSV BETUFU`; texto obrigatório: `Use CSV UTF-8. A importação não aceita XLSX.`
2. Seletor aceita um `.csv`; não fazer upload automático ao escolher. Mostrar somente nome seguro do arquivo e ação `Validar arquivo`.
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
| CSV válido | `Importar rascunhos` |
| Publicação | `Publicar {objeto}` |
| Resultado | `Confirmar resultado` |

### Vazios e erros

| Elemento | Cópia obrigatória |
|---|---|
| Agenda vazia | `Nenhum evento publicado` — `Ainda não há eventos publicados para acompanhar.` |
| Filtros sem resultado | `Nenhum evento encontrado` — `Ajuste ou limpe os filtros para ver outros eventos.` |
| Bootstrap da primeira competição | `Nenhuma competição cadastrada` — `Cadastre a primeira competição para iniciar o catálogo esportivo. O identificador e o escopo serão gerados pela plataforma.` — `Cadastrar competição`. Em falha: `Não foi possível carregar as competições` — `Não foi possível confirmar se já existe uma competição cadastrada. Tente carregar novamente antes de iniciar o catálogo.` — `Tentar carregar competições`. |
| Vazios operacionais O2–O4, O6 e O7 | Usar literalmente os títulos, corpos e próximas ações definidos nas respectivas subseções. Não reutilizar a cópia pública `Nenhum {objeto} publicado` em administração. |
| Erro público | `Não foi possível carregar estes dados. Verifique a conexão e tente novamente. Código de referência: {correlationId}.` |
| Erros bloqueantes O2–O4, O6 e O7 | Usar literalmente o erro e a ação contextual definidos na subseção; acrescentar `Código de referência: {correlationId}.` somente quando o BFF o fornecer. |
| Falhas da foto C3 | Usar literalmente `Foto não selecionada`, `Não foi possível enviar a foto`, `Não foi possível processar a foto` ou `Não foi possível remover a foto`, com corpo e próxima ação definidos em C3. |
| Fonte inválida/ausente | `Informe uma fonte oficial válida antes de publicar.` |
| CSV inválido | `Arquivo não importado` — `Corrija todas as linhas indicadas e valide novamente. Nenhum registro foi salvo.` |
| CSV com resultado não confirmado | `Estado da importação não confirmado` e os corpos literais de O5. Usar `Consultar estado da importação` quando disponível; sem consulta, preservar arquivo/resumo, proibir reenvio automático e oferecer `Copiar informações para suporte` e `Voltar ao painel operacional`. |
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
| Importar CSV válido | `Importar {n} atletas e vínculos como rascunho? Nenhum item ficará público nesta ação.` | `Revisar arquivo` |
| Cancelar evento | `Cancelar {evento} em {data}? A agenda pública será atualizada e a versão anterior permanecerá no histórico.` | `Manter evento` |
| Publicar objeto | `Publicar {objeto} na edição {edição}? Esta versão ficará disponível ao público.` | `Voltar à edição` |

Botão destrutivo usa vermelho e nome específico. A ação secundária contextual recebe foco inicial; `Escape` produz o mesmo resultado e restaura o foco ao gatilho. Não existe fallback visual genérico para ação material/sensível, nem `Excluir competição`, `Excluir atleta` ou deleção de histórico neste contrato.

---

## Acessibilidade, teclado e movimento

- WCAG 2.2 AA é o mínimo: contraste, zoom/reflow, ordem do DOM, rótulos, nome/estado/valor, erros associados e alvo de 44px.
- Foco global: `outline: 3px solid #FFC08A; outline-offset: 3px`. Não remover sem substituição idêntica. Item ativo não substitui foco.
- Ordem: skip link → cabeçalho → filtros/contexto → conteúdo → paginação/ações. A mudança de rota leva o foco ao título principal; filtro aplicado leva ao título/contagem dos resultados, não ao topo da janela.
- Combobox/select, disclosures, menus, diálogo e bracket funcionam com Tab/Shift+Tab, Enter, Espaço, setas quando o padrão nativo exigir e Escape. Não implementar clique em `<div>`.
- Tabelas têm `caption`; cartões mobile repetem rótulos. Placar lido como frase (`Equipe A 2, Equipe B 1`), não como sequência ambígua de números.
- `aria-live="polite"` para sucesso, filtros, upload e recarga; `role="alert"` somente para erro bloqueante/recusa. Não anunciar contadores a cada tecla.
- Foto possui alternativa; ícone decorativo fica oculto. Chaveamento não depende de linhas/conectores ou posição visual.
- Transições limitadas a opacidade/transform de até 160ms em controles pequenos. Sem parallax, scroll animation, blur animado ou layout contínuo. `prefers-reduced-motion: reduce` remove movimento; progresso mantém texto estático.
- Conteúdo funciona a 200% de zoom e 320 CSS px sem perda, sobreposição ou scroll horizontal de página.

---

## Privacidade, mídia, fontes e links

- Matrícula é entrada transitória apenas em formulário autorizado. Não aparece em preview, mensagem, CSV de erro, DOM posterior, URL, analytics, storage do navegador ou log comum. HMAC nunca aparece.
- Público recebe somente registros publicados. Rascunho, versão privada, motivo interno, ator, correlation ID de terceiros e fila de vínculo não atravessam a fronteira pública.
- A BETUFU não afirma vínculo oficial com a UFU. Não usar “login UFU”, “SSO UFU”, “atleta verificado pela UFU”, brasão, selo ou identidade que sugira endosso.
- Foto é mídia própria processada e servida pela BETUFU; avatar padrão é local. Sem URL externa de imagem, CDN nova, pixel, iframe, script, asset remoto ou imagem gerada.
- Biografia é texto, nunca `innerHTML`. Link social/fonte/mapa passa por allowlist de esquema (`https` e, quando explicitamente suportado, rota interna); rejeitar `javascript:`, `data:`, credenciais embutidas e domínio vazio.
- Link externo identifica destino e abre de modo seguro (`rel="noopener noreferrer"` quando nova aba). A UI não busca preview, favicon ou Open Graph de terceiro.
- Fonte mostra título/rótulo, origem e data; URL longa pode quebrar com `overflow-wrap:anywhere`, mas o texto do link continua compreensível. Fonte substituída permanece no histórico e recebe `Substituída`/`Corrige versão anterior` em texto.
- Nenhum campo da fase usa comprovante, documento, XLSX ou upload genérico. O input de foto e o input CSV têm contratos e endpoints separados.

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
| Agenda | filtros em disclosure/coluna; cartões empilhados | filtros em duas colunas; grade 2 | barra de filtros envolvente; grade 2–3 |
| Cabeçalho | marca + `Entrar` ou `Conta`/`Operação` com wrap; sem ícone solitário | uma linha quando couber | uma linha, menus ancorados |
| Competição/entidade | seções empilhadas e links de salto | grade 2 | resumo + grade, até 1200px |
| Classificação | cartões rotulados | tabela semântica | tabela semântica |
| Erros CSV | cartões por linha | tabela | tabela |
| Chaveamento | uma rodada por vez | colunas quando couber ou seletor | colunas por rodada dentro do contêiner |
| Workspace | seletor + subnav envolvente/vertical; uma subseção | duas colunas em formulários compatíveis | conteúdo e painel auxiliar; nunca página gigante |
| Formulário | uma coluna, ações empilháveis | até 640px | até 640px centralizado/alinhado ao conteúdo |

Textos, chips e ações podem envolver. Somente metadado redundante pode truncar visualmente, mantendo nome acessível completo; nome de entidade, estado, fonte, placar e erro nunca são truncados.

---

## Expectativas de aceitação em navegador real

O plano deve reservar checkpoint humano/versionado após testes automatizados. Registrar no artefato de evidência: commit/HEAD, build, navegador e versão exata, viewport, rota, identidade/capacidade usada e resultado.

Provas mínimas:

1. Chrome/Chromium real suportado, versão exata registrada, em 320×800 e 1280×800; ao menos uma repetição em outro navegador suportado definida pelo planejamento.
2. Visitante abre `/`, filtra agenda e percorre competição, evento, organização, equipe, atleta, classificação e chaveamento sem login e sem request protegido bloqueando a pintura pública.
3. Pessoa autenticada vê `Conta`; `Operação` aparece somente por capacidade. 401/403 e payload malformado falham fechados sem vazar rota/escopo.
4. Percurso somente por teclado em agenda, filtros, menus, combobox, diálogo, tabela/cartões, bracket mobile, CSV e publicação; foco sempre visível e restaurado.
5. Nenhuma rolagem horizontal da página em 320px; classificação e erro CSV viram cartões; bracket troca rodada sem arrasto obrigatório.
6. Loading, vazio, populado, parcial, stale, erro, não autenticado, não autorizado e conflito de versão são exercitados com fixtures determinísticas.
7. CSV com uma linha inválida prova zero salvamentos e ausência de matrícula no DOM/mensagem; CSV válido cria somente rascunhos.
8. Upload rejeita tipo/tamanho inválido, mantém foto anterior em falha e entrega somente mídia processada sem metadado público.
9. Fonte/link malicioso (`javascript:`, `data:`, credencial embutida) não vira link; biografia não executa HTML.
10. Publicação/concorrência em duas sessões prova que a segunda não sobrescreve a primeira e recebe orientação de comparação.
11. Inspeção de DOM/storage/network confirma ausência de matrícula, HMAC, token e rascunho em superfície pública.
12. `prefers-reduced-motion`, zoom 200%, foco de 3px, alvos 44px e contraste WCAG 2.2 AA são medidos, não inferidos apenas por screenshot.

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
| CSV | Limites de arquivo/linha, encoding/BOM, contrato de erro seguro, idempotência e progresso sem sucesso parcial. |
| Fontes/links | Schema, allowlist de esquemas/domínios quando necessária e política de abertura externa. |
| Tabelas/classificação | Colunas genéricas permitidas pela fonte sem codificar regra esportiva ou desempate. |
| Aceitação | Browsers/versões suportados e fixtures determinísticas para todos os estados. |

Nenhum gate autoriza adicionar dependência, mudar tokens, criar componente library, gerar imagem ou reabrir D-53–D-80 sem aprovação específica.

---

## UI Considerations

Probe executado após a aprovação do checker, com classificação confirmada pelo responsável: **20 superfícies**, os **6 tipos de elemento** (`form`, `list-collection`, `nav`, `media`, `interactive-control` e `static-content`) e **156/156 considerações aplicáveis resolvidas explicitamente**. Resultado: 0 `backstop`, 0 dispensadas e 0 não resolvidas.

Inventário de superfícies: shell/navegação; agenda; competição/edição; evento; organização; equipe; atleta; chaveamento; classificação/fontes; favorito; proposta de vínculo; perfil esportivo/foto; seleção de escopo; catálogo; organizações/equipes operacionais; atletas/elencos; CSV; eventos/resultados; chaveamento/classificação operacional; fila de revisão.

| Categoria | Aplicações | Status | Verdade explícita |
|---|---:|---|---|
| `empty` | 19 | resolved · explicit | Ausência real, formulário inicial e mídia ausente possuem estado próprio e próxima ação segura. A cópia vem de **Contrato de cópia**; rascunho ou falha nunca aparece como vazio de sucesso. |
| `loading` | 20 | resolved · explicit | Carregamento inicial não inventa dados; recarga preserva o último snapshot válido; mutação bloqueia apenas o controle acionado e informa a ação em andamento. |
| `error` | 20 | resolved · explicit | Toda falha apresenta mensagem segura e próxima ação. Conflito de versão, upload, CSV e autorização seguem fluxos próprios, sem sobrescrita nem vazamento. |
| `populated` | 19 | resolved · explicit | Coleções e mídias preenchidas exibem identidade, contexto de competição/edição, fonte e versão sempre que esses dados afetam a interpretação. |
| `partial` | 19 | resolved · explicit | Dados parciais preservam blocos válidos e usam fallbacks contratados (`Local a definir`, avatar padrão e `Agenda básica`), sem inventar placar, estatística, fonte ou dado opcional ausente. |
| `overflow` | 20 | resolved · explicit | Em 320px a página não rola horizontalmente; tabelas viram cartões, o chaveamento usa uma rodada por vez e texto essencial quebra ou reflui. |
| `zero-one-many` | 19 | resolved · explicit | Coleções distinguem zero, um e muitos itens com vazio próprio, identidade preservada, pluralização contextual, ordenação estável e paginação ou carregamento explícito. |
| `long-text` | 20 | resolved · explicit | Nomes, biografia, fonte, local, motivo, erro e controles refluem; HTML não executa; URL e identificador seguro quebram sem ocultar conteúdo essencial. |

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

**Approval:** approved em 2026-08-26, sem recomendações abertas.
