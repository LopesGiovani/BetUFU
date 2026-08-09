---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_phase: 1
current_phase_name: Fundação Segura da Plataforma
status: In progress
stopped_at: F1/P18 concluído e documentado; F1/P03 aguarda autorização explícita
last_updated: "2026-08-09T14:38:21.882Z"
last_activity: 2026-08-09
last_activity_desc: Plano 01-18 implementado e testado pelo commit 59b0f1f; fechamento documental preparado para revisão.
progress:
  total_phases: 1
  completed_phases: 0
  total_plans: 28
  completed_plans: 4
---

# Estado do Projeto

## Project Reference

Consulte: .planning/PROJECT.md (atualizado em 2026-08-01)

**Valor central:** Tornar os jogos universitários da UFU mais envolventes por meio de uma competição social de palpites, justa, transparente e sem dinheiro real.
**Current focus:** Revisar o fechamento de F1/P18 e decidir conscientemente se o Plano 01-03 deve ser iniciado

## Current Position

Phase: 1 of 7 (Fundação Segura da Plataforma)
Plan: 4 of 28
Status: In progress — F1/P18 concluído e documentado; Onda 5 ainda não autorizada
Last activity: 2026-08-09 — commit `59b0f1f` separou os profiles, fixou Java 25 e adicionou testes isolados de configuração.

GitHub: milestone `Fase 1 — Fundação Segura da Plataforma` com 28 issues executáveis abertas e a issue de acompanhamento `#29`.
Git: branch principal `master`; a implementação do Plano 01-18 está no commit `59b0f1f` e o fechamento documental aguarda stage e commit separados.

Progress: [█░░░░░░░░░] 14%

## Métricas de desempenho

**Velocidade:**

- Total de planos concluídos: 4
- Duração média: não mensurada — os planos foram acompanhados em múltiplas interações
- Tempo total de execução: não mensurado

**Por fase:**

| Fase | Planos | Total | Média/plano |
|------|--------|-------|-------------|
| 1 | 4 | não mensurado | não mensurada |

**Tendência recente:**

- Últimos 5 planos: 01-01, 01-02, 01-17, 01-18
- Tendência: ainda não estabelecida sem duração contínua mensurada

## Contexto acumulado

### Decisões

- O build backend permanece estritamente em Java 25, Maven 3.9.16, Spring Boot 4.1.0 e Spring Modulith 2.1.0; falhas não autorizam downgrade silencioso.
- Spring Session Redis usa o starter oficial do Boot, com Lettuce transitivo, e toda a família ArchUnit permanece alinhada em 1.4.2.
- Spring atua como BFF OIDC: o Spring Security gerencia `state`, `nonce` e PKCE, enquanto tokens e material de autenticação permanecem no servidor.
- A configuração runtime usa somente variáveis externas; o smoke substitui o issuer por um `ClientRegistration` confidencial em memória e não acessa serviços externos.
- Os profiles `dev`, `homolog` e `prod` ativam automaticamente `runtime`; `integrated` permanece diretamente ativável para testes integrados.
- Origens usam o contrato `betufu.web.*`, forwarded headers dependem de proxies explicitamente confiáveis e o diagnóstico não possui feature flag permissiva.
- Decisões de produto ainda em aberto continuam sem resolução implícita; a tabela Key Decisions de PROJECT.md permanece a fonte consolidada.

### Pendências

- Revisar o próximo bloco e decidir se a execução do Plano 01-03 deve ser autorizada.
- PostgreSQL, Keycloak, Redis, MinIO e e-mail reais permanecem no Plano 01-03 e não foram entregues pelo Plano 01-18.
- Homologação e produção ainda precisarão fornecer origens e uma lista não vazia de proxies durante os planos de infraestrutura e implantação.

### Bloqueios e pontos de atenção

- Antes do planejamento da Phase 2: validade da verificação, ciclo de vida da matrícula/HMAC e política para matrícula reemitida precisam de decisão explícita ou configuração.
- Antes do planejamento da Phase 3: esportes e categorias exatos do piloto e tabelas de fontes confiáveis precisam ser confirmados.
- Antes do planejamento das Phases 4–6: políticas em aberto sobre odds, créditos, correções de liquidação e ranking com verificação expirada precisam ser decididas quando os requisitos correspondentes exigirem.
- Antes do lançamento público: escolha de nuvem/CI e fluxos legais, de privacidade, retenção e direitos dos titulares precisam ser encerrados.
- Antes da divulgação pública: decidir se o repositório será transferido para uma organização própria da BETUFU; essa mudança não é necessária para executar o MVP e não oferece anonimato absoluto.

## Itens adiados

| Categoria | Item | Status | Adiado em |
|-----------|------|--------|-----------|
| v2 | Notificações, janela formal de contestação, esportes ampliados, aplicativos nativos e MFA/passkeys para usuários | Adiado no roadmap do MVP | 2026-08-01 |

## Session Continuity

Last session: 2026-08-09T14:38:21.863Z
Stopped at: F1/P18 concluído e documentado; F1/P03 aguarda autorização explícita
Resume file: .planning/phases/01-funda-o-segura-da-plataforma/01-03-PLAN.md
