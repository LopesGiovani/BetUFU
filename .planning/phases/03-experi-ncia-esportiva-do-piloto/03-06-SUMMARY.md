---
phase: 03-experiencia-esportiva-do-piloto
plan: 06
wave: 7
status: complete-pending-git
completed: 2026-08-30
---

# Plano 03-06 - Resumo

## Resultado

O plano entregou o registro global de atletas e a seam transitória de
matrícula/HMAC versionado. O atleta não exige conta BETUFU, pode ser reutilizado
entre equipes futuras e permanece separado da página pública P6, reservada ao
03-11. O4 ficou disponível no Console operacional com seleção de competição e
equipe por nome.

A matrícula existe somente no request administrativo em memória: o backend
normaliza, calcula o fingerprint compatível com a Fase 2, persiste apenas o
fingerprint e sua versão e não devolve matrícula, HMAC ou fingerprint em
responses, Problem Details, URLs, logs ou modelos públicos.

## Entrega técnica

- V12 criou `public.sports_athlete` com UUID global, nome, slug, status,
  `lock_version`, timestamps e fingerprint HMAC versionado opcional.
- A migration adicionou unicidade para slug, nome normalizado e fingerprint,
  além de checks de formato, tamanho, status, versão e integridade temporal.
- `EnrollmentFingerprintPort` mantém a fronteira pública mínima; o adapter em
  `verification.internal` reutiliza a normalização/HMAC da Fase 2 sem expor seus
  tipos internos ao módulo `sports`.
- O cadastro deriva a competição pela equipe persistida e rejeita administrador
  de competição fora do escopo. Atualização e publicação usam optimistic locking;
  versão stale retorna HTTP 409 sem sobrescrever dados.
- O cliente OpenAPI foi governado pelo candidato `03-06` com digest
  `111FEA082473D8D6541BAD51A44D5D00DABB160316EF7625A564CDAA55101818`.
  Os hashes `governed-before` e `governed-after` são idênticos a esse digest.
- O4 usa Angular standalone/Signals/Reactive Forms. A matrícula é extraída e
  limpa imediatamente no submit, inclusive quando a API falha ou o formulário é
  cancelado; ela não entra em Signals, storage ou analytics.
- A alteração incidental `frontend/angular.json` (`cli.analytics`) foi removida
  e o arquivo foi normalizado para LF, retornando ao `HEAD`.

## Validação

- Maven completo: `251` Surefire + `359` Failsafe = `610` testes, todos verdes;
  `failsafe-summary.xml` registra `errors=0`, `failures=0`, `skipped=0`.
- Testes focais de atleta, HMAC, privacidade, Modulith e Flyway passaram; os ITs
  cobrem escopo, ausência de matrícula nas respostas, fingerprint de 32 bytes,
  constraints V12 e conflitos 409.
- Node completo: `625/625`, incluindo as mutações negativas do guard.
- `node frontend/scripts/generate-api.mjs --check`: aprovado.
- Build Angular production: aprovado; permanecem somente avisos de budget SCSS
  informativos já presentes em outras telas.
- Angular completo: `261/266`; as cinco falhas são exclusivamente de
  `role-admin-page.component.spec.ts` e reproduzem a lacuna baseline da `master`
  limpa (`248/253` antes dos novos testes deste plano).
- Playwright esportivo: `2/2` (desktop e 320 px) em `sports-pilot.spec.ts`.
- `git diff --check`: aprovado; o aviso LF -> CRLF é do checkout Windows.

## Ratificação de escopo

O responsável aprovou V12 e a seam de matrícula, autorizou a worktree/branch do
03-06 e determinou que o gate humano do candidato fosse pulado durante sua
ausência. A execução permaneceu restrita ao plano; não houve problema de regra
de negócio que exigisse interrupção.

## Riscos e pendências

- As cinco falhas Angular do administrador de papéis continuam uma lacuna
  histórica fora da allowlist e não foram mascaradas nem corrigidas neste plano.
- Avisos de Logback/Mockito e teardown Redis/Hikari apareceram durante o Maven,
  mas os relatórios finais ficaram verdes.
- O atleta global ainda não possui vínculo de elenco nesta wave; isso é
  intencional e será entregue pelos planos posteriores de propostas e elencos.

## Estado Git e próximo gate

- Worktree: `C:/Users/giova/gsd-workspaces/phase-03/03-06`.
- Branch: `worktree-agent-03-06`.
- Base preservada: `master` e `origin/master` em `9f664be` no início da retomada.
- Nenhum stage, commit, integração, limpeza ou push foi executado.
- Próximo gate: congelar HEAD/status/hashes para revisão Codex somente leitura;
  depois solicitar autorização Git específica para stage/commit, integração,
  limpeza e push.
