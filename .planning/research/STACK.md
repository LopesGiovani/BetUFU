# Stack Research

**Domínio:** aplicação web responsiva de palpites esportivos sociais, com créditos virtuais, chat por partida, verificação documental temporária, RBAC e operação administrativa
**Projeto:** BETUFU
**Pesquisado em:** 2026-08-01
**Confiança:** ALTA para versões, compatibilidade e capacidades documentadas; MÉDIA para dimensionamento, que dependerá do piloto

## Recomendação executiva

Construir a BETUFU como um **monólito modular full-stack em TypeScript**, com Next.js no runtime Node.js e PostgreSQL como única fonte de verdade. Hospedar a aplicação na Vercel e usar um projeto Supabase em São Paulo para PostgreSQL, Auth, Realtime e Storage reduz substancialmente a operação do MVP sem enfraquecer o núcleo transacional.

O ponto inegociável é a separação entre conveniência e autoridade: Supabase Auth/Realtime/Storage aceleram autenticação, chat e documentos, mas **apostas, ledger, liquidação, odds aceitas, resultados e auditoria são comandos server-side e transações PostgreSQL**. Realtime apenas avisa que dados persistidos mudaram. Redis, quando usado, serve somente para rate limiting; nunca guarda saldo ou estado canônico.

Adotar **TypeScript 6.0.3**, embora 7.0.2 seja o `latest` em 2026-08-01. TypeScript 7 ainda não oferece a API programática estável consumida por ferramentas como `typescript-eslint`; a própria equipe do TypeScript orienta manter 6.x para esse caso. Esta é uma escolha deliberada de compatibilidade, não desatualização acidental.

## Topologia recomendada para o MVP

```text
Navegador móvel/desktop
  ├─ HTTPS ───────────────> Next.js 16 / Node 24 na Vercel (gru1)
  │                           ├─ comandos e consultas tipadas ─> PostgreSQL 17 (Supabase sa-east-1)
  │                           ├─ sessão/RBAC ─────────────────> Supabase Auth + RLS
  │                           ├─ documento temporário ────────> Supabase Storage privado
  │                           └─ rate limit efêmero ──────────> Upstash Redis
  └─ WebSocket de leitura ─> Supabase Realtime (canal privado / Broadcast de evento persistido)
```

Não criar API separada, microsserviços, servidor Socket.IO, fila dedicada ou serviço Python de probabilidades no MVP. As fronteiras devem existir como módulos internos para permitir extração posterior, caso métricas reais justifiquem.

## Stack recomendada

### Tecnologias centrais

| Tecnologia | Versão-alvo | Finalidade | Por que é a escolha para a BETUFU |
|---|---:|---|---|
| Node.js | **24.18.1 LTS (Krypton)** | Runtime de produção e desenvolvimento | Linha LTS atual, já contém correções de segurança de 2026-07-29 e satisfaz Next.js, pnpm, Vitest, ESLint e `file-type`. Não usar Node 26 Current em produção. |
| pnpm | **11.18.0** | Gerenciador de pacotes e lockfile | Instalações determinísticas e eficientes; requer Node >=22.13, portanto é compatível com Node 24. Registrar `packageManager: "pnpm@11.18.0"`. |
| Next.js | **16.2.12** (App Router) | UI responsiva, Server Components, Route Handlers e Server Actions | Permite um único deploy e uma única linguagem. O App Router é o caminho greenfield; o runtime Node suporta driver PostgreSQL e transações. Next 16 exige Node >=20.9 e usa Turbopack por padrão. |
| React / React DOM | **19.2.8** | Camada de interface | Par explicitamente aceito pelos peers do Next 16.2.12. Priorizar Server Components e hidratar somente bilhete, chat e telas administrativas interativas. |
| TypeScript | **6.0.3** | Tipagem do domínio e build | É suportado por `typescript-eslint@8.65` (`>=4.8.4 <6.1`) e pelo Next 16. TypeScript 7.0.2 fica adiado até o ecossistema consumidor da Compiler API declarar suporte. Usar `strict`. |
| PostgreSQL no Supabase | **17.x; 17.10 disponível na plataforma em 2026-08-01** | Fonte de verdade para usuários de domínio, eventos, odds, apostas, ledger, rankings e auditoria | O modelo é relacional e exige constraints, transações, locks, tipos exatos, índices e RLS. O Supabase ainda documenta PostgreSQL 17 como alvo hospedado; não escrever migrations dependentes de PostgreSQL 18. |
| Drizzle ORM | **0.45.2 estável** | Queries tipadas e mapeamento do schema | Mantém SQL visível e permite `CHECK`, `UNIQUE`, índices e SQL customizado, importantes para ledger/RBAC/auditoria. Não adotar o canal `1.0.0-rc` no primeiro release. |
| Postgres.js | **3.4.9** | Driver usado pelo Drizzle | Driver pequeno, ESM, com transações e parametrização. Em Supavisor Transaction mode configurar `{ prepare: false }`; migrations usam conexão direta. |
| Supabase JS / SSR | **`@supabase/supabase-js` 2.111.0** e **`@supabase/ssr` 0.12.4** | Auth SSR/PKCE, cliente Storage e assinatura Realtime | O pacote SSR 0.12.4 requer `supabase-js ^2.111.0`, combinação verificada. Fixar versões exatas porque `@supabase/ssr` ainda é pré-1.0. |
| Supabase Auth | Gerenciado | Cadastro, confirmação de e-mail, recuperação, sessões e identidade | Evita implementar armazenamento de senhas. Usar fluxo PKCE/cookies no SSR, CAPTCHA e SMTP próprio. RBAC de negócio permanece em tabelas da aplicação, com claims apenas como aceleração/defesa adicional. |
| Supabase Realtime | Gerenciado; cliente incluído no `supabase-js` | Atualização do chat e, opcionalmente, de placares | Usar **Broadcast from Database em canais privados**. O cliente envia mensagem para um comando Next.js; após validação, rate limit e persistência, trigger emite o evento. A tabela de chat continua sendo a fonte histórica. |
| Supabase Storage | Gerenciado | Avatares e comprovantes temporários | Bucket separado e privado para comprovantes, RLS, limite de tamanho/MIME e URL assinada curta. A exclusão pós-revisão deve chamar a Storage API; apagar apenas a linha de metadata deixa objeto órfão. |

### Infraestrutura e serviços gerenciados

| Serviço | Configuração prescrita | Uso e observações |
|---|---|---|
| Supabase | Região específica **South America (São Paulo), `sa-east-1`** | Colocar banco, Auth, Realtime e Storage perto do público. Antes do piloto público, usar plano com recuperação point-in-time adequada à trilha financeira virtual; backups do banco não incluem objetos do Storage. |
| Vercel | Next.js em Node runtime, região **São Paulo, `gru1`** | Colocar compute na mesma região AWS do banco. Não usar Edge runtime para criar aposta, liquidar mercado ou alterar ledger. Conteúdo estático continua no CDN. |
| Upstash Redis | `@upstash/ratelimit` **2.0.8** + `@upstash/redis` **1.38.1** | Rate limit distribuído para envio de chat, denúncias, upload e comandos administrativos sensíveis. Somente dados efêmeros; falha do Redis não pode alterar saldo, odds ou resultado. |
| SMTP transacional | SMTP próprio no Supabase Auth; **Resend** recomendado | Confirmação e recuperação de conta. O emissor embutido do Supabase é apenas para teste e limita-se a 2 e-mails/hora; configurar domínio, SPF, DKIM e DMARC antes do piloto. Não é necessário SDK Resend para Auth, apenas credenciais SMTP. |
| Cloudflare Turnstile | Serviço gerenciado, integrado ao Supabase Auth | CAPTCHA em cadastro, login/recovery sob risco e reenvios. É oficialmente suportado pelo Supabase Auth e reduz abuso sem manter uma solução própria. |
| Supabase Cron / `pg_cron` | **1.6.4 na plataforma**; uso restrito | Somente tarefas curtas e idempotentes próximas do banco, como expirar verificações e limpar histórico técnico. A exclusão de objetos precisa passar pela Storage API. Não transformar Cron em fila geral. |

### Bibliotecas de aplicação

| Biblioteca | Versão | Finalidade | Quando e como usar |
|---|---:|---|---|
| Zod | **4.4.3** | Schemas de entrada e DTOs | Validar todo comando na fronteira server-side; compartilhar somente schemas puros com formulários. Zod não substitui constraints PostgreSQL. |
| decimal.js | **10.6.0** | Exibir odds e retorno potencial sem `Number` binário | Usar no preview/UI e testes. A liquidação canônica ocorre em SQL com `NUMERIC`; transportar `BIGINT`/`NUMERIC` em DTOs como string decimal. |
| Tailwind CSS / PostCSS | **4.3.3 / 4.3.3** | Design responsivo | Mobile-first, tokens em CSS e estados acessíveis. Tailwind gera CSS sem runtime no cliente. |
| shadcn CLI / Radix UI | **4.16.1 / 1.6.7** | Componentes acessíveis sob controle do repositório | Gerar apenas componentes necessários; o código copiado passa a ser do projeto. Radix cobre dialog, menu, tabs, tooltip e primitives de acessibilidade sem impor tema. |
| Lucide React | **1.28.0** | Ícones | Usar ícones acompanhados de rótulo/`aria-label`; não comunicar resultado de aposta apenas por cor ou ícone. |
| React Hook Form / resolvers | **7.84.0 / 5.5.7** | Formulários administrativos e de verificação | Usar nos formulários longos/dinâmicos. Para formulários simples, preferir Server Actions e APIs React para não criar estado cliente desnecessário. |
| TanStack Query | **5.101.4** | Cache cliente de superfícies realmente interativas | Restringir a chat, paginação infinita e tabelas admin. Páginas públicas, rankings e times devem vir de Server Components/cache do Next; não duplicar todo o estado do servidor no browser. |
| file-type | **22.0.1** | Verificação de assinatura binária de uploads | Inspecionar bytes no servidor além de extensão/MIME, aceitar lista curta (por exemplo JPEG, PNG e PDF) e limitar o comprovante a 6 MB. Não abrir HTML/SVG de usuário no painel admin. |
| Pino | **10.3.1** | Logs estruturados | Configurar `redact` para e-mail, matrícula/HMAC, conteúdo de documentos, signed URLs, tokens, cookies e chaves. Gerar `request_id`, `actor_id` e `audit_event_id`, sem registrar payload sensível. |
| Sentry para Next.js | **10.69.0** | Erros e performance | Ativar antes do piloto com `sendDefaultPii: false`, scrubbing de payloads e sourcemaps privados. O audit log de negócio não deve depender do Sentry. |

### Ferramentas de desenvolvimento e qualidade

| Ferramenta | Versão | Uso prescrito |
|---|---:|---|
| Supabase CLI | **2.111.0** | Subir Auth/Postgres/Storage/Realtime localmente via Docker, aplicar migrations e gerar tipos quando necessário. Ambientes local, preview/staging e produção devem ser separados. |
| Drizzle Kit | **0.31.10** | Gerar SQL revisável a partir do schema; configurar prefixo de migration `supabase` e manter **um** histórico em `supabase/migrations`. Nunca usar `push` em staging/produção. |
| ESLint / eslint-config-next | **10.8.0 / 16.2.12** | Flat config com `core-web-vitals` e `typescript`. Next 16 removeu `next lint`; executar `eslint .` diretamente. |
| Prettier / plugin Tailwind | **3.9.6 / 0.8.1** | Formatação determinística e ordenação de classes. Formatação não deve reescrever migrations SQL já revisadas. |
| Vitest / coverage-v8 / jsdom | **4.1.10 / 4.1.10 / 30.0.1** | Testes de unidade para odds, payout, permissões e regras; integração de repositórios deve usar PostgreSQL local real, não mock de ORM. |
| Testing Library React / jest-dom | **16.3.2 / 7.0.0** | Testar comportamento e acessibilidade dos componentes interativos. |
| Playwright | **1.62.1** | E2E de cadastro, verificação, bloqueio de aposta, concorrência visível, liquidação, RBAC e moderação em navegadores móveis/desktop. Next recomenda E2E para Server Components assíncronos. |
| `@types/node`, `@types/react`, `@types/react-dom` | **24.13.3 / 19.2.18 / 19.2.4** | Alinhar tipos ao runtime Node 24 e React 19. |

## Regras de integridade que a stack deve impor

Estas decisões são parte da escolha da stack, não detalhes opcionais de implementação:

1. **Créditos em unidades inteiras.** Usar `BIGINT` para stake/saldo e DTO string; nunca `float`, `double` ou `number` para liquidação.
2. **Odds exatas e versionadas.** Usar `NUMERIC(10,4)` (ou precisão definida pela política), gravar a odd aceita na aposta e manter `odds_versions` append-only. `decimal.js` só antecipa a apresentação.
3. **Ledger de dupla entrada ou equivalente balanceado.** Cada concessão, aposta, devolução e prêmio gera uma transação imutável com entradas, conta de sistema e `idempotency_key UNIQUE`. Saldo materializado, se existir, é atualizado na mesma transação.
4. **Uma transação por comando financeiro virtual.** Criar aposta deve revalidar usuário/verificação/conflito de interesse, mercado aberto e horário do servidor; bloquear a conta com `SELECT ... FOR UPDATE` ou usar `SERIALIZABLE`, inserir aposta/ledger e atualizar saldo antes do `COMMIT`.
5. **Retry explícito.** PostgreSQL `SERIALIZABLE` pode abortar com `serialization_failure`; repetir um número limitado de vezes usando a mesma chave de idempotência.
6. **Servidor como autoridade.** Browser nunca recebe chave secreta nem escreve diretamente em saldo, apostas, odds, resultados, funções administrativas ou status de verificação. RLS continua habilitado como defesa em profundidade.
7. **Auditoria no mesmo commit.** Alterações de odds, resultado, RBAC e verificação gravam ator, motivo, antes/depois mínimo e correlação na mesma transação do comando.
8. **Tempo do banco/servidor.** Fechamento usa `timestamptz` e `now()` do PostgreSQL; relógio do cliente é apenas visual. Exibir em `America/Sao_Paulo` via `Intl`.

## Privacidade e verificação documental

- Guardar comprovante em bucket `verification-documents` **privado**, sem URL pública e sem nome original; caminho aleatório por solicitação.
- Limitar no bucket tipos permitidos e 6 MB. Validar magic bytes com `file-type`; renderizar/download somente por uma rota administrativa autorizada ou URL assinada de poucos minutos.
- Ao decidir a verificação, chamar `storage.from(bucket).remove(...)` e somente após sucesso marcar `document_deleted_at`. Um job diário repete deleções pendentes/expiradas de forma idempotente.
- Não colocar comprovantes em backups próprios. A documentação do Supabase informa que backups do banco não incluem objetos do Storage, o que é desejável para este material temporário; ainda assim, documentar retenção e validar o contrato do provedor.
- Não conservar matrícula em claro. Normalizar e calcular **HMAC-SHA-256 com pepper secreto** no servidor para unicidade/antiduplicidade; hash simples é vulnerável a enumeração por ter espaço pequeno. Guardar apenas HMAC, status, datas e metadados mínimos da decisão.
- Manter uma instância separada do cliente Supabase para operações privilegiadas; chaves `secret/service_role` nunca usam o cliente SSR compartilhado com cookies e nunca chegam ao browser.
- Claims JWT de `admin`/`moderator` ajudam RLS e navegação, mas permissões críticas devem consultar a tabela de papéis ou uma função autorizadora, pois um token já emitido pode ficar temporariamente desatualizado.

## Chat e realtime moderado

Fluxo prescrito:

```text
POST/Server Action enviarMensagem
  -> autenticar + checar mute/ban + Zod + rate limit
  -> INSERT chat_messages (texto simples, sem HTML) + audit/correlation id
  -> trigger realtime.broadcast_changes em tópico privado match:<id>
  -> clientes recebem evento e invalidam/adicionam a mensagem persistida
```

Usar Broadcast em canal privado, não `Postgres Changes` indiscriminado e não Broadcast direto entre clientes. Presence deve ser adiada: a documentação do Supabase recomenda uso mínimo devido ao custo e ela não é necessária para o MVP. Denúncia, exclusão lógica, mute e moderação permanecem comandos persistidos e auditados.

## Migrations e acesso ao banco

- Manter migrations SQL revisadas e versionadas em `supabase/migrations/`. Configurar Drizzle Kit com `migrations.prefix: "supabase"`, que gera nomes `YYYYMMDDHHmmss_nome.sql` compatíveis.
- Incluir nas migrations, além das tabelas Drizzle, RLS, grants, functions, triggers, índices parciais, constraints e políticas de Storage/Realtime.
- Em desenvolvimento, aplicar com `supabase db reset`; em CI/staging/produção, validar em banco efêmero e aplicar com `supabase db push` após backup/checagem. Nunca editar migration já aplicada.
- Usar conexão direta (`5432`) para migrations, `pg_dump` e backend persistente. Na Vercel, usar Supavisor Transaction mode (`6543`) e `postgres(DATABASE_URL, { prepare: false })`, pois esse modo não suporta prepared statements.
- Limitar o pool e criar o cliente uma vez no escopo do módulo. Aumentar conexões não corrige consultas lentas; indexar primeiro colunas de RLS, FKs, `match_id`, `user_id`, status e timestamps de feed/ranking.

## Instalação inicial

Fixar as versões no `package.json` e commitar `pnpm-lock.yaml`; não usar faixas `^` nos pacotes de framework, autenticação, banco e cálculo.

```bash
# Runtime e scaffold (instale primeiro o Node.js 24.18.1 LTS)
npm install --global pnpm@11.18.0
pnpm dlx create-next-app@16.2.12 betufu --typescript --tailwind --eslint --app --src-dir --use-pnpm
cd betufu

# Fixar o núcleo gerado
pnpm add --save-exact next@16.2.12 react@19.2.8 react-dom@19.2.8
pnpm add --save-exact @supabase/supabase-js@2.111.0 @supabase/ssr@0.12.4
pnpm add --save-exact drizzle-orm@0.45.2 postgres@3.4.9

# Domínio, formulários, realtime no cliente e proteção de abuso
pnpm add --save-exact zod@4.4.3 decimal.js@10.6.0
pnpm add --save-exact react-hook-form@7.84.0 @hookform/resolvers@5.5.7 @tanstack/react-query@5.101.4
pnpm add --save-exact @upstash/ratelimit@2.0.8 @upstash/redis@1.38.1 file-type@22.0.1
pnpm add --save-exact pino@10.3.1 @sentry/nextjs@10.69.0

# UI
pnpm add --save-exact tailwindcss@4.3.3 @tailwindcss/postcss@4.3.3
pnpm dlx shadcn@4.16.1 init
pnpm add --save-exact radix-ui@1.6.7 lucide-react@1.28.0

# Toolchain e testes
pnpm add --save-dev --save-exact typescript@6.0.3 @types/node@24.13.3 @types/react@19.2.18 @types/react-dom@19.2.4
pnpm add --save-dev --save-exact drizzle-kit@0.31.10 supabase@2.111.0
pnpm add --save-dev --save-exact eslint@10.8.0 eslint-config-next@16.2.12
pnpm add --save-dev --save-exact prettier@3.9.6 prettier-plugin-tailwindcss@0.8.1
pnpm add --save-dev --save-exact vitest@4.1.10 @vitest/coverage-v8@4.1.10 jsdom@30.0.1
pnpm add --save-dev --save-exact @testing-library/react@16.3.2 @testing-library/jest-dom@7.0.0 @playwright/test@1.62.1

# Supabase local requer Docker/Docker Desktop
pnpm exec supabase init
pnpm exec supabase start
pnpm exec playwright install --with-deps
```

Depois do scaffold, registrar:

```json
{
  "engines": { "node": "24.18.x" },
  "packageManager": "pnpm@11.18.0"
}
```

## Alternativas consideradas

| Recomendado | Alternativa | Quando a alternativa faz sentido | Por que não é a escolha inicial |
|---|---|---|---|
| Next.js 16 modular monolith | React Router 7 + Fastify | Equipe já domina essa arquitetura ou haverá API pública independente desde o dia 1 | Dois deploys e mais contratos/infra sem necessidade comprovada no MVP. |
| Supabase integrado | Neon PostgreSQL + Better Auth 1.6.25 + Ably/Pusher + S3 | Exigência forte de portabilidade por serviço, região/contrato específico ou Auth muito customizado | Quatro integrações, painéis e políticas onde um projeto Supabase atende o escopo atual. |
| Drizzle 0.45.2 + SQL explícito | Prisma 7.9.1 | Equipe prioriza client gerado e CRUD e aceita usar SQL separado para constraints/functions/RLS | A BETUFU é SQL-heavy: ledger, locks, idempotência, RLS, triggers e auditoria ficam mais transparentes com Drizzle. |
| Supabase Broadcast | Socket.IO 4.8.3 + servidor persistente | Milhares de conexões simultâneas, protocolo customizado, acknowledgements complexos ou necessidade de self-host | Exige serviço WebSocket, adapter/fanout e operação que o piloto ainda não justifica. |
| Vercel + Supavisor | Container persistente em Fly.io/Railway | Worker contínuo, WebSocket próprio ou transações longas se tornarem centrais | Vercel simplifica o monólito atual e Realtime elimina a necessidade de socket no processo Next. |
| Upstash rate limit | Rate limit transacional no PostgreSQL | Piloto fechado e minúsculo, com desejo de eliminar um fornecedor | Contadores de abuso competem com o banco autoritativo e aumentam escrita/locks; Redis isola essa carga. |
| PostgreSQL 17 no Supabase | PostgreSQL 18.4 gerenciado/self-hosted | Provedor escolhido certificar 18 e a equipe aceitar operar Auth/Realtime/Storage separadamente | Supabase documenta 17.10 como alvo atual; recursos exclusivos de 18 criariam incompatibilidade. |
| Supabase Cron apenas para manutenção | `pg-boss` 12.26.4 + worker Node separado | Surgirem notificações, importações, retry/backoff ou workloads duráveis fora de request | Um worker permanente é custo e superfície operacional prematuros; liquidação e apostas devem continuar síncronas/transacionais. |

## O que NÃO usar

| Evitar | Problema específico | Usar em vez disso |
|---|---|---|
| TypeScript 7.0.2 diretamente no MVP | Em 7.0 não há API programática estável; `typescript-eslint` atual declara `<6.1.0` | TypeScript 6.0.3; reavaliar 7.1+ quando Next/ESLint declararem suporte conjunto. |
| Drizzle ORM `1.0.0-rc.*` | Canal ainda release candidate; o registry mantém 0.45.2 como `latest` estável | Drizzle ORM 0.45.2 e Drizzle Kit 0.31.10. |
| Node 26 Current | Ainda não é LTS em 2026-08-01 | Node 24.18.1 LTS. |
| `number`, `float8` ou arredondamento de UI para créditos/payout | IEEE-754 permite divergência e quebra auditoria | `BIGINT`, `NUMERIC`, `decimal.js` somente na apresentação e cálculo SQL canônico. |
| MongoDB/Firestore como banco primário | O domínio depende de integridade referencial, locks, constraints, transações multi-entidade e consultas de ranking | PostgreSQL. |
| Escrita direta do browser em apostas, ledger, odds, resultados ou verificação | Contorna invariantes, RBAC e auditoria coordenada | Comandos Next.js server-side + transação PostgreSQL + RLS. |
| Broadcast de chat cliente-a-cliente | Mensagem pode circular sem persistência, moderação ou rate limit | Persistir primeiro e emitir Broadcast from Database em canal privado. |
| Bucket público ou documento em `bytea` no banco | Expõe dado sensível ou infla backup/PITR; apagar metadata não apaga objeto | Bucket privado temporário e deleção pela Storage API. |
| `drizzle-kit push` em ambiente compartilhado | Pode aplicar mudanças destrutivas sem migration revisável | `generate`, revisão de SQL e `supabase db push`. |
| Saldo no Redis, cache ou estado do processo | Perde consistência em restart/falha e permite dupla aposta | PostgreSQL transacional; cache apenas de leitura derivada. |
| JWT como única fonte de autorização administrativa | Claim permanece válida até refresh/expiração após revogação | Tabela RBAC + checagem server-side; claims apenas para RLS/UI. |
| Microsserviços, Kafka, Kubernetes ou event sourcing genérico no MVP | Multiplica falhas e custo sem escala comprovada | Monólito modular, ledger append-only e outbox local quando necessário. |
| Serviço Python/ML para odds iniciais | Pouco histórico não sustenta modelo complexo; cria segunda stack | Elo/Bradley-Terry transparente no módulo TypeScript/SQL, revisão administrativa e versão dos parâmetros. |
| GraphQL/tRPC no primeiro cliente web | Não existe consumidor externo nem benefício que compense nova camada | Server Components, Server Actions/Route Handlers e serviços de domínio tipados. |

## Padrões por variante

**Se o piloto permanecer abaixo de algumas centenas de usuários simultâneos:**

- Manter Supabase Broadcast privado e Postgres como histórico do chat.
- Calcular ranking sob demanda com bons índices e cache curto; materializar somente após medir consultas lentas.
- Executar odds sugeridas no comando administrativo ou em job curto, sempre com revisão humana.

**Se chat/realtime ultrapassar limites ou dominar custo:**

- Medir conexões, mensagens/segundo, atraso e erros antes de trocar tecnologia.
- Primeiro ajustar tópicos privados por partida, payload mínimo e Broadcast from Database.
- Somente então extrair gateway Socket.IO/Elixir e usar Redis adapter; a tabela `chat_messages` e o contrato de evento permanecem estáveis.

**Se aparecer trabalho assíncrono durável:**

- Criar `outbox_events` na mesma transação do comando.
- Adicionar worker Node separado com `pg-boss@12.26.4`, idempotência e dead-letter/retry observável.
- Não mover aposta ou liquidação para processamento eventual; somente efeitos colaterais.

**Se a aplicação sair da Vercel:**

- Gerar build `standalone` do Next em container Node 24.
- Usar conexão direta ou Supavisor Session mode; manter região próxima de `sa-east-1`.
- Realtime e Storage permanecem via APIs Supabase, portanto a migração não exige reescrever o domínio.

**Se houver exigência de saída do Supabase:**

- PostgreSQL, SQL migrations, HMACs e módulos de domínio já são portáveis.
- Isolar Auth, Realtime e Storage atrás de adapters finos; substituir por Better Auth, Socket.IO/Ably e S3 sem alterar ledger/apostas.

## Compatibilidade de versões

| Pacote/serviço | Compatível com | Nota operacional |
|---|---|---|
| Node.js 24.18.1 | Next 16.2.12, pnpm 11.18.0, ESLint 10.8.0, Vitest 4.1.10 | Next exige >=20.9; pnpm >=22.13; ESLint aceita >=24; Vitest aceita >=24. |
| Next 16.2.12 | React/React DOM 19.2.8 | Peer oficial aceita `^19.0.0`; manter React e React DOM exatamente iguais. |
| Next 16.2.12 | TypeScript >=5.1 | Usar 6.0.3 por compatibilidade do tooling. App Router/Turbopack são padrão. |
| TypeScript 6.0.3 | typescript-eslint 8.65 / eslint-config-next 16.2.12 | `typescript-eslint` aceita TypeScript `>=4.8.4 <6.1.0`. |
| TypeScript 7.0.2 | **Não adotado ainda** | Sem API programática estável em 7.0; usar lado a lado só em experimento de CI, não como compilador oficial. |
| `@supabase/ssr` 0.12.4 | `@supabase/supabase-js ^2.111.0` | Instalar 2.111.0 e pin exato; revisar changelog em todo upgrade pré-1.0. |
| Drizzle ORM 0.45.2 | Postgres.js >=3; escolhido 3.4.9 | Não misturar snippets da documentação `@rc` sem adaptar à versão estável. |
| Postgres.js 3.4.9 | Supavisor Transaction mode | `prepare: false`; conexão na porta 6543. Para migration usar endpoint direto 5432. |
| Schema BETUFU | PostgreSQL 17 | Não usar sintaxe/feature exclusiva de PostgreSQL 18 enquanto o projeto Supabase estiver em 17. |
| Vitest 4.1.10 | Node `^20 || ^22 || >=24` | Node 24 é suportado; Server Components assíncronos críticos vão para Playwright. |
| ESLint 10.8.0 | Node `^20.19 || ^22.13 || >=24` | Executar CLI; `next lint` não existe no Next 16. |
| file-type 22.0.1 | Node >=22 | Usar somente em módulo server-only/Node runtime. |
| `@types/node` 24.13.3 | Node 24.x | Não instalar `@types/node@26` enquanto produção estiver em Node 24. |
| Vercel `gru1` | Supabase `sa-east-1` | Colocalização reduz latência nas transações e evita o default Vercel `iad1`. |

## Política de atualização

- Fixar versões exatas e lockfile; usar automação de dependências em PRs agrupados, nunca auto-merge em Auth, ORM, driver, Next ou cálculo.
- Aplicar patches de segurança do Node LTS e Next prioritariamente, com smoke E2E.
- Atualizar Supabase JS e SSR juntos conforme peers; rodar fluxos de refresh/logout e RLS.
- Toda alteração de Drizzle/driver roda suíte de concorrência contra PostgreSQL real: duas apostas simultâneas, fechamento concorrente, liquidação idempotente e retry serializável.
- Reavaliar TypeScript 7 quando `typescript-eslint`/`eslint-config-next` suportarem a API 7.x sem alias de compatibilidade.

## Fontes

### Runtime, framework e linguagem

- [Node.js 24.18.1 LTS — release oficial e correções de segurança](https://nodejs.org/en/blog/release/v24.18.1) — **ALTA**.
- [Ciclo oficial de releases do Node.js](https://nodejs.org/en/about/previous-releases) — 24 LTS versus 26 Current — **ALTA**.
- [Next.js: instalação e requisitos de sistema](https://nextjs.org/docs/app/getting-started/installation) e [upgrade para Next 16](https://nextjs.org/docs/app/guides/upgrading/version-16) — Node >=20.9, App Router/Turbopack — **ALTA**.
- [Registry NPM: Next.js 16.2.12](https://registry.npmjs.org/next/16.2.12), [React 19.2.8](https://registry.npmjs.org/react/19.2.8) e [React DOM 19.2.8](https://registry.npmjs.org/react-dom/19.2.8) — versões e peers — **ALTA**.
- [Anúncio oficial do TypeScript 7.0](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/) — ausência da API estável e orientação de compatibilidade 6.x — **ALTA**.
- [Registry NPM: TypeScript 6.0.3](https://registry.npmjs.org/typescript/6.0.3) e [typescript-eslint 8.65.0](https://registry.npmjs.org/typescript-eslint/8.65.0) — versão e peer `<6.1.0` — **ALTA**.
- [Next.js ESLint](https://nextjs.org/docs/app/api-reference/config/eslint) — flat config e remoção de `next lint` — **ALTA**.

### PostgreSQL, Drizzle e integridade

- [PostgreSQL: versões suportadas](https://www.postgresql.org/versions.json) — 18.4 upstream atual — **ALTA**.
- [PostgreSQL 18: tipos numéricos](https://www.postgresql.org/docs/18/datatype-numeric.html) — `NUMERIC` exato versus floating point inexato — **ALTA**; o comportamento citado também existe no 17.
- [PostgreSQL: isolamento de transações](https://www.postgresql.org/docs/18/transaction-iso.html) — serialização, abort/retry e locks — **ALTA**; aplicar sem recursos exclusivos do 18.
- [Drizzle: migrations](https://orm.drizzle.team/docs/migrations), [índices e constraints](https://orm.drizzle.team/docs/indexes-constraints) e [prefixo Supabase](https://orm.drizzle.team/docs/latest-releases/drizzle-orm-v0320) — **ALTA**.
- [Drizzle + Supabase](https://orm.drizzle.team/docs/connect-supabase) — Postgres.js e `prepare: false` no transaction pooler — **ALTA**.
- [Registries NPM: Drizzle ORM 0.45.2](https://registry.npmjs.org/drizzle-orm/0.45.2), [Drizzle Kit 0.31.10](https://registry.npmjs.org/drizzle-kit/0.31.10) e [Postgres.js 3.4.9](https://registry.npmjs.org/postgres/3.4.9) — **ALTA**.

### Supabase, realtime, autenticação e privacidade

- [Supabase: upgrade da plataforma](https://supabase.com/docs/guides/platform/upgrading) — PostgreSQL 17.10 — **ALTA**.
- [Supabase: regiões disponíveis](https://supabase.com/docs/guides/platform/regions) — São Paulo `sa-east-1` — **ALTA**.
- [Conexões PostgreSQL no Supabase](https://supabase.com/docs/guides/database/connecting-to-postgres) — direto/session/transaction e prepared statements — **ALTA**.
- [Supabase SSR Auth](https://supabase.com/docs/guides/auth/server-side/advanced-guide), [password auth](https://supabase.com/docs/guides/auth/passwords), [RBAC/custom claims](https://supabase.com/docs/guides/api/custom-claims-and-role-based-access-control-rbac) e [RLS](https://supabase.com/docs/guides/database/postgres/row-level-security) — **ALTA**.
- [Supabase Auth: rate limits](https://supabase.com/docs/guides/auth/rate-limits) e [CAPTCHA/Turnstile](https://supabase.com/docs/guides/auth/auth-captcha) — **ALTA**.
- [Supabase Realtime: guia inicial](https://supabase.com/docs/guides/realtime/getting_started), [Broadcast](https://supabase.com/docs/guides/realtime/broadcast) e [mudanças do banco](https://supabase.com/docs/guides/realtime/subscribing-to-database-changes) — canais privados e broadcast via trigger — **ALTA**.
- [Supabase Storage: buckets privados](https://supabase.com/docs/guides/storage/buckets/fundamentals), [controle de acesso](https://supabase.com/docs/guides/storage/security/access-control), [limites](https://supabase.com/docs/guides/storage/uploads/file-limits) e [exclusão de objetos](https://supabase.com/docs/guides/storage/management/delete-objects) — **ALTA**.
- [Supabase Cron](https://supabase.com/docs/guides/cron) e [versão `pg_cron` da plataforma](https://supabase.com/docs/guides/troubleshooting/pgcron-debugging-guide-n1KTaz) — **ALTA** para capacidade/versão; **MÉDIA** para adequação após escala real.
- [Registries NPM: Supabase JS 2.111.0](https://registry.npmjs.org/%40supabase%2Fsupabase-js/2.111.0), [Supabase SSR 0.12.4](https://registry.npmjs.org/%40supabase%2Fssr/0.12.4) e [CLI 2.111.0](https://registry.npmjs.org/supabase/2.111.0) — **ALTA**.

### Deploy, UI e testes

- [Regiões da Vercel](https://vercel.com/docs/regions) e [configuração de Function region](https://vercel.com/docs/functions/configuring-functions/region) — `gru1` e recomendação de proximidade do banco — **ALTA**.
- [Tailwind CSS: integração por framework](https://tailwindcss.com/docs/installation/framework-guides) e [shadcn/ui para Next.js](https://ui.shadcn.com/docs/installation/next) — **ALTA**.
- [Next.js: estratégia de testes](https://nextjs.org/docs/app/guides/testing) — Vitest e Playwright, com preferência por E2E para Server Components assíncronos — **ALTA**.
- Demais versões foram verificadas nos endpoints de versão específicos do [registry NPM](https://registry.npmjs.org/) em 2026-08-01 — **ALTA**.

---
*Stack research for: BETUFU — aplicação social de palpites esportivos com créditos exclusivamente virtuais*
*Researched: 2026-08-01*
