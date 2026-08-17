---
phase: 01-funda-o-segura-da-plataforma
plan: "31"
subsystem: node-validator-fixtures
tags: [crlf, autocrlf, fixtures, mutation-testing, windows]

requires:
  - phase: 01-29
    provides: "Validador de pré-requisitos de runtime e suas regressões negativas"
provides:
  - "Substituição multilinha tolerante ao fim de linha do checkout"
  - "Suíte Node integralmente verde, sem regressão inerte"
affects: [01-16, validation]

actuals:
  tasks: 1
  implementation_paths: 1
  mutations_executed: 2
  mutations_effective: 2
  mutations_discarded_not_applied: 1

tech-stack:
  added: []
  patterns:
    - "Fixture de mutação preserva o fim de linha real do checkout em vez de normalizar para LF"

patterns-established:
  - "Mutação só conta como prova depois de confirmada no disco: uma substituição que não casa produz verde e parece inércia."
  - "Guarda de fixture-mudou converte inércia silenciosa em falha visível, e é por isso que o defeito era uma falha e não um falso PASS."
---

# Plano 01-31 — Fechar a última regressão inerte da suíte Node

`BASE_HEAD`: `4157fef`. Nada commitado no momento da redação.

## Causa

`core.autocrlf=true` entrega os fontes Java em CRLF no checkout Windows. `.gitattributes` cobre
paths específicos, mas **não** `backend/src/main/java/**` — medido: `SecurityConfiguration.java`
tem 239 caracteres CR.

O teste `rejeita bypass CSRF na SecurityConfiguration canonica` mutava a fixture por string literal
com `\n`. Contra CRLF o `replace` não casava, a mutação virava no-op, e a guarda
`assert.notEqual(changed, original, 'fixture deve realmente mudar')` do helper `mutate` (`:153`)
acusava. A guarda estava certa: foi ela que transformou inércia silenciosa em falha visível.

## Duas hipóteses descartadas por medição, antes de escolher a correção

**O validador não é cego a CRLF.** As sete regras de `CSRF_BYPASS`
(`validate-runtime-prerequisites.mjs:1165-1177`) são regex de token único, como
`/ignoringRequestMatchers\s*\(/u`. Nenhuma depende de fim de linha, então o detector funciona em
produção. Se dependessem, o problema seria detector quebrado, não fixture inerte — e a correção
seria outra.

**Não eram cinco regressões inertes, era uma.** O `CLAUDE.md` registrava cinco. Os dois
validadores que mutam texto têm guarda de fixture-mudou (`:153` aqui e
`validate-angular-route-boundary.test.mjs:136`), logo mutação inerte falha em vez de passar
escondida; o terceiro muta objeto JSON e é imune. O agregado tinha exatamente uma falha, e era
esta. **A pendência do `CLAUDE.md` estava superestimada.**

## A correção, e por que não `.gitattributes`

`replaceAcrossLines(source, search, replacement)` converte o padrão literal em regex casando
`\r?\n` entre as linhas, reusa o fim de linha que o arquivo realmente tem no texto inserido, e
**falha alto** quando o padrão não existe — sem essa guarda, um trecho renomeado no futuro voltaria
a produzir mutação silenciosa.

A alternativa `*.java text eol=lf` renormalizaria o fim de linha de todos os fontes Java,
incluindo arquivos certificados por hash em snapshots de outros planos, para obter o mesmo
resultado. Pior: normalizar a fixture para LF faria o teste exercitar o validador contra uma forma
que **não ocorre** na máquina. A correção escolhida mantém a fixture em CRLF real, então o teste
roda contra exatamente o que existe em produção.

## Prova

```text
node --test scripts/validate-runtime-prerequisites.test.mjs   263/263
node --test scripts/*.test.mjs                                481/481
```

**Primeira vez que a suíte Node fica integralmente verde.** O agregado saiu de 480/481 para
481/481, sem nenhuma falha remanescente.

### Mutações

| # | Mutação | Vermelho em |
|---|---|---|
| C1 | voltar ao `replace` com `\n` literal — o defeito original | teste 82 |
| C2 | quebrar o padrão buscado, mantendo a guarda | teste 82 |

C1 prova que a correção é o que conserta; C2 prova que a guarda de padrão-não-encontrado morde, e
não é decoração.

**Uma terceira tentativa foi descartada, e o registro importa.** A primeira execução de C2 deu
verde e parecia inércia. Antes de reportá-la como tal, conferi se a mutação havia chegado ao disco:
não havia — o script de substituição multilinha não casou. Refeita com verificação por contagem
(`TRECHO_INEXISTENTE` = 1 ocorrência), o teste caiu. É a mesma lição da M3a do 01-22: mutação só
conta depois de observada no disco, e "verde" pode significar "não aplicada".

## Limites conhecidos da prova

1. **Só a mutação hoje inerte foi convertida.** As demais mutações multilinha do arquivo continuam
   com `\n` literal. Passam porque atingem arquivos que `.gitattributes` força a LF, ou porque são
   de linha única — não porque foram verificadas uma a uma.
2. **A causa raiz permanece.** `core.autocrlf=true` continua ativo e `.gitattributes` continua sem
   cobrir `backend/src/main/java/**`. Um teste novo que mute fonte Java por string literal com
   `\n` reproduzirá o defeito; o caminho certo é usar `replaceAcrossLines`.
3. Nenhum Maven, Docker ou Keycloak foi executado: a prova é da suíte Node.

## Pendências

1. A pendência de `core.autocrlf` no `CLAUDE.md` fala de cinco regressões inertes. Medi uma. O
   registro merece correção, e isso é edição de `CLAUDE.md`, fora de qualquer allowlist.
2. Considerar `replaceAcrossLines` como padrão para mutações multilinha futuras sobre fontes do
   backend, para que o limite 2 acima não volte como defeito.
