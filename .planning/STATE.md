---
gsd_state_version: 1.0
milestone: v1.0
current_phase: 3
current_phase_name: Experiência Esportiva do Piloto
status: planning
stopped_at: Phase 3 plan 03-25 complete; awaiting next plan checkpoint
last_updated: "2026-09-04T00:23:19-03:00"
last_activity: 2026-09-04
last_activity_desc: Plan 03-25 certified and documented locally; 03-26 is the next blocking checkpoint.
state_head: b07ac9fad668bc57269462bcb24eab8abec60358
progress:
  total_phases: 8
  completed_phases: 3
  total_plans: 91
  completed_plans: 89
milestone_name: milestone
---

# Estado do Projeto

Git - Fase 3 / gap closure (03-24, concluido localmente, 2026-09-03): o 03-24 foi certificado na worktree `worktree-agent-03-gap-exec` sobre `8a85cec`. O commit tecnico `a477296` entrega o contrato CSV/XLSX fixo, a V23 de periodo de vinculo, a atomicidade da importacao, o bloqueio de PUBLICADO e o cliente temporal governado; testes focais Node `72/72`, Maven `57/57` e Angular `12/12` passaram. O candidato OpenAPI `D093B4368DA5F438B199C24A64CE7DC70AEED9D2638D7A5925C95FCD3ADEADC1` foi aprovado. Integracao, cleanup e push continuam separados.
Git - Fase 3 / gap closure (03-25, concluido localmente, 2026-09-04): o 03-25 foi certificado na worktree `worktree-agent-03-gap-exec` sobre `8447678`. O commit tecnico `b07ac9f` entrega o historico append-only V24, a consulta escopada de lifecycle, o contrato OpenAPI e o cliente Angular governado; testes focais Maven `35/35`, Node `72/72` e Angular `12/12` passaram. O candidato OpenAPI `5D6400EF390D0D459B05A0D3CB4C94774271928E16D7DB618968100D59809D9B` foi aprovado. Integracao, cleanup e push continuam separados.
Git - Fase 3 / Wave 19 (integrada, enviada e limpa, 2026-09-02): o 03-20 foi executado na worktree dedicada `worktree-agent-03-20` sobre `8e1b893`; o commit tecnico `fa2985d` converge as seis rotas de produto para o ConsoleShell, remove o AuthenticatedLayoutComponent e consolida a prova operacional. Angular focal `22/22` e `35/35`, Angular completo `342/342`, Node `625/625`, validador de rotas `173/173`, validador vivo `OK`, build production e Playwright legado `4/4` passaram. O checkpoint humano `aprovar Console convergido 03-20` foi recebido. O commit documental `88eb964` foi integrado por fast-forward; a worktree e a branch exatas foram removidas e `master`/`origin/master` foram sincronizadas. O follow-up de manutenibilidade permanece registrado na formalizacao corrente.

Git - Fase 3 / Wave 20 (concluida, integrada e limpa, 2026-09-02): o 03-17 certificou arquitetura/privacidade, matriz Chromium/Firefox, acessibilidade medida e mutacoes negativas restauraveis. Os commits tecnico e documental foram integrados por fast-forward; a worktree e a branch exatas foram removidas. A divida de manutenibilidade da prova permanece registrada como follow-up.

Git - Fase 3 / gap closure (03-23, integrado localmente, 2026-09-03): o 03-23 foi implementado na worktree `worktree-agent-03-gap-exec` sobre `8388bb3` e rebased sobre `2ecb388`. O commit tecnico rebased `69c887b` entrega a decisao A1 e a autorizacao derivada por competicao para organizacoes, atletas, rosters e importacao; a matriz focal passou com `22/22`. A certificacao ampla das capacidades preservadas continua prevista para 03-27; cleanup e push nao foram executados.

Git - Fase 3 / gap closure (03-22, integrado localmente, 2026-09-03): o 03-22 foi implementado na worktree `worktree-agent-03-gap-exec` sobre `5cfaa4c` e rebased sobre `2ecb388`. Os commits rebased `f8a2023` e `2200d6e` entregam o formato GENERIC, a V22, os guards contra derivacao indevida, o contrato e as provas focais; o candidato OpenAPI `D8DDE8402F6D66D279B46E66DAF5000619A010ED28C5CA2B4000A6287B527E3D` foi aprovado. Cleanup e push nao foram executados.

Git - Fase 3 / gap closure (03-21, integrado localmente, 2026-09-03): o 03-21 foi implementado na worktree `worktree-agent-03-gap-exec` sobre `fba0337` e rebased sobre `2ecb388`. Os commits rebased `ea1149f` e `963f01b` entregam a associacao N:N, o backfill V21, o contrato e as provas focais; o candidato OpenAPI `D53E38AE2F7CE539B0610694480E158535697ED5E83FB52C4A4C2309B72228DC` foi aprovado. Cleanup e push nao foram executados.

Git - Fase 3 / Wave 18 (integrada, enviada e limpa, 2026-09-02): o 03-19 foi executado na worktree dedicada sobre `d63d3bd` e normalizado no padrao de dois commits: tecnico `975f9f6` e documental corrente, que nao cita o proprio hash. Surefire `266/266`, Failsafe `441/441`, Node `625/625`, Angular `356/356`, build production, lint, validador de rotas e Playwright pessoal `2/2` passaram; a variante pessoal do ConsoleShell e as tres rotas de conta ficaram verdes. A worktree e a branch exatas foram removidas; `master` e `origin/master` estao sincronizadas.

Git - Fase 3 / Wave 17 (integrada, enviada e limpa, 2026-09-01): o 03-16 foi executado na worktree dedicada sobre `32c6af3`; os commits tecnico `a5e0714` e documental `df1a7ca` foram integrados por fast-forward em `master`. Surefire `266/266`, Failsafe `441/441`, Node `625/625`, Angular `354/354`, builds e Playwright `3/3` passaram; capabilities, isolamento de escopo e rotas O1-O8 ficaram verdes. A worktree e a branch exatas foram removidas e o resultado esta contido em `origin/master`.

## Project Reference

Consulte: .planning/PROJECT.md (atualizado em 2026-08-26)

**Valor central:** Tornar os jogos universitários da UFU mais envolventes por meio de uma competição social de palpites, justa, transparente e sem dinheiro real.
**Current focus:** fechar os dois gaps restantes da Fase 3 nos planos 03-26 e 03-27; a Fase 4 continua aguardando essa certificacao.

## Current Position

Phase: 3 of 8 — Experiência Esportiva do Piloto (gap closure)
Plan: 03-25 concluido localmente; 03-26 e o proximo checkpoint.
Status: Complete - implementacao e documentacao locais; integracao, cleanup e push continuam separados.
Last activity: 2026-09-04 - plano 03-25 certificado e documentado na worktree de gap closure.

GitHub: milestones das Fases 1 e 2 encerrados; milestone da Fase 3 aberto com tracking e 17 issues de plano.
Git — Fase 3 / Wave 11 (concluida, enviada e limpa, 2026-08-31): o 03-10 foi executado na `worktree-agent-03-10` sobre `b2fce12`. Os commits tecnico `cf478b1` e documental `8e7109d` foram integrados por fast-forward na `master`; Maven focal, Node, Angular e builds passaram. A worktree e a branch exatas foram removidas e `origin/master` foi sincronizada.
Git — Fase 3 / Wave 12 (concluida, enviada e limpa, 2026-08-31): o 03-11 foi executado na `worktree-agent-03-11` sobre `63e0c0e`. Os commits tecnico `36a3d8c` e documental `1db2d19` foram integrados por fast-forward na `master`; Maven focal/completo, Node, Angular, builds e Playwright passaram. A worktree e a branch exatas foram removidas e `origin/master` foi sincronizada.
Git — Fase 3 / Wave 13 (integrada, limpa e enviada, 2026-08-31): o 03-12 foi executado na `worktree-agent-03-12` sobre `b84e3db`; apos rebase sobre o commit legitimo `424d98b`, o tecnico `7088123` contem 42 paths certificados e o documental foi integrado na sequencia. O candidato OpenAPI aprovado e `8BE63578F11D7D3DBC4F771A8A0F03B41482C1B7C00698AAC69165EF1A216396`, com governed-before/after iguais. Maven completo `416/416`, Node `625/625`, Angular `316/316`, Playwright esportivo `3/3`, builds e gerador passaram. A worktree e a branch exatas foram removidas e `master`/`origin/master` estao em `9f3bbea`.
Git — Fase 3 / Wave 14 (integrada, enviada e limpa, 2026-08-31): o 03-13 foi executado na `worktree-agent-03-13` sobre `8537c4b`; o commit tecnico `e03c0b5` contem exatamente 32 paths certificados da allowlist e o documental `4ba73ad` formaliza o plano e o estado. `frontend/src/app/api/generated/index.ts` permaneceu inalterado. O candidato aprovado e `5BC25D0DC503474976AFC5223037464CB66205D4DD29725A796011B78DC0295B`, com governed-before/after iguais. Surefire `266/266`, Failsafe `421/421`, Node `625/625`, Angular final `330/330`, Playwright esportivo `3/3`, builds e gerador passaram. A primeira suite Angular teve um timeout intermitente fora do escopo, depois verde isolado e na repeticao completa. A integracao foi fast-forward em `master`, a worktree e a branch exatas foram removidas, e o resultado esta contido em `origin/master` `93e7d65`.
Git — Fase 3 / Wave 15 (integrada, enviada e limpa, 2026-08-31): o 03-14 foi executado na worktree dedicada; o commit tecnico `022f5a1` e o documental `93e7d65` foram integrados por fast-forward em `master`. O candidato aprovado e `A519EC31AF3F6CF504DB180A9606B700288A76C2CB6504BEEA3802A4A4726B93`, com governed-before/after `5BC25D0DC503474976AFC5223037464CB66205D4DD29725A796011B78DC0295B`. Maven, Node, Angular, builds e Playwright passaram; a worktree e a branch exatas foram removidas e `origin/master` esta em `93e7d65`.
Git - Fase 3 / Wave 16 (integrada, enviada e limpa, 2026-09-01): o 03-15 foi executado na worktree dedicada sobre `112ca13`; o commit tecnico `0474365` e o documental `7809a93` foram integrados por fast-forward em `master`. O candidato OpenAPI aprovado e `1BE6BAB88EA2F208A38BE115E3CB4D9B0DA2EBB8E04FBF038FD122E5A2B43F27`, com governed-before/after `A519EC31AF3F6CF504DB180A9606B700288A76C2CB6504BEEA3802A4A4726B93`. Surefire `266/266`, Failsafe `431/431`, Node `625/625`, Angular `346/346`, builds e gerador passaram; Flyway `14/14`, media IT `7/7` e matriz S3 `19/19` passaram. A worktree e a branch exatas foram removidas e o resultado esta contido em `origin/master`.
Git - Processo de desenvolvimento (sincronizado, 2026-09-01): os commits `9600e82`, `e9cb00b`, `1761ef4` e `3d5679f` integram bundles de skills, guardrails, coordenadores, preflight e gates de revisao; os commits `75522a6` e `a20b6d4` consolidam a formatacao. Os seis commits estao em `master` e `origin/master` por meio de `112ca13`; a worktree e a branch de formatacao foram removidas.
Git — Fase 3: Wave 1, Wave 2 e o fechamento documental do 03-18 foram enviados. A Wave 4 foi criada na `worktree-agent-03-03` a partir de `7d3cbc6`; o commit técnico `9399a7c` contém exatamente 44 paths e o documental `3368d73` contém os seis documentos GSD. A integração fast-forward levou ambos para `master`; limpeza da worktree/branch e push continuam pendentes de aprovação específica.

Git ? Fase 3 / Wave 6 (snapshot certificado, 2026-08-29): o 03-05 foi executado na `worktree-agent-03-05` sobre `72d8854`. O candidato aprovado e `12B2982C70B98D02BD6609BA95AAE350E2BF65999E57F5D16183B65DABBA7201`; hashes governados before/after `21C94593F724A3478B866C4F0F4402B0FACA4EE92FD265A9B9CDEDD6BE5E5F37`. Node `625/625`, Maven `350/350`, Playwright Arena `2/2` e Console `1/1` passaram; Angular completo `248/253` conserva cinco falhas historicas fora do escopo.

Git ? Fase 3 / Wave 7 (concluida, enviada e limpa, 2026-08-30): o 03-06 foi executado na `worktree-agent-03-06` sobre `9f664be`. Os commits tecnico `6c8d74f`, documental `01afe86` e de formalizacao `455f75e` foram integrados por fast-forward; `master` e `origin/master` estao em `455f75e`. O candidato aprovado e `111FEA082473D8D6541BAD51A44D5D00DABB160316EF7625A564CDAA55101818`. Maven `610/610`, Node `625/625`, Angular focal O4 `11/11`, build production e Playwright esportivo `2/2` passaram; Angular completo `261/266` conserva cinco falhas historicas fora do escopo. A worktree e a branch exatas foram removidas.

Git ? Fase 3 / Wave 8 (concluida na worktree, 2026-08-30): o 03-07 foi executado na `worktree-agent-03-07` sobre `11fd3b8`. O candidato aprovado e `B75187EFECB25E0D4809F6ED5D0354D329D9175DB9ACE3BCEB1BBA4ECDB7A8B2`; hashes governados before/after `DB17FF4C4B1058BED0836C51F1922922D621E78CC3DFA35C001C682ADED5DC24`. Maven `625/625`, Node `625/625`, Angular focal `10/10`, build production e Playwright esportivo `2/2` passaram; Angular completo `272/277` conserva cinco falhas historicas fora do escopo. Nenhum stage, commit, integracao, limpeza ou push foi executado. A proxima wave tem colisao documental V14 entre este plano e `03-08`.

Git — Fase 3 / Wave 10 preflight documental (2026-08-30): o replanejamento do 03-09 foi ampliado de CSV para CSV/XLSX, com parsers separados e linha logica compartilhada, e formalizado no commit `bd6b5ce`. A allowlist inicial possuia 50 paths; a implementacao exigiu seis paths mecanicos adicionais e o PLAN foi reconciliado para 56. A Task 1 recebeu aprovacao para Commons CSV 1.14.1 e Apache POI 5.5.1.

Git — Fase 3 / Wave 10 certificacao do 03-09 (2026-08-31): a worktree `worktree-agent-03-09` sobre `2447652` concluiu os cinco tasks. Dependency tree, parsers, importacao atomica, candidato OpenAPI `C1B4B3F7F37E84EF63911A6BE31A2B36C949DDC0FB63B84C193D72F19B3D3BB8`, geracao governada, O5, fronteira de rotas, builds e Playwright passaram. Maven completo terminou com Surefire `266/266`, Failsafe `391/391`, total `657`; Node `625/625`; Angular focal `7/7`; Playwright `2/2`. Angular completo ficou `285/290` por cinco falhas historicas de `role-admin-page.component.spec.ts`, fora do escopo. O plano e o SUMMARY estao `complete-pending-git`; stage, commits, integracao, limpeza e push continuam pendentes.

Git — Fase 3 / Wave 4 (snapshot local aprovado, 2026-08-29): o 03-03 entrega a agenda pública paginada, o candidato OpenAPI aprovado `0B631F67A415BEE7BC95ECF209BD240C922A93ED47B69385B969B093D464AB95`, o cliente governado e a Arena V. O gate final passou Surefire `251/251`, Failsafe `343/343`, Node `623/623`, gerador `72/72`, Angular `241/241`, build produtivo e Playwright `2/2`; a auditoria fresca terminou `PASS`, P0=P1=P2=0. `frontend/angular.json` não possui diff de conteúdo e fica fora da allowlist final. Os temporários locais de `.playwright-cli/` foram removidos antes do handoff.

Git — Fase 3 / Wave 4 (commit técnico, 2026-08-29): o commit `9399a7c13af45280e001f0f0d6e3bc77a8c4b3e1`, filho direto de `7d3cbc6bfdfbec52bd9e78e4eeef916b846de11c`, contém exatamente os 44 paths técnicos certificados; `git diff --cached --check` passou antes do commit. Os seis documentos GSD foram formalizados no commit documental seguinte; os quatro metadados candidatos continuam ignorados, `frontend/angular.json` não possui diff de conteúdo e `frontend/src/app/api/generated/index.ts` permaneceu inalterado.

Git — Fase 3 / Wave 4 (commit documental, 2026-08-29): o commit `3368d7374fb77d99007c712c80875e79d2c46d9c`, filho direto de `9399a7c13af45280e001f0f0d6e3bc77a8c4b3e1`, contém exatamente `03-03-PLAN.md`, `03-03-SUMMARY.md`, `03-UI-SPEC.md`, `03-VALIDATION.md`, `ROADMAP.md` e `STATE.md`. Por regra anti-autorreferência, seu próprio SHA é registrado nesta passada posterior. `frontend/angular.json`, os quatro metadados candidatos e qualquer path técnico ficaram fora deste segundo commit. A integração fast-forward foi concluída; limpeza e push continuam pendentes.

Git — Fase 3 / Wave 2 (concluída, enviada e limpa, 2026-08-27): a `master` recebeu o commit técnico `2be1b0f`, contendo exatamente os 14 paths técnicos certificados, seguido pela formalização documental com `03-02-PLAN.md`, `03-02-SUMMARY.md`, `03-VALIDATION.md`, `STATE.md` e `ROADMAP.md`. A árvore passou `SportsTracerIT` 9/9, `ModulithArchitectureTests` 2/2, Surefire 251/251, Failsafe 342/342, Node 623/623 e Maven `clean verify` com `BUILD SUCCESS`. A mutação de escopo ficou vermelha e o service foi restaurado ao SHA-256 `552921E19D2B9ED51DE813470DE8EA90F67E65947C2B88C59A71CBAEDBB05261`. A revisão Codex retornou `PASS`, P0=P1=P2=0. Os 14 hashes da worktree revisada permaneceram aprovados e o conteúdo Git integrado ficou idêntico em 14/14 paths. A worktree e a branch temporárias foram removidas; `.gitignore` foi atualizado para ignorar somente o cache local de pesquisa, e `master`/`origin/master` chegaram juntas a `12d8f01`.

Git — Wave 7 (concluída e integrada, 2026-08-23): operações Git mutáveis autorizadas e executadas, na ordem. (1) `git worktree add` para os três planos, a partir do SHA base `a6f5f65`, com branches neutras `worktree-agent-02-23`, `worktree-agent-02-28` e `worktree-agent-02-30`; os três nasceram limpos. (2) Um quarto worktree temporário e destacado, `wave-07/integration`, montou a árvore combinada sem commit para as suítes agregadas e para cada rodada de revisão. (3) **Rebase de dobra**: as correções das cinco rodadas de revisão foram dobradas nos commits existentes por cherry-pick encadeado com `--amend`, sem criar commit de correção. Os SHAs `eb4e79f`, `e63e313` e `a9b68de` deixaram de existir. (4) **Normalização de mensagens**: uma segunda passada reescreveu as mensagens dos cinco commits da onda para uma linha, no padrão dos vinte commits anteriores do projeto, e removeu os rodapés `Co-Authored-By` e `Claude-Session`, que nenhum commit do repositório usa. Os corpos longos foram auditados item a item antes de descartados: nove itens que existiam somente neles foram acrescentados aos SUMMARY, e as árvores dos três commits de código permaneceram idênticas antes e depois, provando que só a mensagem mudou.

| Commit | Conteúdo | Prova na própria árvore |
|---|---|---|
| `35deeaa` | `feat(02-28)` origem Keycloak endurecida | módulo `56/56` |
| `d9b6f52` | `feat(02-30)` inbox assinado e encerramento de sessões | Surefire `187/187`, Failsafe `229/229`, Node `540/540` |
| `e4900a0` | `feat(02-23)` papéis operacionais, escopo e `V7` | Surefire `201/201`, Failsafe `241/241` |

O commit documental de emendas da onda, `41b17eb`, permanece imediatamente acima da base `a6f5f65`. A `master` termina no commit documental desta formalização, cujo hash não cabe dentro dele próprio. A árvore final foi verificada por hash de blob contra o snapshot que recebeu `PASS`: 19 paths, 19 idênticos. Suítes na `master` final, com `clean`: Surefire `201/201`, Failsafe `241/241`, módulo Keycloak `56/56`, Node `540/540`.

**Reconciliação posterior confirmada:** a Wave 7 foi enviada; `master` e `origin/master` chegaram juntas a `c2277a9`. Seus worktrees e branches residuais foram removidos. Antes da Wave 8 existiam somente a worktree principal e a branch `master`.

Git — Wave 8 (concluída e enviada, 2026-08-23): o histórico foi normalizado em quatro commits, dois por plano: `81c663d` (`feat(02-17)`), `25c9951` (`docs(02-17)`), `8ddaeff` (`feat(02-31)`) e o commit documental final `022869d`. O OpenAPI e o cliente de recovery acompanham `feat(02-31)`, enquanto o contrato de papéis permanece em `feat(02-17)`; assim cada commit preserva paridade código ↔ contrato e pode ser investigado por `git bisect`. Os 48 caminhos não documentais terminam idênticos por blob ao snapshot revisado. A branch foi integrada por fast-forward, as branches/worktrees temporárias foram removidas e `master`/`origin/master` chegaram juntas a `022869d`. `AGENTS.md` permaneceu fora dos commits.

Git — Wave 9 (concluída e enviada, 2026-08-24): quatro commits lineares, dois por plano: `8b3b632` (`feat(02-10)`), `a79e8b2` (`docs(02-10)`), `46b85fe` (`feat(02-29)`) e `ffac2ea` (`docs(02-29)`). A árvore combinada passou Surefire `235/235`, Failsafe `303/303`, módulo Keycloak `59/59` e Node `615/615`; os validadores de realm, runtime e recovery e `docker compose config --quiet` também passaram. O primeiro `clean verify` do backend falhou corretamente sem as variáveis HMAC obrigatórias; a execução canônica seguinte usou chaves descartáveis somente no processo e terminou verde, sem persistir segredo. O snapshot final recebeu revisão sem P0–P2. A integração ocorreu por fast-forward, as branches e worktrees temporários exatos foram removidos e `master`/`origin/master` chegaram juntos a `ffac2ea`. `AGENTS.md` permaneceu fora dos commits.

Git — Wave 11 (snapshot técnico revisado, 2026-08-25): o commit `32ea355` (`feat(02-12)`) contém exatamente os dez paths da allowlist certificados por hash. A árvore passou Surefire `246/246`, Failsafe `317/317` e Node `617/617`; as mutações de ordem transacional e de matcher ficaram vermelhas e foram restauradas por hash. Os refs finais de integração e sincronização não são autorreferenciados neste documento e devem ser confirmados ao vivo. `AGENTS.md` e caches Angular permanecem alheios e fora dos commits.

Git — Wave 12 (preflight aprovado, 2026-08-25): o dry-run do adaptador confirmou duas lanes sem sobreposição, `02-13` e `02-25`. Antes de criar worktrees, o responsável ratificou três correções documentais: a fatia S3 possui oito arquivos; o manifesto governado do cliente vive em `frontend/scripts/generate-api.mjs`; e a aprovação do inventário/digest passou a ser o checkpoint explícito `02-25-T2`, elevando a matriz para 68 Task IDs. O commit que transporta essas emendas para as worktrees não pode citar o próprio hash; confirme-o ao vivo por `git log -1`. Nenhuma worktree, branch temporária, chamada externa ou teste material havia sido iniciado neste ponto. `AGENTS.md`, `CLAUDE.md` e os caches Angular permanecem fora do commit.

Git — Wave 12 (snapshot final aprovado, 2026-08-25): as lanes `02-13` e `02-25` foram montadas sobre `d4cc1099` num worktree de integração destacado, totalizando 21 paths e SHA-256 composto `8023A1881DCCE314757C722C0655AC939C7C703A4CEE32C178A6A7EFE1DF5543`. A árvore terminou Angular `136/136`, Node `617/617`, Surefire `246/246`, Failsafe `317/317`, build produtivo e gates de contrato/fronteira verdes. O cliente final possui 47 arquivos e digest aprovado `9C8D3FF3BF4D40B20FEC58E4C44DF7F4189D15CE36ADF23AB63614C6E06E4071`. A inspeção final em Chrome real provou 320px/1280px, teclado, PUT único, mensagem persistente e ausência da matrícula no DOM/storage. A revisão retornou `PASS`, P0=P1=P2=0. Nenhum stage, commit, integração, limpeza ou push desta onda ocorreu até este registro.

Git — Wave 13 (concluida em 2026-08-25): o Plano `02-19` entregou S5 em 12 paths de implementacao, com Angular `157/157`, Node `617/617`, Surefire `246/246`, Failsafe `317/317`, build produtivo e smoke em navegador real. O snapshot final reconciliou 15/15 paths e recebeu `PASS`, P0=P1=P2=0. O historico preserva a emenda de decisao, o commit tecnico e este commit documental; refs finais de integracao e sincronizacao devem ser confirmados ao vivo porque o documento nao pode autorreferenciar seu proprio hash.

Git — Wave 14 (concluída em 2026-08-26): o Plano `02-20` entregou a superfície S6 em nove paths e foi integrado pelos commits técnicos/documentais `6670789` e `34ce0c7`. O Plano `02-32` entregou o endpoint e o cliente de capacidades em 14 paths pelo commit técnico `413614d`; seu commit documental é o registro corrente e, por isso, não cita o próprio hash. A árvore combinada foi certificada com Angular `196/196`, Node `617/617`, Surefire `251/251`, Failsafe `332/332`, build produtivo e revisão final `PASS`, P0=P1=P2=0. O inventário gerado final possui 50 arquivos e digest aprovado `BDF0CA8BDBC4118DA74FA0E5B0FFF47D4C0FF636DC9309751F550161F8EE4E71`. A integração foi linear; limpeza exata e sincronização de `origin/master` são confirmadas ao vivo depois deste commit documental.

Git — Wave 15 (concluída e revisada em 2026-08-26): o Plano `02-26` entregou guard global de completude de perfil, navegação S7 governada pelas três capacidades do BFF e limpeza de estado privilegiado em 13 paths pelo commit técnico `9ace941`. O snapshot passou Angular `219/219`, Node `623/623`, Surefire `251/251`, Failsafe `332/332`, build produtivo e mutação discriminante do guard; a revisão final retornou `PASS`, P0=P1=P2=0. Movimento físico por Tab e geometria real em 320 px continuam deliberadamente reservados ao checkpoint humano do 02-27. O commit documental corrente não cita o próprio hash; integração, limpeza e sincronização devem ser confirmadas ao vivo depois dele.

Git — Wave 16 (concluída e aprovada em 2026-08-26): o checkpoint 02-27 foi repetido sobre `a0b5a20` após as remediações proprietárias 01-14/02-26. O manifesto `F941E34A81F17600521E8B9904757FEC049781CA3403F94A1721AD31902FC38B` validou 35 paths, com 33 intactos, dois remediados e zero divergências. Angular `220/220`, Node `623/623`, build produtivo, fronteira e Playwright S1-S7 ficaram verdes; desktop/320 px, teclado, foco, 44 px, ausência de overflow, fonte local e 403 fail-closed foram inspecionados antes da resposta literal `aprovado`. `master` está em `a0b5a20`, quatro commits à frente de `origin/master` (`076c7c4`); formalização, limpeza e push permanecem operações separadas.

Emendas de allowlist da Wave 7, todas ratificadas pelo responsável **antes** da edição: `META-INF/betufu-password-reset-outbox-changelog.xml` e `keycloak-password-event-listener/pom.xml` para o 02-28; `scripts/validate-runtime-prerequisites.mjs`, `scripts/validate-runtime-prerequisites.test.mjs` e `FlywayMigrationIT.java` para o 02-30; `V7__enforce_operational_role_scope.sql` para o 02-23, que também já havia recebido o `FlywayMigrationIT.java` na emenda anterior desta onda.

Emenda de allowlist — Wave 7 (2026-08-22): o `02-23-PLAN.md` passou de 14 para 15 paths por ratificação prévia do responsável, acrescentando `backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java`. Causa: `V6` invalida as seis asserções que fixam o total de migrations em 5 — duas em cada um dos três testes do arquivo. A lane do 02-23 gravou **6**. **Corrigido no fechamento da onda:** a premissa de que o 02-30 não precisava tocar o arquivo estava errada. O commit do 02-30 cria a `V5_1` e, sem reconciliar a contagem, ficava vermelho isoladamente — qualquer `git bisect` entre ele e o 02-23 daria falso positivo. O arquivo entrou também na allowlist do 02-30, que grava **6** com a `V5_1`, e o 02-23 leva a **8** com a `V6` e a `V7`. O defeito não constava de nenhum achado de revisão; apareceu porque o rebase de dobra obrigou a medir cada commit individualmente.

| Commit | Conteúdo |
|---|---|
| `7ae235a` | Emendas de escopo da Fase 2 em `PROJECT.md`, `REQUIREMENTS.md` e `ROADMAP.md`: verificação documental do vínculo UFU adiada para a v2 (`VERI-01`–`VERI-05`, `VERI-08`, `VERI-12`, `OPER-05`), `PRIV-02` reatribuído à Fase 3, e MFA tornado opcional em todo o MVP (`IDEN-05`, `ODDS-12`). |
| `db4177f` | `02-CONTEXT.md` e `02-DISCUSSION-LOG.md` da discussão da Fase 2. Hash preenchido na passada seguinte, conforme previsto. |
| _(commit seguinte, sem hash próprio)_ | `02-UI-SPEC.md` aprovado, a emenda de `OPER-01` em `REQUIREMENTS.md` e este `STATE.md`. O hash não cabe dentro do próprio commit. |

Progress: [###########-] 25/27 planos da Fase 3 (93%; 2 gap-closure pendentes)

## Métricas de desempenho

**Velocidade:**

- Total de planos concluídos: 78
- Duração média: não mensurada — os planos foram acompanhados em múltiplas interações
- Tempo total de execução: não mensurado

**Por fase:**

| Fase | Planos | Total | Média/plano |
|------|--------|-------|-------------|
| 1 | 31 | não mensurado | não mensurada |
| 2 | 32 | não mensurado | não mensurada |
| 3 | 4 | não mensurado | não mensurada |

**Tendência recente:**

- Últimos planos: 03-01, 03-02, 03-18 e 03-03; o 03-18 foi exclusivamente documental e o 03-03 reabriu a execução técnica na Wave 4
- Correção de registro: esta seção declarava 14 planos e uma lista defasada enquanto o frontmatter já dizia 18. Reconciliada no fechamento do 01-24.
- Tendência: ainda não estabelecida sem duração contínua mensurada

## Contexto acumulado

### Decisões

- A variante V autenticada por SHA-256 é a referência visual canônica da Fase 3; a identidade única se expressa como `Arena pública` e `Console operacional` em Angular nativo.
- A Arena V aprovada usa `#910008` para superfícies vermelhas profundas, `#FF626B` para sinal/ação, `#FFC4C4` para títulos editoriais e off-white/creme no hero; esses papéis são tokens semânticos, não cores ad hoc.
- A agenda calcula a próxima fronteira temporal por Signal e timer único: eventos vencidos deixam hero e ticker sem polling nem nova chamada à API.
- O 03-03 preserva o tracer vertical e entrega a listagem pública real mínima ponta a ponta; a expansão operacional completa do workspace/catálogo O1 permanece exclusivamente no 03-04.
- Consumidores do cliente OpenAPI seguem a ordem candidato read-only, aprovação humana de inventário/digest, geração governada e somente então consumo Angular.
- O replanejamento técnico fica serializado de W4 a W20: 03-19 converge o Console pessoal, 03-20 converge a operação legada/retira o shell antigo e 03-17 certifica 26 superfícies. D-95 proíbe alterar lógica de negócio durante essa migração visual.
- O tracer esportivo deriva `competitionId` da edição ou evento persistido antes da autorização; payload e path não são usados como prova de escopo.
- `sports` consome `platform.identity.OperationalIdentityLookup`, enquanto `identity` valida concessões pela porta pública `sports.CompetitionScopeCatalog`; nenhum módulo importa o `internal` do outro.
- Publicação esportiva cria snapshot append-only e o GET anônimo consulta somente a publicação mais recente; rascunho, ator e dados internos não cruzam a fronteira pública.
- O matcher público libera apenas `GET /api/v1/public/sports/**`; comandos continuam protegidos pelo matcher autenticado de `/api/v1/**`.

- Na navegação S7, o backend converte os papéis D-46 em três capacidades fechadas; o frontend nunca recebe nem mapeia papel. Ausência, valor desconhecido ou payload malformado falham fechados, e a visibilidade do menu é apenas conveniência sobre a autorização do BFF.
- A trilha de auditoria referencia estados e versões sem armazenar o HMAC da matrícula; MFA e evidência documental permanecem ausentes do MVP.
- O usuário de migration será separado do usuário de runtime. A aplicação não terá `UPDATE`, `DELETE`, `TRUNCATE` ou DDL sobre `audit_event`, e um trigger que lança erro servirá como segunda camada.
- Durante o MVP fechado, a auditoria é retida pela vida do ambiente. Uma política definitiva por classe de dado permanece gate de `PRIV-05` antes do lançamento público.
- Mudanças de apelido limitam o instante efetivo por `created_at`, impedindo regressão temporal quando o relógio do PostgreSQL está ligeiramente à frente do host Java.

- A claim `betufu_roles` do ID token vira `ROLE_TECHNICAL` por allowlist positiva e fechada: exige coleção, depois String, depois igualdade byte a byte com `TECHNICAL`. E-mail, username, grupo e `realm_access` nunca concedem autoridade, e a fiação do mapper é explícita nos dois ramos do `SecurityConfiguration`.
- `GET /api/v1/diagnostic` existe apenas em `dev` e `homolog`. Em produção não há bean nem mapping — ausência material, provada por inspeção do contexto e do `RequestMappingHandlerMapping`, nunca por status 403, que não distingue negado de inexistente.
- O contrato OpenAPI passou a cobrir os quatro paths `/api/v1` da fase, e o modo `--contract` do gate agregado foi implementado: ele compara mappings do código com o contrato e roda o check de drift do cliente gerado.
- **Guard de validador protege propriedade, não identidade de artefato.** Dois guards caíram neste plano sem que nenhuma propriedade fosse violada, e ambos foram escritos quando existia um único artefato do tipo: o `OIDC_BOUNDARY` fixava `Customizer.withDefaults()`, e o `GENERATED_WITH_CREDENTIALS` fixava `identity.service.ts` com contagem exata. É padrão, não coincidência.
- O reset local é o único comando destrutivo da fase. Dry-run é o padrão, `--apply` exige a frase literal `APAGAR DADOS LOCAIS BETUFU` em terminal interativo, e o bootstrap é opcional e desligado.
- Um recurso Docker só é removido pela conjunção de todos os critérios: containers exigem serviço na allowlist, `project=infra`, `oneoff=False` e `config_files` apontando para este checkout; volumes exigem nome literal, `project=infra` e label de volume igual ao nome. Redes ficam fora.
- O label `com.docker.compose.project` sozinho é insuficiente para autorizar remoção, porque `--project-name infra` é genérico e colide com qualquer composição num diretório homônimo. `config_files` é o único discriminador que amarra um recurso a um checkout — verificado presente em containers e ausente em volumes e redes no Compose 5.3.1.
- Prune é o oposto de allowlist: `system prune`, `volume prune`, `image prune` e `down -v` são proibidos no reset, que remove item a item por identidade.
- Um teste de escopo destrutivo não cria o recurso hostil de verdade: se a guarda falhar, o próprio teste causa a perda que deveria detectar. A prova recai sobre a função pura de seleção, com inventário em memória.
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
- O host Angular usa uma seam de rotas build-specific: development/homolog importam a lista development e production a substitui por módulo próprio vazio.
- O frontend usa HttpClient same-origin com XSRF e não armazena, constrói ou injeta bearer token; o BFF Spring continuará responsável por tokens e sessão.
- A fronteira Angular é protegida por validador Node fail-closed com 69 regressões negativas e por um AppComponent com router-outlet único.
- A allowlist de cinco pacotes `hasInstallScript` reconhece o grafo, mas não autoriza scripts; `npm ci --ignore-scripts` foi preservado e o risco moderado `GHSA-frvp-7c67-39w9` foi aceito temporariamente.
- O cliente OpenAPI inicial usa o wrapper npm `@openapitools/openapi-generator-cli@2.40.1` somente com `--ignore-scripts` e o motor Java `7.24.0` adquirido separadamente, com 31.904.922 bytes e SHA-256 `4B83CCC6FD43056C8C631CD0195E5100BD0550912502527BAB09AC76152DAB0C`.
- O lockfile do 01-20 é governado pelo SHA-256 `99A856C8C635EB4FB9845DEE6F1F2E7A2A4E8576BF80654B9695B5E6D6C4F035`; sua allowlist de seis entradas `hasInstallScript` apenas reconhece metadados e não autoriza lifecycle.
- A árvore TypeScript gerada é machine-owned e exige exatamente **20 paths** com o digest canônico `27AC44819495367D671C92341400E86EE0B79F4ECAE4229F53BB90F2560C35C0`, aprovado pelo responsável no 01-26. O digest anterior `78E92BB1...` valia para os 13 paths de antes do contrato completo; o histórico `701CCD...` continua não reproduzível.
- O risco de o runner resolver Java pelo `PATH` local permanece aceito temporariamente somente com as propriedades aprovadas de Java 25/Eclipse Adoptium; a identidade binária imutável deve ser decidida para CI.
- A identidade local usa o par OIDC exato `(issuer, subject)` como chave natural case-sensitive e um UUID aleatório como identificador público; e-mail e claims opcionais não participam da correlação.
- A migration Flyway V1 e o JPA validam `public.local_identity` no PostgreSQL 18.4; repetição e concorrência usam `ON CONFLICT DO NOTHING` seguido de `SELECT` em `READ_COMMITTED`, sem no-op update.
- O endpoint `PUT /api/v1/tracer/identity/current` existe em todos os profiles de runtime, inclusive production, usa somente o ID token OIDC e responde por DTO allowlist com quatro campos.
- O lifecycle Maven mantém `*Test` no Surefire e executa `*IT` no Failsafe; a prova agregada do 01-05 terminou com Surefire 8/8 e Failsafe 17/17.
- O Plano 01-29 entregou e comprovou o predecessor explícito do runtime browser: datasource PostgreSQL externo, sessão `dev` deliberadamente em memória até o 01-06 e suporte XSRF SPA oficial do Spring Security 7.1 sem endpoint ou header manual no Angular.
- A prova agregada do 01-29 passou com Maven `36/36`, Node `191/191`, runtime `status: ok`, matriz `403/403/403/200/200`, SQL `BETUFU_RUNTIME_SQL_OK`, JAR imutável, zero conexões Redis e cleanup restaurado.
- O Plano 01-21 comprovou no navegador real o fluxo hospedado, a validação D-15, dois PUTs explícitos com UUID estável, o XSRF same-origin automático, a ausência de tokens no browser e a ausência do tracer em production; o 01-08 reutiliza essa base e a endurece para origem, logout e STOMP CONNECT.
- O Plano 01-06 fixa timeout ocioso `PT8H`, sem prazo absoluto adicional, e usa Spring Session Redis no profile `dev`.
- A allowlist inicial de atividade válida reconhece somente `PUT /api/v1/tracer/identity/current` autenticado e concluído com resposta `2xx`; tráfego técnico ou malsucedido não renova a sessão.
- A persistência seletiva conserva o instante original em operações sem atividade válida, avança o tempo de forma monotônica quando há atividade aprovada e trata session fixation sem reintroduzir uma sessão encerrada.
- A prova agregada do 01-06 passou com Node `263/263` e Maven `56/56` — Surefire `27/27` e Failsafe `29/29`, incluindo `RedisSessionIT` `7/7` — e restaurou containers e redes ao baseline aprovado, sem resíduos do Testcontainers.
- A revisão externa do snapshot final do 01-06 terminou em `PASS`, sem achados P0–P2; os três P3 foram preservados como acompanhamento, sem reabrir a implementação validada.
- O opt-out do `userinfo_endpoint` é aplicado por um bean `OAuth2UserService` com `setRetrieveUserInfo(false)`; depender apenas do escopo `openid` deixaria a decisão silenciosamente reversível.
- A claim `iss` chega como `java.net.URL` após a conversão real do `OidcIdTokenDecoderFactory`; a projeção aceita `String`/`URL`/`URI` por `toString()`, byte-idêntico ao valor cru, preservando a chave natural `(issuer, subject)` do 01-05. `sub`, `preferred_username` e `acr` não são convertidos e mantêm verificação estrita.
- O `revocation_endpoint` vem sempre da metadata de discovery e falha fechado quando ausente; a revogação envia somente refresh token e `token_type_hint`, sem qualquer fallback para access token.
- A ordem da revogação é capturar → remover authorized client → chamar remoto; o material fica retido no estado pendente para retry dentro do TTL, e falha remota nunca revive a sessão.
- O `PENDING_TTL` de cinco minutos limita reuso, não retenção: a purga é oportunista e o material pode permanecer em memória além da janela.
- O Plano 01-07 tocou nove paths de código contra os oito declarados; a ampliação para `SecurityConfiguration.java` foi ratificada pelo responsável, por ser fiação de cliente OIDC.
- Prova nova só é confiável depois de falhar contra o código antigo; o padrão de mutação foi aplicado no `iss` e no timeout de revogação.
- O Plano 01-22 fechou a superfície HTTP de sessão: `GET /api/v1/session` é `permitAll` no par exato `(GET, /api/v1/session)` e não renova o prazo; todo o resto de `/api/v1/**` segue autenticado.
- O logout voluntário usa `client_id` no RP-initiated logout, **sem `id_token_hint`**. O ID token é material de autenticação e vazaria por histórico, `Referer` e log de proxy. A tela de confirmação resultante é também mitigação contra logout forçado por terceiro.
- Logout voluntário remove o authorized client mas **não revoga o grant**, coerente com `OidcSessionRevocationService`, que revoga só em `ADMINISTRATIVE` e `SECURITY`.
- A ordem do logout é contrato de segurança: authorized client, sessão HTTP e SecurityContext caem **antes** de a coordenação com o provedor ser computada, espelhando o 01-07.
- Uma mutação só conta como prova depois de observada vermelha. A M3a do 01-22 foi especificada, aprovada e executou verde: ela mediu que a guarda do caminho anônimo mora no short-circuit do controller, não em `currentState`.
- **Papel operacional escopado exige escopo; o global o proíbe.** A regra vive no DTO, no serviço e como `CHECK` da `V7`, porque o serviço não é a única porta para a tabela. `hasRole` de papel global só aceita concessão com `scopeRef` nulo, senão um `ADMINISTRADOR_GERAL` "escopado" aparentaria autoridade limitada enquanto a concessão é irrestrita.
- **Migration aplicada não se edita.** A `V6` recebeu uma `V7` em vez de alteração: mudar migration aplicada invalida o checksum do Flyway, garantia que o próprio `FlywayChecksumIT` testa. O mesmo princípio governou a coluna `next_attempt_at`, que entrou por changeset Liquibase novo.
- **A ativação da integração de reset é conjunção fail-closed**, não flag: habilitação explícita, HTTPS com host presente e sem `userInfo`, path exato do contrato sem query nem fragment, key-id e chave Base64 com ao menos 32 bytes. Três rodadas de revisão foram necessárias porque cada correção fechava uma borda e revelava a seguinte.
- **A autorização da recuperação usa a API suportada do Keycloak.** `AppAuthManager.BearerTokenAuthenticator` é o único caminho, e a autoridade é o papel `manage-realm` do cliente `realm-management` — papel de realm homônimo não concede nada, porque qualquer realm pode declarar um papel com esse nome.
- **Medido por execução, não deduzido:** `TokenVerifier.create(t, AccessToken.class).withChecks(IS_ACTIVE).verify()`, sem `SignatureVerifierContext`, lança `VerificationException("Secret key not set")`. O fallback removido nunca aceitou token forjado; era código morto que se lia como autenticação.
- **Alerta e DTO operacional carregam correlação e estado, nunca dado do titular.** Sujeito, realm, tipo do evento e mensagem crua de exceção ficam fora; o erro persistido é um código estável.
- **O painel S4 consome contrato antes de UI.** O OpenAPI e o cliente gerado são aprovados por inventário e digest antes do adapter; nenhum consumidor Angular interpreta um contrato ainda não fixado.
- **A presença dos blocos operacionais é decisão do servidor.** O frontend não reconstrói papéis nem revela bloco ausente; falha parcial nomeia somente bloco que o ator já podia consultar.
- **401/403 limpam o estado privilegiado e não recebem retry.** Somente falhas transitórias preservam o último snapshot válido e admitem até duas novas tentativas.
- **A auditoria da S4 mantém identidade e tempo explícitos.** A janela de sete dias é inclusiva em ambos os limites, e o UUID do ator permanece visível mesmo quando existe papel complementar.
- **As 27 mutações do cliente gerado agora são semânticas.** Cada caso falha pela regra declarada antes do digest; o digest continua como barreira final para alterações sem regra específica.
- Decisões de produto ainda em aberto continuam sem resolução implícita; a tabela Key Decisions de PROJECT.md permanece a fonte consolidada.

### Pendências

- **P3 do 03-02 ? slug duplicado.** Resolvido pelo catalogo O2 do 03-04; a V10 preserva unicidade e os comandos reportam conflito sem heuristica textual.
- **P3 do 03-02 ? expansao da fronteira publica.** Resolvida pelo catalogo e filtros reais do 03-04; novas leituras continuam sob DTO allowlist e dados publicados.

- ~~As 27 regras semânticas de `validate-angular-route-boundary` não tinham controle válido.~~ **Resolvido pelo Plano 02-18.** As 27 mutações agora exigem sua regra declarada e são avaliadas antes do digest final; as duas mutações de URL absoluta também receberam rótulos semânticos. O digest permanece ativo como controle de integridade residual.
- **P3 da Wave 10 — vocabulário dos exemplos OpenAPI.** Dois exemplos ainda usam `PERMITTED`/`BLOCKED`, enquanto o runtime usa `PERMITIDO`/`BLOQUEADO`. Tipos e comportamento estão corretos; falta alinhar apenas os exemplos documentais.
- **P3 da Wave 10 — orçamento de estilo.** O build passa, mas o SCSS do painel excede o orçamento de 4 kB em 2,67 kB; o perfil já excedia em 1,94 kB. Tratar antes de ampliar a S4 para evitar crescimento silencioso do bundle.
- **P3 da Wave 19 — manutenibilidade do validador e da prova de navegador.** A análise RepoWise (parcial, 7/17 arquivos) sinalizou complexidade ciclomática 26 e aninhamento em `validateRouteSources`, além de duplicação aproximada de 34% em `frontend/e2e/sports-pilot.spec.ts`. As suítes `173/173` e Playwright `4/4` passaram; não há defeito funcional confirmado. Reavaliar em manutenção futura, antes de ampliar o validador ou a lane E2E.
- **P3 da Wave 20 — manutenibilidade da certificação.** O RepoWise sinalizou complexidade elevada em `frontend/e2e/sports-pilot.spec.ts` e `backend/src/test/java/br/com/betufu/PrivacyArchitectureTest.java`, além de aninhamento no harness `scripts/validate-phase3-negative-mutations.mjs`. As suítes e mutações passaram; não há defeito funcional confirmado. Reavaliar antes de ampliar a prova de navegador ou o harness.
- **Varrer os demais validadores atrás de guards que pinam artefato em vez de propriedade.** Dois apareceram no 01-26 e ambos foram escritos quando existia um único artefato do tipo.
- **O adiamento do contrato OpenAPI tem custo medido.** A dívida aberta no 01-22 e repetida no 01-12 custou **4 dos 7 arquivos novos** do cliente regenerado no 01-26. Paga na época, o 01-26 teria acrescentado 3. Registrar como custo real de adiamento, não como observação.
- ~~Dois validadores em contradição sobre o usuário técnico do Keycloak.~~ **Resolvido pelo Plano 01-30.** `validate-keycloak-realm.mjs` exigia exatamente 9 chaves em `realm.users[0]` e rejeitava `firstName`/`lastName`, que `016390b` (01-29) acrescentou ao realm para destravar o provider `VERIFY_PROFILE`, e que `validate-runtime-prerequisites.mjs:1290-1297` já exigia. O contrato foi ampliado para 11 chaves com valores pinados; o realm não foi alterado, porque o defeito estava no validador. Qual lado cedia não dependeu de julgamento: `01-03-PLAN.md:203` proíbe required action na conta técnica, e `VERIFY_PROFILE` é required action de provider, então o 01-29 respeitou o contrato de origem.
- ~~O reset local nunca executou `--apply`.~~ **Executado pelo responsável em 2026-08-17.** Removeu exatamente os 11 recursos da lista — 7 containers `infra-*` e 4 volumes `betufu-local-*` — com 0 falhas. Verificado depois: zero containers `infra-*` e zero volumes `betufu-local-*` restantes; total da máquina caiu de 36/23 para 29/19, exatamente os 11; `finvise-postgres` e as demais stacks de terceiro intactas; e a segunda execução converge para `(nenhum — nada a fazer)` sobre 29/29 e 19/19. O limite declarado desde o primeiro handoff está fechado: está provado que o reset remove o certo e só o certo.
- Reavaliar `GHSA-frvp-7c67-39w9` quando houver correção upstream compatível com a Matriz B, sem `npm audit fix`, `--force` ou downgrade silencioso.
- O provedor e a topologia S3 de homologação/produção continuam sem escolha; SeaweedFS não deve ser promovido silenciosamente além do ambiente local.
- Homologação e produção ainda precisarão fornecer origens e uma lista não vazia de proxies durante os planos de infraestrutura e implantação.
- A coleção `pendingOriginalIds` pode reter uma entrada após falha terminal de save; acompanhar o P3 de possível crescimento lento no caminho de erro antes de ampliar o lifecycle.
- Os beans opcionais de filtro e lifecycle podem degradar silenciosamente quando ausentes; a opcionalidade necessária aos contextos focais de teste deve ser reavaliada antes de novas integrações de segurança.
- `hasApprovedActivity` está efetivamente inativo porque os nomes de atributo ocultam o marcador; decidir se o método deve ser conectado ao contrato ou removido em manutenção futura.
- Os avisos de Mockito/Byte Buddy sobre agente dinâmico futuro, reconnect do Lettuce durante o teardown e conversão Git LF→CRLF permanecem não bloqueantes e não invalidaram as provas.
- A suite Angular completa do 03-05 ficou em 248/253 por cinco falhas historicas de `role-admin-page.component.spec.ts`; o foco O3/perfil e o navegador Arena/Console estao verdes.
- Nenhum plano da Fase 1 assumiu os gatilhos `ADMINISTRATIVE`/`SECURITY`. `01-CONTEXT.md:75` delega essas regras a agentes posteriores, mas a delegação está sem destinatário; enquanto isso durar, o `SessionInvalidationListener` do 01-07 permanece sem consumidor em produção.
- O refresh token vive em `InMemoryOAuth2AuthorizedClientService`, que não sobrevive a restart nem é compartilhado entre réplicas, ao contrário da sessão que o 01-06 moveu para o Redis. Aceito como pendência conhecida, a resolver antes de existir qualquer disparo real de revogação; registrado com os demais P3 em `01-22-PLAN.md`.
- ~~Cinco regressões negativas dos validadores Node inertes em checkout Windows.~~ **Medido no 01-31: era uma, não cinco.** Os dois validadores que mutam texto têm guarda de fixture-mudou, então mutação inerte falha em vez de passar escondida, e o terceiro muta objeto JSON. A única inerte foi corrigida por `replaceAcrossLines`, que casa `\r?\n` e preserva o CRLF real da fixture. **A causa raiz permanece:** `core.autocrlf=true` segue ativo e `.gitattributes` não cobre `backend/src/main/java/**`, portanto mutação multilinha nova sobre fonte Java deve usar aquele helper. A contagem de cinco no `CLAUDE.md` está superestimada e merece correção.
- O Playwright 1.62.1 e o Chromium 1234 do 01-21 foram fixados, inspecionados e validados; qualquer atualização, nova instalação ou aquisição de navegador continua sujeita a checkpoint próprio.
- `GET /api/v1/session`, `POST /api/v1/session/logout` e o schema RFC 9457 de Problem Details (Plano 01-12) existem em runtime sem constar em `backend/openapi/betufu.yaml`, e nenhum validador detecta a divergência. Bloco atribuído ao **Plano 01-26, Task 2** (Wave 23), que já declara o yaml e a árvore gerada e verifica por `scripts/validate-phase1.mjs --contract` — script ainda inexistente. Divergência conhecida, atribuída e sem detector até lá.
- `/ws` e o gate STOMP do 01-08 existem no backend mas não têm rota no `frontend/proxy.conf.json` — `validate-tracer.mjs:775-776` congela o proxy nos três segmentos BFF — nem cobertura E2E em navegador. Atribuído à Phase 7 (`ROADMAP.md:245-250`, `CHAT-01`/`CHAT-02`), que entrega o roteamento `/ws`, o repasse explícito de `Origin` (necessário para que o `setAllowedOrigins` não fique inerte contra CSWSH) e a conexão STOMP real. Sem detector até lá.
- Registrar o `post_logout_redirect_uri` no realm do Keycloak. Condição da ratificação do `id_token_hint`: sem isso o redirect pós-logout não é honrado e o usuário para na página genérica do provedor.
- `mfaAuthenticated` permanece no `SessionContractDto` sem ser publicado pela API; decidir a regra `acr`/`amr` antes de existir consumidor.
- `END_SESSION_UNAVAILABLE` conflacia principal inesperado, registro inexistente e discovery sem `end_session_endpoint`; fail-closed e sanitizado, mas um operador não distingue os três.
- Snapshot de sessão corrompido faz `GET /api/v1/session` responder 500 em vez de contrato não autenticado. Deliberado: uma leitura pura não deve encerrar sessão como efeito colateral.

### Bloqueios e pontos de atenção

 - **Wave 20 resolvida em 2026-09-02:** o 03-17 foi implementado, certificado, integrado, limpo e publicado. A base da Fase 3 está certificada; os gaps 03-21 a 03-27 permanecem pendentes antes da Fase 4.

- Na execução da Phase 2, os checkpoints sobre validade, ciclo de vida da matrícula/HMAC e matrícula reemitida permanecem bloqueantes; nenhuma decisão foi tomada implicitamente no planejamento.
- ~~Antes do planejamento da Phase 3: esportes e categorias exatos do piloto e tabelas de fontes confiáveis precisam ser confirmados.~~ **Resolvido em 2026-08-26:** a Fase 3 adotou catálogo esportivo genérico, registrou as modalidades conhecidas apenas como dados iniciais e definiu página oficial, regulamento e boletins oficiais como fontes autoritativas; detalhes de 2026 serão dados operacionais, não bloqueio arquitetural.
- Antes do planejamento das Phases 4–6: políticas em aberto sobre odds, créditos, correções de liquidação e ranking com verificação expirada precisam de decisão explícita ou configuração.
- Antes do lançamento público: escolha de nuvem/CI e fluxos legais, de privacidade, retenção e direitos dos titulares precisam ser encerrados.
- Antes da divulgação pública: decidir se o repositório será transferido para uma organização própria da BETUFU; essa mudança não é necessária para executar o MVP e não oferece anonimato absoluto.
- **Os gates herdados da Wave 7 para o 02-29 foram concluídos na Wave 9.** A cadeia D-40 real foi exercitada de ponta a ponta, e a validação criptográfica do Bearer cobriu assinatura forjada, token expirado, issuer incorreto, audience incorreta, papel global homônimo recusado e `realm-management/manage-realm` aceito. O rollout só ocorreu depois do sinal literal `ativar rollout D-40 02-29`, seguido da reconciliação idempotente do realm e das suítes agregadas.
- **Prova de recurso desatualizado no classpath.** Durante o rebase, um `verify` acusou 3 falhas e 41 erros que não vinham do commit: `backend/target/classes/db/migration` guardava a `V6` de um build anterior, e o Flyway lê migrations do classpath, não de `src`. É a irmã da armadilha do `.class` desatualizado, um nível acima. Use `clean` ao medir árvores diferentes no mesmo diretório.
- A Wave 19 foi concluída com o 01-24, único plano da onda; a Wave 20 está liberada e começa pelo 01-12.
- **A suíte Node está integralmente verde: 481/481.** O gate agregado do 01-16 não é mais bloqueado por nenhum detector Node — `validate-keycloak-realm` foi destravado pelo 01-30 e a última falha, de CRLF, pelo 01-31.
- A allowlist do 01-24 declarava 4 paths; `STATE.md` e `ROADMAP.md` foram ratificados pelo responsável no fechamento, para que o registro do projeto não continuasse descrevendo o 01-11. Ampliação registrada aqui, e não no SUMMARY, porque o SUMMARY estava sob `PASS` externo e editá-lo o invalidaria.

## Itens adiados

| Categoria | Item | Status | Adiado em |
|-----------|------|--------|-----------|
| v2 | Notificações, janela formal de contestação, esportes ampliados, aplicativos nativos e MFA/passkeys para usuários | Adiado no roadmap do MVP | 2026-08-01 |

## Session Continuity

Last session: 2026-09-04T00:23:19-03:00
Stopped at: Phase 3 plan 03-25 complete; awaiting 03-26 checkpoint
Resume file: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-26-PLAN.md

## Accumulated Context

### Roadmap Evolution

- Phase 3 edited: Fase 3 reconciliada após discussão: domínio esportivo genérico, vínculo por HMAC sem comprovante, política de atleta permissiva no MVP e confirmação de resultado pela mesma pessoa.
- Phase 3 edited: A reatribuição histórica de PRIV-02 à Fase 3 foi superada: comprovantes documentais permanecem na v2; a Fase 3 usa matrícula transitória e HMAC sem arquivo.
- Phase 03.1 inserted after Phase 3: Convergência do Contrato de Diagnóstico (URGENT)
- Phase 3 gap closure added: seven bounded plans (03-21 through 03-27) must be implemented and certified before Phase 4.

## Performance Metrics

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 03 P01 | 20 min | 2 tasks | 3 files |
| Phase 03 P02 | 5h13 elapsed | 2 tasks | 15 snapshot paths |
| Phase 03 P18 | 7h28 elapsed | 3 tasks | 23 planning paths |
| Phase 03 P03 | multi-session | 5 tasks | 54 snapshot paths |
| Phase 03 P04 | multi-session | 6 tasks | 52 allowlist paths |
| Phase 03 P05 | multi-session | 6 tasks | 52 allowlist paths |
| Phase 03 P06 | multi-session | 5 tasks | 32 allowlist paths |
| Phase 03 P10 | 3h 33m | 5 tasks | 32 files |
| Phase 03 P11 | multi-session | 5 tasks | 52 allowlist paths |
| Phase 03 P12 | multi-session | 5 tasks | 42 allowlist paths |
| Phase 03 P13 | multi-session | 5 tasks | 32 allowlist paths |
