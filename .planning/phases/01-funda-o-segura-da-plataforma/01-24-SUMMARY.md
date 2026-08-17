---
phase: 01-funda-o-segura-da-plataforma
plan: "24"
subsystem: local-destructive-reset
tags: [docker, compose, reset, allowlist, fail-closed, data-loss-prevention]

requires:
  - phase: 01-11
    provides: "Perfis daily/full, orquestrador dev.mjs e lock por lockfile"
provides:
  - "Reset local destrutivo com dry-run padrão e confirmação literal"
  - "Função pura de seleção fail-closed, ancorada no checkout por config_files"
  - "Documentação de desenvolvimento com runtimes, perfis, comandos e smokes"
affects: [ops, docker, local-environment]

actuals:
  tasks: 1
  implementation_paths: 4
  mutations_executed: 13
  mutations_effective: 13
  acceptance_criteria_deferred: 1
  external_review_rounds: 3
  p1_found_by_review: 1
  p3_applied: 11

tech-stack:
  added: []
  patterns:
    - "Seleção destrutiva em função pura, provada por inventário em memória, nunca por remoção real"
    - "Conjunção de critérios com âncora de checkout, em vez de label de projeto isolado"
    - "Inventário coletado sem pré-filtro, para que o dry-run demonstre a recusa dos vizinhos"

patterns-established:
  - "Quando o nome do projeto Compose é genérico, o discriminador confiável é com.docker.compose.project.config_files, que amarra o recurso a um checkout."
  - "Um teste de escopo destrutivo não pode criar o recurso hostil de verdade: se a guarda falhar, o teste causa a perda que deveria detectar."
  - "Prune é o oposto de allowlist — remove por ausência de critério, não por presença."
---

# Plano 01-24 — Reset local confirmado e estritamente allowlisted

`BASE_HEAD`: `6ca340ac19954be016f73f4b5d4faabe1e0aaac2`, igual a `origin/master`.
Nada foi commitado; stage, commit e push dependem de autorização própria.

## O que foi entregue

`node scripts/reset-local.mjs` é o único comando da fase que apaga dados. Dry-run
é o padrão; `--apply` exige a frase `APAGAR DADOS LOCAIS BETUFU` comparada byte a
byte; `--bootstrap` é opcional e desligado.

A decisão do que remover vive em `scripts/lib/local-resources.mjs`, função pura
sem I/O. O CLI apenas coleta inventário, imprime a lista e executa.

## O achado que determinou o desenho

`scripts/lib/compose.mjs:61,77` fixa `--project-name infra`. O Compose deriva o
nome do projeto do diretório, e `infra` é banal: qualquer outra composição num
diretório assim produz `com.docker.compose.project=infra`. O filtro que o 01-11
já usava (`dev.mjs:780`) é, sozinho, insuficiente para autorizar remoção.

O dry-run nesta máquina confirmou que o risco não era hipotético: entre os 36
containers há stacks de terceiro reais — `finvise-*`, `evo-crm-community-*`,
`evolution_api`, `cf-tunnel-*` — incluindo um `finvise-postgres`, mesmo serviço
que a BETUFU usa.

## Evidência de labels — verificada, não presumida

`docker inspect` em Compose 5.3.1, nesta máquina:

| Recurso | `project` | `config_files` | Outros |
|---|---|---|---|
| container | `infra` | **presente** | `service`, `oneoff`, `working_dir` |
| rede | `infra` | **ausente** | `network`, `config-hash` |
| volume | `infra` | **ausente** | `volume`, `config-hash` |

Isso decidiu tudo. Containers, que tinham o nome mais fraco, são exatamente onde
existe a âncora de checkout. Volumes não a têm, mas `infra/compose.yaml:187-195`
lhes dá `name:` explícito e distintivo. Redes não têm critério forte — e já
haviam saído da allowlist por decisão do responsável, o que a evidência ratificou
por um motivo que a decisão original não tinha.

`config-hash` foi descartado como âncora: é hash da configuração do recurso, não
do caminho, então dois checkouts idênticos produzem o mesmo valor.

## Critério final

**Containers — 4 condições conjuntas:** `service` no conjunto de 7; `project ==
infra`; `oneoff == False`; `config_files` igual ao `infra/compose.yaml` deste
checkout, após normalização de separador (case-folding só em win32).

**Volumes — 3 condições conjuntas:** nome em igualdade byte a byte com um dos 4;
`project == infra`; label `volume` igual ao nome.

**Redes:** fora da allowlist.

## Decisões do responsável

- **D1** rede `infra_default` fora — não guarda dado e é recriada pelo `up`.
- **D2** imagens `betufu/*:local` não removidas — reset apaga dados, não builds.
- **D3** bootstrap opcional e off por padrão — encadear destruição com subida
  embaralha o diagnóstico.
- **D4** criação de `docs/` ratificada antes, não depois.
- **D5** confirmação de `config_files` por inspeção, bloqueante antes do código.

## Revisão externa — um P1 real encontrado

A primeira entrega passou por revisão externa, que devolveu `CHANGES_REQUIRED`
com um **P1 legítimo e determinístico**, mais dois P2 e seis P3. Todos aceitos.

**P1 — `--apply --bootstrap` nunca concluía.** O reset segurava
`.betufu-dev.lock` e disparava `dev.mjs up daily` de dentro do `try`. O
subprocesso chama `acquireLock()` como primeiro ato (`dev.mjs:97`) sobre o mesmo
lockfile, encontrava o PID dono vivo e morria com "Outra instância está
rodando" (`compose.mjs:223-229`). Não era corrida nem timing: falhava sempre. O
critério de aceitação do plano — "bootstrap volta healthy" — era inalcançável, e
`docs/development.md` recomendava a forma que nunca conclui.

Corrigido movendo a subida para **fora** do `try`/`finally`, após a liberação do
lock, com propagação do exit code. `dev.mjs` não foi tocado.

**A causa do P1 não foi só o código: foi a ausência de teste.** Os 19 testes
originais cobriam integralmente a função pura e nada do CLI — 353 linhas sem
detector. O P1 morava exatamente ali. A correção do P2 de cobertura é o que
impede a reincidência.

**P2a** — a recusa de homolog/prod exigida pelo `<action>` não existia.
Implementada em `detectNonLocalEnvironment`, cobrindo `CI`, `NODE_ENV`,
`BETUFU_ENV` e `SPRING_PROFILES_ACTIVE`.

**P2b** — o CLI ganhou cobertura. `runReset` passou a receber dependências por
injeção, e 13 testes novos exercitam dry-run padrão, os gates de `--apply`,
recusa de ambiente, ordem containers→volumes, falha parcial e a ordem entre
liberar o lock e subir o bootstrap. Nenhum toca o daemon.

**P3 aplicados:** (a) ambiente já limpo ainda executa o bootstrap pedido;
(b) `--apply` exige TTY, fechando `echo FRASE | node ... --apply`;
(c) containers removidos por ID imutável, fechando a janela entre inventário e
remoção; (d) recurso sem nome passou a ser registrado em `ignored` em vez de
descartado em silêncio; (e) `normalizeConfigPath` preserva prefixo UNC `//`;
(f) `inspectLabels` passou a operar em lotes de 40, afastando o limite de linha
de comando do Windows.

## Segunda rodada de revisão — `PASS`, com 5 P3

A rodada 2 retornou `PASS`, sem P0, P1 ou P2, e listou 5 P3 de acompanhamento.
O responsável mandou corrigir todos antes de seguir. Os quatro que são de código
foram aplicados; o quinto é ratificação e não é corrigível por mim.

**P3-1 — guarda de execução direta transformava o comando em no-op silencioso.**
A comparação por sufixo de caminho é case-sensitive, então `node
Scripts\reset-local.mjs` num filesystem case-insensitive não executava `main()` e
encerrava com 0. Para um comando destrutivo, "não fez nada e reportou sucesso" é
o modo de falha errado. Substituído por
`import.meta.url === pathToFileURL(process.argv[1]).href`, o que também eliminou
a heurística `import.meta.url.endsWith(...)`, que era sempre verdadeira.
Verificado empiricamente: com a guarda antiga, `node Scripts/reset-local.mjs`
produz **zero linhas de saída e exit 0**; com a nova, executa normalmente.

**P3-2 — "já removido" contava como falha.** Um recurso que sumia entre o
inventário e a remoção fazia o Docker sair não-zero, o que marcava estado
parcial, suprimia o bootstrap e devolvia 1 sobre uma convergência benigna. As
remoções passaram a devolver `removed` / `absent` / `failed`, e `absent` não é
falha.

**P3-3 — marcadores de ambiente com critérios desiguais.** `NODE_ENV` usava
âncora de início enquanto os outros dois casavam em qualquer posição, então
`NODE_ENV=staging-production` passava pelo portão e o mesmo valor em `BETUFU_ENV`
era recusado. Os três passaram a usar o mesmo padrão não ancorado.

**P3-4 — falha de aquisição do lock sem teste.** Era o único portão descoberto.
Fechado.

**P3-5 — início do Docker Desktop na ausência do responsável.** Não é corrigível
por código: `AGENTS.md:8` exige aprovação específica para ação sobre
infraestrutura, e eu agi sem ela. **Ratificado pelo responsável após a rodada 2.**

## Prova

```text
node --test scripts/reset-local.test.mjs        36/36
node --test scripts/*.test.mjs                  477 testes, 475 pass, 2 fail
```

As 2 falhas do agregado são **pré-existentes em `6ca340a` e alheias a este
plano**. Prova: `git diff --stat` vazio — nenhum arquivo rastreado foi alterado,
os 4 paths deste plano são todos novos, e nenhum validador os importa. Ambas
falham igualmente quando executadas isoladamente.

**As duas não são equivalentes**, e a revisão externa estava certa ao separá-las.
Medi os dois lados de forma independente:

1. `validate-runtime-prerequisites` — "rejeita bypass CSRF na
   SecurityConfiguration canonica": `fixture deve realmente mudar`. É a pendência
   já documentada de `core.autocrlf=true`: o working copy vem em CRLF, a fixture
   muta com `\n`, o `replace` vira no-op. **Tem dono designado** — o projeto, via
   `.gitattributes` ou normalização das mutações com `\r?\n`.
2. `validate-keycloak-realm` — "aceita o realm canonico". **É drift real, não
   CRLF, e não tem dono.** Verificado: `infra/keycloak/realm-betufu.json` traz 11
   chaves em `realm.users[0]` — `id, username, firstName, lastName, email,
   enabled, emailVerified, requiredActions, realmRoles, clientRoles,
   credentials` — enquanto `validate-keycloak-realm.mjs:243-253` exige
   exatamente 9, sem `firstName` nem `lastName`. É um detector vermelho contra o
   artefato canônico commitado em `6ca340a`, e um teste positivo, não uma
   regressão negativa inerte. Vai bloquear o gate agregado até alguém decidir
   qual lado é autoritativo: o realm ou o contrato do validador.

Nenhuma das duas foi corrigida — não pertencem a este plano.

### Mutações

Treze aplicadas, todas revertidas e conferidas por busca (`grep -rn MUTATION
scripts/` vazio).

| # | Mutação | Vermelho em |
|---|---|---|
| M1 | `startsWith` no lugar de igualdade de nome de volume | teste 3 |
| M2 | âncora `config_files` removida | testes 1, 4, 5, 12 |
| M2b | `project` e label de volume ignorados | testes 7 e 8 |
| M4 | aceita qualquer serviço nomeado | teste 9 |
| M4b | verificação de one-off removida | teste 10 |
| M9 | normalização de separador removida | testes 11 e 13 |
| M5 | `trim()` + `toUpperCase()` na confirmação | teste 15 |
| **M10** | bootstrap disparado de dentro do lock — o defeito P1 original | teste 28 |
| **M11** | guarda de execução por sufixo de caminho | empírica: zero saída, exit 0 |
| **M12** | `absent` tratado como falha | teste 33 |
| **M13** | `NODE_ENV` re-ancorado no início | teste 34 |
| **M14** | erro de aquisição do lock engolido | teste 35 |
| **M15** | `classifyRemoval` devolvendo sempre `absent` | teste 36 |

M10 é a regressão do P1: reintroduzir o defeito derruba
`"o bootstrap roda DEPOIS de o lock ser liberado"`. O bug que a revisão externa
achou agora tem detector.

M11 é a única mutação verificada fora dos testes, porque a guarda é de módulo e
não é alcançável por teste unitário: com a forma antiga, `node
Scripts/reset-local.mjs` produz zero linhas e encerra 0; com a nova, executa.

M2 é a guarda mais forte: derruba inclusive o baseline, porque o container de
outro checkout passaria a ser selecionado. M3 (conjunção vs. disjunção) não teve
mutação dedicada — sua asserção caiu junto com M2b, o que já demonstra que morde.

## Dry-run real — artefato de handoff

Executado nesta máquina, sem `--apply`:

```text
Examinados 36 containers e 23 volumes da máquina.
Selecionados: 7 containers (infra-*) e 4 volumes (betufu-local-*)
Preservados:  29 de 36 containers, 19 de 23 volumes
Recusas observadas: finvise-postgres (projeto != infra),
                    evo-crm-community-* (serviço fora da allowlist),
                    cf-tunnel-* (sem label de serviço)
```

Este é o único artefato que cobre a lacuna declarada nos testes: eles provam o
que o reset *decidiria*; só o dry-run mostra o que ele *decidiu* aqui, com os
vizinhos presentes.

## Limites conhecidos da prova

1. **Nenhum `--apply` foi executado.** Que o `docker volume rm` apagou o volume
   certo e só ele permanece não provado, por escolha: provar exigiria destruir
   dados reais. A primeira remoção é decisão do responsável.
2. **Convergência pós-remoção não foi observada.** O critério de aceite "segunda
   execução seleciona conjunto vazio" foi rebaixado ao teste de função pura
   (inventário sem os recursos → seleção vazia). A convergência real é
   verificação do responsável no momento do `--apply`.
3. **Estado parcial foi exercitado só com fake.** O teste prova que uma falha de
   remoção encerra non-zero e impede o bootstrap; interromper uma remoção real
   não foi testado.
4. **Bootstrap real não foi executado.** A ordem correta (fora do lock) está
   provada por teste e pela mutação M10, mas `dev.mjs up daily` de verdade não
   rodou nesta Task. Que ele agora conclua é dedução da correção, não observação.
5. **O daemon Docker caiu no meio da sessão** e foi reiniciado para reproduzir o
   dry-run com o código refatorado. O dry-run publicado abaixo é o pós-refactor,
   e é idêntico ao pré-refactor.
5. `node --test scripts/` (diretório) não funciona neste Node 22.23.2 — tenta
   carregar `scripts` como módulo. O agregado exige o glob `scripts/*.test.mjs`.

## Pendências abertas

1. Executar o primeiro `--apply` com o responsável presente, lendo a lista.
2. **Corrigir `validate-keycloak-realm.mjs`, não o realm.** A investigação
   fechou a questão, e não é mais uma escolha entre dois lados válidos:

   - `060d5ed` (Plano 01-03) criou o realm **e** o validador na mesma commit,
     coerentes: 9 chaves, sem `firstName`/`lastName`.
   - `016390b` (Plano 01-29) acrescentou exatamente duas linhas ao realm —
     `"firstName": "BETUFU"` e `"lastName": "Technical"` — e **não** atualizou
     `validate-keycloak-realm.mjs`.
   - A causa está em `01-29-GATE.md:115-117`: o provider `VERIFY_PROFILE` do
     Keycloak bloqueava o login porque o usuário técnico não tinha perfil
     completo. Os dois campos são a correção desse defeito, não enfeite.
   - `validate-runtime-prerequisites.mjs:1291-1294` **exige** os dois campos como
     strings não vazias, e `validate-runtime-prerequisites.test.mjs:985-987` tem
     regressões negativas para `firstName ausente`, `firstName vazio` e
     `lastName ausente`.

   Ou seja: os dois validadores hoje **se contradizem**. Remover os campos do
   realm consertaria `validate-keycloak-realm` e quebraria simultaneamente
   `validate-runtime-prerequisites` e o login real no Keycloak. O realm está
   certo; o contrato de 9 chaves é que ficou para trás no 01-29.

   Correção mínima: acrescentar `firstName` e `lastName` à lista de chaves
   esperadas em `validate-keycloak-realm.mjs:243-253`. Fora da allowlist do
   01-24, portanto não aplicada aqui. Precisa de entrada em `STATE.md`.
3. **Volumes são compartilhados entre checkouts.** Como `compose.yaml` fixa
   `name:` explícito, dois clones da BETUFU na mesma máquina usam os mesmos
   volumes, e nenhum label os distingue — porque não são recursos diferentes.
   Documentado em `docs/development.md`, não corrigível neste plano.
4. `prohibitions.status` em `01-24-PLAN.md:26-29` segue `unverified`; deve virar
   `verified` citando `reset-local.test.mjs` no fechamento.
5. `STATE.md` diverge: linha 31 diz "Plano 01-11 em auditoria/revisão pós-correções
   Rodada 3" e linha 32 "Wave 18 em encerramento", contra `completed_plans: 18`,
   `01-11-SUMMARY.md` existente e `ROADMAP.md:109` marcando a Wave 18 concluída.
   Registrado sob instrução expressa de não corrigir nesta auditoria.
