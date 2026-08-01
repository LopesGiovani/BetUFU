# Pesquisa de Arquitetura

**Domínio:** aplicação web responsiva de palpites esportivos sociais com créditos exclusivamente virtuais
**Projeto:** BETUFU
**Pesquisado em:** 2026-08-01
**Confiança:** ALTA para transações, concorrência, autorização, realtime e armazenamento; MÉDIA para os limiares de escala, que dependem de carga real

## Recomendação executiva

Construir o MVP como **um monólito modular TypeScript/Next.js, um banco PostgreSQL como fonte única de verdade e um pequeno executor de tarefas do mesmo código-base**. Os módulos devem ser separados por capacidade de negócio e possuir contratos explícitos, mas continuar no mesmo processo e na mesma transação quando a operação exige consistência — especialmente aposta, ledger, publicação de odds, resultado, liquidação e auditoria.

Não iniciar com microsserviços. A BETUFU terá uma equipe e uma operação inicialmente pequenas, um único piloto e fluxos que cruzam vários domínios de forma transacional. Separá-los por rede transformaria invariantes locais em problemas de consistência distribuída, descoberta, versionamento e observabilidade. A documentação da Microsoft registra que cada microsserviço pode ser simples enquanto o sistema completo fica mais complexo, com desafios específicos de consistência e transações; a experiência publicada pela Shopify mostra a alternativa de manter uma unidade de implantação com fronteiras estritas entre domínios. A extração futura deve ocorrer somente diante de pressão medida de escala ou autonomia operacional, não por antecipação.

**Decisões firmes para o MVP:**

| Decisão | Consequência prática |
|---|---|
| Um monólito modular, uma implantação web | Uma operação pode atravessar módulos em uma transação ACID sem chamadas HTTP internas. |
| PostgreSQL é a fonte de verdade | Saldo, apostas, odds aceitas, resultados, chat, elegibilidade e auditoria sobrevivem à perda de cache, worker ou conexão realtime. |
| Escritas críticas passam pelo servidor | O navegador nunca grava diretamente ledger, apostas, odds, resultados, papéis ou moderação. Server Actions e Route Handlers são tratados como endpoints públicos e repetem autenticação, autorização e validação. |
| Consistência forte no núcleo; consistência eventual nas projeções | Aceitar aposta e movimentar créditos é atômico; ranking, conquistas, broadcast e limpeza podem ocorrer depois e ser repetidos. |
| Registros publicados são imutáveis e versionados | Corrigir resultado ou liquidação cria versão/reversão; não reescreve o passado. |
| Realtime é transporte, não armazenamento | Todo cliente recupera o estado durável pela API ao entrar ou reconectar. |
| Sem Redis, Kafka ou fila externa inicialmente | PostgreSQL, outbox e Cron atendem as tarefas curtas do piloto; adicionar um worker Postgres-backed apenas quando métricas justificarem. |
| Documento de matrícula é efêmero | Bucket privado, acesso assinado curto, exclusão via Storage API com estado `deletion_pending`, retry e reconciliação. |

## Arquitetura padrão

### Visão geral do sistema

```text
┌──────────────────────────────── Navegador ────────────────────────────────┐
│ UI responsiva │ Supabase Auth │ WebSocket Realtime │ upload assinado     │
└───────────────┬──────────────────────┬──────────────────────┬──────────────┘
                │ HTTPS                │ recebe eventos       │ bytes direto
                ▼                      ▼                      ▼
┌──────────────────────── Monólito modular Next.js ────────────────────────┐
│ App Router / BFF                                                        │
│ autentica → autoriza → valida → chama um caso de uso → devolve DTO       │
│                                                                          │
│ ┌─────────┐ ┌────────┐ ┌─────────┐ ┌────────┐ ┌──────────┐              │
│ │ Acesso  │ │ Esporte│ │Mercados │ │Apostas │ │  Ledger  │              │
│ └─────────┘ └────────┘ └─────────┘ └────────┘ └──────────┘              │
│ ┌────────────┐ ┌──────────┐ ┌─────────┐ ┌───────────┐ ┌─────────────┐   │
│ │Verificação │ │Liquidação│ │  Social │ │Engajamento│ │  Auditoria  │   │
│ └────────────┘ └──────────┘ └─────────┘ └───────────┘ └─────────────┘   │
│                       contratos internos, sem HTTP                       │
└───────────────────────────────┬──────────────────────────────────────────┘
                                │ SQL transacional
                                ▼
┌──────────────────────── PostgreSQL / Supabase ───────────────────────────┐
│ schemas por módulo │ constraints │ ledger │ audit │ outbox │ projeções   │
│ triggers de broadcast com payload mínimo │ RLS nos recursos expostos    │
└──────────────┬─────────────────────────────┬───────────────────────────────┘
               │ WAL/Broadcast               │ trabalho pendente
               ▼                             ▼
┌─────────────────────────┐      ┌─────────────────────────────────────────┐
│ Supabase Realtime       │      │ Executor/Cron do mesmo código-base     │
│ canais privados + RLS   │      │ limpeza, expiração, retry, rebuild     │
└─────────────────────────┘      └──────────────────┬──────────────────────┘
                                                    │ Storage API
                                                    ▼
                                      ┌───────────────────────────────┐
                                      │ Supabase Storage privado      │
                                      │ comprovantes temporários      │
                                      └───────────────────────────────┘
```

O executor pode começar como uma função curta disparada pelo Supabase Cron. Ele pertence ao mesmo repositório, usa os mesmos casos de uso e não é um microsserviço. Se tarefas duráveis crescerem, o mesmo entrypoint pode se tornar um processo worker independente sem mover a propriedade dos dados.

### Fontes de verdade e dados derivados

| Informação | Fonte de verdade | Derivados descartáveis |
|---|---|---|
| Credenciais e sessão | Supabase Auth | cookie/sessão no servidor |
| Perfil, papéis, suspensões e elegibilidade | PostgreSQL, módulo Acesso | DTO de sessão, cache curto |
| Calendário, equipes, atletas e partidas | PostgreSQL, módulo Esporte | páginas cacheadas e índices de busca |
| Odds publicadas e histórico | versões imutáveis no PostgreSQL | odd atual em read model/cache |
| Aposta e odd aceita | aposta referenciando `odds_quote_id` imutável | bilhete renderizado |
| Créditos | journal/postings do Ledger | saldo materializado na conta |
| Resultados e liquidações | versões de resultado + lançamentos do Ledger | ranking, estatísticas, conquistas |
| Chat | mensagens e ações de moderação no PostgreSQL | evento websocket, contador online |
| Comprovante | objeto temporário no bucket privado | URL assinada de poucos minutos |
| Status da verificação | metadados mínimos no PostgreSQL | badge no perfil |
| Trilha administrativa | eventos append-only de auditoria | dashboard e exportação de consulta |

### Responsabilidades dos componentes

| Componente/módulo | O que possui | Não deve possuir |
|---|---|---|
| **Identidade e Acesso** | vínculo entre usuário Auth e conta; perfil; papéis; permissões; suspensões; política de elegibilidade; relacionamento administrador-evento | senha, saldo, regra de mercado |
| **Verificação estudantil** | solicitações, fingerprint da matrícula, período/expiração, decisão e ciclo de vida do documento | bytes permanentes do comprovante ou matrícula em claro após decisão |
| **Catálogo Esportivo** | competição, edição, modalidade, categoria, participante, equipe, atleta, fase, partida/evento e afiliações com vigência | odds, saldo ou liquidação |
| **Mercados e Odds** | tipos de mercado, seleções, recomendação em rascunho, conjunto publicado imutável, fechamento e histórico | aposta do usuário ou lançamento de crédito |
| **Apostas** | bilhete simples, seleção, stake, odd aceita, retorno potencial, estado e decisão de elegibilidade no aceite | mutação direta de saldo ou cálculo de ranking |
| **Ledger** | contas, transações, postings, reversões, projeção de saldo e política de arredondamento | regra esportiva ou decisão sobre vencedor |
| **Resultados e Liquidação** | versões de resultado, execução de liquidação, outcome de cada aposta, void e correção | edição destrutiva de lançamento anterior |
| **Social e Moderação** | salas por partida, mensagem persistida, denúncia, mute/ban e ação de moderação | broadcast autoritativo enviado pelo cliente |
| **Rankings e Conquistas** | projeções reconstruíveis, ligas privadas, membros e badges concedidos | saldo autoritativo ou resultado canônico |
| **Auditoria** | evento semântico de ação relevante, ator, razão, alvo, correlação e diff sanitizado | conteúdo de comprovante, token, URL assinada ou matrícula em claro |
| **Integrações/Operações** | outbox, jobs, retries, Storage, Realtime, observabilidade | regra de negócio duplicada |
| **Admin UI** | orquestra casos de uso dos módulos e exibe filas de trabalho | tabelas próprias que dupliquem os domínios administrados |

### Limites e dependências internas

Os módulos expõem somente um `public.ts`/`index.ts` com comandos, consultas e tipos de contrato. Importar repositórios, tabelas ou entidades internas de outro módulo deve falhar no lint/teste de arquitetura.

```text
Identidade/Acesso ◄──── Verificação
       ▲
       ├──────── Catálogo Esportivo ◄──── Social
       │                ▲
       │                └──── Mercados/Odds ◄──── Apostas ────► Ledger
       │                           ▲                 │
       └───────────────────────────┴──── Liquidação ─┘
                                                   │
                                                   └────► Rankings/Conquistas

Todos os módulos ──► porta append-only de Auditoria
Todos os produtores de efeito eventual ──► Outbox
```

Regras para comunicação:

1. **Chamada síncrona por contrato** quando a resposta decide o commit atual. Exemplo: Apostas consulta política de Acesso, lê a odd publicada de Mercados e solicita um lançamento ao Ledger dentro da mesma transação.
2. **Evento de domínio/outbox** quando o consumidor pode ficar momentaneamente atrasado. Exemplo: `BetSettled` atualiza ranking, conquista e broadcast.
3. **Nunca HTTP entre módulos do mesmo monólito.** Rede só para integrações externas.
4. **Um módulo não escreve na tabela de outro.** Uma foreign key entre schemas pode preservar integridade, mas a mutação passa pelo contrato do dono.
5. **Sem dependência circular.** Fluxos que atravessam módulos são coordenados na camada de aplicação, não fazendo um domínio importar o outro em ambas as direções.

## Modelo estrutural e invariantes

### Catálogo extensível de modalidades

Não criar uma tabela `matches` cheia de colunas opcionais específicas de cada esporte nem condicionar o núcleo com `if (sport === ...)`. O núcleo deve modelar um **evento de confronto**, seus participantes ordenados, horário, fase e estado. Regras específicas entram por contratos:

```typescript
interface SportRuleAdapter {
  validateResult(input: unknown): CanonicalResult
  supportedMarketTypes(): MarketType[]
  settle(market: PublishedMarket, result: CanonicalResult): SelectionOutcome[]
  extractOddsFeatures(history: EventHistory): FeatureSnapshot
}
```

O payload específico pode ser `jsonb`, mas precisa de schema versionado e validação no adaptador; não é um depósito de dados sem contrato. Confrontos diretos usam o adaptador inicial. Provas multicompetidor adicionam outro adaptador e tipos de mercado sem alterar Ledger, Apostas ou Auditoria.

### Odds imutáveis e apostas vinculadas à versão

Estrutura conceitual:

```text
market
  ├── market_selection
  ├── odds_recommendation (rascunho, modelo e features)
  └── odds_set v1, v2, v3 ... (publicado, imutável)
          └── odds_quote por seleção

bet ──► odds_quote exata aceita
```

Publicar odds deve, numa única transação, bloquear o mercado para escrita, inserir um novo `odds_set`, inserir todas as cotações, atualizar o ponteiro da versão corrente, registrar auditoria e produzir o evento. Não alterar uma versão publicada. Guardar `source`, `model_version`, qualidade/amostra dos dados, snapshot das features, ator revisor, razão e `published_at`.

A odd usa `NUMERIC(precision, scale)`, nunca `float`/`double` nem `number` JavaScript para cálculo final. A documentação do PostgreSQL distingue `numeric` exato dos tipos de ponto flutuante inexatos. O retorno potencial é calculado no servidor com uma política única de arredondamento, armazenado no aceite e recalculável a partir de stake + quote.

### Ledger append-only de partidas dobradas

O ledger não é uma coluna `users.balance` atualizada livremente. Recomenda-se:

```text
ledger_account       user_available, bet_escrow, system_issuance/house
ledger_transaction   grant, stake, payout, loss, void, reversal
ledger_posting       transaction_id, account_id, amount_atomic
wallet_balance       projeção transacional para leitura rápida
```

- Créditos são `BIGINT` em uma unidade atômica fixa; o produto decide uma vez quantas unidades formam um crédito exibido.
- Cada transação tem dois ou mais postings cuja soma é zero. Uma função/repositório único do Ledger valida e grava postings + projeção na mesma transação.
- Conta de usuário nunca fica negativa; contas sistêmicas explicitamente configuradas podem ficar.
- `business_reference` e `idempotency_key` são únicos.
- Corrigir não faz `UPDATE` nem `DELETE`: cria transação `reversal_of` e, se necessário, um novo lançamento.
- `wallet_balance` é otimização; o journal é reconstruível e auditável.

### Resultados e liquidação corrigíveis

Usar `event_result_version` imutável e `settlement_run`. Para cada aposta, uma linha `bet_settlement` referencia a versão do resultado, outcome, payout e transação do Ledger. Restrições únicas como `(bet_id, settlement_run_id)` e `ledger_transaction.idempotency_key` tornam replay seguro.

No piloto, uma execução pequena pode liquidar sincronicamente em transações curtas. Ainda assim, o modelo deve registrar uma execução retomável. Se o volume tornar a transação longa, o mesmo caso de uso passa a consumir lotes com `FOR UPDATE SKIP LOCKED`; não é necessário redesenhar o domínio. Uma correção de resultado cria nova versão, reverte os lançamentos anteriores e liquida novamente, sempre com motivo administrativo obrigatório.

### Autorização: RBAC para função, ABAC/ReBAC para contexto

Papéis base: `user`, `student_verified`, `moderator`, `event_admin`, `super_admin`. Eles não bastam para as regras da BETUFU. A decisão também precisa dos atributos e relacionamentos:

- verificação válida no instante da aposta;
- atleta não pertence à equipe participante;
- administrador não gerencia a partida/mercado;
- usuário não está suspenso ou silenciado;
- membro pertence à disputa privada solicitada;
- mercado está aberto e o horário de fechamento ainda não passou.

Centralizar isso em políticas nomeadas como `canPlaceBet(actor, market, now)`, `canModerateRoom(actor, match)` e `canViewDocument(actor, request)`. Negar por padrão e testar cada entrada. A OWASP recomenda verificar permissão em toda requisição e prefere ABAC/ReBAC quando a decisão exige atributos e relações além de um papel.

RLS é defesa em profundidade para schemas/tabelas expostos, Storage e canais Realtime; não substitui as políticas do caso de uso. O PostgreSQL aplica default-deny quando RLS está ativo e nenhuma policy permite a linha, mas owner, superuser e papéis `BYPASSRLS` normalmente contornam RLS. Portanto, chaves `service_role`/secret permanecem somente no backend, e tabelas críticas não recebem escrita direta do navegador.

### Auditoria de negócio, separada de logs operacionais

Toda alteração de odds, resultado, verificação, papéis, créditos, suspensão e moderação grava `audit_event` **na mesma transação** da alteração principal. Campos mínimos:

```text
event_id, occurred_at_db, actor_type, actor_id, effective_role,
action, entity_type, entity_id, reason, before_sanitized, after_sanitized,
request_id, correlation_id, source_ip_hash/user_agent_reduced, app_version
```

A role normal de aplicação recebe `INSERT` e `SELECT` limitado, nunca `UPDATE/DELETE`, na tabela de auditoria. Testes de integração exigem evento de auditoria para os comandos sensíveis. Acesso e exportação da própria trilha também são auditados. Em produção, gerar digest periódico ou cópia para mídia/serviço somente leitura melhora detecção de adulteração; a OWASP recomenda registrar “quando, onde, quem e o quê”, restringir acesso e incorporar detecção de alteração.

Não registrar comprovante, matrícula, conteúdo integral de chat removido, token, cookie, secret, URL assinada ou corpo HTTP arbitrário. A OWASP recomenda remover, mascarar, hashear ou criptografar tokens e dados pessoais sensíveis nos logs.

## Estrutura de projeto recomendada

```text
src/
├── app/                         # composição HTTP/UI; sem regra de domínio
│   ├── (public)/                # calendário, equipes, partidas, rankings públicos
│   ├── (account)/               # perfil, bilhete, histórico, verificação
│   ├── admin/                   # telas operacionais
│   ├── api/                     # webhooks, upload finalize, health e jobs protegidos
│   └── actions/                 # adaptadores finos para comandos de aplicação
├── modules/
│   ├── identity-access/
│   ├── verification/
│   ├── sports/
│   ├── markets-odds/
│   ├── betting/
│   ├── ledger/
│   ├── settlement/
│   ├── social-moderation/
│   ├── engagement/
│   └── audit/
│       ├── domain/              # entidades, value objects, regras puras
│       ├── application/         # comandos, consultas, DTOs e portas
│       ├── infrastructure/      # Drizzle/SQL e adaptadores externos
│       ├── ui/                  # UI específica, quando houver
│       └── public.ts            # único contrato importável por outro módulo
├── platform/
│   ├── auth/                    # sessão Supabase e contexto do ator
│   ├── db/                      # pool, unit of work, helpers transacionais
│   ├── idempotency/             # request key + hash do payload
│   ├── realtime/                # tópicos e payloads mínimos
│   ├── storage/                 # signed URL, inspeção e remoção
│   ├── jobs/                    # lease, retry e métricas
│   ├── observability/           # logs, tracing, métricas, request ID
│   └── validation/              # schemas de borda
└── workers/
    ├── outbox-relay.ts
    ├── document-retention.ts
    ├── verification-expiry.ts
    └── projection-rebuild.ts

db/
├── migrations/                  # ordem global e revisável
├── schemas/                     # iam, sports, markets, betting, ledger, social, audit, ops
├── policies/                    # grants/RLS de recursos expostos, Storage e Realtime
├── functions/                   # operações SQL que protegem invariantes
└── seeds/                       # dados explícitos do piloto

tests/
├── unit/                        # regras puras
├── integration/                 # Postgres real + Storage/Realtime adaptados
├── concurrency/                 # corridas de aposta, odds e liquidação
├── architecture/                # imports e dependências permitidas
└── e2e/                         # jornadas mobile/desktop e admin
```

Organizar por capacidade de negócio reduz o acoplamento que aparece quando todo o projeto é separado apenas em `controllers/`, `services/` e `repositories/`. Schemas PostgreSQL por módulo reforçam propriedade, mas continuam no mesmo banco e podem participar da mesma transação. O schema `public` deve conter somente superfícies deliberadamente expostas; os domínios sensíveis ficam fora da Data API ou com grants/RLS explícitos.

## Padrões arquiteturais

### 1. Unidade de trabalho transacional explícita

Cada comando crítico recebe uma transação compartilhada pelos contratos dos módulos. Nenhum método inicia uma segunda conexão silenciosamente.

```typescript
await db.transaction(async (tx) => {
  const actor = await access.requireActor(tx, session)
  const quote = await markets.lockAcceptableQuote(tx, input.quoteId) // FOR SHARE
  await access.requireCanPlaceBet(tx, actor, quote.market, quote.acceptedAt)

  const request = await idempotency.claim(tx, actor.id, input.key, input)
  if (request.replayed) return request.previousResult

  const bet = await betting.create(tx, actor, quote, input.stake)
  await ledger.reserveStake(tx, bet.id, actor.id, input.stake)
  await audit.append(tx, auditBetAccepted(actor, bet, quote))
  await outbox.append(tx, domainEvent("BetAccepted", bet.id))
  return idempotency.complete(tx, request, bet.id)
})
```

O exemplo é ordem conceitual; na implementação, a política e os locks seguem uma ordem global documentada. Server Actions e Route Handlers apenas adaptam a entrada para esse comando.

### 2. Versionamento imutável + máquina de estados

Estados válidos são explícitos e transições passam por comandos:

```text
market: draft → published/open → closed → settling → settled
                                      └──────────────→ void
result: draft → confirmed → superseded
bet: accepted → won | lost | void
verification: uploaded → under_review → approved | rejected → document_deleted
```

Uma alteração administrativa não pula estados por `UPDATE` genérico. O comando valida precondição, versiona, registra motivo e auditoria. Isso evita “consertos” manuais que deixam ledger, ranking e histórico divergentes.

### 3. Idempotência no limite e no domínio

Toda escrita suscetível a retry recebe uma chave do cliente/operador e um hash do payload. Mesma chave + mesmo payload retorna o resultado anterior; mesma chave + payload diferente retorna conflito. A garantia real vem de índices `UNIQUE`, não de “consultar antes de inserir”. Liquidação, exclusão de documento, concessão de créditos, moderação e consumers de outbox também possuem chaves semânticas únicas.

### 4. Transactional outbox somente para efeitos pós-commit

Ao alterar estado e solicitar um efeito externo, inserir `outbox_event` na mesma transação. O relay lê apenas commits, tenta entregar e marca `processed_at`; consumers aceitam duplicatas. Esse padrão evita o dual-write “commitou no banco, falhou antes do broadcast/delete”. A AWS recomenda outbox para esse problema e alerta que consumidores devem ser idempotentes.

Não usar outbox para fugir de uma transação que deveria ser local. Débito + aposta + auditoria continuam síncronos. Usos adequados: ranking/conquista, broadcast, exclusão no Storage, exportação de auditoria e integrações futuras.

### 5. CQRS leve, sem event sourcing

Comandos protegem invariantes; consultas retornam DTOs moldados para cada tela. Ranking, desempenho recente e página de equipe podem ter tabelas de projeção reconstruíveis. Não adotar event sourcing completo: versões de domínio e ledger já preservam o histórico necessário, e reconstruir todo o produto a partir de eventos adicionaria custo sem valor no piloto.

### 6. Cache-aside apenas para leitura pública

Calendário publicado, páginas de equipes e rankings podem usar cache/CDN com invalidação por tag/evento. Nunca confiar em cache para saldo, autorização, fechamento ou odd aceita. A tela pode mostrar uma odd que acabou de mudar; o comando de aposta relê a versão atual no PostgreSQL e devolve uma resposta de “cotação alterada” sem debitar.

## Concorrência e transações

### Estratégia recomendada

Usar `READ COMMITTED` para a maioria dos comandos com locks e updates condicionais deliberados. PostgreSQL documenta que esse é o padrão e que cada comando vê um snapshot; operações mais complexas não podem assumir que dois `SELECT` sucessivos veem o mesmo mundo. `REPEATABLE READ`/`SERIALIZABLE` só devem ser usados quando simplificarem um invariante específico, sempre com retry de `40001`.

#### Aceite de aposta

1. Começar transação curta e obter horário do banco; nunca confiar no relógio do navegador.
2. Buscar `market` + quote corrente com `FOR SHARE`. Várias apostas obtêm shared lock simultaneamente, mas publicar/fechar (`UPDATE`) espera, evitando uma versão “meio trocada” sem serializar todos os apostadores.
3. Validar `accepted_at < closes_at`, estado aberto, versão corrente, estudante verificado e conflitos de interesse.
4. Reservar stake por `UPDATE wallet_balance SET available = available - :stake ... WHERE available >= :stake RETURNING ...`. O predicado atômico impede gasto duplo; não fazer `SELECT balance` seguido de `UPDATE` solto.
5. Inserir aposta referenciando a quote, postings do Ledger, auditoria, idempotência e outbox.
6. Commit; somente depois responder/broadcast.

O PostgreSQL documenta que `FOR SHARE` bloqueia `UPDATE/DELETE`, mas é compatível com outros `FOR SHARE`, e recomenda adquirir múltiplos locks em ordem consistente para evitar deadlocks.

#### Ordem global de locks

Para qualquer transação que precise de mais de um recurso:

```text
event/market → eligibility/membership → bet(s) → ledger account(s) em ordem de UUID → projection
```

Liquidações em lote ordenam contas antes de atualizar. Configurar `lock_timeout` e `statement_timeout`, manter transações sem I/O externo e aplicar retry com jitter para deadlock (`40P01`) e serialização (`40001`). Nunca esperar input humano com transação aberta.

#### Publicação e fechamento

- Publicação de nova odd e fechamento usam lock exclusivo da linha de mercado.
- `closes_at` é a barreira autoritativa. Um Cron pode atualizar o estado visual, mas atraso do Cron nunca reabre apostas.
- `accepted_at` é gerado pelo banco e persistido; “a requisição saiu do celular antes do horário” não é critério.
- Não expor seleções de outros usuários antes do fechamento. A query/read model devolve somente a própria aposta ou agregados que não revelem escolha; o canal realtime nunca transmite picks abertos.

#### Liquidação e correção

- Índice único garante no máximo uma liquidação por aposta/execução.
- A transação do Ledger usa chave `settlement:{run}:{bet}`.
- Reexecutar o comando não duplica saldo.
- Resultado corrigido cria `result_version` e postings compensatórios; jamais “ajusta saldo” manualmente.
- Em caso de lote grande, consumers competidores usam `FOR UPDATE SKIP LOCKED`, que a documentação do PostgreSQL reserva justamente para tabelas semelhantes a fila. A visão é inconsistente para consultas normais, portanto não usar esse modo fora do worker.

### Constraints que pertencem ao banco

- foreign keys e `NOT NULL` para todas as referências obrigatórias;
- `CHECK (stake_atomic > 0)`, odds dentro da faixa de produto e estados válidos;
- `UNIQUE (user_id, idempotency_key)` para aposta;
- `UNIQUE (market_id, version)` e `UNIQUE (odds_set_id, selection_id)`;
- `UNIQUE (registration_fingerprint)` para uma matrícula por identidade estudantil;
- `UNIQUE (bet_id, settlement_run_id)` e chave semântica única no Ledger;
- índices parciais para um único conjunto de odds corrente e um job ativo por chave;
- trigger/função transacional que rejeita journal desbalanceado.

Essas garantias não devem existir apenas no ORM. PostgreSQL fornece `UNIQUE`, foreign keys e `ON CONFLICT` com comportamento concorrente definido; migrations SQL são parte do produto.

## Fluxos de dados principais

### 1. Cadastro, sessão e autorização

```text
Navegador → Supabase Auth → sessão validada no servidor
          → IdentityAccess carrega conta/papéis/estado atual
          → policy avalia papel + atributos + relações
          → caso de uso autorizado
```

O JWT identifica; não torna verdade permanente papel, verificação ou suspensão que podem ter mudado. Decisões sensíveis consultam estado atual no banco. Middleware/proxy pode fazer redirect otimista, mas a autorização segura ocorre junto ao dado. O guia atual do Next.js recomenda uma DAL e exige tratar Server Actions/Route Handlers como endpoints públicos.

### 2. Upload e revisão de comprovante

```text
Conta autenticada
  → servidor cria verification_request + object key aleatória
  → servidor emite signed upload URL curta
  → navegador envia bytes ao bucket privado
  → finalize valida tamanho, magic bytes, tipo e posse; marca under_review
  → admin recebe signed read URL curta e registra decisão
  → transação salva status/fingerprint/expiração + audit + outbox delete
  → executor chama Storage API
  → sucesso: limpa object_key, grava deleted_at e audit
  → falha: mantém deletion_pending e tenta novamente
```

O bucket permite somente tipos e tamanho necessários, nomes gerados e acesso mínimo. A OWASP recomenda allowlist de extensão/tipo, não confiar em `Content-Type`, limitar tamanho, armazenar fora do webroot e restringir leitura. Documento deve passar por inspeção de conteúdo/magic bytes e, se viável, antimalware/sandbox antes da visualização administrativa.

Para prevenir duplicidade sem guardar a matrícula, normalizar a entrada no servidor e armazenar um **HMAC com pepper secreto**, não SHA simples de um identificador de baixa entropia. Após decisão, descartar valor em claro. A exclusão de bytes sempre usa Storage API: a documentação do Supabase alerta que apagar somente metadados por SQL deixa objeto órfão.

### 3. Publicação e aceite de odds

```text
Histórico esportivo → OddsRecommendation (rascunho + evidência)
Administrador revisa/edita → PublishOdds
  → nova versão imutável + auditoria + invalidação de cache
Usuário monta bilhete com quote_id
  → PlaceBet relê/compartilha lock do mercado
  → valida elegibilidade/horário/conflitos
  → aposta + Ledger + audit + outbox em um commit
```

O cliente envia `quote_id`, stake e idempotency key, nunca a odd “confiável”, payout, usuário ou saldo. Se `quote_id` não for mais corrente, o servidor devolve a nova cotação e não movimenta créditos.

### 4. Resultado, liquidação e ranking

```text
Admin confirma resultado versionado
  → Settlement calcula outcomes pelo SportRuleAdapter
  → para cada aposta: status + postings + audit idempotentes
  → commit da execução
  → outbox BetSettled/MarketSettled
  → projeções de ranking, desempenho e conquistas
```

Rankings leem somente apostas liquidadas. A projeção guarda cursor/versão processada e pode ser apagada e reconstruída a partir de resultados, apostas e ledger. Disputas privadas adicionam filtro de membership; não duplicam apostas.

### 5. Chat moderado

```text
Cliente → comando SendMessage no servidor
        → autenticação + mute/ban + rate limit + validação
        → INSERT chat_message + audit/outbox/broadcast DB
        → commit
Supabase Realtime → evento mínimo {messageId, roomId, version}
Cliente → busca DTO autorizado/durável e deduplica por messageId
```

Cliente não publica mensagem autoritativa diretamente em Broadcast. Pode usar evento efêmero somente para “digitando” no futuro. Moderação altera `visibility_status` e cria `moderation_action`; broadcast avisa invalidar/ocultar a mensagem. Histórico usa paginação por cursor `(created_at, id)`, não offset.

## Realtime

Usar **Supabase Realtime Broadcast com canais privados e autorização RLS** para chat e invalidações pontuais. A documentação atual recomenda canais privados em produção e Broadcast para a maioria dos casos; Postgres Changes é indicado para baixo volume/testes. Preferir payload semântico mínimo produzido do banco após a escrita, em vez de replicar linhas inteiras — sobretudo para impedir vazamento de pick, papel ou dado de verificação.

Tópicos sugeridos:

```text
match:{matchId}:chat
match:{matchId}:state
user:{userId}:account
league:{leagueId}:ranking
```

Regras:

- autorização no join e nomes não adivinháveis não substituem policy;
- um evento contém ID/versão, não documento completo sensível;
- deduplicar por `event_id`/`message_id`;
- reconexão sempre executa query de catch-up por cursor;
- não depender do replay do Broadcast como histórico: a documentação lista retenção de 72 horas e máximo de 25 mensagens por requisição de replay;
- monitorar conexões, joins, mensagens/s e lag segundo os limites do plano;
- presença online é opcional e deve ser usada minimamente; não entra no núcleo do MVP;
- aplicação web permanece stateless; não manter salas ou sessão em memória da instância Next.js.

Para eventos simples de linha, um trigger pode chamar `realtime.send()`/`realtime.broadcast_changes()` com payload aprovado. Como a mensagem de banco é inserida e lida pelo WAL, ela só aparece a partir da alteração commitada. Para qualquer consumer com consequência de negócio, continuar usando outbox e estado durável.

## Filas, jobs e tarefas programadas

### MVP: PostgreSQL + Cron, sem broker

| Tarefa | Mecanismo | Garantia |
|---|---|---|
| Fechar visualmente mercados vencidos | SQL curto agendado | idempotente; `closes_at` já bloqueia aposta sem o job |
| Expirar verificações | função SQL/Cron | update condicional + audit/outbox |
| Excluir comprovante | worker HTTP/Edge protegido chamado pelo Cron | Storage API + retry + `deletion_pending` |
| Reconciliar objetos órfãos | job diário | compara requests/objetos por prefixo e remove após quarentena |
| Processar outbox | relay curto | lease, backoff, `processed_at`, dead-letter status |
| Atualizar/reconstruir ranking | consumer/rebuild | checkpoint por evento e upsert idempotente |
| Exportar/digerir auditoria | job diário | cópia somente leitura/tamper evidence |

Supabase Cron usa `pg_cron`, registra execuções e recomenda no máximo oito jobs concorrentes e duração inferior a dez minutos. Jobs longos devem ser quebrados. Cada execução possui `job_key`, `attempt`, `next_attempt_at`, `locked_until`, `last_error` sanitizado e métricas. Uma falha não é resolvida apagando a linha; fica visível ao operador.

Quando houver volume assíncrono comprovado, adicionar um worker TypeScript Postgres-backed (por exemplo, pg-boss) no mesmo repositório. Só introduzir Redis/SQS/Kafka quando houver requisito que PostgreSQL não atende — throughput, isolamento de falha, atraso massivo ou integração externa — e então manter consumers idempotentes e outbox na origem.

## Armazenamento efêmero de documentos

### Controles obrigatórios

1. Bucket exclusivo `verification-documents`, privado por padrão, sem listagem pública.
2. Object key aleatória e sem nome original, matrícula, e-mail ou apelido.
3. Signed upload/read URL com TTL mínimo operacional; emissão auditada para revisores autorizados.
4. Allowlist pequena de formatos, limite de bytes no bucket e validação novamente no servidor por conteúdo real.
5. `service_role`/secret somente em backend; nunca em bundle, variável `NEXT_PUBLIC_*` ou log.
6. Estado explícito `document_state`: `awaiting_upload`, `uploaded`, `under_review`, `deletion_pending`, `deleted`, `delete_failed`.
7. A decisão de revisão e a solicitação de exclusão entram juntas no PostgreSQL; a deleção externa ocorre depois e é repetível.
8. Job de retenção remove upload abandonado, documento revisado ainda presente e órfão. Alertar quando `deletion_pending` exceder o SLO definido.
9. Backups e ambientes de teste não recebem comprovantes reais; fixtures são sintéticas.
10. Auditoria guarda somente evento de upload/revisão/exclusão e IDs internos, nunca o conteúdo ou signed URL.

Bucket privado e URL assinada temporária são recursos documentados pelo Supabase. A exclusão deve chamar `storage.remove`; SQL em `storage.objects` remove metadado, não o objeto físico.

## Escalabilidade realista

| Escala observada | Arquitetura recomendada |
|---|---|
| **Piloto / até alguns milhares de contas e centenas simultâneas** | Um deploy Next.js stateless, PostgreSQL gerenciado com pool, Supabase Realtime/Storage, Cron, índices adequados e cache de leitura pública. Sem Redis e sem serviços separados. |
| **Dezenas de milhares de contas / milhares simultâneos em picos** | Escalar web horizontalmente; pooler e limites de conexão; CDN/cache para catálogo; projeção incremental de ranking; worker dedicado do mesmo código-base; rate limit distribuído; observar WAL/joins do Realtime; particionar chat/audit/outbox somente se tamanho/retention exigirem. |
| **Centenas de milhares ativos ou picos nacionais** | Read replicas para catálogo/read models, partições por tempo, fila dedicada se outbox não acompanhar, serviço específico para chat/realtime apenas se houver escala/falha independente. Ledger e aceite continuam com um writer consistente; sharding só após medir contenção real. |

### Ordem provável de gargalos

1. **Consultas e fan-out de chat/ranking**, não o ledger: resolver com cursor, índices, projeções, cache e limites de canal.
2. **Conexões PostgreSQL de funções web:** pooler, transações curtas e teto de concorrência antes de aumentar instâncias.
3. **Autorização de joins Realtime e mensagens/s:** policy simples/indexada, poucos canais por tela e plano adequado.
4. **Crescimento de chat/auditoria:** retenção definida, arquivamento e particionamento por tempo.
5. **Liquidação em lote:** execução retomável em chunks com `SKIP LOCKED`; não aumentar timeout indefinidamente.

Não há justificativa atual para particionar ledger ou separar módulos. Extrair um módulo apenas quando possuir perfil de carga, ciclo de implantação ou isolamento de falha realmente diferente e quando o contrato já estiver estável.

### Índices iniciais importantes

```text
market(event_id, status, closes_at)
odds_set(market_id, version) UNIQUE
bet(user_id, accepted_at DESC, id DESC)
bet(market_id, status, id)
ledger_transaction(idempotency_key) UNIQUE
ledger_posting(account_id, created_at, id)
chat_message(room_id, created_at DESC, id DESC)
verification_request(status, reviewed_at)
outbox_event(status, next_attempt_at, id)
audit_event(entity_type, entity_id, occurred_at DESC)
```

Validar com `EXPLAIN (ANALYZE, BUFFERS)` e métricas antes de criar índices adicionais. Paginação pública deve ser por cursor estável.

## Observabilidade e testes de invariantes

Métricas essenciais:

- aposta aceita/rejeitada por razão, latência da transação e lock wait;
- diferença entre saldo materializado e soma do ledger;
- liquidações pendentes, repetidas e revertidas;
- idade da outbox e taxa de retry/dead-letter;
- `deletion_pending` por idade e objetos órfãos;
- lag, joins recusados e mensagens do Realtime;
- mensagens/denúncias/mutes por sala;
- decisões administrativas sem razão/audit esperado;
- conexões, deadlocks, `40001`, `40P01` e transações ociosas.

Logs técnicos usam `request_id`/`correlation_id`; auditoria usa o mesmo identificador sem copiar dados sensíveis. OpenTelemetry ou provedor equivalente pode ser acrescentado como adaptador, sem contaminar regras de domínio.

Testes que bloqueiam o lançamento:

1. Várias apostas paralelas na mesma carteira nunca deixam saldo negativo nem criam dois débitos para uma chave.
2. Corrida entre publicação de odds e aposta faz cada aposta referenciar integralmente a versão antiga ou nova, nunca mistura cotações.
3. Corrida entre fechamento e aposta só aceita quando o `accepted_at` do banco é anterior a `closes_at`.
4. Repetir liquidação N vezes não altera o saldo depois da primeira.
5. Corrigir resultado produz reversão + nova liquidação e mantém soma de postings zero.
6. Falha após decisão de verificação e antes da Storage API deixa `deletion_pending`; retry termina a exclusão.
7. Usuário, atleta, administrador com conflito e estudante expirado recebem negação em todas as entradas HTTP.
8. Pick alheio não aparece em API, cache, HTML server-rendered, RLS ou evento Realtime antes do fechamento.
9. Mensagem moderada some da projeção ao receber evento duplicado ou fora de ordem.
10. Cada alteração crítica cria audit event sanitizado na mesma transação.

## Antipadrões a evitar

### Microsserviços por módulo no primeiro lançamento

**Erro:** transformar Ledger, Apostas, Odds e Liquidação em APIs independentes.
**Consequência:** débito/aposta e resultado/payout deixam de compartilhar transação; surgem saga, retries, observabilidade distribuída e estados intermediários.
**Em vez disso:** módulos internos com contratos e uma implantação. Extrair seletivamente quando métricas e organização justificarem.

### Saldo mutável sem journal

**Erro:** `UPDATE users SET credits = ...` em vários serviços.
**Consequência:** não é possível explicar, reconciliar ou reverter créditos.
**Em vez disso:** uma única porta do Ledger, postings append-only, idempotência e saldo derivado.

### Consultar saldo e depois debitar

**Erro:** `SELECT balance`, validar em TypeScript e fazer `UPDATE` separado.
**Consequência:** duas requisições concorrentes gastam os mesmos créditos.
**Em vez disso:** update condicional/lock dentro da mesma transação e constraint.

### Atualizar odds/resultados publicados no lugar

**Erro:** sobrescrever uma linha para “corrigir”.
**Consequência:** aposta perde a condição aceita; auditoria e ranking ficam inexplicáveis.
**Em vez disso:** versão imutável, referência da aposta e reversão compensatória.

### Confiar no Cron para fechar mercado

**Erro:** só mudar `status` a cada minuto.
**Consequência:** atraso/queda do job aceita aposta após o horário.
**Em vez disso:** `closes_at` e relógio do banco validados no comando; Cron apenas materializa visualmente.

### Realtime como banco de chat ou autorização

**Erro:** cliente envia broadcast e os outros o exibem como mensagem válida.
**Consequência:** bypass de moderação, perda na reconexão e spoofing.
**Em vez disso:** persistir/autorizá-la no servidor e transmitir apenas após commit; refetch por ID.

### Exclusão de comprovante “best effort” sem estado

**Erro:** aprovar, chamar delete uma vez e esquecer a falha.
**Consequência:** o banco diz “apagado” enquanto bytes continuam armazenados.
**Em vez disso:** `deletion_pending`, outbox, Storage API, retry, reconciliação e SLO.

### RLS como única regra de negócio

**Erro:** espalhar toda elegibilidade, conflito e máquina de estados em policies complexas.
**Consequência:** lógica difícil de testar, risco de corrida em subqueries e bypass por role privilegiada.
**Em vez disso:** políticas de aplicação centralizadas + constraints/transações; RLS simples como segunda barreira nos recursos expostos.

### Pasta `shared/` sem dono

**Erro:** qualquer módulo importa entidades/repositórios de qualquer outro por um diretório genérico.
**Consequência:** monólito modular vira big ball of mud.
**Em vez disso:** `platform/` apenas para capacidades técnicas estáveis e `public.ts` por domínio, com teste de importação.

## Pontos de integração

### Serviços externos

| Serviço | Padrão | Cuidados |
|---|---|---|
| Supabase Auth | SDK no cliente + validação server-side | JWT identifica, mas papéis/elegibilidade sensíveis vêm do estado atual; não pôr secret no browser. |
| PostgreSQL gerenciado | Drizzle/SQL por conexão server-side e pooler | uma transação para invariantes; migrations versionadas; backups e restore testado. |
| Supabase Realtime | Broadcast privado + RLS; evento após commit | payload mínimo, dedupe e catch-up durável; observar limites do plano. |
| Supabase Storage | signed upload/read URL + Storage API | bucket privado, object key aleatória, validação, delete retry; nunca apagar só metadado SQL. |
| Supabase Cron/pg_cron | SQL curto ou chamada autenticada ao executor | job idempotente, observável e menor que o limite operacional recomendado. |
| Fonte esportiva futura | adaptador de importação para staging | validar, comparar e publicar; nunca escrever tabelas canônicas diretamente. |
| Modelo de odds | porta interna `OddsRecommendationEngine` | produz recomendação/racional em rascunho; administrador publica. |

### Fronteiras internas críticas

| Fronteira | Comunicação | Regra |
|---|---|---|
| Apostas ↔ Ledger | comando síncrono na mesma transação | não existe aposta aceita sem reserva de stake nem reserva sem aposta. |
| Apostas ↔ Mercados/Odds | query/lock síncrono | aposta referencia quote publicada e corrente. |
| Apostas ↔ Acesso/Esporte | policy síncrona | valida matrícula, expiração, atleta/equipe e admin/evento. |
| Liquidação ↔ Ledger | comando idempotente síncrono | payout/reversal com chave semântica única. |
| Liquidação → Engajamento | outbox/evento | projeção pode atrasar e ser reconstruída. |
| Social → Realtime | persistência + broadcast após commit | websocket nunca antecede mensagem durável. |
| Verificação → Storage | outbox/job | decisão e solicitação de delete são atômicas; bytes são removidos eventualmente. |
| Todos → Auditoria | append na mesma transação | falha de audit em ação crítica aborta a ação. |

## Ordem recomendada de construção

1. **Esqueleto modular, banco e segurança de borda** — estrutura por módulos, migrations, unit of work, Auth, ator, policy deny-by-default, idempotência, auditoria e observabilidade básica.
2. **Catálogo esportivo e operação administrativa** — competição/edição/modalidade/equipes/atletas/fases/partidas, estados e importação manual confiável.
3. **Verificação e relações de integridade** — elegibilidade com expiração, afiliação atleta-equipe, admin-evento e ciclo completo de documento temporário.
4. **Ledger** — journal de partidas dobradas, concessão inicial, saldo projetado, reversão, reconciliação e testes concorrentes.
5. **Mercados e odds versionadas** — adaptadores de esporte/mercado, recomendação em rascunho, revisão, publicação e histórico.
6. **Aposta simples pré-jogo** — bilhete, lock/clock do banco, idempotência, conflitos, pick oculto, histórico próprio.
7. **Resultado e liquidação** — versão de resultado, win/loss/void, replay seguro, correção/reversão e operação retomável.
8. **Read models de ranking, ligas e conquistas** — derivados somente após o núcleo poder ser reconciliado.
9. **Chat e moderação realtime** — depende de identidade, partida e sanções; persistência primeiro, broadcast depois.
10. **Hardening do piloto** — jobs, reconciliação de Storage/Ledger, limites, testes de corrida, restore, SLOs e runbooks.

Essa ordem reduz retrabalho: social e rankings dependem de entidades e eventos já estáveis; apostar depende de elegibilidade, mercado e ledger; liquidar depende de aposta, resultado e regras da modalidade.

## Sinais para uma futura extração

Considerar separar um módulo somente quando pelo menos um destes sinais for observado e sustentado por métricas:

- chat/realtime exige escala e disponibilidade independentes da web;
- cálculo de odds consome CPU ou dependências incompatíveis com o runtime web;
- um worker acumula backlog que ameaça latência transacional do PostgreSQL principal;
- equipes distintas precisam implantação autônoma com contrato estabilizado;
- requisito de isolamento/região supera o custo de consistência distribuída.

Mesmo então, começar extraindo consumer/projeção sem autoridade monetária. **Ledger, aceite de aposta e liquidação devem permanecer juntos enquanto a atomicidade for mais valiosa que a escala independente.**

## Fontes

### Arquitetura e segurança de aplicação

- [Shopify Engineering — Deconstructing the Monolith: Designing Software that Maximizes Developer Productivity](https://shopify.engineering/deconstructing-monolith-designing-software-maximizes-developer-productivity) — caso real de monólito modular e fronteiras por domínio; confiança MÉDIA para a decisão contextual.
- [Microsoft Azure Architecture Center — Microservices architecture style](https://learn.microsoft.com/en-us/azure/architecture/microservices/) — complexidade, consistência e transações em microsserviços; confiança ALTA.
- [Next.js — Authentication](https://nextjs.org/docs/app/guides/authentication) — DAL, autorização próxima ao dado e segurança de Server Actions/Route Handlers; atualizado em 2026; confiança ALTA.
- [Next.js — `use server`](https://nextjs.org/docs/app/api-reference/directives/use-server) — autenticar e autorizar toda operação server-side sensível; atualizado em 2026; confiança ALTA.
- [OWASP — Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) — deny-by-default, verificação por requisição e ABAC/ReBAC; confiança ALTA.
- [OWASP — Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) — atributos, exclusão de dados sensíveis e proteção contra adulteração; confiança ALTA.

### PostgreSQL, transações e consistência

- [PostgreSQL 18 — Transaction Isolation](https://www.postgresql.org/docs/current/transaction-iso.html) — comportamento de `READ COMMITTED`, serializable e retries; versão estável atual em 2026; confiança ALTA.
- [PostgreSQL 18 — Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html) — `FOR SHARE`, conflitos, deadlocks e ordem de locks; confiança ALTA.
- [PostgreSQL 18 — SELECT locking clause](https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE) — `SKIP LOCKED` para tabela semelhante a fila; confiança ALTA.
- [PostgreSQL 18 — Constraints](https://www.postgresql.org/docs/current/ddl-constraints.html) — unicidade, chaves e integridade referencial; confiança ALTA.
- [PostgreSQL 18 — INSERT / ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html) — upsert concorrente apoiado por constraint; confiança ALTA.
- [PostgreSQL 18 — Numeric Types](https://www.postgresql.org/docs/current/datatype-numeric.html) — `numeric` exato versus ponto flutuante inexato; confiança ALTA.
- [PostgreSQL 18 — Row Security Policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html) — default-deny, bypass e riscos de policies com subqueries; confiança ALTA.
- [AWS Prescriptive Guidance — Transactional outbox pattern](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) — dual-write, outbox e consumers idempotentes; confiança ALTA.

### Supabase Realtime, Storage e Cron

- [Supabase — Realtime Getting Started](https://supabase.com/docs/guides/realtime/getting_started) — canais privados, Broadcast e escolha entre recursos; confiança ALTA.
- [Supabase — Broadcast](https://supabase.com/docs/guides/realtime/broadcast) — Broadcast from Database, autorização e retenção; confiança ALTA.
- [Supabase — Realtime Architecture](https://supabase.com/docs/guides/realtime/architecture) — WAL, entrega de Broadcast e partições; confiança ALTA.
- [Supabase — Realtime Limits](https://supabase.com/docs/guides/realtime/limits) — conexões, mensagens, joins e replay; consultado em 2026-08-01; confiança ALTA, mas os valores dependem do plano.
- [Supabase — Storage Buckets](https://supabase.com/docs/guides/storage/buckets/fundamentals) — bucket privado, restrições e URL assinada; confiança ALTA.
- [Supabase — Storage Access Control](https://supabase.com/docs/guides/storage/security/access-control) — RLS e segredo que bypassa policies; confiança ALTA.
- [Supabase — Delete Objects](https://supabase.com/docs/guides/storage/management/delete-objects) — exclusão pela API e risco de objeto órfão ao apagar via SQL; confiança ALTA.
- [Supabase — Cron](https://supabase.com/docs/guides/cron) — `pg_cron`, monitoramento e limites operacionais recomendados; confiança ALTA.
- [OWASP — File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html) — validação, segregação, autorização, limites e inspeção; confiança ALTA.

## Lacunas e validações futuras

- Os limites de simultaneidade do piloto e do plano Supabase escolhido precisam de teste de carga antes do lançamento; não há volume histórico da BETUFU.
- A política exata de retenção de chat/auditoria e o SLO de exclusão do comprovante exigem decisão de produto/privacidade.
- A visualização segura de PDF/imagem e o fornecedor de antimalware precisam de pesquisa específica na fase de verificação.
- A granularidade de crédito e a regra de arredondamento precisam ser congeladas antes de criar o primeiro lançamento do Ledger.
- O primeiro conjunto de adaptadores depende das modalidades e fontes de resultados que realmente estiverem disponíveis no piloto.

---
*Pesquisa de arquitetura para BETUFU — 2026-08-01*
