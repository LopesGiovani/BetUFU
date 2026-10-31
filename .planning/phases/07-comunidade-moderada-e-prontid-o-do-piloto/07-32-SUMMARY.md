---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '32'
wave: 17
status: complete
scope: isolated-local-technical-only
technical_commit: e648d720a244c043b9c5cf75b39eceee428ea7c1
completed: '2026-10-31'
requires: [07-31, 07-30, 07-29]
provides: [source-bound-local-certification, restricted-rules-preference-export, runtime-concurrency-proof]
requirements-completed: [CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, CHAT-06, RT-01, INTE-05, OPS-04, TEST-03, TEST-05, UX-01, UX-02]
requirements-pending: [PRIV-03, PRIV-04, PRIV-05]
public_launch_authorized: false
actuals:
  tasks: 2
  technical_paths: 15
  commits: 2
---

# Plano 07-32 — certificação técnica local

A certificação em `docs/phase7-certification.json` vincula fontes, HEAD base,
comandos, logs e artefatos por SHA-256. Mapeia 16 requisitos, 40 decisões e
35 disposições de proibições com limites locais explícitos. Publicação e piloto
público permanecem bloqueados. Os requisitos concluídos acima têm esse alcance;
não significam prontidão jurídica, infraestrutura de produção ou CI hospedada.

## Entrega e autoridade

A autorização contínua do responsável cobre implementação inline na master,
autocorreção, testes, stage e dois commits locais por plano. O HEAD base é
`e5e1f625ef975a48aa6cc9e5f9f3a8fd852f1641`. Quinze arquivos técnicos foram
congelados e revisados inline; 61 hashes protegidos permaneceram intactos.
Não houve push, worktree, Antigravity, subagentes ou atualização de dependências.

A exportação IDENTITY agora compõe `chatRulesDismissed` por uma porta pública
consumer-owned de privacy. Identity permanece seu único produtor; falta ou
duplicidade do produtor de preferência bloqueia a classe. O HTTP real verifica
false/true por titular, ausência de perfil, outra conta, cache privado, anonimato
e sessão revogada. Não houve alteração de contrato REST ou migration.

Os testes adicionais usam PostgreSQL real e usuário runtime sem superusuário nem
propriedade de tabela para replay concorrente, limiter compartilhado, pausa e
fechamento definitivo. Sanção e revisão preservam um snapshot financeiro não
vazio; UPDATE e DELETE dos fatos históricos falham com SQLSTATE P0001. A troca
de gerenciador transacional existe somente na configuração da fixture de teste.

## Evidências finais

| Verificação | Resultado |
| --- | --- |
| Focal Java literal | 16/16; zero falhas, erros e ignorados |
| Maven verify após clean | 1.141 testes: Surefire 361/37 classes + Failsafe 780/153 classes; zero falhas, erros e ignorados |
| Node agregado | 985/985; zero falhas, cancelados e ignorados |
| Angular | 533/533 em 85 arquivos |
| Gerador, lint, build, Spotless, diff | saída zero; avisos CSS existentes preservados |
| Chrome real integrado | 13/13 desktop + 13/13 em 320 px; stacks independentes e limpeza owned |
| Carga local | 50 usuários, 300,8757162 s, 14.614 requisições, p95 39,1802 ms, 0% erros, zero violações de integridade |
| Palpites na carga | 300 aceitos + 2.600 replays reais; integridade das 50 contas conferida |
| Restore dos três substratos | 1 fato de chat, 3 usuários Keycloak, objetos privados não vazios; supressão reaplicada antes de servir, zero registros suprimidos servidos |
| RPO / RTO medidos | 16,689 s / 12,387 s; limites locais de 3.600 s / 14.400 s respeitados |
| Publicação negativa | saída 1 esperada: INSTITUTIONAL_IDENTITY |
| Certificado final | `--record-local` e `--local` aprovados |

Os 14 comandos finais e seus horários/hashes constam no certificado. Os logs
ficam em `.git/gsd/phase7/07-32-*-proof.log`; o snapshot de revisão é
`.git/gsd/phase7/07-32-frozen.json`. XMLs frescos são a fonte das contagens Maven;
o resumo duplicado do wrapper não foi usado como total.

## Autocorreções e limites

- O red fresco da exportação demonstrou a ausência da preferência antes da
  composição. Configuração transacional e Accept foram corrigidos nas fixtures;
  bytecode antigo foi removido por clean antes das provas finais.
- O primeiro restore recusou o registro produzido pela prova isolada anterior.
  Registro e relatório foram conferidos entre si e arquivados; somente a entrada
  descartável foi reinicializada. A guarda contra registro operacional não
  verificado permaneceu intacta. Nenhuma supressão do responsável foi removida.
- O primeiro certificado recusou backups com política `proposed`. O validador
  confundia proposta com autorização de descarte. A correção exige autoridade
  pendente e nenhuma disposição de expurgo; 25 testes focais incluem três
  tentativas de autorização indevida. Toda a sequência foi repetida com o novo
  hash de fontes; as provas anteriores foram arquivadas.
- Uma execução E2E encontrou ERR_ABORTED quando o teste navegou após HTTP 200
  de logout, concorrendo com o redirecionamento OIDC do shell. Uma repetição
  completa sem mudanças passou 26/26. A falha permanece em
  `07-32-browser-navigation-abort.log`; essa fragilidade de sincronização do
  teste é uma limitação conhecida, sem asserção removida ou timeout ampliado.

O scan de dependências do 07-30 permanece dentro de 24 horas e vinculado aos
mesmos manifests: 239 versões Maven e 623 npm, sem achados confirmados. Não
substitui pentest da aplicação. RepoWise estava indisponível; a revisão foi
inline, sem parecer externo ou independente.

V1–V47, fatos append-only, documentos do responsável, ausência deliberada de
`docs/manual-testing.md`, seeds e volumes do responsável foram preservados.
Retenção ratificada: 90 dias para mensagens comuns e 180 dias após encerramento
para evidências, com holds; demais expurgos bloqueados e nenhum fato apagado.

A Fase 7 encerra 35/35 planos locais e o acompanhamento global chega a 170/170.
PRIV-03/04/05 continuam pendentes em seu alcance integral. Identidade institucional,
domínio/contato, revisão jurídica independente, políticas restantes e operação
de produção exigem resolução antes de publicação ou piloto público.
