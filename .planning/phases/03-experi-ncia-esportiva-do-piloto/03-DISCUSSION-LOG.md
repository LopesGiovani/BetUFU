# Fase 3: Experiência Esportiva do Piloto — Registro da discussão

> **Somente trilha de auditoria.** Não usar como entrada de planejamento, pesquisa ou execução.
> As decisões canônicas estão em `03-CONTEXT.md`; este arquivo preserva as alternativas consideradas.

**Data:** 2026-08-26
**Fase:** 3 — Experiência Esportiva do Piloto
**Áreas discutidas:** generalização do domínio, modalidades, navegação, administração, chaveamentos, atletas, elencos, fontes, resultados e privacidade

---

## Abrangência do produto

| Opção | Descrição | Selecionada |
|---|---|---|
| Site específico da Olimpíada UFU 2026 | Estruturas e navegação fixas para uma única edição | |
| Plataforma genérica de competições | Várias competições e edições ao longo do ano; 2026 é a primeira operação real | ✓ |

**Escolha do responsável:** plataforma genérica, comparável à organização de campeonatos distintos em sites esportivos.
**Notas:** os dados de 2025 são rascunho e referência, não a edição que será operada.

---

## Modalidades e formatos

| Opção | Descrição | Selecionada |
|---|---|---|
| Suporte detalhado a todas as modalidades | Implementar regras específicas de cada esporte já no MVP | |
| Catálogo completo e operação detalhada de confronto direto | Todos aparecem; somente eventos compatíveis recebem agenda/resultados/chaveamento completo | ✓ |
| Apenas poucas modalidades cadastradas | Omitir as demais do catálogo | |

**Escolha do responsável:** catálogo amplo, com profundidade inicial em confronto direto.
**Notas:** registrar reavaliação futura para enquadrar todas as modalidades; compatibilidade é do formato do evento, não do nome do esporte.

---

## Navegação pública e favorito

| Opção | Descrição | Selecionada |
|---|---|---|
| Agenda primeiro | Hoje, próximos eventos e filtros, sempre identificando competição/edição | ✓ |
| Competição primeiro | Exigir escolha de competição antes de mostrar agenda | |
| Modalidade primeiro | Exigir escolha de esporte antes da agenda | |

**Escolha do responsável:** agenda primeiro.
**Notas:** o favorito representa a atlética/delegação inteira, agregando suas equipes de diferentes modalidades.

---

## Organizações, equipes e elenco

| Opção | Descrição | Selecionada |
|---|---|---|
| Organização permanente, equipes por edição | Atlética preserva identidade; equipes e elencos variam por edição | ✓ |
| Reutilizar equipe e elenco para sempre | Mistura históricos de anos distintos | |
| Recriar a organização por edição | Duplica identidade e seguidores | |

**Escolha do responsável:** organização permanente e equipe/elenco por edição.
**Notas:** escalação de titulares e reservas por partida ficou fora do MVP; existe somente o elenco geral publicado.

---

## Administração escopada

| Opção | Descrição | Selecionada |
|---|---|---|
| Administrador único por competição | Apenas uma pessoa opera cada competição | |
| Vários administradores no mesmo escopo | Compartilham o UUID da competição; auditoria e concorrência distinguem as ações | ✓ |

**Escolha do responsável:** vários `ADMINISTRADOR_COMPETICAO` por competição.
**Notas:** administrador geral atribui o escopo; administrador de competição não concede papéis.

---

## Chaveamentos e classificação

| Opção | Descrição | Selecionada |
|---|---|---|
| Sorteio pela BETUFU | A plataforma cria confrontos | |
| Transcrição do chaveamento oficial | Administrador registra a fonte; chave deriva das partidas | ✓ |
| Sem chaveamento | Apenas lista de jogos | |

**Escolha do responsável:** transcrição manual do chaveamento oficial.
**Notas:** grupos e pontos corridos mostram jogos/resultados, mas a classificação é transcrita do boletim no MVP; desempates automáticos ficam para o futuro.

---

## Cadastro e vínculo de atletas

| Opção | Descrição | Selecionada |
|---|---|---|
| Comprovante documental | Arquivo privado e revisão humana | |
| Matrícula transitória e HMAC | Administrador digita; backend calcula HMAC e descarta a matrícula | ✓ |
| Somente nome | Não permite propor vínculo posterior com conta | |

**Escolha do responsável:** matrícula transitória com o mesmo HMAC da conta, sem comprovante.
**Notas:** correspondência gera proposta que a pessoa aceita ou rejeita; não existe vínculo automático silencioso nem selo oficial da UFU.

---

## Importação de elenco

| Opção | Descrição | Selecionada |
|---|---|---|
| Somente cadastro individual | Mais simples, mas trabalhoso para elencos grandes | |
| Individual + CSV oficial | Modelo fixo UTF-8, validação completa e importação atômica | ✓ |
| XLSX flexível | Várias planilhas, fórmulas e colunas customizadas já no MVP | |

**Escolha do responsável:** cadastro individual e CSV oficial.
**Notas:** separar parser da regra de importação para permitir XLSX futuro sem reescrever validação, HMAC, transação ou auditoria.

---

## Perfil público do atleta

| Opção | Descrição | Selecionada |
|---|---|---|
| Perfil esportivo mínimo | Nome, foto e equipe | |
| Perfil esportivo ampliado | Foto, curso, biografia e redes opcionais, além de dados esportivos oficiais | ✓ |
| Perfil com dados pessoais administrativos | Inclui matrícula/e-mail/identificadores internos | |

**Escolha do responsável:** perfil ampliado controlado pelo próprio atleta após aceitar o vínculo.
**Notas:** estatísticas específicas por esporte ficaram para depois; o MVP mostra somente métricas genéricas confiáveis.

---

## Política de apostas para atletas

| Opção | Descrição | Selecionada |
|---|---|---|
| Bloquear eventos da própria equipe | Reduz conflito, mas exclui participantes importantes e amplia a lógica do MVP | |
| Permitir somente a favor | Ainda usa informação interna e cria regra difícil de explicar | |
| Não restringir atleta no MVP | Permite qualquer aposta e registra reavaliação futura | ✓ |

**Escolha do responsável:** atleta pode apostar inclusive na própria equipe/evento, a favor ou contra.
**Notas:** risco aceito para créditos virtuais; as relações esportivas devem continuar modeladas para política futura. Restrições administrativas permanecem.

---

## Publicação de resultados

| Opção | Descrição | Selecionada |
|---|---|---|
| Mesmo administrador pode confirmar | Ajuda de outra pessoa é opcional; auditoria e fonte continuam obrigatórias | ✓ |
| Segundo administrador obrigatório | Maior segregação de função, porém mais operação para o MVP | |
| Resultado só na Fase 6 | Deixa agenda e chaveamento incompletos | |

**Escolha do responsável:** o mesmo administrador pode registrar e confirmar, inclusive para a liquidação futura do MVP.
**Notas:** `registradoPor` e `confirmadoPor` permanecem separados; duas pessoas tornam-se evolução obrigatória antes de prêmio real, escala maior ou parceria institucional. O boletim oficial corretivo mais recente prevalece e o histórico anterior não é apagado.

---

## Locais

| Opção | Descrição | Selecionada |
|---|---|---|
| Local reutilizável + espaço opcional | Ex.: Centro Esportivo UFU → Ginásio 05 | ✓ |
| Texto livre por evento | Duplica endereço e dificulta filtro | |
| Somente local principal | Não distingue quadra, campo ou ginásio | |

**Escolha do responsável:** local reutilizável com espaço interno opcional.
**Notas:** endereço, mapa e observações também são opcionais; `LOCAL_A_DEFINIR` é estado válido.

---

## Discrição dos agentes posteriores

- detalhes técnicos de pacotes, contratos internos, paginação, códigos de erro, cache e concorrência;
- limites concretos de foto, biografia e CSV;
- microtextos e composição visual, sujeitos a um `UI-SPEC.md` antes da implementação frontend.

## Ideias adiadas

- restrição de apostas para atletas;
- confirmação financeira obrigatória por duas pessoas;
- estatísticas específicas por modalidade;
- escalações por partida;
- importação XLSX;
- classificação/desempate automáticos;
- operação completa de provas multi-competidor;
- integração automática com fontes oficiais;
- comprovantes documentais, se uma necessidade futura justificar a coleta.

---

## Convergência visual posterior — variante V

Entre 2026-08-27 e 2026-08-28, o responsável retomou a direção visual da página pública porque as primeiras propostas pareciam genéricas, pouco esportivas e sem impacto memorável. A exploração foi deliberadamente tratada como sketch descartável: nenhuma alternativa virou contrato apenas por existir.

### Caminho considerado

- foram comparadas composições editoriais, painéis esportivos, hero de transmissão, faixas inclinadas, ticker, cards, filtros e diferentes paletas;
- referências externas serviram para estudar ritmo, hierarquia e acabamento, sem copiar marca, conteúdo ou arquitetura de terceiros;
- React Bits foi usado apenas como referência do efeito visual Grainient/CRTWarp; a decisão técnica permaneceu Angular nativo;
- as variantes B, F, H, I, K, L, N, O e P foram descartadas; R foi preservada temporariamente como marco de impacto; T refinou estrutura e U refinou o fundo; V consolidou composição, paleta, filtros e hero automático;
- o responsável aprovou V como base definitiva e depois autorizou apagar as demais variantes, preservando somente o artefato final.

### Por que V venceu

V combina o melhor da composição broadside esportiva com acabamento noturno contemporâneo: tipografia condensada em escala de transmissão, coral como sinal principal, superfícies vinho/preto, agenda real como conteúdo central, ticker, faixa esportiva integrada, controles arredondados e movimento com propósito. Ela é memorável sem depender de ilustração pronta ou de uma biblioteca visual.

O hero alterna automaticamente entre duas composições do mesmo próximo evento, sem setas ou paginação competitiva. A troca pausa em hover, foco, aba oculta, fora da viewport e movimento reduzido. O fundo Grainient aparece somente na região editorial da agenda e possui fallback estático; não invade formulários nem o console.

### Efeito no contrato oficial

O novo `03-UI-SPEC.md` substitui a direção visual anterior da Fase 3, mas não altera o objetivo técnico, requisitos, regras de publicação, privacidade, autorização ou escopo do domínio. A mudança é uma convergência de identidade e componentes, registrada em D-81 a D-94. Arena pública e Console operacional continuam sendo o mesmo produto, com intensidades diferentes.

### Gate do 03-18

O 03-18 deve registrar aqui e em `03-VALIDATION.md` a rodada inicial e cada correção dos checkers. Nenhuma alternativa visual descartada pode ser reaberta por sugestão do checker; recomendações que contradigam D-81 a D-89 são classificadas como não acionáveis com justificativa explícita.

**Rodada inicial do gsd-ui-checker — 2026-08-28:** 7 achados acionáveis, sendo 5 BLOCK (`UI-01`, `UI-03`, `UI-05`, `UI-06`, `UI-07`) e 2 FLAG (`UI-02`, `UI-04`). Não houve contradição de produto. A primeira correção foi autorizada por D-90 para tornar auditáveis os estados por superfície, oferecer pausa persistente ao movimento automático, consolidar tipografia/espaçamento, garantir alvo mobile e fechar cópia/proporção de cor.

**Correção 1 do gsd-ui-checker — 2026-08-28:** a contagem caiu estritamente de 7 para 2. Restaram `UI-R1-01` BLOCK, sobre vazios literais das coleções públicas P4–P6, e `UI-R1-02` FLAG, sobre aliases antigos na comparação Arena/Console. A segunda correção foi autorizada por D-90 porque houve redução e ainda existe uma tentativa.

**Correção 2 do gsd-ui-checker — 2026-08-28:** a contagem caiu de 2 para 0. As seis dimensões passaram; não restou BLOCK/FLAG, contradição, estagnação ou limite esgotado. O Gate A fechou e o contrato visual está pronto para orientar o replanejamento técnico.

### Replanejamento técnico do 03-18

O planner fresco reescreveu `03-03` a `03-17` e manteve o baseline serial W4–W18. `03-PATTERNS.md` foi considerado desnecessário porque cada PLAN já explicita interfaces e analogias relevantes.

- `03-03` passou a entregar a listagem pública mínima real de eventos do repositório ao navegador, incluindo OpenAPI, cliente gerado, Arena V, fallback e Playwright;
- o tracer V9 sustenta somente período e competição/edição, então o plano não cria filtros cosméticos de esporte/categoria/fase;
- `03-04` acrescenta esses filtros quando V10 e o catálogo real passarem a fornecer os campos, e concentra a expansão completa O1/O2;
- `03-05`–`03-17` preservam suas responsabilidades exclusivas e migram a UI por componentes/tokens compartilhados;
- o preflight estrutural passou, mas o checker independente deverá julgar explicitamente exatidão dos `<files>` por task e decomposição de blocos grandes antes do Gate B fechar.

**Rodada inicial do gsd-plan-checker — 2026-08-28:** 9 achados acionáveis, com 6 BLOCKER e 3 WARNING. Os achados cobrem decomposição, ordem do manifesto gerado, numeração O1–O4, carrossel V, matriz final de navegador/acessibilidade, mutações negativas, key links, ownership público e foco Playwright.

O achado `PLAN-R0-01` revelou uma incompatibilidade no próprio contrato aprovado: 2–3 tasks, no máximo cinco arquivos por task e apenas quinze PLANs não comportam fatias verticais de 22–39 paths exatos. Não é seguro “resolver” usando diretórios/wildcards, omitindo arquivos gerados ou condensando artificialmente componentes. Como a allowlist da execução não autoriza editar `03-18-PLAN.md` nem criar PLANs adicionais, nenhuma tentativa foi iniciada e o Gate B foi pausado para uma decisão explícita do responsável.

### Correção 1 do Gate B — aplicada, aguardando checker fresco

O responsável autorizou a emenda documental do `03-18-PLAN.md`, cujo SHA-256 passou a `9E0192E632DCEF97E3E0634C34936D670EBF9D1CF83259B5200152584B29AE68`. A autoridade preserva exatamente quinze PLANs técnicos e o DAG serial W4–W18, mas permite 3–5 tasks e exceções acima de cinco paths somente para saída determinística ou bloco indivisível, sempre com paths enumerados, justificativa e prova focal única.

A correção 1 tratou os nove IDs da rodada inicial:

- `PLAN-R0-01`: todos os PLANs `03-03`–`03-17` possuem cinco tasks, paths exatos e zero diretórios/wildcards;
- `PLAN-R0-02`: `03-03` cria o modo candidato read-only; cada plano OpenAPI aprova inventário/digest antes de manifesto, geração governada e consumo Angular;
- `PLAN-R0-03`: O1 escopo, O2 catálogo, O3 organizações/equipes, O4 atletas/elencos, O5 CSV, O6 eventos/resultados, O7 chaveamento/classificação e O8 revisão;
- `PLAN-R0-04`: `AutoEventHero` cobre duas composições do mesmo evento, ~5,2 s, pausa composta, `inert`, `aria-hidden`, foco e movimento reduzido;
- `PLAN-R0-05`: a certificação final cobre Chromium e Firefox, zoom 200%, foco ≥3 px, alvos ≥44×44 e contraste medido;
- `PLAN-R0-06`: o plano final inclui harness de mutações com alvos explícitos, vermelho esperado, `finally` e hashes before/after;
- `PLAN-R0-07`: key links foram reconciliados com símbolos existentes ou explicitamente planejados;
- `PLAN-R0-08`: `03-05`, `03-06`, `03-08` e `03-10` não possuem páginas públicas completas; P1–P6 pertencem ao `03-11`;
- `PLAN-R0-09`: Playwright focal usa `--grep`, e a suíte completa fica somente no gate da onda.

O preflight local confirmou 15/15 frontmatters e estruturas válidas, cinco tasks por plano, cobertura exata de requisitos e DAG preservado. Isso não constitui parecer do checker: nenhuma nova contagem ou PASS foi registrada, a baseline continua 9 e o Gate B permanece aberto até uma rodada fresca independente.

### Parecer fresco após a correção 1 do Gate B

O `gsd-plan-checker` fresco reduziu estritamente os achados acionáveis de 9 para 4, todos BLOCKER e sem WARNING. A correção 2/3 foi autorizada porque houve redução e não apareceu contradição de produto. Os quatro pontos restantes foram: outputs candidatos OpenAPI fora das allowlists produtoras; três alvos temporários do harness fora da allowlist do `03-17/T4`; a âncora `isCompositionInteractive` não garantida pelo plano produtor; e ausência de comandos, timeouts e regexes literais para M1–M3.

### Correção 2 do Gate B — aplicada, aguardando checker fresco

- `03-03`–`03-15` enumeram `inventory.json`, `digest.sha256`, `governed-before.sha256` e `governed-after.sha256` no frontmatter e na task produtora como saídas determinísticas;
- `03-03/T5` exige `AutoEventHeroComponent.isCompositionInteractive(index: number): boolean` e o teste discriminante `[M1] keeps inactive composition inert and outside tab order`;
- `03-16/T1` exige `OperationalCapabilitiesIT.scopeA_doesNotAuthorizeScopeB` como prova nomeada de isolamento A/B;
- `03-17/T4` inclui os três alvos temporários na allowlist e fixa para M1–M3 comando literal, timeout e regex específica, rejeitando falha genérica de build, infraestrutura ou timeout.

Esta seção registra somente a aplicação da correção 2. A contagem oficial permanece 4 e o Gate B continua aberto até um novo parecer independente; nenhum PASS é inferido localmente.

### Parecer fresco após a correção 2 e autorização excepcional

O `gsd-plan-checker` fresco encontrou 5 BLOCKER e 1 WARNING, totalizando 6 acionáveis: persistência indevida da árvore TypeScript candidata; reconciliação atleta–conta incompleta nos dois sentidos; P4–P8 sem wiring executável de deep links; matriz Wave 0 de CSV incompleta; `dependency:tree` citado sem comando verificável; e páginas públicas Angular sem o padrão `generated service → adapter → signal store → componente`. Como a contagem subiu de 4 para 6, D-91 proibiu a correção automática e o responsável recebeu a escalada antes de qualquer nova edição.

Em 2026-08-28, o responsável respondeu literalmente `aprovado: correção 3 ampliada dos 6 achados do Gate B`. A autorização preservou o caráter estritamente documental do 03-18 e limitou a edição aos PLANs `03-03`, `03-07`, `03-09`, `03-11`, `03-12`, `03-13`, `03-15` e aos registros do Gate B. Código, dependências, testes materiais, stage, commit, integração, cleanup e push permaneceram proibidos.

### Correção 3 ampliada do Gate B — aplicada, aguardando checker fresco

- `PLAN-R2-01`: `03-03/T2` agora gera a árvore TypeScript em scratch de sistema criado por `mkdtemp`, calcula inventário/digest, remove o scratch em `finally` e persiste no checkout somente os quatro metadados enumerados; o teste rejeita qualquer `.ts` residual;
- `PLAN-R2-02`: `03-07/T2` planeja `EnrollmentVerified(identityId)` e `SportsAthleteCreated(athleteId)` pós-commit, uma porta consumer-owned, `AthleteLinkReconciler` idempotente nos dois sentidos e testes discriminantes para `DIVERGENT_DATA`, `MULTIPLE_CANDIDATES` e `ALREADY_LINKED`;
- `PLAN-R2-03`: `03-11/T5`, `03-12/T5` e `03-13/T5` incluem `app.routes.ts` e specs de rota, com URLs diretas explícitas para P4–P8 e refresh/deep-link sem guard autenticado;
- `PLAN-R2-04`: `03-09/T2` enumera fixtures para válido, erro tardio, BOM, encoding inválido, quoting/newline, duplicata, referência ausente, limites exatos/excedidos e fórmula; o IT de erro na última linha exige zero escrita;
- `PLAN-R2-05`: `03-09/T2` e `03-15/T2` possuem comandos literais `dependency:tree -Dverbose` com assertions de versões, BOM, ausência de snapshots e clientes HTTP inesperados; o gate da onda repete os mesmos comandos;
- `PLAN-R2-06`: `03-11` cria adapter/store público compartilhado por P2–P6; `03-12` e `03-13` criam adapters/stores públicos e operacionais separados, e os componentes deixam de consumir serviços gerados diretamente.

O preflight posterior confirmou 15/15 frontmatters válidos, 15/15 estruturas válidas, igualdade entre `files_modified` e a união dos `<files>` de todas as tasks e `git diff --check` verde. Isso registra somente a aplicação da correção excepcional: a contagem oficial continua 6 e nenhum PASS é inferido antes de um `gsd-plan-checker` novo.

### Parecer fresco após a correção 3 ampliada — limite esgotado

O checker independente reduziu a contagem de 6 para 3, com 2 BLOCKER e 1 WARNING. A redução confirma que os seis achados da rodada anterior foram materialmente tratados; porém, como a tentativa 3/3 foi consumida, D-91 exige nova escalada humana e impede fechar o Gate B ou iniciar o Gate C.

Os três achados remanescentes são:

1. `03-07` registra o estado `CONTESTADA`, mas não planeja ponta a ponta o comando owner-only de contestação, rota/contrato HTTP, autorização, idempotência, IT owner versus terceiro e cenário de UI exigidos por SPRT-08;
2. `03-07`, `03-08`, `03-09`, `03-10`, `03-14` e `03-15` ainda não materializam integralmente `generated service → adapter → signal store → componente`; faltam stores/specs, separação owner/público em `03-14`/`03-15` e limpeza de estado privado em 401/403;
3. `03-15` promete a matriz adversarial de mídia, mas não associa deterministamente falso MIME, truncamento, metadados e falha de storage a fixtures enumeradas ou derivações/injeções nomeadas no IT.

O snapshot permaneceu intacto durante a revisão: branch `worktree-agent-03-18`, HEAD `12d8f0126acf866685a1ac164b6d848d420f4424`, stage vazio, hashes dos PLANs e variante V idênticos. O `03-18` continua incompleto até nova autorização específica do responsável.

### Correção 4 excepcional do Gate B — aplicada, aguardando checker fresco

Depois da explicação pedagógica do motivo das três lacunas, o responsável autorizou literalmente `aprovado: correção 4 excepcional dos 3 achados, checker fresco e conclusão documental do 03-18 se o Gate B zerar` e confirmou `pode continuar`. A autorização não muda produto ou arquitetura: ela torna executáveis contratos que os PLANs já prometiam e preserva as proibições de código, dependências, testes materiais e Git mutável.

- `03-07` agora descreve a contestação owner-only ponta a ponta: endpoint `PUT /api/v1/me/sports/athlete-links/{proposalId}/decision`, decisão `CONTEST`, estado `CONTESTADA`, revisão O8 única, replay idempotente, terceiro bloqueado, corrida com um terminal e controle/spec owner explícitos;
- `03-07`, `03-08`, `03-09`, `03-10`, `03-14` e `03-15` completam `generated service -> adapter -> Signal store -> component`, sem Observable HTTP no template e sem retry em mutações;
- stores owner, operacional e público permanecem independentes. Em 401/403, cada store privado elimina o conteúdo que não pode sobreviver à perda de autorização: proposta/fila, rascunho/preview, arquivo/token, formulário/versão ou File/Blob/object URL/progresso;
- `03-15` associa cada caso adversarial a fixture, derivação ou fake nomeado, inclusive metadados removidos após reencode e falha de storage que preserva a foto anterior;
- `03-18` enumera `03-18-SUMMARY.md` como quarto arquivo da Task 3, evitando que o próprio output obrigatório fique fora da allowlist.

O preflight documental passou em 15/15 frontmatters, 15/15 estruturas, allowlists exatas de `03-03` a `03-18` e `git diff --check` sem erro. Isso ainda não constitui PASS: a contagem oficial permanece 3 e o Gate B aguarda um `gsd-plan-checker` independente.

### Parecer fresco após a correção 4 — um bloqueio operacional novo

O checker confirmou que as três lacunas autorizadas foram resolvidas e aprovou todas as demais dimensões funcionais, arquiteturais e documentais. A contagem caiu de 3 para 1, mas o Gate B não zerou porque `03-17/T4` usa `&&` para encadear dois comandos no `<automated>`.

Esse não é um problema da lógica de teste: é incompatibilidade de shell. A BETUFU executa os planos em Windows PowerShell `5.1.26100.9168`, e essa versão rejeita `&&` no parser antes de iniciar o primeiro comando. A correção mínima é usar `powershell -NoProfile -Command`, executar o teste Node, interromper se `$LASTEXITCODE` for diferente de zero e só então executar o runner de mutações. Como o achado surgiu fora dos três itens da Correção 4, ele foi registrado e escalado sem edição do `03-17`.

### Correção 5 focal — aplicada, aguardando checker fresco

O responsável aprovou explicitamente a correção do comando e a conclusão documental condicionada a Gate B zerado. `03-17/T4` agora usa uma sequência compatível com PowerShell 5.1 e preserva o contrato fail-fast: o runner principal não é iniciado se o teste Node falhar, e o exit code original é propagado. A forma do comando foi verificada com processos neutros nos caminhos de sucesso (`0`) e falha (`7`), sem executar testes materiais da BETUFU. Nenhum outro PLAN técnico foi alterado nesta correção focal.

### Parecer fresco após a correção 5 — três bloqueios adicionais

O checker aprovou o comando corrigido do `03-17`, mas encontrou dois comandos antigos com o mesmo tipo de risco em outra forma: `03-09/T2` e `03-15/T2` envolvem scripts PowerShell contendo `$tree`, `$text`, `$expected`, `$versions` e `$LASTEXITCODE` em aspas duplas externas. O shell orquestrador consome essas variáveis antes de criar o processo filho; uma reprodução neutra confirmou exit code `1`. A correção precisa preservar os cifrões para o processo filho, preferencialmente com aspas simples externas.

O terceiro bloqueio é de rastreabilidade documental, não de produto: embora D-82, D-90 e D-93 estejam declaradas no contrato, o gate reconheceu 39/42 porque essas decisões não são citadas em actions/tasks executáveis. A correção mínima é ligar D-82 às actions de Arena/Console em `03-03`/`03-04`, D-90 à execução dos checkers no `03-18` e D-93 à task que verifica os quatro hashes históricos. Como os três achados estão fora da autorização anterior, foram apenas registrados; Gate C continua fechado.

### Correção 6 focal — aplicada, aguardando checker fresco

Com autorização específica do responsável, os scripts Maven aninhados de `03-09` e `03-15` passaram a usar `powershell -NoProfile -Command { ... }`, preservando variáveis e strings no scriptblock. Uma primeira forma com string entre aspas simples foi rejeitada pelo próprio preflight antes do checker porque o transporte nativo removeu aspas internas; o scriptblock substituto passou na prova neutra do PowerShell 5.1. A rastreabilidade também foi fechada sem alterar decisões: D-82 está nas actions que constroem Arena e Console; D-90 está nas duas actions de checker do `03-18`; D-93 está na action de certificação dos hashes históricos. Nenhum outro comportamento técnico ou requisito foi ampliado.

### Parecer final do Gate B e fechamento documental

O `gsd-plan-checker` fresco retornou `VERDICT: PASS`, `actionable_count: 0` e aprovou o Gate B. O parecer confirmou 15/15 PLANs, 75/75 tasks, cobertura exata dos 17 requisitos `AUTH-01` e `SPRT-01` a `SPRT-16`, 42/42 decisões D-53 a D-94, DAG W3-W18, allowlists, fluxo OpenAPI governado, Nyquist, threat models, key links e compatibilidade dos três comandos PowerShell 5.1.

Com o Gate A já encerrado em `7 -> 2 -> 0`, o Gate B fechou em `9 -> 4 -> 6 -> 3 -> 1 -> 3 -> 0`. O Gate C materializou o manifesto 15/15, a auditoria multifuente, os hashes protegidos e a reconciliação de escopo. O 03-18 está concluído somente como plano documental: nenhum requisito de produto foi marcado como entregue, nenhum teste material foi executado e nenhuma operação Git mutável ocorreu. O próximo bloco elegível é o briefing interativo do 03-03 na Wave 4.

---

## Reabertura autorizada — convergência do frontend legado

Depois do fechamento inicial, o responsável perguntou em que momento o frontend anterior seria refatorado. A inspeção das rotas reais mostrou uma lacuna do manifesto 15/15: a nova identidade cobria Arena, páginas esportivas e o futuro workspace O1-O8, mas não atribuía de forma explícita a convergência completa das seis superfícies já existentes de conta e operação.

O responsável autorizou reabrir documentalmente o `03-18` para corrigir essa lacuna sem substituir o planejamento esportivo. A lógica de negócio permanece fora de alcance: APIs, stores, adapters, guards, permissões, capabilities, validações e regras do backend continuam iguais. O trabalho novo é exclusivamente de apresentação, composição de shell, navegação, responsividade, acessibilidade e aposentadoria segura do layout antigo.

A decomposição escolhida evita misturar contextos com densidades diferentes:

- `03-19/W18`: convergência do Console pessoal para `/conta/perfil/completar`, `/conta/perfil` e `/conta/matricula`, reutilizando `ConsoleShell` como variante do mesmo modo Console;
- `03-20/W19`: convergência das telas `/operacao/painel`, `/operacao/papeis` e `/operacao/matriculas/corrigir`, seguida da retirada controlada de `AuthenticatedLayoutComponent` somente após o corte das rotas e testes equivalentes;
- `03-17/W20`: certificação final ampliada para as 26 superfícies, depois das duas migrações.

`tracer` e `diagnostic` continuam ferramentas development-only e não recebem maquiagem de produto. Não serão criados dados fictícios, contratos HTTP, dependências visuais ou um terceiro tema. Qualquer necessidade funcional descoberta durante a execução deve ser escalada como bloqueio, não absorvida pela refatoração visual.

Esta reabertura invalida apenas o alcance final dos pareceres anteriores: o contrato base V e os quinze planos já aprovados continuam preservados, mas o UI-SPEC ampliado e o DAG de dezessete planos técnicos exigem `gsd-ui-checker` e `gsd-plan-checker` frescos antes de um novo fechamento documental.

### Retomada da certificação — 2026-08-28

O responsável autorizou corrigir documentalmente o 03-18 na worktree, sem stage, commit, push ou alteração da `master`. A inconsistência da Task 3 foi corrigida: ela lista cinco arquivos editáveis e agora declara a mesma contagem.

O `gsd-ui-checker` fresco da reabertura retornou `PASS`, com `actionable_count: 0` (`BLOCK=0`, `FLAG=0`). Os dois bloqueios de copywriting foram resolvidos no `03-UI-SPEC.md`: ações secundárias operacionais receberam rótulos contextuais e as subcoleções de histórico/equipes/modalidades/resultados receberam estados vazio/um/muitos explícitos.

Três tentativas do `gsd-plan-checker` fresco falharam antes da análise por indisponibilidade do provedor: duas respostas HTTP `429 Too Many Requests` e uma resposta `404 No active credentials for provider: openai`. Isso não é um parecer nem um PASS; B2 permanece pendente. As provas mecânicas locais confirmaram 18/18 frontmatters, 18/18 estruturas, 85/85 tasks técnicas, cobertura exata de requisitos e ausência de overlap entre waves.

Naquele ponto, o 03-18 continuava incompleto: A2 estava PASS, B2 aguardava checker disponível e C2 aguardava a retirada do sketch V do índice staged e a reconciliação Git autorizada. Nenhum requisito de produto foi marcado como concluído e nenhum teste material foi executado.

Uma verificação adicional encontrou e corrigiu uma divergência de allowlist: `03-PATTERNS.md` era mencionado como arquivo eventual apesar de ter sido considerado desnecessário e não existir no candidato. Ele foi removido da allowlist e da Task 2; `03-18-PLAN.md` agora reconcilia exatamente 25 arquivos com a união dos `<files>`.

### Reconciliação Git autorizada e nova indisponibilidade do checker

O responsável autorizou retirar o sketch do stage e atualizar o índice com as correções documentais, sem commit ou push. A operação preservou `.planning/sketches/004-identity-lab/index.html` no disco como arquivo não rastreado e deixou exatamente os 25 paths documentais da allowlist no stage. A reconciliação confirmou `25/25`, nenhum path fora de `.planning/`, hashes protegidos intactos e `git diff --cached --check` limpo.

Uma quarta tentativa do `gsd-plan-checker`, agora sobre o stage reconciliado, também falhou antes da análise com `404 No active credentials for provider: openai`. Isso não altera o conteúdo nem produz parecer. O pré-requisito de índice do C2 está satisfeito, mas B2 continua pendente e, pela ordem sequencial do plano, C2 não pode ser promovido a PASS até existir um checker de planos fresco.

### Fechamento fresco B2/C2 — 2026-08-28

Com a autorização para corrigir os três bloqueadores PowerShell, a contagem `3/20` do ROADMAP e a rastreabilidade de D-82, D-90 e D-93, foi executado um `gsd-plan-checker` fresco no snapshot congelado. O retorno foi `VERIFICATION PASSED`, com `actionable_count: 0` (0 BLOCKER, 0 WARNING). O checker validou os 17 PLANs técnicos (`03-03`–`03-17`, `03-19`, `03-20`), 85 tasks, requisitos exatos AUTH-01 + SPRT-01–16, DAG W4–W20, allowlists, fluxo OpenAPI governado, Nyquist, threat models e comandos PowerShell 5.1.

A checagem final permaneceu em `worktree-agent-03-18` @ `12d8f0126acf866685a1ac164b6d848d420f4424`, com 25 paths documentais staged, nenhum tracked unstaged, sketch V fora do índice e hashes protegidos idênticos. A leitura UTF-8 também eliminou a suspeita de mojibake no predicado de `26 superfícies`.

O Gate B2 foi fechado como PASS e, em sequência, o Gate C2 foi fechado como PASS: ROADMAP/STATE foram reconciliados para 20 planos e `03-03/W4` como próximo plano interativo, e a allowlist staged continua exclusivamente documental. Nenhum requisito de produto foi marcado como concluído; não houve commit, push, integração, cleanup, código, dependência, banco, infraestrutura, Docker ou teste material.
