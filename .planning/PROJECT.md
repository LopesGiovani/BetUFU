# BETUFU

## What This Is

A BETUFU é uma aplicação web responsiva e independente, sem vínculo oficial com a Universidade Federal de Uberlândia, para palpites esportivos com créditos exclusivamente virtuais. O produto transforma os jogos das Olimpíadas UFU em uma experiência social: estudantes e demais interessados acompanham partidas, torcem por equipes, conversam e disputam rankings, enquanto as apostas nos eventos internos ficam reservadas a estudantes com vínculo verificado.

O primeiro lançamento será operado por administradores, que cadastram competições, modalidades, equipes, atletas, partidas, mercados, resultados e revisam as odds sugeridas pelo sistema. A arquitetura deve permitir novas modalidades sem reconstruir o núcleo do produto.

## Core Value

Tornar os jogos universitários da UFU mais envolventes por meio de uma competição de palpites social, justa e sem dinheiro real.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Oferecer cadastro aberto, autenticação e perfis públicos com apelido e equipe favorita.
- [ ] Permitir verificação opcional do vínculo com a UFU por comprovante de matrícula revisado manualmente.
- [ ] Excluir o comprovante após a revisão e manter somente os dados mínimos necessários para o status, a expiração e a prevenção de duplicidade.
- [ ] Restringir apostas em jogos internos da UFU e o ranking universitário a contas verificadas, mantendo a navegação pública aberta.
- [ ] Modelar competições, edições, modalidades, categorias, equipes, atletas, fases e partidas de forma extensível.
- [ ] Usar uma edição das Olimpíadas UFU como piloto e ativar as modalidades de confronto direto que tenham tabelas e resultados confiáveis.
- [ ] Disponibilizar páginas de equipes com elenco, modalidade, partidas, resultados e desempenho recente.
- [ ] Permitir que administradores cadastrem e publiquem partidas, mercados, odds e resultados.
- [ ] Calcular odds sugeridas a partir do histórico, resultados recentes e força dos adversários, com revisão administrativa antes da publicação.
- [ ] Exibir o histórico de mudanças das odds e preservar, em cada aposta, a odd aceita no momento do registro.
- [ ] Distribuir e controlar créditos virtuais sem compra, saque, prêmio financeiro, transferência ou conversão em dinheiro.
- [ ] Oferecer apostas simples antes da partida, com bilhete, valor apostado, retorno potencial, histórico e liquidação do saldo.
- [ ] Bloquear novas apostas antes do início da partida e manter palpites de outros usuários ocultos até o fechamento.
- [ ] Disponibilizar ranking geral, ranking exclusivo de estudantes verificados e disputas privadas entre amigos.
- [ ] Disponibilizar chats por partida, com denúncia, silenciamento e moderação administrativa.
- [ ] Oferecer conquistas por sequências de acertos, palpites em azarões, desempenho em rodadas e apoio a uma equipe.
- [ ] Impedir atletas de apostar em partidas da própria equipe e administradores de apostar em eventos que administram.
- [ ] Manter trilha de auditoria para mudanças de odds, resultados, verificações e ações administrativas relevantes.
- [ ] Funcionar bem em navegadores de celular e desktop.

### Out of Scope

- Dinheiro real, depósitos, saques ou conversão de créditos — o MVP é exclusivamente recreativo.
- Compra, venda ou transferência de créditos e prêmios financeiros — evitam criar valor monetário ou mercado paralelo.
- Apostas ao vivo e apostas múltiplas — aumentam muito a complexidade e não são necessárias para validar o núcleo.
- Notificações de partidas, liquidações, chats e rankings — planejadas para depois do MVP.
- Janela formal de contestação de resultados — planejada para depois do MVP.
- Mercados complexos para provas com vários competidores, como atletismo e natação — serão ativados após validar confrontos diretos.
- Aplicativos nativos para Android e iOS — a primeira versão será uma aplicação web responsiva.
- Vínculo, endosso ou representação oficial da UFU — a BETUFU deve declarar claramente sua independência.

## Context

- O público principal são universitários da UFU que acompanham e torcem por equipes e atléticas nos jogos universitários.
- Contas não verificadas podem acessar a experiência pública; a verificação libera apostas nos jogos internos e o ranking UFU.
- A matrícula será opcional e nunca exibida publicamente. Um comprovante poderá ser enviado como alternativa de verificação, com revisão por administrador e exclusão posterior do arquivo.
- Cada matrícula poderá validar somente uma conta, e a verificação deverá expirar periodicamente, por exemplo a cada semestre.
- O piloto será uma edição específica das Olimpíadas UFU. A grade exata dependerá das tabelas oficiais disponíveis.
- Modalidades coletivas e confrontos individuais compartilham um modelo de evento direto. Provas com múltiplos competidores precisarão de mercados e cálculo de probabilidades próprios.
- As odds automáticas devem funcionar como recomendação transparente ao administrador, especialmente enquanto houver pouco histórico. Eventos sem dados suficientes precisam de uma estimativa inicial neutra ou manual.
- A operação inicial será deliberadamente assistida: administradores revisam matrículas, publicam jogos e odds, informam resultados e moderam chats.
- O engajamento vem de torcer pelas equipes, comparar conhecimento esportivo, competir com amigos e conquistar posições e distintivos — não de recompensas financeiras.

## Constraints

- **Economia virtual**: créditos não possuem valor monetário e não podem ser comprados, vendidos, transferidos ou sacados — limite essencial do MVP.
- **Independência institucional**: a plataforma não representa a UFU — textos, identidade e uso de marcas devem evitar sugerir vínculo oficial.
- **Privacidade**: matrícula e comprovante são dados identificadores — coletar o mínimo, restringir acesso, proteger o armazenamento temporário e eliminar documentos após a análise.
- **Integridade**: uma conta por matrícula, bloqueio de conflitos de interesse, fechamento pré-jogo e auditoria — necessários para que rankings e conquistas sejam confiáveis.
- **Operação inicial**: verificações, tabelas, odds e resultados dependem de administradores — os fluxos precisam ser rápidos e rastreáveis.
- **Plataforma**: web responsiva primeiro — atender celulares e desktops sem manter aplicativos nativos no MVP.
- **Fonte de dados**: calendários, elencos e resultados dependem de publicações e cadastros confiáveis — não há integração oficial assumida.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Começar exclusivamente com créditos virtuais | Validar diversão, retenção e integridade sem movimentação financeira | — Pending |
| Manter cadastro aberto, mas restringir apostas UFU a estudantes verificados | Permitir descoberta pública e reduzir participação indevida nos eventos internos | — Pending |
| Usar comprovante com revisão manual e exclusão posterior | Oferecer verificação quando não houver integração institucional disponível | — Pending |
| Usar uma edição das Olimpíadas UFU como piloto | Concentrar calendário, equipes e comunidade em um evento observável | — Pending |
| Construir um modelo extensível de modalidades | Permitir expansão sem reescrever o núcleo de apostas e eventos | — Pending |
| Começar por confrontos diretos e adiar mercados multicompetidor | Reduzir complexidade do cálculo de odds e da liquidação | — Pending |
| Calcular odds automaticamente, com revisão administrativa | Combinar consistência estatística com controle em cenários de pouco histórico | — Pending |
| Tornar o histórico das odds transparente | Aumentar confiança e preservar a condição aceita em cada aposta | — Pending |
| Incluir páginas de equipes, chats, rankings e conquistas no MVP | Fazer da BETUFU uma experiência social universitária, não apenas um simulador de apostas | — Pending |
| Adiar notificações e contestação formal de resultados | Manter o primeiro lançamento concentrado no ciclo principal | — Pending |
| Lançar como aplicação web responsiva | Entregar boa experiência móvel com menor custo operacional inicial | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-08-01 after initialization*
