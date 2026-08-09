---
phase: 01-funda-o-segura-da-plataforma
plan: "18"
subsystem: configuration
tags: [spring-boot, profiles, flyway, hibernate, actuator, tomcat, forwarded-headers]

requires:
  - phase: 01-17
    provides: "Scaffold Spring Boot/Modulith e fronteira BFF OIDC ativada pelos profiles internos `runtime` ou `integrated`"
provides:
  - "Profiles separados para desenvolvimento, homologação e produção sem valores secretos versionados"
  - "Grupos de profiles que ativam automaticamente a configuração compartilhada `runtime`"
  - "Contrato de origem pública, origem do backend e proxies confiáveis por ambiente"
  - "Validação isolada da configuração sem Keycloak, PostgreSQL, Redis, Docker ou rede"
affects: [01-03, 01-11, 01-14, 01-16, 01-24, 01-26, backend, operations]

actuals:
  tokens: 2017
  tasks: 1
  commits: 1

tech-stack:
  added: []
  patterns:
    - "Profiles públicos de ambiente compõem o profile técnico compartilhado `runtime`"
    - "Homologação e produção recebem origens e proxies por variáveis externas sem fallback aberto"
    - "Forwarded headers usam suporte nativo do Tomcat limitado aos proxies declarados"

key-files:
  created:
    - ".java-version"
    - "backend/src/main/resources/application-dev.yaml"
    - "backend/src/main/resources/application-homolog.yaml"
    - "backend/src/main/resources/application-prod.yaml"
    - "backend/src/test/java/br/com/betufu/platform/EnvironmentConfigurationTest.java"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-18-SUMMARY.md"
  modified:
    - "backend/src/main/resources/application.yaml"
    - ".planning/STATE.md"
    - ".planning/ROADMAP.md"

key-decisions:
  - "Ativar `runtime` automaticamente pelos grupos `dev`, `homolog` e `prod`, mantendo `integrated` diretamente ativável para testes integrados"
  - "Usar `betufu.web.public-origin` e `betufu.web.backend-origin` como contrato interno de origens"
  - "Usar o profile do servidor como único gate do diagnóstico, sem feature flag permissiva"
  - "Confiar em forwarded headers somente pelo modo nativo do Tomcat e por uma lista explícita de proxies"

patterns-established:
  - "Configuração por ambiente: cada `application-{profile}.yaml` contém somente valores locais públicos ou placeholders externos"
  - "Schema: Flyway permanece habilitado e Hibernate apenas valida com `ddl-auto=validate`"
  - "Teste de configuração: `ApplicationContextRunner` carrega cada profile sem iniciar serviços externos"

requirements-completed: [OPS-02]

coverage:
  - id: D1
    description: "Dev, homolog e prod carregam arquivos próprios e isolados"
    requirement: OPS-02
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/platform/EnvironmentConfigurationTest.java#loadsDevWithOnlyLoopbackProxies, #loadsHomologWithExternalValues e #loadsProdWithExternalValues"
        status: pass
    human_judgment: false
  - id: D2
    description: "Ativar qualquer profile de ambiente também ativa automaticamente `runtime`"
    requirement: OPS-02
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/platform/EnvironmentConfigurationTest.java#runProfile"
        status: pass
    human_judgment: false
  - id: D3
    description: "Homologação e produção mantêm origens e proxies externos, sem fallback ou wildcard versionado"
    requirement: OPS-02
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/platform/EnvironmentConfigurationTest.java#keepsSharedEnvironmentValuesExternalAndDefinesNoDiagnosticFlag"
        status: pass
    human_judgment: false
  - id: D4
    description: "O diagnóstico não possui feature flag reativável em produção"
    requirement: OPS-02
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/platform/EnvironmentConfigurationTest.java#assertSharedRuntimeContract"
        status: pass
    human_judgment: false
  - id: D5
    description: "Java 25 está fixado e a política compartilhada usa Flyway, validação Hibernate e exposição mínima do Actuator"
    requirement: OPS-02
    verification:
      - kind: integration
        ref: "node scripts/maven.mjs -f backend/pom.xml test"
        status: pass
    human_judgment: false

duration: "não mensurada — execução interativa com aprovações por bloco"
completed: 2026-08-09
status: complete
---

# Fase 1 Plano 18: Resumo da configuração segura por ambiente

**Profiles `dev`, `homolog` e `prod` isolados, composição automática com `runtime` e confiança em proxy definida sem segredos versionados**

## Desempenho

- **Duração:** não mensurada — implementação acompanhada por aprovações explícitas
- **Início:** não registrado — trabalho conduzido em múltiplas interações
- **Conclusão:** 2026-08-09T11:36:03-03:00
- **Tarefas:** 1
- **Arquivos modificados:** 6 artefatos de entrega e 3 documentos de acompanhamento GSD

## Entregas

- Profiles de desenvolvimento, homologação e produção separados, sem credenciais reais ou hosts compartilhados versionados.
- Grupos `dev`, `homolog` e `prod` ativam automaticamente `runtime`, preservando a configuração OIDC do Plano 01-17.
- Dev aceita forwarded headers somente de loopback; homologação e produção exigem origens e proxies fornecidos externamente.
- Flyway está habilitado, Hibernate usa `ddl-auto=validate`, o Actuator continua limitado a health e não existe flag de diagnóstico.
- Validação direcionada executou 4 testes e a regressão completa executou 8 testes, sem falhas, erros ou testes ignorados.

## Commit da tarefa

A tarefa única foi implementada em blocos revisados pelo responsável e consolidada em um commit atômico:

1. **Task 1: separar configuração por ambiente** — `59b0f1f` (`feat`).

Este `SUMMARY`, `STATE` e `ROADMAP` compõem o fechamento documental do plano no futuro commit de metadados.

## Arquivos criados e modificados

- `.java-version` — fixa Java 25 para ferramentas compatíveis.
- `backend/src/main/resources/application.yaml` — declara grupos de profiles e a política compartilhada de Flyway/Hibernate.
- `backend/src/main/resources/application-dev.yaml` — fixa as origens locais e restringe proxies a loopback.
- `backend/src/main/resources/application-homolog.yaml` — recebe origens e proxies de homologação externamente.
- `backend/src/main/resources/application-prod.yaml` — recebe origens e proxies de produção externamente e não contém capacidade diagnóstica.
- `backend/src/test/java/br/com/betufu/platform/EnvironmentConfigurationTest.java` — prova isolamento, composição e controles de configuração.
- `.planning/phases/01-funda-o-segura-da-plataforma/01-18-SUMMARY.md` — registra resultados, cobertura e prontidão.
- `.planning/STATE.md` — avança a posição operacional para o Plano 01-18 concluído.
- `.planning/ROADMAP.md` — atualiza o progresso da Fase 1 para `4/28`.

## Decisões tomadas

- Os nomes de ambiente permanecem simples para operação (`dev`, `homolog`, `prod`), enquanto `runtime` concentra configurações compartilhadas.
- `integrated` continua diretamente ativável e não foi transformado em grupo de ambiente.
- `betufu.web.public-origin` e `betufu.web.backend-origin` formam o contrato interno de origens para consumidores posteriores.
- A proteção do diagnóstico depende exclusivamente de profiles do servidor; o controller e o mapping somente serão criados no Plano 01-26.
- `server.forward-headers-strategy=native` delega o tratamento ao Tomcat, mas somente proxies declarados podem influenciar host, protocolo e IP encaminhados.

## Desvios do plano

Nenhum — o plano corrigido e aprovado foi executado como escrito.

## Problemas encontrados

- A extensão Java sinalizou nulabilidade imprecisa no método de referência `PropertySource::getName`; a expressão foi substituída por uma lambda equivalente, o aviso desapareceu e os 4 testes direcionados passaram novamente.
- A regressão completa exibiu o aviso não bloqueante já conhecido sobre auto-attach do Mockito/Byte Buddy em versões futuras do JDK. Os 8 testes atuais passaram e nenhuma configuração foi alterada silenciosamente.

## Configuração manual necessária

Nenhuma nesta etapa. Os futuros ambientes de homologação e produção deverão fornecer `BETUFU_PUBLIC_ORIGIN`, `BETUFU_BACKEND_ORIGIN` e uma lista não vazia em `BETUFU_TRUSTED_PROXIES`; essa ligação pertence aos planos de infraestrutura e implantação.

## Prontidão para o próximo plano

- O Plano `01-03` pode conectar PostgreSQL, Keycloak, Redis, SeaweedFS local pelo contrato S3 genérico e e-mail aos profiles agora separados.
- `OPS-02` continua pendente na rastreabilidade global porque também é compartilhado pelos Planos `01-03`, `01-11`, `01-14`, `01-16` e `01-24`.
- A ausência de flag diagnóstica está comprovada; a ausência física do bean e do mapping em produção continua pertencendo ao Plano `01-26`.
- Valores reais de homologação e produção, escolha de proxy e implantação não foram inventados por este plano.

## Autoavaliação: APROVADA

- [x] Os seis arquivos declarados no Plano `01-18` existem.
- [x] O commit `59b0f1f` contém exatamente os seis artefatos de implementação e teste.
- [x] `node scripts/maven.mjs -f backend/pom.xml test -Dtest=EnvironmentConfigurationTest` executou 4 testes sem falhas.
- [x] `node scripts/maven.mjs -f backend/pom.xml test` executou 8 testes sem falhas.
- [x] Cada profile de ambiente ativa `runtime` e carrega somente seu arquivo específico.
- [x] Dev usa `http://localhost:4200`, backend `http://127.0.0.1:8080` e proxies loopback.
- [x] Homologação e produção usam somente placeholders externos, sem fallback ou wildcard versionado.
- [x] Nenhuma feature flag de diagnóstico foi introduzida.
- [x] Nenhum segredo real, dependência, serviço externo ou acesso de rede foi introduzido.
- [x] Nenhum push ocorreu durante este fechamento.

---
*Fase: 01-funda-o-segura-da-plataforma*
*Concluído em: 2026-08-09*
