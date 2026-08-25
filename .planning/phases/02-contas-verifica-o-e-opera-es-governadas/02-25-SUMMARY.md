---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 25
subsystem: enrollment-correction-contract
tags: [openapi, generated-client, problem-details, enrollment, privacy, digest]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: backend autorizado e transacional da correção entregue pelo 02-12
provides:
  - Contrato OpenAPI do POST de correção administrativa
  - Problem Details compartilhado para todas as recusas declaradas da correção
  - Cliente Angular regenerado pelo motor fixado 7.24.0
  - Manifesto final de 47 arquivos e digest aprovado
affects: [02-20, VERI-07, OPER-01, AUDT-01, API-01, API-02]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - Contrato público criado somente depois do backend provado
    - Árvore gerada reproduzível e governada por inventário e digest
    - Regras semânticas executadas antes do alarme genérico de integridade
    - Extensão OpenAPI para restrição autoritativa em octetos UTF-8

key-files:
  created:
    - frontend/src/app/api/generated/model/enrollment-correction-request.ts
    - frontend/src/app/api/generated/model/enrollment-correction-response.ts
    - frontend/src/app/api/generated/model/betufu-field-violation.ts
    - frontend/src/app/api/generated/model/betufu-problem-details.ts
  modified:
    - backend/openapi/betufu.yaml
    - frontend/src/app/api/generated/api/enrollment.service.ts
    - frontend/src/app/api/generated/model/models.ts
    - frontend/scripts/generate-api.mjs
    - scripts/validate-angular-route-boundary.test.mjs

key-decisions:
  - "As recusas 400, 401, 403, 404 e 409 referenciam um schema executável de Problem Details, não apenas uma descrição em prosa."
  - "O motivo possui minLength 10, maxLength 1024 e a extensão x-betufu-max-utf8-bytes 1024."
  - "O contrato de saída nunca contém matrícula, digest ou versão de chave."
  - "O inventário intermediário de 45 arquivos foi superado pela correção de revisão; o único manifesto final é 47 arquivos com digest 9C8D3FF3…."

requirements-completed: [VERI-07, OPER-01, AUDT-01]

coverage:
  - id: D1
    description: O OpenAPI representa o endpoint e os DTOs reais sem publicar material sensível.
    requirement: VERI-07
    verification:
      - kind: other
        ref: validate-phase1 --contract
        status: pass
    human_judgment: false
  - id: D2
    description: Todas as respostas de erro declaradas têm application/problem+json e schema compartilhado compatível com o backend.
    requirement: OPER-01
    verification:
      - kind: other
        ref: revisão Codex do OpenAPI e dos writers Spring
        status: pass
    human_judgment: false
  - id: D3
    description: A árvore TypeScript é reproduzível, aprovada por inventário/digest e não foi editada manualmente.
    requirement: AUDT-01
    verification:
      - kind: other
        ref: validate-angular-route-boundary e Node 617/617
        status: pass
    human_judgment: true
---

# Plano 02-25 — Contrato e cliente da correção administrativa

## Verification Evidence

- O contrato passou por `node scripts/validate-phase1.mjs --contract`, com todos os mappings em paridade.
- O validador de fronteira terminou verde com inventário final de `47` arquivos e digest `9C8D3FF3BF4D40B20FEC58E4C44DF7F4189D15CE36ADF23AB63614C6E06E4071`.
- Node terminou `617/617`; backend com `clean` terminou Surefire `246/246` e Failsafe `317/317`.
- O motor aprovado permaneceu OpenAPI Generator `7.24.0`; nenhum pacote ou dependência foi acrescentado.
- A primeira candidata passou de `43` para `45` arquivos, adicionando request/response da correção, e recebeu aprovação intermediária com digest `4F29D8A0D69F06602CD780325F042C15FC59CA6EE6DD177C14FD6E5B46D8FAD1`.
- A revisão invalidou essa candidata porque as respostas de erro mencionavam Problem Details somente em texto. A candidata corrigida passou de `45` para `47`, acrescentando `betufu-field-violation.ts` e `betufu-problem-details.ts`, sem remover arquivo.
- O responsável aprovou especificamente o inventário final `47` e o digest final `9C8D…` em 2026-08-25.
- Antes da fixação, o validador parou em `GENERATED_MANIFEST_PATHS`; depois de atualizar somente a lista, parou em `GENERATED_MANIFEST_DIGEST`; somente então o digest final foi gravado.
- A revisão final retornou `PASS`, sem P0, P1 ou P2.

## What Was Built

O OpenAPI agora publica `POST /api/v1/operations/enrollments/correction`. A entrada exige `targetIdentityId`, `matricula` e `reason`; a saída contém somente identidade alvo, estado, instante e o indicador de liberação do vínculo anterior.

O motivo exige no mínimo 10 caracteres. `maxLength: 1024` expressa o limite padrão em caracteres, enquanto `x-betufu-max-utf8-bytes: 1024` documenta a restrição autoritativa que o backend e o PostgreSQL aplicam em octetos UTF-8.

As respostas `400`, `401`, `403`, `404` e `409` publicam `application/problem+json` e referenciam `BetufuProblemDetails`. O schema exige `type`, `title`, `status`, `detail`, `code` e `action`; `instance`, `correlationId` e `fieldErrors` são opcionais, como no runtime Spring.

O cliente TypeScript foi regenerado integralmente. O manifesto e o barrel de modelos passaram a reconhecer os dois tipos de correção e os dois tipos de Problem Details.

## Security and Privacy

- O contrato de resposta não contém matrícula, HMAC, digest ou versão de chave.
- `additionalProperties: false` fecha os dois schemas BETUFU publicados e evita que campos não decididos entrem silenciosamente no cliente.
- Mensagens e violações de campo são descritas como públicas e sanitizadas.
- A árvore gerada continua protegida por lista exata, digest e regressões semânticas.

## Review Corrections

- P1: descrições textuais de Problem Details foram substituídas por schemas e referências reais sob `application/problem+json`.
- P2: o contrato deixou de afirmar apenas o teto em bytes e passou a representar separadamente o limite OpenAPI em caracteres e a regra adicional em octetos UTF-8.
- A correção criou dois modelos gerados, exigiu nova aprovação de inventário/digest e invalidou a primeira revisão. A árvore completa foi regenerada e retestada antes do `PASS`.

## Deviations from Plan

- O plano foi emendado durante a revisão para reconhecer que `BetufuProblemDetails` ainda não existia. Essa correção documental precedeu o snapshot final.
- A allowlist continuou com quatro entradas lógicas; a expansão `frontend/src/app/api/generated/**` terminou com quatro arquivos novos no conjunto final.
- Nenhum arquivo gerado foi editado manualmente.

## Issues Encountered

- A primeira candidata estava verde nos gates disponíveis, mas não provava o contrato de erro que alegava. A revisão cética encontrou a lacuna e forçou um schema executável.
- O PowerShell exibiu comentários UTF-8 gerados como mojibake em uma leitura; a leitura pelo Node confirmou ausência de U+FFFD e presença correta de `Título`, `Explicação` e `Ação`. O arquivo não estava corrompido.
- Avisos LF→CRLF do Git permaneceram informativos no Windows e não alteraram os blobs revisados.

## User Setup Required

Nenhuma configuração. O cliente é gerado com as ferramentas já pinadas no repositório.

## Next Phase Readiness

- O Plano 02-20 pode consumir o contrato da correção por meio do cliente gerado aprovado.
- O digest canônico é somente `9C8D3FF3BF4D40B20FEC58E4C44DF7F4189D15CE36ADF23AB63614C6E06E4071`; `4F29…` permanece apenas como registro da candidata superada.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-25*
