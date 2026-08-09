---
phase: 01-funda-o-segura-da-plataforma
plan: "02"
subsystem: infra
tags: [maven, java-25, spring-boot, spring-modulith, supply-chain, redis, archunit]

requires:
  - phase: 01-01
    provides: "Java 25, Node compatível, Docker ativo e gate humano de pacotes e runtimes concluído"
provides:
  - "Maven Wrapper 3.9.16 reproduzível, `only-script`, com checksum SHA-256 fixado"
  - "POM Java 25/Spring Boot 4.1.0/Spring Modulith 2.1.0 resolvido e compilado"
  - "Launcher Maven portátil que seleciona o Wrapper por sistema operacional sem concatenar entrada livre em shell"
  - "Regra de Git que exclui qualquer diretório Maven `target/`"
affects: [01-17, backend, build, ci, redis-session]

actuals:
  tokens: 7263
  tasks: 3
  commits: 2

tech-stack:
  added:
    - "Apache Maven 3.9.16 via Wrapper"
    - "Maven Wrapper Plugin 3.3.4"
    - "Maven Enforcer Plugin 3.6.3"
    - "Spring Boot 4.1.0"
    - "Spring Modulith 2.1.0"
    - "Testcontainers 2.0.5"
    - "ArchUnit 1.4.2"
    - "Lettuce 7.5.2.RELEASE, transitivo do starter Redis"
  patterns:
    - "Build backend invocado por `node scripts/maven.mjs -f backend/pom.xml ...`"
    - "Wrapper sem JAR versionado, distribuição oficial e checksum fixado"
    - "Java, Maven, releases e versões de plugins protegidos pelo Maven Enforcer"

key-files:
  created:
    - ".gitignore"
    - "mvnw"
    - "mvnw.cmd"
    - ".mvn/wrapper/maven-wrapper.properties"
    - "backend/pom.xml"
    - "scripts/maven.mjs"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-02-SUMMARY.md"
  modified:
    - ".planning/STATE.md"
    - ".planning/ROADMAP.md"

key-decisions:
  - "Manter exatamente Java 25, Maven 3.9.16, Spring Boot 4.1.0 e Spring Modulith 2.1.0; falhas não autorizam downgrade silencioso"
  - "Usar `spring-boot-starter-session-data-redis` para integrar Spring Session, autoconfiguração do Boot e o cliente Lettuce"
  - "Alinhar toda a família ArchUnit em 1.4.2, conforme a versão trazida pelo Spring Modulith 2.1.0"
  - "Permitir apenas os plugins padrão fixos do Maven 3.9.16 no Enforcer, mantendo snapshots e versões dinâmicas proibidos"

patterns-established:
  - "Supply chain Maven: coordenadas, versões, URL, checksum e primeira resolução dependem de revisão humana explícita"
  - "Compatibilidade da stack é provada pelo Wrapper aprovado antes da criação do scaffold Spring"

requirements-completed: [ARCH-01]

coverage:
  - id: D1
    description: "POM da stack backend aprovada resolve e compila sob Java 25 e Maven Wrapper 3.9.16"
    requirement: ARCH-01
    verification:
      - kind: integration
        ref: "node scripts/maven.mjs --batch-mode --no-transfer-progress -f backend/pom.xml -DskipTests dependency:tree compile"
        status: pass
    human_judgment: false
  - id: D2
    description: "Wrapper usa Maven 3.9.16, `only-script`, URL oficial e checksum SHA-256 fixado, sem JAR versionado"
    requirement: ARCH-01
    verification:
      - kind: other
        ref: "Get-FileHash do apache-maven-3.9.16-bin.zip e inspeção de .mvn/wrapper/maven-wrapper.properties"
        status: pass
    human_judgment: true
    rationale: "A confiança na primeira resolução e nos artefatos exatos era um gate humano bloqueante, aprovado explicitamente pelo responsável."
  - id: D3
    description: "Launcher seleciona `mvnw.cmd` no Windows e `mvnw` no Unix, preservando argumentos e recusando metacaracteres"
    requirement: ARCH-01
    verification:
      - kind: other
        ref: "node --check scripts/maven.mjs e execução bem-sucedida do launcher no Windows"
        status: pass
    human_judgment: true
    rationale: "O ramo Windows foi executado; o ramo Unix/WSL2 foi revisado estaticamente e ainda não recebeu execução multiplataforma."
  - id: D4
    description: "Diretórios `target/` permanecem fora do Git em qualquer nível do monorepo"
    requirement: ARCH-01
    verification:
      - kind: other
        ref: "git check-ignore -v --no-index backend/target/probe.txt"
        status: pass
    human_judgment: false

duration: "não mensurada — execução acompanhada em múltiplos gates"
completed: 2026-08-08
status: complete
---

# Phase 1 Plan 02: Build Maven canônico Summary

**Build Maven reproduzível para Java 25, Spring Boot 4.1 e Spring Modulith 2.1, validado pelo Wrapper 3.9.16**

## Performance

- **Duration:** não mensurada — execução acompanhada em múltiplos gates
- **Started:** não registrado — trabalho retomado em múltiplas interações
- **Completed:** 2026-08-08T23:45:56-03:00
- **Tasks:** 3
- **Files modified:** 6 artefatos de entrega e 3 documentos de acompanhamento GSD

## Accomplishments

- Maven Wrapper `3.9.16` gerado no modo `only-script`, sem JAR versionado, com distribuição oficial e checksum SHA-256 fixado.
- POM Java 25/Boot 4.1/Modulith 2.1 protegido pelo Enforcer e resolvido sem snapshots, versões dinâmicas ou stack distribuída fora do MVP.
- Launcher portátil validado no Windows, com seleção explícita de `mvnw.cmd`, argumentos allowlisted e propagação de `stdio`, sinais e exit code.
- Árvore final alinhada em ArchUnit `1.4.2` e preparada para sessões Redis com Lettuce `7.5.2.RELEASE`.

## Task Commits

Os dois commits de tarefa foram criados após autorizações separadas de stage e commit, conforme `AGENTS.md`.

1. **Task 1: preparar o build Maven para revisão sem baixar dependências** — `42397f5` (`chore`).
2. **Gate 1: revisar o scaffold Maven antes da resolução** — checkpoint humano concluído; não modifica arquivos por si só.
3. **Task 2: gerar o Wrapper aprovado e provar a resolução da stack** — `3b97487` (`chore`).

Este `SUMMARY`, `STATE` e `ROADMAP` compõem o fechamento documental do plano no commit de metadados correspondente.

## Files Created/Modified

- `.gitignore` — exclui qualquer diretório Maven `target/`.
- `mvnw` — launcher oficial Unix do Maven Wrapper, sem JAR embarcado.
- `mvnw.cmd` — launcher oficial Windows do Maven Wrapper.
- `.mvn/wrapper/maven-wrapper.properties` — fixa Wrapper Plugin `3.3.4`, Maven `3.9.16`, `only-script`, URL oficial e checksum.
- `backend/pom.xml` — fixa a stack backend, dependências geridas e regras do Maven Enforcer.
- `scripts/maven.mjs` — oferece o comando Maven canônico e portátil da BETUFU.
- `.planning/phases/01-funda-o-segura-da-plataforma/01-02-SUMMARY.md` — registra resultado, evidências e desvios do plano.
- `.planning/STATE.md` — avança a posição operacional até o Plano `01-02`.
- `.planning/ROADMAP.md` — atualiza o progresso da Fase 1 para `2/28`.

## Decisions Made

- O build permanece estritamente em Java 25, Maven `3.9.16`, Spring Boot `4.1.0` e Spring Modulith `2.1.0`; nenhuma falha justificará downgrade automático.
- O starter `spring-boot-starter-session-data-redis` substitui a dependência direta de baixo nível e fornece a integração completa do Boot, incluindo Lettuce.
- ArchUnit foi alinhado em `1.4.2` para evitar uma família mista entre os módulos JUnit e o core transitivo do Modulith.
- O Enforcer aceita os plugins padrão fixos do Maven `3.9.16`, mas continua rejeitando releases dinâmicas, snapshots e versões de plugin não determinísticas.

## Deviations from Plan

### Correções bloqueantes aprovadas

**1. [Regra 3 — Bloqueio] Atualização do Maven Enforcer**

- **Found during:** revisão do POM antes da resolução.
- **Issue:** a versão inicialmente declarada não era a versão estável aprovada para o gate.
- **Fix:** Maven Enforcer atualizado para `3.6.3` após aprovação explícita.
- **Files modified:** `backend/pom.xml`.
- **Verification:** todas as cinco regras do Enforcer passaram na execução final.
- **Committed in:** `42397f5`.

**2. [Regra 3 — Bloqueio] Coordenadas Testcontainers 2.x corrigidas**

- **Found during:** primeira geração e resolução Maven.
- **Issue:** os nomes antigos dos artefatos não recebiam versões do BOM Testcontainers `2.0.5`.
- **Fix:** uso de `testcontainers-junit-jupiter` e `testcontainers-postgresql` após aprovação explícita.
- **Files modified:** `backend/pom.xml`.
- **Verification:** ambos resolveram em `2.0.5` na árvore final.
- **Committed in:** `42397f5`.

**3. [Regra 3 — Bloqueio] Plugins padrão fixos do Maven permitidos no Enforcer**

- **Found during:** primeira execução de `dependency:tree compile` pelo Wrapper.
- **Issue:** `requirePluginVersions` rejeitou o `maven-site-plugin:3.12.1` padrão e determinístico do Maven `3.9.16`.
- **Fix:** `banMavenDefaults=false`, preservando as demais proibições de versão dinâmica e snapshot.
- **Files modified:** `backend/pom.xml`.
- **Verification:** `RequirePluginVersions` passou na execução final.
- **Committed in:** `42397f5`.

### Alinhamentos de dependências aprovados

**4. [Regra 4 — Dependência] Família ArchUnit alinhada**

- **Found during:** inspeção da primeira árvore de dependências bem-sucedida.
- **Issue:** Modulith trazia core `1.4.2`, enquanto os módulos JUnit estavam fixados em `1.4.1`.
- **Fix:** propriedade `archunit.version` alterada para `1.4.2` após aprovação explícita.
- **Files modified:** `backend/pom.xml`.
- **Verification:** core, API e engine resolveram integralmente em `1.4.2`.
- **Committed in:** `42397f5`.

**5. [Regra 4 — Integração] Starter completo para Spring Session Redis**

- **Found during:** inspeção da árvore e do requisito futuro de sessão Redis real.
- **Issue:** a dependência direta `spring-session-data-redis` não fornecia cliente Redis.
- **Fix:** substituição por `spring-boot-starter-session-data-redis` após inspeção dos POMs oficiais e aprovação explícita.
- **Files modified:** `backend/pom.xml`.
- **Verification:** árvore final inclui Spring Session Data Redis `4.1.0` e Lettuce `7.5.2.RELEASE`.
- **Committed in:** `42397f5`.

---

**Total deviations:** 5 correções aprovadas pelo responsável — 3 bloqueios de resolução e 2 alinhamentos de dependências.
**Impact on plan:** todas preservam a arquitetura aprovada, eliminam inconsistências verificáveis e não adicionam microsserviços ou infraestrutura fora do escopo.

## Issues Encountered

- A primeira resolução falhou pelas coordenadas antigas do Testcontainers; a execução foi interrompida até a correção ser aprovada.
- A primeira compilação foi bloqueada pelo tratamento dos plugins padrão do Maven; a regra foi corrigida somente após aprovação.
- O arquivo temporário `C:\Users\giova\AppData\Local\Temp\betufu-maven-3.9.16-bin.zip` permanece fora do repositório porque a política do shell recusou sua remoção. Ele não afeta o projeto e pode ser removido manualmente depois.

## User Setup Required

None — nenhuma configuração de serviço externo é necessária para este plano.

## Next Phase Readiness

- A Onda 2 está tecnicamente concluída e habilita o Plano `01-17`, que criará o scaffold Spring Boot/Modulith e a fronteira BFF OIDC.
- `ARCH-01` continua globalmente pendente porque também é compartilhado pelos Planos `01-04`, `01-14`, `01-16`, `01-17` e `01-19`.
- Este `SUMMARY`, `STATE` e `ROADMAP` encerram documentalmente o Plano `01-02`; o Plano `01-17` e a Onda 3 permanecem sem autorização de execução.

## Self-Check: PASSED

- [x] Os seis artefatos declarados no plano existem.
- [x] `node --check scripts/maven.mjs` passou.
- [x] Wrapper fixa Maven `3.9.16`, `only-script` e checksum SHA-256, sem `maven-wrapper.jar` versionado.
- [x] `dependency:tree compile` terminou com `BUILD SUCCESS` sob Java 25 e Maven Wrapper `3.9.16`.
- [x] As cinco regras do Maven Enforcer passaram.
- [x] ArchUnit resolveu integralmente em `1.4.2`; Redis inclui Lettuce `7.5.2.RELEASE`.
- [x] `git check-ignore` confirmou `backend/target/` coberto por `**/target/`.
- [x] Commits de tarefa `42397f5` e `3b97487` existem e contêm somente seus arquivos aprovados.
- [x] Metadados GSD compõem o fechamento documental do plano; nenhum push ocorreu durante esta verificação.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Completed: 2026-08-08*
