# Auditoria de Cobertura de Fontes — Fase 1

Todos os itens obrigatórios estão cobertos. `Ideias adiadas` e capacidades explicitamente atribuídas às fases 2–7 são exclusões deliberadas, não lacunas.

## Multi-Source Coverage Audit

| SOURCE | ID | Feature/Requirement | Plan | Status | Notes |
|---|---|---|---|---|---|
| GOAL | — | Operadores executam/evoluem plataforma segura, modular, com sessão protegida, dados confiáveis e contratos | 05, 21, 28, 16 | COVERED | Vínculo backend + tracer não-prod + ausência production + gate integral. |
| REQ | ARCH-01 | Monorepo e stack aprovada/fixada | 01, 02, 04, 14, 17, 19, 27, 16 | COVERED | Build, scaffold e UI foram recortados sem alterar a stack. |
| REQ | ARCH-02 | Monólito Spring Modulith com dez limites | 09, 16 | COVERED | Package metadata + verify. |
| REQ | ARCH-03 | Gate de dependência; sem microsserviços/Kafka/Kubernetes | 23, 16 | COVERED | ArchUnit fail-first sobre módulos declarados no 09. |
| REQ | IDEN-01 | Responsabilidades Keycloak/BETUFU | 17, 03, 07, 26, 16 | COVERED | OIDC hospedado + domínio local. |
| REQ | IDEN-02 | Identidade estável `(iss, sub)` | 05, 07, 16 | COVERED | Migration/unique/upsert. |
| REQ | IDEN-03 | BFF server-side; tokens fora do browser | 17, 19, 21, 06, 07, 22, 08, 26, 27, 16 | COVERED | E2E e storage assertions. |
| REQ | IDEN-04 | TTL/regeneração/logout/revogação/OIDC/cookie/CSRF/STOMP | 06, 07, 22, 08, 27, 16 | COVERED | Contrato/testes completos. |
| REQ | DATA-01 | PostgreSQL 18 autoritativo | 05, 10, 16 | COVERED | Testcontainers e proibição Redis-authority. |
| REQ | DATA-02 | Flyway versionado/validado | 05, 10, 16 | COVERED | Repeat/concurrent migrate. |
| REQ | DATA-06 | Redis auxiliar, S3 privado, bancos separados | 03, 10, 11, 16 | COVERED | Bootstrap e testes. |
| REQ | API-01 | `/api/v1`, OpenAPI, Problem Details | 20, 05, 22, 12, 26, 16 | COVERED | UTF-8/empty/concurrency. |
| REQ | API-02 | Cliente gerado e drift gate | 20, 21, 26, 15, 28, 16 | COVERED | Geração inicial, regeneração final atômica e check read-only. |
| REQ | OPS-01 | Modos daily/full reproduzíveis | 01, 03, 21, 11, 24, 16 | COVERED | Compose profiles, proxy same-origin e CLI. |
| REQ | OPS-02 | Configuração separada/sem segredo | 18, 03, 11, 24, 14, 16 | COVERED | Profiles, env examples e isolation tests. |
| REQ | OPS-03 | Health/logs/correlation/metrics seguros | 12, 25, 13, 26, 15, 28, 16 | COVERED | DTO/MDC/Micrometer/sanitizer. |
| REQ | TEST-01 | JUnit/Testcontainers/PostgreSQL real | 05, 10, 16 | COVERED | Persistence IT. |
| REQ | TEST-02 | Modulith Test/ArchUnit | 23, 16 | COVERED | Boundary gates sobre os módulos declarados no 09. |
| CONTEXT | D-01 | Estrutura inicial da interface + diagnóstico | 19, 21, 14, 27, 28, 16 | COVERED | Tracer apenas não-prod; production contém somente a estrutura inicial. |
| CONTEXT | D-02 | Diagnóstico técnico somente não-prod e autenticado | 18, 21, 26, 28, 16 | COVERED | Build/backend absence gate para diagnóstico e tracer. |
| CONTEXT | D-03 | Seis componentes/status/correlation/version/env | 13, 15, 16 | COVERED | DTO e UI exact-six. |
| CONTEXT | D-04 | Degradação parcial/retry sem detalhe interno | 13, 15, 16 | COVERED | Backend+UI+Playwright. |
| CONTEXT | D-05 | Idle TTL 8h renovável | 06, 16 | COVERED | Boundary/precision tests. |
| CONTEXT | D-06 | Sem TTL absoluto | 06, 16 | COVERED | `absoluteTimeout=null`. |
| CONTEXT | D-07 | MFA de login válido na sessão | 06, 07, 16 | COVERED | `acr`/`amr` snapshot; sem prompt por operação. |
| CONTEXT | D-08 | Sessões multi-device independentes | 06, 22, 16 | COVERED | Teste session A/B. |
| CONTEXT | D-09 | Logout apenas do dispositivo corrente | 22, 16 | COVERED | Revogação de segurança separada. |
| CONTEXT | D-10 | Dois modos locais | 11, 16 | COVERED | Daily/full. |
| CONTEXT | D-11 | Bootstrap automático/reproduzível/sem credencial real | 03, 11, 16 | COVERED | Env local + idempotência. |
| CONTEXT | D-12 | Stop/start preserva; reset separado/confirmado/escopado | 24, 16 | COVERED | Allowlist e testes. |
| CONTEXT | D-13 | Windows/Linux principais; comandos portáteis | 02, 11, 24, 16 | COVERED | Launcher Maven + orquestradores Node sem cadeia de shell. |
| CONTEXT | D-14 | Erro público em português/código/próxima ação | 12, 13, 15, 16 | COVERED | Problem Details/UI. |
| CONTEXT | D-15 | Erro de campo preserva/foca/especifica | 03, 21, 16 | COVERED | Tema herda validação nativa + Playwright contra Keycloak real. |
| CONTEXT | D-16 | Retry só consulta; mutação sem retry silencioso | 13, 15, 16 | COVERED | Adapter RxJS/tests. |
| CONTEXT | D-17 | Falha isolada preserva restante | 13, 15, 16 | COVERED | Partial UI/E2E. |
| RESEARCH | R-01 | Java 25/Node >=22.22.3/Docker daemon ausentes | 01 | COVERED | Precondition bloqueante e sem fallback. |
| RESEARCH | R-02 | Package Legitimacy Audit — seis npm `SUS` | 01 | COVERED | Gate humano antes do install. |
| RESEARCH | R-03 | Compatibilidade Boot 4.1/Modulith 2.1 incerta | 02, 17, 09, 23, 16 | COVERED | Compile, verify mínimo, módulos completos e gate. |
| RESEARCH | R-04 | Gerador anuncia suporte até Angular 21 | 20, 26, 16 | COVERED | Compile inicial, regeneração final e check read-only. |
| RESEARCH | R-05 | BFF OIDC; não SPA OAuth | 17, 21, 07 | COVERED | Authorization Code/PKCE/server tokens. |
| RESEARCH | R-06 | Sessão BETUFU distinta da sessão Keycloak | 06, 07, 22 | COVERED | Política central, revogação e logout. |
| RESEARCH | R-07 | Diagnóstico é projeção, não Actuator bruto | 13, 26 | COVERED | DTO allowlist, probes e endpoint condicionado. |
| RESEARCH | R-08 | Migrations imutáveis e PostgreSQL real | 10 | COVERED | Checksum/repeat/concurrency. |
| RESEARCH | R-09 | Compose health antes de dependentes | 03, 11 | COVERED | `service_healthy` e tests. |
| RESEARCH | R-10 | Micrometer/Actuator/correlation sanitizados | 12, 25 | COVERED | Observability e HealthMetrics ITs. |
| RESEARCH | R-11 | Atividade válida resolvida | 06, 08 | COVERED | Allowlist explícita; consulta, polling, pings/health/heartbeat excluídos. |
| RESEARCH | R-12 | Lacunas pré-execução roteadas | 01–28 | COVERED | Cada lacuna aponta ao plano responsável e ao gate agregado. |

## Cobertura suplementar obrigatória

- **UI-SPEC:** `empty`, `loading`, `error`, `populated`, `partial`, `overflow`, `zero-one-many` e `long-text` aparecem como oito truths separadas em 01-15 e entram nos gates 01-28/01-16.
- **Edge fallback:** 34/34 marcadores `[EDGE REQ/category]` aparecem exatamente uma vez: 4 de IDEN-04 em 01-06; 8 de IDEN-01/02/03 em 01-07; 2 de API-02 em 01-20; 6 de DATA em 01-10; 6 de OPS em 01-11; e 8 de API-01/OPS-03 em 01-12.
- **Assumption-delta:** scan determinístico retornou `detected:false`; nenhum checkpoint foi criado.
- **Schema push:** nenhum padrão Prisma/Drizzle/Supabase/TypeORM/Payload foi detectado. Flyway permanece coberto normalmente por 01-10; nenhum push ORM foi fabricado.
- **API coverage:** detector retornou `detected:true`; `COVERAGE.md` decide 14 capabilities OIDC (6 `INTEGRATE`, 8 `OPT-OUT` com razão) e distingue Keycloak externo de `/api/v1` interno.

## Prohibition Probe

### Itens bespoke mantidos como flagged-unverified

Os itens estão em `must_haves.prohibitions`, sem `check_*`: ausência de páginas futuras; ausência de formulários de credencial BETUFU; sem logout-all voluntário; sem microsserviços/Kafka/Kubernetes; Redis nunca autoritativo; reset estritamente local; sem vínculo visual/oficial UFU; diagnóstico e tracer ausentes do production build; sem auto-refresh que simule monitoramento; gate final nunca green com stage ignorado.

### Breadcrumbs canon — não cunhados

- Roubo de token, session fixation, CSRF, origem, XSS/encoding e segredo em telemetria são canon OWASP/ASVS — cobertos nos `<threat_model>` e destinados ao `$gsd-secure-phase`.
- Privacidade/retention genéricas são canon e/ou fases posteriores — não duplicadas como proibição de produto nesta fase.

## Exclusões válidas

- Nova confirmação de MFA por operação é `Deferred Ideas`; não foi planejada. D-07 implementa a política aprovada da sessão ativa.
- Fluxos de verificação UFU, esportes, odds, créditos, palpites, liquidação, ranking, grupos e chat pertencem às fases 2–7; somente seus limites modulares são declarados em ARCH-02.
- Provedor de nuvem/CI permanece decisão futura; 01-16 cria gate local/Linux genérico, não escolhe plataforma.

**Resultado:** 1 GOAL + 17 REQ + 12 RESEARCH + 17 CONTEXT = 47/47 linhas `COVERED`; nenhum item `MISSING`.
