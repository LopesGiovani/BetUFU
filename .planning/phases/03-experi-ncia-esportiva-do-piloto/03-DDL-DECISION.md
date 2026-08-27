---
phase: 03-experiencia-esportiva-do-piloto
plan: 03-01
decision: initial-sports-ddl
status: approved
approved_at: 2026-08-27
---

# Decisao aprovada: ER e DDL esportivo inicial

## Escopo da aprovacao

Esta decisao foi aprovada pelo responsavel em 2026-08-27 para orientar a primeira
migration esportiva. Ela fixa identidades, cardinalidades, constraints, limites
modulares e a estrategia de correcao. Ela nao cria migration, tabela, endpoint ou
codigo de producao.

O primeiro schema esportivo sera aditivo. As migrations V1-V8 e V5.1 permanecem
imutaveis.

## Interpretacao da hierarquia generica

A sequencia de navegacao aprovada em D-53 continua sendo:

```text
Esporte -> Competicao -> Edicao -> Categoria -> Fase -> Evento
```

Ela nao significa que uma competicao possa possuir apenas um esporte. No modelo
fisico, `competition_id` e a raiz administrativa e de autorizacao. Uma competicao
pode oferecer varios esportes por uma associacao de catalogo adicionada na expansao
posterior.

Isso permite representar corretamente, sem modelos especiais:

- Olimpiada Universitaria UFU -> edicao 2026 -> futsal, volei, judo e outros;
- Brasileirao -> uma edicao anual -> futebol;
- Libertadores -> uma edicao anual -> futebol.

O esporte continua sendo filtro e contexto obrigatorio onde aplicavel, mas nunca
substitui o UUID da competicao usado no `scopeRef`.

## Identidades publicas

- Toda raiz e todo recurso publicado usam UUID opaco gerado pelo servidor.
- O UUID e a identidade canonica de URL, FK, auditoria e autorizacao.
- Slugs sao identificadores amigaveis secundarios e podem mudar sem alterar a
  identidade do recurso.
- Slugs sao unicos no pai apropriado, usando normalizacao deterministica.
- IDs sequenciais, nomes e slugs nunca sao usados como prova de identidade entre
  recursos ou como `scopeRef`.

## ER da primeira migration esportiva

A primeira migration, planejada como `V9__create_sports_tracer.sql`, entrega apenas
o tracer permanente. Esporte, categoria, fase, local, organizacao, equipe, atleta,
elenco e resultado completo entram por migrations posteriores.

```text
sports_competition
  1 ─── N sports_edition

sports_edition
  1 ─── N sports_event

sports_event
  1 ─── N sports_event_slot
  1 ─── N sports_event_publication

sports_event_publication
  1 ─── N sports_event_publication_slot
  0..1 ─── supersede uma publicacao anterior do mesmo evento
```

### `sports_competition`

Responsabilidade: raiz administrativa e valor real do escopo operacional.

Campos minimos:

- `competition_id uuid` como PK e ID publico;
- `name`, `slug` e estado operacional minimo;
- `lock_version bigint` para concorrencia otimista;
- `created_at` e `updated_at` com precisao de microssegundos.

Constraints:

- nome e slug nao vazios e limitados por bytes;
- slug normalizado unico globalmente no tracer;
- `lock_version >= 0`;
- `updated_at >= created_at`.

### `sports_edition`

Responsabilidade: edicao historica pertencente a uma competicao, como 2026.

Campos minimos:

- `edition_id uuid` como PK e ID publico;
- `competition_id uuid` como FK obrigatoria;
- `name`, `slug`, datas opcionais de inicio/fim e estado operacional minimo;
- `lock_version bigint`, `created_at` e `updated_at`.

Constraints:

- `UNIQUE (competition_id, slug_normalizado)`;
- `UNIQUE (competition_id, edition_id)` para FKs compostas dos filhos;
- data final ausente ou maior/igual a data inicial;
- timestamps e `lock_version` validos.

### `sports_event`

Responsabilidade: estado de trabalho editavel do evento.

Campos minimos:

- `event_id uuid` como PK e ID publico estavel;
- `competition_id` e `edition_id` obrigatorios;
- titulo operacional, formato do evento e estado `RASCUNHO` inicial;
- instante canonico UTC e `zone_id` IANA;
- estado de local que aceite `LOCAL_A_DEFINIR` sem criar local ficticio;
- referencia textual da fonte usada no tracer;
- `lock_version bigint`, `created_at` e `updated_at`.

Constraints:

- FK composta `(competition_id, edition_id)` garante que a edicao pertence ao
  mesmo escopo da competicao;
- formato limitado inicialmente a valores explicitamente suportados pelo tracer;
- evento nao publicado pode ser editado somente contra a `lock_version` vigente;
- timestamps e campos textuais possuem checks de ordem, ausencia e tamanho.

### `sports_event_slot`

Responsabilidade: participantes/posicoes do estado de trabalho sem antecipar a
tabela de equipes das migrations posteriores.

Campos minimos:

- `event_id` como FK;
- `slot_no` com ordenacao estavel;
- rotulo de exibicao obrigatorio no tracer;
- futura referencia tipada permanece ausente ate existir a entidade correspondente.

Constraints:

- PK ou unique em `(event_id, slot_no)`;
- nenhuma associacao por nome ou slug e tratada como identidade;
- `HEAD_TO_HEAD` exige exatamente dois slots na regra de dominio e no teste da V9.

Quando equipes forem introduzidas, uma forward migration adicionara a referencia
tipada. Os rotulos publicados permanecem como fotografia historica e nao serao
reinterpretados como FKs.

### `sports_event_publication`

Responsabilidade: versao publica append-only do evento.

Campos minimos:

- `publication_id uuid` como ID publico imutavel da versao;
- `event_id uuid` como FK para a identidade estavel;
- `version_no bigint` monotono por evento;
- snapshot dos campos publicos do evento;
- `published_by_identity_id` como FK para `local_identity`;
- `published_at`;
- `supersedes_publication_id` opcional;
- `base_lock_version` que registra qual revisao de trabalho originou a publicacao.

Constraints:

- `UNIQUE (event_id, version_no)` e `UNIQUE (event_id, publication_id)`;
- a supersessao referencia publicacao do mesmo evento;
- `version_no > 0` e `base_lock_version >= 0`;
- publicacoes nao sao atualizadas ou apagadas por comandos normais.

### `sports_event_publication_slot`

Responsabilidade: snapshot imutavel dos participantes exibidos naquela publicacao.

Constraints:

- FK para `sports_event_publication`;
- unique em `(publication_id, slot_no)`;
- alteracao posterior do rascunho ou do nome de uma futura equipe nao reescreve a
  publicacao anterior.

## Concorrencia e historico

`lock_version` e versao publicada resolvem problemas diferentes:

- `lock_version` detecta duas pessoas tentando salvar o mesmo rascunho;
- `publication_id` e `version_no` preservam exatamente o que foi publicado;
- publicar cria uma nova linha; nunca transforma a linha anterior;
- conflito de `lock_version` deve produzir `409`, sem last-write-wins.

`@Version` nunca sera usado como identidade historica consumida por outras fases.

## Escopo e fronteiras Modulith

Todo recurso administrativo deve carregar ou derivar `competition_id` por FKs
persistidas. O backend carrega o recurso antes de comparar o papel e o `scopeRef`;
o UUID enviado pelo navegador nunca e autoridade.

Grafo permitido para o tracer:

```text
identity.internal -> sports::CompetitionScopeCatalog
sports.internal   -> platform.identity
sports.internal   -> audit
platform.security -> namespace HTTP literal validado por teste
```

Grafo proibido:

```text
sports -> identity.internal
sports -> verification.internal
platform -> sports apenas para importar constante de controller
```

`CompetitionScopeCatalog` pertence a API estreita de `sports` e permite que a
concessao de `ADMINISTRADOR_COMPETICAO` rejeite um UUID inexistente sem importar
classes internas ou repositories esportivos.

## Limites das migrations

V9 entrega somente:

- competicao e edicao minimas;
- rascunho de evento e slots do tracer;
- publicacao imutavel do evento e snapshot dos slots;
- constraints de escopo, concorrencia e historico necessarias ao tracer.

Migrations posteriores entregam, em fatias verificaveis:

- associacao de esportes, categoria, fase e locais reutilizaveis;
- organizacoes permanentes, equipes por edicao e favorito;
- atletas, HMAC, propostas, elencos e CSV;
- ciclo completo de evento, fontes e resultados confirmados;
- chaveamentos, classificacoes e perfil esportivo.

Nenhuma tabela vazia ou FK especulativa sera criada apenas para reservar o futuro.

## Favorito preservador

Esta decisao nao migra `favorite_team_id`. Ela fixa a precondicao:

- converter somente quando uma relacao persistida e inequivoca provar a
  organizacao correspondente;
- nunca converter por nome, slug, posicao ou similaridade textual;
- manter `NULL` quando nao houver correspondencia segura;
- preservar o restante do perfil mesmo quando o favorito nao puder ser convertido.

O inventario real e a migration do favorito pertencem ao plano que criar
organizacoes e equipes.

## Forward-fix e rollback conceitual

- Migration aplicada nunca e editada.
- Defeito de schema recebe nova migration de forward-fix.
- Rollback de aplicacao pode deixar tabelas novas inertes, mas nao apaga dados nem
  versoes publicadas.
- Uma migration destrutiva futura exige inventario, backup/restauracao provada e
  novo checkpoint humano.
- Slug pode ser corrigido sem trocar UUID.
- Uma versao publica incorreta e substituida por nova versao, nunca apagada.

## Aprovacao

`approved`

O responsavel aprovou o ER/DDL inicial, os UUIDs publicos, a competicao como raiz
multi-esporte, os slugs secundarios, `lock_version`, publicacao append-only,
fronteiras Modulith e a estrategia de forward-fix. A aprovacao nao autoriza criar
V9, editar codigo, executar banco, preparar stage ou criar commit.
