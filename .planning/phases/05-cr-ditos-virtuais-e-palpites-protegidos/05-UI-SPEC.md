---
phase: 5
slug: creditos-virtuais-e-palpites-protegidos
status: approved
shadcn_initialized: false
preset: none
created: 2026-09-13
reviewed_at: 2026-09-13
checker: control-plane-review
---

# Fase 5 — Contrato de Design de UI

> Contrato visual e de interação para o bilhete próprio de palpite integrado à
> página pública de odds. Estende os contratos aprovados das Fases 1, 2, 3 e 4;
> onde não divergir, os tokens, shells, estados e padrões anteriores continuam
> valendo.

---

## Escopo e fontes de decisão

| Fonte | Decisão executável usada neste contrato |
|---|---|
| `05-CONTEXT.md` D-122..D-133 | Concessão inicial de 1.000,00 créditos; mínimo 10,00; máximo igual ao saldo disponível, com all-in permitido; escala 2; retorno `stake × odd` incluindo stake; `HALF_UP`; conflito administrativo tratado pelo servidor; bilhete próprio persistente na página pública; stale exige reconfirmação explícita. |
| `REQUIREMENTS.md` CRED-03/04/06/08/10, ODDS-14, PRED-01..08, INTE-01/04, UX-01/02 | Saldo e aceite são autoritativos no backend; UI cobre apenas orientação, entrada, confirmação e comprovante próprio, sem histórico completo ou dados de terceiros. |
| `05-01-PLAN.md` e `05-02-PLAN.md` | Ledger e aceite são imutáveis, atômicos e idempotentes; a interface não promete concessão nem calcula autoridade. |
| `05-03-PLAN.md` | O BFF usa `/api/v1/me/credits` e `/api/v1/me/predictions`; Angular consome somente cliente OpenAPI gerado, cookies/XSRF e `Idempotency-Key` quando exigido pelo contrato. |
| `05-04-PLAN.md` | O bilhete é um componente standalone integrado ao mercado público existente, com adapters/stores Signals e sem rota operacional nova. |
| `05-05-PLAN.md` | Estados e privacidade serão exercitados em Chromium/Firefox, desktop e 320 px; SKIP não é PASS e qualquer falha deve ser visível. |
| `04-UI-SPEC.md` e `frontend/src/styles/_tokens.scss` | Arena/Console compartilham identidade, tokens, foco, estados e componentes locais; não criar segunda paleta, fonte, registry ou biblioteca. |

Esta fase não desenha liquidação, pagamento, ranking, histórico completo,
apostas múltiplas, apostas ao vivo, compra/venda/transferência/saque/recarga,
ajuste administrativo discricionário ou uma lista de palpites de terceiros.
"Créditos" nunca recebem símbolo monetário nem equivalência com reais.

---

## Sistema de design

| Propriedade | Valor |
|---|---|
| Ferramenta | Angular 22 standalone/zoneless, HTML semântico e SCSS local; nenhum pacote visual novo |
| Preset | Não aplicável; `components.json` ausente e shadcn não é usado |
| Biblioteca de componentes | Componentes locais existentes (`ArenaShell`, `ConsoleShell`, `ConfirmDialog`, `ResponsiveDataView`) e elementos HTML nativos; não instalar Angular Material/CDK nesta fase |
| Ícones | Material Symbols Rounded já auto-hospedados, somente quando houver ativo local; ícone sempre acompanha texto visível ou nome acessível |
| Fontes | Inter local 400/600 para interface e Barlow Condensed local 600 para títulos; nenhum CDN ou byte novo |
| Tema | Somente escuro, herdado da Arena; não criar alternância claro/escuro |
| Forma | Tokens `$radius-md`/`$radius-card` em cartões e campos, `$radius-sm` em chips e `$radius-full` apenas em indicadores circulares; `$shadow-raised` somente em superfície elevada |
| Movimento | Transições curtas de estado, sem loop decorativo; `prefers-reduced-motion` remove transformações e fixa o estado |

### Tese visual da fase

- O bilhete é uma continuação da leitura pública da odd, não uma casa de
  apostas separada: confronto e odd vigente vêm antes do formulário.
- A hierarquia é factual: seleção, créditos, versão aceita, fechamento e
  retorno potencial aparecem próximos, com a regra do cálculo legível.
- O destaque coral marca a seleção e o CTA ativo, não transforma a tela em
  promoção nem sugere garantia de resultado.
- Estados de visitante, vínculo ausente, elegibilidade, saldo insuficiente,
  bloqueio administrativo, stale, suspensão, fechamento, erro e sucesso são
  blocos explícitos. Nenhum estado é comunicado somente por cor.

---

## Escala de espaçamento e forma

Todos os valores vêm de `frontend/src/styles/_tokens.scss` e devem ser
consumidos pelo nome, nunca repetidos como literais em SCSS novo.

| Token | Valor | Uso na fase |
|---|---:|---|
| `space-1` | 4px | lacuna ícone-texto, divisor e ajuste inline |
| `space-2` | 8px | ajuda de campo, chips e grupos compactos |
| `space-4` | 16px | gap padrão, padding lateral mobile e linhas do formulário |
| `space-6` | 24px | padding do bilhete, grupos relacionados e painel de estado |
| `space-8` | 32px | gap entre coluna do mercado e rail do bilhete; padding desktop |
| `space-12` | 48px | separação entre mercado, bilhete, explicação e histórico |
| `space-16` | 64px | respiro de página e transição entre áreas da Arena |

Exceções aprovadas pelo contrato herdado: alvo acionável mínimo de 44×44 px,
outline de foco de 3 px com offset de 3 px, ícones de 20 px inline/24 px em
controles e `$form-max-width: 640px` para grupos de entrada. Não criar uma
escala paralela nem usar `gap` arbitrário fora dos tokens.

---

## Tipografia e números

| Papel | Fonte | Tamanho | Peso | Altura de linha | Uso |
|---|---|---:|---:|---:|---|
| Corpo | Inter | 16px | 400 | 1.5 | explicações, ajuda, erros, estados e valores não numéricos |
| Rótulo | Inter | 14px | 600 | 1.3 | labels, legendas, chips, botões e metadados |
| Título | Barlow Condensed | `clamp(24px, 4vw, 56px)` | 600 | 0.9–1.15 | título do mercado e seções principais |
| Destaque | Barlow Condensed | `clamp(32px, 6vw, 72px)` | 600 | 0.82–1.0 | somente odd vigente e resumo de retorno; não usar para parágrafos |

Odd, saldo, stake, retorno, lucro, escala e versões usam
`font-variant-numeric: tabular-nums`. Valores de créditos sempre mostram duas
casas (`1.000,00`), sem símbolo monetário. Nomes de equipes, mensagens, estados,
versões e deadlines quebram linha e nunca são truncados.

---

## Cor e contraste

| Papel | Valor/token | Uso |
|---|---|---|
| Dominante (60%) | `#120B12` (`--bt-canvas`) e `#09080D` (`--bt-ink`) | fundo contínuo da Arena, cabeçalho e texto sobre coral |
| Secundária (30%) | `#171018` (`--bt-surface`), `#21131D` (`--bt-surface-raised`), `#741D42` (`--bt-wine`) | cartão do bilhete, estados, disclosure e agrupamentos |
| Accent (10%) | `#FF626B` (`--bt-signal`) | odd/seleção vigente, CTA primário, ação `Usar saldo total` e foco adjacente |
| Accent soft | `#FFB4B8` (`--bt-signal-soft`) | metadados enfatizados e detalhes de metodologia |
| Texto | `#FFF4F4` (`--bt-text`) e `#CDBFC5` (`--bt-text-muted`) | leitura principal e metadados secundários |
| Foco | `#FFD2D4` (`--bt-focus`) | outline independente do accent |
| Sucesso | `#72D6A0` (`--bt-success`) | `Palpite registrado`/saldo atualizado, sempre com palavra e ícone |
| Aviso | `#F5C95C` (`--bt-warning`) | stale, baixa confiança, saldo no limite e mercado suspenso |
| Erro | `#FF7A82` (`--bt-danger`) | erro, bloqueio, rejeição e conflito; nunca identidade |

Contraste mínimo: 4,5:1 para texto normal e 3:1 para texto grande/elemento
gráfico. Botão primário usa texto `#09080D` sobre `#FF626B`. Cor nunca é a
única informação: seleção tem texto/controle, estados têm título e descrição,
sucesso/erro têm região semântica e ação.

Accent fica reservado a odd vigente, seleção marcada, `Enviar palpite`,
`Usar saldo total`, horário de fechamento, item ativo e foco adjacente. Links
informativos usam texto claro sublinhado; não pintar todos os cards, saldos ou
mensagens com coral.

---

## Superfície e arquitetura de informação

### P1 — Mercado público com bilhete próprio

O bilhete é integrado ao `PublicMarketPageComponent`/`PublicMarketStore` atual,
depois do resumo da odd vigente e antes do histórico. Não criar `/apostar`,
`/palpites` ou shell novo. A ordem acessível é:

1. cabeçalho Arena e retorno para a agenda/evento;
2. confronto, estado, odd vigente, confiança, explicação e deadline;
3. cartão `Palpite neste mercado`, com o estado de elegibilidade correspondente;
4. histórico público de versões da odd, sem palpites de terceiros.

Em desktop o conteúdo do mercado ocupa a coluna principal e o bilhete uma
coluna auxiliar de 320–400 px, dentro do contêiner de 1240 px. A ordem no DOM
continua sendo a ordem de leitura; a coluna auxiliar não pode capturar foco
antes do contexto que ela resume. Em mobile o bilhete vira um bloco de fluxo
único, sem position sticky obrigatório e sem deslocamento horizontal.

### P2 — Anatomia do bilhete elegível

O cartão tem título, estado curto e grupos semânticos:

- `Saldo disponível`: valor de créditos da própria conta, com data de leitura
  quando fornecida pelo contrato;
- `Escolha do resultado`: `fieldset`/`legend` com uma opção por seleção da odd
  vigente, nome textual e odd em duas casas; a seleção marcada é perceptível
  por controle, borda, texto e estado acessível;
- `Créditos do palpite`: input numérico decimal com label acima, escala máxima 2,
  mínimo 10,00 e máximo igual ao saldo; ajuda explica `All-in permitido` sem
  sugerir obrigação;
- atalho `Usar saldo total`, que preenche o máximo localmente sem enviar;
- resumo informativo `Retorno potencial` e `Lucro líquido`, identificando que o
  primeiro inclui os créditos originalmente informados e que o servidor é a
  autoridade;
- `Odd aceita`, `Versão da regra` e `Fecha em`, exibidos como fatos da leitura
  atual; o formulário nunca envia cálculo como autoridade;
- CTA `Enviar palpite`, desabilitado enquanto houver campo inválido, loading,
  estado não elegível, mercado fechado/suspenso ou confirmação stale ausente.

O cartão não mostra papel, capability, escopo, saldo de terceiros, quantidade
de palpites, distribuição, liquidez, volume, identidade operacional ou token.

### P3 — Comprovante próprio

Após aceite, o formulário é substituído no mesmo cartão por `Palpite registrado`.
O comprovante mostra somente seleção, créditos, odd aceita, versão da regra,
retorno potencial, lucro líquido, deadline e estado `Pendente`. Pode mostrar o
código de referência permitido pelo contrato, mas nunca matrícula, e-mail,
identidade ou a chave de idempotência. Uma nova leitura do mesmo mercado deve
reconstruir esse cartão próprio; não há edição, cancelamento ou lista de
terceiros nesta fase.

---

## Estados, interação e cópia

O store orienta a composição; ele não concede créditos, não decide
elegibilidade e não transforma capability em autorização. A API é a autoridade.

| Estado | Composição e ação segura |
|---|---|
| Loading | skeleton preserva a altura do cartão; `aria-busy=true`; nenhum botão de mutação acionável |
| Visitante | Título `Entre para palpitar`; corpo explica que é necessário entrar para usar créditos virtuais; CTA `Entrar para palpitar` encaminha ao fluxo existente e não repete POST |
| Conta sem matrícula ativa | Título `Vínculo necessário`; corpo orienta registrar matrícula ativa; CTA `Registrar matrícula`; não mostrar matrícula ou HMAC |
| Elegível | Saldo, seleção, stake, retorno, lucro, odd/regra/deadline e `Enviar palpite` conforme anatomia P2 |
| Saldo abaixo do mínimo | Título `Saldo insuficiente`; corpo `Você precisa de pelo menos 10,00 créditos disponíveis para enviar este palpite.`; formulário permanece legível, envio bloqueado |
| Administrativamente impedido | Título `Palpite indisponível`; corpo genérico `Sua conta não pode enviar palpites neste momento.`; não revelar papel, escopo, capability ou regra |
| Mercado suspenso | Título `Mercado suspenso`; corpo `Este mercado está suspenso para verificação. A versão pública continua disponível, mas novos palpites estão bloqueados.`; leitura permanece acessível |
| Mercado CLOSED/LOCKED | Título `Mercado encerrado`; corpo `Este mercado não aceita novos palpites. A versão registrada permanece disponível para consulta.`; somente leitura |
| Odd stale | Banner `A odd mudou antes do envio`; mostrar a nova odd/versão, limpar a confirmação anterior e exigir checkbox/controle explícito `Confirmo a nova odd publicada` antes de reativar `Enviar palpite`; nunca reenviar automaticamente |
| Sucesso | Região `aria-live=polite` com `Palpite registrado`; substituir formulário pelo comprovante P3, sem toast temporário |
| 401 | `Entre novamente para continuar`; nenhuma mutação é repetida; preservar contexto público |
| 403/inelegibilidade | `Não foi possível enviar este palpite`; ação contextual segura; sem diagnóstico de autorização |
| 404 | `Mercado não encontrado`; CTA `Voltar para a agenda`; não inferir que o mercado existe |
| 409 não-stale | `Não foi possível confirmar este palpite`; preservar leitura local, orientar atualização e não sobrescrever campos sem ação explícita |
| Erro de transporte/servidor | `Não foi possível enviar o palpite agora. Nenhuma alteração foi confirmada.`; CTA `Tentar novamente` só após ação explícita e com nova chave de idempotência conforme o adapter |
| Parcial | Blocos válidos continuam visíveis; saldo, odd ou regra ausentes são marcados como indisponíveis e bloqueiam apenas a ação dependente; nunca usar zero fictício |

### Reconfirmação de odd stale

O stale é uma transição de integridade, não apenas um alerta cromático. A
resposta 409 deve preservar a intenção local sem repetir o comando, buscar ou
receber uma leitura atualizada somente por GET seguro, destacar o par
`odd/versão` alterado e remover qualquer confirmação anterior. A pessoa precisa
marcar explicitamente a confirmação da nova odd; somente depois o botão volta a
ser acionável. Se a odd mudar novamente, o ciclo reinicia. O foco vai para o
heading do alerta ou para o controle de confirmação apenas quando isso não
interromper a leitura; o foco nunca salta para um CTA invisível.

### Cálculo exibido

Para a leitura local, mostrar:

```text
retorno potencial = créditos do palpite × odd aceita
lucro líquido      = retorno potencial − créditos do palpite
```

Os valores são arredondados visualmente para duas casas e identificados como
estimativa informativa. `HALF_UP`, escala 2 e os valores efetivamente aceitos
pertencem ao snapshot do servidor e prevalecem sobre qualquer cálculo do
browser.

---

## Contrato de cópia

| Elemento | Copy obrigatória |
|---|---|
| Título do cartão | `Palpite neste mercado` |
| CTA de visitante | `Entrar para palpitar` |
| CTA de matrícula | `Registrar matrícula` |
| CTA de envio | `Enviar palpite` |
| Atalho all-in | `Usar saldo total` |
| Label da stake | `Créditos do palpite` |
| Ajuda de stake | `Mínimo 10,00 créditos · máximo igual ao saldo disponível · até duas casas decimais` |
| Retorno | `Retorno potencial (inclui os créditos do palpite)` |
| Lucro | `Lucro líquido` |
| Estado de envio | `Enviando palpite…` |
| Stale | Título: `A odd mudou antes do envio`. Corpo: `Confira a nova odd publicada e confirme novamente antes de enviar.` Controle: `Confirmo a nova odd publicada` |
| Sucesso | `Palpite registrado` e `Seu comprovante permanece disponível neste mercado.` |
| Suspenso | `Mercado suspenso para verificação. A versão pública continua disponível, mas novos palpites estão bloqueados.` |
| Encerrado | `Este mercado não aceita novos palpites. A versão registrada permanece disponível para consulta.` |
| Saldo insuficiente | `Você precisa de pelo menos 10,00 créditos disponíveis para enviar este palpite.` |
| Erro de envio | `Não foi possível enviar o palpite agora. Nenhuma alteração foi confirmada.` |
| Conflito administrativo | `Este palpite foi anulado por incompatibilidade de elegibilidade no instante do envio e os créditos foram devolvidos.`; incluir somente o código de referência permitido |

Mensagens são em português claro, acionáveis e sem culpa. Não usar “aposta
garantida”, “ganho certo”, “lucro garantido”, “dinheiro”, “saldo real” ou
qualquer equivalência monetária. Textos de loading preservam a largura dos
controles; erros ficam próximos do campo ou da ação que falhou.

---

## Responsividade e densidade

| Faixa | Contrato |
|---|---|
| 320–599px | 16 px laterais; uma coluna; bilhete em fluxo; seleção e resumo empilhados; input e CTA com largura total; labels e mensagens envolvem; nenhuma rolagem horizontal da página |
| 600–899px | 24 px laterais; mercado e bilhete podem formar duas colunas somente quando cada bloco conservar leitura; formulário até `$form-max-width`; histórico segue cards ou tabela com wrapper focável |
| ≥900px | contêiner máximo 1240 px e 32 px laterais; coluna principal de mercado + rail de bilhete 320–400 px; contexto e ação ficam próximos; tabela semântica para histórico |

Quebras são dirigidas por conteúdo. Equipe, seleção, estado, versão, deadline,
erro e ajuda não são truncados. Chips e ações podem envolver. Testar no mínimo
320×800 e 1280×800 em Chromium e Firefox; teclado não pode depender de hover,
arrasto ou gesto.

---

## Acessibilidade, teclado e movimento

- Usar `main`, headings em ordem, `fieldset/legend` para escolhas,
  `label` associado ao input, `output`/`dl` para cálculo e snapshots,
  `time[datetime]` para deadline e `role=status`/`role=alert` para estados.
- O skip link e o foco pós-rota continuam sendo os da `ArenaShell`; a entrada
  no bilhete não deve criar outro mecanismo de navegação.
- Foco de teclado usa `--bt-focus`, 3 px com offset 3 px, inclusive em rádio,
  checkbox stale, disclosure e botões. Alvos acionáveis têm no mínimo 44×44 px.
- Erro de formulário anuncia texto curto, aponta `aria-describedby` e leva foco
  ao primeiro inválido após uma tentativa explícita; não apaga o valor válido.
- Mudança de loading, stale, erro e sucesso usa live region sem mover foco
  inesperadamente. O comprovante não depende de toast que desaparece.
- Zoom 200%, alto contraste e `prefers-reduced-motion` preservam a leitura.
  Sem movimento reduzido, somente fade/transform curto de estado; com a
  preferência ativa, a troca é instantânea e nenhum loop é executado.
- A seleção não depende apenas de coral: controle nativo/estado textual e
  `aria-checked` tornam a escolha compreensível para teclado e leitor de tela.

---

## Privacidade, autorização e transporte

- O DOM, URL, storage, analytics, logs do browser e mensagens não contêm
  matrícula, HMAC, e-mail, token, identidade operacional, papel, escopo,
  capability, saldo de terceiros, seleção de terceiros, liquidez ou chave de
  idempotência.
- `PublicMarketBettingAdapter` usa apenas o cliente OpenAPI gerado. Não usar
  `HttpClient` direto, DTO manual paralelo ou `Authorization` montado no
  frontend; cookies/BFF, XSRF e headers governados ficam no transporte existente.
- Sinais de sessão/matrícula servem para orientar a composição, nunca para
  conceder crédito, aprovar elegibilidade, liberar all-in ou aceitar o comando.
  O servidor revalida tudo e pode responder 401/403/409.
- O GET de ticket retorna somente o próprio ticket. Antes do fechamento, não
  existe contagem, distribuição, ranking ou qualquer palpite de outra pessoa.
- O comprovante usa fatos aceitos pelo servidor; o cálculo local é informativo
  e não pode substituir odd, regra, versão, deadline, ledger ou snapshot
  autoritativos.
- Formulários e erros não enviam segredo para analytics, query string ou
  console. Fixtures de browser são determinísticas e não representam usuários
  reais.

---

## Mapa de aplicação por plano

| Plano | Superfície/modo | Contrato visual consumido |
|---|---|---|
| 05-01 | sem UI pública | Nenhuma tela nova; ledger e concessão permanecem no servidor |
| 05-02 | sem UI pública | Aceite atômico e conflitos são fatos do backend; não criar tela operacional |
| 05-03 | BFF + cliente Angular | Transporte governado, respostas Problem Details, sem token manual e sem campos privados |
| 05-04 | `/odds/mercados/:marketId`, Arena | P1/P2/P3, estados da tabela, stale explícito, escala/retorno e responsividade |
| 05-05 | Browser e certificação | Chromium/Firefox, 320 px/desktop, teclado, privacidade, reload do comprovante e ausência de terceiros |

---

## UI Considerations

Inventário focal da fase: **1 superfície pública ampliada** (`PublicMarketPage`)
com os elementos `form`, `list-collection`, `nav`, `interactive-control` e
`static-content`. A cobertura abaixo é explícita e deve ser levantada pelos
planos consumidores; não há estado implícito.

| Categoria | Elemento(s) | Status | Resolução / razão |
|---|---|---|---|
| empty | form, list-collection, static-content | covered | Visitante, vínculo ausente, mercado sem odd e comprovante inexistente usam as cópias e ações seguras documentadas; não há placeholder enganoso. |
| loading | form, list-collection, interactive-control | covered | Skeleton/painel preserva altura, anuncia `aria-busy` e desativa somente a ação em andamento. |
| error | form, nav, interactive-control, static-content | covered | 401/403/404/409/transporte viram estados sanitizados próximos do contexto, sem stack, token ou PII. |
| populated | form, list-collection, static-content | covered | Mercado mostra odd, saldo próprio, seleção, stake, versões, retorno informativo e, após aceite, comprovante próprio. |
| partial | form, list-collection | covered | Blocos válidos permanecem; saldo/odd/regra ausentes bloqueiam somente a dependência correspondente, sem zero fictício. |
| overflow | form, list-collection, nav, static-content | covered | Layout reflui em 320 px, textos essenciais quebram e o histórico usa cards/wrapper focável; a página não cria scroll horizontal. |
| zero-one-many | list-collection | covered | Uma ou várias seleções e zero/um/muitos registros de versão usam labels completas; ticket de terceiros nunca aparece antes do fechamento. |
| long-text | form, nav, interactive-control, static-content | covered | Nomes, mensagens, referência e deadline envolvem; nenhum estado/erro/fonte essencial é truncado. |
| stale-reconfirmation | form, interactive-control, static-content | covered | 409 limpa a confirmação anterior, mostra odd/versão atual e só reativa o envio após ação explícita; nunca há retry automático. |
| own-ticket-reload | list-collection, static-content | covered | GET após reload recompõe somente o comprovante da própria pessoa com os snapshots aceitos. |

Backstops obrigatórios:

| Backstop | Elemento(s) | Evidência |
|---|---|---|
| numeric-authority | form, static-content | Specs verificam duas casas, mínimo 10,00, all-in, retorno total incluindo stake e lucro líquido; o resultado do backend prevalece sobre o cálculo local. |
| browser-matrix | nav, form, list-collection | Playwright 05-05 em Chromium/Firefox, 320×800 e desktop, com visitante, vínculo ausente, elegível, saldo insuficiente, stale, sucesso/reload e administrador bloqueado. |
| privacy-boundary | form, list-collection, static-content | Assertions de DOM, storage e rede garantem ausência de token, matrícula, HMAC, papel, escopo, saldo/seleção de terceiros e idempotency key. |

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|---|---|---|
| shadcn official | nenhum | Não aplicável: stack Angular e `components.json` ausente |
| Terceiros | nenhum | Nenhuma dependência, CDN, asset remoto, iframe ou registry novo |
| BETUFU local | `ArenaShell`, `PublicMarketPage`, `ConfirmDialog` quando necessário, tokens, fontes e adapters/stores Signals | Reutilização de componentes e contratos existentes; qualquer novo componente é standalone local e não altera shells operacionais |

---

## Checker Sign-Off

- [x] Dimensão 1 Copywriting: PASS — cópia factual, acionável e sem promessa monetária
- [x] Dimensão 2 Visuais: PASS — bilhete é extensão da Arena e respeita hierarquia odd → contexto → ação
- [x] Dimensão 3 Cor: PASS — tokens vivos, accent reservado e estados redundantes por texto/semântica
- [x] Dimensão 4 Tipografia: PASS — Inter/Barlow locais, quatro papéis e números tabulares
- [x] Dimensão 5 Espaçamento: PASS — escala herdada, 44 px de alvo e 320 px sem overflow
- [x] Dimensão 6 Registry Safety: PASS — nenhuma biblioteca, fonte ou asset novo

**Approval:** approved 2026-09-13 por revisão do plano de controle; execução deve
preservar este contrato e parar diante de qualquer decisão visual não coberta.
