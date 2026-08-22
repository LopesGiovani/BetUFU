---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 22
subsystem: verification-privacy
tags: [archunit, privacy, hmac, key-rotation, incident-response]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: HMAC versionado, normalização one-way e decisões D-33 a D-37 do Plano 02-06
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Trilha append-only sem matrícula ou digest e procedimento D-37 do Plano 02-21
provides:
  - Quatro regras estruturais contra matrícula ou digest em tipo externo, logger, resposta HTTP e URL
  - Provas sintéticas de discriminação e não vacuidade para cada regra
  - Procedimento governado de geração, rotação, descarte e comprometimento da chave HMAC
affects: [02-07, 02-16, PRIV-01, VERI-06, VERI-07]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 0

tech-stack:
  added: []
  patterns:
    - ArchUnit com violador sintético e prova explícita de não vacuidade
    - Chave corrente para escrita e versões antigas somente para comparação
    - Runbook fail-closed que separa desenvolvimento local de operação de produção

key-files:
  created:
    - backend/src/test/java/br/com/betufu/PrivacyArchitectureTest.java
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-CHAVE-HMAC-PROCEDIMENTO.md
  modified: []

key-decisions:
  - "PRIV-01 é protegido estruturalmente; matrícula alfanumérica não pode depender de regex de sanitização em log."
  - "Rotação planejada mantém todas as chaves antigas ativas para comparação e usa somente a corrente em novas escritas."
  - "Descartar chave antiga permanece decisão manual e irreversível do responsável pelo projeto."
  - "Produção não pode rotacionar antes do Plano 02-07 e da aprovação de um mecanismo de injeção e guarda de segredos."

requirements-completed: [PRIV-01, VERI-06]

coverage:
  - id: D1
    description: Matrícula ou digest não alcançam classes externas, logger, respostas HTTP ou URLs sem tornar uma regra ArchUnit vermelha.
    requirement: PRIV-01
    verification:
      - kind: architecture
        ref: PrivacyArchitectureTest
        status: pass
      - kind: mutation
        ref: quatro provas sintéticas observadas vermelhas individualmente
        status: pass
    human_judgment: false
  - id: D2
    description: As quatro regras analisam produção real, não apenas fixtures sintéticas.
    requirement: PRIV-01
    verification:
      - kind: non_vacuity
        ref: 105 classes totais, 100 externas a verification, 6 controladores e 6 records de resposta
        status: pass
    human_judgment: false
  - id: D3
    description: O procedimento cobre D-33 a D-37, não contém material de chave e preserva a trilha de auditoria durante expurgo.
    requirement: VERI-06
    verification:
      - kind: manual_procedural
        ref: 02-CHAVE-HMAC-PROCEDIMENTO.md
        status: pass
    human_judgment: true

duration: not-recorded
completed: 2026-08-22
status: complete
---

# Phase 2 Plan 22: Privacidade estrutural e operação da chave HMAC

**A BETUFU agora transforma quatro caminhos de vazamento de matrícula em falhas de arquitetura e possui um runbook governado para o ciclo de vida da chave HMAC.**

## Accomplishments

- Criou quatro regras ArchUnit para impedir matrícula ou digest fora do núcleo autorizado, em logger, em resposta HTTP e em rota ou parâmetro de controlador.
- Provou cada detector com violadores sintéticos e com análise não vazia do código real.
- Documentou geração local em memória, rotação multiversão, custo D-36, descarte irreversível e resposta a comprometimento D-37.
- Vinculou o expurgo futuro ao procedimento de auditoria sem permitir alteração de `public.audit_event`.
- Registrou o padrão `^[A-Z0-9]{5,20}$`, aprovado em 2026-08-21, e a irreversibilidade de mudar a normalização.

## Decisions Made

- Sanitização textual não distingue uma matrícula alfanumérica de identificadores legítimos; a garantia precisa estar na arquitetura.
- A versão corrente escreve, enquanto todas as versões antigas permanecem carregadas somente para comparação de duplicidade.
- Uma rotação real continua bloqueada: `v2`, persistência multiversão e o mecanismo de segredos ainda exigem planos e aprovações próprias.
- A ferramenta de expurgo não foi construída preventivamente; ela só será especificada se um incidente exigir seu uso.

## Deviations from Plan

### Auto-fixed Issues

**1. A primeira implementação usou APIs ArchUnit incompatíveis**

- **Issue:** a classe gerada não compilava contra a versão de ArchUnit do projeto.
- **Fix:** duas continuações restritas ajustaram a implementação às APIs reais sem ampliar a allowlist.
- **Verification:** suíte focal, Maven completo e as quatro mutações discriminantes ficaram verdes após restauração.

**2. A suíte integrada exige uma chave HMAC efêmera**

- **Issue:** a primeira execução sem ambiente HMAC deixou 65 testes de integração sem inicializar, comportamento fail-closed esperado.
- **Fix:** a verificação final gerou 32 bytes aleatórios criptograficamente seguros somente em memória e os injetou no processo Maven.
- **Verification:** Surefire `184/184` e Failsafe `163/163`, sem falha, erro ou skip.

**3. O Antigravity editou o procedimento, mas devolveu envelope `ERROR`**

- **Issue:** duas chamadas terminaram com exit code do processo zero e resposta externa `ERROR`, apesar de alterações dentro da allowlist.
- **Fix:** o Codex não aceitou a resposta automaticamente; congelou o teste Java, revisou todo o Markdown e aplicou seis correções objetivas por continuação controlada.
- **Verification:** somente o procedimento mudou, o teste conservou seu SHA-256 e todos os gates textuais passaram.

**Total deviations:** três ajustes de compatibilidade, ambiente de teste e protocolo externo; nenhum requisito, dependência ou superfície de produto foi ampliado.

## Issues Encountered

- O wrapper Maven recusou uma tentativa de filtro com `#método` antes de iniciar o Maven; a mutação temporária foi restaurada imediatamente e não foi contada como evidência.
- O PowerShell disponível não oferece as APIs estáticas mais novas de SHA-256 e RNG; foram usadas as formas compatíveis `Create()`, `GetBytes()` e `Dispose()`.
- Avisos conhecidos de Logback, Mockito, Hikari e Lettuce durante teardown não reprovaram as suítes.

## Verification Evidence

- Cada uma das quatro mutações executou 12 testes, com exatamente 1 falha esperada e 11 verdes; cada arquivo foi restaurado antes da mutação seguinte.
- Não vacuidade ArchUnit: 105 classes de produção, 100 fora de `verification`, 6 controladores e 6 records alcançáveis por respostas HTTP.
- Suíte focal restaurada: 19 testes unitários/arquiteturais e 163 testes de integração verdes.
- Maven completo: Surefire `184/184`; Failsafe `163/163`; total `347/347`.
- Node completo: `536/536`.
- Gate de segredo: zero sequências Base64 com aparência de chave no procedimento.
- SHA-256 final do teste: `D7380975AF65C897CEE0FD9F6809DAFA24C0CA401B4D1CEAF8A8B5233648965A`.
- SHA-256 final do procedimento: `304E90C7F72872FFEF4ED2C53C7591939D0DEB47A757AADB22B3B8B6471440A3`.

## User Setup Required

- Nenhuma ação de ambiente é necessária para integrar este plano.
- Produção permanece bloqueada para rotação até o Plano 02-07 estar implantado e o responsável aprovar um mecanismo de injeção e guarda de segredos.

## Next Phase Readiness

- A Wave 4 está tecnicamente concluída com os Planos 02-11, 02-15 e 02-22.
- O Plano 02-07, único da Wave 5, pode receber briefing; ele implementará persistência e consulta multiversão, sem executar rotação real.
- Stage, commits, integração e push continuam dependentes de aprovações Git separadas.

## Self-Check: PASSED

- Os dois artefatos declarados existem e nenhum path técnico adicional foi alterado.
- Os testes completos, mutações, contagens XML, TAP e não vacuidade foram conferidos no snapshot final.
- O checkpoint humano confirmou a compreensão do procedimento sem autorizar uma rotação real.
- O índice Git permaneceu vazio e nenhum commit ou push foi realizado neste bloco.
- O HEAD técnico permaneceu em `36a190b516b19335bca3b9e406573c7b49209f38`.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-22*
