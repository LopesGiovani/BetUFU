---
phase: 3
slug: experiencia-esportiva-do-piloto
status: in_progress
nyquist_compliant: true
wave_0_complete: false
created: 2026-08-26
---

# Phase 3 — Validation Strategy

> Contrato de validacao da experiencia esportiva, reconciliado com 20 PLANs: os planos tecnicos 03-02 a 03-06 foram executados, o preplano documental 03-18 foi fechado na Wave 3 e os planos seguintes permanecem pendentes nas Waves 8-20.

O 03-18 usa `requirements: []`: ele governa a reconstrução do contrato visual, a cobertura e a validação dos planos, mas sua execução não conclui AUTH-01 nem qualquer SPRT. A reabertura amplia o manifesto para 17/17 e exige checkers frescos sobre 26 superfícies e o DAG W4–W20; os pareceres históricos permanecem evidência, não aprovação automática do novo alcance.

---

## Execução do 03-18 — snapshot e ledger

### Snapshot anterior à primeira edição

Registrado em `2026-08-28 10:12:24 -03:00`, na branch/worktree `worktree-agent-03-18`, HEAD `12d8f0126acf866685a1ac164b6d848d420f4424`.

| Artefato protegido | SHA-256 exigido | Resultado |
|---|---|---|
| Variante V — `.planning/sketches/004-identity-lab/index.html` | `6C422384EE8DA0A91ACDA9397A9415413A0E3FF36F0EEF82A00F8D923F556F98` | ✅ idêntico |
| `03-01-PLAN.md` | `AE34808145B2DE59D77A1E4D1149D965DAC99EE727160D217514BE47A49A47EE` | ✅ idêntico |
| `03-01-SUMMARY.md` | `C44689E7C00C630BB65FC482C54D17B46962FB7E91D4257D8828768300C7CD98` | ✅ idêntico |
| `03-02-PLAN.md` | `A2CEA4ADA5B79C07E241A07E61D04C3811C4B42C38A97C5A368BB75B1D208B0B` | ✅ idêntico |
| `03-02-SUMMARY.md` | `E62E78137A7D2702DB37332ADD189E43C5C1AB8103E502C09C9252753CCA83D8` | ✅ idêntico |

O checkout inicial da nova worktree aplicou CRLF em dois artefatos apesar de o conteúdo normalizado e o blob Git serem idênticos. Os bytes canônicos do checkout principal foram transportados novamente antes da primeira edição autorizada; os cinco hashes passaram e os quatro artefatos concluídos permaneceram sem diff de conteúdo.

### Ledger dos checkers

| Gate | Checker | Rodada | Tentativa | Contagem anterior | Contagem atual | Achados/resolução | Estado |
|---|---|---:|---:|---:|---:|---|---|
| A — contrato visual | `gsd-ui-checker` fresco | inicial | — | — | 7 | `UI-01`, `UI-03`, `UI-05`, `UI-06`, `UI-07` BLOCK; `UI-02`, `UI-04` FLAG. Correções: matriz por superfície, pausa persistente, quatro níveis tipográficos, grid estrito, track mobile ≥44px, `Limpar filtros` e 60/30/10. | 🔧 correction 1 |
| A — contrato visual | `gsd-ui-checker` fresco | correção 1 | 1/2 | 7 | 2 | `UI-R1-01` BLOCK: vazios P4–P6 incompletos; `UI-R1-02` FLAG: aliases tipográficos sem mapeamento. Redução estrita confirmada; aplicar correção 2. | 🔧 correction 2 |
| A — contrato visual | `gsd-ui-checker` fresco | correção 2 | 2/2 | 2 | 0 | P4–P6 possuem vazios literais e pluralização; Arena/Console usam somente `display`, `heading`, `body`, `label`. Seis dimensões PASS. | ✅ passed |
| B — PLANs 03-03–03-17 | `gsd-plan-checker` fresco | inicial | — | — | 9 | 6 BLOCKER: `PLAN-R0-01` decomposição matematicamente incompatível com o limite do 03-18; `PLAN-R0-02` deadlock do manifesto gerado; `PLAN-R0-03` numeração O1–O4; `PLAN-R0-04` carrossel V omitido; `PLAN-R0-05` matriz browser/a11y; `PLAN-R0-06` mutações finais. 3 WARNING: `PLAN-R0-07` key links; `PLAN-R0-08` ownership público; `PLAN-R0-09` Playwright focal. | ⛔ needs authority |
| B — PLANs 03-03–03-17 | correção do planner; checker ainda não executado | correção 1 aplicada | 1/3 | 9 | — | Os nove IDs foram tratados dentro da autoridade emendada. A baseline continua 9 e nenhuma redução, PASS ou parecer é declarada antes de uma rodada fresca do `gsd-plan-checker`. | ⏳ fresh checker pending |
| B — PLANs 03-03–03-17 | `gsd-plan-checker` fresco | correção 1 | 1/3 | 9 | 4 | 4 BLOCKER, 0 WARNING: `PLAN-R1-01` outputs candidatos fora das allowlists; `PLAN-R1-02` alvos temporários fora da allowlist do harness; `PLAN-R1-03` âncora `isCompositionInteractive` não garantida; `PLAN-R1-04` comandos/timeouts/regexes de M1–M3 não literais. Redução estrita confirmada. | 🔧 correction 2 |
| B — PLANs 03-03–03-17 | correção do planner; checker ainda não executado | correção 2 aplicada | 2/3 | 4 | — | Os quatro IDs foram tratados. A baseline vigente continua 4 e nenhuma nova redução, PASS ou parecer é declarada antes de outra rodada fresca do `gsd-plan-checker`. | ⏳ fresh checker pending |
| B — PLANs 03-03–03-17 | `gsd-plan-checker` fresco | correção 2 | 2/3 | 4 | 6 | 5 BLOCKER + 1 WARNING: `PLAN-R2-01` árvore candidata persistente; `PLAN-R2-02` reconciliação incompleta; `PLAN-R2-03` deep links P4–P8; `PLAN-R2-04` fixtures CSV; `PLAN-R2-05` dependency tree; `PLAN-R2-06` adapter/store. Aumento acionou D-91 e escalada humana. | ⛔ escalated |
| B — PLANs 03-03–03-17 | correção excepcional aprovada; checker ainda não executado | correção 3 ampliada aplicada | 3/3 | 6 | — | Os seis IDs foram tratados dentro da autorização literal do responsável. A baseline vigente continua 6; nenhum PASS é declarado antes de um checker fresco. | ⏳ fresh checker pending |
| B — PLANs 03-03–03-17 | `gsd-plan-checker` fresco | correção 3 ampliada | 3/3 | 6 | 3 | 2 BLOCKER: contestação SPRT-08 sem caminho executável; seis famílias Angular sem cadeia adapter/store completa. 1 WARNING: matriz adversarial de mídia sem mapeamento determinístico. Houve redução, mas o limite 3/3 foi esgotado. | ⛔ escalated |
| B — PLANs 03-03–03-17 | correção 4 excepcional aprovada; checker ainda não executado | correção 4 aplicada | 4/4 excepcional | 3 | — | `PLAN-R3-01` a `PLAN-R3-03` foram tratados dentro da autorização literal do responsável: contestação owner-only ponta a ponta, stores Signal completos/separados com cleanup 401/403 e matriz de mídia determinística. A baseline vigente continua 3; nenhum PASS é declarado antes de checker fresco. | ⏳ fresh checker pending |
| B — PLANs 03-03–03-17 | `gsd-plan-checker` fresco | correção 4 excepcional | 4/4 excepcional | 3 | 1 | 1 BLOCKER novo: `03-17/T4` usa `&&` no `<automated>`, sintaxe rejeitada pelo Windows PowerShell 5.1. As três lacunas R3 foram aprovadas, e todas as demais dimensões passaram. Gate B continua aberto e exige nova autoridade. | ⛔ escalated |
| B — PLANs 03-03–03-17 | ajuste PowerShell aprovado; checker ainda não executado | correção 5 focal aplicada | 5/5 excepcional | 1 | — | `03-17/T4` usa `powershell -NoProfile -Command`, checa `$LASTEXITCODE` após o teste Node e só então executa o runner; sucesso e falha foram validados em PowerShell 5.1. A baseline continua 1 até checker fresco. | ⏳ fresh checker pending |
| B — PLANs 03-03–03-17 | `gsd-plan-checker` fresco | correção 5 focal | 5/5 excepcional | 1 | 3 | 3 BLOCKER: aspas duplas consomem variáveis PowerShell em `03-09/T2` e `03-15/T2`; decision coverage reconhece somente 39/42 porque D-82, D-90 e D-93 não aparecem em actions/tasks rastreáveis. O ajuste 03-17 passou. Gate B continua aberto. | ⛔ escalated |
| B — PLANs 03-03–03-17 | três bloqueadores aprovados; checker ainda não executado | correção 6 focal aplicada | 6/6 excepcional | 3 | — | `03-09/T2` e `03-15/T2` preservam variáveis/strings com `powershell -Command { ... }`; D-82 está nas actions Arena/Console e D-90/D-93 nas tasks correspondentes do 03-18. Baseline continua 3 até checker fresco. | ⏳ fresh checker pending |
| B — PLANs 03-03–03-17 | `gsd-plan-checker` fresco | correção 6 focal | 6/6 excepcional | 3 | 0 | PASS: 15/15 PLANs, 75/75 tasks, 17/17 requisitos, 42/42 decisões, DAG, allowlists, OpenAPI, Nyquist, threats, key links e comandos PowerShell 5.1 aprovados. | ✅ passed |

Regra de contagem: Gate A usa `BLOCK + FLAG` acionáveis; Gate B usa `BLOCKER + WARNING` acionáveis. Uma correção só pode prosseguir quando a nova contagem for estritamente menor.

### Emenda de autoridade para a correção 1 do Gate B

O responsável autorizou em 2026-08-28 a emenda estritamente documental do contrato `03-18`, preservando os quinze PLANs `03-03`–`03-17`, as Waves 4–18 e a proibição de stage/commit/push.

| Medição | SHA-256 de `03-18-PLAN.md` | Interpretação |
|---|---|---|
| Antes da emenda | `B9B73FD269FAD9EBBD9EE539D1E92E3D0D1BF10B8CAE525D02DD1192878AD415` | Contrato que limitava cada PLAN a 2–3 tasks e tratava cinco paths como teto matemático. |
| Depois da emenda autorizada | `9E0192E632DCEF97E3E0634C34936D670EBF9D1CF83259B5200152584B29AE68` | Contrato válido pelo schema e pela estrutura, agora incluído na própria allowlist. |

A regra vigente permite 3–5 tasks por PLAN, mantém paths exatos, trata cinco paths por task como alvo e aceita exceção somente para saída determinística gerada ou bloco coeso enumerado e justificado. Nenhum PLAN adicional pode ser criado. Para o cliente OpenAPI, a ordem obrigatória é candidato read-only → aprovação humana de inventário/digest → fixação do manifesto, geração determinística e consumo posterior. A baseline do checker continua em 9 até a correção 1 ser avaliada por um novo parecer.

### Preflight local dos PLANs antes do checker

| Controle | Resultado |
|---|---|
| Frontmatter | ✅ 15/15 válidos pelo schema `plan` |
| Estrutura | ✅ 15/15 válidas por `verify.plan-structure` |
| Tasks | ✅ 5 por plano, dentro da faixa autorizada 3–5; todas possuem ao menos um `<automated>` planejado |
| Requirements | ✅ união exata `AUTH-01` + `SPRT-01` a `SPRT-16` |
| DAG seguro | ✅ `03-18/W3 → 03-03/W4 → ... → 03-17/W18`; uma unidade por wave |
| Responsabilidade 03-03 | ✅ tracer público real backend/OpenAPI/cliente/Angular/Playwright; sem expansão completa O1 |
| Responsabilidade 03-04 | ✅ expansão completa O1/O2, catálogo/local e filtros adicionais sustentados por V10 |
| `03-PATTERNS.md` | `NÃO NECESSÁRIO`; analogias e interfaces estão descritas nos próprios PLANs |
| Paths por task | ✅ zero diretórios agregados e zero wildcards; todos os paths são exatos. Blocos acima de cinco enumeram cada path, justificam indivisibilidade ou saída determinística e usam uma prova focal única. |
| Cliente OpenAPI | ✅ 13/13 planos consumidores seguem candidato read-only → aprovação do inventário/digest → manifesto/geração governada → consumo Angular posterior. |
| Playwright focal | ✅ todos os `<verify>` focais usam `--grep`; a suíte completa aparece somente no gate da onda. |

### Correção 1 aplicada ao Gate B

| Achado | Correção aplicada |
|---|---|
| `PLAN-R0-01` | `03-03`–`03-17` agora possuem cinco tasks; diretórios/wildcards foram eliminados e exceções acima de cinco paths estão enumeradas, justificadas e ligadas a uma única prova focal. |
| `PLAN-R0-02` | O `03-03` cria `--candidate <dir>` read-only com inventário/digest e prova before/after; `03-03`–`03-15` aprovam o candidato antes de fixar manifesto, gerar árvore governada ou consumir tipos. |
| `PLAN-R0-03` | Numeração canônica fixada: O1 escopo, O2 catálogo, O3 organizações/equipes, O4 atletas/elencos, O5 importação tabular CSV/XLSX, O6 eventos/resultados, O7 chaveamento/classificação e O8 revisão. |
| `PLAN-R0-04` | `03-03` especifica `AutoEventHero` com duas composições do mesmo evento, ciclo ~5,2 s, todas as causas de pausa, slide inativo `inert`/`aria-hidden`/fora do foco, reduced motion, unitários e Playwright focal. |
| `PLAN-R0-05` | `03-17` exige Chromium + Firefox, zoom 200%, foco medido ≥3 px, alvos ≥44×44 e contraste WCAG calculado. |
| `PLAN-R0-06` | `03-17` cria harness executável com três alvos, vermelho/assinatura esperados, timeout, restauração em `finally` e SHA-256 antes/depois. |
| `PLAN-R0-07` | `key_links` agora usam classes/serviços/componentes existentes ou explicitamente criados pelo próprio plano. |
| `PLAN-R0-08` | `03-05`, `03-06`, `03-08` e `03-10` fornecem domínio/contratos/componentes; páginas públicas completas P1–P6 pertencem somente ao `03-11`. |
| `PLAN-R0-09` | Playwright focal usa `--grep` em `03-03`, `03-11` e `03-17`; execuções completas foram movidas para `<wave_verification_standard>`. |

Nenhum checker fresco foi executado nesta seção. Gate B, Task 3 e a contagem baseline 9 permanecem abertos até o próximo parecer independente.

### Parecer fresco após a correção 1 do Gate B

O checker independente retornou 4 BLOCKER e 0 WARNING, reduzindo estritamente a contagem de 9 para 4. A redução autorizou a correção 2/3. O snapshot protegido permaneceu em HEAD `12d8f0126acf866685a1ac164b6d848d420f4424`, `03-18-PLAN.md` SHA-256 `9E0192E632DCEF97E3E0634C34936D670EBF9D1CF83259B5200152584B29AE68` e variante V SHA-256 `6C422384EE8DA0A91ACDA9397A9415413A0E3FF36F0EEF82A00F8D923F556F98`.

### Correção 2 aplicada ao Gate B

| Achado | Correção aplicada |
|---|---|
| `PLAN-R1-01` | `03-03`–`03-15` agora enumeram `inventory.json`, `digest.sha256`, `governed-before.sha256` e `governed-after.sha256` no frontmatter e na task produtora; cada bloco os justifica como saída determinística indivisível. |
| `PLAN-R1-02` | `03-17` inclui `auto-event-hero.component.ts`, `OperationalCapabilitiesService.java` e `backend/openapi/betufu.yaml` no frontmatter e em `T4`, mesmo sendo mutações temporárias restauradas por hash. |
| `PLAN-R1-03` | `03-03/T5` exige `AutoEventHeroComponent.isCompositionInteractive(index: number): boolean` e o teste `[M1] keeps inactive composition inert and outside tab order`. |
| `PLAN-R1-04` | `03-16/T1` fixa `scopeA_doesNotAuthorizeScopeB`; `03-17/T4` fixa os comandos focais de Angular, Maven e gerador, timeouts de 120000/180000/60000 ms e regexes discriminantes para M1–M3. |

Nenhum checker fresco foi executado após esta correção. A baseline oficial permanece 4, Gate B e Task 3 continuam abertos e nenhum PASS é inferido do preflight local.

### Parecer fresco após a correção 2 e escalada D-91

O checker independente retornou 5 BLOCKER e 1 WARNING, elevando a contagem de 4 para 6. A ausência de redução estrita bloqueou a Correção 3 automática conforme D-91. O snapshot permaneceu em HEAD `12d8f0126acf866685a1ac164b6d848d420f4424`, `03-18-PLAN.md` SHA-256 `9E0192E632DCEF97E3E0634C34936D670EBF9D1CF83259B5200152584B29AE68` e variante V SHA-256 `6C422384EE8DA0A91ACDA9397A9415413A0E3FF36F0EEF82A00F8D923F556F98`.

O responsável autorizou explicitamente uma terceira correção ampliada para os seis achados, sem ampliar o escopo material além do planejamento documental e sem autorizar Git mutável.

### Correção 3 ampliada aplicada ao Gate B

| Achado | Correção aplicada |
|---|---|
| `PLAN-R2-01` | `03-03/T2` usa scratch temporário descartável para a árvore TypeScript e persiste somente `inventory.json`, `digest.sha256`, `governed-before.sha256` e `governed-after.sha256`; o teste exige zero `.ts` residual. |
| `PLAN-R2-02` | `03-07/T2` materializa sinais pós-commit opacos nos dois sentidos, porta consumer-owned, reconciliador idempotente e casos discriminantes para divergência, múltiplos candidatos e já vinculado. |
| `PLAN-R2-03` | `03-11` conecta P4–P6 e `03-12`/`03-13` conectam P7/P8 em `app.routes.ts`, com specs de URL direta, refresh e ausência de guard autenticado. |
| `PLAN-R2-04` | `03-09/T2` enumera a matriz Wave 0 completa de CSV e prova erro somente na última linha com zero escrita. |
| `PLAN-R2-05` | `03-09/T2` e `03-15/T2` possuem comandos literais `dependency:tree -Dverbose` e assertions de versões, BOM, snapshots e módulos HTTP inesperados; os gates das ondas os repetem. |
| `PLAN-R2-06` | `03-11`–`03-13` seguem `generated service → adapter → signal store → componente`, com stores público/operacional separados quando há duas superfícies. |

O preflight após a edição passou em 15/15 frontmatters, 15/15 estruturas, 15/15 allowlists reconciliadas entre frontmatter e tasks e `git diff --check`. A baseline oficial permanece 6 até o próximo parecer independente; Gate B e Task 3 continuam abertos e nenhum PASS é inferido localmente.

### Parecer fresco após a correção 3 ampliada

O checker independente retornou 2 BLOCKER e 1 WARNING, reduzindo a contagem de 6 para 3. As dimensões de estrutura, allowlist, DAG, OpenAPI, estimativas, comandos Maven adicionados, rotas P4–P8 e matriz CSV passaram. Permaneceram somente:

| Achado | Lacuna restante | Estado |
|---|---|---|
| `PLAN-R3-01` | SPRT-08: `03-07` precisa de comando/endpoint owner-only de contestação, autorização/idempotência, IT e controle/spec de UI explícitos. | BLOCKER |
| `PLAN-R3-02` | `03-07`, `03-08`, `03-09`, `03-10`, `03-14` e `03-15` precisam completar a cadeia adapter/store, separar estado público/privado onde aplicável e limpar estado sensível em 401/403. | BLOCKER |
| `PLAN-R3-03` | `03-15` precisa mapear cada caso adversarial a fixture enumerada ou derivação/injeção determinística e assertion nomeada. | WARNING |

O limite de correções chegou a 3/3. Mesmo com redução estrita, D-91 impede nova correção automática: Gate B e Task 3 permanecem abertos, Gate C não iniciou e o 03-18 continua incompleto até autorização humana específica. O snapshot final da revisão manteve HEAD `12d8f0126acf866685a1ac164b6d848d420f4424`, stage vazio, `03-18-PLAN.md` SHA-256 `9E0192E632DCEF97E3E0634C34936D670EBF9D1CF83259B5200152584B29AE68` e variante V SHA-256 `6C422384EE8DA0A91ACDA9397A9415413A0E3FF36F0EEF82A00F8D923F556F98`.

### Correção 4 excepcional aplicada ao Gate B

O responsável autorizou literalmente `aprovado: correção 4 excepcional dos 3 achados, checker fresco e conclusão documental do 03-18 se o Gate B zerar`. A autorização preserva o escopo exclusivamente documental, não instala dependências, não executa testes materiais e não autoriza stage, commit, integração, cleanup ou push.

| Achado | Correção aplicada |
|---|---|
| `PLAN-R3-01` | `03-07` fixa `PUT /api/v1/me/sports/athlete-links/{proposalId}/decision` com `CONTEST`, subject owner, replay idempotente, revisão O8 única, corrida terminal e ITs nomeados para owner, terceiro e repetição; a UI owner oferece contestação por `AthleteLinkProposalStore`. |
| `PLAN-R3-02` | `03-07`, `03-08`, `03-09`, `03-10`, `03-14` e `03-15` agora enumeram e testam `generated service -> adapter -> Signal store -> component`. Estados owner/operacional/público são separados quando coexistem; 401/403 limpa proposta/fila, rascunho, arquivo/preview/token, formulários/versões e File/Blob/object URL conforme a superfície. Mutações não usam retry e Observables HTTP não chegam aos templates. |
| `PLAN-R3-03` | `03-15` mapeia deterministicamente: sucesso em `valid-avatar.png`; MIME falso com bytes PNG declarados JPEG; truncamento por `Arrays.copyOf`; excesso de pixels em `oversized-avatar.png`; metadados em `metadata-avatar.png` com marcador removido; polyglot em `polyglot-avatar.png`; falha de storage por fake injetado preservando a foto anterior. |

O contrato `03-18` também passou a enumerar o SUMMARY obrigatório no frontmatter e na Task 3, totalizando quatro arquivos de certificação e no máximo 24 paths documentais. O preflight após a correção passou em 15/15 frontmatters, 15/15 estruturas, igualdade exata entre `files_modified` e a união dos `<files>` em `03-03`–`03-18`, faixa de tasks válida e `git diff --check` sem erro; os avisos exibidos foram somente a política CRLF do checkout. Os cinco hashes protegidos permanecem idênticos e o stage continua vazio. A baseline oficial permanece 3 até o próximo parecer independente; Gate B, Gate C e o 03-18 continuam abertos.

### Parecer fresco após a correção 4 excepcional

O checker independente confirmou que `PLAN-R3-01`, `PLAN-R3-02` e `PLAN-R3-03` foram resolvidos: contestação SPRT-08 executável, seis cadeias Angular completas com stores separados/cleanup 401/403 e matriz adversarial de mídia determinística. Também aprovou 15/15 frontmatters/estruturas, 75/75 tasks com verificação automatizada, cobertura de AUTH-01 e SPRT-01–16, DAG, allowlists, 13/13 fluxos OpenAPI, Nyquist, threat models e integridade do diff.

Permaneceu um BLOCKER novo em `03-17/T4`: o `<automated>` encadeia dois comandos com `&&`. O ambiente real é Windows PowerShell `5.1.26100.9168`, cujo parser rejeita a expressão antes de executar qualquer teste: `O token '&&' não é um separador de instruções válido nesta versão.` A correção mínima proposta pelo checker é envolver a sequência em `powershell -NoProfile -Command`, executar o primeiro comando, verificar `$LASTEXITCODE` e somente então executar o segundo. Como esse achado não fazia parte dos três itens autorizados, Gate B permanece aberto em `actionable_count = 1`, Gate C não inicia e o 03-18 exige nova aprovação específica.

### Correção 5 focal — compatibilidade do comando com PowerShell 5.1

O responsável autorizou literalmente `aprovado: corrigir comando PowerShell do 03-17, executar checker fresco e concluir documentalmente o 03-18 se zerar`. O `<automated>` de `03-17/T4` passou a usar `powershell -NoProfile -Command`, executar primeiro `node --test scripts/validate-phase3-negative-mutations.test.mjs`, interromper com o mesmo exit code quando `$LASTEXITCODE` for diferente de zero e somente então executar `node scripts/validate-phase3-negative-mutations.mjs`.

A semântica de controle foi provada sem executar as suítes do projeto: no Windows PowerShell `5.1.26100.9168`, um comando neutro no caminho de sucesso retornou `0` e um primeiro processo neutro encerrado com `7` fez a sequência retornar `7`, sem alcançar semanticamente a continuação. Isso confirma parsing e propagação do exit code; não constitui teste material do runner de mutações. A contagem oficial permanece 1 até um checker fresco e Gate C continua fechado.

### Parecer fresco após a correção 5 focal

O checker aprovou a correção de `03-17/T4`: a sequência possui sintaxe válida no PowerShell 5.1, retorna `0` no caminho feliz e propaga a falha do primeiro comando. Também preservou como aprovados os requisitos, o DAG, as allowlists, 15/15 PLANs, 75/75 verificações e as três correções R3.

Foram encontrados três BLOCKER novos. Em `03-09/T2` e `03-15/T2`, o argumento de `powershell -NoProfile -Command` usa aspas duplas externas; por isso o PowerShell orquestrador expande `$tree`, `$text`, `$expected`, `$versions` e `$LASTEXITCODE` antes de iniciar o processo filho. Uma reprodução neutra transformou `$tree = 'x'; $text = $tree; Write-Output $text` em `= 'x'; = ; Write-Output` e retornou exit code `1`, confirmando que os dois comandos planejados não são executáveis como escritos. A correção mínima é usar aspas simples externas ou escapar cada `$`, preservando o conteúdo para o processo filho.

O terceiro BLOCKER é a cobertura formal de decisões em 39/42. `D-82`, `D-90` e `D-93` existem na seção `<preapproved_decisions>`, mas o gate de rastreabilidade exige referência em superfícies executáveis: D-82 nas actions dos planos que materializam Arena/Console, e D-90/D-93 nas tasks de checker/proteção do próprio `03-18`. Nenhum dos três itens foi corrigido nesta rodada. Gate B permanece aberto em `actionable_count = 3`; Gate C, SUMMARY, ROADMAP e STATE finais não foram iniciados.

### Correção 6 focal — scripts aninhados e decision coverage

O responsável autorizou literalmente `aprovado: corrigir os 3 bloqueadores, executar checker fresco e concluir documentalmente o 03-18 se zerar`. Em `03-09/T2` e `03-15/T2`, os scripts agora são passados como scriptblock por `powershell -NoProfile -Command { ... }`. A primeira tentativa com string entre aspas simples foi descartada antes do checker porque uma prova neutra revelou perda das aspas internas no transporte nativo do Windows. O scriptblock foi então comprovado no PowerShell 5.1 e preserva `$tree`, `$text`, `$expected`, `$versions`, `$_`, `$LASTEXITCODE` e as strings sem expansão antecipada.

Para a cobertura formal, D-82 passou a aparecer nas actions que materializam `Arena pública` em `03-03/T5` e `Console operacional` em `03-04/T5`. D-90 passou a aparecer nas actions de execução dos checkers em `03-18/T1` e `03-18/T2`; D-93 aparece na action de proteção por hashes em `03-18/T3`. Nenhuma decisão foi alterada: apenas suas ligações executáveis ficaram explícitas. A contagem oficial permanece 3 até checker fresco e Gate C continua fechado.

### Parecer final do Gate B

O `gsd-plan-checker` fresco retornou `VERDICT: PASS`, `actionable_count: 0` e `Gate B PASS`. O snapshot revisado permaneceu em HEAD `12d8f0126acf866685a1ac164b6d848d420f4424`, branch `worktree-agent-03-18` e stage vazio. Foram aprovados:

- 15/15 PLANs e 75/75 tasks estruturados, todos com `<files>`, `<action>`, `<verify>` e `<done>`;
- cobertura exata dos 17 requisitos `AUTH-01` e `SPRT-01` a `SPRT-16`;
- 42/42 decisões aplicáveis D-53 a D-94 rastreadas em superfícies executáveis;
- DAG serial W3–W18, sem ciclo, dependência ausente ou overlap de mesma wave;
- allowlists exatas, fluxo OpenAPI candidato → aprovação → geração → consumo, Nyquist, threat models e key links;
- contestação SPRT-08, stores Signals/cleanup 401/403, matriz adversarial de mídia e os três comandos PowerShell corrigidos.

O limite da evidência permanece explícito: o checker foi somente leitura e não executou Maven, Angular, Docker, rede ou testes materiais.

---

## Gate C — manifesto final histórico, cobertura e integridade

### Manifesto 15/15 dos planos técnicos

| Plano | Responsabilidade final | Requirements governados | Allowlist | Dependência | Wave | Superfície UI | Evidência automatizada planejada | Threat refs | Checker |
|---|---|---|---:|---|---:|---|---|---|---|
| 03-03 | Tracer público vertical com listagem real mínima, contrato e navegador. | AUTH-01, SPRT-12 | 55 paths | 03-18 | 4 | Arena P1 / home V | SportsTracerIT, candidato/digest, generate-api e Playwright `@03-03-home-v` | T-03-03-PUBLIC, CONTRACT, MOTION, FOCUS | PASS |
| 03-04 | Expansão operacional do workspace/catálogo O1–O2 e locais reutilizáveis. | SPRT-01, SPRT-10, SPRT-12 | 43 paths | 03-03 | 5 | Console O1–O2 + filtros P1 | Catálogo/capability/Flyway IT, contrato e specs O1/O2/agenda | T-03-04-IDOR, RACE, BOOT, CONTRACT | PASS |
| 03-05 | Organizacoes, equipes por edicao e reconciliacao preservadora do favorito. | SPRT-02, SPRT-13, SPRT-15 | 52 paths | 03-04 | 6 | Console O3 / perfil | Organization/Profile/Flyway IT, contrato, specs O3/perfil e browser Arena/Console | T-03-05-IDOR, MIG, CONTRACT | PASS |
| 03-06 | Atleta global e seam de matrícula transitória/HMAC. | SPRT-02–05 | 32 paths | 03-05 | 7 | Console O4 | Athlete/HMAC/Flyway IT, Privacy/Modulith, spec O4, build e Playwright esportivo | T-03-06-PII, IDOR, MODULE, CONTRACT | PASS |
| 03-07 | Propostas, aceite/rejeição/contestação owner-only e revisão segura O8. | SPRT-06–08 | 40 paths | 03-06 | 8 | Conta owner + Console O8 | Proposal/concurrency/Flyway IT, contrato e stores/specs owner/O8 | T-03-07-TAKEOVER, RACE, REPLAY, CONTEST, ENUM, CONTRACT | PASS |
| 03-08 | Elencos versionados, snapshot publicado e correção manual. | SPRT-02, SPRT-09 | 29 paths | 03-07 | 9 | Console O4 / RosterView | Roster/concurrency/Flyway IT, contrato e specs store/O4 | T-03-08-PUBLISH, IDOR, PII, CONTRACT | PASS |
| 03-09 | Importação tabular CSV/XLSX atômica, validada e sem retry perigoso. | SPRT-02 | 56 paths | 03-08 | 10 | Console O5 | dependency trees das duas bibliotecas, parsers/import IT, contrato e specs store/O5 | T-03-09-SC, DOS, FORMULA, PARTIAL | PASS (complete-pending-git) |
| 03-10 | Lifecycle de eventos/resultados, fontes, versões e concorrência. | SPRT-10, SPRT-11 | 32 paths | 03-09 | 11 | Console O6 | Event/result/concurrency/Flyway IT, contrato e specs store/O6 | T-03-10-RACE, SOURCE, SCOPE, CONTRACT | PASS |
| 03-11 | Leituras e páginas públicas completas P1–P6 além do tracer mínimo. | AUTH-01, SPRT-12, SPRT-13 | 38 paths | 03-10 | 12 | Arena P1–P6 | PublicViewIT, contrato, specs de detalhes e Playwright P1–P6 | T-03-11-DRAFT, PII, CACHE, CONTRACT | PASS |
| 03-12 | Chaveamento P7/O7 derivado dos eventos oficiais. | SPRT-14 | 33 paths | 03-11 | 13 | Arena P7 + Console O7 | Bracket/Flyway IT, contrato e stores/specs P7/O7 | T-03-12-CYCLE, SCOPE, DIVERGE, CONTRACT | PASS |
| 03-13 | Classificação manual P8/O7, oficial, versionada e acessível. | SPRT-14 | 33 paths | 03-12 | 14 | Arena P8 + Console O7 | Standings/Flyway IT, contrato e stores/specs P8/O7 | T-03-13-FABRICATE, SCOPE, A11Y, CONTRACT | PASS |
| 03-14 | Perfil textual/social controlado pelo atleta e projeção pública segura. | SPRT-08, SPRT-13, SPRT-16 | 32 paths | 03-13 | 15 | Conta owner + Arena P6 | Profile/Flyway IT, contrato e stores/specs owner/P6 | T-03-14-XSS, IDOR, PII, CONTRACT | PASS |
| 03-15 | Mídia processada, storage privado e fallback público preservador. | SPRT-16 | 40 paths | 03-14 | 16 | Conta owner + Arena P6 | AWS tree, Media IT adversarial, contrato e specs de foto/stores | T-03-15-MEDIA, STORAGE, RACE, SC | PASS |
| 03-16 | Capabilities, autorização escopada e navegação operacional O1–O8. | AUTH-01, SPRT-01, SPRT-02, SPRT-08, SPRT-12, SPRT-16 | 22 paths | 03-15 | 17 | Console O1–O8 | Capabilities/roles IT, painel, build e spec O1–O8 | T-03-16-IDOR, ROLE, STALE | PASS |
| 03-17 | Certificação final de arquitetura, privacidade, E2E e mutações. | AUTH-01, SPRT-01–16 | 10 paths | 03-16 | 18 | Arena + Console | Architecture/privacy, browsers, mutações e suítes agregadas | T-03-17-PRIV, A11Y, MUT, SC | PASS |

### Execucao do 03-05/W6

O plano 03-05 foi executado na worktree `worktree-agent-03-05` sobre o HEAD
`72d8854`. O candidato OpenAPI foi aprovado com o digest
`12B2982C70B98D02BD6609BA95AAE350E2BF65999E57F5D16183B65DABBA7201`; os
hashes governados before/after permaneceram iguais em
`21C94593F724A3478B866C4F0F4402B0FACA4EE92FD265A9B9CDEDD6BE5E5F37`.

| Controle | Resultado |
|---|---|
| Backend completo | PASS - Maven `350/350`, `BUILD SUCCESS`; V11, organizacao, perfil e concorrencia incluidos. |
| Node completo | PASS - `625/625`, incluindo mutacoes negativas do guard de rotas/imports. |
| Contrato OpenAPI | PASS - `generate-api --check` OK. |
| Angular completo | PARCIAL - `248/253`; cinco falhas apenas em `role-admin-page.component.spec.ts`, fora da allowlist do plano. |
| Builds | PASS - development e production; warnings de budget SCSS permanecem informativos. |
| Playwright | PASS - Arena `2/2`; Console operacional `1/1` apos fixture V11. |
| Integridade | PASS - `git diff --check`; avisos LF -> CRLF do checkout Windows. |

As cinco falhas Angular sao uma lacuna historica da tela de administracao de
papeis e nao foram mascaradas. A fixture E2E do painel operacional foi
atualizada somente para incluir `favoriteOrganizationId: null`, conforme
autorizacao explicita do responsavel.

### Execucao do 03-06/W7

O plano 03-06 foi executado na worktree `worktree-agent-03-06` sobre o HEAD
`9f664be`. O candidato OpenAPI foi aprovado com o digest
`111FEA082473D8D6541BAD51A44D5D00DABB160316EF7625A564CDAA55101818`; os
hashes governados before/after permaneceram iguais nesse mesmo digest.

| Controle | Resultado |
|---|---|
| Backend completo | PASS - Maven `251/251` Surefire + `359/359` Failsafe = `610/610`, sem failures/errors/skips. |
| Node completo | PASS - `625/625`, incluindo mutacoes negativas dos guards. |
| Contrato OpenAPI | PASS - candidato regenerado e `generate-api --check` OK. |
| Angular focal | PASS - spec O4 `11/11`; cliente contratual incluido no conjunto. |
| Angular completo | PARCIAL - `261/266`; cinco falhas apenas em `role-admin-page.component.spec.ts`, baseline reproduzido na master limpa. |
| Builds | PASS - production; warnings de budget SCSS permanecem informativos. |
| Playwright | PASS - esportivo `2/2` em desktop e 320 px. |
| Integridade | PASS - `git diff --check`; aviso LF -> CRLF do checkout Windows. |

O4 limpa a matricula imediatamente no submit, sucesso, erro e cancelamento;
V12 persiste somente fingerprint HMAC versionado. A pagina publica P6 e o
vinculo atleta-conta continuam atribuidos aos planos 03-07, 03-08 e 03-11.

### Execucao do 03-07/W8

O plano 03-07 foi executado na worktree `worktree-agent-03-07` sobre o HEAD
`11fd3b8`. O candidato OpenAPI foi aprovado com o digest
`B75187EFECB25E0D4809F6ED5D0354D329D9175DB9ACE3BCEB1BBA4ECDB7A8B2`; os
hashes governados before/after permaneceram iguais em
`DB17FF4C4B1058BED0836C51F1922922D621E78CC3DFA35C001C682ADED5DC24`.

| Controle | Resultado |
|---|---|
| Backend completo | PASS - Maven `251/251` Surefire + `374/374` Failsafe = `625/625`, sem failures/errors/skips. |
| Node completo | PASS - `625/625`, incluindo contratos, guards e mutacoes negativas. |
| Contrato OpenAPI | PASS - candidato aprovado, hashes before/after iguais e `generate-api --check` OK. |
| Angular focal | PASS - stores/componentes owner/O8 `10/10`. |
| Angular completo | PARCIAL - `272/277`; cinco falhas apenas em `role-admin-page.component.spec.ts`, lacuna historica fora da allowlist. |
| Builds | PASS - production; warnings de budget SCSS permanecem informativos. |
| Playwright | PASS - esportivo `2/2` em desktop e 320 px. |
| Integridade | PASS - `git diff --check`; aviso LF -> CRLF do checkout Windows. |

As propostas usam V13/V14, FKs, indices parciais, optimistic locking, estados
terminais e revisao O8 sem enumeracao de matricula/HMAC/e-mail. Os eventos
`EnrollmentVerified(identityId)` e `SportsAthleteCreated(athleteId)` sao
publicados depois do commit e a reconciliacao e idempotente nos dois sentidos.

O snapshot identificou uma colisao de versao para a proxima wave: `03-07`
usa `V14__harden_athlete_link_proposals.sql`, enquanto `03-08-PLAN.md`
declarava `V14__create_sports_rosters.sql`. A colisao foi resolvida antes da
execucao: as migrations futuras foram deslocadas para V15-V20, sem renomear
V13/V14 ja publicadas.

### Auditoria multifuente

| Fonte | Universo | Resultado | Evidência |
|---|---:|---|---|
| GOAL do ROADMAP | 1 objetivo de fase | COVERED | Tracer/leituras públicas em 03-03/03-11; operação escopada em 03-04–03-17. |
| Requirements | 17 IDs | COVERED 17/17 | União exata `AUTH-01` + `SPRT-01` a `SPRT-16`; o 03-18 não conclui nenhum deles. |
| Decisões atuais | D-53 a D-94 | COVERED 42/42 | Checker final confirmou referências executáveis, inclusive D-82, D-90 e D-93. |
| Decisões herdadas | D-01 a D-52 | PRESERVED | Permanecem nos contextos predecessores; nenhuma foi reaberta ou reescrita pelo 03-18. |
| RESEARCH / SOURCE AUDIT / COVERAGE | recomendações aplicáveis | COVERED | OpenAPI governado, Signals, dados reais, supply chain, CSV, mídia, browser/a11y e mutações estão nos PLANs. |
| Itens adiados/outras fases | odds, créditos, liquidação, chat e extensões listadas | EXCLUDED VALIDLY | Permanecem explicitamente fora da Fase 3; nenhuma exclusão foi inferida por custo. |

### Resultado do Gate C

| Controle | Resultado |
|---|---|
| Frontmatter e estrutura | 16/16 PLANs válidos (`03-03`–`03-18`); 75 tasks técnicas + 3 tasks documentais estruturadas. |
| DAG | `03-02/W2 → 03-18/W3 → 03-03/W4 → … → 03-17/W18`; sem ciclo e uma unidade por wave. |
| Allowlists | União dos `<files>` idêntica a `files_modified` em 16/16 PLANs; `03-PATTERNS.md` permanece NÃO NECESSÁRIO. |
| Responsabilidades | Listagem mínima somente em 03-03; expansão O1 somente em 03-04; P1–P6 completos somente em 03-11. |
| OpenAPI | 13/13 consumidores seguem candidato read-only → aprovação literal → geração governada → consumo. |
| Nyquist e segurança | 75/75 tasks técnicas possuem `<automated>`; fixtures, comandos, threats e mutações estão atribuídos antes do uso. |
| UI | Variante V autenticada; Arena/Console usam a identidade única, Angular nativo, fallback e movimento seguro. |
| Integridade protegida | Variante V e quatro artefatos de 03-01/03-02 mantêm os cinco SHA-256 exigidos. |
| Escopo material | Diff restrito à documentação autorizada; nenhum código, dependência, banco ou infraestrutura foi alterado. Sketch V permanece untracked, pertencente ao responsável e byte-idêntico. |
| Git e execução | HEAD/base `12d8f012...`; stage vazio; nenhum commit, integração, cleanup ou push. Nenhum teste material, Docker ou serviço executado. |

**Gate A:** PASS (`7 → 2 → 0`)
**Gate B:** PASS (`9 → 4 → 6 → 3 → 1 → 3 → 0`, com escaladas e autorizações registradas)
**Gate C:** PASS — manifesto, cobertura, DAG, allowlists, hashes e escopo documental certificados.

---

## Reabertura — convergência das superfícies legadas

O responsável autorizou reabrir documentalmente o 03-18 depois de identificar que o manifesto anterior não atribuía de forma explícita a migração das seis telas já existentes. Esta emenda preserva o Gate A/B/C histórico como evidência do contrato base, mas invalida seu uso como certificação suficiente para o alcance ampliado.

### Escopo e invariantes

| Controle | Contrato da reabertura |
|---|---|
| Comportamento | APIs, contratos HTTP, adapters, Signal stores, guards, validações, permissões, capabilities e regras de negócio permanecem semanticamente iguais (D-95). |
| Conta | `03-19/W18` converge `/conta/perfil/completar`, `/conta/perfil` e `/conta/matricula` para o Console pessoal. |
| Operação | `03-20/W19` converge `/operacao/painel`, `/operacao/papeis` e `/operacao/matriculas/corrigir`, depois retira o shell antigo. |
| Certificação | `03-17/W20` depende de `03-20` e valida 26 superfícies. |
| Desenvolvimento | `tracer`/`diagnostic` continuam exclusivos de development; production mantém ambos ausentes. |
| Dependências/dados | Nenhuma dependência visual, API, fixture cosmética ou dado fictício novo é autorizado. |

### Manifesto ampliado 17/17

As quinze linhas aprovadas anteriormente permanecem válidas, com a atualização de que `03-16` cria o shell/base O1-O8 sem absorver as rotas legadas e `03-17` passa para W20. Acrescentam-se:

| Plano | Responsabilidade final | Requirements governados | Allowlist | Dependência | Wave | Superfície UI | Evidência automatizada planejada | Threat refs | Checker |
|---|---|---|---:|---|---:|---|---|---|---|
| 03-19 | Variante pessoal do Console e convergência das três rotas `/conta/...`, sem alteração funcional. | SPRT-04, SPRT-15, SPRT-16 | 23 paths | 03-16 | 18 | Superfícies 21–23 | route boundary vivo/mutações, specs shell/perfil/matrícula e Playwright `@03-19-personal-console` | T-03-19-LOGIC, PII, ROUTE, A11Y | PENDING FRESH CHECKER |
| 03-20 | Convergência das três rotas `/operacao/...`, corte final e aposentadoria do AuthenticatedLayoutComponent. | SPRT-01, SPRT-02, SPRT-08 | 29 paths | 03-19 | 19 | Superfícies 24–26 | specs das páginas/shell, route boundary vivo/mutações, build e Playwright `@03-20-legacy-operations` | T-03-20-AUTH, LOGIC, STALE, RETIRE | PENDING FRESH CHECKER |

### DAG ampliado

`03-02/W2 → 03-18/W3 → 03-03/W4 → 03-04/W5 → 03-05/W6 → 03-06/W7 → 03-07/W8 → 03-08/W9 → 03-09/W10 → 03-10/W11 → 03-11/W12 → 03-12/W13 → 03-13/W14 → 03-14/W15 → 03-15/W16 → 03-16/W17 → 03-19/W18 → 03-20/W19 → 03-17/W20`

### Gates frescos da reabertura

| Gate | Universo | Estado |
|---|---|---|
| A2 — UI | UI-SPEC com 26 superfícies, Console pessoal/operacional e aposentadoria do shell legado | ✅ PASS — `gsd-ui-checker` fresco, 0 BLOCK/FLAG |
| B2 — PLANs | 17 PLANs técnicos, 85 tasks, DAG W4–W20, allowlists e D-53–D-100 | ✅ PASS — `gsd-plan-checker` fresco, 0 BLOCKER/WARNING acionável |
| C2 — integridade | ROADMAP 20 planos, STATE 83 totais, hashes protegidos, diff/allowlist e ausência de código | ✅ PASS — auditoria documental após B2, 25/25 paths staged |

Nenhum requisito de produto foi concluído por esta reabertura e nenhum teste material foi executado. O próximo plano técnico permanece `03-03/W4`; `03-19` e `03-20` apenas garantem que o frontend anterior também seguirá a identidade V antes da certificação final.

### Parecer fresco da reabertura — Gate A2

O `gsd-ui-checker` fresco retornou `VERDICT: PASS`, `actionable_count: 0` (`BLOCK=0`, `FLAG=0`) para as 26 superfícies. Os dois bloqueios de copywriting encontrados na primeira rodada foram resolvidos no contrato: CTAs secundários das superfícies operacionais receberam rótulos contextuais e as subcoleções de histórico de fontes, equipes, modalidades e resultados passaram a declarar estados vazio/um/muitos. As seis dimensões visuais foram aprovadas. Nenhum arquivo, índice ou estado Git foi alterado pelo checker.

### Tentativas iniciais do Gate B2 (histórico)

As quatro primeiras tentativas de iniciar o `gsd-plan-checker` fresco falharam antes da análise: duas retornaram HTTP `429 Too Many Requests` e duas retornaram `404 No active credentials for provider: openai`, incluindo a tentativa sobre o stage reconciliado. Esses resultados permanecem apenas como histórico de indisponibilidade; a execução posterior registrada abaixo é o parecer normativo do B2.

### Parecer fresco final da reabertura — Gate B2 e fechamento C2

Após a correção dos três bloqueadores PowerShell, da contagem `3/20` do ROADMAP e da rastreabilidade de D-82, D-90 e D-93, o `gsd-plan-checker` foi executado novamente sobre o snapshot congelado. O parecer retornou `VERIFICATION PASSED` com `actionable_count: 0` (0 BLOCKER e 0 WARNING).

O checker confirmou 17/17 PLANs técnicos (`03-03`–`03-17`, `03-19` e `03-20`), 85/85 tasks, cobertura exata de AUTH-01 e SPRT-01–SPRT-16, decisões rastreáveis, DAG W4–W20 sem ciclos, allowlists sem divergência, fluxo OpenAPI candidato → aprovação → geração → consumo, Nyquist, threat models e os comandos PowerShell 5.1 corrigidos. A leitura UTF-8 confirmou que o predicado `26 superfícies|26 superficies` casa o texto real sem mojibake.

O snapshot final do checker permaneceu em `worktree-agent-03-18` @ `12d8f0126acf866685a1ac164b6d848d420f4424`, com `03-18-PLAN.md` SHA-256 `6B08847DDA22C6D3325E1826CC3C5A765BE64650E8BF3ADCD97660CD515B26F4`, 25 paths documentais staged, nenhum tracked unstaged e o sketch V preservado fora do índice. Os hashes protegidos e o hash da variante V continuam idênticos aos valores exigidos.

Com B2 aprovado, a auditoria sequencial C2 foi fechada: ROADMAP registra 20 planos e a ordem W3–W20; STATE aponta `03-03/W4` como próximo plano interativo; a allowlist coincide com os 25 paths staged; nenhum código, dependência, banco, infraestrutura, sketch ou artefato concluído de 03-01/03-02 foi alterado. B2 e C2 estão PASS; o 03-18 está concluído exclusivamente como plano documental. Stage permanece sem commit/push.

### Reconciliação inicial do índice para o Gate C2 (histórico)

Com autorização Git específica, o sketch V foi retirado somente do índice e preservado no disco como arquivo não rastreado. O stage passou a conter exatamente os 25 paths da allowlist do `03-18-PLAN.md`, todos sob `.planning/`, com `0` faltantes, `0` extras, `0` paths técnicos e `git diff --cached --check` limpo. Os quatro hashes protegidos de `03-01`/`03-02` e o SHA-256 da variante V permaneceram idênticos. Essa evidência era o pré-requisito de integridade do índice; o fechamento normativo do C2 está registrado no parecer final abaixo, após B2.

### Escalada do Gate B após a rodada inicial

O checker confirmou que `03-18-PLAN.md` exige simultaneamente: somente quinze PLANs técnicos (`03-03`–`03-17`), 2–3 tasks por PLAN, paths exatos e split quando uma task toca mais de cinco arquivos. Isso limita cada PLAN a no máximo 15 arquivos, mas as fatias verticais obrigatórias precisam modificar de 22 a 39 paths exatos por plano, incluindo saídas determinísticas do cliente gerado. O conflito não pode ser resolvido dentro da allowlist da Task 2: alterar o próprio `03-18-PLAN.md` ou criar PLANs adicionais exige nova autoridade do responsável.

Até esta escalada, nenhuma tentativa de correção havia sido consumida. A autoridade estrutural foi concedida depois e está registrada na seção anterior; a contagem baseline permanece 9, Gate B e Task 3 continuam abertos e a correção 1 deve resolver os nove achados antes do próximo parecer fresco.

---

## Execução do 03-03 — Wave 4

### Contrato e candidato governado

O plano preservou a ordem aprovada: OpenAPI real → candidato read-only → aprovação humana do digest → fixação do manifesto → geração governada → consumo Angular. O candidato aprovado foi `0B631F67A415BEE7BC95ECF209BD240C922A93ED47B69385B969B093D464AB95`; os hashes `governed-before` e `governed-after` permaneceram idênticos em `BDF0CA8BDBC4118DA74FA0E5B0FFF47D4C0FF636DC9309751F550161F8EE4E71`. O diretório candidato contém somente os quatro metadados autorizados e nenhum TypeScript residual.

### Gate material final

| Prova | Resultado final |
|---|---|
| Backend focal | `SportsTracerIT` verde para a agenda pública paginada, filtros, ordenação e DTO allowlist. |
| Backend completo | Surefire `251/251`, Failsafe `343/343`, `BUILD SUCCESS` em 7m38s. |
| Node completo | `623/623`. |
| Gerador OpenAPI | `72/72`; `node frontend/scripts/generate-api.mjs --check` verde. |
| Angular focal | `13/13` specs dos componentes/stores corrigidos. |
| Angular completo | `241/241`. |
| Build produtivo | Verde; sem aumento de orçamento em `frontend/angular.json`. |
| Playwright | `2/2`, cobrindo desktop e viewport de 320 px. |
| Auditoria fresca final | `PASS`, P0=0, P1=0, P2=0. |

Os avisos de reconexão Redis/Hikari e de encerramento tardio da JVM ocorreram durante o teardown dos Testcontainers, depois de todas as classes passarem; o Maven terminou com exit code `0`. Os bundles SCSS da agenda e do hero ficaram próximos do teto de 8 kB, mas abaixo do bloqueio. Permanecem como limitações não bloqueantes a ausência de nova rodada com leitor de tela, zoom 200%, segundo navegador e suspensão prolongada da aba.

### Correções finais verificadas

- A agenda reage à passagem do tempo sem nova chamada à API: eventos vencidos saem de `upcomingEvents`, `nextEvent` e ticker na próxima fronteira temporal.
- Agenda composta somente por eventos passados não exibe “Na sequência” nem mantém evento expirado como próximo.
- O slide direto do hero apresenta horário e fuso dentro da composição ativa.
- Deep links, resposta fora de ordem, âncora, filtros, máquina de pausa compartilhada, mobile de 320 px, nomes longos e navegação foram reconciliados antes do parecer final.

### Limite dos requisitos

A evidência do `03-03` está verde no escopo do plano. `AUTH-01` e `SPRT-12` permanecem `pending` no mapa global porque ainda dependem das expansões e certificações dos planos `03-11` e `03-17`; o fechamento local da Wave 4 não antecipa a conclusão da Fase 3.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend unit/architecture** | JUnit 5, AssertJ, Spring Modulith e ArchUnit via Maven Surefire |
| **Backend integration** | Spring Boot Test, MockMvc, Spring Security Test e PostgreSQL Testcontainers via Maven Failsafe |
| **Frontend unit** | Angular test builder + Vitest (`frontend/src/**/*.spec.ts`) |
| **Contract/static gates** | Node test runner, gerador OpenAPI determinístico e validadores do repositório |
| **Browser** | Playwright; a Fase 3 precisa de config/spec executável própria |
| **Quick backend command** | `node scripts/maven.mjs -f backend/pom.xml verify -Dtest=<ClassName>` ou `... -Dit.test=<ClassNameIT>` |
| **Quick frontend command** | `npm --prefix frontend run test -- --watch=false --include <spec>` |
| **Full backend command** | `node scripts/maven.mjs -f backend/pom.xml verify` |
| **Full static command** | `node --test scripts/*.test.mjs` |
| **Full frontend command** | `npm --prefix frontend run test -- --watch=false` |
| **Browser command** | `npm --prefix frontend exec playwright test -- --config playwright.sports.config.ts` |
| **Estimated runtime** | medir no primeiro preflight de execução; não estimar sem evidência |

---

## Sampling Rate

- **Depois de cada task:** executar o teste focal literal do `<verify>` e sua mutação discriminante quando prevista.
- **Depois de cada plano:** repetir todos os focais do plano, arquitetura afetada, allowlist e gates Node relacionados.
- **Depois de cada onda:** executar backend `verify`, Node completo, Angular completo quando houver frontend e Playwright da superfície entregue.
- **Antes da revisão Codex:** congelar HEAD, status, hashes e contagens dos relatórios; qualquer correção invalida o snapshot.
- **Antes de `$gsd-verify-work`:** todas as suítes materiais precisam estar verdes.
- **Latência máxima desejada:** focal rápido por task; qualquer focal que dependa de toda a suíte deve ser dividido ou justificado no PLAN.

---

## Requirement Verification Map

| Requirement | Planned Task | Threat Ref | Secure/Correct Behavior | Primary Test Type | Required Automated Evidence | Status |
|-------------|--------------|------------|-------------------------|-------------------|-----------------------------|--------|
| AUTH-01 | 03-02/T2 (W2), 03-03/T1 (W4), 03-11/T1 (W12), 03-17/T1 (W20) | T-03-02-PUBLIC / T-03-03-PUBLIC / T-03-11-PUBLIC / T-03-16-PUBLIC | Visitante sem cookie acessa somente versões públicas; rascunhos e comandos continuam protegidos. | MockMvc IT + Playwright | GET anônimo, controle negativo de rascunho e navegador sem sessão. | ⬜ pending |
| SPRT-01 | 03-02/T2 (W2), 03-04/T1 (W5), 03-16/T1 (W17), 03-20/T2 (W19) | T-03-01-SCOPE / T-03-02-IDOR / T-03-04-IDOR / T-03-16-IDOR / T-03-20-AUTH | Catálogo/local respeitam o UUID real da competição; outro escopo falha e a convergência operacional não muda autorização. | PostgreSQL/MockMvc IT + Angular | matriz geral/escopo alheio/escopo correto, constraints e equivalência do Console. | ⬜ pending |
| SPRT-02 | 03-05/T2 (W6), 03-06/T2 (W7), 03-08/T2 (W9), 03-09/T2 (W10), 03-20/T2 (W19) | T-03-05-IDOR / T-03-08-PUBLISH / T-03-09-CSV / T-03-09-IDOR / T-03-20-LOGIC | Cadastro individual e importação CSV/XLSX não criam referência implícita nem escrita parcial; telas operacionais convergidas mantêm os mesmos comandos. | unit + IT + Angular | CSV/XLSX válido, referência ausente, escopo alheio, erro tardio com zero writes e equivalência visual/funcional. | ⬜ pending |
| SPRT-03 | 03-06/T2 (W7) | T-03-06-PII | Atleta sem conta é identidade esportiva válida e não exige `identityId`. | repository/MockMvc IT | persistência e DTO público do atleta não vinculado. | ⬜ pending |
| SPRT-04 | 03-06/T2 (W7), 03-19/T3 (W18) | T-03-06-HMAC / T-03-06-PII / T-03-19-PII | Matrícula é transitória e usa exatamente normalização/HMAC da Fase 2; a página convergida mantém o body autorizado sem persistência/exposição. | unit/architecture/IT + Angular | compatibilidade do seam e ausência em entity, response, DOM pós-sucesso, storage e log. | ⬜ pending |
| SPRT-05 | 03-06/T2 (W7) | T-03-06-PII | Banco guarda somente versão+fingerprint, sem valor bruto consultável. | Flyway/ArchUnit IT | inspeção de schema e regra de privacidade estreita. | ⬜ pending |
| SPRT-06 | 03-07/T2 (W8) | T-03-07-RACE | Correspondência única cria uma única proposta auditada nos dois sentidos. | concurrency IT | retry/idempotência para conta-primeiro e atleta-primeiro. | ⬜ pending |
| SPRT-07 | 03-07/T2 (W8) | T-03-07-ENUM / T-03-07-RACE | Ambiguidade, fingerprint ligado, múltiplos candidatos ou dados divergentes nunca produzem vínculo automático. | MockMvc/concurrency IT | `AthleteLinkProposalIT`: HMAC coincidente + dados divergentes → `BLOCKED/DIVERGENT_DATA`, zero vínculo, uma fila manual idempotente e nenhum fingerprint/PII enumerado. | ⬜ pending |
| SPRT-08 | 03-07/T2 (W8), 03-14/T2 (W15), 03-20/T2 (W19) | T-03-07-OWNER-IDOR / T-03-07-SCOPE-IDOR / T-03-14-IDOR / T-03-20-AUTH | Somente a pessoa alvo aceita, rejeita ou contesta; operação de revisão mantém capability e concorrência com um vencedor. | security/concurrency IT + frontend | owner/alheio, aceite concorrente, UI sem campos pessoais antes do aceite e Console fail-closed. | ⬜ pending |
| SPRT-09 | 03-08/T2 (W9) | T-03-08-PUBLISH / T-03-08-RACE | Elenco nasce rascunho, publica snapshot e alteração tardia exige motivo. | domain + IT | rascunho/publicação/correção; ausência de titular/reserva. | ⬜ pending |
| SPRT-10 | 03-02/T2 (W2), 03-04/T1 (W5), 03-10/T2 (W11) | T-03-04-INTEGRITY / T-03-10-SCOPE | Evento valida participantes, UTC+zona, fase, fonte e local/espaço coerentes. | domain + PostgreSQL IT | matriz de formato/tempo/local e constraints. | ⬜ pending |
| SPRT-11 | 03-02/T2 (W2), 03-10/T2 (W11) | T-03-01-HISTORY / T-03-10-RACE / T-03-10-AUDIT | Evento percorre `RASCUNHO/PUBLICADO/SUSPENSO/ADIADO/CANCELADO`; resultado percorre `INFORMADO/AGUARDANDO_REVISAO/CONFIRMADO/CORRIGIDO`, com versões e 409 sem sobrescrita. | lifecycle/concurrency/audit IT | `SportsEventResultIT`: publicar, suspender, adiar, cancelar, informar, revisar, confirmar e corrigir com fonte/motivo/versão/auditoria, mais saltos inválidos; `SportsEventResultConcurrencyIT`: dois editores. | ⬜ pending |
| SPRT-12 | 03-03/T1 (W4), 03-11/T1 (W12) | T-03-02-PUBLIC / T-03-11-GUARD | Agenda pública é estável, filtrável, acessível e responsiva. | query IT + Angular + Playwright | filtros/deep link, teclado, 320px/1280px, loading/empty/error. | ⬜ pending |
| SPRT-13 | 03-05/T2 (W6), 03-11/T1 (W12), 03-14/T2 (W15) | T-03-05-PII / T-03-11-PUBLIC / T-03-14-PRIV | Páginas públicas usam allowlist e estatísticas derivadas, sem PII. | DTO/MockMvc + Angular/Playwright | organização/equipe/atleta, estados parciais e inspeção DOM/storage/network. | ⬜ pending |
| SPRT-14 | 03-12/T2 (W13), 03-13/T2 (W14) | T-03-12-DERIVE / T-03-12-IDOR / T-03-13-AUDIT | Bracket deriva de eventos e classificação é snapshot manual versionado. | domain + API + accessibility | `bye`, vencedor anterior, correção de evento e leitura sem conectores. | ⬜ pending |
| SPRT-15 | 03-05/T2 (W6), 03-19/T3 (W18) | T-03-01-FAVORITE / T-03-05-MIGRATION / T-03-19-LOGIC | Favorito passa a organização sem associação incorreta nem perda do perfil; sua apresentação convergida preserva o mesmo store/contrato. | Flyway/profile IT + Angular | migration preservadora, organização inexistente, regressão do perfil atual e equivalência visual/funcional. | ⬜ pending |
| SPRT-16 | 03-14/T2 (W15), 03-15/T2 (W16), 03-19/T3 (W18) | T-03-14-IDOR / T-03-14-XSS / T-03-15-MEDIA / T-03-15-SSRF / T-03-19-PII | Só o atleta vinculado altera texto/social/foto; mídia é processada, falha preserva anterior e o Console pessoal não muda a regra owner-only. | security/media IT + frontend | owner/alheio, URL segura, fixtures adversariais, remoção, DTO público e equivalência da rota pessoal. | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

## Plan Verification Commands

| Plan/Wave | Primary automated evidence |
|---|---|
| 03-01 / W1 | decisão documents gate (`approved`, IDs e forward-fix) |
| 03-02 / W2 | `SportsTracerIT`, Flyway, Modulith e API pública backend — ✅ green: focal 9/9 + 2/2, Surefire 251/251, Failsafe 342/342 e Node 623/623 |
| 03-18 / W3 | contrato visual V, manifesto 17/17, 26 superfícies, DAG/cobertura e pareceres frescos; nenhum requisito de produto concluído |
| 03-03 / W4 | ✅ green — candidato `0B631F...AB95`, gerador 72/72 + check, Node 623/623, Angular 241/241, Playwright 2/2, Surefire 251/251, Failsafe 343/343 e auditoria sem P0/P1/P2 |
| 03-04 / W5 | `SportsCatalogIT`, expansão operacional completa do workspace O1 e contract gates |
| 03-05 / W6 | V11, `SportsOrganizationIT`, `ProfilePersistenceIT`, contrato, specs O3/perfil, Node guard, Angular, builds e Playwright Arena/Console |
| 03-06 / W7 | athlete/HMAC compatibility, IDOR, privacy, Modulith e O4 |
| 03-07 / W8 | proposal functional/concurrency/IDOR IT, `DIVERGENT_DATA` sem enumeração e O8 |
| 03-08 / W9 | roster functional/concurrency IT e O4 |
| 03-09 / W10 | parsers CSV/XLSX, atomic import/IDOR IT, full dependency trees e O5 |
| 03-10 / W11 | event/result lifecycle completo, transições inválidas, concurrency/audit IT e O6 |
| 03-11 / W12 | public view IT, Angular P1–P6 e Playwright público |
| 03-12 / W13 | bracket IT e P7/O7 |
| 03-13 / W14 | standings IT e P8/O7 |
| 03-14 / W15 | owner/XSS profile IT e perfil owner/público |
| 03-15 / W16 | adversarial media IT, S3 19/19, AWS tree e UI de foto |
| 03-16 / W17 | capabilities/authz UUID A/B e workspace O1–O8 |
| 03-19 / W18 | route boundary híbrido, Console pessoal, specs perfil/matrícula e Playwright `@03-19-personal-console` |
| 03-20 / W19 | Console operacional legado, retirada do shell, build e Playwright `@03-20-legacy-operations` |
| 03-17 / W20 | privacy, Modulith, 26 superfícies, mutações, suítes completas e snapshot |

---

## Mandatory Negative Mutations

- [ ] Colocar o matcher público depois de `/api/v1/**.authenticated()` deixa AUTH-01 vermelho.
- [ ] Remover a comparação de `scopeRef` ou usar apenas `hasRole` deixa SPRT-01/SPRT-02 vermelhos.
- [ ] Remover o filtro de versão publicada faz o teste anônimo falhar.
- [ ] Sobrescrever uma versão confirmada deixa o teste histórico/liquidação futura vermelho.
- [ ] Persistir antes de validar a última linha CSV/XLSX deixa o teste de atomicidade vermelho.
- [ ] Criar vínculo HMAC sem aceite deixa o teste de proposta vermelho.
- [ ] Remover o bloqueio `DIVERGENT_DATA` faz `AthleteLinkProposalIT` criar vínculo ou omitir a fila manual e deixa SPRT-07 vermelho.
- [ ] Serializar entity de atleta na rota pública deixa o teste de privacidade vermelho.
- [ ] Permitir suspensão/adiamento/cancelamento/informação/correção sem fonte ou motivo, ou publicar placar em `INFORMADO/AGUARDANDO_REVISAO`, deixa `SportsEventResultIT` vermelho.
- [ ] Servir bytes originais da foto deixa o teste de mídia vermelho.
- [ ] Copiar placar/participante para o bracket deixa o teste derivado vermelho.

### Evidência discriminante concluída na Wave 2

| Controle | Resultado |
|----------|-----------|
| Escopo persistido A/B | ✅ `rejectsPublicationByAdministratorFromAnotherScope()` ficou vermelho ao remover `requireCompetitionAdministrator`: esperado 403, observado 201. |
| Restauração | ✅ `SportsTracerService.java` restaurado ao SHA-256 `552921E19D2B9ED51DE813470DE8EA90F67E65947C2B88C59A71CBAEDBB05261`. |
| Limite da evidência | AUTH-01, SPRT-01, SPRT-10 e SPRT-11 continuam `pending` no mapa global porque também dependem dos planos 03-03, 03-04, 03-10, 03-11, 03-16 e 03-17. |

---

## Wave 0 Requirements

- [ ] 03-03/T1 e 03-17/T1 — `frontend/playwright.sports.config.ts` e `frontend/e2e/sports-pilot.spec.ts` com 320px, 1280px, teclado e sessão ausente. **Parcela 03-03: ✅ green; certificação ampliada do 03-17 permanece pendente.**
- [x] 03-09/T2 — `backend/src/test/resources/sports/csv/` e `backend/src/test/resources/sports/xlsx/` com casos válidos, erro tardio, BOM/encoding, quoting, duplicata, referência ausente, tipos/fórmulas/aba/ZIP e limites. Green: `SportsCsvParserTest` 8/8, `SportsXlsxParserTest` 7/7 e `SportsTabularImportIT` 8/8.
- [ ] 03-15/T2 — `backend/src/test/resources/sports/media/` com válida, MIME falso, truncada, pixels excessivos, metadados e falha de storage.
- [x] 03-06/T2 — compatibilidade da porta HMAC e extensão estreita de `PrivacyArchitectureTest`. **Green - V12, atleta/HMAC, privacidade e Modulith certificados na Wave 7.**
- [ ] 03-02/T2, 03-04/T1, 03-05/T2, 03-06/T2, 03-07/T2, 03-08/T2, 03-10/T2, 03-12/T2, 03-13/T2, 03-14/T2 e 03-15/T2, com reconciliação no 03-17/T1 — atualização incremental de `FlywayMigrationIT`/checksum para V9–V20. **V9: green no 03-02; V10/V11/V12: green; V13/V14: green no 03-07; V15-V20 permanecem pendentes.**
- [x] 03-02/T1 — teste de existência de competição ao conceder papel escopado. **Provado por `rejectsGrantForUnknownCompetition()`.**
- [ ] 03-03/T2 a 03-15/T3, com inventário/digest final no 03-17/T1 — gate do gerador OpenAPI e checkpoint humano de inventário/digest. **Parcela 03-03: ✅ candidato aprovado e geração governada verde; planos posteriores permanecem pendentes.**

Esses itens não precisam estar todos no primeiro plano. Cada PLAN deve possuir a fixture/gate antes da task que depende dela.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Fidelidade visual final e clareza dos fluxos públicos/operacionais | AUTH-01, SPRT-12–16 | julgamento visual complementa, mas não substitui, acessibilidade automatizada | navegador real em 320px e 1280px conforme `03-UI-SPEC.md`; registrar evidência por superfície. |
| Aprovação de inventário/digest OpenAPI | requisitos com contrato HTTP | decisão humana governada do artefato gerado | comparar arquivos adicionados/removidos, campos e digest; recusar PII ou serviço inesperado. |
| Dependências CSV/XLSX e/ou imagem | SPRT-02, SPRT-16 | instalação/árvore/licença são decisão material | apresentar coordenadas, versões, licenças, transitivos, riscos de ZIP/XML e limites antes de editar `pom.xml`. |
| Primeiro DDL esportivo e migração do favorito | SPRT-01, SPRT-15 | one-way/costly | revisar ER/DDL, mapeamento de dados, forward-fix e invariantes antes da migration. |

---

## Validation Sign-Off

- [x] Todos os PLANs/tasks possuem `<automated>` ou dependência explícita de Wave 0.
- [x] Nenhuma sequência de três tasks fica sem feedback automatizado.
- [x] Todos os caminhos ainda inexistentes recebem fixture/gate antes do uso.
- [x] Nenhum comando usa watch mode.
- [x] Testes focais deverão ser repetidos pelo orquestrador; testes do worker não certificam entrega.
- [x] Suítes agregadas e Playwright material estão atribuídos a cada onda aplicável.
- [x] Mutações negativas são discriminantes e restauradas por hash.
- [x] `nyquist_compliant: true` foi mantido somente depois de os PLANs preencherem IDs, comandos e arquivos reais.

**Approval:** Gates B2/C2 permanecem fechados; 03-03/W4 a 03-06/W7 foram implementados, certificados e o 03-06 foi integrado linearmente na master. O proximo plano tecnico e `03-07/W8`; `AUTH-01` e `SPRT-12` permanecem `pending` no nivel global da fase.

## Wave 10 — Certificacao do 03-09

O plano 03-09 foi implementado na worktree `worktree-agent-03-09` sobre `2447652` e permanece `complete-pending-git`. O candidato OpenAPI aprovado pelo responsavel e `C1B4B3F7F37E84EF63911A6BE31A2B36C949DDC0FB63B84C193D72F19B3D3BB8`; os hashes `governed-before` e `governed-after` permaneceram iguais.

| Gate | Evidencia | Resultado |
|---|---|---|
| Supply chain | Dependency tree completa com Commons CSV 1.14.1, POI 5.5.1 e transitivos exigidos; nenhum `SNAPSHOT` ou Apache HTTP | PASS |
| Backend focal | `SportsCsvParserTest` 8/8, `SportsXlsxParserTest` 7/7, `SportsTabularImportIT` 8/8 | PASS |
| Backend completo | Surefire 266/266 + Failsafe 391/391 = 657 testes | PASS |
| Node completo | `node --test scripts/*.test.mjs` = 625/625 | PASS |
| Angular O5 | Store/componente de importacao = 7/7 | PASS |
| Angular completo | 285/290; cinco falhas historicas somente em `role-admin-page.component.spec.ts` | PARCIAL CONHECIDO |
| Builds | development, homolog e production | PASS |
| Browser | Playwright esportivo desktop e 320px = 2/2 | PASS |
| Contrato/rotas | `generate-api.mjs --check`, hashes do candidato e fronteira Angular | PASS |

O filtro Maven com `*` previsto no texto original do plano foi rejeitado pelo wrapper seguro; a mesma verificacao foi executada na arvore completa sem curinga. A primeira rodada Maven sem HMAC falhou somente por ambiente sem variaveis; a repeticao com a chave de teste padronizada passou integralmente. Ambos sao desvios mecanicos e nao alteram a regra de negocio.

Os seis paths que surgiram durante a implementacao — dois modelos auxiliares, duas rotas build-specific e dois arquivos da allowlist — foram reconciliados no PLAN, totalizando 56 paths declarados. Nenhum stage, commit, integracao, limpeza ou push foi executado.
