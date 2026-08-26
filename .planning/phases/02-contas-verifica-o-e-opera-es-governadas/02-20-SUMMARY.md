---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 20
subsystem: enrollment-administration-ui
tags: [angular, signals, enrollment, privacy, accessibility, security]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: contrato de correcao de matricula e dialogo governado pelos Planos 02-25 e 02-19
provides:
  - Superficie S6 responsiva em /operacao/matriculas/corrigir
  - Resolucao manual e fail-closed da identidade publica sem transportar papeis
  - Correcao governada com matricula substituta e motivo integral somente em memoria
  - Limpeza de estado transitorio no encerramento da pagina e em recusas autoritativas
affects: [02-26, 02-27, VERI-06, VERI-07, OPER-01, PRIV-01, AUDT-01]

actuals:
  tokens: not-recorded
  tasks: 1
  commits: 2

tech-stack:
  added: []
  patterns:
    - Adapter dedicado que reduz RoleGrantResponse a identityId e nickname validados
    - Store com Signals e geracao de ciclo de vida contra conclusoes assincronas tardias
    - Mutacao sem retry e com invalidacao do alvo apos 403 ou 404
    - Dialogo governado com confirmacao unica e retorno de foco

key-files:
  created:
    - frontend/src/app/features/operations/enrollment-admin-api.adapter.ts
    - frontend/src/app/features/operations/enrollment-admin-api.adapter.spec.ts
    - frontend/src/app/features/operations/enrollment-admin.store.ts
    - frontend/src/app/features/operations/enrollment-admin-page.component.ts
    - frontend/src/app/features/operations/enrollment-admin-page.component.html
    - frontend/src/app/features/operations/enrollment-admin-page.component.scss
    - frontend/src/app/features/operations/enrollment-admin-page.component.spec.ts
  modified:
    - frontend/src/app/app.routes.development.ts
    - frontend/src/app/app.routes.production.ts

key-decisions:
  - "A busca usa somente o UUID publico digitado e descarta roles dentro do adapter; papeis nunca chegam a store, DOM, log ou storage."
  - "O UUID canonico devolvido pelo backend substitui a forma digitada antes da confirmacao."
  - "403 e 404 invalidam o alvo resolvido; 401, validacao e indisponibilidade recebem mensagens distintas e sanitizadas."
  - "O encerramento da pagina limpa matricula, motivo e identidade, e resultados assincronos tardios nao repovoam o store raiz."

requirements-completed: [VERI-06, VERI-07, OPER-01, PRIV-01, AUDT-01]
---

# Plano 02-20 - Superficie S6 de correcao administrativa de matricula

## Verification Evidence

- Angular focal da pagina/store/adapter: `23/23`, sem falhas.
- Angular completo da arvore combinada: `196/196` em 18 arquivos.
- Build de producao: sucesso; permaneceram apenas avisos SCSS preexistentes.
- Backend completo com `clean verify`: Surefire `251/251` em 23 classes e Failsafe
  `332/332` em 43 classes. A execucao usou chaves HMAC sinteticas somente no processo
  de teste para satisfazer o fail-closed; nenhum segredo foi persistido.
- Validadores Node: `617/617`.
- Snapshot combinado: os 14 paths do 02-32 permaneceram identicos ao commit tecnico,
  e os tres paths corrigidos do 02-20 coincidiram por hash de blob com a revisao final.
- Mutacoes discriminantes: remover a limpeza no destroy, aceitar conclusoes tardias,
  preservar alvo apos 403/404, deixar de canonicalizar o UUID e colapsar 400/422 em erro
  generico deixaram os testes vermelhos; todas foram restauradas e conferidas por hash.
- Revisao final do snapshot combinado: `PASS`, sem P0, P1 ou P2.

## What Was Built

- A rota S6 foi adicionada aos manifests de desenvolvimento e producao sem introduzir
  tracer ou diagnostico em producao.
- O operador informa manualmente o UUID publico. O adapter usa `RoleService.getRoles`
  apenas para validar `identityId` e `nickname`, descartando os papeis imediatamente.
- A correcao usa exclusivamente o cliente gerado de enrollment, exige motivo integral e
  nunca consulta ou exibe a matricula anterior.
- Matricula substituta e motivo permanecem somente em memoria durante a tentativa. O
  destroy da pagina limpa o store e invalida respostas assincronas que chegarem depois.
- O dialogo compartilhado mantem foco, Escape e confirmacao unica; a mutacao nunca recebe
  retry automatico.

## Review Corrections

- O store raiz podia conservar UUID, nickname, matricula e motivo depois de sair da rota,
  e uma Promise tardia podia repopula-lo. Uma geracao de ciclo de vida agora invalida essas
  conclusoes e `reset()` limpa todo o estado transitorio.
- Uma recusa 403 mantinha o alvo resolvido e permitia repetir a tentativa. 403 e 404 agora
  removem o alvo antes de outra correcao.
- UUID com espacos era aceito na busca, mas o formulario conservava a forma nao canonica e
  bloqueava o envio. O valor validado retornado pelo store volta ao formulario sem emitir evento.
- Erros 404, 400/422 e 5xx/timeout agora possuem mensagens distintas, sanitizadas e cobertas.

## Deviations from Plan

- Nenhuma dependencia foi instalada e nenhum path saiu da allowlist de nove arquivos.
- O backend foi reexecutado na arvore combinada porque o `clean` revelou corretamente a
  exigencia fail-closed das chaves HMAC; as chaves de teste foram fornecidas apenas ao processo.

## Next Phase Readiness

- A rota S6 e o contrato de privacidade estao prontos para o Plano 02-26 consumir junto do
  cliente de capacidades aprovado no Plano 02-32.
- O checkpoint humano final continua reservado ao Plano 02-27, depois do 02-26.

## Self-Check: PASSED

- Nove paths de implementacao reconciliados com a allowlist.
- Commit tecnico presente e worktree limpa antes da formalizacao.
- Provas focais, agregadas, mutacoes e revisao final registradas acima.

---
*Completed: 2026-08-26*
