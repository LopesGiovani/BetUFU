---
phase: 02-contas-verifica-o-e-opera-es-governadas
verified: 2026-08-26T20:25:42Z
status: passed
score: 5/5 must-haves verified
behavior_unverified: 0
overrides_applied: 5
overrides:
  - must_have: "PROF-01 inclui edição de equipe favorita"
    reason: "Equipe favorita depende do catálogo autoritativo de equipes, criado na Fase 3; a Fase 2 entrega apelido e preserva favoriteTeamId nulo sem inventar UUID."
    accepted_by: "responsável do projeto"
    accepted_at: "2026-08-19T00:00:00Z"
  - must_have: "PROF-02 entrega perfil público completo de terceiros"
    reason: "A Fase 2 entrega e prova a metade de privacidade; equipe, posição, conquistas e consumidores públicos nascem nas Fases 3, 6 e 7."
    accepted_by: "responsável do projeto"
    accepted_at: "2026-08-19T00:00:00Z"
  - must_have: "VERI-09 bloqueia diretamente apostas e ranking"
    reason: "A Fase 2 entrega o predicado público e testado de matrícula ativa; os consumidores de apostas e ranking pertencem às Fases 5 e 6."
    accepted_by: "responsável do projeto"
    accepted_at: "2026-08-22T00:00:00Z"
  - must_have: "OPER-02 ativa operações por competição atribuída"
    reason: "Modelo, invariantes de scopeRef e autorização fail-closed foram entregues; catálogo e seletor de competição foram explicitamente atribuídos à Fase 3."
    accepted_by: "responsável do projeto"
    accepted_at: "2026-08-25T00:00:00Z"
  - must_have: "OPER-04 ativa operações de moderação em chat e denúncias"
    reason: "A Fase 2 prova a fronteira negativa e impede capacidades indevidas; chat, denúncias e o escopo autoritativo do moderador pertencem à Fase 7."
    accepted_by: "responsável do projeto"
    accepted_at: "2026-08-25T00:00:00Z"
---

# Fase 2: Contas, Verificação e Operações Governadas — Relatório de verificação

**Objetivo da fase:** Pessoas conseguem administrar com segurança contas BETUFU e o registro opcional da matrícula UFU, enquanto operadores atuam apenas nos escopos e dados privados para os quais possuem autorização.
**Snapshot verificado:** `ccbef1094b34343fea8cdbecd23a823c6d50be0c`
**Verificado em:** 2026-08-26T20:25:42Z
**Status:** `passed`

## Conclusão

A Fase 2 atingiu seu objetivo reconciliado de MVP. As cinco verdades do ROADMAP possuem implementação, wiring e prova comportamental. Cinco partes literais de requisitos dependem de domínios que ainda não existem; os adiamentos aprovados estão explícitos no frontmatter e não foram convertidos artificialmente em funcionalidades vazias ou UUIDs livres.

## Objetivo alcançado

### Verdades observáveis

| # | Verdade | Status | Evidência |
|---:|---|---|---|
| 1 | A pessoa cria conta no Keycloak, confirma e-mail, mantém/encerra sessão, recupera acesso e atualiza o perfil sem entregar senha à BETUFU. | ✓ VERIFIED | Realm e BFF usam fluxo hospedado; `ProfileController`/`BetufuProfileService` governam o perfil. A cadeia real D-40 encerra sessões após reset. Testes Keycloak, perfil, logout e `PasswordResetSessionTerminationIT` exercitam os fluxos; S1/S2 foram aprovados no navegador. |
| 2 | A pessoa registra matrícula voluntariamente, recebe conflito compreensível e consulta seu próprio estado. | ✓ VERIFIED | `EnrollmentController` e `EnrollmentService` implementam GET/PUT; índices únicos e `EnrollmentAttemptLimiter` governam corrida/abuso. `EnrollmentControllerIT`, `UfuEnrollmentUniquenessIT` e S3 exercitam sucesso, duplicidade, limites e estados. |
| 3 | Matrícula original não é consultável: só o HMAC versionado persiste e nenhum dado sensível cruza resposta, URL, log ou telemetria. | ✓ VERIFIED | Migration V5 persiste digest binário + versão, sem coluna da matrícula; DTOs usam allowlist. `PrivacyArchitectureTest`, auditoria, contrato e canários de logs/URL provam ausência de matrícula, HMAC e chaves. |
| 4 | A plataforma usa HMAC não público para unicidade e comunica elegibilidade sem revelar informação privada. | ✓ VERIFIED | `MatriculaHmac`/`EnrollmentService` consultam todas as versões ativas e o índice parcial arbitra concorrência. `EnrollmentEligibility` expõe somente estado/predicado; rotação, concorrência e três estados possuem testes. |
| 5 | Administração de papéis é global/escopada, fail-closed e auditada; cada superfície recebe somente capacidades autorizadas. | ✓ VERIFIED | `RoleGrantService`, `OperationalIdentityLookup`, contributor de authorities, `SecurityConfiguration`, painel e capabilities validam papel/escopo em backend. `RoleAuthorizationMatrixIT`, `RoleGrantControllerIT`, `OperationalPanelIT`, `OperationalCapabilitiesIT` e S4–S7 exercitam separação, 403, ausência de blocos e encerramento de sessões. |

**Pontuação:** 5/5 verdades verificadas.

### Artefatos obrigatórios

| Artefato | Esperado | Status | Detalhes |
|---|---|---|---|
| `identity/internal/BetufuProfileService.java` e controllers/DTOs | Perfil próprio governado e privado | ✓ EXISTS + SUBSTANTIVE | Persistência, cooldown, idempotência e allowlist de resposta. |
| `verification/internal/EnrollmentService.java` e migration V5 | Registro HMAC único | ✓ EXISTS + SUBSTANTIVE | Busca multiversão, estados, unicidade e auditoria sem dado cru. |
| `verification/EnrollmentEligibility.java` | Contrato público mínimo | ✓ EXISTS + SUBSTANTIVE | Retorna somente elegibilidade/estado; implementação interna fica encapsulada. |
| `identity/internal/RoleGrantService.java` e `platform/identity/OperationalIdentityLookup.java` | Papéis e escopos autoritativos | ✓ EXISTS + SUBSTANTIVE | Invariantes de papel global/escopado, autorização atual e contrato sem ciclo Modulith. |
| `platform/operations/**` | Painel e capabilities governados | ✓ EXISTS + SUBSTANTIVE | Blocos não autorizados ficam ausentes no servidor; payload de capabilities não expõe identidade/papel. |
| `platform/session/passwordreset/**` e provider Keycloak | Recuperação D-40 real | ✓ EXISTS + SUBSTANTIVE | HMAC, replay window, inbox, retry, retenção, recovery administrativo e encerramento de todas as sessões. |
| `audit/**` e migrations de auditoria | Trilha append-only minimizada | ✓ EXISTS + SUBSTANTIVE | Ator, alvo, ação, estado, motivo/correlação; roles de runtime não alteram/apagam. |
| `frontend/src/app/features/{profile,enrollment,operations}/**` | S2–S7 governadas | ✓ EXISTS + SUBSTANTIVE | Signals/standalone, guards, dialogs acessíveis, fail-closed por capabilities e layouts responsivos. |

**Artefatos:** 8/8 verificados.

### Ligações essenciais

| Origem | Destino | Como | Status | Detalhes |
|---|---|---|---|---|
| Token OIDC | Identidade local | `(issuer, subject)` exatos | ✓ WIRED | `OperationalIdentityLookupService` nunca cria identidade durante consulta operacional. |
| Matrícula recebida | PostgreSQL | normalização → HMAC versionado → índice único | ✓ WIRED | Número cru é transitório; colisão/duplicidade vira recusa auditada. |
| Papel vigente | Spring Security e serviço | contributor de authorities + consulta autoritativa | ✓ WIRED | Guardas declarativa e transacional são independentes e testadas. |
| Reset no Keycloak | Sessões BETUFU | outbox → HMAC → inbox → termination | ✓ WIRED | E2E real discrimina imagem sem provider e rejeita Bearers/assinaturas inválidas. |
| OpenAPI | Cliente Angular | gerador oficial + digest aprovado | ✓ WIRED | Contratos de perfil, matrícula, painel, papéis, correção e capabilities são consumidos pelos adapters. |
| Capabilities | Navegação S7 | endpoint mínimo → store/guard/menu | ✓ WIRED | Capability ausente/desconhecida e 403 falham fechados e removem a superfície privilegiada. |

**Ligações:** 6/6 verificadas.

## Cobertura dos requisitos

| Requisito | Status | Evidência ou qualificador |
|---|---|---|
| AUTH-02 | ✓ SATISFIED | Cadastro hospedado no Keycloak; senha nunca entra na aplicação. |
| AUTH-03 | ✓ SATISFIED | Verificação de e-mail, login, persistência de sessão e logout do dispositivo exercitados. |
| AUTH-04 | ✓ SATISFIED | Fluxo de reset Keycloak e cadeia D-40 encerram sessões reais. |
| PROF-01 | PASSED (override) | Apelido editável entregue; equipe favorita aguarda catálogo da Fase 3. |
| PROF-02 | PASSED (override) | Privacidade negativa está provada; superfície pública completa nasce com os domínios consumidores. |
| VERI-06 | ✓ SATISFIED | Só digest HMAC versionado persiste; matrícula original não é consultável. |
| VERI-07 | ✓ SATISFIED | Índices e serviço bloqueiam segunda associação, inclusive concorrente. |
| VERI-09 | PASSED (override) | Predicado público de matrícula ativa entregue; apostas/ranking o consumirão nas Fases 5/6. |
| VERI-11 | ✓ SATISFIED | Fluxo nunca solicita credencial ou SSO oficial UFU. |
| OPER-01 | ✓ SATISFIED | Administrador geral concede/revoga os três papéis com escopo e auditoria. |
| OPER-02 | PASSED (override) | Modelo/invariantes/fail-closed entregues; catálogo e operação por competição entram na Fase 3. |
| OPER-03 | ✓ SATISFIED | Administrador geral governa papéis/configurações e as capabilities não concedem apostas. |
| OPER-04 | PASSED (override) | Fronteira negativa impede dados/capacidades indevidos; moderação real entra na Fase 7. |
| OPER-06 | ✓ SATISFIED | Painel entrega pendências/estados permitidos por papel sem enumeração de pessoas. |
| IDEN-05 | ✓ SATISFIED | Comando sensível exige papel vigente, audita e invalida sessões; MFA não condiciona comando. |
| PRIV-01 | ✓ SATISFIED | Arquitetura, DTOs, URLs, logs e telemetria proíbem matrícula/HMAC/tokens/chaves. |
| AUDT-01 | ✓ SATISFIED | Eventos registram ator, papel/escopo, alvo, ação, estados, motivo, horário e correlação sem HMAC. |
| AUDT-02 | ✓ SATISFIED | Auditoria usa privilégio append-only; retenção do MVP é a vida do ambiente, sem exclusão automática. |

**Cobertura:** 18/18 requisitos satisfeitos ou aceitos explicitamente como entrega fase-local.

## Auditoria da qualidade dos testes

| Grupo de testes | Requisitos ligados | Ativos | Desabilitados | Circular | Nível de asserção | Veredito |
|---|---|---:|---:|---|---|---|
| Perfil e privacidade | PROF-01/02, PRIV-01 | sim | 0 | não | valor, contrato e integração | suficiente |
| Matrícula/HMAC/rotação | VERI-06/07/09/11 | sim | 0 | não | concorrência, valor e integração PostgreSQL/Redis | suficiente |
| Papéis/capabilities/painel | OPER-01..04/06, IDEN-05 | sim | 0 | não | matriz comportamental e mutações discriminantes | suficiente |
| Auditoria/correção | AUDT-01/02 | sim | 0 | não | valor, imutabilidade e privilégio real | suficiente |
| Recuperação D-40 | AUTH-04, IDEN-05 | sim | 0 | não | E2E Keycloak/backend e controles negativos | suficiente |
| Angular S1–S7 | objetivo integrado | sim | 0 | não | componente, teclado, foco, viewport e navegador real | suficiente |

**Testes desabilitados vinculados a requisitos:** 0.

**Padrões circulares:** 0.

**Asserções insuficientes:** 0 bloqueantes.

Relatórios XML persistidos no checkout registram Surefire `251/251` e Failsafe `332/332`, zero falhas/erros/skips; o módulo Keycloak registra `59/59`. O checkpoint final registra Angular `220/220`, Node `623/623`, build de produção e Playwright S1–S7. Não houve alteração de produção entre o snapshot testado `a0b5a20` e o HEAD verificado.

## Cobertura das decisões

O gate `check.decision-coverage-verify` encontrou **28/28** decisões do `02-CONTEXT.md` nos artefatos entregues. Os adiamentos de equipe favorita, perfil público completo, consumidores de elegibilidade, catálogo de competição e moderação estão documentados acima como overrides, não como silêncio.

## Antipadrões e observações

| Local | Observação | Severidade | Impacto |
|---|---|---|---|
| `.planning/REQUIREMENTS.md` | A rastreabilidade ainda marca os 18 requisitos como `Pending`. | ⚠ Warning documental | O relatório canônico corrige a leitura do GSD; a tabela deve ser reconciliada separadamente. |
| `.planning/ROADMAP.md` | O campo calculado interno ainda imprime `28/32 plans executed`, embora existam 32 PLANs e 32 SUMMARYs. | ⚠ Warning documental | Não afeta código nem veredito; é manutenção de índice. |
| Escopos futuros | Catálogo de competição, moderação, equipe favorita e consumidores de aposta/ranking não existem ainda. | ℹ Info | Decisão deliberada: fail-closed agora, implementação nas fases proprietárias. |

**Antipadrões de implementação:** nenhum blocker ou warning funcional encontrado.

## Verificação humana

Já concluída no Plano 02-27. O responsável aprovou literalmente o snapshot integrado após percorrer S1–S7 em desktop e 320 px, teclado, foco, privacidade, capabilities fail-closed e stale 403. Não há nova ação humana necessária.

## Lacunas

**Nenhuma lacuna bloqueante encontrada.** Os cinco overrides permanecem compromissos rastreáveis das fases futuras, e não habilitam comportamento inseguro na Fase 2.

## Metadados

- **Abordagem:** goal-backward, iniciando pelos cinco critérios do ROADMAP.
- **Fontes:** ROADMAP, REQUIREMENTS, CONTEXT, 32 PLANs, 32 SUMMARYs, validação, código, relatórios XML e checkpoint humano final.
- **Verificações automatizadas consideradas:** Java backend/Keycloak, Node, Angular, builds, validadores, mutações discriminantes e E2E real.
- **Verificações humanas pendentes:** 0.

---
*Verificado em 2026-08-26T20:25:42Z pelo Codex, em modo somente leitura sobre a implementação.*
