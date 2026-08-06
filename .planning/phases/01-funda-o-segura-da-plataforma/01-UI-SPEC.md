---
phase: 1
slug: funda-o-segura-da-plataforma
status: approved
shadcn_initialized: false
preset: none
created: 2026-08-01
---

# Fase 1 — Contrato de Design de UI

> Contrato visual e de interação para a estrutura inicial da interface BETUFU e o diagnóstico técnico protegido. A fase não entrega telas de esportes, odds, créditos, palpites, rankings, grupos ou chat.

---

## Escopo e decisões de origem

| Decisão | Contrato executável | Fonte |
|---|---|---|
| Direção visual | Identidade esportiva proprietária, madura e escura; base azul-marinho/grafite, superfícies contrastadas e laranja quente de placar. Não usar verde-neon como marca, nem selo, brasão ou sinais de identidade institucional da UFU. | decisão do usuário / `01-CONTEXT.md` D-01 |
| Navegação inicial | Mostrar somente a marca BETUFU, identidade da sessão autenticada e ação `Sair`. Não renderizar links, abas ou placeholders para capacidades futuras. | decisão do usuário |
| Diagnóstico | Rota protegida, exclusivamente para conta técnica em desenvolvimento/homologação; não compilar a rota nem seus links no build de produção. | decisão do usuário / `01-CONTEXT.md` D-02–D-04 |
| Autenticação | O Angular não implementa credenciais. Login, recuperação, verificação de e-mail e MFA são fluxos nativos do Keycloak, sob um tema BETUFU mínimo. | decisão do usuário / `REQUIREMENTS.md` IDEN-01–IDEN-04 |
| Segurança da informação | Nunca renderizar token, cookie, segredo, endereço interno, payload técnico, stack trace ou detalhe bruto de health/Actuator. | `01-CONTEXT.md` D-03–D-04, D-14; `01-RESEARCH.md` |

## Sistema de design

| Propriedade | Valor |
|---|---|
| Ferramenta | Angular Material/CDK com tema e tokens proprietários BETUFU; componentes standalone Angular. |
| Preset | Não aplicável. Não usar shadcn. |
| Biblioteca de componentes | Angular Material/CDK; preferir `mat-toolbar`, `mat-menu`, `mat-button`, `mat-card`, `mat-icon`, `mat-progress-spinner` e CDK a componentes próprios equivalentes. |
| Ícones | Angular Material Symbols Rounded, auto-hospedados no pacote da aplicação; ícone sempre acompanhado de texto visível ou de nome acessível. |
| Fontes | Inter e Barlow Condensed, auto-hospedadas em `frontend`; nenhuma dependência de Google Fonts ou outra CDN. |
| Tema | Somente escuro no MVP. Não criar alternância claro/escuro nem um tema claro incompleto. |
| Forma | Raio de 8px em cartões e campos; 4px em chips/etiquetas; 999px somente em avatar e indicadores circulares. Sombra discreta `0 8px 24px rgba(0,0,0,.24)` apenas em superfícies elevadas. |

### Marca e Keycloak

- O cabeçalho usa o monograma geométrico `B` inspirado em segmentos de placar, seguido do wordmark `BETUFU`. É uma marca inicial, não um brasão nem a marca definitiva.
- A estrutura autenticada usa o wordmark no cabeçalho; o monograma isolado pode ser usado como favicon, avatar de marca e marca reduzida em telas estreitas, sempre com `aria-label="BETUFU"`.
- Criar um tema Keycloak mínimo com a mesma paleta, raios, foco e fontes. Manter intactos os campos e a lógica nativos de credencial, recuperação, verificação de e-mail e MFA; o Angular apenas redireciona ao BFF/Keycloak e recebe o retorno de sessão.
- Em todas as páginas Keycloak, após o formulário e antes de ajuda/rodapé, exibir: `A BETUFU é uma plataforma independente e não possui vínculo oficial com a UFU.` O aviso é texto comum, não um link nem uma alegação jurídica adicional.
- Não usar “login UFU”, “SSO UFU”, sigla estilizada como marca, selo universitário ou linguagem que sugira endosso da universidade.

---

## Tokens de espaçamento e layout

Valores declarados, sempre múltiplos de 4:

| Token | Valor | Uso obrigatório |
|---|---:|---|
| `space-1` | 4px | vão de ícone e texto, marcador interno, ajuste mínimo |
| `space-2` | 8px | itens compactos, etiqueta e linhas de formulário |
| `space-4` | 16px | espaçamento padrão entre controles e conteúdo de cartão |
| `space-6` | 24px | padding de cartão, distância entre grupos relacionados |
| `space-8` | 32px | padding lateral de página desktop e lacuna da grade |
| `space-12` | 48px | separação de blocos principais |
| `space-16` | 64px | respiro superior/inferior de página completa |

Exceções permitidas: alvo de toque mínimo de 44×44px para qualquer controle acionável; ícones de 20px (inline) ou 24px (controle); contorno de foco de 3px. Não usar valores de espaçamento arbitrários fora dos tokens.

| Faixa | Largura e recuo |
|---|---|
| Mobile | 320–599px; conteúdo com 16px laterais; cabeçalho de 56px; grade de diagnóstico com uma coluna. |
| Tablet | 600–899px; conteúdo com 24px laterais; grade com duas colunas. |
| Desktop | 900px ou mais; contêiner central com máximo de 1200px, 32px laterais e grade com três colunas. |

Não há navegação inferior nem lateral nesta fase. A expansão futura pode adotá-las, mas nenhum espaço vazio ou item desativado deve antecipá-las.

## Tipografia

Há exatamente quatro tamanhos e dois pesos neste contrato. Não introduzir pesos intermediários, itálico decorativo, caixa alta para parágrafos nem mais tamanhos nesta fase.

| Papel | Fonte | Tamanho | Peso | Altura de linha | Uso |
|---|---|---:|---:|---:|---|
| Rótulo | Inter | 14px | 600 | 1.3 | botões, chips de estado, rótulos de dados e menu |
| Corpo | Inter | 16px | 400 | 1.5 | texto de sessão, instruções, erros e conteúdo de cartão |
| Título | Barlow Condensed | 24px | 600 | 1.2 | título de página e nome de seção técnica |
| Destaque | Barlow Condensed | 32px | 600 | 1.2 | resumo geral do diagnóstico e eventuais numerais esportivos futuros |

O wordmark pode usar Barlow Condensed 600, preservando a legibilidade da marca. Todo texto de interface e formulário permanece em Inter. Os valores numéricos do diagnóstico usam alinhamento tabular quando a fonte o suportar, sem adicionar outra família ou peso.

## Cor e contraste

| Token / papel | Valor | Uso |
|---|---|---|
| `color-canvas` — dominante (60%) | `#0B1220` | fundo da aplicação e do Keycloak |
| `color-surface` — secundária (30%) | `#142033` | cabeçalho, cartões, menus e áreas de formulário |
| `color-surface-raised` | `#1C2C42` | cartão em foco/hover e resumo elevado; não substitui o fundo dominante |
| `color-accent` — acento (10%) | `#FF8A3D` | ação primária e ênfase estritamente listadas abaixo |
| `color-on-accent` | `#0B1220` | texto e ícone sobre botão laranja |
| `color-text` | `#F8FAFC` | texto primário |
| `color-text-muted` | `#C5D1E0` | metadados e instruções secundárias; nunca para informação essencial isolada |
| `color-border` | `#52657D` | bordas, divisores e foco de baixo contraste; não comunicar estado |
| `color-focus` | `#FFC08A` | contorno de foco visível |
| `color-available` | `#56D19C` | estado `Disponível`, sempre junto de ícone e palavra |
| `color-degraded` | `#F6C453` | estado `Degradado`, sempre junto de ícone e palavra |
| `color-unavailable` / destrutiva | `#FF6B6B` | estado `Indisponível`, erro bloqueante e ação destrutiva futura; não é acento de marca |

Contratos de contraste: texto normal mantém no mínimo 4,5:1 contra a superfície; texto grande e elementos gráficos informativos, no mínimo 3:1; a combinação `#0B1220` sobre `#FF8A3D` é obrigatória no botão primário. Não usar cor como único meio de estado: os três estados de diagnóstico exigem também ícone, palavra e descrição.

O laranja fica reservado exclusivamente para: botão primário `Verificar novamente`, anel/contorno de foco, estado ativo do menu de sessão, monograma/wordmark e destaque do resumo geral quando não representar disponibilidade. Não aplicá-lo a todos os links, cartões, números ou status. Links simples usam `#F8FAFC` sublinhado e só recebem laranja no foco/hover.

---

## Superfícies e interações entregues

### Estrutura inicial autenticada

- Cabeçalho persistente, sem sombra em repouso: marca à esquerda; à direita, botão de identidade da sessão (nome de exibição ou `Conta autenticada`) que abre um `mat-menu`, e ação `Sair` dentro desse menu. Em mobile, o nome pode truncar em uma linha, mas o botão mantém o nome completo como `aria-label`/tooltip e alvo de 44px.
- O menu contém somente a identidade não sensível e `Sair`; não incluir links futuros. `Sair` encerra apenas a sessão do dispositivo atual e inicia o logout OIDC aplicável. Não requer diálogo de confirmação, pois é uma ação explícita, reversível por novo login e não apaga dados.
- Durante a verificação inicial de sessão, reservar a estrutura do cabeçalho e mostrar o indicador textual `Preparando sua sessão…`; não mostrar conteúdo autenticado antes da resposta do BFF.
- Ao expirar a sessão por 8 horas de inatividade, limpar conteúdo protegido, preservar apenas uma tela de retorno segura e exibir: `Sua sessão expirou por inatividade. Entre novamente para continuar.` A ação é `Entrar novamente`. Nenhum token, cookie, tempo restante ou causa interna é mostrado.
- O foco inicial após retorno de login vai para o `main`; a saída conclui no início da página pública/Keycloak. Usar um link “Pular para o conteúdo” visível ao receber foco.

### Rota de diagnóstico protegido

**Disponibilidade:** a rota só é registrada em configurações de desenvolvimento e homologação e exige autorização do servidor para conta técnica. Guardas Angular são apenas melhoria de experiência; uma resposta 401/403 do BFF prevalece. Produção não contém código de rota, item de menu nem URL apresentada pela interface.

**Estrutura visual:**

1. `h1` “Diagnóstico da plataforma”, seguido de texto curto: `Visão segura do estado dos serviços BETUFU.`
2. Cartão-resumo de largura total: indicador com ícone+texto `Disponível`, `Degradado` ou `Indisponível`; momento de última verificação no formato local `Verificado às HH:mm`; botão laranja `Verificar novamente`.
3. Grade responsiva com exatamente seis cartões: `API`, `Sessão BFF`, `Keycloak`, `PostgreSQL`, `Redis` e `Armazenamento de objetos`. Cada um expõe somente nome, ícone de estado, palavra de estado e descrição pública curta permitida pelo DTO sanitizado. Não mostrar host, porta, URL, versão de dependência, cabeçalho, exceção, log ou propriedade.
4. Região “Código de referência”: texto monoespaçado selecionável com `{correlationId}` e botão de ícone+texto `Copiar código de referência`. O código não é segredo; se estiver indisponível, mostrar `Código de referência indisponível` sem um valor inventado.
5. Rodapé técnico de baixa ênfase: versão/build e ambiente somente quando o DTO autorizar valores públicos, em formato `Versão {versão} · Ambiente {ambiente}`. Não mostrar esta região se nenhum desses valores seguros estiver disponível.

**Regras de estado do resumo:** `Disponível` só quando os seis cartões estiverem disponíveis; `Degradado` quando uma ou mais dependências estiverem degradadas/indisponíveis, mas a projeção responder; `Indisponível` quando a projeção segura não puder responder. O resumo nunca mascara o pior estado de um cartão.

**Comportamento do botão:** ao acionar `Verificar novamente`, desabilitar somente esse botão, trocar seu rótulo para `Verificando…` e preservar os últimos cartões válidos. Consultas podem ter repetição automática limitada pela política de rede; a ação manual é sempre disponível ao final. Nunca repetir silenciosamente logout, envio de formulário ou outra mutação.

**Cópia e teclado:** `Copiar código de referência` copia somente o texto do código. Enquanto a Clipboard API estiver pendente, manter o código visível e desabilitar apenas esse botão para impedir acionamento duplicado. Ao concluir, anunciar em região `aria-live="polite"`: `Código de referência copiado.` Em falha de cópia: `Não foi possível copiar o código. Selecione-o e copie novamente.` O botão é ativável por Enter e Espaço; não depender de hover.

## Contrato de estados, erros e cópia

| Elemento | Cópia e comportamento obrigatórios |
|---|---|
| CTA primário | `Verificar novamente` — inicia consulta segura de diagnóstico. Durante a consulta: `Verificando…`. |
| Sucesso | `Diagnóstico atualizado às {HH:mm}.` em região `aria-live="polite"`; não usar toast que desaparece antes da leitura. |
| Sem dados | Título: `Nenhum diagnóstico disponível`. Corpo: `Ainda não há uma verificação registrada. Selecione “Verificar novamente” para consultar o ambiente.` Mostrar somente após uma resposta válida sem dados; não confundir com falha. |
| Falha ao carregar | `Não foi possível atualizar o diagnóstico. Verifique a conexão e tente novamente. Código de referência: {correlationId}.` Manter cartões válidos anteriores com marca `Dados da última verificação; atualização não concluída.` |
| Falha sem dados anteriores | `Não foi possível carregar o diagnóstico. Tente novamente. Código de referência: {correlationId}.` Mostrar botão `Verificar novamente`; não renderizar grade vazia como sucesso. |
| Parcial/degradado | `Alguns serviços precisam de atenção. Consulte os cartões marcados como Degradado ou Indisponível.` Não ocultar cartões saudáveis nem substituir a página por erro total. |
| Estado disponível | `Disponível` e descrição `Respondendo normalmente.` |
| Estado degradado | `Degradado` e descrição `Respondendo com limitações.` |
| Estado indisponível | `Indisponível` e descrição `Não foi possível confirmar a disponibilidade.` |
| Erro genérico da aplicação | `Algo não funcionou como esperado. Tente novamente. Código de referência: {correlationId}.` No login nativo Keycloak, campos obrigatórios mantêm mensagem acessível por campo e foco no primeiro inválido; o identificador não sensível pode ser preservado quando seguro, mas senha e OTP nunca são preservados. Credenciais incorretas usam erro geral sem enumeração. Na aplicação, usar o erro geral apenas quando não houver campo associado. |
| Confirmação destrutiva | Não há ação destrutiva de dados na UI desta fase. `Sair` não exige confirmação e encerra apenas a sessão atual. Reinicialização de dados locais é um comando de desenvolvimento fora desta interface e, se ganhar UI em fase futura, exigirá confirmação explícita que nomeie os dados locais apagados. |

Para erros 401/403 da rota de diagnóstico, não explicar regras de autorização ou identidade técnica. Exibir, respectivamente, `Sua sessão não permite continuar. Entre novamente.` ou `Você não tem acesso a este diagnóstico.` sem ação que revele se a rota existe em produção.

## Acessibilidade, movimento e conteúdo resiliente

- Atender WCAG 2.2 AA como mínimo: contraste definido acima, ordem de foco visual e de DOM coerente, alvos de 44px, rótulos programáticos e operação integral por teclado.
- Todo controle de ícone tem texto visível nesta fase; se uma variante compacta for necessária em 320px, manter `aria-label` idêntico ao rótulo. Ícones de status usam nomes acessíveis completos, por exemplo `Disponível: API respondendo normalmente`.
- O foco usa `outline: 3px solid #FFC08A; outline-offset: 3px` em todos os controles, inclusive `mat-menu`, cartões acionáveis futuros e campos Keycloak. Nunca remover o foco nativo sem substituição equivalente.
- Usar `aria-live="polite"` para sucesso, cópia e atualização de estado; usar `role="alert"` apenas em falha bloqueante. Não anunciar novamente os seis cartões quando só um deles mudar; anunciar o resumo e o cartão afetado.
- O estado é comunicado por ícone distinto, texto e cor. Recomenda-se `check_circle` para disponível, `warning_amber` para degradado e `error_outline` para indisponível; não usar animação como diferenciador.
- Transições podem ter opacidade/elevação de até 160ms. Com `prefers-reduced-motion: reduce`, remover transições, pulsos e spinners decorativos; manter indicador textual estático `Verificando…` e o progresso funcional.
- Não usar atualização automática visível para simular monitoramento em tempo real. O timestamp muda só após carregamento ou nova verificação solicitada; isso evita interrupções para leitores de tela e reduz ambiguidade operacional.
- Textos longos em cartões e mensagens quebram por palavra, sem corte horizontal; IDs podem quebrar com `overflow-wrap:anywhere`. O conteúdo principal não deve produzir rolagem horizontal em 320px. Títulos de cartões ocupam até duas linhas; descrições não são truncadas.

## Considerações de UI

Considerações aplicáveis resolvidas: 8 cobertas, 0 backstop, 0 não resolvidas.

| Categoria | Elemento(s) | Status | Resolução |
|---|---|---|---|
| empty | formulários Keycloak e projeção de diagnóstico | ✅ covered | Formulários Keycloak sem preenchimento mantêm rótulos e instruções nativos, sem erro falso; resposta válida do diagnóstico sem dados mostra o estado vazio documentado e `Verificar novamente`. |
| loading | sessão inicial, formulários Keycloak, resumo, grade, nova verificação e cópia | ✅ covered | O layout permanece estável e apresenta progresso textual; envios nativos do Keycloak e os botões em andamento bloqueiam somente o próprio controle, sem apagar conteúdo válido. |
| error | sessão, Keycloak, resumo, grade, carregamento e cópia | ✅ covered | A falha fica restrita à superfície afetada, mantém o restante utilizável e oferece próxima ação; mensagens públicas não revelam regra de autorização nem detalhe interno. |
| populated | grade dos seis serviços | ✅ covered | Em sucesso, a grade contém exatamente API, Sessão BFF, Keycloak, PostgreSQL, Redis e Armazenamento de objetos, acompanhados de resumo e código de referência. |
| partial | formulários Keycloak, diagnóstico degradado e dados anteriores | ✅ covered | O Keycloak conserva valores não sensíveis quando for seguro e mantém validação por campo; cartões saudáveis e dados anteriores continuam visíveis e são rotulados quando a atualização falha. |
| overflow | cabeçalho, Keycloak, resumo, grade e código de referência | ✅ covered | Todas as superfícies reorganizam ou quebram conteúdo sem rolagem horizontal em 320px; alvos de toque e controles permanecem operáveis. |
| zero-one-many | grade dos serviços | ✅ covered | A grade bem-sucedida é fixa em seis cartões; ausência é estado vazio e nunca uma coleção de tamanho variável com significado ambíguo. |
| long-text | identidade, Keycloak, resumo, cartões, mensagens e código de referência | ✅ covered | Identidade pode truncar apenas visualmente com nome acessível completo; demais textos e IDs quebram ou reorganizam sem perda do conteúdo acessível. |

## Segurança de interface e dados expostos

- A interface consome exclusivamente a projeção OpenAPI de diagnóstico sanitizada. Não consumir ou refletir o Actuator bruto, `env`, `configprops`, logs, trace, cabeçalhos, URL interna ou exceções.
- O BFF decide autenticação e autorização; o cliente não armazena `access token` nem `refresh token` em `localStorage`, `sessionStorage`, IndexedDB ou qualquer armazenamento equivalente.
- Exibir o correlation/reference code apenas como texto de suporte copiável. Não o tratar como senha, não associá-lo a dados de sessão na tela e não registrá-lo em analytics de frontend.
- Não criar analytics, pixels, fontes remotas, iframes ou requests de terceiros nesta fase. Qualquer telemetria futura precisa de revisão de privacidade antes de ser inserida.

## Segurança de registro

| Registro | Blocos usados | Safety Gate |
|---|---|---|
| Angular Material/CDK (dependências oficiais npm) | componentes base e CDK listados neste contrato | Não é registry shadcn; confirmado em 2026-08-01. |
| shadcn official | nenhum | Não aplicável — `components.json` ausente por decisão de stack Angular. |
| Terceiros | nenhum | Não há bloco de registry nem vetting pendente. |

## Pontos resolvidos e gates de execução

| Item | Resolução no contrato | Gate de execução |
|---|---|---|
| Eventos que renovam as 8 horas de inatividade | Resolvido no 01-06: request autenticado/intencional ou navegação protegida renovam; health, assets, consulta de sessão, polling, preflight e heartbeat não renovam. | `SessionPolicyTest`, `RedisSessionIT` e a prova STOMP do 01-08. |
| Código estável e forma final de `Problem Details` | A interface usa somente mensagem pública, código estável e `{correlationId}` allowlisted; nenhuma taxonomia adicional é inventada no cliente. | 01-12 fecha schema/handler e 01-26 regenera o cliente final antes da UI. |
| Gerador OpenAPI compatível com Angular 22 | Fixado em `@openapitools/openapi-generator-cli@2.40.1` + `typescript-angular`; falha não autoriza troca silenciosa. | Smoke inicial 01-20, regeneração final 01-26 e check read-only 01-16. |

---

## Checker Sign-Off

- [x] Dimensão 1 Copywriting: PASS
- [x] Dimensão 2 Visuals: PASS
- [x] Dimensão 3 Color: PASS
- [x] Dimensão 4 Typography: PASS
- [x] Dimensão 5 Spacing: PASS
- [x] Dimensão 6 Registry Safety: PASS

**Approval:** approved 2026-08-02
