---
phase: 07
slug: comunidade-moderada-e-prontid-o-do-piloto
status: passed-local
scope: isolated-local-technical-only
nyquist_compliant: true
wave_0_complete: true
public_launch_authorized: false
created: 2026-10-04
updated: 2026-10-31
technical_commit: e648d720a244c043b9c5cf75b39eceee428ea7c1
---

# Fase 7 — validação técnica local executada

Este documento substitui a estratégia draft por evidências executadas. O escopo
local foi expressamente ratificado: 35 planos, 17 ondas e 102 tasks planejadas.
nyquist_compliant refere-se à cobertura de verificação local; não aprova políticas
jurídicas nem prontidão de produção. Publicação e piloto público bloqueados.

## Evidência autoritativa

- Certificado: [phase7-certification.json](../../../docs/phase7-certification.json),
  status passed-local, HEAD base e5e1f625ef975a48aa6cc9e5f9f3a8fd852f1641.
- Fonte SHA-256: 8218776f15354188b65129f03c79e393c0b0eaf8bac2d05e39eb9f3bc362dbb7.
- Snapshot: .git/gsd/phase7/07-32-frozen.json; 15 arquivos técnicos e 61 hashes
  protegidos. Revisão inline somente leitura, sem parecer externo independente;
  RepoWise indisponível.
- Maven: 361 testes Surefire/37 classes + 780 Failsafe/153 classes = 1.141 XML
  frescos; zero falhas, erros e ignorados. Node 985/985; Angular 533/533 em 85 arquivos.
- Chrome real: 13/13 desktop e 13/13 em 320 px; OIDC/API/STOMP e dados reais,
  stacks separadas e limpeza restrita aos recursos owned.
- Carga: 50 usuários, 300,8757162 s, 14.614 requisições, p95 de 39,1802 ms,
  0% de erros, zero violações; 300 palpites aceitos + 2.600 replays, 50 contas conferidas.
- Restore: BETUFU, Keycloak e objetos privados não vazios; supressão posterior
  ao snapshot reaplicada antes da leitura, zero conteúdo suprimido servido.
  RPO de 16,689 s <= 3.600 s; RTO de 12,387 s <= 14.400 s.
- Scan vigente e vinculado aos manifests: 239 versões Maven + 623 npm, zero
  achados confirmados. Não equivale a pentest da aplicação.

## Comandos finais e tempos observados

Execução sequencial, sem concorrência entre Maven/Testcontainers, E2E, carga
e restore. O executor recusou alteração de fonte e registrou início, fim, saída
e SHA-256 de cada log UTF-8. Os logs ficam em .git/gsd/phase7/07-32-*-proof.log.

| Etapa | Comando literal | Saída | Duração |
| --- | --- | --- | --- |
| focal | `node scripts/maven.mjs -f backend/pom.xml -Dtest=Phase7ConcurrencyIT,Phase7PrivacyIT,Phase7CertificationIT,ModulithArchitectureTests test` | 0 | 47.32 s |
| clean | `node scripts/maven.mjs -f backend/pom.xml clean` | 0 | 2.64 s |
| maven | `node scripts/maven.mjs -f backend/pom.xml verify` | 0 | 1042.10 s |
| node | `node --test scripts/*.test.mjs` | 0 | 254.77 s |
| angular | `npm --prefix frontend test -- --watch=false` | 0 | 102.24 s |
| generator | `npm --prefix frontend run generate:api:check` | 0 | 3.75 s |
| lint | `npm --prefix frontend run lint` | 0 | 10.35 s |
| build | `npm --prefix frontend run build:production` | 0 | 9.54 s |
| spotless | `node scripts/maven.mjs -f backend/pom.xml spotless:check` | 0 | 2.89 s |
| browser | `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-integrated.spec.ts` | 0 | 449.09 s |
| load | `node scripts/phase7-load.mjs --profile infra/load/phase7-load-profile.json --isolated` | 0 | 451.32 s |
| restore | `node scripts/phase7-restore.mjs --policy infra/backup/phase7-backup-policy.json --isolated` | 0 | 115.95 s |
| publication | `node scripts/phase7-quality.mjs --publication` | 1 (recusa esperada) | 0.08 s |
| diff | `git diff --check` | 0 | 0.09 s |

Publicação tem saída 1 obrigatória por INSTITUTIONAL_IDENTITY; saída 0 seria
falha da prova negativa. --record-local e --local passaram após a sequência.

## Mapa dos 16 requisitos

| Requisito | Evidência no certificado | Disposição |
| --- | --- | --- |
| CHAT-01 | maven, browser | Aprovado no alcance local indicado |
| CHAT-02 | maven, browser | Aprovado no alcance local indicado |
| CHAT-03 | maven, browser | Aprovado no alcance local indicado |
| CHAT-04 | maven, browser | Aprovado no alcance local indicado |
| CHAT-05 | maven, browser | Aprovado no alcance local indicado |
| CHAT-06 | maven, browser | Aprovado no alcance local indicado |
| RT-01 | maven, browser | Aprovado no alcance local indicado |
| INTE-05 | maven, browser | Aprovado no alcance local indicado |
| PRIV-03 | node, publication | Parcial; lançamento bloqueado |
| PRIV-04 | publication | Parcial; lançamento bloqueado |
| PRIV-05 | maven, browser, publication | Parcial; lançamento bloqueado |
| OPS-04 | restore | Aprovado no alcance local indicado |
| TEST-03 | maven, node, angular, generator, browser | Aprovado no alcance local indicado |
| TEST-05 | maven, node, generator, spotless | Aprovado no alcance local indicado |
| UX-01 | browser | Aprovado no alcance local indicado |
| UX-02 | angular, browser | Aprovado no alcance local indicado |

OPS-04 cobre automação e recuperação isoladas; operação, destino e chaves de
produção pendentes. TEST-05 cobre pipeline local e definição do workflow, não
execução hospedada. UX cobre jornadas críticas em Chrome desktop/320px, teclado
e estados; não toda matriz de navegadores ou WCAG integral. TEST-04 conserva
as provas da Fase 6 e recebe o perfil local de 50 usuários ratificado e medido.

## Evidências por plano

Cada SUMMARY registra autoridade, entrega, focais, agregados, desvios e limites.
Os 35 arquivos estão presentes; os checkpoints históricos não são substituídos
por fixtures nem autorizam publicação pelo simples fechamento local.

[07-01](07-01-SUMMARY.md), [07-02](07-02-SUMMARY.md), [07-03](07-03-SUMMARY.md), [07-04](07-04-SUMMARY.md), [07-05](07-05-SUMMARY.md), [07-06](07-06-SUMMARY.md), [07-07](07-07-SUMMARY.md), [07-08](07-08-SUMMARY.md), [07-09](07-09-SUMMARY.md), [07-10](07-10-SUMMARY.md), [07-11](07-11-SUMMARY.md), [07-12](07-12-SUMMARY.md), [07-13](07-13-SUMMARY.md), [07-14](07-14-SUMMARY.md), [07-15](07-15-SUMMARY.md), [07-16](07-16-SUMMARY.md), [07-17](07-17-SUMMARY.md), [07-18](07-18-SUMMARY.md), [07-19](07-19-SUMMARY.md), [07-20](07-20-SUMMARY.md), [07-21](07-21-SUMMARY.md), [07-22](07-22-SUMMARY.md), [07-23](07-23-SUMMARY.md), [07-24](07-24-SUMMARY.md), [07-25](07-25-SUMMARY.md), [07-26](07-26-SUMMARY.md), [07-27](07-27-SUMMARY.md), [07-28](07-28-SUMMARY.md), [07-29](07-29-SUMMARY.md), [07-30](07-30-SUMMARY.md), [07-31](07-31-SUMMARY.md), [07-32](07-32-SUMMARY.md), [07-33](07-33-SUMMARY.md), [07-34](07-34-SUMMARY.md), [07-35](07-35-SUMMARY.md).

## Provas discriminantes e autocorreções

A exportação privada falhou antes da composição de chatRulesDismissed. HTTP real
agora verifica contas diferentes, preferência false/true, ausência de perfil,
cache e sessão revogada. Corridas usam PostgreSQL com usuário runtime não
proprietário e NOSUPERUSER. Sanção/revisão preservam snapshot financeiro não
vazio; triggers recusam UPDATE/DELETE com SQLSTATE P0001.

O validador possui 25 testes focais e recusa fontes, HEAD, gates ou provas
faltantes/vencidas, testes ignorados e restauração incompleta. O defeito que
confundia backups proposed com autorização foi corrigido com a política real e
três controles negativos de expurgo; toda a sequência foi repetida com a fonte final.

Restore recusou o registro da prova isolada anterior. Após conferir sua
correspondência ao relatório isolado, ambos foram arquivados e uma entrada
descartável nova foi preparada. A guarda permaneceu intacta; nenhum registro
operacional do responsável foi removido.

Um E2E encontrou ERR_ABORTED ao disputar navegação com o redirecionamento OIDC
após logout 200. Repetição completa sem alterações passou 26/26; falha retida em
.git/gsd/phase7/07-32-browser-navigation-abort.log. A fragilidade de sincronização
do teste permanece documentada; asserções e timeouts não mudaram.

## Proibições e fronteiras preservadas

As 35 disposições são revisões locais vinculadas aos hashes dos PLANs. As
proibições recorrentes possuem estas provas: ChatWriteIT/ChatPushSecurityIT e
jornadas reais negam escrita sem autorização; Phase7CertificationIT/ChatSanctionIT
preservam fatos financeiros; publicação recusada mantém o gate institucional.
O status histórico unresolved dos PLANs não se transforma em autorização pública.

V1–V47, append-only, portas públicas consumer-owned, contrato/cliente gerado,
documentos do responsável e ausência deliberada de manual-testing.md verificados.
Seeds e volumes do responsável preservados. Sem push, worktrees, Antigravity,
subagentes ou dependências novas neste plano. GSD interativo/auto_advance=false.

## Gates externos não concluídos

- PRIV-03: publicação e checklist de todos os artefatos/histórico públicos não
  concluídos; provada a recusa local, sem publicação autorizada.
- PRIV-04: identidade legítima, domínio, e-mail e responsável pela infraestrutura
  pendentes. Nenhum vínculo oficial com a UFU é presumido.
- PRIV-05: revisão jurídica independente e demais políticas por classe pendentes,
  embora direitos, exportação e retenção de 90/180 dias tenham prova local.
- Demais expurgos bloqueados; holds, revisões e casos preservados, nenhum fato
  histórico apagado. Produção, provedor, chaves e disponibilidade não certificados.

Parte técnica local aprovada. A próxima etapa é revisão pelo responsável e
resolução específica dos gates externos, sem implantação ou publicação implícita.
