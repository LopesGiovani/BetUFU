---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 19
subsystem: role-administration-ui
tags: [angular, signals, roles, accessibility, security, playwright]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: contrato de papeis e authorities pelos Planos 02-17 e 02-23
provides:
  - Superficie S5 responsiva em /operacao/papeis
  - Administracao governada do papel global ADMINISTRADOR_GERAL
  - Dialogo compartilhado com foco inicial, focus trap, Escape e retorno de foco
  - Deferimento explicito dos papeis escopados aos catalogos autoritativos futuros
affects: [02-20, 02-26, 02-27, OPER-01, OPER-02, OPER-03, OPER-04, IDEN-05]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 3

tech-stack:
  added: []
  patterns:
    - Formulario reativo com Signals para estado de dominio
    - Backend autoritativo com antecipacao fail-closed das guardas na UI
    - Mutacao sem retry e com trava contra envio concorrente
    - Foco devolvido ao controle real que sobrevive ou substitui o gatilho

key-files:
  created:
    - frontend/src/app/features/operations/role-admin.store.ts
    - frontend/src/app/features/operations/role-admin-page.component.ts
    - frontend/src/app/features/operations/role-admin-page.component.html
    - frontend/src/app/features/operations/role-admin-page.component.scss
    - frontend/src/app/features/operations/role-admin-page.component.spec.ts
    - frontend/src/app/shared/confirm-dialog/confirm-dialog.component.ts
    - frontend/src/app/shared/confirm-dialog/confirm-dialog.component.scss
    - frontend/src/app/shared/confirm-dialog/confirm-dialog.component.spec.ts
  modified:
    - frontend/src/app/features/operations/operations.models.ts
    - frontend/src/app/features/operations/operations-api.adapter.ts
    - frontend/src/app/app.routes.production.ts
    - frontend/src/app/app.routes.development.ts

key-decisions:
  - "Somente ADMINISTRADOR_GERAL e acionavel na Fase 2; nenhum scopeRef e inventado pelo cliente."
  - "ADMINISTRADOR_COMPETICAO aguarda o catalogo real da Fase 3 e MODERADOR aguarda o escopo da Fase 7."
  - "D-48a falha fechado se a identidade do ator nao puder ser confirmada; D-48b continua exclusivamente autoritativa no backend transacional."
  - "O dialogo nao finge restaurar foco em elemento removido: a pagina foca o gatilho sobrevivente ou o botao que o substituiu."

requirements-completed: [OPER-01, OPER-02, OPER-03, OPER-04, IDEN-05]
---

# Plano 02-19 - Superficie S5 de administracao de papeis

## Verification Evidence

- Angular: `157/157` testes em 16 arquivos, sem falhas.
- Build de producao: sucesso; a fatia lazy de S5 ficou em aproximadamente `30.98 kB`.
- Backend completo com `clean verify`: Surefire `246/246` em 22 classes e Failsafe
  `317/317` em 42 classes. A execucao usou chaves HMAC sinteticas somente no processo
  de teste para satisfazer o fail-closed do smoke context; nenhum segredo foi persistido.
- Validadores Node: `617/617`.
- `git diff --check`: verde; somente avisos LF/CRLF ja conhecidos no checkout Windows.
- Snapshot tecnico: 15 paths esperados, 15 encontrados, nenhum ausente e nenhum fora
  da allowlist ratificada.
- Navegador real: 320px e 1280px sem overflow ou alvo interativo abaixo de 44px;
  foco inicial em Cancelar; Tab preso; Escape devolvendo foco; UUID invalido sem
  navegacao nativa; concessao trocando o CTA para `Revogar papel` e focando esse
  botao substituto; recusa `409 LAST_ADMIN` com codigo copiavel.
- Mutacoes discriminantes: papel escopado acionavel, remocao da guarda de
  autoconcessao e remocao do tratamento `409 LAST_ADMIN` deixaram a suite vermelha
  e foram restauradas antes do snapshot final.
- Revisao final: `02-19-REVIEW.md`, `PASS`, P0=P1=P2=0.

## What Was Built

- A rota `/operacao/papeis` foi adicionada aos builds de desenvolvimento e producao
  sob o layout autenticado.
- A pessoa alvo e localizada exclusivamente pelo UUID publico. Email e matricula nao
  sao aceitos nem exibidos como chave de busca.
- A tela sempre modela os tres papeis. Somente `ADMINISTRADOR_GERAL` possui acoes;
  os dois papeis escopados mostram o motivo literal do deferimento e nenhum campo ou
  chamada de `scopeRef`.
- O adapter usa somente o `RoleService` gerado. Leituras podem repetir no maximo duas
  vezes; grant e revoke nunca repetem automaticamente.
- Concessao e revogacao exigem dialogo com custo D-49 antes da confirmacao. A store
  bloqueia um segundo envio enquanto a primeira mutacao esta em andamento.
- Autoconcessao e antecipada pela UI e revalidada pelo backend. A revogacao do ultimo
  administrador continua decidida pelo servidor sob transacao e aparece como recusa
  segura `409 LAST_ADMIN`.

## Review Corrections

- O formulario inicial usava submit nativo sem `ReactiveFormsModule`; a primeira prova
  em navegador encontrou a navegacao indevida. Ele agora usa `FormControl`, preserva a
  rota e foca o campo quando o UUID e invalido.
- A ausencia do perfil do ator podia deixar a antecipacao D-48a permissiva. O estado
  agora permanece fail-closed inclusive quando o contexto se perde depois da busca.
- O dialogo chamava `.focus()` enquanto o gatilho estava desabilitado ou ja removido.
  A restauracao agora ocorre depois da mutacao e mira o controle que realmente existe.
- A store ganhou trava contra dupla submissao concorrente.

## Deviations from Plan

- Nenhuma dependencia foi instalada e nenhum path de implementacao saiu da allowlist.
- O historico possui tres commits porque a decisao de produto sobre papeis escopados
  precisou ser transportada para a worktree antes da implementacao. O commit tecnico
  e o documental permanecem separados; o terceiro e exclusivamente a emenda aprovada.
- O build conserva tres avisos de budget SCSS pre-existentes em perfil, painel e
  matricula. S5 e o dialogo compartilhado nao introduziram novo aviso de budget.

## Next Phase Readiness

- A Wave 14 esta liberada para os Planos 02-20 e 02-26, que possuem escopos distintos:
  S6 de correcao administrativa de matricula e S7 de guard/layout/menu governado.
- O catalogo de competicoes continua na Fase 3 e o escopo de moderacao na Fase 7;
  a Wave 14 nao deve ativar esses papeis por atalho.

---
*Completed: 2026-08-25*
