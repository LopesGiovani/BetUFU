---
phase: 03-experiencia-esportiva-do-piloto
plan: 03-01
decision: confirmed-result-version-and-favorite-migration
status: approved
approved_at: 2026-08-27
---

# Decisao aprovada: versao confirmada e favorito preservador

## Escopo da aprovacao

Esta decisao foi aprovada pelo responsavel em 2026-08-27. Ela fixa a identidade
historica que a Fase 6 consumira e a estrategia de reconciliacao de
`favorite_team_id` com organizacao esportiva.

Ela nao autoriza migration, alteracao de entity, contrato HTTP, banco, stage ou
commit.

## Tres identidades diferentes

O modelo deve manter separados:

1. `eventId`: UUID estavel do evento ao longo de toda a vida;
2. `lockVersion`: contador tecnico do rascunho usado apenas para detectar edicao
   concorrente;
3. `confirmedVersionId`: UUID imutavel de uma versao confirmada especifica do
   resultado.

`lockVersion` nunca e usado como identidade historica. Um `@Version` maior nao
significa que o conteudo anterior foi preservado.

## Contrato de `confirmedVersionId`

Uma versao de resultado confirmada e append-only:

- recebe UUID proprio gerado pelo servidor;
- referencia o `eventId` estavel;
- registra numero monotono por evento;
- registra a versao de evento/publicacao contra a qual o resultado foi informado;
- preserva placar ou dados genericos do resultado, fonte e instante;
- registra a versao anterior que substitui, quando for correcao;
- nunca recicla UUID, nunca sobrescreve a versao anterior e nunca depende de uma
  consulta por "resultado atual" para ser interpretada.

A projecao publica pode apontar para a versao confirmada vigente. Atualizar esse
ponteiro nao altera a versao anterior.

## Estados e supersessao

O ciclo detalhado sera implementado no plano de eventos/resultados, mas deve
preservar desde agora estas regras:

- `INFORMADO` e `AGUARDANDO_REVISAO` ainda nao sao versoes consumiveis pela
  liquidacao;
- `CONFIRMADO` produz um `confirmedVersionId` referenciavel;
- corrigir um resultado confirmado cria outra versao e liga
  `supersedesConfirmedVersionId` a anterior;
- a versao substituida continua legivel no historico;
- cancelamento, reversao ou reliquidacao futura adicionam fatos e nunca apagam a
  cadeia anterior.

O termo `superseded` significa que uma versao deixou de ser a vigente, nao que ela
foi removida.

## Atores separados

O historico preserva responsabilidades diferentes:

- `recordedByIdentityId`: quem informou o resultado;
- `confirmedByIdentityId`: quem confirmou a fonte e o resultado;
- `authorizedForSettlementByIdentityId`: quem futuramente autorizou aquela versao
  exata para liquidacao.

Cada ator possui seu proprio instante e referencia. No MVP os UUIDs podem ser da
mesma pessoa, mas nunca sao comprimidos em um unico campo `actor_id`.

A autorizacao para liquidacao pertence a uma fronteira futura e deve referenciar
o `confirmedVersionId` exato. A Fase 3 nao movimenta credito e nao antecipa uma
tabela vazia da Fase 6.

## Fonte e correcao

- Resultado confirmado exige fonte oficial persistida.
- Informacao verbal ou mensagem informal pode gerar pendencia, mas nao
  `confirmedVersionId` publico.
- Correcao referencia a fonte anterior e a fonte corretiva ou um motivo
  auditavel aplicavel.
- A fonte anterior permanece consultavel.
- A API publica expoe somente campos autorizados da versao publicada; motivo
  operacional interno nao vaza por conveniencia.

## Contrato para a Fase 6

A futura liquidacao recebe e persiste o `confirmedVersionId` autorizado. Ela nao:

- consulta apenas `eventId` para descobrir silenciosamente o resultado vigente;
- reconfirma placar;
- transforma `lockVersion` em versao financeira;
- muda de versao se uma correcao esportiva for publicada depois.

Uma correcao posterior inicia um fluxo explicito de revisao, reversao ou
reliquidacao. Ela nunca troca a referencia de uma liquidacao anterior sem novo
registro auditado.

## Estado atual do favorito

O baseline medido possui:

- `betufu_profile.favorite_team_id uuid NULL`;
- nenhuma FK para equipe ou organizacao;
- getter Java, mas nenhum comando de producao que grave o campo;
- teste que comprova que qualquer UUID e aceito pela estrutura da Fase 2;
- contrato publico atual que deliberadamente nao expoe `favoriteTeamId`.

Portanto, um UUID nao nulo nao prova que exista equipe ou organizacao
correspondente.

## Inventario obrigatorio antes da migration

A migration de organizacoes deve medir, no mesmo banco alvo e antes de transformar
dados:

```sql
SELECT count(*)
FROM public.betufu_profile
WHERE favorite_team_id IS NOT NULL;
```

O resultado e a estrategia escolhida devem ficar registrados na evidencia da
migration. Nao se presume zero apenas porque o aplicativo atual nao grava o campo.

## Estrategia preservadora em duas rotas

### Rota A: nenhum valor legado

Se o inventario provar zero linhas nao nulas:

- renomear `favorite_team_id` para `favorite_organization_id` por forward
  migration;
- atualizar a entity para `favoriteOrganizationId`;
- adicionar FK para a organizacao esportiva quando a tabela existir;
- manter a coluna anulavel;
- publicar somente a nova semantica no contrato.

Essa rota e permitida somente com a contagem zero registrada.

### Rota B: existem valores legados

Se existir qualquer UUID nao nulo:

- adicionar `favorite_organization_id uuid NULL` como coluna nova;
- manter `favorite_team_id` temporariamente como legado inerte para rollback e
  reconciliacao;
- nao copiar UUID por nome, slug, igualdade acidental ou formato;
- preencher a nova coluna somente quando uma relacao autoritativa persistida
  produzir exatamente uma organizacao;
- manter `favorite_organization_id = NULL` quando nao houver prova unica;
- registrar contagens de convertido, ambiguo e sem correspondencia;
- remover a coluna legada apenas em migration posterior, depois de verificacao e
  novo checkpoint quando houver dado material.

O perfil, nickname e demais campos permanecem intactos mesmo quando o favorito nao
puder ser convertido.

## O que constitui prova de correspondencia

Aceitavel:

- FK ou tabela de mapeamento autoritativa criada durante a importacao governada;
- uma antiga equipe identificada por UUID e ligada de forma unica a uma
  organizacao persistida.

Proibido:

- comparar nome, apelido, slug ou texto aproximado;
- escolher a primeira organizacao encontrada;
- reutilizar o UUID arbitrario como UUID de organizacao;
- converter valor ambiguo;
- apagar o perfil para resolver o conflito.

## Contrato publico

- O nome publico futuro e `favoriteOrganizationId` ou uma projecao de organizacao
  equivalente.
- `favoriteTeamId` nao sera reaproveitado com significado diferente.
- APIs antigas nao passarao a devolver organizacao sob um nome enganoso.
- IDs internos, valores legados e detalhes de reconciliacao nao fazem parte do DTO
  publico.

## Concorrencia e integridade

- A escolha futura de favorito valida a organizacao persistida e autorizada antes
  de gravar.
- A FK impede favorito para organizacao inexistente.
- Atualizacoes concorrentes seguem a politica de perfil vigente ou recebem
  controle otimista explicito; last-write-wins silencioso nao e inferido por esta
  decisao.
- Migration e aplicacao precisam tolerar rollout em etapas sem interpretar a
  coluna legada como nova semantica.

## Forward-fix e rollback conceitual

- V3 nunca sera editada.
- Divergencia recebe migration aditiva.
- Na Rota B, manter a coluna antiga durante a janela de compatibilidade permite
  rollback da aplicacao sem inventar mapeamento.
- Dados confirmados e versoes de resultado nunca sao apagados durante rollback de
  aplicacao.
- Qualquer remocao posterior da coluna legada exige nova medicao e prova de que
  nao ha valor pendente de reconciliacao.

## Aprovacao

`approved`

O responsavel aprovou `confirmedVersionId` imutavel, supersessao aditiva, atores
separados e a estrategia preservadora de favorito com `NULL` quando nao existir
mapeamento seguro. A aprovacao nao autoriza implementar schema ou contrato.
