---
phase: 01-funda-o-segura-da-plataforma
plan: "30"
subsystem: keycloak-realm-contract
tags: [keycloak, realm, validator, contract-drift, verify-profile]

requires:
  - phase: 01-03
    provides: "Realm canônico, validador e contrato original da conta técnica"
  - phase: 01-29
    provides: "Perfil completo da conta técnica que destravou o VERIFY_PROFILE"
provides:
  - "Contrato de `realm.users[0]` reconciliado com o artefato canônico, em 11 chaves"
  - "Valores de `firstName`/`lastName` pinados, no mesmo estilo dos demais campos"
  - "Quatro regressões negativas próprias para perfil incompleto"
affects: [01-16, keycloak, identity]

actuals:
  tasks: 1
  implementation_paths: 2
  mutations_executed: 2
  mutations_effective: 2

tech-stack:
  added: []
  patterns:
    - "Contrato de validação acompanha o artefato quando o artefato muda por defeito comprovado"

patterns-established:
  - "Igualdade exata de chaves detecta campo ausente, mas não campo em branco: pinar o valor é o que fecha os dois."
  - "Quando dois validadores discordam sobre o mesmo artefato, o que define o lado certo é qual deles ainda satisfaz o critério de aceite de origem."
---

# Plano 01-30 — Reconciliar o contrato do validador de realm

`BASE_HEAD`: `76e4923db9477d4d9fed1f328a4083925cf28dcb`.
Nada foi commitado; stage, commit e push dependem de autorização própria.

## Causa

`scripts/validate-keycloak-realm.mjs` nasceu em `060d5ed` (Plano 01-03) junto do realm e do seu
próprio teste, os três coerentes num contrato de 9 chaves para `realm.users[0]`.

`016390b` (Plano 01-29) acrescentou exatamente duas linhas ao realm — `"firstName": "BETUFU"` e
`"lastName": "Technical"` — e não tocou o validador. `git log` daquele arquivo tinha, até este
plano, uma única entrada: ele nunca havia sido atualizado por ninguém.

A adição corrigiu um defeito real: `01-29-GATE.md` registra que o provider `VERIFY_PROFILE` do
Keycloak bloqueava o login porque a conta técnica não tinha perfil completo.

## Qual lado estava errado não dependeu de julgamento

`01-03-PLAN.md:203` exige que a conta técnica **não tenha required action**. `VERIFY_PROFILE` é
required action de *provider*, não de usuário. Preencher nome e sobrenome foi, portanto, a forma
de destravar o login **sem violar** o critério do 01-03 — o 01-29 respeitou o contrato de origem, e
só o validador ficou para trás.

O segundo argumento é independente do primeiro:
`scripts/validate-runtime-prerequisites.mjs:1290-1297` **exige** os dois campos como strings não
vazias, com quatro regressões negativas próprias em `:983-996`. Remover os campos do realm
consertaria um validador, quebraria o outro e reintroduziria a falha de login.

Ambas as leituras foram confirmadas por revisão externa independente antes da execução.

## O que mudou

`scripts/validate-keycloak-realm.mjs` — `firstName` e `lastName` entraram no `expectExactKeys` de
`realm.users[0]`, e `EXPECTED` ganhou `technicalFirstName` e `technicalLastName` com os
`expectExact` correspondentes, no mesmo estilo de pinagem de valor já usado para `id`, `username`,
`email`, `enabled` e `emailVerified`.

`scripts/validate-keycloak-realm.test.mjs` — quatro regressões negativas novas: cada campo ausente
e cada campo em branco.

**`infra/keycloak/realm-betufu.json` não foi tocado.** O defeito estava no validador.

## Por que valor exato, e não só presença

A opção mínima seria acrescentar as duas chaves à lista e parar. Foi descartada por evidência, não
por gosto: a igualdade exata de chaves detecta **campo ausente**, mas aceita **campo em branco**.
Como `validate-runtime-prerequisites` rejeita branco, a opção mínima teria recriado a discordância
entre os dois validadores — de forma mais sutil e mais difícil de achar do que a original.

A mutação N2 demonstra isso: trocar os `expectExact` por checagem de presença derruba exatamente as
duas regressões de valor em branco, e nada mais.

Ressalva registrada: pinar `'BETUFU'`/`'Technical'` compra **detecção de drift e
reprodutibilidade, não segurança adicional**. A propriedade com significado de segurança é "perfil
completo", que é o não-vazio. A pinagem se justifica por ser o contrato que o arquivo inteiro
adota e por fixar valores que o 01-29 escolheu deliberadamente.

## Prova

```text
node --test scripts/validate-keycloak-realm.test.mjs     31/31   (era 27, com 1 vermelho)
node --test scripts/*.test.mjs                           481 testes, 480 pass, 1 fail
```

A aritmética fecha item a item com o critério de aceite: +1 teste corrigido, +4 testes novos,
uma falha a menos. A falha remanescente é a de `core.autocrlf=true` em
`validate-runtime-prerequisites`, que tem dono designado e não pertence a este plano.

### Mutações

| # | Mutação | Vermelho em |
|---|---|---|
| N1 | `firstName` removido do `expectExactKeys` | `aceita o realm canonico` |
| N2 | `expectExact` trocado por checagem de presença | `firstName em branco`, `lastName em branco` |

Ambas revertidas e conferidas.

## Limites conhecidos da prova

1. **Nenhum Keycloak foi executado.** O que está provado é que o validador aceita o realm canônico
   e recusa perfil incompleto. Que o `VERIFY_PROFILE` de fato destrave o login com esses valores é
   evidência herdada do `01-29-GATE.md`, não reproduzida aqui.
2. **Os valores pinados não foram confrontados com o realm persistido.** O 01-29 reconciliou o
   usuário já gravado no banco por Admin API; este plano valida apenas o fixture versionado.
3. As 25 regressões negativas anteriores continuam passando, mas não foram re-mutadas: o que se
   verificou é que nenhuma mudou de resultado.

## Pendências

1. `validate-runtime-prerequisites` segue vermelho pela pendência de `core.autocrlf=true`, a
   corrigir por `.gitattributes` ou por normalização das mutações com `\r?\n`.
2. O gate agregado do 01-16 deixa de ser bloqueado por este detector, mas continua bloqueado pelo
   item 1.
