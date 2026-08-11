---
phase: 01-funda-o-segura-da-plataforma
plan: "03"
subsystem: local-infrastructure-and-identity
tags: [docker-compose, postgres, redis, keycloak, seaweedfs, s3, mailpit, oidc, keycloak-theme]

requires:
  - phase: 01-18
    provides: "Profiles dev/homolog/prod separados e contrato runtime sem segredos versionados"
provides:
  - "Ambiente local reproduzível com PostgreSQL, Redis, Keycloak, SeaweedFS S3 e Mailpit fixados por versão e digest"
  - "Realm betufu-local com cliente confidencial BFF, PKCE S256 e somente o papel técnico TECHNICAL"
  - "Bancos e usuários PostgreSQL separados para BETUFU e Keycloak"
  - "Tema de login Keycloak filho, offline, licenciado e validado por HTTP real"
  - "Contrato S3 genérico no backend, mantendo SeaweedFS restrito ao desenvolvimento local"
affects: [01-04, 01-05, 01-06, 01-07, 01-11, 01-14, 01-16, 01-21, 01-24, 01-26, backend, frontend, operations]

actuals:
  tasks: 3
  commits: 3

tech-stack:
  added:
    - "PostgreSQL 18.4 trixie"
    - "Redis 8.10.0 trixie"
    - "Keycloak 26.7.1"
    - "SeaweedFS 4.41"
    - "Mailpit 1.30.7"
  patterns:
    - "Toda invocação Compose usa --env-file infra/.env.example e um preflight contra variáveis herdadas"
    - "Imagens OCI usam tag e digest aprovados, portas são publicadas somente em loopback"
    - "Realm e tema possuem validadores Node nativos sem dependências adicionais"
    - "Ativos externos entram somente por origem imutável, tamanho e SHA-256 aprovados"

key-files:
  created:
    - "infra/.env.example"
    - "infra/compose.yaml"
    - "infra/postgres/init-databases.sh"
    - "infra/keycloak/realm-betufu.json"
    - "infra/keycloak/themes/betufu/login/theme.properties"
    - "infra/keycloak/themes/betufu/login/footer.ftl"
    - "infra/keycloak/themes/betufu/login/resources/css/login.css"
    - "infra/keycloak/themes/betufu/login/resources/fonts/inter-regular.woff2"
    - "infra/keycloak/themes/betufu/login/resources/fonts/inter-semibold.woff2"
    - "infra/keycloak/themes/betufu/login/resources/fonts/barlow-condensed-semibold.woff2"
    - "infra/keycloak/themes/betufu/login/resources/fonts/inter-ofl.txt"
    - "infra/keycloak/themes/betufu/login/resources/fonts/barlow-condensed-ofl.txt"
    - "scripts/validate-keycloak-realm.mjs"
    - "scripts/validate-keycloak-realm.test.mjs"
    - "scripts/validate-keycloak-theme.mjs"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-03-SUMMARY.md"
  modified:
    - ".gitattributes"
    - ".gitignore"
    - ".planning/STATE.md"
    - ".planning/ROADMAP.md"

key-decisions:
  - "SeaweedFS é somente a implementação S3 local do MVP; homologação e produção continuam sem provedor selecionado"
  - "O backend permanece desacoplado por BETUFU_S3_* e não recebe nomes específicos do SeaweedFS"
  - "Somente TECHNICAL pertence ao Keycloak nesta fase; papéis administrativos e de domínio permanecem na BETUFU/PostgreSQL"
  - "O tema estende keycloak.v2 apenas por footer.ftl e CSS, sem copiar templates de página"
  - "Inter e Barlow Condensed são auto-hospedadas com bytes e licenças preservados"
  - "Volumes locais persistem em stop/start e down sem volumes; reset destrutivo exige autorização separada"

patterns-established:
  - "Compose local: referências OCI completas, binds 127.0.0.1, volumes com name explícito e healthchecks reais"
  - "Bootstrap PostgreSQL: script POSIX LF executado pelo entrypoint, idempotente e com owners/privilégios separados"
  - "OIDC local: cliente confidencial Authorization Code + PKCE, redirects exatos e claim técnica somente no ID token"
  - "Tema Keycloak: allowlist de arquivos, hashes locais e smoke HTTP manual/same-origin"

requirements-completed: [IDEN-01, DATA-06, OPS-01, OPS-02]

coverage:
  - id: INFRA-LOCAL
    description: "Os cinco serviços locais usam imagens fixadas, volumes persistentes, healthchecks e binds de loopback"
    requirement: OPS-01
    verification:
      - kind: integration
        ref: "docker compose --env-file infra/.env.example -f infra/compose.yaml up -d --wait e provas da Task 2B"
        status: pass
      - kind: static
        ref: "docker compose --env-file infra/.env.example -f infra/compose.yaml config -q"
        status: pass
    human_judgment: false
  - id: DATA-ISOLATION
    description: "BETUFU e Keycloak usam bancos/owners separados; Redis é auxiliar e o S3 local rejeita acesso anônimo"
    requirement: DATA-06
    verification:
      - kind: integration
        ref: "rerun/assertions PostgreSQL e PUT/GET/403/persistência S3 executados na Task 2B"
        status: pass
    human_judgment: false
  - id: IDENTITY-CONTRACT
    description: "Keycloak mantém credenciais e recuperação, expõe somente TECHNICAL no contrato aprovado e preserva páginas nativas"
    requirement: IDEN-01
    verification:
      - kind: test
        ref: "node --test scripts/validate-keycloak-realm.test.mjs — 27/27"
        status: pass
      - kind: integration
        ref: "provas Keycloak/SMTP/claim da Task 2B e smoke HTTP scripts/validate-keycloak-theme.mjs"
        status: pass
    human_judgment: false
  - id: CONFIG-SEPARATION
    description: "Valores versionados são exclusivamente locais e descartáveis; ambiente herdado é bloqueado antes do Compose"
    requirement: OPS-02
    verification:
      - kind: static
        ref: "preflight PowerShell 5.1, git check-ignore e infra/.env.example"
        status: pass
    human_judgment: false

duration: "não mensurada — execução interativa com aprovações por bloco"
completed: 2026-08-10
status: complete
---

# Fase 1 Plano 03: Resumo da infraestrutura local e identidade Keycloak

**Infraestrutura local real, realm OIDC restrito e tema Keycloak offline entregues com governança humana, imagens fixadas e provas comportamentais**

## Desempenho

- **Duração:** não mensurada — o plano foi executado em blocos interativos aprovados separadamente.
- **Início da implementação:** 2026-08-10T17:27:22-03:00.
- **Conclusão da implementação:** 2026-08-10T21:28:59-03:00.
- **Fechamento documental:** 2026-08-10.
- **Tarefas materiais:** 3.
- **Commits de implementação:** 3.
- **Arquivos de entrega declarados:** 17, sem ausência ou arquivo alheio no agregado dos commits.

## Entregas

- Docker Compose local com PostgreSQL, Redis, Keycloak, SeaweedFS e Mailpit, usando as cinco referências OCI aprovadas e portas publicadas somente em loopback.
- Quatro volumes Docker com nomes reais explícitos e persistência preservada por operações não destrutivas.
- Bootstrap PostgreSQL portátil entre Windows/Linux, executado dentro do contêiner, com bancos, owners, grants e revogações separados para BETUFU e Keycloak.
- Realm `betufu-local`, cliente confidencial `betufu-bff`, Authorization Code + PKCE S256, redirects/origins exatos e somente o papel `TECHNICAL` no escopo técnico aprovado.
- SMTP exclusivamente local pelo Mailpit e conta técnica descartável para provas de desenvolvimento.
- SeaweedFS como implementação S3 somente local, com autenticação, bucket privado, acesso anônimo negado e contrato externo genericamente S3.
- Tema `betufu` filho de `keycloak.v2`, com somente `footer.ftl`, paleta sempre escura, CTA laranja e três fontes WOFF2 auto-hospedadas.
- Validadores Node sem dependências para realm, mutações inseguras, tema, hashes e recursos HTTP same-origin.

## Commits das tarefas

1. **Task 1 — fundação da infraestrutura local**: `3aab183` (`feat(01-03): add local infrastructure foundation`).
2. **Task 2 — realm e provas comportamentais**: `060d5ed` (`feat(01-03): provision and validate Keycloak realm`).
3. **Task 3 — tema Keycloak verificado**: `7008f4b` (`feat(01-03): add verified Keycloak login theme`).

O Gate documental foi aprovado antes da implementação no commit `09ca001`. Este `SUMMARY`, `STATE` e `ROADMAP` compõem o fechamento documental posterior e permanecem sujeitos a stage, commit e push separados.

## Evidências de verificação

| Bloco | Evidência | Resultado |
|---|---|---|
| Task 1 | `git check-attr`, `git check-ignore`, preflight PowerShell 5.1, Compose 5.3.1 e `config -q` | PASS |
| Task 2 — contrato | `node --test scripts/validate-keycloak-realm.test.mjs` | 27/27 PASS |
| Task 2 — realm | `node scripts/validate-keycloak-realm.mjs infra/keycloak/realm-betufu.json` | PASS |
| Task 2 — runtime | cinco serviços healthy; bootstrap/rerun/isolamento PostgreSQL; SMTP e claim; PUT/GET/403/persistência S3 | PASS na execução interativa autorizada da Task 2B |
| Task 3 — local | `node scripts/validate-keycloak-theme.mjs --local-only` | PASS |
| Task 3 — runtime | mount read-only e `node scripts/validate-keycloak-theme.mjs` contra a página OIDC real | PASS |
| Auditoria final | configuração Compose, 27 testes, realm canônico, tema local/HTTP, cinco containers e Git limpo/sincronizado | PASS |

A auditoria final não repetiu bootstrap, envio SMTP, escrita S3 nem stop/start: esses resultados foram registrados da execução material da Task 2B para respeitar a proibição de reexecutar operações mutáveis sem nova autorização. Os outputs brutos não são artefatos versionados separados; este resumo registra comandos, escopo e resultado observados na sessão interativa.

## Arquivos criados e modificados

- `.gitattributes` — força LF no bootstrap e nos OFL; trata WOFF2 explicitamente como binário.
- `.gitignore` — ignora somente `/infra/.env`, mantendo o exemplo versionável.
- `infra/.env.example` — contrato local público, descartável e explicitamente carregado pelo Compose.
- `infra/compose.yaml` — cinco serviços, healthchecks, mounts, rede e volumes locais.
- `infra/postgres/init-databases.sh` — bootstrap idempotente de bancos, roles e privilégios.
- `infra/keycloak/realm-betufu.json` — realm, cliente, SMTP, papel/claim e conta técnica locais.
- `scripts/validate-keycloak-realm.mjs` e `.test.mjs` — validação canônica e 27 cenários de regressão.
- `infra/keycloak/themes/betufu/login/` — propriedades, footer, CSS, fontes e licenças do tema.
- `scripts/validate-keycloak-theme.mjs` — validação local e HTTP do tema e seus recursos.

## Decisões tomadas

- Nenhuma imagem, versão, porta, credencial local, papel ou ativo foi escolhido silenciosamente; todos vieram do Gate 1 aprovado.
- O SeaweedFS não define a arquitetura de produção. O endpoint e as credenciais continuam sob nomes `BETUFU_S3_*`.
- Credenciais do `.env.example` são públicas e descartáveis, adequadas somente ao daemon local confiável.
- `TECHNICAL` é o único papel Keycloak desta etapa e aparece somente na claim de ID token aprovada.
- O tema não sobrescreve templates de página; recuperação, verificação e MFA permanecem fluxos nativos do Keycloak.
- A tipografia Keycloak reutiliza os mesmos bytes aprovados que o frontend consumirá posteriormente, sem novo download.

## Desvios do plano

- A Task 3 também endureceu `.gitattributes`, embora o arquivo não estivesse na lista local da tarefa. Ele já pertencia ao escopo global do plano. As regras `*.woff2 binary` e `*-ofl.txt text eol=lf` impedem que um checkout Windows altere os cinco hashes aprovados.
- Durante a inspeção inicial da Task 3B, `docker compose ... config --services` foi usado somente para listar nomes antes do preflight. O comando não exibiu valores nem alterou estado. A sequência material executou o preflight aprovado antes de qualquer restart/up.

## Problemas encontrados

- O primeiro wrapper de download não era compatível com o Windows PowerShell 5.1 disponível; nenhum byte foi aceito naquele intento. O segundo fluxo carregou a biblioteca HTTP instalada, recusou redirects, verificou todos os tamanhos/hashes em área temporária e só então moveu os cinco ativos.
- A primeira versão do validador do tema tratava um iterador como conjunto; o teste local falhou e a conversão explícita foi corrigida antes da aceitação.
- A revisão independente encontrou `outline-offset: 4px`, divergente dos `3px` da UI-SPEC; CSS e validador foram corrigidos e retestados.
- O validador foi endurecido contra contratos simulados em comentários, sobrescritas CSS posteriores, HTML não renderizado, stylesheet desabilitado, base externa e imports CSS transitivos.
- O OFL original do Barlow contém um espaço final. O byte foi preservado deliberadamente porque integra o conteúdo upstream e o SHA-256 aprovado.

## Riscos residuais registrados

- O validador HTTP ignora URLs `data:`/`blob:` porque não fazem request remoto; isso é ligeiramente mais permissivo que a leitura literal de “todo recurso same-origin”. O CSS customizado atual é fixado por hash e não contém essas URLs.
- A posição do aviso é inferida do ponto de extensão oficial `footer.ftl`; a prova visual/browser integral continua pertencendo ao Plano 01-21.
- Homologação e produção ainda não possuem provedor S3, imagens de implantação, segredos ou topologia selecionados.
- A evidência comportamental detalhada da Task 2B está resumida aqui, sem logs brutos versionados contendo ambiente ou credenciais.

Nenhum risco residual acima reabre ameaça high no escopo implementado; qualquer troca de imagem, ativo, contrato do tema ou solução S3 reabre o Gate humano correspondente.

## Configuração manual necessária

- Docker Desktop/Engine deve estar disponível localmente.
- Todo comando deve carregar `--env-file infra/.env.example -f infra/compose.yaml` e executar o preflight aprovado no mesmo processo PowerShell antes do primeiro Compose material do bloco.
- Os valores do exemplo não podem ser reutilizados em CI compartilhado, túnel, daemon remoto, homologação ou produção.
- Os containers locais permanecem executáveis pelo Compose; volumes só podem ser apagados por reset destrutivo aprovado separadamente.

## Prontidão para o próximo plano

- O Plano 01-04 pode criar o workspace Angular 22/TypeScript 6 consumindo este resumo e o ambiente local saudável.
- `IDEN-01`, `DATA-06`, `OPS-01` e `OPS-02` continuam pendentes na rastreabilidade global porque são compartilhados por outros planos da Fase 1.
- Iniciar o Plano 01-04 exige nova proposta material e autorização explícita; este fechamento não o autoriza.

## Autoavaliação: APROVADA

- [x] Gate 1 aprovado antes de qualquer implementação.
- [x] Os três commits agregam exatamente os 17 arquivos declarados pelo plano.
- [x] Compose e atributos Git passam nas verificações estáticas aprovadas.
- [x] O realm canônico passa e 27 mutações/contratos automatizados foram verificados sem falha.
- [x] Os cinco serviços ficaram saudáveis e as provas PostgreSQL, SMTP, claim e S3 passaram na Task 2B.
- [x] O tema passa localmente e contra a página OIDC real, com ativos offline e hashes aprovados.
- [x] Git estava limpo e sincronizado em `7008f4b` antes deste fechamento documental.
- [x] Nenhum requisito compartilhado foi marcado globalmente como concluído.
- [x] Nenhum stage, commit ou push foi executado durante a criação deste fechamento.

---
*Fase: 01-funda-o-segura-da-plataforma*
*Concluído em: 2026-08-10*
