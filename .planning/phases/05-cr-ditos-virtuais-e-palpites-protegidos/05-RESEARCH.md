# Fase 5: Créditos Virtuais e Palpites Protegidos — Pesquisa

**Pesquisado:** 2026-09-13
**Domínio:** ledger de créditos virtuais, comando de palpite pré-jogo e integração Angular/OpenAPI
**Confiança:** HIGH para fronteiras e padrões existentes; MEDIUM para nomes internos ainda em aberto

<user_constraints>
## Restrições do responsável

As decisões D-122 a D-133 de `05-CONTEXT.md` estão congeladas e não podem ser reinterpretadas durante a execução:

- Cada conta elegível recebe uma única concessão inicial de **1.000,00 créditos**, auditável e idempotente; revalidação nunca concede novamente.
- O palpite aceita valor mínimo de **10,00** e permite **all-in** até o saldo disponível.
- Saldos, apostas e retornos usam duas casas decimais; precisão maior é recusada.
- Retorno potencial é `stake × odd aceita`, incluindo o stake; o lucro líquido é retorno menos stake.
- Escala 2 e arredondamento `HALF_UP` são versionados no snapshot aceito.
- Papel administrativo concedido depois do aceite só bloqueia apostas futuras; conflito já vigente no instante do aceite gera anulação e crédito compensatório por novos lançamentos.
- A mensagem de conflito é genérica, acionável e sem papel, escopo, identidade ou regra explorável.
- O bilhete vive na página pública de odds; visitante recebe CTA de login, conta sem matrícula recebe ação de matrícula, conta elegível recebe saldo/formulário e administrador bloqueado recebe estado genérico.
- O cartão do próprio palpite persiste após reload, sem histórico completo ou dados de terceiros.
- Mudança da versão da odd invalida o envio e exige reconfirmação explícita.

### Fora do escopo desta fase

Liquidação/pagamento, ranking, grupos, conquistas, histórico completo, agregados após fechamento, compra/venda/transferência/saque/recarga, missões, apostas ao vivo ou múltiplas e ajuste administrativo discricionário permanecem para a Fase 6 ou posteriores.
</user_constraints>

<architectural_responsibility_map>
## Mapa de responsabilidade

| Capacidade | Camada primária | Camada secundária | Motivo |
|---|---|---|---|
| Concessão e saldo autoritativo | PostgreSQL/ledger | Backend `ledger` | O saldo e os lançamentos precisam de lock, constraints e histórico imutável. |
| Aceite de palpite | Backend `betting` | PostgreSQL, portas de `verification`, `identity`, `sports` e `odds` | Uma única transação revalida todos os fatos e debita o ledger. |
| Identidade OIDC da requisição | BFF/backend | `platform.identity.OperationalIdentityLookup` | O browser não envia nem armazena tokens; o servidor resolve `(issuer, subject)`. |
| Bilhete e estados de elegibilidade | Browser Angular | API versionada | A UI orienta a jornada, mas nunca concede autorização nem decide elegibilidade. |
| Contrato de transporte | OpenAPI | Cliente Angular gerado | O YAML é a fonte do contrato e o gerador/anti-drift protege a sincronização. |
</architectural_responsibility_map>

<research_summary>
## Resumo

O repositório já possui fronteiras Spring Modulith reservadas para `betting` e `ledger`, PostgreSQL 18 como autoridade (`StorageAuthorityRules`), eventos públicos de verificação (`EnrollmentVerified`) e portas neutras para elegibilidade, papéis e resolução de identidade. A implementação deve usar esses contratos, nunca importar pacotes `internal` de outro módulo. A concessão inicial pode ser acionada pelo evento de matrícula verificada e protegida por uma chave de negócio única; a leitura do saldo também deve ser segura para contas antigas que já ficaram elegíveis.

O comando de palpite precisa capturar um snapshot completo (mercado, versão da odd/publicação, regra, deadline, stake, retorno e identidade) antes do débito. A mesma transação deve bloquear mercado/versão, elegibilidade/conflito e conta de créditos em ordem determinística, verificando deadline e versão esperada. Uma tabela de recibos por ator/tipo/chave com fingerprint de payload permite replay idempotente e rejeita reutilização divergente. Aceite e débito são fatos append-only; anulação posterior só cria fatos compensatórios.

No frontend, a página pública de odds existente deve receber um bilhete como estado adicional, consumindo apenas o cliente gerado. Estados anônimo, sem matrícula, elegível, saldo insuficiente, conflito administrativo, stale e sucesso devem ser explícitos e acessíveis. A visualização de palpites de terceiros continua proibida antes do fechamento. A forma exata de reconfirmação da odd fica condicionada ao contrato `05-UI-SPEC.md`, que deve ser aprovado antes do plano de UI.

**Recomendação principal:** construir cinco slices sequenciais — ledger/concessão, aceite de palpite, contrato REST/cliente, bilhete público e certificação concorrente/privacidade — com migrations aditivas V29+ e testes PostgreSQL reais.
</research_summary>

<standard_stack>
## Stack já aprovada

| Tecnologia | Uso na Fase 5 | Regra |
|---|---|---|
| Java 25 + Spring Boot 4.1 | serviços, controllers e Problem Details | preservar perfis `runtime | integrated` e BFF OIDC |
| Spring Modulith 2.1 | fronteiras `betting` e `ledger` | contratos públicos em pacotes não-`internal`; verificar `ApplicationModules` |
| PostgreSQL 18 + Flyway | contas, ledger, palpites, recibos e constraints | migrations aditivas; sem UPDATE/DELETE de fatos históricos |
| Spring JDBC/JPA existente | repositórios e transações | seguir `FOR UPDATE`, compare-and-set e `TransactionTemplate` já usados |
| OpenAPI 3.1 + OpenAPI Generator 7.24.0 | contrato e cliente Angular | atualizar YAML e rodar `generate-api.mjs`; nunca editar `generated/` |
| Angular 22 + TypeScript 6 + Signals | store/adapters/bilhete | standalone, OnPush, mensagens sem PII e sem autorização no cliente |
| JUnit/Testcontainers + Angular/Vitest + Playwright | certificação | PostgreSQL real para concorrência e browser para jornada |

Não é necessária dependência nova. Redis continua apenas auxiliar de sessão/cache e não pode armazenar saldo ou palpite autoritativo.
</standard_stack>

<architecture_patterns>
## Padrões recomendados

### Fluxo de concessão inicial

```text
EnrollmentService.register()
  -> evento público EnrollmentVerified(identityId)
  -> listener do ledger inicia transação própria
  -> INSERT credit_account se ausente + INSERT GRANT se ausente
  -> constraint (identity_id, grant_kind) e recibo idempotente
  -> saldo 1.000,00 persistido; nenhuma matrícula/HMAC é copiada
```

O listener deve ser seguro para retry e para eventos duplicados. Caso a leitura ocorra antes do listener, `ensureInitialGrant` pode ser chamado dentro de uma transação do ledger; em ambos os casos uma conta elegível jamais recebe duas concessões.

### Fluxo de aceite atômico

```text
POST /api/v1/me/predictions + Idempotency-Key
  -> resolve issuer/subject -> identityId (servidor)
  -> lock mercado/versão e lê evento/competition
  -> verifica enrollment ATIVO, papéis conflitantes e deadline
  -> compara marketVersion/publication/ruleVersion enviados
  -> valida seleção, escala, mínimo e all-in contra saldo bloqueado
  -> calcula retorno HALF_UP(scale=2)
  -> grava prediction + ledger debit + receipt na mesma transação
  -> devolve o mesmo ticket em replay idempotente
```

O lock order deve ser documentado e coberto por teste concorrente para evitar deadlock e saldo negativo. Uma unique key `(identity_id, market_id)` é a última barreira contra duas seleções aceitas no mesmo mercado.

### Fronteiras de módulos

- `betting` consome apenas portas públicas de `ledger`, `verification`, `identity`, `sports` e `odds`.
- `ledger` não importa `betting`; recebe um `sourceType/sourceId` opaco para débitos e compensações.
- `odds` expõe uma projeção pública específica para aceitação, sem vazar `odds.internal.publicview`.
- `platform` resolve a identidade OIDC por `OperationalIdentityLookup`; controllers não consultam repositório de outro módulo.
- Angular consome adapters sobre o cliente gerado; nunca chama `HttpClient` diretamente para comandos de negócio.

### Anti-padrões proibidos

- confiar em capability, saldo ou elegibilidade enviados pelo frontend;
- usar e-mail como chave de identidade;
- fazer `UPDATE`/`DELETE` de palpite, odd, ledger ou auditoria;
- conceder créditos em cada GET/revalidação sem chave idempotente;
- aceitar odd atual sem comparar a versão confirmada no formulário;
- expor seleção de terceiros, distribuição, identidade, matrícula, tokens ou saldo de outra pessoa;
- adicionar `any`, endpoint não documentado ou modelo TypeScript manual em `generated/`.
</architecture_patterns>

<dont_hand_roll>
## Não reinventar

| Problema | Não criar | Reutilizar |
|---|---|---|
| Resolver identidade | parser próprio de e-mail/claims | `OperationalIdentityLookup` e o padrão `OidcClaimsProjection` |
| Elegibilidade | consulta a `ufu_enrollment` pelo betting | `EnrollmentEligibility` |
| Papel/conflito | capabilities do Angular | `OperationalRoleQuery` + `OperationalRole` e competição do snapshot de odds |
| Erros | texto de exceção exposto | `ApiProblemException`, `ApiErrorCode` e `ProblemDetailsHandler` |
| Idempotência | cache/flag em memória | recibo persistido, fingerprint e unique constraints PostgreSQL |
| Transporte Angular | `HttpClient` direto/DTO duplicado | OpenAPI + adapter/store Signals |
</dont_hand_roll>

<common_pitfalls>
## Armadilhas a vigiar

1. **Concessão duplicada após revalidação:** evento duplicado ou retry pode inserir duas grants. Prevenir com chave única no banco, `ON CONFLICT` e teste que repete registro/GET.
2. **Saldo negativo sob concorrência:** duas requisições leem o mesmo saldo sem lock. Bloquear a conta e verificar o valor dentro da mesma transação; testar `N` requisições simultâneas e retry do mesmo idempotency key.
3. **Odd stale:** o navegador envia versão/publication antiga depois de uma publicação nova. Comparar ambos os identificadores e retornar Problem Details estável que force reconfirmação.
4. **Conflito administrativo incompleto:** checar somente `ADMINISTRADOR_GERAL` ou somente competição. Revalidar os dois papéis vigentes e a competição do evento; atleta continua permitido.
5. **Divergência de arredondamento:** Java, OpenAPI e Angular calculam valores diferentes. O backend é autoridade; a regra, escala, modo e valores resultantes devem viajar no ticket e ter casos de fronteira.
6. **Vazamento no erro/log:** exceções JDBC podem conter matrícula, HMAC ou payload. Responder apenas códigos/detalhes estáveis e sanitizar logs conforme padrões existentes.
7. **Falsa cobertura por mock:** testes Angular/Playwright não provam lock nem autorização. A certificação deve usar Testcontainers/PostgreSQL e testes de integração de segurança; browser cobre somente jornada/privacidade.
</common_pitfalls>

<verification_strategy>
## Estratégia de verificação

- **Ledger:** JUnit unitário + integração PostgreSQL para grant única, persistência entre competições, constraints, imutabilidade e ausência de fontes extras.
- **Betting:** integração com mercado/odds/verification/identity/sports reais; testes de mínimo, all-in, precisão, deadline, estado, conflito, atleta, stale, duplicate selection e replay divergente.
- **Concorrência:** duas ou mais transações reais sobre a mesma conta/mercado; assert saldo nunca negativo, exatamente um ticket/débito e ausência de deadlock não tratado.
- **Contrato:** validação OpenAPI, geração determinística e specs Angular para headers, cookie/XSRF, `Idempotency-Key`, 401/403/409 e ausência de campos privados.
- **Browser:** Chromium e Firefox em desktop/320px; visitante, sem matrícula, elegível e stale; nenhum PII ou palpite de terceiro no DOM/network.
- **Snapshot:** `05-VALIDATION.md` e `05-VERIFICATION.md` devem registrar contagens reais, hashes das migrations/contrato/fixtures e `git rev-parse HEAD`; SKIP não é PASS.
</verification_strategy>

<source_evidence>
## Evidência do código atual

- `verification.EnrollmentVerified` carrega somente `identityId` e é publicado após registro bem-sucedido.
- `verification.EnrollmentEligibility` fornece `ATIVO`, `LIBERADO` e `SEM_VINCULO` sem expor o repositório.
- `identity.OperationalRoleQuery` trata `ADMINISTRADOR_GERAL` global somente com `scopeRef == null` e papéis escopados por par exato.
- `platform.identity.OperationalIdentityLookup` resolve `(issuer, subject)` para `identityId` e papéis sem criar identidade inexistente.
- `odds.internal.market.OddsMarketService` já usa `@Transactional`, versão corrente, deadline e lock otimista; novas apostas devem consumir uma porta pública, não o pacote interno.
- `OddsPublicViewModels` é explicitamente uma projeção anônima e não deve ser importada por `betting`; criar contrato público de leitura para aceitação.
- `OddsMarketRepository` demonstra `FOR UPDATE`, `ON CONFLICT`, receipts e histórico imutável como padrões de persistência.
- `StorageAuthorityRules` declara PostgreSQL como fonte de saldo/apostas; Redis não pode virar atalho.
- `ProblemDetailsHandler` e `ApiErrorCode` fornecem respostas estáveis sem mensagem interna.
- `PublicMarketPageComponent`, `PublicMarketStore` e adapters existentes são a base da integração do bilhete.
- `generate-api.mjs` fixa OpenAPI Generator 7.24.0 e digest do engine; geração deve ser executada após qualquer mudança contratual.

O índice RepoWise consultado está um commit atrás do HEAD (`indexed_commit=4ebc619`, `live_head=720b86d`). As assinaturas acima foram conferidas novamente no código local; o índice não deve ser atualizado como parte desta pesquisa.
</source_evidence>

<deferred>
## Decisões deliberadamente adiadas

- política visual de reconfirmação e microinterações: futuro `05-UI-SPEC.md`;
- settlement, ranking, grupos, conquistas, histórico completo e conflitos pós-liquidação: Fase 6;
- ajuste administrativo direto, top-up, compra/venda/transferência/saque e múltiplas: fora do MVP;
- nomes definitivos de classes internas e granularidade entre migrations, desde que as fronteiras e invariantes deste documento sejam preservadas.
</deferred>

---

*Pesquisa técnica concluída para a Fase 5; nenhum código, dependência, banco ou Git mutável foi alterado.*
