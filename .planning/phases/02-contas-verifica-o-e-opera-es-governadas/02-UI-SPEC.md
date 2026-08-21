---
phase: 2
slug: contas-verifica-o-e-opera-es-governadas
status: approved
reviewed_at: 2026-08-20
shadcn_initialized: false
preset: none
created: 2026-08-20
extends: .planning/phases/01-funda-o-segura-da-plataforma/01-UI-SPEC.md
---

# Fase 2 — Contrato de Design de UI

> Contrato visual e de interação para conta BETUFU, registro da matrícula UFU e operações governadas.
> **Este documento estende o `01-UI-SPEC.md`; não o substitui.** Tokens, tipografia, cor, foco, tema escuro
> exclusivo, fontes e regras de acessibilidade da Fase 1 permanecem vinculantes e **não são reabertos aqui**.
> Onde este documento cala, vale a Fase 1.

---

## Escopo e decisões de origem

| Decisão | Contrato executável | Fonte |
|---|---|---|
| Perfil obrigatório no primeiro acesso | O apelido é escolhido antes de qualquer navegação autenticada. Nenhuma rota de produto renderiza conteúdo enquanto o perfil estiver incompleto. | `02-CONTEXT.md` D-39 |
| Apelido | ASCII estrito, 3–20 caracteres, `A-Z a-z 0-9 _ -`. Sem espaço, acento, símbolo, caractere de controle ou largura zero. | D-44 |
| Apelido não é único | Listas mostram **somente** o apelido. A distinção entre homônimos vive **no perfil**, pelo identificador público. **Não** existe discriminador visível em lista — opção explicitamente recusada. | D-42, D-43 |
| Equipe favorita | Modelada como referência nula. **O seletor não é renderizado nesta fase** — nem visível, nem desabilitado, nem como espaço reservado. Entra na Fase 3. | D-45 |
| Matrícula | Campo único de texto livre, autodeclarado. Aceite ou recusa imediatos. **Nunca chamar de “verificação”**: o MVP não confere titularidade. | D-25, D-28, D-29, `<specifics>` |
| Matrícula não expira | Não existe estado `expirado`, aviso de revalidação, contagem regressiva ou data de validade em nenhuma superfície. | D-26 |
| Matrícula imutável para o usuário | Depois de registrada, o usuário não edita nem remove. O único caminho é a correção administrativa com motivo obrigatório. | D-32 |
| Papéis | Três papéis modelados: administrador geral, administrador de competição, moderador. Conceder, revogar, delimitar, auditar. | D-46 |
| Guardas de papel | (a) Concessão a si mesmo é bloqueada; (b) revogar o próprio `administrador geral` é recusado quando ele for o último. As duas recusas têm superfície visual própria. | D-48 |
| Alteração de papel encerra sessões | Conceder ou revogar encerra **todas** as sessões da identidade afetada. A interface avisa **antes** de confirmar, nunca só depois. | D-49, `IDEN-05` |
| MFA | **Nenhuma superfície de MFA nesta fase**: sem inscrição, sem step-up, sem interstitial, sem selo, sem indicador de sessão. `mfaAuthenticated` permanece ausente do contrato de sessão e a UI não pode inventá-lo. | D-50, D-52 |
| Verificação documental | **Fora de escopo.** Não existe envio de arquivo, fila de revisão, tela de revisor, estado de análise, aprovação ou rejeição em lugar nenhum da Fase 2. Nenhum `<input type="file">`. | D-25 |
| Autenticação | Cadastro, login, confirmação de e-mail e redefinição de senha são **páginas hospedadas pelo Keycloak**, não rotas Angular. Ver *Fronteira de superfície*. | D-38, D-40, D-41, `01-UI-SPEC.md` |
| Privacidade | Matrícula e HMAC nunca aparecem em URL, conteúdo público, telemetria ou log. Quando o perfil público de terceiros existir — não nesta fase, ver *Angular — tudo o mais* — ele exporá apenas apelido, equipe favorita, posição permitida e conquistas. A restrição já é vinculante para qualquer superfície futura. | `PRIV-01`, `PROF-02` |
| Erros e formulários | `D-14`–`D-17` da Fase 1 valem integralmente e sem exceção nesta fase. | `01-CONTEXT.md` |

---

## Fronteira de superfície: Keycloak × Angular

Esta é a distinção mais fácil de errar nesta fase. Ela é normativa.

### Hospedado pelo Keycloak — **não** projetar como rota Angular

| Página | Observação |
|---|---|
| Cadastro (`register`) | Novo nesta fase: `registrationAllowed` passa a `true`. Na primeira etapa coleta **somente o e-mail**; depois de a pessoa confirmar esse endereço pelo link recebido, o Keycloak apresenta a criação da senha. **O apelido não é coletado aqui** — não criar atributo customizado, não estender o formulário. |
| Confirmação de e-mail (`login-verify-email` e página após o link) | Novo nesta fase: `verifyEmail` passa a `true`. O login não conclui antes da confirmação, então **não existe estado intermediário no Angular** a desenhar. |
| Login | Já existente. |
| Redefinição de senha (`login-reset-password`, `login-update-password`) | Já existente. |
| E-mails (assunto e corpo) | Permanecem nativos. Fora do escopo do tema. |

Regra dura herdada: o tema `betufu` estende `keycloak.v2` **apenas** por `footer.ftl` e CSS. **Não copiar nem sobrescrever** `template.ftl`, `login.ftl`, `register.ftl` ou qualquer template de página.

### Angular — tudo o mais

Perfil obrigatório, perfil próprio, matrícula, painel operacional, administração de papéis e correção administrativa de matrícula.

**O perfil público de terceiros não é entregue nesta fase.** `PROF-02` define o que ele pode exibir — apelido, equipe favorita, posição permitida e conquistas — e três desses quatro só passam a existir na Fase 3 (equipes) e na Fase 6 (conquistas e posição). Renderizá-lo agora produziria uma página de campo único. A restrição de `PROF-02` permanece vinculante e está registrada em *Privacidade como requisito visual*; a superfície nasce na fase que tiver o que mostrar.

### Continuidade visual exigida através da fronteira

O que **deve** ser idêntico dos dois lados:

1. Os mesmos valores de token, aplicados no Keycloak pelas variáveis CSS já existentes em `login.css` (`--betufu-canvas`, `--betufu-surface`, `--betufu-accent`, `--betufu-focus`, …). Nenhum valor novo.
2. As mesmas fontes, com os **mesmos bytes** auto-hospedados: `inter-regular.woff2`, `inter-semibold.woff2`, `barlow-condensed-semibold.woff2`. Sem CDN.
3. Os mesmos quatro tamanhos e dois pesos tipográficos.
4. O mesmo foco: `outline: 3px solid #FFC08A; outline-offset: 3px`.
5. O mesmo CTA primário: fundo `#FF8A3D` com texto `#0B1220`.
6. Os mesmos raios (`8px` em campos e cartões).
7. Aparência escura sempre, inclusive sob `prefers-color-scheme: light`.
8. O aviso de rodapé, exatamente uma vez por página, como texto comum:
   `A BETUFU é uma plataforma independente e não possui vínculo oficial com a UFU.`
   **As duas páginas novas — cadastro e confirmação de e-mail — precisam exibi-lo, e precisam de smoke próprio de tema.**

O que **não** será contínuo, e é aceito explicitamente: o DOM e o layout PatternFly (`pf-v5-*`), a ordem nativa dos campos, o texto nativo das mensagens de erro do Keycloak, a validação nativa e a ausência de marca BETUFU no corpo das páginas. **Não tentar reproduzir a estética Angular dentro do Keycloak alterando DOM.** O limite é CSS mais footer, e ele é deliberado.

### Entrega (handoff) entre as duas superfícies

Ao voltar do Keycloak, a primeira pintura autenticada é o estado `Preparando sua sessão…` já contratado na Fase 1, e em seguida o gate de perfil. **Não** existe tela de boas-vindas intermediária, não existe segunda marca, não existe confirmação redundante de que o e-mail foi verificado — o Keycloak já a deu.

---

## Sistema de design

| Propriedade | Valor |
|---|---|
| Ferramenta | Nenhuma (não é projeto shadcn). Angular Material/CDK com tema e tokens proprietários BETUFU, componentes standalone. |
| Preset | Não aplicável — `components.json` ausente por decisão de stack Angular. |
| Biblioteca de componentes | Angular Material/CDK. Preferir `mat-form-field`, `mat-input`, `mat-menu`, `mat-button`, `mat-card`, `mat-icon`, `mat-dialog`, `mat-chip`, `mat-progress-spinner` e o CDK a equivalentes próprios. Formulários usam `ReactiveFormsModule`. |
| Ícones | Material Symbols Rounded auto-hospedados. Ícone sempre com texto visível ou nome acessível. |
| Fontes | Inter e Barlow Condensed auto-hospedadas, bytes canônicos compartilhados com o tema Keycloak. **Nenhuma fonte nova pode ser introduzida nesta fase.** |
| Tema | Somente escuro. Sem alternância. |
| Forma | `$radius-md` 8px em cartões, campos e botões; `$radius-sm` 4px em chips de estado; `$radius-full` apenas em avatar e indicadores circulares. Sombra `$shadow-raised` só em superfície elevada e em diálogo. |
| Tokens | `frontend/src/styles/_tokens.scss`. **Consumir por nome**, nunca por literal hexadecimal ou pixel avulso. |
| Estado | `signal`/`computed` em um store por feature, no padrão de `diagnostic.store.ts`. |

### Novos tokens

Nenhum de cor, espaçamento, raio, sombra, breakpoint ou família tipográfica.

**Um token de medida**, aprovado pelo responsável em 2026-08-20: `$form-max-width: 640px` — largura de leitura de formulário, irmã de `$container-max-width`. A execução da Fase 2 deve declará-lo em `frontend/src/styles/_tokens.scss` antes do primeiro consumo, e nenhuma folha de estilo pode escrever o literal.

Único uso não tokenizado, **herdado** e não ampliado: `font-family: monospace` genérico para o identificador público e o código de referência, exatamente como o diagnóstico já faz. Não é fonte nova — é a família genérica do sistema. Diferença obrigatória em relação ao diagnóstico: o identificador público usa `$color-text`, **não** `$color-accent`, porque o laranja tem orçamento fechado (ver *Cor*).

---

## Escala de espaçamento

Herdada sem alteração de `_tokens.scss`. Sempre múltiplos de 4.

| Token | Valor | Uso nesta fase |
|---|---:|---|
| `$space-1` | 4px | vão de ícone e texto, marcador interno |
| `$space-2` | 8px | rótulo e campo, chips de papel, linhas compactas de auditoria |
| `$space-4` | 16px | espaçamento padrão entre campos de formulário e entre controles |
| `$space-6` | 24px | padding de cartão e de diálogo, distância entre grupos de campos |
| `$space-8` | 32px | padding lateral de página desktop, lacuna da grade do painel |
| `$space-12` | 48px | separação entre blocos principais do painel operacional |
| `$space-16` | 64px | respiro superior/inferior de página completa e do gate de perfil |

Exceções permitidas — as **mesmas** da Fase 1, sem acréscimo: alvo de toque mínimo `$min-touch-target` 44×44px em qualquer controle acionável (inclusive itens de menu e botões de ação em linha de tabela); ícones de 20px inline ou 24px em controle; contorno de foco de 3px com deslocamento de 3px.

Acrescenta-se **um único token de medida**, `$form-max-width: 640px`, aplicado à largura de leitura de formulários em desktop. Não é espaçamento — é comprimento de linha legível, da mesma natureza do `$container-max-width: 1200px` já aprovado na Fase 1, e múltiplo de 4 como todo o restante da escala. **Aprovado pelo responsável em 2026-08-20.** Deve ser declarado em `frontend/src/styles/_tokens.scss` durante a execução da Fase 2 e consumido por nome; o literal `640px` não pode aparecer em nenhuma folha de estilo. Fora deste token e das exceções herdadas acima, **nenhum valor de espaçamento arbitrário fora dos tokens.**

| Faixa | Largura e recuo |
|---|---|
| Mobile | 320–599px; 16px laterais; cabeçalho 56px; formulários em coluna única; painel operacional em uma coluna; tabelas de papéis e auditoria degradam para **lista de cartões empilhados**, nunca rolagem horizontal. |
| Tablet | 600–899px; 24px laterais; painel em duas colunas. |
| Desktop | ≥900px; contêiner central máximo de 1200px; 32px laterais; painel em três colunas. Formulários limitados a `$form-max-width` (**640px**), centralizados — campo de largura total em 1200px é ilegível. |

---

## Tipografia

Exatamente **quatro tamanhos e dois pesos**, herdados. Nada novo.

| Papel | Fonte | Tamanho | Peso | Altura de linha | Uso nesta fase |
|---|---|---:|---:|---:|---|
| Rótulo | Inter | 14px | 600 | 1.3 | botões, rótulos de campo, chips de papel e de estado, itens de menu, cabeçalho de tabela, texto de ajuda de campo |
| Corpo | Inter | 16px | 400 | 1.5 | instruções, mensagens de erro, texto de confirmação, linhas de auditoria, valor de campo |
| Título | Barlow Condensed | 24px | 600 | 1.2 | `h1` de página e título de diálogo |
| Destaque | Barlow Condensed | 32px | 600 | 1.2 | apenas os números agregados dos cartões do painel operacional |

Regras adicionais: todo texto de formulário permanece em Inter; nenhum peso intermediário; sem itálico decorativo; sem caixa alta em parágrafo. Números agregados do painel usam alinhamento tabular quando a fonte o suportar, sem outra família ou peso. Mensagem de erro **não** usa peso 600 para chamar atenção — a distinção é feita por cor, ícone e posição.

---

## Cor

Herdada integralmente de `_tokens.scss`. Nenhum valor novo.

| Papel | Token | Valor | Uso nesta fase |
|---|---|---|---|
| Dominante (60%) | `$color-canvas` | `#0B1220` | fundo da aplicação e das páginas Keycloak |
| Secundária (30%) | `$color-surface` | `#142033` | cabeçalho, cartões, menu, diálogos, áreas de formulário, linhas de tabela |
| Elevada | `$color-surface-raised` | `#1C2C42` | hover/foco de cartão, superfície de diálogo, linha de tabela em foco |
| Acento (10%) | `$color-accent` | `#FF8A3D` | lista fechada abaixo |
| Sobre acento | `$color-on-accent` | `#0B1220` | texto e ícone sobre laranja |
| Texto | `$color-text` | `#F8FAFC` | texto primário, identificador público, valores |
| Texto secundário | `$color-text-muted` | `#C5D1E0` | instrução, ajuda de campo, metadado; **nunca** informação essencial isolada |
| Borda | `$color-border` | `#52657D` | bordas, divisores, borda de campo em repouso; não comunica estado |
| Foco | `$color-focus` | `#FFC08A` | contorno de foco |
| Positivo | `$color-available` | `#56D19C` | chip `Matrícula registrada`, chip de papel vigente. 8,6:1 sobre `#142033` |
| Atenção | `$color-degraded` | `#F6C453` | pendência aberta no painel operacional. 10,1:1 sobre `#142033` |
| Recusa / destrutiva | `$color-unavailable` | `#FF6B6B` | recusa, erro bloqueante, ação destrutiva. 5,9:1 como texto sobre `#142033`; 6,7:1 com `#0B1220` sobre preenchimento |

**Laranja reservado exclusivamente para:** o CTA primário de cada superfície (`Salvar apelido e continuar`, `Salvar alterações`, `Registrar matrícula`, `Atualizar painel`, `Conceder papel`, `Localizar conta`), o CTA primário dos formulários Keycloak, o contorno de foco, o item ativo do menu de sessão e o monograma/wordmark.

**Laranja proibido em:** chips de papel, chips de estado da matrícula, números do painel, links de navegação, identificador público, código de referência, borda de campo válido, cabeçalho de tabela, ícones decorativos. Links simples usam `#F8FAFC` sublinhado e só recebem laranja em foco/hover.

**Destrutiva** (`Revogar papel`, `Corrigir matrícula`): gatilho em linha é botão de texto com rótulo `#FF6B6B` sobre `#142033`; o botão de confirmação dentro do diálogo é preenchido `#FF6B6B` com rótulo `#0B1220`. **Ação destrutiva nunca usa laranja** — laranja significa “caminho principal seguro”.

Cor nunca é o único meio de comunicar estado: todo chip e toda mensagem carregam também ícone e palavra. Sugeridos: `check_circle` para registrada/vigente, `warning_amber` para pendência, `error_outline` para recusa, `lock` para campo imutável.

Contraste mínimo: 4,5:1 para texto normal, 3:1 para texto grande e elemento gráfico informativo. `#0B1220` sobre `#FF8A3D` é obrigatório no botão primário.

---

## Superfícies entregues

Todas as rotas abaixo existem **em produção**, ao contrário de `tracer` e `diagnostic`, que continuam exclusivos de development/homolog e selados pelo 01-28. Caminhos em português, porque são superfícies de usuário; os caminhos técnicos em inglês permanecem como estão.

### S1 — Gate de perfil obrigatório · `/conta/perfil/completar`

Bloqueia a navegação enquanto o perfil estiver incompleto (D-39).

- Página em coluna única, centralizada, máximo `$form-max-width`, sem cartões laterais.
- Cabeçalho reduzido: marca e `Sair`. **Os itens navegacionais do menu não são renderizados enquanto o gate estiver ativo** — não desabilitados, ausentes.
- `h1` `Escolha seu apelido`.
- Corpo: `Seu apelido é o nome que aparece para outras pessoas na BETUFU. Você precisa escolher um antes de continuar.`
- Campo único `Apelido`, `autocomplete="off"`, `maxlength="20"`, `spellcheck="false"`. Ajuda persistente sob o campo: `De 3 a 20 caracteres. Use letras sem acento, números, hífen (-) ou sublinhado (_).`
- Contador `{n}/20` em `$color-text-muted`, atualizado sem `aria-live` (ruído em leitor de tela); o limite real é comunicado pela ajuda persistente.
- CTA laranja `Salvar apelido e continuar`.
- Qualquer rota de produto acessada com perfil incompleto redireciona para cá. **A guarda Angular é conveniência; a recusa do BFF prevalece.**
- Foco inicial no campo `Apelido`.

### S2 — Perfil · `/conta/perfil`

- `h1` `Seu perfil`.
- Cartão **Apelido**: campo editável com as mesmas regras de S1. CTA laranja `Salvar alterações`, desabilitado enquanto o formulário estiver intocado ou inválido.
- Cartão **Identificador público** — é aqui, e somente aqui, que homônimos se distinguem (D-43):
  - Rótulo `Identificador público`.
  - Valor: o UUID público, monoespaçado, selecionável, `$color-text`, com `overflow-wrap: anywhere`.
  - Explicação obrigatória: `Apelidos podem se repetir na BETUFU. Este identificador é único e distingue seu perfil de qualquer outro com o mesmo apelido.`
  - Botão ícone+texto `Copiar identificador`; ao concluir, `aria-live="polite"`: `Identificador copiado.`; em falha: `Não foi possível copiar. Selecione o identificador e copie novamente.`
  - Este cartão é o que torna a decisão D-43 utilizável de fato. **Não replicar o identificador em lista pública ou de produto** — ranking, grupos, chat e qualquer superfície voltada ao público. Listas operacionais auditadas (S4, S5, S6) são a exceção deliberada: visíveis apenas a papel autorizado, elas usam o identificador porque ali ele é o único seletor seguro de pessoa, já que o apelido não é único (D-42) e o e-mail é credencial (D-41).
- Cartão **Matrícula UFU**: apenas o estado, nunca o número. Registrada → chip `Matrícula registrada` em `$color-available`. Não registrada → texto e link para S3.
- **Equipe favorita não é renderizada.** Sem campo, sem rótulo, sem espaço reservado, sem “em breve”.
- O perfil **não** exibe e-mail, matrícula, HMAC, identidade civil, papéis operacionais ou qualquer indicador de MFA.

### S3 — Registro da matrícula UFU · `/conta/matricula`

- `h1` `Matrícula UFU`.
- Texto de abertura, obrigatório e literal: `O registro da matrícula é voluntário e garante que ela pertença a uma única conta BETUFU. A BETUFU não confere documentos e não guarda o número informado.`
- **Estado não registrada** (formulário):
  - Campo único de texto livre `Número da matrícula`, `autocomplete="off"`, `spellcheck="false"`, `inputmode="text"`. Ajuda: `Informe como aparece no seu registro acadêmico. Letras, números e separadores são aceitos.`
  - CTA laranja `Registrar matrícula`.
  - Aviso de imutabilidade **antes** de confirmar, não depois: `Depois de registrada, a matrícula só pode ser alterada por um administrador geral.`
- **Estado registrada** (somente leitura): chip `Matrícula registrada` com `check_circle`, e o corpo `Sua conta já tem uma matrícula registrada. O número não é exibido aqui nem em nenhuma outra tela, porque a BETUFU não o conserva. Para corrigir, fale com um administrador geral.` Sem botão de editar, sem botão de remover, sem ícone de lápis. Nenhuma data de validade, nenhum aviso de revalidação.
- **Nunca** renderizar campo de arquivo, área de arraste, botão de anexo ou qualquer menção a comprovante.
- O campo é limpo em caso de sucesso e **preservado** em caso de recusa (D-16). O valor digitado vive apenas na memória do componente: não vai para `localStorage`, `sessionStorage`, IndexedDB, URL, título de página, relatório de erro do cliente ou código de referência.

### S4 — Painel operacional · `/operacao/painel` (`OPER-06`)

- `h1` `Painel operacional`, subtítulo `Pendências e falhas da edição piloto, dentro do que seu papel autoriza.`
- CTA laranja `Atualizar painel`; durante a consulta, rótulo `Atualizando…` e apenas esse botão desabilitado, preservando os blocos válidos.
- Momento da consulta: `Consultado às HH:mm`.
- Grade responsiva de blocos. **Cada bloco só é renderizado se o papel vigente o autoriza** — bloco não autorizado é ausente, nunca desabilitado nem borrado, porque um bloco desabilitado revela a existência do escopo.
- Blocos desta fase (são estes, e só estes — os blocos de competição e moderação nascem nas fases que criam essas capacidades):

  | Bloco | Papel | Conteúdo | Regra |
  |---|---|---|---|
  | `Registros de matrícula recusados` | administrador geral | número agregado no período, separado entre `por duplicidade` e `por limite de tentativas` | jamais exibe número de matrícula, HMAC ou quem tentou |
  | `Correções administrativas de matrícula` | administrador geral | últimas correções: apelido do alvo, identificador público, ator, horário, presença de motivo | o motivo é exibido na íntegra; o número, nunca |
  | `Alterações de papel` | administrador geral | concessões, revogações e **tentativas bloqueadas** (autoconcessão, último administrador) | tentativa bloqueada aparece com ícone `error_outline` e `$color-unavailable` |
  | `Contas sem matrícula registrada` | administrador geral, administrador de competição | apenas o número agregado | sem lista, sem identificação de pessoas |

- Número agregado usa Destaque 32px; o rótulo abaixo usa Rótulo 14/600; o recorte temporal usa Corpo 16/400 em `$color-text-muted`.
- Bloco com pendência aberta recebe borda de 1px em `$color-degraded` mais ícone `warning_amber` e a palavra `Pendência`; nunca só a cor.
- Consulta de leitura: repetição automática permitida, no máximo 2 tentativas com espera de 250ms e 500ms, no mesmo padrão de `diagnostic.store.ts`.
- Papel sem bloco autorizado (moderador nesta fase) recebe o estado vazio de escopo, não uma página de erro nem uma promessa de futuro.

### S5 — Administração de papéis · `/operacao/papeis` (`OPER-01`)

- `h1` `Papéis operacionais`, subtítulo obrigatório: `Conceda e revogue papéis. Toda alteração é auditada e encerra as sessões da pessoa afetada.` — o custo de D-49 é declarado **antes** de qualquer ação, não depois.
- Os três papéis administráveis aqui incluem `administrador geral`, embora `OPER-01` nomeie apenas moderador e administrador de competição: a superfície mais ampla é exigida por D-47 (a semente cria um único administrador) e por D-48b (a saída do último administrador só é possível depois de conceder o papel a outra pessoa). Ampliação deliberada e registrada, não omissão.
- **Seleção da pessoa pelo identificador público**, nunca por e-mail e nunca por matrícula. Campo `Identificador público da pessoa` + botão laranja `Localizar conta`. Justificativa registrada no contrato: o apelido não é único (D-42) e o e-mail é credencial de acesso (D-41).
- Encontrada a pessoa, exibir apelido + identificador público (monoespaçado) e a lista dos três papéis, cada linha com nome do papel, chip de estado (`Vigente` em `$color-available` / `Não concedido` em `$color-text-muted`) e o controle correspondente.
- Concessão: botão laranja `Conceder papel`. Revogação: botão de texto `Revogar papel` com rótulo `#FF6B6B`.
- **Ambas exigem diálogo de confirmação**, porque ambas encerram sessões alheias.
- **Guarda (a) — autoconcessão bloqueada (D-48a).** Quando o alvo localizado for o próprio ator, os botões `Conceder papel` são desabilitados e acompanhados do motivo visível: `Concessão a si mesmo bloqueada. Peça a outro administrador geral.` Os botões de revogar permanecem habilitados. O motivo é ligado ao botão por `aria-describedby`.
- **Guarda (b) — último administrador geral (D-48b).** Quando o ator for o único administrador geral, o botão `Revogar papel` da linha `Administrador geral` é desabilitado com o motivo visível: `Você é o único administrador geral. Conceda o papel a outra pessoa antes de revogar o seu.`
- Desabilitar é conveniência. **A recusa autoritativa vem do servidor** e tem cópia própria (ver *Contrato de cópia*), porque a guarda precisa continuar funcionando quando o estado do cliente estiver defasado.
- Se o ator revogar o próprio papel com sucesso, a própria sessão termina; a interface cai no estado de sessão encerrada de S7.

### S6 — Correção administrativa de matrícula · `/operacao/matriculas/corrigir` (D-32)

- `h1` `Corrigir matrícula`.
- Texto de abertura: `A correção libera a matrícula registrada nesta conta e registra a nova. O número anterior não é exibido, porque a plataforma não o conserva.`
- Campos, nesta ordem: `Identificador público da conta`; `Nova matrícula`; `Motivo da correção` (área de texto, **obrigatório**), com ajuda `Descreva por que a correção é necessária. O motivo fica na auditoria e não pode ser apagado depois.`
- CTA `Corrigir matrícula`, seguido de diálogo de confirmação destrutiva.
- Sem o motivo preenchido, o CTA permanece desabilitado **e** o envio é recusado pelo servidor.

### S7 — Navegação e sessão (evolução do cabeçalho da Fase 1)

- O botão de identidade abre o `mat-menu` já contratado na Fase 1. Itens desta fase: `Meu perfil`, `Matrícula UFU`, divisor, `Sair`.
- Itens governados por papel — `Painel operacional`, `Papéis operacionais`, `Corrigir matrícula` — aparecem em um grupo próprio rotulado `Operação` **somente quando o papel vigente os autoriza**. Sem papel: os itens não existem no DOM. **Nunca renderizar item desabilitado**, porque isso revela a existência do escopo.
- Enquanto o gate S1 estiver ativo, o menu contém apenas `Sair`.
- O menu não exibe papéis como texto, e-mail, matrícula ou selo de MFA.
- Estado de sessão encerrada por alteração de papel ou por redefinição de senha (D-40, D-49): reutiliza a superfície de sessão encerrada da Fase 1 com a cópia de *Contrato de cópia*. Quando o BFF não informar a causa, usar a cópia genérica — **não deduzir a causa no cliente**.

---

## Contrato de cópia

Todo texto abaixo é **pt-BR literal**, em conformidade com D-14 a D-17: linguagem simples, diz o que falhou e qual a próxima ação possível, oferece código de referência copiável, e nunca expõe stack trace, endereço interno, nome de tabela, coluna, código HTTP cru ou regra de autorização.

### CTAs primários

| Superfície | Rótulo em repouso | Rótulo em andamento |
|---|---|---|
| S1 Gate de perfil | `Salvar apelido e continuar` | `Salvando…` |
| S2 Perfil | `Salvar alterações` | `Salvando…` |
| S3 Matrícula | `Registrar matrícula` | `Registrando…` |
| S4 Painel | `Atualizar painel` | `Atualizando…` |
| S5 Papéis (busca) | `Localizar conta` | `Localizando…` |
| S5 Papéis (concessão) | `Conceder papel` | `Concedendo…` |
| S6 Correção | `Corrigir matrícula` | `Corrigindo…` |

Durante o andamento, desabilitar **somente** o botão acionado, preservar todo o conteúdo válido já exibido e manter o formulário legível.

### Estados vazios

| Superfície | Título | Corpo |
|---|---|---|
| S3 Matrícula não registrada | `Nenhuma matrícula registrada` | `Você ainda não registrou sua matrícula UFU. O registro é voluntário e é o que permite participar do ranking UFU.` |
| S4 Painel sem pendências | `Nada pendente agora` | `Nenhuma pendência ou falha aberta no seu escopo. Selecione “Atualizar painel” para consultar novamente.` |
| S4 Papel sem bloco autorizado | `Nenhum item no seu escopo` | `Seu papel não tem pendências abertas nesta etapa.` |
| S5 Pessoa sem papéis | `Nenhum papel concedido` | `Esta pessoa não tem papéis operacionais. Selecione um papel para conceder.` |
| S5 Pessoa não localizada | `Nenhuma conta com esse identificador` | `Confira o identificador público e tente novamente. Ele é diferente do apelido.` |

Estado vazio só aparece após resposta válida sem dados. **Nunca confundir vazio com falha.**

### Erros de campo (âncora no campo, foco no primeiro inválido, valor digitado preservado)

| Campo | Mensagem |
|---|---|
| Apelido vazio | `Informe um apelido para continuar.` |
| Apelido fora do padrão | `Use de 3 a 20 caracteres, apenas letras sem acento, números, hífen (-) ou sublinhado (_).` |
| Matrícula vazia | `Informe o número da matrícula.` |
| Matrícula fora do padrão | `Este número não corresponde ao formato de matrícula da UFU. Confira e digite novamente.` |
| Identificador público inválido | `Este identificador não tem o formato esperado. Copie-o do perfil da pessoa.` |
| Motivo da correção vazio | `Informe o motivo da correção.` |
| Motivo curto demais | `Descreva o motivo com mais detalhe. Ele precisa fazer sentido para quem ler a auditoria depois.` |

### Recusas de negócio (bloco no topo do formulário, `role="alert"`, valor digitado preservado)

| Situação | Mensagem |
|---|---|
| Matrícula já pertence a outra conta (`VERI-07`, D-30) | `Esta matrícula já está registrada em outra conta BETUFU. Se ela é sua, fale com um administrador geral para pedir a correção. Código de referência: {correlationId}.` |
| Limite de tentativas atingido (D-30) | `Muitas tentativas de registro. Aguarde alguns minutos e tente novamente. Código de referência: {correlationId}.` — **não** informar quantas tentativas restam, quantas foram feitas nem o tamanho exato da janela; a contagem exposta ajudaria a sondagem que a D-31 tenta encarecer. |
| Conta já tem matrícula registrada | `Sua conta já tem uma matrícula registrada. Só um administrador geral pode alterá-la. Código de referência: {correlationId}.` |
| Autoconcessão bloqueada (D-48a) | `Você não pode conceder papéis a si mesmo. Peça a outro administrador geral. Código de referência: {correlationId}.` |
| Último administrador geral (D-48b) | `Você é o único administrador geral. Conceda o papel a outra pessoa antes de revogar o seu. Código de referência: {correlationId}.` |
| Nova matrícula já registrada em outra conta (S6) | `A nova matrícula já está registrada em outra conta. A correção não foi aplicada. Código de referência: {correlationId}.` |

### Erros de transporte e sistema

| Situação | Mensagem |
|---|---|
| Falha ao salvar perfil | `Não foi possível salvar seu perfil. Verifique a conexão e tente novamente. Código de referência: {correlationId}.` |
| Falha ao registrar matrícula | `Não foi possível registrar a matrícula. Verifique a conexão e tente novamente. Nada foi alterado na sua conta. Código de referência: {correlationId}.` |
| Falha ao carregar o painel, sem dados anteriores | `Não foi possível carregar o painel. Tente novamente. Código de referência: {correlationId}.` |
| Falha ao atualizar o painel, com dados anteriores | `Não foi possível atualizar o painel. Verifique a conexão e tente novamente. Código de referência: {correlationId}.` — manter os blocos válidos, marcados com `Dados da última consulta; atualização não concluída.` |
| Bloco isolado indisponível | `Este bloco não pôde ser carregado.` — restrito ao próprio cartão; os demais blocos e a página continuam utilizáveis (D-17). |
| Falha em alteração de papel | `Não foi possível alterar o papel. Nenhuma alteração foi aplicada. Tente novamente. Código de referência: {correlationId}.` |
| Sessão expirada (401) | `Sua sessão expirou. Entre novamente para continuar.` |
| Sem autorização (403) | `Você não tem acesso a esta área.` — sem explicar qual papel faltou nem se a área existe. |
| Erro genérico | `Algo não funcionou como esperado. Tente novamente. Código de referência: {correlationId}.` |
| Sessão encerrada por alteração de papel (causa informada pelo BFF) | `Seu papel foi alterado e sua sessão foi encerrada. Entre novamente para continuar.` |
| Sessão encerrada por alteração de segurança da conta (causa informada) | `Sua conta passou por uma alteração de segurança e a sessão foi encerrada. Entre novamente para continuar.` |
| Sessão encerrada, causa não informada | `Sua sessão foi encerrada. Entre novamente para continuar.` |

### Sucessos (região `aria-live="polite"`, sem toast que some antes da leitura)

| Situação | Mensagem |
|---|---|
| Apelido salvo no gate | `Apelido salvo. Bem-vindo à BETUFU.` |
| Perfil atualizado | `Perfil atualizado.` |
| Matrícula registrada | `Matrícula registrada. O número não fica guardado nem aparece em nenhuma tela.` |
| Papel concedido | `{Papel} concedido a {apelido}. As sessões dessa pessoa foram encerradas.` |
| Papel revogado | `{Papel} revogado de {apelido}. As sessões dessa pessoa foram encerradas.` |
| Matrícula corrigida | `Matrícula corrigida. O motivo foi registrado na auditoria.` |
| Identificador copiado | `Identificador copiado.` |
| Código de referência copiado | `Código de referência copiado.` |

### Confirmações destrutivas

Diálogo modal (`mat-dialog`), foco inicial no botão **cancelar**, `Esc` cancela, ação confirmatória nomeando explicitamente o que acontece. Nenhuma dessas ações é auto-repetida.

| Ação | Título | Corpo | Botões |
|---|---|---|---|
| Conceder papel | `Conceder {papel} a {apelido}?` | `A pessoa passa a ter esse papel imediatamente e todas as sessões dela são encerradas. A ação fica registrada na auditoria.` | `Conceder papel` (laranja) · `Cancelar` |
| Revogar papel | `Revogar {papel} de {apelido}?` | `A pessoa perde esse papel imediatamente e todas as sessões dela são encerradas. A ação fica registrada na auditoria.` | `Revogar papel` (`#FF6B6B` preenchido, rótulo `#0B1220`) · `Cancelar` |
| Revogar o próprio papel | `Revogar seu papel de administrador geral?` | `Você perde o papel imediatamente e sua sessão é encerrada. Só outro administrador geral poderá devolvê-lo.` | `Revogar meu papel` (destrutivo) · `Cancelar` |
| Corrigir matrícula | `Corrigir a matrícula de {apelido}?` | `A matrícula registrada hoje é liberada e substituída pela nova. Não há como desfazer sozinho — seria preciso outra correção. O motivo informado fica na auditoria.` | `Corrigir matrícula` (destrutivo) · `Cancelar` |

`Sair` continua **sem** confirmação, como na Fase 1. Registrar a matrícula não usa diálogo: o aviso de imutabilidade fica visível junto do CTA, antes do envio, o que é mais honesto do que um modal depois do clique.

---

## Comportamento de formulário, rede e retentativa

Conformidade obrigatória com D-14 a D-17:

1. **Valores digitados são preservados** em qualquer falha ou recusa. Nenhum formulário desta fase é limpo por erro. Exceção única: o campo de matrícula é limpo **no sucesso**.
2. **Cada campo inválido recebe explicação própria**, ancorada ao campo por `aria-describedby`, com `aria-invalid="true"`; o foco vai para o **primeiro** campo inválido; a mensagem nunca substitui o rótulo.
3. **Somente consultas de leitura podem repetir automaticamente** — carregamento do perfil, do estado da matrícula, do painel e da busca de pessoa: no máximo 2 tentativas, espera de 250ms e 500ms. **Nenhuma mutação repete sozinha**: salvar apelido, registrar matrícula, conceder, revogar e corrigir são sempre acionadas por gesto humano. Repetir o registro de matrícula sozinho consumiria o limite de tentativas do usuário sem que ele soubesse.
4. **Falha isolada degrada apenas a própria região.** Um bloco do painel que falha não derruba os outros nem a página.
5. **Envio duplo é impossível**: o CTA desabilita durante o andamento e o formulário ignora `Enter` repetido enquanto houver requisição em voo.
6. Nenhuma superfície faz atualização automática periódica. O painel só muda quando carregado ou quando `Atualizar painel` é acionado.

---

## Privacidade como requisito visual

`PRIV-01` e `PROF-02` são contrato de interface, não apenas de backend.

- **A matrícula e o HMAC nunca aparecem em URL, rota, parâmetro de consulta, fragmento, título de página, `document.title`, atributo de dado, breadcrumb ou histórico do navegador.** A rota de matrícula é única e sem identificador. O identificador público **pode** aparecer em URL; a matrícula e o HMAC, nunca.
- **O número da matrícula nunca é reexibido depois do envio**, nem mascarado, nem parcial, nem em modo “mostrar”. Não há campo de leitura, ícone de olho ou botão de copiar para ele.
- O valor digitado da matrícula não é gravado em `localStorage`, `sessionStorage`, IndexedDB, cookie, cache de formulário do Angular, relatório de erro do cliente ou qualquer telemetria. O campo usa `autocomplete="off"`.
- O **código de referência copiável nunca contém** matrícula, HMAC, e-mail, token ou identificador de sessão. É apenas o `correlationId` já contratado na Fase 1.
- **O perfil público exibe somente** apelido, equipe favorita (a partir da Fase 3), posição permitida e conquistas. Nunca e-mail, matrícula, HMAC, identidade civil, papéis operacionais ou indicador de MFA.
- Telas administrativas identificam pessoas por **apelido + identificador público**. Não exibem e-mail nem matrícula, mesmo para o administrador geral, porque nenhuma decisão desta fase depende deles.
- Contagens agregadas do painel (`Contas sem matrícula registrada`, `Registros de matrícula recusados`) são apresentadas **sem lista de pessoas**, para que o agregado não vire enumeração.
- Nenhuma analytics, pixel, iframe, fonte remota ou requisição de terceiro. Qualquer telemetria futura exige revisão de privacidade antes de existir.
- O cliente não guarda `access token` nem `refresh token` em nenhum armazenamento do navegador. O BFF continua dono de sessão e autorização.

---

## Acessibilidade, movimento e conteúdo resiliente

- WCAG 2.2 AA como mínimo: contraste declarado acima, ordem de foco coerente com o DOM, alvos de 44px, rótulos programáticos e operação integral por teclado.
- Foco: `outline: 3px solid #FFC08A; outline-offset: 3px` em todo controle, inclusive itens de `mat-menu`, botões de linha de tabela, campos e botões de diálogo. Nunca remover o foco nativo sem substituição equivalente.
- O diálogo modal prende o foco, devolve o foco ao gatilho ao fechar e tem `aria-labelledby` no título e `aria-describedby` no corpo.
- `aria-live="polite"` para sucesso e cópia; `role="alert"` apenas para recusa de negócio e falha bloqueante. Ao falhar um único bloco do painel, anunciar somente o bloco afetado.
- Botão desabilitado por guarda (autoconcessão, último administrador) **sempre** traz o motivo em texto visível, ligado por `aria-describedby`. Botão desabilitado sem motivo legível é violação deste contrato.
- Chips de estado têm nome acessível completo, por exemplo `Administrador geral: vigente` e `Matrícula registrada`.
- Transições de opacidade e elevação de até 160ms. Sob `prefers-reduced-motion: reduce`, remover transições e spinners decorativos, mantendo o indicador textual (`Salvando…`, `Atualizando…`) e o progresso funcional.
- Sem rolagem horizontal em 320px. Apelido e nomes quebram por palavra; identificador público e código de referência quebram com `overflow-wrap: anywhere`; motivos de correção quebram em várias linhas e **não são truncados**, porque a auditoria depende de lê-los inteiros.
- Tabelas de papéis e de auditoria viram cartões empilhados abaixo de 600px, com rótulo por valor; nunca contêiner com rolagem horizontal.

---

## UI Considerations (Considerações de UI)

> Título mantido em inglês porque `plan-phase.md:825` levanta esta seção pelo nome literal `## UI Considerations` para dentro dos `must_haves` dos planos, e sua checklist exige *no silent drops*.

Sondagem executada em 2026-08-20 com `ui-consideration-probe.cjs` sobre as sete superfícies S1–S7: **41 pares superfície×categoria aplicáveis, 41 cobertos, 0 backstop, 0 não resolvidos.**

O tipo de cada elemento foi declarado explicitamente (`form`, `list-collection`, `interactive-control`, `nav`, `static-content`) em vez de inferido: o classificador do engine trabalha com pistas em inglês e devolveu `unclassified` para as sete superfícies descritas em português. O override é o caminho previsto pelo próprio workflow para esse caso, e sem ele a cobertura teria sido silenciosamente vazia.

As 41 resoluções estão agregadas nas oito categorias abaixo, cada linha nomeando as superfícies a que se aplica. A concordância de número dos agregados do painel foi ratificada pelo responsável em 2026-08-20.

| Categoria | Elemento(s) | Status | Resolução |
|---|---|---|---|
| empty | formulários de apelido, matrícula e correção; blocos do painel; lista de papéis | ✅ covered | Cada superfície tem título e corpo próprios em *Estados vazios*, exibidos apenas após resposta válida sem dados; o formulário não vazio nunca é apresentado como falha, e o painel sem escopo autorizado mostra `Nenhum item no seu escopo` em vez de erro. |
| loading | gate de perfil, perfil, matrícula, painel, busca de pessoa, concessão/revogação, correção | ✅ covered | O layout não salta: o CTA acionado troca de rótulo (`Salvando…`, `Registrando…`, `Atualizando…`, `Localizando…`, `Concedendo…`, `Corrigindo…`), só ele é desabilitado, e o conteúdo válido já exibido permanece. |
| error | todos os formulários, todos os blocos do painel, o menu governado por papel | ✅ covered | Erro de campo fica ancorado ao campo com foco no primeiro inválido; recusa de negócio ocupa bloco `role="alert"` no topo com código copiável; falha de bloco degrada só aquele cartão; 401 e 403 usam cópia que não revela regra de autorização nem existência de escopo. |
| populated | blocos do painel operacional, lista de papéis da pessoa | ✅ covered | O painel autorizado exibe os quatro blocos contratados com número agregado em Destaque 32px, rótulo em 14/600 e recorte temporal; a lista de papéis exibe sempre as três linhas fixas com chip `Vigente` ou `Não concedido`. |
| partial | formulários com falha parcial, painel com bloco indisponível | ✅ covered | Valores digitados são preservados em qualquer falha, com marca por campo; blocos válidos continuam visíveis e são rotulados `Dados da última consulta; atualização não concluída.` quando a atualização não completa. |
| overflow | identificador público, código de referência, motivo da correção, tabelas de papéis e auditoria, menu do cabeçalho | ✅ covered | Nenhuma superfície produz rolagem horizontal em 320px: identificadores quebram com `overflow-wrap: anywhere`, tabelas viram cartões empilhados abaixo de 600px e o menu rola verticalmente mantendo alvos de 44px. |
| zero-one-many | blocos do painel, lista de papéis, histórico de correções e de alterações de papel | ✅ covered | Zero usa a cópia de estado vazio; um e muitos usam concordância explícita — `1 recusa registrada` e `{n} recusas registradas`, `1 correção` e `{n} correções` — e a lista de papéis tem cardinalidade fixa de três linhas, jamais ambígua. |
| long-text | apelido, matrícula digitada, motivo da correção, apelidos alheios em telas administrativas, itens de menu | ✅ covered | Apelido é limitado a 20 caracteres com contador e quebra por palavra; motivo quebra em múltiplas linhas sem truncar; apelido alheio pode truncar visualmente apenas com `title`/`aria-label` completo; itens de menu quebram em duas linhas em vez de cortar. |

---

## Segurança de registro

| Registro | Blocos usados | Safety Gate |
|---|---|---|
| Angular Material/CDK (dependências oficiais npm, já fixadas pela Matriz B da Fase 1) | `mat-form-field`, `mat-input`, `mat-button`, `mat-card`, `mat-menu`, `mat-dialog`, `mat-chip`, `mat-icon`, `mat-progress-spinner`, CDK a11y/overlay | Não é registry shadcn; nenhum pacote novo é introduzido por este contrato. Confirmado em 2026-08-20. |
| shadcn official | nenhum | Não aplicável — `components.json` ausente por decisão de stack Angular (Fase 1). Gate shadcn não se aplica a projeto Angular. |
| Terceiros | nenhum | Nenhum registry de terceiro é declarado nesta fase. Não há bloco a inspecionar e nenhum vetting pendente. Confirmado em 2026-08-20. |

---

## Pontos herdados e gates de execução

| Item | Resolução no contrato | Gate de execução |
|---|---|---|
| Padrão exato da matrícula UFU | A UI não fixa a expressão regular; ela consome o padrão do backend e apresenta a recusa de formato com a cópia contratada. **A pesquisa deve confirmar o formato real antes de fixar**, preferindo padrão que não rejeite pós-graduação ou aluno antigo (D-29). | Plano de pesquisa da fase; teste de campo com matrícula de pós e de aluno antigo. |
| Frequência de troca do apelido | O contrato de UI não decide o intervalo. Se houver intervalo mínimo, o campo fica somente leitura com a cópia `Você poderá trocar o apelido novamente em {quando}.` — **a decisão pertence ao planejamento**, e a UI precisa dessa cópia se ela existir. | Decisão registrada no PLAN antes de implementar S2. |
| Valores do limite de tentativas | A UI nunca exibe o valor, a contagem restante nem o tamanho da janela (D-31). Só a cópia genérica de espera. | Teste que verifica ausência de contador na resposta e na tela. |
| Mecanismo do gatilho `SECURITY` (D-40) | A UI só reage ao estado de sessão encerrada e à causa **quando o BFF a informar**; não deduz causa no cliente. | Contrato de sessão do PLAN precisa declarar se a causa é publicada; se não for, apenas a cópia genérica é usada. |
| Códigos de erro estáveis | As recusas desta fase entram no enum `ApiErrorCode` existente, no formato `BETUFU-{status}-{NOME}`, junto de `title`, `detail` e `action` em português. A UI usa a mensagem contratada acima e o código estável; **não inventa taxonomia no cliente**. | Regeneração do cliente TypeScript e atualização do digest governado da árvore gerada. |
| Duas páginas Keycloak novas | Cadastro e confirmação de e-mail precisam de smoke de tema comprovando tokens, fontes, foco, CTA laranja e o aviso de rodapé exatamente uma vez. | Smoke do tema `betufu` estendido para `register` e `login-verify-email`. |
| Primeiras rotas de produção | Esta fase entrega as primeiras rotas Angular presentes no build de produção. `app.routes.production.ts` deixa de ser vazio; `tracer` e `diagnostic` continuam ausentes de produção. | Validador de fronteira de rotas atualizado no mesmo bloco, ou a suíte Node quebra. |

---

## Checker Sign-Off

- [x] Dimensão 1 Copywriting: PASS
- [x] Dimensão 2 Visuals: PASS
- [x] Dimensão 3 Color: PASS
- [x] Dimensão 4 Typography: PASS
- [x] Dimensão 5 Spacing: PASS
- [x] Dimensão 6 Registry Safety: PASS

**Approval:** aprovado em 2026-08-20.

Duas passadas do `gsd-ui-checker`. A primeira retornou `VERIFIED` com FLAG em Copywriting e Espaçamento; seis achados foram corrigidos. A segunda retornou `VERIFIED` com FLAG remanescente apenas em Espaçamento, resolvido pela aprovação do token `$form-max-width` pelo responsável. Fidelidade de token reconferida contra `_tokens.scss` nas duas passadas.
