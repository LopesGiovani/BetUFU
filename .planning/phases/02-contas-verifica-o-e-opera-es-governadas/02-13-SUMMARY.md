---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 13
subsystem: enrollment-ui
tags: [angular, signals, enrollment, privacy, accessibility, playwright]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: registro de matrícula do 02-07, correção administrativa do 02-12 e cliente aprovado pelo 02-16
provides:
  - Superfície S3 responsiva para registro voluntário da matrícula UFU
  - Adapter e store Signals sobre o cliente OpenAPI gerado, sem HttpClient direto
  - Registro sem retry automático e sem persistência do número no navegador
  - Estado registrado somente leitura, sem matrícula, edição, remoção, validade ou comprovante
  - Prova em Chrome real a 320px e 1280px do fluxo por teclado e do feedback persistente
affects: [02-19, 02-20, 02-26, VERI-06, VERI-07, PRIV-01]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - Fatia Angular standalone com adapter exclusivo sobre cliente gerado
    - Signals para estado initial/loading/empty/populated/error
    - Retry limitado somente à leitura e nenhuma repetição automática da mutação
    - Feedback de sucesso visível, acessível e persistente

key-files:
  created:
    - frontend/src/app/features/enrollment/enrollment.models.ts
    - frontend/src/app/features/enrollment/enrollment-api.adapter.ts
    - frontend/src/app/features/enrollment/enrollment-api.adapter.spec.ts
    - frontend/src/app/features/enrollment/enrollment.store.ts
    - frontend/src/app/features/enrollment/enrollment-page.component.ts
    - frontend/src/app/features/enrollment/enrollment-page.component.html
    - frontend/src/app/features/enrollment/enrollment-page.component.scss
    - frontend/src/app/features/enrollment/enrollment-page.component.spec.ts
  modified:
    - frontend/src/app/app.routes.production.ts
    - frontend/src/app/app.routes.development.ts

key-decisions:
  - "O número digitado existe apenas na memória do formulário, é limpo no sucesso e preservado somente durante a recusa."
  - "A leitura pode repetir no máximo duas vezes; o PUT de registro executa uma única vez."
  - "O aviso de imutabilidade aparece antes do botão, sem diálogo posterior ao clique."
  - "A mensagem de sucesso é um status visível e não desaparece por temporizador."
  - "O estado registrado mostra somente a situação do vínculo e nunca o número informado."

requirements-completed: [VERI-06, VERI-07, PRIV-01]

coverage:
  - id: D1
    description: A pessoa registra a matrícula pela S3 e o valor não permanece no DOM, inputs ou storage após o sucesso.
    requirement: PRIV-01
    verification:
      - kind: unit
        ref: enrollment-page.component.spec.ts
        status: pass
      - kind: e2e
        ref: Playwright CLI em Chrome real a 320px e 1280px
        status: pass
    human_judgment: true
  - id: D2
    description: A mutação executa uma vez, enquanto somente a leitura aceita retry limitado.
    requirement: VERI-06
    verification:
      - kind: unit
        ref: enrollment-api.adapter.spec.ts e enrollment-page.component.spec.ts
        status: pass
    human_judgment: false
  - id: D3
    description: O estado registrado não oferece edição, remoção, validade, revalidação ou comprovante.
    requirement: VERI-07
    verification:
      - kind: unit
        ref: enrollment-page.component.spec.ts
        status: pass
      - kind: e2e
        ref: capturas mobile e desktop do snapshot final
        status: pass
    human_judgment: true
---

# Plano 02-13 — Superfície S3 de matrícula UFU

## Verification Evidence

- Angular terminou `136/136` em `14` arquivos de spec; o build de produção terminou com sucesso.
- Node terminou `617/617`; backend completo com `clean` terminou Surefire `246/246` em `22` classes e Failsafe `317/317` em `42` classes.
- A jornada final em Chrome real executou exatamente `GET /api/v1/session`, `GET /api/v1/enrollment/me` e um único `PUT /api/v1/enrollment/me`, todos com `200`.
- Em 320px e 1280px, `scrollWidth` ficou igual à largura do viewport. O console terminou com zero erros e zero avisos.
- O envio foi feito por teclado: preenchimento do campo, `Tab` até o botão habilitado e `Enter` para confirmar.
- Depois do sucesso, a matrícula de teste não aparecia no texto do DOM, não havia input remanescente e localStorage/sessionStorage estavam vazios.
- A mensagem `Matrícula registrada. O número não fica guardado nem aparece em nenhuma tela.` tinha `role=status`, `aria-live=polite`, caixa visível e continuava presente após cinco segundos.
- Capturas finais e SHA-256: desktop inicial `F1147CF5…`, mobile/teclado `53B34B92…`, mobile/sucesso `F89F9ABA…` e desktop/sucesso `6F5BE943…`.
- A revisão final somente leitura retornou `PASS`, sem P0, P1 ou P2. O snapshot combinado de 21 paths permaneceu em `8023A1881DCCE314757C722C0655AC939C7C703A4CEE32C178A6A7EFE1DF5543`.

## What Was Built

A rota `/conta/matricula` foi acrescentada às listas de desenvolvimento e produção. A página explica antes do envio que o registro é voluntário, garante unicidade entre contas e não conserva o número informado.

O `EnrollmentApiAdapter` consome exclusivamente o `EnrollmentService` gerado pelo OpenAPI. O store usa Signals para representar carregamento, ausência, vínculo registrado e erro. A leitura pode repetir duas vezes em falha transitória; o registro não possui retry automático.

No sucesso, o formulário é limpo e substituído pelo estado somente leitura. A página mostra um feedback verde persistente e o chip `Matrícula registrada`, mas nunca mostra a matrícula, data de validade, comprovante ou controles de edição e remoção.

## Security, Privacy and Accessibility

- Não existe `HttpClient` direto na feature, armazenamento do número, parâmetro de URL ou telemetria própria.
- O campo usa ajuda persistente; erros de campo ficam associados ao input e recusas de negócio usam alerta sanitizado.
- O botão desabilitado não entra na ordem de Tab; quando válido, o caminho campo → botão funciona integralmente por teclado.
- O link `Pular para o conteúdo principal` recebe foco visível.
- O feedback de sucesso é informação visual e região acessível; não depende somente de cor e não desaparece automaticamente.
- O estado registrado não contém upload, comprovante, revalidação, edição ou remoção.

## Review Corrections

- A primeira revisão encontrou um feedback de sucesso apenas em região visualmente oculta e removido após quatro segundos. A correção criou `.success-feedback` visível com `role=status`, manteve a região oculta somente para a ação curta de copiar e removeu o temporizador do sucesso.
- O spec agora avança cinco segundos e exige que o feedback continue visível, discriminando a implementação anterior.
- As correções invalidaram o parecer anterior; todas as suítes e a inspeção em navegador foram repetidas antes do `PASS` final.

## Deviations from Plan

- Nenhuma dependência foi adicionada e os dez paths de implementação permaneceram dentro da allowlist.
- A prova visual foi executada com respostas HTTP locais controladas, sem enviar matrícula ou credencial para serviço externo. O backend real foi verificado separadamente pela suíte Failsafe completa.

## Issues Encountered

- O build de produção passou, mas o SCSS da matrícula excedeu o budget por `466` bytes. É aviso não bloqueante e deve ser observado antes de ampliar a tela.
- O `playwright-cli` informou que a documentação da skill local não corresponde exatamente à versão do binário. Os comandos usados funcionaram; nenhuma instalação ou atualização foi feita.
- Uma consulta auxiliar de overflow desktop teve erro de sintaxe. Ela foi repetida corretamente, produzindo `1280/1280`; a página e as capturas não foram afetadas.

## User Setup Required

Nenhuma configuração nova. A tela usa a sessão OIDC, o BFF e o cliente gerado já existentes.

## Next Phase Readiness

- A S3 está pronta para integração.
- Os Planos 02-19 e 02-20 podem reutilizar o padrão cliente gerado → adapter → store → componente.
- Os PNGs e arquivos `.playwright-cli` são evidência local e permanecem fora dos commits do produto.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-25*
