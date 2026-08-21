---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 09
subsystem: session-security
tags: [spring-session, redis, oidc, revocation, security]

requires:
  - phase: 01-fundacao-segura-da-plataforma
    provides: Sessao Redis com toque seletivo e revogacao OIDC
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Medicao do indice de principal e decisao pelo repositorio indexado
provides:
  - Enumeracao e encerramento de todas as sessoes de uma identidade
  - Indice Redis de principal mantido no mesmo ciclo transacional da sessao
  - Limpeza explicita, natural, sob consulta e periodica de indices orfaos
  - Resultado estruturado para falhas locais e de revogacao remota
affects: [02-11, 02-23, IDEN-05, OPER-01]

actuals:
  tasks: 2
  commits: 0

tech-stack:
  added: []
  patterns:
    - Encerramento local antes da coordenacao de revogacao remota
    - Redis SCAN e SSCAN em lotes para reconciliacao sem KEYS ou SMEMBERS globais
    - Bootstrap Spring independente de conexao Redis

key-files:
  created:
    - backend/src/main/java/br/com/betufu/platform/session/IdentitySessionTerminationService.java
    - backend/src/test/java/br/com/betufu/platform/session/IdentitySessionTerminationIT.java
    - backend/src/test/java/br/com/betufu/platform/session/IdentitySessionTerminationServiceTest.java
  modified:
    - backend/src/main/java/br/com/betufu/platform/session/SelectiveTouchSessionRepository.java
    - backend/src/main/java/br/com/betufu/platform/session/SessionSecurityConfiguration.java
    - backend/src/main/resources/application.yaml
    - backend/src/test/java/br/com/betufu/platform/EnvironmentConfigurationTest.java
    - backend/src/test/java/br/com/betufu/platform/session/PrincipalNameIndexProbeIT.java
    - scripts/validate-runtime-prerequisites.mjs
    - scripts/validate-runtime-prerequisites.test.mjs

key-decisions:
  - "O repository-type de sessao passa de default para indexed; a mutacao negativa agora e voltar ao valor antigo."
  - "O wrapper mantem indice, shadow de expiracao e chave reversa de principal na mesma transacao Redis."
  - "A aplicacao configura notificacoes de keyspace no arranque e falha ruidosamente se o Redis recusar."
  - "A varredura global inicia apos 15 minutos; consultas continuam fazendo autocorrecao imediata."

requirements-completed: [IDEN-05, OPER-01]

coverage:
  - id: D1
    description: Todas as sessoes da identidade alvo sao encerradas e sessoes de terceiros permanecem intactas.
    requirement: IDEN-05
    verification:
      - kind: integration
        ref: IdentitySessionTerminationIT
        status: pass
    human_judgment: false
  - id: D2
    description: Falhas de exclusao, listener e revogacao remota sao reportadas sem reativar sessoes ja encerradas.
    requirement: OPER-01
    verification:
      - kind: unit
        ref: IdentitySessionTerminationServiceTest
        status: pass
    human_judgment: false
  - id: D3
    description: Expiracao natural e reconciliacao removem indices orfaos sem afetar outra identidade.
    requirement: IDEN-05
    verification:
      - kind: integration
        ref: PrincipalNameIndexProbeIT
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-21
status: complete
---

# Phase 2 Plan 09: Encerramento governado de sessões

**A BETUFU agora consegue localizar e encerrar todas as sessões de uma identidade, preservando sessões de terceiros e expondo falhas parciais sem afirmar uma revogação que não aconteceu.**

## Accomplishments

- Alterou o Spring Session para `repository-type: indexed` e preservou os contratos de oito horas, `on-save` e `on-set-attribute`.
- Criou `IdentitySessionTerminationService`, que tenta todas as exclusões locais antes de coordenar a revogação remota somente para exclusões bem-sucedidas.
- Diferenciou falha de exclusão local, falha do listener e resultado de revogação remota; qualquer uma delas torna `isFullyRevoked()` falso.
- Manteve índice de principal, chave shadow de expiração e chave reversa técnica de forma transacional.
- Interceptou expiração natural, adicionou autocorreção durante consultas e uma reconciliação global limitada por `SCAN`/`SSCAN`.
- Preservou o contrato de bootstrap: criar o contexto Spring não abre conexão Redis; a primeira varredura ocorre após 15 minutos.

## Decisions Made

- Foi adotado o caminho único do repositório indexado. O wrapper escreve os hashes diretamente e, por isso, também passou a manter o índice explicitamente; apenas trocar a classe delegada não seria suficiente.
- A aplicação permanece responsável por habilitar as notificações de keyspace. Um Redis que proíba essa configuração impede o arranque em vez de degradar silenciosamente a limpeza do índice.
- O número da matrícula, e-mail, tokens, papéis e MFA nunca entram no índice. A chave reversa guarda somente o identificador técnico opaco do principal.
- Falha remota não revive sessão local. O chamador recebe um resumo estruturado e poderá auditar ou compensar o grant que permaneceu no provedor.

## Deviations from Plan

### Auto-fixed Issues

**1. O repositório oficial não mantinha o índice pelo caminho de gravação do wrapper**

- **Issue:** `SelectiveTouchSessionRepository` substitui a gravação oficial, então a simples troca para `RedisIndexedSessionRepository` não criaria o índice.
- **Fix:** índice, hash, shadow e chave reversa passaram a ser atualizados na mesma transação Redis.
- **Verification:** duas sessões da identidade alvo foram localizadas e encerradas; uma sessão de outra identidade sobreviveu.

**2. O ciclo oficial de expiração conflitou com o TTL físico de oito horas**

- **Issue:** aplicar o buffer oficial de 300 segundos ao hash completo quebrava o contrato preexistente de expiração física em oito horas.
- **Fix:** hash e shadow expiram no instante de oito horas; somente a chave reversa recebe o buffer de cinco minutos necessário para processar a notificação.
- **Verification:** `RedisSessionIT` e `PrincipalNameIndexProbeIT` passaram no Maven completo.

**3. O listener de expiração estava conectado diretamente ao delegado**

- **Issue:** implementar `MessageListener` no wrapper não o registrava automaticamente no container Spring.
- **Fix:** a configuração remove o listener direto e registra exatamente o wrapper nos tópicos oficiais, delegando mensagens que não pertencem à shadow customizada.
- **Verification:** limpeza natural do índice passou contra Redis real sem publicação duplicada.

**4. A primeira reconciliação abriu Redis durante o bootstrap**

- **Issue:** atraso inicial zero violou `devCreatesRedisSessionRepositoryWithoutOpeningAConnection`.
- **Fix:** o primeiro `SCAN` global foi adiado pelo mesmo intervalo de 15 minutos; a autocorreção sob consulta permaneceu imediata.
- **Verification:** o teste que detectou a regressão passou e a suíte Maven completa terminou verde.

**5. A revisão encontrou bordas de falha e serialização**

- **Issue:** listener podia interromper o loop, exclusões podiam ser engolidas, membros Redis serializados não podiam ser lidos como UTF-8, e uma leitura sem limites criaria risco operacional.
- **Fix:** processamento em duas fases, falhas explícitas, serializers do próprio `RedisOperations`, `SCAN`/`SSCAN` em lotes de 100 e ciclo de executor controlado pelo contexto Spring.
- **Verification:** revisão Codex final sem bloqueios e todas as suítes verdes.

**Total deviations:** 5 correções de segurança, ciclo de vida e compatibilidade, sem dependência nova ou ampliação de produto.

## Issues Encountered

- Uma tentativa de teste falhou antes da JVM por falta de memória causada por um PowerShell órfão de inspeção; somente esse processo foi encerrado e a memória foi recuperada.
- O Docker Desktop estava com o daemon travado. O responsável aprovou seu reinício; nenhuma imagem, volume ou arquivo foi removido.
- Logback reportou configuração depreciada, Mockito avisou sobre carregamento dinâmico de agente e Lettuce tentou reconectar durante o teardown dos containers. Nenhum aviso produziu falha de teste.

## Verification Evidence

- Maven focal: 172 testes Surefire e 23 Failsafe, todos verdes.
- Node focal: 272/272.
- Maven completo: 172/172 Surefire e 149/149 Failsafe; total Java 321/321.
- Node completo: 525/525.
- `git diff --check`: sem erros.
- Nenhum container permaneceu ativo após o Testcontainers.
- Revisão Codex do snapshot final: sem achados bloqueantes.
- Snapshot técnico congelado por SHA-256 nos dez arquivos; `SelectiveTouchSessionRepository.java` terminou em `D4DC4ED7498E32CA8CB6CCA0DE46414C43939AD83553655766A6270F9C69C698`.

## User Setup Required

None — nenhuma dependência, variável ou alteração manual de infraestrutura foi adicionada. O provedor Redis futuro precisa aceitar a configuração de notificações de keyspace; se não aceitar, a aplicação falhará no arranque de forma deliberada.

## Next Phase Readiness

- O serviço está pronto para ser consumido pela alteração de segurança do Plano 02-11 e pela alteração de papéis do Plano 02-23.
- A Wave 3 permanece em andamento: `02-05` e `02-21` ainda precisam de teste, revisão e formalização.
- O plano ainda não possui commit; stage, commit, integração, push e limpeza do worktree exigem aprovações próprias.

## Self-Check: PASSED

- Os dez arquivos técnicos declarados existem.
- As contagens foram extraídas dos XML de Surefire e Failsafe.
- O índice Git permaneceu vazio durante implementação, correções, testes e formalização.
- O HEAD técnico permaneceu em `610747186a82e78165466cde7a4f9d28d5cc6d93`.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-21*
