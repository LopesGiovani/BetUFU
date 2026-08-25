---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 12
subsystem: enrollment-correction
tags: [enrollment, authorization, audit, privacy, concurrency]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: vínculo UFU do 02-07, papéis do 02-23/02-17, auditoria do 02-21 e painel do 02-10
provides:
  - Correção administrativa transacional de matrícula por identificador público
  - Preservação do vínculo anterior como LIBERADO e registro do novo vínculo sob a chave corrente
  - Endpoint POST exclusivo de ADMINISTRADOR_GERAL com CSRF e atividade válida
  - Auditoria integral do motivo sem número de matrícula, digest ou versão de chave na resposta
affects: [02-20, 02-25, VERI-07, OPER-01, AUDT-01]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - TransactionTemplate obrigatório para substituição atômica do vínculo
    - Bloqueio pessimista da linha existente e índices únicos parciais como árbitro final
    - Contrato OperationalIdentityLookup para resolver o ator sem criar ciclo Modulith
    - Matcher HTTP exato antes da regra ampla e prova por mutação discriminante

key-files:
  created:
    - backend/src/main/java/br/com/betufu/verification/internal/EnrollmentCorrectionController.java
    - backend/src/main/java/br/com/betufu/verification/internal/EnrollmentCorrectionRequest.java
    - backend/src/main/java/br/com/betufu/verification/internal/EnrollmentCorrectionResponse.java
    - backend/src/main/java/br/com/betufu/verification/internal/EnrollmentCorrectionService.java
    - backend/src/test/java/br/com/betufu/verification/EnrollmentCorrectionAuthorizationIT.java
    - backend/src/test/java/br/com/betufu/verification/EnrollmentCorrectionIT.java
  modified:
    - backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java
    - backend/src/main/java/br/com/betufu/platform/session/ValidActivityFilter.java
    - backend/src/main/java/br/com/betufu/verification/internal/UfuEnrollmentRepository.java
    - backend/src/test/java/br/com/betufu/identity/RoleAuthorizationMatrixIT.java

key-decisions:
  - "A correção recebe a conta alvo por UUID público; a matrícula existe somente no corpo da requisição."
  - "Motivo aparado com ao menos 10 caracteres e no máximo 1024 octetos UTF-8 é validado antes de qualquer escrita."
  - "A nova matrícula é comparada sob todas as versões HMAC ativas antes da liberação do vínculo anterior."
  - "Liberação, inserção e auditoria permitida compartilham uma transação; colisão tardia reverte tudo e registra a recusa depois do rollback."
  - "Somente ADMINISTRADOR_GERAL atravessa o matcher POST exato; CSRF permanece ativo."

requirements-completed: [VERI-07, OPER-01, AUDT-01]

coverage:
  - id: D1
    description: A substituição preserva histórico e nunca deixa a conta sem vínculo por falha parcial.
    requirement: VERI-07
    verification:
      - kind: integration
        ref: EnrollmentCorrectionIT
        status: pass
    human_judgment: false
  - id: D2
    description: Colisões e corridas preservam o vínculo anterior e deixam um único vencedor.
    requirement: VERI-07
    verification:
      - kind: integration
        ref: EnrollmentCorrectionIT com PostgreSQL real
        status: pass
    human_judgment: false
  - id: D3
    description: Somente o administrador geral alcança a operação e recusas não revelam papel ou área.
    requirement: OPER-01
    verification:
      - kind: integration
        ref: EnrollmentCorrectionAuthorizationIT e RoleAuthorizationMatrixIT
        status: pass
    human_judgment: false
  - id: D4
    description: A trilha guarda o motivo integral e referências opacas, nunca matrícula ou digest.
    requirement: AUDT-01
    verification:
      - kind: integration
        ref: EnrollmentCorrectionIT
        status: pass
    human_judgment: false
---

# Plano 02-12 — Correção administrativa de matrícula

## Verification Evidence

- Snapshot final: Surefire `246/246` em `22` classes, Failsafe `317/317` em `42` classes e Node `617/617`, sem falhas, erros ou testes ignorados.
- A revisão Codex somente leitura terminou em `PASS`, sem achados P0, P1 ou P2.
- A allowlist final reconciliou `10/10` paths, sem arquivo externo nem entrada planejada ausente.
- Mutação transacional: liberar o vínculo antes da verificação tornou `EnrollmentCorrectionIT` vermelho com `6` falhas em `11` testes; a restauração foi confirmada pelo SHA-256 `D891745B…`.
- Mutação de autorização: mover o matcher exato para depois de `/api/v1/**` tornou `EnrollmentCorrectionAuthorizationIT` vermelho (`1` falha em `2` testes, administrador de competição recebendo `200`); a restauração foi confirmada pelo SHA-256 `EB7F2536…`.

## What Was Built

`POST /api/v1/operations/enrollments/correction` permite ao Administrador Geral corrigir o vínculo UFU de uma conta identificada por UUID público. A matrícula nova viaja somente no corpo, e a resposta devolve apenas o alvo, o estado, o instante e se havia vínculo anterior liberado.

O serviço valida o motivo e a conta alvo, normaliza a matrícula e calcula candidatos sob todas as versões HMAC ativas antes de tocar no vínculo existente. Depois, uma única transação bloqueia o vínculo ativo, marca-o como `LIBERADO`, insere o novo como `ATIVO` sob a chave corrente e grava a auditoria permitida. Os índices únicos parciais do PostgreSQL continuam sendo o árbitro final de corridas.

## Security and Privacy

- O matcher POST exato vem antes de `/api/v1/**` e exige `ADMINISTRADOR_GERAL`.
- CSRF continua ativo; os testes usam token CSRF real do suporte Spring Security.
- O ator vem do par OIDC autenticado `(issuer, subject)` resolvido por `OperationalIdentityLookup`, nunca do corpo.
- Pessoa sem papel, Administrador de Competição e Moderador recebem `403` genérico; o corpo não informa papel, matrícula, correção nem existência da área.
- Matrícula e digest não aparecem na resposta nem nas colunas textuais da auditoria.

## Decisions Made

- O motivo precisa ter pelo menos 10 caracteres após `trim` e no máximo 1024 octetos UTF-8, igual à constraint real da auditoria. Isso evita tanto justificativa inútil quanto erro interno por texto grande demais.
- Corrigir para a matrícula já ativa da própria conta é operação inócua: não cria histórico artificial, não libera a linha e registra a tentativa permitida com referências iguais.
- A auditoria de sucesso participa da transação principal. Uma recusa detectada antes dela ou após rollback é registrada numa transação própria iniciada pelo `AuditRecorder`.
- Nenhuma migration foi alterada: os índices e constraints existentes já expressam as invariantes necessárias.

## Deviations from Plan

- O plano descrevia cinco cenários mínimos; `EnrollmentCorrectionIT` terminou com onze testes para cobrir também rotação de chave, corrida real, operação inócua, histórico sucessivo, limite UTF-8 e allowlist estrutural da resposta.
- Nenhuma dependência, tabela, variável de ambiente ou path fora da allowlist foi acrescentado.

## Issues Encountered

- O encerramento da suíte agregada demorou porque threads antigas de Hikari/Lettuce tentaram reconectar a containers já encerrados. O Maven precisou finalizar o fork após 30 segundos, mas terminou com código `0`, `BUILD SUCCESS` e XMLs sem falha; o ruído não afetou os testes.
- O primeiro comando de leitura dos relatórios apontou por engano para `target/` na raiz. A leitura correta em `backend/target/` produziu as contagens registradas acima; nenhum arquivo foi alterado pelo erro de caminho.

## User Setup Required

Nenhuma configuração adicional. A operação reutiliza PostgreSQL, HMAC de matrícula, sessão OIDC, papéis operacionais e auditoria existentes.

## Next Phase Readiness

- O Plano 02-25 pode publicar o contrato OpenAPI e regenerar o cliente da correção.
- O Plano 02-20 pode construir a superfície S6 depois de o contrato/digest do 02-25 ser aprovado.
- A matrícula anterior continua irrecuperável por desenho; a UI nunca deve prometer exibi-la.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-25*
