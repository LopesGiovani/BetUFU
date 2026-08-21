---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 06
subsystem: security
tags: [java, spring-boot, hmac-sha256, privacy, configuration]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Decisões D-28, D-29 e D-33 a D-35 sobre matrícula e rotação de chave
provides:
  - Normalização e validação determinísticas da matrícula UFU
  - HMAC-SHA256 versionado com comparação em tempo constante
  - Configuração Spring fail-closed e guard de runtime sem fallback
affects: [02-07, 02-08, 02-10, 02-22, VERI-06, PRIV-01]

actuals:
  tokens: 8441
  tasks: 2
  commits: 1

tech-stack:
  added: []
  patterns:
    - Chave externa versionada, decodificada e validada no bootstrap
    - Nova instância de Mac por cálculo para segurança concorrente

key-files:
  created:
    - backend/src/main/java/br/com/betufu/verification/internal/MatriculaNormalizer.java
    - backend/src/main/java/br/com/betufu/verification/internal/MatriculaHmac.java
    - backend/src/main/java/br/com/betufu/verification/internal/MatriculaHmacConfiguration.java
    - backend/src/main/java/br/com/betufu/verification/internal/MatriculaHmacKeyProperties.java
  modified:
    - backend/src/main/resources/application.yaml
    - scripts/validate-runtime-prerequisites.mjs

key-decisions:
  - "A matrícula normalizada usa o padrão permissivo ^[A-Z0-9]{5,20}$, aprovado pelo responsável em 2026-08-21."
  - "A configuração HMAC pertence ao documento base do application.yaml para falhar fechada também no perfil padrão."
  - "O cálculo recusa qualquer texto que não esteja previamente normalizado e validado."

patterns-established:
  - "Validate before digest: entrada malformada nunca atravessa a fronteira do HMAC."
  - "No secret fallback: versão e chave são placeholders obrigatórios protegidos pelo guard Node."

requirements-completed: [VERI-06, PRIV-01]

coverage:
  - id: D1
    description: A matrícula é recortada, convertida com Locale.ROOT, limpa e validada antes do HMAC.
    requirement: VERI-06
    verification:
      - kind: unit
        ref: "MatriculaNormalizerTest; mutação Locale.getDefault observada vermelha"
        status: pass
    human_judgment: false
  - id: D2
    description: O HMAC-SHA256 é determinístico, versionado, concorrente e comparado em tempo constante.
    requirement: VERI-06
    verification:
      - kind: unit
        ref: "MatriculaHmacTest"
        status: pass
    human_judgment: false
  - id: D3
    description: A aplicação recusa subir sem versão e chave Base64 válida de pelo menos 32 bytes.
    requirement: PRIV-01
    verification:
      - kind: unit
        ref: "HmacKeyFailClosedTest"
        status: pass
      - kind: other
        ref: "node --test scripts/validate-runtime-prerequisites.test.mjs"
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-21
status: complete
---

# Phase 2 Plan 06: Núcleo criptográfico da matrícula

**Matrículas normalizadas agora produzem HMAC-SHA256 versionado sem conservar o número original, e a aplicação falha no bootstrap quando a chave externa não é válida.**

## Performance

- **Duration:** não cronometrada; a execução atravessou retomadas de sessão
- **Started:** horário não registrado
- **Completed:** 2026-08-21
- **Tasks:** 2
- **Files modified:** 11, incluindo este summary

## Accomplishments

- Fixou a normalização irreversível e o padrão aprovado `^[A-Z0-9]{5,20}$` antes de qualquer digest.
- Implementou HMAC-SHA256 por versão, com digest de 32 bytes, cópia defensiva das chaves e uma instância de `Mac` por chamada.
- Impediu o bootstrap sem versão corrente, chave Base64 válida e pelo menos 32 bytes de material decodificado.
- Protegeu os placeholders HMAC contra valores literais, fallback e override lateral em perfis.

## Task Commits

- `b9b1060` — `feat(02-06): implementar HMAC versionado da matricula` — contém exatamente os dez arquivos técnicos do plano.

Este summary forma o commit documental subsequente. O hash desse próprio commit não é registrado dentro dele, porque só existe após a gravação do conteúdo.

## Files Created/Modified

- `MatriculaNormalizer.java` — recorta, converte com `Locale.ROOT`, remove separadores e valida o padrão aprovado.
- `MatriculaHmac.java` — calcula HmacSHA256 por versão e compara digests em tempo constante.
- `MatriculaHmacKeyProperties.java` e `MatriculaHmacConfiguration.java` — ligam as propriedades Spring e validam o segredo no bootstrap.
- `application.yaml` — declara versão e chave por variáveis obrigatórias, sem fallback.
- `validate-runtime-prerequisites.mjs` e seu teste — recusam drift, fallback e override do contrato HMAC.
- `MatriculaNormalizerTest.java`, `MatriculaHmacTest.java` e `HmacKeyFailClosedTest.java` — provam normalização, criptografia e falha fechada.

## Decisions Made

- O responsável escolheu a opção permissiva em 2026-08-21: de 5 a 20 letras ASCII maiúsculas ou dígitos após a normalização. Isso reduz o risco de excluir matrículas históricas ou de pós-graduação.
- A ordem canônica permanece exatamente a D-29: recortar espaços externos, converter com `Locale.ROOT`, remover separadores internos, validar e somente então calcular o HMAC.
- Chaves antigas podem permanecer ativas para comparação; novas escritas usam a versão corrente. Persistência da versão e rotação operacional pertencem aos planos consumidores.

## Deviations from Plan

### Auto-fixed Issues

**1. A configuração HMAC estava inicialmente restrita ao perfil integrado**

- **Found during:** Maven agregado, em contextos Spring sem perfil ativo.
- **Issue:** o bean fail-closed era carregado no perfil padrão, mas as propriedades estavam em outro documento YAML; nove contextos falharam antes dos testes.
- **Fix:** moveu-se o bloco `betufu.verification.matricula-hmac` para o documento base e o guard passou a exigir o contrato nessa posição.
- **Verification:** Maven completo passou com 164 testes Surefire e 143 Failsafe.

**2. O cálculo aceitava texto malformado e a comparação aceitava dois nulos**

- **Found during:** revisão Codex de segurança.
- **Issue:** chamadas diretas podiam ignorar o normalizador; `isEqual(null, null)` devolvia verdadeiro.
- **Fix:** `calculate` agora exige o padrão aprovado e `isEqual` recusa qualquer operando nulo.
- **Verification:** a correção foi observada vermelha em 43 de 54 execuções antes da implementação e verde em 54 de 54 depois dela.

**3. A descrição Java não refletia literalmente a ordem D-29**

- **Found during:** revisão final do snapshot.
- **Issue:** o código estava correto, mas o Javadoc listava a conversão antes do recorte.
- **Fix:** a enumeração foi alinhada à decisão sem alterar comportamento.
- **Verification:** inspeção direta e `git diff --check` limpo.

**Total deviations:** 3 correções de configuração, segurança e documentação, sem ampliação funcional ou dependência nova.

## Issues Encountered

- O RED inicial dos testes Java produziu 35 erros de compilação porque as classes ainda não existiam, como esperado no TDD.
- O guard Node novo começou com duas falhas e passou depois da implementação das regras.
- Uma chamada do adaptador Antigravity devolveu envelope `ERROR` por formato inválido de `testsRequested`; a auditoria do disco confirmou alteração somente no teste autorizado.
- A mutação `Locale.ROOT` → `Locale.getDefault()` derrubou exatamente o caso turco: 22 execuções, 1 falha. O arquivo foi restaurado e os 22 casos passaram.
- Avisos conhecidos de Logback, Mockito, Hikari e Lettuce apareceram durante o encerramento dos containers, sem falhas de teste.

## Evidências de validação

- Maven focal: 82/82 Surefire e 143/143 Failsafe.
- Node focal: 272/272.
- Maven agregado final: 164/164 Surefire e 143/143 Failsafe; `BUILD SUCCESS`.
- Node agregado: 525/525.
- Correção de segurança RED/GREEN: 54 execuções, 43 falhas antes; 54/54 depois.
- Mutação de localidade: 22 execuções, 1 falha esperada; restauração confirmada com 22/22.
- `git diff --check`: sem erros.
- Revisão Codex final: `CLEAN`, sem exposição do segredo e sem achados bloqueantes.

## User Setup Required

No ambiente de execução, definir `BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION` e a variável da versão correspondente, inicialmente `BETUFU_MATRICULA_HMAC_KEY_V1`, com Base64 de pelo menos 32 bytes. O valor real não deve entrar no Git, banco, logs ou documentação.

## Next Phase Readiness

- O Plano 02-07 pode persistir digest e versão sem conservar a matrícula original.
- O Plano 02-22 ainda deve documentar rotação, comprometimento e proteção estrutural.
- Não há dependência nova, migration ou endpoint neste plano.
- Integração na branch principal, push e limpeza do worktree permanecem pendentes de aprovação própria.

## Self-Check: PASSED

- Os dez arquivos técnicos e este summary existem no worktree.
- As suítes focais, agregadas e as mutações discriminantes produziram as evidências esperadas.
- Nenhum valor de chave foi gravado ou exibido.
- O stage permaneceu vazio antes da formalização aprovada.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-21*
