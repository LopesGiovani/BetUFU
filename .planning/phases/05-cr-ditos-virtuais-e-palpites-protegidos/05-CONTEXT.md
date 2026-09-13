# Phase 5: Créditos Virtuais e Palpites Protegidos - Context

**Gathered:** 2026-09-12
**Status:** Ready for planning

<domain>
## Phase Boundary

A Fase 5 entrega a concessão inicial única de créditos virtuais e o registro
pré-jogo de palpites simples por pessoas elegíveis. Saldo, elegibilidade,
papéis, escopos, conflito administrativo, mercado, prazo, versão da odd,
limites, idempotência e unicidade são revalidados atomicamente no PostgreSQL.
O palpite aceito, o débito e os snapshots versionados são imutáveis e gravados
na mesma transação.

Esta fase também entrega o bilhete e o comprovante do próprio palpite no
mercado público. Ela define a fórmula versionada do retorno potencial que a
Fase 6 consumirá, mas não implementa liquidação, pagamento por resultado,
ranking, histórico completo de palpites ou correção pós-liquidação.

</domain>

<decisions>
## Implementation Decisions

As decisões D-25 a D-52 da Fase 2, D-53 a D-100 da Fase 3 e D-101 a D-121
da Fase 4 continuam vinculantes. Em especial: matrícula ativa e única governa
elegibilidade; atleta pode apostar no MVP; administradores são bloqueados pela
autoridade real no servidor; odds aceitas são imutáveis; e PostgreSQL é a
fonte autoritativa de saldo, palpite e regras de negócio.

### Concessão, limites e precisão

- **D-122:** Cada conta que se torna elegível recebe uma única concessão
  inicial de `1.000,00` créditos virtuais. Revalidação não concede novamente,
  e a concessão precisa ser um lançamento auditável e idempotente no ledger.
  — **Reversibility:** costly — depois das primeiras concessões, mudar o valor
  padrão não pode reescrever lançamentos históricos nem criar desigualdade
  silenciosa entre participantes.
- **D-123:** O valor mínimo de um palpite é `10,00` créditos. Não existe teto
  fixo menor que o saldo: o máximo é o saldo disponível no instante da
  aceitação, portanto all-in é permitido. A regra continua determinística e
  versionada como parte do snapshot aceito.
- **D-124:** Saldos, valores apostados e retornos usam escala de duas casas
  decimais. O frontend e a API não aceitam precisão maior; o backend continua
  sendo a autoridade e valida a escala novamente.

### Retorno potencial e arredondamento

- **D-125:** O retorno potencial usa odds decimais no formato
  `valor apostado × odd aceita` e inclui o valor originalmente apostado.
  Exemplo: `100,00 × 2,10 = 210,00` de retorno total e `110,00` de lucro
  líquido. O bilhete pode mostrar retorno total e lucro líquido para evitar
  ambiguidade.
- **D-126:** Quando a multiplicação produzir mais de duas casas, o resultado
  é arredondado para escala 2 com `HALF_UP`, de forma consistente com a regra
  de arredondamento já usada nas odds da Fase 4. Fórmula, escala e modo de
  arredondamento pertencem à versão da regra aceita pelo palpite.
  — **Reversibility:** costly — uma regra nova pode valer para palpites futuros,
  mas jamais recalcular silenciosamente snapshots já aceitos.

### Conflito administrativo descoberto depois

- **D-127:** Um papel administrativo concedido depois da aceitação válida de
  um palpite não invalida esse palpite. O novo papel e seu escopo bloqueiam
  somente novas tentativas incompatíveis a partir da vigência da concessão.
- **D-128:** Se evidência autoritativa demonstrar que o papel e o escopo
  conflitantes já estavam vigentes quando o palpite foi aceito, o palpite é
  anulado e os créditos são devolvidos por novos registros compensatórios. O
  palpite e o débito originais nunca são apagados ou sobrescritos.
- **D-129:** A pessoa recebe mensagem genérica e acionável: o palpite foi
  anulado por incompatibilidade de elegibilidade no instante do envio e os
  créditos foram devolvidos. A resposta inclui código de referência, mas não
  revela papel, escopo, identidade operacional ou regras exploráveis.

### Bilhete, elegibilidade e comprovante

- **D-130:** O bilhete é integrado à página pública do mercado de odds, e não
  enviado para uma área operacional ou para uma página de aposta isolada.
- **D-131:** A área do bilhete orienta cada estado honestamente: visitante vê
  “Entre para palpitar”; conta sem matrícula ativa recebe ação para registrar
  matrícula; conta elegível vê saldo e formulário; conta administrativamente
  impedida recebe uma explicação genérica de inelegibilidade. A interface não
  expõe papel, escopo ou matriz de autorização, e a decisão final permanece no
  servidor.
- **D-132:** Depois da aceitação, o formulário é substituído no mesmo mercado
  por um cartão persistente do próprio palpite, inclusive após recarregar a
  página. Ele mostra seleção, créditos, odd e versões aceitas, retorno
  potencial, horário e estado pendente. Isso não cria o histórico completo da
  Fase 6 e não revela palpites ou distribuições de outras pessoas.
- **D-133:** Se a versão da odd mudar enquanto o bilhete estiver aberto, o
  envio com a versão anterior é recusado e a pessoa precisa confirmar
  explicitamente a nova odd antes de tentar novamente, conforme `ODDS-14`.
  A forma visual exata dessa reconfirmação será definida no contrato de UI.

### the agent's Discretion

- Nomes de pacotes, classes, tabelas e tipos internos, preservando os módulos
  Spring Modulith `betting` e `ledger` e sem importar pacotes `internal` de
  outros módulos.
- Ordem concreta de locks, constraints, chaves de negócio, receipts e
  fingerprints do payload, desde que as garantias de atomicidade,
  concorrência, idempotência e ausência de saldo negativo sejam provadas.
- Mecanismo de configuração versionada dos valores aprovados, sem defaults
  silenciosos divergentes entre backend, frontend, seed e testes.
- Códigos estáveis de Problem Details e textos finais, preservando resposta
  mínima, correlação e ausência de dados privados.
- Composição visual, responsividade, foco, loading e estados transitórios do
  bilhete, subordinados ao futuro `05-UI-SPEC.md` e aos tokens já adotados.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Produto, escopo e requisitos

- `.planning/PROJECT.md` — valor central, limites do MVP, economia sem valor
  monetário e arquitetura aprovada.
- `.planning/ROADMAP.md` — objetivo, dependências, critérios de sucesso e
  fronteira entre as Fases 5 e 6.
- `.planning/REQUIREMENTS.md` — `VERI-10`, `CRED-01..06`, `CRED-08`,
  `CRED-10`, `ODDS-14`, `PRED-01..08`, `INTE-01..04`, `DATA-03..05` e
  `API-03`.
- `AGENTS.md` — governança interativa, isolamento, aprovação, verificação e
  operações Git.

### Decisões herdadas

- `.planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-CONTEXT.md`
  — matrícula autodeclarada, identidade, papéis, escopos e autoridade no
  PostgreSQL.
- `.planning/phases/03-experi-ncia-esportiva-do-piloto/03-CONTEXT.md` — atleta
  pode apostar no MVP; administradores gerais e de competição permanecem
  bloqueados conforme papel e escopo.
- `.planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md` —
  odds decimais, versões imutáveis, mercado pré-jogo e regras de suspensão,
  fechamento e transparência.
- `.planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-VALIDATION.md`
  — provas predecessoras de elegibilidade, papéis, escopos e privacidade.
- `.planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-VALIDATION.md`
  — certificação dos mercados e contratos de odds consumidos pela fase.

### Contratos e fronteiras de código

- `backend/openapi/betufu.yaml` — contrato REST governado a ser evoluído antes
  da geração do cliente TypeScript; arquivos gerados nunca são editados à mão.
- `backend/src/main/java/br/com/betufu/betting/package-info.java` — fronteira
  Spring Modulith reservada para apostas.
- `backend/src/main/java/br/com/betufu/ledger/package-info.java` — fronteira
  Spring Modulith reservada para saldo e lançamentos.
- `backend/src/main/java/br/com/betufu/platform/persistence/StorageAuthorityRules.java`
  — PostgreSQL é a única fonte autoritativa de saldo e apostas.
- `backend/src/main/java/br/com/betufu/verification/EnrollmentEligibility.java`
  — porta pública existente para verificar matrícula ativa.
- `backend/src/main/java/br/com/betufu/identity/OperationalRole.java` —
  vocabulário fechado dos três papéis operacionais.
- `backend/src/main/java/br/com/betufu/identity/OperationalRoleQuery.java` —
  porta pública para consultar concessões vigentes com escopo.
- `backend/src/main/java/br/com/betufu/odds/internal/publicview/OddsPublicViewModels.java`
  — projeção anônima atual; o planejamento deve criar uma fronteira pública
  adequada para apostas sem importar tipos `internal`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- `EnrollmentEligibility`: consulta pública já implementada para matrícula
  ativa, sem acesso do novo módulo ao repositório interno de verificação.
- `OperationalRoleQuery`: fornece papéis e `scopeRef` vigentes, necessários à
  proibição de administradores gerais e de competição.
- `AuditRecorder` e `ApiErrorCode`: padrões existentes para auditoria e erros
  estáveis compatíveis com Problem Details.
- Infraestrutura de odds da Fase 4: oferece mercados, estados, publicações e
  versões imutáveis que o comando de palpite precisa congelar.
- Cliente Angular gerado e padrões de adapter/store Signals: base para o
  bilhete sem `HttpClient` direto nem duplicação manual de DTOs.

### Established Patterns

- Spring Modulith protege fronteiras; contratos entre módulos são públicos e
  pacotes `internal` não atravessam módulos.
- Migrations Flyway usam constraints explícitas e são verificadas no
  PostgreSQL real.
- Comandos críticos usam chave de idempotência persistente, fingerprint do
  payload, resposta repetível e recusa de reutilização divergente.
- Dados imutáveis são corrigidos por novos fatos compensatórios, nunca por
  `UPDATE` destrutivo ou exclusão do histórico.
- OpenAPI, implementação Java e cliente Angular gerado evoluem juntos, com
  digest e anti-drift governados.
- Autorização e elegibilidade são aplicadas no servidor; sinais do frontend
  servem apenas para orientar a experiência.

### Integration Points

- `verification` decide se a matrícula da identidade está ativa.
- `identity` informa papéis operacionais e escopos vigentes.
- `sports` relaciona o evento à competição usada no conflito administrativo.
- `odds` fornece mercado, prazo canônico, estado e versão exata da publicação.
- `betting` coordena o comando idempotente e preserva a seleção aceita.
- `ledger` concede, debita e futuramente recebe pagamentos/devoluções sem
  permitir movimentação órfã ou saldo negativo.
- A página pública de odds recebe o bilhete e o comprovante do próprio
  palpite; rotas operacionais continuam separadas.

</code_context>

<specifics>
## Specific Ideas

- Exemplo de retorno: `100,00` créditos na odd `2,10` produz retorno total de
  `210,00` e lucro líquido de `110,00`.
- O all-in deve continuar possível mesmo quando o saldo tiver centavos.
- A interface usa sempre a palavra “créditos”, sem símbolo monetário ou
  equivalência com reais.
- O comprovante do próprio palpite deve sobreviver ao reload da página do
  mercado, mas não substitui o histórico completo da fase seguinte.

</specifics>

<deferred>
## Deferred Ideas

- Liquidação, pagamento, devolução por resultado, estados ganho/perdido e
  histórico completo de palpites pertencem à Fase 6.
- Conflito descoberto depois da liquidação, especialmente quando créditos
  pagos incorretamente já tiverem sido gastos, depende da política
  compensatória ainda aberta da Fase 6.
- Ajuste administrativo direto no ledger e seu eventual maker-checker
  permanecem decisões da Fase 6.
- Exibição de palpites agregados depois do fechamento do mercado será decidida
  com as superfícies de histórico/ranking; antes do fechamento continua
  proibida por `PRED-07`.
- Compra, venda, transferência, saque, recarga, missão, recuperação recorrente,
  apostas ao vivo e apostas múltiplas permanecem fora do MVP.

</deferred>

---

*Phase: 5-Créditos Virtuais e Palpites Protegidos*
*Context gathered: 2026-09-12*
