---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_phase: 1
current_phase_name: Fundação Segura da Plataforma
status: In progress
stopped_at: F1/P04 concluído e documentado; F1/P19 aguarda autorização explícita
last_updated: "2026-08-11T13:44:40.1269493Z"
last_activity: 2026-08-11
last_activity_desc: Plano 01-04 implementado, validado e enviado pelos commits 32abc70, ab7923b e 9686883; fechamento documental preparado para revisão.
progress:
  total_phases: 1
  completed_phases: 0
  total_plans: 28
  completed_plans: 6
---

# Estado do Projeto

## Project Reference

Consulte: .planning/PROJECT.md (atualizado em 2026-08-01)

**Valor central:** Tornar os jogos universitários da UFU mais envolventes por meio de uma competição social de palpites, justa, transparente e sem dinheiro real.
**Current focus:** Revisar o fechamento de F1/P04 e decidir conscientemente se o Plano 01-19 deve ser iniciado

## Current Position

Phase: 1 of 7 (Fundação Segura da Plataforma)
Plan: 6 of 28
Status: In progress — F1/P04 concluído e documentado; Onda 7 ainda não autorizada
Last activity: 2026-08-11 — commits `32abc70`, `ab7923b` e `9686883` entregaram o workspace Angular, a Matriz B e as provas de build/teste.

GitHub: milestone `Fase 1 — Fundação Segura da Plataforma`; issues `#1` a `#5` concluídas, issue `#6` aberta até a publicação deste fechamento e issue de acompanhamento `#29` aberta.
Git: branch principal `master`; a implementação do Plano 01-04 está nos commits `32abc70`, `ab7923b` e `9686883`, sincronizados com `origin/master`; este fechamento documental aguarda stage e commit separados.

Progress: [██░░░░░░░░] 21%

## Métricas de desempenho

**Velocidade:**

- Total de planos concluídos: 6
- Duração média: não mensurada — os planos foram acompanhados em múltiplas interações
- Tempo total de execução: não mensurado

**Por fase:**

| Fase | Planos | Total | Média/plano |
|------|--------|-------|-------------|
| 1 | 6 | não mensurado | não mensurada |

**Tendência recente:**

- Últimos 5 planos: 01-02, 01-17, 01-18, 01-03, 01-04
- Tendência: ainda não estabelecida sem duração contínua mensurada

## Contexto acumulado

### Decisões

- O build backend permanece estritamente em Java 25, Maven 3.9.16, Spring Boot 4.1.0 e Spring Modulith 2.1.0; falhas não autorizam downgrade silencioso.
- Spring Session Redis usa o starter oficial do Boot, com Lettuce transitivo, e toda a família ArchUnit permanece alinhada em 1.4.2.
- Spring atua como BFF OIDC: o Spring Security gerencia `state`, `nonce` e PKCE, enquanto tokens e material de autenticação permanecem no servidor.
- A configuração runtime usa somente variáveis externas; o smoke substitui o issuer por um `ClientRegistration` confidencial em memória e não acessa serviços externos.
- Os profiles `dev`, `homolog` e `prod` ativam automaticamente `runtime`; `integrated` permanece diretamente ativável para testes integrados.
- Origens usam o contrato `betufu.web.*`, forwarded headers dependem de proxies explicitamente confiáveis e o diagnóstico não possui feature flag permissiva.
- O ambiente local usa PostgreSQL, Redis, Keycloak, SeaweedFS e Mailpit por referências OCI fixadas, binds de loopback e volumes nomeados persistentes.
- SeaweedFS permanece somente a implementação S3 local; o backend usa `BETUFU_S3_*` e nenhuma solução de homologação/produção foi selecionada.
- Keycloak mantém somente o papel técnico `TECHNICAL`; o tema `betufu` estende `keycloak.v2` apenas por footer/CSS e usa fontes locais aprovadas por hash.
- O workspace frontend usa a Matriz B com 14 pins diretos exatos, npm 10.9.8 e lockfile v3 governado pelo SHA-256 `FB8FE8A18EA4F72927AF5E3B506E2112F968C3BF98267DA0902EBC5BC40EA841`.
- O bootstrap Angular é standalone, zoneless, strict e sem routing/SSR; development, homolog e production compilam com as configurações e budgets aprovados.
- A allowlist de cinco pacotes `hasInstallScript` reconhece o grafo, mas não autoriza scripts; `npm ci --ignore-scripts` foi preservado e o risco moderado `GHSA-frvp-7c67-39w9` foi aceito temporariamente.
- Decisões de produto ainda em aberto continuam sem resolução implícita; a tabela Key Decisions de PROJECT.md permanece a fonte consolidada.

### Pendências

- Revisar o fechamento documental do Plano 01-04; após commit/push, atualizar a issue `#6`/checklist `#29` e decidir se a execução do Plano 01-19 deve ser autorizada.
- Reavaliar `GHSA-frvp-7c67-39w9` quando houver correção upstream compatível com a Matriz B, sem `npm audit fix`, `--force` ou downgrade silencioso.
- O provedor e a topologia S3 de homologação/produção continuam sem escolha; SeaweedFS não deve ser promovido silenciosamente além do ambiente local.
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

Last session: 2026-08-11T13:44:40.1269493Z
Stopped at: F1/P04 concluído e documentado; F1/P19 aguarda autorização explícita
Resume file: .planning/phases/01-funda-o-segura-da-plataforma/01-19-PLAN.md
