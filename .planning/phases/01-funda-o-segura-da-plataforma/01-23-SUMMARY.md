---
phase: 01-funda-o-segura-da-plataforma
plan: "23"
subsystem: architecture-modulith-gates
tags: [modulith, archunit, architecture, boundaries, tests, discrimination]

requires:
  - phase: 01-09
    provides: "Dez limites modulares Spring Modulith explícitos para os domínios de negócio"
provides:
  - "Gate arquitetural Spring Modulith com verify() e trava de contagem dos 10 módulos + platform"
  - "Regra ArchUnit contra dependências cíclicas com prova de discriminação sintética (CycleClassA/CycleClassB)"
  - "Regra ArchUnit contra acesso cross-module a pacotes .internal. com prova de discriminação sintética"
  - "Guarda de regressão ArchUnit permanente contra stacks distribuídas (Kafka, Kubernetes, gRPC, Spring Cloud)"
  - "Teste isolado @ApplicationModuleTest do módulo identity em modo STANDALONE"
affects: [architecture, tests, identity, modulith, archunit]

actuals:
  tasks: 1
  implementation_paths: 5
  documentation_paths: 1
  mutations_executed: 2
  mutations_effective: 2
  external_review_rounds: 2

tech-stack:
  patterns:
    - "ApplicationModules.of(BetufuApplication.class).verify() para validação de grafo modular"
    - "ArchUnit JUnit 5 @AnalyzeClasses para regras de bytecode e fatias (Slices)"
    - "@ApplicationModuleTest em modo STANDALONE para teste de slice modular"
    - "Fixtures sintéticas isoladas para comprovação de discriminação direta no ArchUnit"

patterns-established:
  - "Qualquer introdução de Kafka, Kubernetes, gRPC ou stacks distribuídas quebra imediatamente o build."
  - "Qualquer acesso direto a pacotes .internal. de outro módulo quebra imediatamente o build."
  - "Qualquer introdução de dependência cíclica entre módulos quebra imediatamente o build."
  - "O módulo identity inicializa em contexto STANDALONE sem depender de módulos de domínio futuros."
---

# Plano 01-23 — Gates Arquiteturais Modulith e ArchUnit (ARCH-03 / TEST-02)

`BASE_HEAD`: `707428a`. Integração: Implementação commitada em `8725992` (feat) e governança em `5cecda4` (docs), sincronizados com `origin/master` sob autorização do responsável.

## Task 1 — Imposição dos Gates Modulith e ArchUnit com Provas de Discriminação

A Task 1 implementou as suítes de testes arquiteturais bloqueantes:

1. **`ModulithArchitectureTests.java`**:
   - `verifiesModulithBoundaries`: executa `ApplicationModules.of(BetufuApplication.class).verify()`, validando a coerência do grafo modular do monólito.
   - `verifiesAllTenBusinessModulesAndPlatformArePresent`: valida a presença dos 10 módulos de negócio de ARCH-02 mais o módulo técnico `platform` com trava estrutural explícita de contagem (`hasSize(11)`).

2. **`ModuleDependencyRulesTest.java` (ArchUnit)**:
   - `NO_CROSS_MODULE_ACCESS_TO_IDENTITY_INTERNAL`: proíbe acesso cruzado a `identity.internal` a partir de classes externas (`ARCH-02 / ARCH-03`). Comprovada por teste de discriminação aplicando a regra real contra `SyntheticExternalViolator`.
   - `NO_MODULE_CYCLES`: garante que as fatias modulares `br.com.betufu.(*)..` são livres de ciclos. Comprovada por teste de discriminação aplicando a regra real contra as fixtures circulares `CycleClassA` e `CycleClassB`.
   - `NO_DISTRIBUTED_OR_STREAMING_STACKS`: proíbe imports de Kafka, Kubernetes, gRPC, Netflix ou Spring Cloud em código de produção (`ARCH-03`).

3. **`IdentityModuleTest.java` (`@ApplicationModuleTest`)**:
   - Inicializa o contexto do módulo `identity` em modo `STANDALONE`.
   - Comprova que os serviços e controllers internos (`LocalIdentityService`, `LocalIdentityController`, `LocalIdentityRepository`) são inicializados no slice sem acoplamento a módulos futuros.
   - Comprova no grafo do Modulith que `identity` não possui dependências diretas para nenhum dos 9 módulos de negócio futuros (`verification`, `sports`, `odds`, `betting`, `ledger`, `settlement`, `ranking`, `chat`, `audit`).

## Limites da Evidência e Discriminação por Regra

- **`NO_CROSS_MODULE_ACCESS_TO_IDENTITY_INTERNAL`:** Provada e discriminada diretamente. A regra real foi executada contra a classe `SyntheticExternalViolator` e comprovadamente falhou com `AssertionError: was violated`.
- **`NO_MODULE_CYCLES`:** Provada e discriminada diretamente. A regra real foi executada contra `CycleClassA` e `CycleClassB` e comprovadamente falhou com `AssertionError: was violated (Cycle detected)`.
- **`NO_DISTRIBUTED_OR_STREAMING_STACKS`:** Guarda de regressão estática permanente. Não é discriminável em tempo de execução sem introduzir Kafka, gRPC ou Kubernetes no `pom.xml` (o que violaria o próprio requisito ARCH-03). A regra garante que adições futuras de tais dependências ou imports em classes de produção quebrem o build imediatamente.
- **`ApplicationModules.verify()`:** Valida a conformidade do grafo atual (sem ciclos e sem imports ilegais declarados). A capacidade de rejeitar violações estruturais em nível de bytecode é garantida de forma discriminante pelas regras ArchUnit.

## Validação e Verificação

- **Testes Arquiteturais (Surefire):** `node scripts/maven.mjs -f backend/pom.xml test "-Dtest=ModulithArchitectureTests,ModuleDependencyRulesTest,IdentityModuleTest"` executou 9 testes com **BUILD SUCCESS** (0 falhas, 0 erros).
- **Suíte Agregada Surefire:** `node scripts/maven.mjs -f backend/pom.xml test` executou **51/51 testes GREEN** (0 falhas, 0 erros).
- **Formatação:** `git diff --check` retornou 0 inconformidades.
