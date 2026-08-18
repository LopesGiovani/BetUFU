# Plano 01-13 — Modelar a projeção diagnóstica e seus probes tolerantes a falha

- **Fase:** 01-funda-o-segura-da-plataforma
- **Wave:** 22
- **Dependência:** 01-25
- **Status:** Concluído com sucesso (após incorporação de todos os achados da auditoria externa nas Rodadas 1 e 2)

---

## 1. O que foi entregue

Este plano entregou a **projeção pura de dados** do diagnóstico da plataforma BETUFU (DTOs, serviço de agregação e probes tolerantes a falha parcial), atendendo rigorosamente aos requisitos **OPS-03**, **D-03** e **D-04**:

1. **Estados Allowlisted (`DiagnosticStatus`):**
   - Enumeração restrita de três estados: `AVAILABLE`, `DEGRADED`, `UNAVAILABLE`.
2. **Contrato de Componente (`DiagnosticComponent`):**
   - Record imutável que valida os nomes contra os exatamente seis componentes permitidos:
     - `API` (Auto-reporte operacional da aplicação; se a requisição diagnóstica é atendida, a API está operacional)
     - `Sessão BFF` (Acoplada à vitalidade do Redis)
     - `Keycloak`
     - `PostgreSQL`
     - `Redis`
     - `Armazenamento de objetos`
3. **Projeção Agregada (`DiagnosticResponse`):**
   - Record imutável contendo `status`, `summary`, `correlationId`, `timestamp`, `components` (exatamente 6 itens ordenados), `environment` e `version`.
4. **Serviço de Agregação Seguro (`DiagnosticService`):**
   - Garante a ordenação fixa estrita per D-03.
   - Computa o status geral em 3 estados reais baseados na **regra semântica de severidade da BETUFU**:
     - `AVAILABLE`: todos os 6 componentes saudáveis.
     - `UNAVAILABLE`: se o **PostgreSQL** (fonte autoritativa de dados, 01-10) estiver indisponível, ou se **Keycloak** (autoridade de identidade) estiver indisponível, ou se 2 ou mais dependências externas estiverem indisponíveis simultaneamente (política conservadora para múltiplos serviços auxiliares fora do ar).
     - `DEGRADED`: falhas parciais em componentes auxiliares (como Redis isolado ou Armazenamento de objetos) sem paralisação total da plataforma.
   - Reusa o `correlationId` do MDC (instalado pelo `CorrelationIdFilter` do Plano 01-12) com fallback transparente `untracked-<uuid>` quando chamado fora do ciclo HTTP.
   - Sanitiza resumos públicos e formata `environment` ordenado e determinístico.
5. **Sondagem Concorrente Tolerante a Falha (`PlatformDependencyProbes`):**
   - Execução concorrente de probes via Virtual Threads sob um **deadline global único de 1.5s** (não cumulativo).
   - Cancelamento imediato de tarefas pendentes (`future.cancel(true)`) ao atingir o deadline.
   - Distinção precisa no fallback entre timeout real e falha de execução.
   - Encerramento limpo do `ExecutorService` via `@PreDestroy` / `AutoCloseable`.
   - Derivação do estado da `Sessão BFF` a partir do probe de Redis (suporte auxiliar de sessão).
   - Reúso por composição com os probes existentes de `platform.persistence` (`PostgresAuthorityProbe`, `RedisAuxiliaryProbe`, `PrivateObjectStorageProbe`).
   - Logging sanitizado (`LogSanitizer`) e descrições públicas genéricas sem vazar topologia ou credenciais.

---

## 2. Reconciliação e Ratificação Formal da Allowlist

- **Pacote `br.com.betufu.platform.diagnostic`:** O plano declarava originalmente `br/com/betufu/diagnostic/`. A capacidade foi realocada sob o módulo técnico `platform` para preservar a contagem estrutural de 11 módulos (10 de negócio da ARCH-02 + `platform`) do `ModulithArchitectureTests` e a ausência de ciclos do ArchUnit (`NO_MODULE_CYCLES`), com aprovação do responsável e ratificação do revisor.
- **Inclusão de Artefatos de Planejamento:** Modificações em `.planning/STATE.md` e `.planning/ROADMAP.md` incluídas no escopo de entrega para refletir o avanço da Fase 1 e o encerramento da Wave 22.

---

## 3. Evidências de Testes e Discriminação

### Testes Automatizados
- **Unitários (Surefire):** `DiagnosticServiceTest` (9 testes verdes).
- **Integração (Failsafe):** `DiagnosticDegradationIT` (3 testes verdes).
- **Agregado Java:** 26 classes / 170 testes verdes (Surefire: 66 / Failsafe: 104, 0 falhas, 0 erros, 0 ignorados).
- **Frontend (Node/Vitest):** 3 suítes / 8 testes verdes.

### Provas de Discriminação por Mutação
1. **M-13A (Ordenação Canônica):** Bypass da reordenação forçada no `DiagnosticService` → **VERMELHO** (`unorderedInputIsSortedToCanonicalOrder` falhou).
2. **M-13C (Cálculo de Pior Estado):** Retorno forçado de `AVAILABLE` no `computeOverallStatus` → **VERMELHO** (`partialAuxiliaryFailureReturnsDegradedOverallStatus` falhou).
3. **M-13D (Ausência de Topologia/Segredos):** Vazamento de mensagem interna da exceção com IP/porta/senha no `probePostgresql` → **VERMELHO** (`postgresFailureProducesUnavailableResponsePreservingOtherComponents` falhou).
4. **M-13E (Tolerância a Falha Parcial):** Relançamento de exceção de probe a partir do método `probeAll()` do agregador interrompendo a coleta → **VERMELHO** (`DiagnosticDegradationIT` falhou com erro de agregação).
5. **M-13F (Status Agregado UNAVAILABLE):** Desativação do branch `UNAVAILABLE` no `computeOverallStatus` → **VERMELHO** (`postgresUnavailableMakesPlatformUnavailable` e `multipleFailuresReturnUnavailableOverallStatus` falharam).

---

## 4. Arquivos Entregues

- `backend/src/main/java/br/com/betufu/platform/diagnostic/DependencyProbe.java`
- `backend/src/main/java/br/com/betufu/platform/diagnostic/DiagnosticComponent.java`
- `backend/src/main/java/br/com/betufu/platform/diagnostic/DiagnosticResponse.java`
- `backend/src/main/java/br/com/betufu/platform/diagnostic/DiagnosticService.java`
- `backend/src/main/java/br/com/betufu/platform/diagnostic/DiagnosticStatus.java`
- `backend/src/main/java/br/com/betufu/platform/diagnostic/PlatformDependencyProbes.java`
- `backend/src/test/java/br/com/betufu/platform/diagnostic/DiagnosticDegradationIT.java`
- `backend/src/test/java/br/com/betufu/platform/diagnostic/DiagnosticServiceTest.java`
