---
phase: 02
slug: contas-verifica-o-e-opera-es-governadas
status: ready-for-execution
nyquist_compliant: true
wave_0_complete: false
evidence_state: planned-not-run
created: 2026-08-19
updated: 2026-08-22
---

# Phase 02 — Validation Strategy

> `nyquist_compliant: true` significa que todas as 67 tarefas finais possuem uma prova automatizada ou uma pré-condição automatizada vinculada. Não significa que os testes já foram escritos ou passaram. `wave_0_complete: false` e `evidence_state: planned-not-run` são intencionais: esta revisão altera somente planejamento.

## Test Infrastructure

| Property | Value |
|---|---|
| Backend | Maven/Spring tests em `backend/src/test/java`; Testcontainers somente quando o plano exige PostgreSQL, Keycloak ou Redis reais |
| Frontend | Angular/Vitest em `frontend/src/**/*.spec.ts`; axe-core para regras automatizáveis de acessibilidade |
| Contract | `backend/openapi/betufu.yaml`, `frontend/src/app/api/generated/**`, `scripts/validate-phase1.mjs --contract` e `scripts/validate-angular-route-boundary.mjs` |
| Security | testes negativos de autorização, isolamento, enumeração e logs; ASVS L1; ameaça alta bloqueia o avanço |
| Evidence destination | o `02-NN-SUMMARY.md` do plano registra comando RED, falha esperada, comando GREEN, resultado, controle negativo/mutação e aprovação humana quando aplicável |

## Nyquist Execution Contract

1. Cada tarefa produtiva começa criando ou ampliando a prova indicada abaixo e executando-a antes da implementação. A primeira execução precisa falhar pela ausência do comportamento, e não por erro de ambiente.
2. O executor aplica o controle negativo indicado: inverte temporariamente a regra de autorização/validação, remove a ligação crítica ou apresenta entrada inválida, confirma que a prova falha e restaura a implementação antes do GREEN.
3. Uma tarefa de decisão ou verificação humana não inventa teste unitário: ela executa a pré-condição automatizada do plano e grava a escolha/aprovação explícita. Uma escolha bloqueante sem solução aprovada interrompe plano e fase.
4. Nenhum resultado abaixo é evidência existente. Todos permanecem `PLANNED — NOT RUN` até a execução autorizada do respectivo plano.
5. Os comandos canônicos são os blocos `<verify><automated>` dos PLANs. Esta matriz nomeia a prova e o oráculo; se ambos divergirem, o PLAN é corrigido antes de executar.

## Final Task-to-Test Map

| Task ID | Requirements / decisions | Planned automated proof | Planned fail-first or negative control | State |
|---|---|---|---|---|
| 02-01-T1 | PROF-01; VERI-07; AUDT-02; D-32; D-44 | `PostgresSemanticsIT` mede no PostgreSQL 18.4 A-7 (regex POSIX em `CHECK`), A-5 (índice único parcial) e A-9 (trigger ruidoso versus `DO INSTEAD NOTHING`) e registra A-5/A-7/A-9 em `02-MEASUREMENTS.md` | remover o `CHECK` de A-7, retirar o predicado parcial de A-5 ou trocar o trigger ruidoso de A-9 por `DO INSTEAD NOTHING` deve tornar a respectiva prova vermelha | PLANNED — NOT RUN |
| 02-01-T2 | IDEN-05; D-49; A-12 | `PrincipalNameIndexProbeIT` produz sessão autenticada real, observa as chaves Redis, a presença ou ausência do índice de principal e se `SelectiveTouchSessionRepository` contorna `save`, acrescentando somente A-12 a `02-MEASUREMENTS.md` | sessão anônima ou contexto de segurança ausente deve ser rejeitado; a saída válida "índice ausente" permanece uma medição, não uma falha | PLANNED — NOT RUN |
| 02-01-T3 | PROF-01; VERI-07; IDEN-05; AUDT-02; D-49 | os dois probes GREEN são a pré-condição automatizada da decisão OQ-8 | probe inconclusivo ou arquitetura não aprovada bloqueia; não há conclusão por adiamento | PLANNED — NOT RUN |
| 02-02-T1 | AUTH-02; AUTH-03; AUTH-04; VERI-11; D-42..D-47 | `SelfRegistrationKeycloakIT`, `OidcRevocationKeycloakIT`, `validate-keycloak-realm.test.mjs`, `validate-runtime-prerequisites.test.mjs` e reimportação cobrem os contratos travados | mutações que liberam login sem confirmação, removem fluxo de vínculo ou quebram reimportação devem falhar | PLANNED — NOT RUN |
| 02-02-T2 | AUTH-02; AUTH-03; AUTH-04; VERI-11 | `validate-keycloak-theme` e `SelfRegistrationKeycloakIT` cobrem cadastro, confirmação, erros, foco e contrato do tema | remover feedback/foco ou expor detalhe sensível deve falhar | PLANNED — NOT RUN |
| 02-02-T3 | AUTH-02; AUTH-03; AUTH-04; VERI-11 | suítes completas mais procedimento humano reconciliam realm, runtime e tema | drift reimportado ou pendência de aprovação mantém o bloco aberto | PLANNED — NOT RUN |
| 02-03-T1 | PROF-01; PROF-02; PRIV-01; D-34..D-39 | `ProfileControllerIT`, `ProfilePersistenceIT` e `CurrentProfileResponseTest` cobrem GET/PATCH próprio, unicidade e allowlist da resposta | remover o CHECK do apelido ou acrescentar campo à resposta deve falhar | PLANNED — NOT RUN |
| 02-03-T2 | PROF-01; PROF-02; PRIV-01; D-38 | `ProblemDetailsIT`, testes de códigos estáveis e da allowlist de atividade cobrem recusas e renovação válida | aceitar valor rejeitado ou acrescentar rota indevida à allowlist deve falhar | PLANNED — NOT RUN |
| 02-04-T1 | AUDT-01; AUDT-02; D-40 | leitura automatizada de AUDT-01/AUDT-02/D-40 precede a decisão sobre digest, MFA e evidência | emenda ou significado novo sem aprovação bloqueia plano/fase | PLANNED — NOT RUN |
| 02-04-T2 | AUDT-01; AUDT-02; D-40 | leitura automatizada precede a decisão conjunta de segregação e retenção | solução que cubra apenas um lado bloqueia plano/fase | PLANNED — NOT RUN |
| 02-21-T1 | AUDT-01; AUDT-02; D-40 | `AuditEventIT`, `AuditImmutabilityIT`, `FlywayMigrationIT` e `FlywayChecksumIT` cobrem append-only, trigger, partição e minimização | remoção do trigger e ação fora do vocabulário observaram vermelho; Maven 172/172 + 161/161, Node 536/536 e gate de coluna sensível passaram | PASS — 2026-08-21 |
| 02-21-T2 | AUDT-01; AUDT-02; D-40 | verificador do procedimento cobre acesso, retenção e expurgo aprovados | revisão Codex confirmou acesso agregado, retenção do ambiente fechado, gate PRIV-05 e segregação do expurgo D-37 | PASS — 2026-08-21 |
| 02-05-T1 | PROF-01; PROF-02; UI S1 | `profile-api.adapter.spec.ts` e `profile-gate-page.component.spec.ts` cobrem estados, foco, privacidade, preservação do valor e navegação contratual | RED inicial observado; Angular agregado 69/69 e ausência de `HttpClient` direto confirmada | PASS — 2026-08-21 |
| 02-05-T2 | PROF-01; PROF-02; UI S1 | revisão humana em 320 px e desktop cobre foco, overflow, cópia, estados e ausência de navegação indevida | screenshots finais aprovados; nenhum resíduo de apelido permaneceu no banco | PASS — 2026-08-21 |
| 02-05-T3 | PROF-01; PROF-02; UI S1 | validador focal, build de produção e suítes completas cobrem a primeira rota publicada | quatro mutações observaram vermelho; validador 158/158, Node 529/529, Maven 307/307; guard do tracer preservado por hash | PASS — 2026-08-21 |
| 02-06-T1 | VERI-06; PRIV-01; D-26 | leitura automatizada das fontes precede a decisão do regex normalizado | ausência de regex literal aprovado mantém a decisão aberta e bloqueia a implementação | PLANNED — NOT RUN |
| 02-06-T2 | VERI-06; PRIV-01; D-26, D-48, D-52 | `MatriculaNormalizerTest`, `MatriculaHmacTest`, `HmacKeyFailClosedTest` e `validate-runtime-prerequisites.test.mjs` cobrem formato e chave dedicada/versionada | trocar locale, usar dado bruto, aceitar fallback ou tolerar chave ausente deve falhar | PLANNED — NOT RUN |
| 02-22-T1 | PRIV-01; VERI-06; D-48 | `PrivacyArchitectureTest` impede matrícula/digest em logger, DTO, URL e mensagem de exceção | cada um dos quatro violadores sintéticos deve tornar a regra vermelha e ser removido | PLANNED — NOT RUN |
| 02-22-T2 | PRIV-01; VERI-06; D-48, D-52 | suítes completas e verificação do procedimento cobrem versionamento, rotação, reindexação e comprometimento | incluir segredo/base64 longa ou omitir resposta a incidente deve falhar/bloquear | PLANNED — NOT RUN |
| 02-07-T1 | VERI-06; VERI-07; VERI-09; AUDT-01; D-25..D-35 | leitura automatizada precede a aprovação literal de janela, teto por conta, teto por origem e bloqueio | ausência de qualquer um dos quatro valores bloqueia as tasks seguintes; limite só por conta não conclui | PASS — aprovados 5 recusas/conta/15 min, 50/origem/1 h e bloqueio sem revelar contagem ou janela |
| 02-07-T2 | VERI-06; VERI-07; D-26, D-31, D-32, D-34 | `UfuEnrollmentUniquenessIT`, `FlywayMigrationIT`, `FlywayChecksumIT` e `PrivacyArchitectureTest` cobrem schema sem texto puro/validade, digest binário, versão, repositório e dois índices parciais | `UfuEnrollmentUniquenessIT` começa RED sem tabela/repositório; remover qualquer índice, aceitar digest fora de 32 octetos ou perder a versão deve falhar | PASS — RED inicial observado; 184 unitários e 57 integrações verdes no bloco; migration V5 e dois índices discriminados |
| 02-07-T3 | VERI-06; VERI-07; VERI-09; AUDT-01; D-30, D-31, D-35 | `EnrollmentControllerIT`, `EnrollmentAttemptLimiterIT`, `EnrollmentEligibilityTest`, `PrivacyArchitectureTest` e testes de fronteira cobrem serviço/endpoint, lookup multi-chave, auditoria, dois limites, três estados e API pública | os três testes proprietários começam RED; violar outra constraint não pode virar duplicidade, e ignorar um eixo, revelar contagem ou acessar pacote interno deve falhar | PASS — Maven 187 unitários + 196 integrações; Node 536; mutações de atomicidade e liberação em 500 observadas vermelhas; revisão final sem P0–P2 |
| 02-08-T1 | OPER-01; OPER-03; IDEN-05; D-49, D-51 | leitura automatizada precede a decisão sobre identidade oficial do seed e precondições de projeção | ausência de identidade oficial completa ou incompatibilidade com D-51 bloqueia | PLANNED — NOT RUN |
| 02-23-T1 | OPER-01; OPER-02; OPER-03; OPER-04; AUDT-01; IDEN-05; D-49, D-51 | `RoleGrantControllerIT`, `RoleChangeSessionTerminationIT`, `RoleGuardTest` e matriz de autorização cobrem grants pós-commit e encerramento de sessão. Acrescido na Wave 7: invariante papel/escopo no DTO, no serviço e como `CHECK` da `V7`, com `hasRole` de papel global exigindo `scopeRef` nulo | remover guard de autoelevação/último admin, manter sessão privilegiada, aceitar papel escopado sem escopo ou papel global com escopo deve falhar | PASS — 2026-08-23; mutações M12, M13 e M14 vermelhas; Surefire 201/201 e Failsafe 241/241 |
| 02-09-T1 | IDEN-05; OPER-01; D-22, D-24, D-50 | `IdentitySessionTerminationIT`, `IdentitySessionTerminationServiceTest`, `RedisSessionIT`, `CurrentDeviceLogoutIT`, `SessionControllerIT` e `validate-runtime-prerequisites.test.mjs` cobrem listar/revogar sessões próprias | restaurar tipo antigo de repositório, enumerar todas as sessões ou vazar token deve falhar | PLANNED — NOT RUN |
| 02-09-T2 | IDEN-05; OPER-01; D-50 | ITs GREEN são pré-condição da decisão humana sobre detector de keyspace/TTL | ausência de detector aprovado, colisão de prefixo ou TTL ausente bloqueia | PLANNED — NOT RUN |
| 02-10-T1 | OPER-06; OPER-01; OPER-02; OPER-04; D-01..D-11 | `OperationalPanelIT`, `RoleAuthorizationMatrixIT`, `AuditEventIT` e `OperationalPanelResponseTest` provam o primeiro bloco governado | autorizar papel indevido ou acrescentar PII/campo ao DTO deve falhar | PLANNED — NOT RUN |
| 02-10-T2 | OPER-06; OPER-01; OPER-02; OPER-04; D-01..D-11 | `OperationalPanelIT`, `EnrollmentControllerIT` e allowlists da resposta cobrem os quatro blocos, contagem e vazio | transformar agregado em enumeração de identidades deve falhar | PLANNED — NOT RUN |
| 02-11-T1 | IDEN-05; AUTH-04; D-40, D-50 | `PasswordResetTriggerSpikeIT` mede o comportamento real do Keycloak | resultado inconclusivo ou gatilho ausente mantém RED e bloqueia a decisão | PLANNED — NOT RUN |
| 02-11-T2 | IDEN-05; AUTH-04; D-40, D-50 | spike GREEN é a pré-condição automatizada da decisão do mecanismo | mecanismo/canal sem aprovação ou prova mantém a fase bloqueada | PLANNED — NOT RUN |
| 02-24-T1 | AUTH-04; IDEN-05; PRIV-01; D-40 | `PasswordResetProviderTracerIT` via Surefire cobre UPDATE_CREDENTIAL/password, commit/rollback, unicidade e payload mínimo da outbox | evento legado, credencial divergente, HTTP no callback, dado privado ou duplicata deve falhar | PLANNED — NOT RUN |
| 02-24-T2 | AUTH-04; IDEN-05; PRIV-01; D-40 | o mesmo teste cobre ServiceLoader, entity provider, changelog e idempotência do bootstrap | provider ausente, migration duplicada ou índice único removido deve falhar | PLANNED — NOT RUN |
| 02-24-T3 | AUTH-04; IDEN-05; PRIV-01; D-40 | o mesmo teste cobre USER/ACTION e parse exato de users/{id}/reset-password | getResourceId, path ambíguo, action/type divergente ou id vazio deve falhar | PLANNED — NOT RUN |
| 02-28-T1 | AUTH-04; IDEN-05; OPER-06; PRIV-01; D-40 | `PasswordResetDeliveryRecoveryTest` cobre claim/lease, 2 s/5 s, jitter/cap 30 s, restart e RTO <=60 s. Acrescido na Wave 7: gate de ativação fail-closed por habilitação, HTTPS com host e sem `userInfo`, path exato sem query nem fragment, key-id e chave Base64 >=32 bytes | claim duplo, timeout/cap divergente, evento perdido no restart ou ativação com endpoint fora do contrato deve falhar | PASS — 2026-08-23; mutações M1–M4, M7, M18, M20 e M21 vermelhas; módulo 56/56 |
| 02-28-T2 | AUTH-04; IDEN-05; OPER-06; PRIV-01; D-40 | o mesmo teste cobre alerta na quinta falha, FAILED retido, replay manage-realm e cleanup DELIVERED >30 d. Acrescido na Wave 7: autenticação exclusiva pelo `BearerTokenAuthenticator`, autoridade só pelo papel do cliente `realm-management`, alerta e DTO minimizados, e changeset novo criando `next_attempt_at` | retry automático após FAILED, replay sem autoridade, papel de realm homônimo aceito, vazamento de sujeito/realm/erro cru ou coluna ausente no Liquibase real deve falhar | PASS — 2026-08-23; mutações M5, M6, M8–M11, M16, M17, M19c e M19d vermelhas; módulo 56/56. **Evidência deferida:** a verificação criptográfica real do Bearer é gate do 02-29 |
| 02-30-T1 | AUTH-04; IDEN-05; PRIV-01; D-40 | `PasswordResetHermeticTracerIT` cobre envelope exato, inbox e `terminateAllSessionsFor(subject, SECURITY)`. **É simulação contratual**: monta a representação do evento e assina com as constantes do destino, sem exercitar a outbox nem o dispatcher reais. Origem e destino foram alinhados **por inspeção**, nunca por execução conjunta | tampering, payload extra, lógica de terminação duplicada ou sessão controle encerrada deve falhar | PARTIAL — 2026-08-23; inbox, unicidade e código de erro estável provados, com mutação M15 vermelha; Surefire 187/187 e Failsafe 229/229 na árvore do commit. Cadeia real **deferida ao 02-29** com aprovação do responsável |
| 02-30-T2 | AUTH-04; IDEN-05; PRIV-01; D-40 | `PasswordResetEventInboxIT` cobre migration, unique sourceEventId, concorrência e rollback recuperável | ACK antes do commit, efeito duplicado ou estado falso de sucesso deve falhar | PLANNED — NOT RUN |
| 02-30-T3 | AUTH-04; IDEN-05; PRIV-01; D-40 | os dois ITs cobrem rota/configuração dedicadas, HMAC constant-time e fail-closed sem chave | bearer/cookie substituindo HMAC, rota ampla ou segredo default deve falhar | PLANNED — NOT RUN |
| 02-31-T1 | AUTH-04; IDEN-05; OPER-06; PRIV-01; D-40 | `PasswordResetEventInboxIT` cobre current+previous, janela exata de cinco minutos e ACK idempotente | terceira chave, timestamp fora da janela, tampering ou segunda terminação deve falhar | PASS — 2026-08-23; limites 31/32 bytes, timestamp extremo e efeito único cobertos |
| 02-31-T2 | AUTH-04; IDEN-05; OPER-06; PRIV-01; D-40 | `PasswordResetDeliveryRecoveryIT` cobre crash/restart, retomada única, FAILED retido e cleanup DELIVERED >30 d | perda/duplicação após crash ou exclusão de unresolved deve falhar | PASS — 2026-08-23; claim antes do efeito, lease, restart e retenção verificados no PostgreSQL real |
| 02-31-T3 | AUTH-04; IDEN-05; OPER-06; PRIV-01; D-40 | IT e `validate-password-reset-recovery.test.mjs` cobrem recovery admin, auditoria, owner/backup e runbook | membro/operador, ausência de owner ou ativação antecipada deve falhar/bloquear | PASS — 2026-08-23; duas guardas independentes, auditoria e validador 19/19; Wave 8 217/276/56/580 e revisão externa PASS |
| 02-29-T1 | AUTH-04; IDEN-05; OPER-06; PRIV-01; D-40 | imagem pinada + `PasswordResetSessionTerminationIT` provam hosted/admin, current+previous, crash/restart e RTO antes do rollout | digest/JDK/JAR divergente, segredo em layer, sessão controle encerrada ou ativação antecipada deve falhar | PLANNED — NOT RUN |
| 02-29-T2 | AUTH-04; IDEN-05; OPER-06; PRIV-01; D-40 | evidências, runbook, secrets e rollback são pré-condições do checkpoint literal | HIGH aberta, evidência pendente ou sinal diferente de `ativar rollout D-40 02-29` bloqueia | PLANNED — NOT RUN |
| 02-29-T3 | AUTH-04; IDEN-05; OPER-06; PRIV-01; D-40 | validators realm/runtime, compose config, E2E e gates completos cobrem a única ativação produtiva. **Gates bloqueantes herdados da revisão externa da Wave 7**, ambos formalizados no fechamento daquela onda: (a) teste integrado com dispatcher e endpoint verdadeiros, provando entrega única, recusa de credencial não assinada e encerramento das sessões pela cadeia real; (b) **validação criptográfica real do Bearer da recuperação, com Keycloak real** — assinatura forjada, token expirado, issuer incorreto, audience incorreta, papel global homônimo recusado e `realm-management/manage-realm` aceito. Na Wave 7 a integração com a API autenticadora suportada foi verificada apenas **estruturalmente**, inclusive por inspeção de bytecode | listener/digest/secrets divergentes, qualquer gate vermelho, **ausência da prova E2E real ou ausência da prova criptográfica** deve falhar | PLANNED — NOT RUN |
| 02-12-T1 | VERI-07; OPER-01; AUDT-01; D-32 | `EnrollmentCorrectionIT` troca o vínculo, libera o anterior e preserva histórico/auditoria | liberar antes da confirmação, apagar histórico ou manter o anterior ativo deve falhar | PLANNED — NOT RUN |
| 02-12-T2 | VERI-07; OPER-01; AUDT-01; D-32 | `EnrollmentCorrectionAuthorizationIT`, `RoleAuthorizationMatrixIT` e testes de contrato de erro provam exclusividade do administrador geral | colocar matcher depois de regra ampla ou permitir membro/operador deve falhar | PLANNED — NOT RUN |
| 02-25-T1 | VERI-07; OPER-01; AUDT-01 | validadores de contrato/rota, cliente gerado e suítes completas mantêm a correção sincronizada | drift de endpoint, DTO ou digest deve falhar | PLANNED — NOT RUN |
| 02-13-T1 | VERI-06; VERI-07; PRIV-01; UI S3 | `enrollment-api.adapter.spec.ts`, `enrollment-page.component.spec.ts`, build e validador de rota cobrem registro opcional sem persistir a matrícula | usar storage/retry automático, deixar matrícula no DOM ou perder foco no erro deve falhar | PLANNED — NOT RUN |
| 02-13-T2 | VERI-06; VERI-07; PRIV-01; UI S3 | suíte frontend verde é pré-condição da jornada humana/axe S3 | teclado, estados ou privacidade divergentes bloqueiam aprovação | PLANNED — NOT RUN |
| 02-14-T1 | PROF-01; PROF-02; PRIV-01; D-34..D-39 | validadores de contrato/rota provam OpenAPI e cliente gerado do perfil | somente o digest deve falhar imediatamente após regenerar o cliente | PLANNED — NOT RUN |
| 02-14-T2 | PROF-01; PROF-02; PRIV-01 | validador de contrato/digest GREEN é pré-condição da aprovação humana literal | digest não aprovado bloqueia todos os consumidores Angular | PLANNED — NOT RUN |
| 02-14-T3 | PROF-01; PROF-02; PRIV-01 | validadores de contrato/rota e suítes completas comprovam o digest corrigido | arquivo fora da árvore gerada/allowlist ou digest divergente deve falhar | PLANNED — NOT RUN |
| 02-15-T1 | PROF-01; PROF-02; UI S2 | `profile-page.component.spec.ts`, suíte Angular `85/85`, build production, validador de rota `OK`, regressões Node `158/158` e revisão Codex cobrem estados, privacidade, navegação e identificador público | as correções assíncronas foram validadas; edição indevida, fallback proibido e perda de erro/estado continuam cobertos pelas regressões | PASS — 2026-08-21 |
| 02-16-T1 | VERI-06; VERI-07; VERI-09; D-25..D-35 | `UfuEnrollmentRotationIT` e `UfuEnrollmentConcurrencyIT` provam todas as versões ativas e corrida contra índice real | comparar só digest corrente ou remover índice parcial deve tornar o IT focal vermelho | PLANNED — NOT RUN |
| 02-16-T2 | VERI-06; VERI-07; VERI-09 | validadores de contrato/rota cobrem vínculo e consulta governada antes do consumo | drift de schema, DTO, status, código ou digest deve falhar | PLANNED — NOT RUN |
| 02-16-T3 | VERI-06; VERI-07; VERI-09 | validador de contrato/digest GREEN é pré-condição da aprovação humana | digest não aprovado bloqueia S5 e integrações | PLANNED — NOT RUN |
| 02-17-T1 | OPER-01; OPER-02; OPER-03; OPER-04; AUDT-01; D-01..D-21, D-49, D-51 | `RoleScopeTest`, `RoleAuthorizationMatrixIT`, `AdminSeedIdempotencyIT`, `RoleGrantControllerIT` e `OidcTechnicalAuthorityMapperTest` cobrem escopos, seed e autoridade técnica | remover escopo, aceitar papel desconhecido ou quebrar idempotência do seed deve falhar | PASS — 2026-08-23; Surefire focal 36/36, Failsafe focal 22/22; M1, M3 e M4 vermelhas e restauradas |
| 02-17-T2 | OPER-01; OPER-02; OPER-03; OPER-04; AUDT-01 | validadores de contrato/rota cobrem endpoints, DTOs mínimos e cliente gerado de papéis | endpoint sem regra, campo extra ou digest divergente deve falhar | PASS — 2026-08-23; Node 552/552 no commit isolado e paridade de contrato verde |
| 02-17-T3 | OPER-01; OPER-02; OPER-03; OPER-04; AUDT-01 | validador de contrato/digest GREEN é pré-condição da aprovação humana | digest não aprovado bloqueia painel e guards | PASS — 2026-08-23; inventário e digest `B112196A…` aprovados pelo responsável |
| 02-18-T1 | OPER-06; OPER-01; OPER-02; OPER-04; D-01..D-11 | validadores de contrato/rota provam OpenAPI e cliente gerado do painel antes do adapter | contrato ou digest divergente bloqueia o início da UI | PLANNED — NOT RUN |
| 02-18-T2 | OPER-06; OPER-01; OPER-02; OPER-04 | validador de contrato/digest GREEN é pré-condição da aprovação humana | mudança não compreendida ou digest não aprovado bloqueia a task seguinte | PLANNED — NOT RUN |
| 02-18-T3 | OPER-06; OPER-01; OPER-02; OPER-04; UI S4 | `operations-api.adapter.spec.ts`, `operations-panel-page.component.spec.ts`, build e validador de rota cobrem painel e estados vazios | renderizar para papel errado, expor PII ou quebrar retry/estado vazio deve falhar | PLANNED — NOT RUN |
| 02-19-T1 | OPER-01; OPER-02; OPER-03; OPER-04; IDEN-05; UI S5 | `role-admin-page.component.spec.ts`, `confirm-dialog.component.spec.ts`, build e validador de rota cobrem membro e operador | remover razões do guard, revelar matrícula ou alterar sem confirmação deve falhar | PLANNED — NOT RUN |
| 02-19-T2 | OPER-01; OPER-02; OPER-03; OPER-04; IDEN-05; UI S5 | suíte frontend GREEN é pré-condição da jornada humana membro/operador | cópia, teclado ou privacidade divergente bloqueia aprovação | PLANNED — NOT RUN |
| 02-20-T1 | VERI-06; VERI-07; OPER-01; PRIV-01; AUDT-01; D-32; UI S6 | `enrollment-admin-page.component.spec.ts`, build, validador de rota e suíte completa cobrem nova matrícula somente em memória, motivo integral e correção governada | persistir matrícula, exibir a anterior, truncar motivo, aceitar motivo vazio/curto ou repetir mutação deve falhar | PLANNED — NOT RUN |
| 02-26-T1 | PROF-01; PROF-02; OPER-01; OPER-02; OPER-03; OPER-04; AUTH-04; IDEN-05; D-39; D-46; UI S7 | `profile-completion.guard.spec.ts`, `session-layout.component.spec.ts`, prova de 403 real com cliente defasado, build e `validate-angular-route-boundary.test.mjs` cobrem guard global funcional, os três papéis autoritativos BETUFU/PostgreSQL, contrato real de capacidade, disclosure nativo e rotas protegidas | guard ausente/no gate incorreto, loop em S1, papel vindo do Keycloak, capacidade futura exposta, item não autorizado presente no DOM, autoridade inventada no cliente, estado defasado superando 403, causa de término inferida ou quebra de teclado/foco/320px deve falhar | PLANNED — NOT RUN |
| 02-27-T1 | PROF-01; PROF-02; VERI-06; VERI-07; OPER-01; OPER-02; OPER-03; OPER-04; OPER-06; IDEN-05; PRIV-01; UI S1..S7 | evidências GREEN dos Planos 02-20 e 02-26 são pré-condição do checkpoint humano final S1–S7, incluindo variantes reais de papel/capacidade, disclosure nativo e cliente defasado com 403 | qualquer evidência pendente, superfície, papel, teclado, foco, scroll/wrap, viewport, privacidade ou autoridade divergente bloqueia a fase e nunca é autoaprovado | PLANNED — NOT RUN |

### Handoff de cobertura S7

`02-15-T2` não foi executada nem descartada: sua cobertura planejada foi transferida para `02-26-T1`, depois que o contrato de papéis/capacidades do 02-17 e os destinos dos Planos 02-13/18/19/20 existem. O checkpoint humano final passa integralmente a `02-27-T1` e bloqueia explicitamente em `02-20` e `02-26`. A contagem agora é 67 Task IDs únicos: D-40 possui 14 tasks distribuídas entre `02-24-T1..T3`, `02-28-T1..T2`, `02-30-T1..T3`, `02-31-T1..T3` e `02-29-T1..T3`, incluindo o checkpoint final `02-29-T2`; os gates materiais internos anteriores às tasks continuam sem Task ID. `02-15-T1` está `PASS`, enquanto as 14 tasks D-40, `02-26-T1`, `02-27-T1` e as demais tasks ainda não executadas permanecem `PLANNED — NOT RUN`.

## Wave 0 Requirements and Dependencies

Wave 0 é uma preparação lógica fail-first dentro da tarefa proprietária, não uma onda de implementação já executada. Não existe plano separado nem evidência fabricada. Antes de editar produção, o executor autorizado deve criar/ampliar estes scaffolds e obter RED:

| Owning plans | Planned scaffold before production changes | Dependency unlocked |
|---|---|---|
| 02-01 | `PostgresSemanticsIT`, `PrincipalNameIndexProbeIT` | semântica PostgreSQL e enumeração indexada |
| 02-02 | `SelfRegistrationKeycloakIT`, `OidcRevocationKeycloakIT`, validadores de realm/runtime/tema e reimportação | autenticação e verificação |
| 02-03 | `ProfileControllerIT`, `ProfilePersistenceIT`, `CurrentProfileResponseTest`, `ProblemDetailsIT` e testes de allowlist | perfil backend e erros estáveis |
| 02-04, 02-21 | pré-condições das decisões D-40, `AuditEventIT`, `AuditImmutabilityIT`, `FlywayMigrationIT`, `FlywayChecksumIT` | decisões e persistência/retenção de auditoria |
| 02-05, 02-15 | specs de `ProfileApiAdapter`, gate S1, página S2 e validador de fronteira | UI S1 e S2 |
| 02-06, 02-22 | `MatriculaNormalizerTest`, `MatriculaHmacTest`, `HmacKeyFailClosedTest` e `PrivacyArchitectureTest` | núcleo HMAC e backstop estrutural de privacidade |
| 02-07, 02-16 | `UfuEnrollmentUniquenessIT` (02-07-T2); `EnrollmentControllerIT`, `EnrollmentAttemptLimiterIT`, `EnrollmentEligibilityTest` (02-07-T3); `UfuEnrollmentRotationIT`, `UfuEnrollmentConcurrencyIT` (02-16-T1) | persistência exclusiva, integração/limites/elegibilidade exclusiva, rotação e concorrência |
| 02-08, 02-17, 02-23 | pré-condição do seed, `RoleScopeTest`, `RoleAuthorizationMatrixIT`, `AdminSeedIdempotencyIT`, `RoleGrantControllerIT`, `RoleChangeSessionTerminationIT` | decisão, papéis, escopos e encerramento privilegiado |
| 02-09, 02-11, 02-24, 02-28, 02-30, 02-31, 02-29 | `IdentitySessionTerminationIT`, `RedisSessionIT`, `CurrentDeviceLogoutIT`, `SessionControllerIT`, `PasswordResetTriggerSpikeIT`, `PasswordResetProviderTracerIT`, `PasswordResetEventInboxIT`, `PasswordResetHermeticTracerIT`, `PasswordResetDeliveryRecoveryTest`, `PasswordResetDeliveryRecoveryIT`, `PasswordResetSessionTerminationIT` e validadores recovery/realm/runtime | enumeração e decisão; origem; hardening da origem; destino/tracer; hardening de inbox/operações; rollout final governado |
| 02-10, 02-18 | `OperationalPanelIT`, `OperationalPanelResponseTest`, matriz de autorização e specs do painel | painel S4 após contrato |
| 02-12, 02-25 | `EnrollmentCorrectionIT`, `EnrollmentCorrectionAuthorizationIT`, matriz de autorização e validadores de contrato/digest | correção administrativa D-32 e contrato S6 |
| 02-13, 02-19, 02-20, 02-26, 02-27 | specs S3, S5 e S6; guard global de perfil, layout/menu disclosure e mutações do validador em S7; builds; axe; e evidência automatizada antes do checkpoint | superfícies finais, navegação governada e revisão humana S1..S7 sem perda de cobertura |
| 02-14, 02-16, 02-17, 02-18, 02-25 | validadores OpenAPI/cliente/digest | consumo frontend somente após contrato aprovado |

`wave_0_complete` só poderá mudar para `true` depois que todos os scaffolds aplicáveis existirem e os REDs forem registrados nos SUMMARYs. Um teste que passa na primeira execução sem demonstrar ausência do comportamento deve ser corrigido antes da implementação.

## Wave Execution Sampling

| Wave | Plans | Required sampling before approval |
|---|---|---|
| 1 | 02-01, 02-02, 02-03 | decisões bloqueantes resolvidas quando exigidas; RED/GREEN das três fatias iniciais |
| 2 | 02-04, 02-06, 02-14 | decisões de auditoria, núcleo HMAC e contrato de perfil aprovado |
| 3 | 02-05, 02-09, 02-21 | S1, sessões próprias e auditoria append-only |
| 4 | 02-11, 02-15, 02-22 | spike/decisão D-40, S2 e privacidade estrutural/procedimento HMAC |
| 5 | 02-07 | vínculo completo e migration V5 concluídos antes da V5_1 D-40 |
| 6 | 02-08, 02-16, 02-24 | decisão OQ-4, contrato de matrícula e núcleo de origem D-40 sem ativação |
| 7 | 02-23, 02-28, 02-30 | concessão/revogação, hardening da origem e destino/tracer D-40 em arquivos disjuntos |
| 8 | 02-17, 02-31 | autoridades/seed e hardening de inbox/operações sem ativação |
| 9 | 02-10, 02-29 | painel operacional e rollout D-40 final sob gate bloqueante |
| 9 | 02-10 | backend completo do painel |
| 10 | 02-18 | contrato aprovado antes do adapter e S4 validada |
| 11 | 02-12 | correção D-32 e autorização administrativa |
| 12 | 02-13, 02-25 | S3 validada e contrato/digest da correção aprovados |
| 13 | 02-19 | S5 validada |
| 14 | 02-20, 02-26 | S6 e S7/guard/menu/validador em paralelo, sem sobreposição de arquivos |
| 15 | 02-27 | checkpoint humano final S1..S7, bloqueado nas duas fatias da Onda 14 |

### Comandos obrigatórios das Waves 6–9 para D-40

Estes comandos são evidência futura da execução aprovada, não resultados já obtidos. Cada plano possui checkpoint material próprio; 02-29 possui ainda um segundo checkpoint final antes da ativação. Nenhuma aprovação autoriza a onda seguinte ou Git.

1. **Wave 6 / 02-24 origem:** `node scripts/maven.mjs -f keycloak-password-event-listener/pom.xml test '-Dtest=PasswordResetProviderTracerIT'`.
2. **Wave 7 / 02-28 origem/recovery:** `node scripts/maven.mjs -f keycloak-password-event-listener/pom.xml test '-Dtest=PasswordResetDeliveryRecoveryTest'`.
3. **Wave 7 / 02-30 destino/tracer:** `node scripts/maven.mjs -f keycloak-password-event-listener/pom.xml package` e `node scripts/maven.mjs -f backend/pom.xml test-compile failsafe:integration-test failsafe:verify '-Dit.test=PasswordResetEventInboxIT,PasswordResetHermeticTracerIT'`.
4. **Wave 8 / 02-31 inbox/operações:** `node --test scripts/validate-password-reset-recovery.test.mjs`, `node scripts/validate-password-reset-recovery.mjs` e `node scripts/maven.mjs -f backend/pom.xml test-compile failsafe:integration-test failsafe:verify '-Dit.test=PasswordResetEventInboxIT,PasswordResetDeliveryRecoveryIT'`.
5. **Wave 9 / 02-29 imagem e E2E pré-rollout:** `slopcheck keycloak-password-event-listener/pom.xml`, package do provider, build da imagem pinada e `node scripts/maven.mjs -f backend/pom.xml test-compile failsafe:integration-test failsafe:verify '-Dit.test=PasswordResetSessionTerminationIT'`.
6. **Wave 9 / checkpoint final:** revisar a evidência e só prosseguir com o sinal literal `ativar rollout D-40 02-29`; não existe comando que substitua a decisão humana.
7. **Wave 9 / ativação e gates:** validators realm/runtime, `docker compose -f infra/compose.yaml config --quiet`, E2E `PasswordResetSessionTerminationIT`, Maven verify dos dois módulos e `node --test scripts/*.test.mjs`.

O formato `test-compile` seguido dos goals diretos de Failsafe é obrigatório nos ITs isolados; `-Dtest` seleciona Surefire e não serve como evidência de um `*IT`.

Cada onda pausa para aprovação explícita conforme `AGENTS.md`; nenhuma aprovação autoriza a onda seguinte, Git ou serviços externos.

## Phase Exit Gate

- Os 67 Task IDs acima precisam de evidência no SUMMARY proprietário; não há marcador pendente nem tarefa sem mapa.
- Todo comportamento produtivo precisa de RED, GREEN e controle negativo/mutação registrado; checkpoints precisam da pré-condição automatizada e da decisão/aprovação humana.
- Todos os testes focados passam, a suíte de regressão passa e nenhum artefato sensível aparece em logs, erros, cache, DTO ou UI.
- Nenhuma ameaça `critical` ou `high` permanece aberta sob ASVS L1.
- Os contratos OpenAPI e digests são aprovados antes dos adapters Angular dependentes.
- D-40, D-49 e D-51 não possuem caminho de conclusão por adiamento: dúvida ou alternativa não aprovada bloqueia e exige replanejamento.
- A revisão humana final de `02-27-T1`, somente após `02-20` e `02-26`, confirma UI S1-S7, responsividade, teclado, foco, contraste, estados vazios/erro, privacidade e separação por papel, sem caminho de autoaprovação.

## Approval

Esta estratégia está pronta para execução interativa, mas contém somente evidência planejada. A execução de cada plano/onda, qualquer alteração de código, dependência, banco, infraestrutura, serviço externo e qualquer operação Git exige a aprovação separada definida em `AGENTS.md`.
