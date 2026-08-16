---
phase: 01-funda-o-segura-da-plataforma
plan: "09"
subsystem: architecture-modulith-boundaries
tags: [modulith, spring, architecture, boundaries, java25, package-info]

requires:
  - phase: 01-08
    provides: "Hardening de segurança de fronteira HTTP e STOMP CONNECT"
provides:
  - "Dez limites modulares Spring Modulith explícitos para os domínios de negócio"
  - "Preservação e encapsulamento estrito do módulo identity/internal"
  - "Estrutura pronta para os gates arquiteturais e de discriminação do Plano 01-23"
affects: [identity, verification, sports, odds, betting, ledger, settlement, ranking, chat, audit, architecture]

actuals:
  tasks: 2
  implementation_paths: 10
  documentation_paths: 1
  mutations_executed: 0
  mutations_effective: 0
  external_review_rounds: 0

tech-stack:
  patterns:
    - "Spring Modulith 2.1 com anotação @ApplicationModule em package-info.java"
    - "Encapsulamento de submódulos internos via convenção .internal."
    - "Isolamento estrito entre suporte técnico (platform) e domínios de capacidade"

patterns-established:
  - "Todos os módulos de negócio da BETUFU possuem ponto de entrada raiz explícito com @ApplicationModule."
  - "Nenhum código de negócio de fases futuras é antecipado na fundação."
---

# Plano 01-09 — Declaração dos Dez Módulos Spring Modulith (ARCH-02)

`BASE_HEAD`: `dc7df5d`. Nada foi commitado; stage, commit e push continuam dependendo de autorização própria do responsável.

## Task 1 — Declaração de identity, verification, sports, odds e betting

A Task 1 materializou a primeira metade dos limites modulares:

1. **`identity`**: Criado `identity/package-info.java` anotado com `@ApplicationModule(displayName = "Identidade")`. O código real existente em `br.com.betufu.identity.internal` foi mantido integralmente e permanece privado ao módulo.
2. **`verification`**: Criado `verification/package-info.java` anotado com `@ApplicationModule(displayName = "Verificação")`.
3. **`sports`**: Criado `sports/package-info.java` anotado com `@ApplicationModule(displayName = "Esportes")`.
4. **`odds`**: Criado `odds/package-info.java` anotado com `@ApplicationModule(displayName = "Odds")`.
5. **`betting`**: Criado `betting/package-info.java` anotado com `@ApplicationModule(displayName = "Apostas")`.

## Task 2 — Declaração de ledger, settlement, ranking, chat e audit

A Task 2 completou os dez limites modulares de capacidade exigidos pelo requisito ARCH-02:

1. **`ledger`**: Criado `ledger/package-info.java` anotado com `@ApplicationModule(displayName = "Livro-Razão")`.
2. **`settlement`**: Criado `settlement/package-info.java` anotado com `@ApplicationModule(displayName = "Liquidação")`.
3. **`ranking`**: Criado `ranking/package-info.java` anotado com `@ApplicationModule(displayName = "Classificação")`.
4. **`chat`**: Criado `chat/package-info.java` anotado com `@ApplicationModule(displayName = "Chat")`.
5. **`audit`**: Criado `audit/package-info.java` anotado com `@ApplicationModule(displayName = "Auditoria")`.

## Validação e Verificação

- **Compilação Backend:** `node scripts/maven.mjs -f backend/pom.xml -DskipTests compile` compilou os 35 arquivos fonte gerando os respectivos `.class` de bytecode em Java 25.
- **Enumeração Estrutural:** Script Node.js validou a presença dos 10 módulos de negócio com `@ApplicationModule` distintos do suporte técnico `platform`.
- **Suíte de Testes Unitários:** `node scripts/maven.mjs -f backend/pom.xml test` executou 42 testes com **BUILD SUCCESS** (0 falhas, 0 erros).
- **Validador de Supply Chain e Ambiente:** `node scripts/validate-tracer.mjs` concluiu com **PASS em todos os 8 critérios**.
- **Formatação:** `git diff --check` retornou 0 inconformidades.

## Limites da Evidência e Relação com 01-23

Conforme registrado na auditoria, a validação sobre módulos vazios é estrutural e declarativa. A prova de discriminação bloqueante (com testes de violação sintética que falham se fronteiras forem quebradas) é a entrega central do próximo plano, o **Plano 01-23**.
