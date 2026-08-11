---
phase: 01-funda-o-segura-da-plataforma
plan: "04"
subsystem: frontend-platform
tags: [angular, typescript, npm, vitest, standalone, zoneless, supply-chain, frontend-build]

requires:
  - phase: 01-03
    provides: "Infraestrutura local e identidade Keycloak verificadas para os consumidores frontend posteriores"
provides:
  - "Workspace Angular 22/TypeScript 6 reproduzível com 14 dependências diretas exatas da Matriz B"
  - "Lockfile npm v3 aprovado por hash integral, origem oficial, integridades SHA-512 e allowlist de install scripts"
  - "Bootstrap standalone e zoneless mínimo, sem routing, SSR ou responsabilidades de planos posteriores"
  - "Builds development, homolog e production com budgets explícitos"
  - "Smoke Vitest do componente raiz e pipeline npm executado com scripts de instalação desabilitados"
affects: [01-14, 01-19, 01-20, 01-21, frontend, build, ci]

actuals:
  tasks: 5
  commits: 3

tech-stack:
  added:
    - "Angular 22.1.0"
    - "Angular CLI e build 22.1.2"
    - "TypeScript 6.0.3"
    - "RxJS 7.8.2"
    - "Vitest 4.1.10"
    - "npm 10.9.8"
  patterns:
    - "Dependências diretas usam pins exatos e o lockfile integral é governado por SHA-256"
    - "Instalação reproduzível usa npm ci --ignore-scripts após Gate humano aprovado"
    - "Comandos npm no Windows usam Push-Location/Pop-Location e preservam códigos de saída"
    - "O frontend nasce standalone, zoneless e neutro para não antecipar host, rotas, UI ou OpenAPI"

key-files:
  created:
    - "frontend/package.json"
    - "frontend/package-lock.json"
    - "frontend/angular.json"
    - "frontend/tsconfig.json"
    - "frontend/tsconfig.app.json"
    - "frontend/tsconfig.spec.json"
    - "frontend/src/main.ts"
    - "frontend/src/index.html"
    - "frontend/src/styles.scss"
    - "frontend/src/app/app.config.ts"
    - "frontend/src/app/app.component.ts"
    - "frontend/src/app/app.component.html"
    - "frontend/src/app/app.component.scss"
    - "frontend/src/app/app.component.spec.ts"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-04-SUMMARY.md"
  modified:
    - ".gitignore"
    - ".gitattributes"
    - ".planning/STATE.md"
    - ".planning/ROADMAP.md"

key-decisions:
  - "A Matriz B preserva 13 pins da Matriz A e troca somente Vitest 4.0.8 por 4.1.10 para remover o advisory crítico"
  - "O lockfile aprovado é identificado pelo SHA-256 FB8FE8A18EA4F72927AF5E3B506E2112F968C3BF98267DA0902EBC5BC40EA841"
  - "Os cinco pacotes com hasInstallScript formam uma allowlist de reconhecimento, não uma autorização para executar scripts"
  - "O risco moderado GHSA-frvp-7c67-39w9 foi aceito temporariamente sem npm audit fix, force ou downgrade"
  - "Routing, Material/CDK, OpenAPI, Playwright e identidade visual continuam pertencendo aos planos posteriores"

patterns-established:
  - "Supply chain npm: matriz direta exata, lockfile v3, registro oficial, SHA-512 e assinatura do grafo"
  - "Angular basal: bootstrapApplication, provideZonelessChangeDetection, strict mode e SCSS"
  - "Ambientes frontend: development legível; homolog e production otimizados com budgets equivalentes"
  - "Higiene Windows/Git: lockfile forçado para LF e diretórios gerados ignorados na raiz"

requirements-completed: [ARCH-01]

coverage:
  - id: FRONTEND-SUPPLY-CHAIN
    description: "A Matriz B e o lockfile aprovado reproduzem exatamente as dependências permitidas"
    requirement: ARCH-01
    verification:
      - kind: static
        ref: "Validador de 01-04-GATE.md: 14 diretas, lock v3, 524 entradas baixáveis, registro npm oficial, SHA-512 e hash integral"
        status: pass
      - kind: audit
        ref: "npm ls --all --package-lock-only e npm audit --audit-level=high"
        status: pass
    human_judgment: true
  - id: ANGULAR-BOOTSTRAP
    description: "O workspace inicializa um componente standalone e zoneless sem routing, SSR ou feature antecipada"
    requirement: ARCH-01
    verification:
      - kind: static
        ref: "Auditoria dos 15 artefatos e validadores Node/Git das Tasks 1 e 2"
        status: pass
    human_judgment: false
  - id: FRONTEND-BUILDS
    description: "Development, homolog e production compilam com as configurações e budgets aprovados"
    requirement: ARCH-01
    verification:
      - kind: build
        ref: "npm run build:development, npm run build:homolog e npm run build:production na Task 5"
        status: pass
    human_judgment: false
  - id: FRONTEND-SMOKE
    description: "O componente raiz é criado e renderiza o placeholder neutro da BETUFU"
    requirement: ARCH-01
    verification:
      - kind: test
        ref: "npm test -- --watch=false — 2/2"
        status: pass
    human_judgment: false

duration: "não mensurada — execução interativa com aprovações por bloco"
completed: 2026-08-11
status: complete
---

# Fase 1 Plano 04: Resumo do workspace Angular reproduzível

**Workspace Angular 22 standalone e zoneless entregue com Matriz B fixada, lockfile aprovado e três builds verificados**

## Desempenho

- **Duração:** não mensurada — o plano foi executado em blocos interativos aprovados separadamente.
- **Conclusão da implementação e provas:** 2026-08-11.
- **Fechamento documental:** 2026-08-11.
- **Blocos do plano:** 5 — três tarefas com arquivos, um checkpoint humano e uma tarefa final de instalação/provas.
- **Commits de implementação:** 3.
- **Inventário de entrega aprovado:** 15 arquivos versionáveis.
- **Caminhos únicos nos três commits materiais:** 19 — os 15 arquivos de entrega e quatro caminhos auxiliares de governança/hardening.

## Entregas

- `frontend/package.json` com sete scripts e 14 dependências diretas exatas, sem faixas de versão.
- `frontend/package-lock.json` v3 com hash integral aprovado, origens oficiais e integridades SHA-512.
- Configurações Angular/TypeScript strict para development, homolog e production, com budgets explícitos nos dois ambientes otimizados.
- Bootstrap `bootstrapApplication` standalone e zoneless, documento `pt-BR` e componente raiz neutro.
- Placeholder mínimo da BETUFU e dois testes smoke, sem routing, SSR, Material, OAuth SPA, OpenAPI, analytics ou fonte remota.
- Regras raiz para ignorar saídas geradas do frontend e preservar o lockfile em LF entre checkouts Windows/Linux.

## Commits das tarefas

1. **Task 1 — configuração do workspace Angular**: `32abc70` (`feat(01-04): add Angular workspace configuration`).
2. **Task 2 — bootstrap standalone e zoneless**: `ab7923b` (`feat(01-04): add standalone Angular bootstrap`).
3. **Task 3 — placeholder, higiene, Matriz B final e lockfile aprovado**: `9686883` (`feat(01-04): materialize approved Matrix B frontend scaffold`).

O commit `18b5996` (`docs(01-04): define Angular scaffold and npm gate`) preparou o Gate, o Plano e a estratégia de validação antes da implementação; ele não é contado entre os três commits materiais acima. Este `SUMMARY`, `STATE` e `ROADMAP` formam o fechamento documental posterior e continuam sujeitos a stage, commit e push separados.

## Evidências de verificação

| Bloco | Evidência | Resultado |
|---|---|---|
| Preflight | `@angular/cli@22.1.2` em diretório temporário, `--skip-install --skip-git`, seguido de limpeza | PASS; 21 arquivos observados, nenhum copiado automaticamente para o repositório |
| Tasks 1 e 2 | Validadores Node/Git estáticos dos dez arquivos de configuração/bootstrap | PASS |
| Matriz B | 14 pins exatos; somente Vitest alterado de 4.0.8 para 4.1.10 | PASS |
| Lockfile | v3; SHA-256 `FB8FE8A18EA4F72927AF5E3B506E2112F968C3BF98267DA0902EBC5BC40EA841`; 524 entradas baixáveis oficiais com SHA-512 | PASS |
| Grafo npm | `npm ls --all --package-lock-only`; allowlist exata de cinco caminhos `hasInstallScript` | PASS |
| Vulnerabilidades | `npm audit --audit-level=high`: 0 high, 0 critical e três ocorrências moderate do advisory aceito | PASS conforme Gate humano |
| Instalação | Validador do Gate seguido de `npm ci --ignore-scripts`, sem drift do lockfile | PASS |
| Assinaturas | `npm audit signatures` | PASS; 376 assinaturas e 108 atestações |
| Builds | development, homolog e production | PASS; development aproximadamente 1,07 MB bruto, homolog/production 94,66 kB |
| Testes | Vitest sem watch | 2/2 PASS |
| Auditoria final | 15/15 artefatos, configurações, pins, lockfile, EOL/higiene e Git limpo/sincronizado em `9686883` | PASS |

As provas materiais da Task 5 foram observadas nesta sessão interativa e registradas no Gate e neste resumo. Os logs brutos não foram versionados; por isso o Git, isoladamente, confirma os artefatos finais, mas não demonstra a ordem temporal nem os códigos de saída das operações. Nenhuma instalação, build ou teste foi reexecutado durante este fechamento.

## Arquivos criados e modificados

- `.gitignore` — ignora somente saídas geradas e caches do workspace `frontend/`.
- `.gitattributes` — força LF em `frontend/package-lock.json`, preservando o hash entre plataformas.
- `frontend/package.json` e `frontend/package-lock.json` — Matriz B direta e grafo transitivo reproduzível.
- `frontend/angular.json` — builder, três ambientes e budgets.
- `frontend/tsconfig.json`, `tsconfig.app.json` e `tsconfig.spec.json` — strict mode e limites de compilação/teste.
- `frontend/src/main.ts`, `index.html` e `styles.scss` — entrada, documento e estilo global neutro.
- `frontend/src/app/app.config.ts` e `app.component.ts` — configuração zoneless e raiz standalone.
- `frontend/src/app/app.component.html`, `.scss` e `.spec.ts` — placeholder, estilo local e smoke Vitest.

## Decisões tomadas

- A Matriz A foi rejeitada porque `vitest@4.0.8` estava na faixa crítica de `GHSA-5xrq-8626-4rwp`; a Matriz B mudou somente esse pin para `4.1.10`.
- O risco moderado `GHSA-frvp-7c67-39w9`, na cadeia exclusivamente de desenvolvimento do Angular CLI, foi aceito temporariamente sem correção forçada ou downgrade.
- A allowlist de `@parcel/watcher`, `esbuild`, `fsevents`, `lmdb` e `msgpackr-extract` reconhece metadados `hasInstallScript`; os scripts permaneceram desabilitados.
- O workspace não possui routing ou SSR porque o Plano 01-19 é dono do host e das rotas. Material/CDK, OpenAPI e Playwright permanecem respectivamente com 01-14, 01-20 e 01-21.
- `assets: []` e o placeholder neutro evitam importar branding demonstrativo do Angular ou antecipar a identidade visual aprovada.

## Desvios do plano e da rastreabilidade

- O commit histórico da Task 1, `32abc70`, materializou inicialmente a Matriz A com `vitest@4.0.8`. Após a auditoria crítica, somente o pin do Vitest foi alterado para a Matriz B no commit `9686883`. O texto final do Plano descreve o estado aprovado; esta sequência histórica fica registrada aqui para evitar leitura retrospectiva incorreta.
- As formas npm baseadas em `--prefix frontend` falharam no Windows/npm 10.9.8 procurando `package.json` na raiz. O fluxo aprovado passou a usar `Push-Location`/`Pop-Location`, restaurando o diretório e preservando o código de saída no PowerShell 5.1.
- `.gitattributes` não estava no inventário original dos 15 arquivos, mas recebeu a regra `frontend/package-lock.json text eol=lf` para impedir que checkout Windows alterasse o hash aprovado.
- `9686883` agregou a Task 3, a atualização final de `package.json`, `.gitattributes` e três documentos de planejamento. Por isso os três commits materiais somam 19 caminhos únicos, embora o produto final possua exatamente os 15 artefatos de entrega aprovados.
- Gate aprovado, lockfile final e narrativa pós-Task-5 chegaram ao histórico no mesmo commit. A ordem Gate → autorização material → `npm ci` foi observada na sessão e registrada documentalmente, mas não possui um commit intermediário independente que a prove sozinho.

## Problemas encontrados

- O primeiro lockfile da Matriz A era estruturalmente válido, mas `npm audit` encontrou a vulnerabilidade crítica do Vitest; ele foi rejeitado sem `npm audit fix`, `--force` ou downgrade silencioso.
- O padrão documentado de `npm --prefix` não funcionou com o npm 10.9.8 no Windows utilizado; a alternativa com mudança explícita de diretório foi validada.
- Cinco dependências transitivas declaram scripts de instalação. A instalação controlada manteve `--ignore-scripts`; nenhuma execução implícita foi aceita.

## Riscos residuais registrados

- O advisory moderado `GHSA-frvp-7c67-39w9` permanece na cadeia `@angular/cli` → `@modelcontextprotocol/sdk` → `@hono/node-server`. Deve ser reavaliado quando houver correção upstream compatível com a Matriz B.
- Autorizar futuramente scripts de instalação, trocar qualquer pin direto ou regenerar o lockfile reabre o Gate humano e exige nova auditoria do grafo.
- Os outputs brutos de instalação, assinaturas, builds e Vitest não são artefatos versionados; Gate e Summary são o registro canônico dos resultados observados.
- Browser real, rotas por ambiente, integração BFF/OpenAPI e identidade visual não pertencem a este scaffold e continuam cobertos por planos posteriores.

Nenhum risco residual acima representa vulnerabilidade high/critical aceita no escopo do Plano 01-04.

## Configuração manual necessária

- Usar Node.js dentro da faixa `>=22.22.3 <23.0.0` e npm `10.9.8` para reproduzir o contrato aprovado.
- Executar comandos npm a partir de `frontend/` e preservar `--ignore-scripts` enquanto não existir nova autorização explícita.
- `frontend/node_modules/`, `frontend/dist/` e `frontend/.angular/cache/` são saídas locais ignoradas e podem ser recriadas; não fazem parte da entrega versionada.

## Prontidão para o próximo plano

- O Plano 01-19 pode construir o host Angular e as rotas build-specific sobre este workspace basal.
- Os Planos 01-14, 01-20 e 01-21 continuam donos de identidade visual, cliente OpenAPI e prova browser/E2E, respectivamente.
- `ARCH-01` está coberto no escopo do P04, mas permanece pendente na rastreabilidade global porque é compartilhado por outros planos da Fase 1; `REQUIREMENTS.md` não foi alterado.
- Iniciar o Plano 01-19 exige nova proposta material e autorização explícita; este fechamento não o autoriza.

## Autoavaliação: APROVADA

- [x] Os 15 artefatos aprovados existem e estão versionados.
- [x] `package.json` contém exatamente as 14 dependências diretas e os sete scripts da Matriz B.
- [x] O lockfile v3 corresponde ao SHA-256 aprovado, usa somente o registro npm oficial e possui integridade SHA-512 em todas as entradas baixáveis.
- [x] A allowlist contém exatamente cinco caminhos e nenhum script de instalação foi autorizado na Task 5.
- [x] Bootstrap standalone/zoneless, três builds e dois testes Vitest passaram.
- [x] Os desvios históricos e as limitações da evidência Git foram registrados sem ocultação.
- [x] Git estava limpo e sincronizado em `9686883` antes deste fechamento documental.
- [x] Nenhum requisito compartilhado foi marcado globalmente como concluído.
- [x] Nenhum stage, commit ou push foi executado durante a criação deste fechamento.

---
*Fase: 01-funda-o-segura-da-plataforma*
*Concluído em: 2026-08-11*
