---
phase: 3
slug: experiencia-esportiva-do-piloto
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-08-26
---

# Phase 3 — Validation Strategy

> Contrato de validação da experiência esportiva, reconciliado com os 17 PLANs executáveis da fase.

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
| AUTH-01 | 03-02/T2 (W2), 03-03/T1 (W3), 03-11/T1 (W11), 03-17/T1 (W17) | T-03-02-PUBLIC / T-03-03-PUBLIC / T-03-11-PUBLIC / T-03-16-PUBLIC | Visitante sem cookie acessa somente versões públicas; rascunhos e comandos continuam protegidos. | MockMvc IT + Playwright | GET anônimo, controle negativo de rascunho e navegador sem sessão. | ⬜ pending |
| SPRT-01 | 03-02/T2 (W2), 03-04/T1 (W4), 03-16/T1 (W16) | T-03-01-SCOPE / T-03-02-IDOR / T-03-04-IDOR / T-03-16-IDOR | Catálogo/local respeitam o UUID real da competição; outro escopo falha. | PostgreSQL/MockMvc IT | matriz geral/escopo alheio/escopo correto + constraints Flyway. | ⬜ pending |
| SPRT-02 | 03-05/T2 (W5), 03-06/T2 (W6), 03-08/T2 (W8), 03-09/T2 (W9) | T-03-05-IDOR / T-03-08-PUBLISH / T-03-09-CSV / T-03-09-IDOR | Cadastro individual e CSV não criam referência implícita nem escrita parcial. | unit + IT | CSV válido, referência ausente, escopo alheio e erro tardio com zero writes. | ⬜ pending |
| SPRT-03 | 03-06/T2 (W6) | T-03-06-PII | Atleta sem conta é identidade esportiva válida e não exige `identityId`. | repository/MockMvc IT | persistência e DTO público do atleta não vinculado. | ⬜ pending |
| SPRT-04 | 03-06/T2 (W6) | T-03-06-HMAC / T-03-06-PII | Matrícula é transitória e usa exatamente normalização/HMAC da Fase 2. | unit/architecture/IT | compatibilidade do seam e ausência em entity, response e log. | ⬜ pending |
| SPRT-05 | 03-06/T2 (W6) | T-03-06-PII | Banco guarda somente versão+fingerprint, sem valor bruto consultável. | Flyway/ArchUnit IT | inspeção de schema e regra de privacidade estreita. | ⬜ pending |
| SPRT-06 | 03-07/T2 (W7) | T-03-07-RACE | Correspondência única cria uma única proposta auditada nos dois sentidos. | concurrency IT | retry/idempotência para conta-primeiro e atleta-primeiro. | ⬜ pending |
| SPRT-07 | 03-07/T2 (W7) | T-03-07-ENUM / T-03-07-RACE | Ambiguidade, fingerprint ligado, múltiplos candidatos ou dados divergentes nunca produzem vínculo automático. | MockMvc/concurrency IT | `AthleteLinkProposalIT`: HMAC coincidente + dados divergentes → `BLOCKED/DIVERGENT_DATA`, zero vínculo, uma fila manual idempotente e nenhum fingerprint/PII enumerado. | ⬜ pending |
| SPRT-08 | 03-07/T2 (W7), 03-14/T2 (W14) | T-03-07-OWNER-IDOR / T-03-07-SCOPE-IDOR / T-03-14-IDOR | Somente a pessoa alvo aceita, rejeita ou contesta; concorrência tem um vencedor. | security/concurrency IT + frontend | owner/alheio, aceite concorrente e UI sem campos pessoais antes do aceite. | ⬜ pending |
| SPRT-09 | 03-08/T2 (W8) | T-03-08-PUBLISH / T-03-08-RACE | Elenco nasce rascunho, publica snapshot e alteração tardia exige motivo. | domain + IT | rascunho/publicação/correção; ausência de titular/reserva. | ⬜ pending |
| SPRT-10 | 03-02/T2 (W2), 03-04/T1 (W4), 03-10/T2 (W10) | T-03-04-INTEGRITY / T-03-10-SCOPE | Evento valida participantes, UTC+zona, fase, fonte e local/espaço coerentes. | domain + PostgreSQL IT | matriz de formato/tempo/local e constraints. | ⬜ pending |
| SPRT-11 | 03-02/T2 (W2), 03-10/T2 (W10) | T-03-01-HISTORY / T-03-10-RACE / T-03-10-AUDIT | Evento percorre `RASCUNHO/PUBLICADO/SUSPENSO/ADIADO/CANCELADO`; resultado percorre `INFORMADO/AGUARDANDO_REVISAO/CONFIRMADO/CORRIGIDO`, com versões e 409 sem sobrescrita. | lifecycle/concurrency/audit IT | `SportsEventResultIT`: publicar, suspender, adiar, cancelar, informar, revisar, confirmar e corrigir com fonte/motivo/versão/auditoria, mais saltos inválidos; `SportsEventResultConcurrencyIT`: dois editores. | ⬜ pending |
| SPRT-12 | 03-03/T1 (W3), 03-11/T1 (W11) | T-03-02-PUBLIC / T-03-11-GUARD | Agenda pública é estável, filtrável, acessível e responsiva. | query IT + Angular + Playwright | filtros/deep link, teclado, 320px/1280px, loading/empty/error. | ⬜ pending |
| SPRT-13 | 03-05/T2 (W5), 03-11/T1 (W11), 03-14/T2 (W14) | T-03-05-PII / T-03-11-PUBLIC / T-03-14-PRIV | Páginas públicas usam allowlist e estatísticas derivadas, sem PII. | DTO/MockMvc + Angular/Playwright | organização/equipe/atleta, estados parciais e inspeção DOM/storage/network. | ⬜ pending |
| SPRT-14 | 03-12/T2 (W12), 03-13/T2 (W13) | T-03-12-DERIVE / T-03-12-IDOR / T-03-13-AUDIT | Bracket deriva de eventos e classificação é snapshot manual versionado. | domain + API + accessibility | `bye`, vencedor anterior, correção de evento e leitura sem conectores. | ⬜ pending |
| SPRT-15 | 03-05/T2 (W5) | T-03-01-FAVORITE / T-03-05-MIGRATION | Favorito passa a organização sem associação incorreta nem perda do perfil. | Flyway/profile IT | migration preservadora, organização inexistente e regressão do perfil atual. | ⬜ pending |
| SPRT-16 | 03-14/T2 (W14), 03-15/T2 (W15) | T-03-14-IDOR / T-03-14-XSS / T-03-15-MEDIA / T-03-15-SSRF | Só o atleta vinculado altera texto/social/foto; mídia é processada e falha preserva anterior. | security/media IT + frontend | owner/alheio, URL segura, fixtures adversariais, remoção e DTO público. | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

## Plan Verification Commands

| Plan/Wave | Primary automated evidence |
|---|---|
| 03-01 / W1 | decisão documents gate (`approved`, IDs e forward-fix) |
| 03-02 / W2 | `SportsTracerIT`, Flyway, Modulith e API pública backend — ✅ green: focal 9/9 + 2/2, Surefire 251/251, Failsafe 342/342 e Node 623/623 |
| 03-03 / W3 | cliente gerado, Angular tracer e Playwright tracer |
| 03-04 / W4 | `SportsCatalogIT`, Angular O1 e contract gates |
| 03-05 / W5 | `SportsOrganizationIT`, `ProfilePersistenceIT` e O2/profile |
| 03-06 / W6 | athlete/HMAC compatibility, IDOR, privacy, Modulith e O3 |
| 03-07 / W7 | proposal functional/concurrency/IDOR IT, `DIVERGENT_DATA` sem enumeração e O8 |
| 03-08 / W8 | roster functional/concurrency IT e O4 |
| 03-09 / W9 | parser, atomic import/IDOR IT, full dependency tree e O5 |
| 03-10 / W10 | event/result lifecycle completo, transições inválidas, concurrency/audit IT e O6 |
| 03-11 / W11 | public view IT, Angular P1–P6 e Playwright público |
| 03-12 / W12 | bracket IT e P7/O7 |
| 03-13 / W13 | standings IT e P8/O7 |
| 03-14 / W14 | owner/XSS profile IT e perfil owner/público |
| 03-15 / W15 | adversarial media IT, S3 19/19, AWS tree e UI de foto |
| 03-16 / W16 | capabilities/authz UUID A/B e workspace O1–O8 |
| 03-17 / W17 | privacy, Modulith, mutações, suítes completas e snapshot |

---

## Mandatory Negative Mutations

- [ ] Colocar o matcher público depois de `/api/v1/**.authenticated()` deixa AUTH-01 vermelho.
- [ ] Remover a comparação de `scopeRef` ou usar apenas `hasRole` deixa SPRT-01/SPRT-02 vermelhos.
- [ ] Remover o filtro de versão publicada faz o teste anônimo falhar.
- [ ] Sobrescrever uma versão confirmada deixa o teste histórico/liquidação futura vermelho.
- [ ] Persistir antes de validar a última linha CSV deixa o teste de atomicidade vermelho.
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

- [ ] 03-03/T1 e 03-17/T1 — `frontend/playwright.sports.config.ts` e `frontend/e2e/sports-pilot.spec.ts` com 320px, 1280px, teclado e sessão ausente.
- [ ] 03-09/T2 — `backend/src/test/resources/sports/csv/` com válido, erro tardio, BOM/encoding, quoting, duplicata, referência ausente e limites.
- [ ] 03-15/T2 — `backend/src/test/resources/sports/media/` com válida, MIME falso, truncada, pixels excessivos, metadados e falha de storage.
- [ ] 03-06/T2 — compatibilidade da porta HMAC e extensão estreita de `PrivacyArchitectureTest`.
- [ ] 03-02/T2, 03-04/T1, 03-05/T2, 03-06/T2, 03-07/T2, 03-08/T2, 03-10/T2, 03-12/T2, 03-13/T2, 03-14/T2 e 03-15/T2, com reconciliação no 03-17/T1 — atualização incremental de `FlywayMigrationIT`/checksum para V9–V19. **V9: ✅ green no 03-02; V10–V19 permanecem pendentes.**
- [x] 03-02/T1 — teste de existência de competição ao conceder papel escopado. **Provado por `rejectsGrantForUnknownCompetition()`.**
- [ ] 03-03/T2 a 03-15/T3, com inventário/digest final no 03-17/T1 — gate do gerador OpenAPI e checkpoint humano de inventário/digest.

Esses itens não precisam estar todos no primeiro plano. Cada PLAN deve possuir a fixture/gate antes da task que depende dela.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Fidelidade visual final e clareza dos fluxos públicos/operacionais | AUTH-01, SPRT-12–16 | julgamento visual complementa, mas não substitui, acessibilidade automatizada | navegador real em 320px e 1280px conforme `03-UI-SPEC.md`; registrar evidência por superfície. |
| Aprovação de inventário/digest OpenAPI | requisitos com contrato HTTP | decisão humana governada do artefato gerado | comparar arquivos adicionados/removidos, campos e digest; recusar PII ou serviço inesperado. |
| Dependência CSV e/ou imagem | SPRT-02, SPRT-16 | instalação/árvore/licença são decisão material | apresentar coordenada, versão, licença, transitivos, alternativa e limites antes de editar `pom.xml`. |
| Primeiro DDL esportivo e migração do favorito | SPRT-01, SPRT-15 | one-way/costly | revisar ER/DDL, mapeamento de dados, forward-fix e invariantes antes da migration. |

---

## Validation Sign-Off

- [ ] Todos os PLANs/tasks possuem `<automated>` ou dependência explícita de Wave 0.
- [ ] Nenhuma sequência de três tasks fica sem feedback automatizado.
- [ ] Todos os caminhos `File Exists: ❌` recebem fixture/gate antes do uso.
- [ ] Nenhum comando usa watch mode.
- [ ] Testes focais são repetidos pelo orquestrador; testes do worker não certificam entrega.
- [ ] Suítes agregadas e Playwright material estão atribuídos a cada onda aplicável.
- [ ] Mutações negativas são discriminantes e restauradas por hash.
- [ ] `nyquist_compliant: true` somente depois de os PLANs preencherem IDs, comandos e arquivos reais.

**Approval:** ready for plan checker
