---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 10
subsystem: operations-panel
tags: [operational-panel, authorization, privacy, audit, spring-modulith]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: auditoria do 02-21, matrícula do 02-07 e authorities operacionais do 02-17
provides:
  - Endpoint backend do painel operacional com quatro blocos governados por papel
  - Projeções agregadas de auditoria sem valores crus nem motivo de alteração de papel
  - Contagem de contas sem matrícula por contrato consumidor que não transporta identidades
  - Ausência no servidor para blocos não autorizados e resposta vazia válida para moderadores
affects: [02-18, OPER-01, OPER-02, OPER-04, OPER-06, PRIV-01]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - Contratos pertencentes ao consumidor para preservar a direção das dependências modulares
    - Projeção de privacidade antes da fronteira do módulo
    - Blocos opcionais ausentes na serialização quando o papel não os autoriza
    - Agregado primitivo no contrato para impedir enumeração por construção

key-files:
  created:
    - backend/src/main/java/br/com/betufu/audit/AuditAggregateQuery.java
    - backend/src/main/java/br/com/betufu/audit/internal/AuditAggregateService.java
    - backend/src/main/java/br/com/betufu/platform/operations/EnrollmentAggregateLookup.java
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalPanelController.java
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalPanelService.java
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalPanelResponse.java
    - backend/src/main/java/br/com/betufu/platform/operations/package-info.java
    - backend/src/main/java/br/com/betufu/verification/internal/EnrollmentAggregateService.java
    - backend/src/test/java/br/com/betufu/platform/operations/OperationalPanelIT.java
    - backend/src/test/java/br/com/betufu/platform/operations/OperationalPanelResponseTest.java
  modified:
    - backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java

key-decisions:
  - "O endpoint é acessível aos três papéis operacionais, mas o serviço monta somente os blocos autorizados; o cliente nunca recebe bloco para esconder depois."
  - "Administrador Geral recebe os quatro blocos; Administrador de Competição recebe somente a contagem sem matrícula; Moderador recebe 200 com conjunto vazio."
  - "EnrollmentAggregateLookup pertence ao platform e devolve apenas int; verification o implementa, mantendo verification -> platform e impedindo lista de pessoas na fronteira."
  - "Alterações de papel expõem somente hasReason, enquanto o motivo integral permanece permitido apenas na projeção específica de correção administrativa de matrícula."
  - "O total de recusas soma exclusivamente as categorias decididas de duplicidade e limite de tentativas; motivos desconhecidos não contaminam o número publicado."

requirements-completed: [OPER-01, OPER-02, OPER-04, OPER-06]

coverage:
  - id: D1
    description: Cada papel recebe exatamente os blocos autorizados, decididos no servidor.
    requirement: OPER-02
    verification:
      - kind: integration
        ref: OperationalPanelIT
        status: pass
    human_judgment: false
  - id: D2
    description: Contas sem matrícula atravessam a fronteira somente como número agregado, sem coleção ou identidade.
    requirement: OPER-06
    verification:
      - kind: unit
        ref: OperationalPanelResponseTest e inspeção de EnrollmentAggregateLookup
        status: pass
    human_judgment: false
  - id: D3
    description: Projeções do painel não expõem e-mail, matrícula, HMAC, digest, segredo nem valores crus da auditoria.
    requirement: OPER-04
    verification:
      - kind: integration
        ref: OperationalPanelIT e OperationalPanelResponseTest
        status: pass
    human_judgment: false
  - id: D4
    description: O painel preserva as fronteiras do Spring Modulith sem ciclo platform para identity ou verification.
    requirement: OPER-01
    verification:
      - kind: unit
        ref: ModuleDependencyRulesTest e ModulithArchitectureTests na suíte agregada
        status: pass
    human_judgment: false
---

# Plano 02-10 — Backend do painel operacional governado

## Verification Evidence

- Snapshot final: Surefire `224/224` em `21` classes, Failsafe `282/282` em `39` classes e Node `580/580`, todos sem falhas nem erros.
- A revisão final do snapshot corrigido terminou em `PASS`, sem achados P0, P1, P2 ou P3.
- `OperationalPanelIT` exerce o endpoint real, a matriz dos três papéis, a ausência dos blocos não autorizados e a resposta `200` vazia do moderador.
- `OperationalPanelResponseTest` trava a allowlist e os tipos dos records; a contagem de contas sem matrícula é `int`, não array, coleção nem objeto que transporte identidades.
- As verificações agregadas mantiveram verdes `ModuleDependencyRulesTest`, `ModulithArchitectureTests` e `PrivacyArchitectureTest`, sem afrouxar regras arquiteturais.
- A allowlist final foi reconciliada em `11/11` paths após ratificar o `package-info.java` que publica a named interface `operations`.

## What Was Built

`GET /api/v1/operations/panel` entrega quatro blocos, sempre acompanhados do instante da consulta e do recorte temporal de sete dias:

1. alterações de papel, incluindo ações permitidas e tentativas bloqueadas;
2. recusas de matrícula, separadas em duplicidade e limite de tentativas;
3. correções administrativas de matrícula, com o motivo integral exigido pela auditoria;
4. número de contas sem matrícula ativa.

Administrador Geral recebe os quatro blocos. Administrador de Competição recebe somente o quarto. Moderador alcança o endpoint, mas recebe uma resposta válida sem blocos. Usuário autenticado sem papel operacional continua barrado pela cadeia de segurança.

Bloco não autorizado fica `null` no serviço e é omitido do JSON por `NON_NULL`. Portanto, a proteção ocorre no servidor: não há dado oculto apenas por CSS, estado desabilitado ou filtragem do frontend.

## Decisions Made

- `OperationalPanelService` resolve o par OIDC exato por `OperationalIdentityLookup`, contrato que já pertence ao módulo `platform`; não importa tipos do módulo `identity`.
- `EnrollmentAggregateLookup` também pertence ao consumidor em `platform.operations`, e `EnrollmentAggregateService` o implementa em `verification.internal`. A aresta permanece `verification -> platform`, evitando o ciclo que ocorreria com `platform -> verification`.
- O contrato de contas sem matrícula devolve somente `int`. Essa restrição impede que uma lista de pessoas atravesse a fronteira e seja futuramente exposta por engano.
- A auditoria projeta os dados antes de entregá-los ao painel. Eventos de papel transportam apenas `hasReason`; o texto cru do motivo não sai dessa projeção. Somente a correção administrativa, cujo uso operacional exige leitura integral, possui `reason`.
- A contagem de recusas reconhece exatamente duas categorias de duplicidade e uma de limite de tentativas. O `totalCount` é a soma dessas categorias aprovadas, não uma soma indiscriminada de qualquer motivo armazenado.

## Review Corrections

- O motivo cru de tentativa bloqueada de alteração de papel foi removido da resposta pública; o teste agora injeta um valor-sentinela e prova que ele não aparece no JSON.
- O total de recusas passou a somar apenas duplicidade e limite de tentativas. Um motivo não reconhecido é inserido pela fixture e comprovadamente não aumenta nenhuma categoria nem o total.
- Os testes foram fortalecidos para verificar o conteúdo serializado, a separação exata das categorias, a ausência de campos sensíveis e a diferença entre motivo permitido na correção administrativa e motivo proibido na alteração de papel.

## Deviations from Plan

- O contrato de agregado de matrícula foi declarado em `platform.operations` e publicado por `@NamedInterface("operations")`; sua implementação fica em `verification.internal`. Essa inversão preserva a direção modular já existente em vez de criar o ciclo determinístico `platform <-> verification` previsto no desenho original.
- `backend/src/main/java/br/com/betufu/platform/operations/package-info.java` foi ratificado na allowlist durante o fechamento. Ele é estrutural: sem a named interface, o Spring Modulith trataria o contrato como interno ao módulo `platform`.

## Issues Encountered

- A primeira formulação da projeção de alterações de papel carregava detalhe excessivo da auditoria. A revisão identificou o vazamento, a projeção foi reduzida a `hasReason` e uma nova prova negativa passou a discriminá-lo.
- A contagem inicial aceitava uma categoria de recusa mais ampla do que o contrato. A correção tornou a classificação fechada e adicionou uma fixture de motivo desconhecido para impedir regressão silenciosa.
- Nenhuma dependência, migration, tabela ou variável de ambiente foi acrescentada por este plano.

## User Setup Required

Nenhuma configuração adicional é necessária. O painel é somente leitura e reutiliza identidade, auditoria, papéis e matrícula já existentes.

## Next Phase Readiness

- O Plano 02-18 pode publicar o contrato OpenAPI, regenerar o cliente e construir a superfície S4 sobre este endpoint.
- O frontend deve preservar a semântica do servidor: campo ausente significa bloco fora do escopo; resposta sem blocos do moderador é sucesso válido, não erro.
- O trabalho técnico foi aprovado no snapshot local e as provas descritas aqui correspondem à árvore formalizada.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-24*
