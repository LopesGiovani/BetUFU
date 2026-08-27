# Fase 3 — Auditoria de Cobertura das Fontes

## Resultado

- Objetivo do ROADMAP: coberto pela cadeia decisória e executável dos Planos 03-01 a 03-17.
- Requisitos: 17/17 cobertos; nenhum ID foi omitido.
- Decisões de contexto: D-53 a D-80 cobertas; ideias adiadas permanecem ausentes.
- Pesquisa: arquitetura, dependências, checkpoints, riscos e validação cobertos.
- UI: 20/20 superfícies e 156/156 considerações explícitas atribuídas; 0 backstops, 0 dispensadas, 0 não resolvidas.
- Edge probe sem SPEC: os 17 IDs canônicos (`EDGE-AUTH-01` e `EDGE-SPRT-01` a `EDGE-SPRT-16`) permanecem `flagged-unverified` nos PLANs relevantes; nenhum foi silenciosamente classificado ou duplicado por sufixo.

## GOAL e requisitos

| Source | ID | Feature/Requirement | Plan | Status |
|---|---|---|---|---|
| GOAL | — | Visitante acompanha catálogo genérico publicado e administrador opera somente o escopo real | 03-01–03-17 | COVERED |
| REQ | AUTH-01 | Consulta pública sem conta | 03-02, 03-03, 03-11, 03-16, 03-17 | COVERED |
| REQ | SPRT-01 | Catálogo, competição/edição, categoria, fase e local escopados | 03-01, 03-02, 03-04, 03-16, 03-17 | COVERED |
| REQ | SPRT-02 | Organização, equipe, atleta, elenco e CSV atômico | 03-05, 03-06, 03-08, 03-09, 03-16, 03-17 | COVERED |
| REQ | SPRT-03 | Atleta sem conta | 03-06, 03-17 | COVERED |
| REQ | SPRT-04 | Matrícula transitória e HMAC compatível | 03-06, 03-17 | COVERED |
| REQ | SPRT-05 | Somente fingerprint/version persistidos | 03-06, 03-17 | COVERED |
| REQ | SPRT-06 | Proposta única e auditável | 03-07, 03-17 | COVERED |
| REQ | SPRT-07 | Ambiguidade/bloqueios e revisão | 03-07, 03-17 | COVERED |
| REQ | SPRT-08 | Aceite, rejeição, contestação e owner-only | 03-07, 03-14, 03-16, 03-17 | COVERED |
| REQ | SPRT-09 | Elenco rascunho/publicação/correção | 03-08, 03-17 | COVERED |
| REQ | SPRT-10 | Evento, participantes, tempo, fonte e local | 03-01, 03-02, 03-04, 03-10, 03-17 | COVERED |
| REQ | SPRT-11 | Estados, resultado, correção, versão e auditoria | 03-01, 03-02, 03-10, 03-17 | COVERED |
| REQ | SPRT-12 | Agenda filtrável e evento completo | 03-03, 03-11, 03-16, 03-17 | COVERED |
| REQ | SPRT-13 | Páginas públicas e estatísticas genéricas | 03-05, 03-11, 03-14, 03-17 | COVERED |
| REQ | SPRT-14 | HEAD_TO_HEAD, bracket derivado e classificação transcrita | 03-12, 03-13, 03-17 | COVERED |
| REQ | SPRT-15 | Favorito por organização com migração preservadora | 03-01, 03-05, 03-17 | COVERED |
| REQ | SPRT-16 | Perfil esportivo e foto owner-only | 03-14, 03-15, 03-16, 03-17 | COVERED |

## Decisões do CONTEXT

| IDs | Tema | Planos | Status |
|---|---|---|---|
| D-53–D-55 | Hierarquia genérica e formato do evento | 03-01, 03-02, 03-04, 03-12 | COVERED |
| D-56–D-58 | Organização permanente, equipe por edição, atleta global e favorito | 03-01, 03-05, 03-06 | COVERED |
| D-59–D-61 | Escopo exato, rascunho, preview, publicação, concorrência e uma pessoa | 03-01, 03-02, 03-03, 03-10, 03-16, 03-17 | COVERED |
| D-62–D-63 | Agenda pública e local/espaço opcional | 03-01, 03-02, 03-03, 03-04, 03-11 | COVERED |
| D-64–D-65 | Bracket derivado e classificação manual | 03-12, 03-13 | COVERED |
| D-66–D-68 | Fonte, resultado confirmado, histórico e contrato da Fase 6 | 03-01, 03-10, 03-13, 03-17 | COVERED |
| D-69–D-71 | Atleta sem conta, matrícula/HMAC e proposta explícita | 03-06, 03-07 | COVERED |
| D-72–D-75 | Elenco e CSV | 03-08, 03-09 | COVERED |
| D-76–D-78 | Perfil, estatísticas genéricas, privacidade e links | 03-11, 03-14, 03-15, 03-17 | COVERED |
| D-79–D-80 | Vínculo não bloqueia apostas; relação preservada | 03-06, 03-08, 03-10, 03-17 | COVERED |

## Pesquisa e checkpoints

| Item | Planos | Status |
|---|---|---|
| Tracer completo administrador geral → escopo → rascunho → publicação → agenda anônima | 03-02, 03-03 | COVERED |
| Spring Modulith acíclico e portas pertencentes ao consumidor | 03-02, 03-06, 03-15, 03-17 | COVERED |
| DDL inicial e identidade de versão confirmada como decisões one-way | 03-01 | COVERED |
| Commons CSV 1.14.1 e AWS SDK BOM/S3/URLConnection 2.54.5 com aprovação separada antes do POM | 03-09, 03-15 | COVERED |
| OpenAPI/cliente gerado sem edição manual e inventário/digest humano | 03-02–03-17 | COVERED |
| Publicação imutável distinta de optimistic locking | 03-02, 03-08, 03-10, 03-12, 03-13, 03-17 | COVERED |
| Capabilities por recurso persistido e workspace O1–O8 | 03-16 | COVERED |
| IDOR, PII pública, matrícula/HMAC, CSV, corrida, XSS/SSRF/mídia, auditoria e certificação | 03-02–03-17 | COVERED |

## UI: 20 superfícies / 156 considerações

| Superfícies | Planos | Categorias explícitas atribuídas |
|---|---|---|
| shell/navegação, agenda | 03-03, 03-11, 03-16, 03-17 | empty, loading, error, populated, partial, overflow, zero-one-many, long-text |
| competição/edição, evento | 03-03, 03-04, 03-10, 03-11, 03-17 | mesmas oito categorias aplicáveis |
| organização, equipe, favorito | 03-05, 03-11, 03-17 | mesmas oito categorias aplicáveis |
| atleta, proposta, perfil/foto | 03-06, 03-07, 03-11, 03-14, 03-15, 03-17 | mesmas oito categorias aplicáveis |
| chaveamento, classificação/fontes | 03-10, 03-12, 03-13, 03-17 | mesmas oito categorias aplicáveis |
| seleção de escopo, catálogo, organizações/equipes operacionais | 03-03, 03-04, 03-05, 03-16, 03-17 | mesmas oito categorias aplicáveis |
| atletas/elencos, CSV, eventos/resultados | 03-06, 03-08, 03-09, 03-10, 03-16, 03-17 | mesmas oito categorias aplicáveis |
| chaveamento/classificação operacional, fila de revisão | 03-07, 03-12, 03-13, 03-16, 03-17 | mesmas oito categorias aplicáveis |

As contagens canônicas permanecem as do `03-UI-SPEC.md`: empty 19, loading 20, error 20, populated 19, partial 19, overflow 20, zero-one-many 19 e long-text 20, total 156. Os PLANs citam as superfícies e incorporam os estados relevantes em `must_haves.truths`; este arquivo preserva a igualdade global e impede dupla contagem silenciosa.
