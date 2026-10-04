---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
status: discussed-awaiting-plan-review
created: 2026-10-04
source: explicit-owner-decisions-and-conversational-discussion
---

# Phase 7: Comunidade Moderada e Prontidao do Piloto - Context

## Phase Boundary

Chats por partida E sala global no piloto, moderacao e prontidao operacional.
O responsavel ampliou expressamente o escopo para incluir o global. Sem efeitos
financeiros, escrita anonima ou dependencia oficial UFU. Fase 6 certificada 8/8.

Autorizacao vigente: consolidar planejamento e subdividir planos, atualizar
STATE/ROADMAP e apresentar proposta. Nao autoriza codigo, migrations, testes
materiais, dependencias, Docker, stage/commit/push, worktree ou Antigravity.
Manter GSD interativo, auto_advance=false e commit_docs=false.

<decisions>

### Regras originais refinadas pela discussao

- **D-01:** Escrita exige sessao e matricula ATIVO. Partida aceita leitura
  publica/inativa. Global tem regra de leitura D-15. Verificacao permanece o
  vinculo autodeclarado existente; sem comprovante ou validacao oficial UFU.
- **D-02:** Partida abre ao publicar e fecha escrita permanentemente duas horas
  apos marco definitivo. D-30/31 especificam marco/correcao; backend autoritativo.
- **D-03:** Uma mensagem por usuario a cada tres segundos e maximo 280 caracteres;
  revalidacao no servidor, concorrencia/retries, texto apenas e anti-XSS.
  Unidade Unicode e alcance exato do limiter ainda exigem ratificacao tecnica.
- **D-04:** Atualizacao integrada a Modulith/Angular Signals. D-27 refina a
  escolha inicial SSE/polling: experiencia server-push quase imediata.
  Protocolo e reconciliacao de RT-01/STOMP ainda nao aprovados.
- **D-05:** Motivos fechados: DISCURSO_DE_ODIO, SPAM, OFENSA, ASSEDIO, FRAUDE.
- **D-06:** Mute pessoal imediato, sem avisar alvo ou alterar outros leitores;
  persistencia multi-dispositivo escolhida em D-21.
- **D-07:** Remocao exibe marcador, preserva original restrito e auditoria;
  nenhum original na API comum, paginacao, cursor, stream ou HTML escondido.
- **D-08:** Sancoes 24h/7dias/banimento de CHAT, sem alterar saldo/ranking/historico;
  D-17..19 especificam autoridade, progressao e revisao.
- **D-09:** Pausa/retomada auditavel de conta/partida/mercado/canal, per INTE-05;
  alcance definido em D-22..25.
- **D-10:** Backup automatico e restore real de BETUFU + Keycloak + objetos
  privados. D-32/38 definem metas/retencao; provedor e chaves nao escolhidos.
- **D-11:** CI seguranca/anti-drift deve bloquear gates falhos. Usar ferramentas
  aprovadas; sem pentest externo ou dependencia nova por inferencia.
- **D-12:** Portas publicas consumer-owned de chat, adaptadores nos produtores
  identity/verification/sports/settlement. Nunca import interno cruzado.
- **D-13:** Mensagens/denuncias/moderacao append-only, Flyway V39+, projecoes
  reconstruiveis. Nao alterar V1-V38, prediction, odds, ledger ou settlement
  historicos. Append-only nao autoriza retencao infinita: expurgo segregado
  somente apos politica juridica e aprovacao especifica.
- **D-14:** Preservar docs/development.md, docs/local-seed.md e
  docs/manual-testing.md. Sem push. Cliente gerado so pelo script oficial,
  nunca editar frontend/src/app/api/generated/ manualmente.

### Sala global, moderacao e pausas

- **D-15:** Global incluido no piloto. Sessao obrigatoria para ler, mesmo
  matricula inativa; escrita ATIVO. Visitante nao recebe historico/global
  em HTTP/stream/cache. Global nao fecha por jogo. Login nao e confidencialidade.
- **D-16:** Pagina Comunidade com link no topo Arena; chat de partida no jogo.
  Visitante usa login contextual. Rotas finais candidatas ao contrato UI.
- **D-17:** Suspensao bloqueia escrita em TODOS os chats, mantendo leitura
  autorizada. Moderador analisa/remove no escopo e encaminha; somente
  ADMINISTRADOR_GERAL suspende globalmente. Sem punicao financeira.
- **D-18:** Sistema sugere 24h -> 7 dias -> ban; administrador confirma.
  Nova infracao comprovada avanca; denuncias duplicadas da mesma ocorrencia
  e punicoes anuladas nao avancam. Denuncia nao pune automaticamente.
  Unidade de caso/concorrencia devem impedir dupla sancao; sem reset inventado.
- **D-19:** Solicitar revisao na conta, com motivo/duracao/explicacao.
  Administrador mantem ou anula por nova decisao auditada, nunca DELETE.
  Revisao pendente nao anula automaticamente a punicao.
- **D-20:** Autenticado com acesso ao canal pode denunciar, inclusive inativo,
  suspenso da escrita ou conta pausada. Visitante faz login contextual.
  Denunciante privado; catalogo D-05 fechado.
- **D-21:** Mute salvo na conta, sincronizado em dispositivos, efeito local
  imediato e sem notificar alvo/alterar outros leitores. Logout limpa estado
  privado. Alcance entre canais e falha de sincronizacao ainda exigem contrato;
  nao inventar punicao ou identificador privado no armazenamento do browser.
- **D-22:** Somente ADMINISTRADOR_GERAL pausa/retoma qualquer alvo, registrando
  ator/motivo/horario. Moderador nao recebe kill-switch implicitamente.
- **D-23:** Chat pausado: leitura autorizada/historico continuam, envios bloqueados,
  aviso textual. Retomada nao vence deadline nem suspensao pessoal vigente.
- **D-24:** Mercado pausado bloqueia novos palpites; partida pausada bloqueia
  novos palpites em TODOS seus mercados. Chat tem pausa independente.
  Liquidacoes/devolucoes/correcoes continuam; nada e cancelado retroativamente.
  Retomada nao reabre mercado vencido.
- **D-25:** Conta pausada bloqueia novos palpites/mensagens, mantem consulta
  de saldo/historico/punicoes e pedido de revisao; palpites aceitos processam.
  Nao substitui revogacao de sessao comprometida. Outros comandos (perfil,
  grupos, direitos) nao foram bloqueados por esta escolha.

### Experiencia de conversa e ciclo da partida

- **D-26:** Autoscroll so no fim; lendo historico preservar posicao e mostrar
  Novas mensagens. Clique leva ao fim sem roubar foco; global/partida/mobile.
- **D-27:** Aviso do servidor quase imediato; nao escolher polling periodico.
  Tempos exemplificados nao sao SLO aprovado ou medicoes. Protocolo permanece gate.
- **D-28:** Envio offline Aguardando conexao, retry automatico ao reconectar.
  Revalidar sessao/ATIVO/limiter/sancao/pausa/deadline. Mesma chave/ator/payload
  devolve mesmo recibo se ja aceito, nao duplica. Rejeicao terminal nao faz
  retry infinito; enviado so apos ACK autoritativo.
- **D-29:** Fila apenas na memoria da aba; fechar/refresh perde pendentes.
  Logout/troca de conta limpa fila/callbacks; sem storage/service worker ou
  envio pela nova persona. Historico confirmado permanece no servidor.
- **D-30:** Com palpites: resultado confirmado e processamento completo de
  TODOS aceitos, em todos mercados, sem parcial/falha. Sem palpites/mercado:
  marco e confirmacao definitiva do resultado. Duas horas depois, escrita
  fecha permanentemente; historico permanece consultavel conforme retencao.
  Cancelamento sem placar e compensacao pendente exigem definicao no gate.
- **D-31:** Correcao CONCLUIDA enquanto chat aberto reconta duas horas desde
  conclusao; se ja encerrou, nunca reabre, mesmo se correcao iniciou antes.
  Deadline autoritativo/clock/lock protegem envio, fechamento, replay e restore.

### Metas do piloto e privacidade

- **D-32:** Metas RPO <= 3600s (uma hora), RTO <= 14400s (quatro horas).
  Provar recuperacao utilizavel dos tres substratos; teste local nao prova
  disponibilidade na nuvem inexistente.
- **D-33:** Referencia de carga: 50 usuarios simultaneos, chat/navegacao/palpites.
  Nao limitar cadastro ou declarar capacidade por escolha. Mix/duracao/ambiente,
  SLO/latencia/taxa maxima de erro ainda precisam aprovacao e medicao.
- **D-34:** TODA vulnerabilidade confirmada bloqueia piloto, incluindo low/moderate.
  Falso positivo exige demonstracao, nao suppress cosmetico. Scanner indisponivel/
  falho ou relatorio stale nao e zero findings. Gates/testes falhos bloqueiam.
- **D-35:** Historico comum: proposta 90 dias, sujeita a revisao juridica.
  Nao apagar evidencias/auditoria indistintamente. Expurgo/pseudonimizacao e
  permissao segregada so apos politica e dry-run aprovados.
- **D-36:** Original moderado restrito a admin geral e moderadores autorizados
  do canal para tratar caso, com acesso/horario registrado. API comum nao recebe.
- **D-37:** Conteudo de evidencias: proposta 180 dias APOS encerrar caso.
  Analise/revisao pendente impede contagem. Sujeita a revisao juridica.
  Registro minimo de decisao/progressao tem politica separada pendente;
  expirar texto nao pode apagar banimento/progressao vigente.
- **D-38:** Backup retido 30 dias, independente da frequencia exigida por RPO.
  Restore reaplica exclusoes/retencoes/supressoes antes de servir usuarios;
  testar exclusao posterior ao snapshot. Destino/chaves/scheduler/expurgo
  exigem aceite operacional; nao apagar recursos do responsavel.
- **D-39:** Area Privacidade na conta: pedir acesso/correcao/exclusao e acompanhar.
  Operacao verifica titular, analisa e responde; nao apagar automaticamente.
  PRIV-05 tambem cobre oposicao/pseudonimizacao, atleta sem conta, logs,
  ledger/auditoria/backups e revisao juridica: manter gates explicitos.
- **D-40:** Responsavel aprovou subdivisao e consolidacao APENAS documental.
  Allowlists incluem candidatos/gerados, <=15 paths sem esconder arquivos.
  Propostas precisam revisao; execucao/Git/banco/Docker/externos nao autorizados.

### Escolhas visuais confirmadas (UI-D01..07)

- **UI-D01:** Preservar identidade visual da Arena, sem redesign global.
- **UI-D02:** Comunidade em pagina propria com entrada no topo desktop/mobile.
- **UI-D03:** Lista compacta tipo Discord em ambos chats, apelido/hora sobre
  texto, alinhamento a esquerda e sem baloes.
- **UI-D04:** Mensagem propria discretamente distinta, com rotulo Voce.
- **UI-D05:** Regras em popup fechavel; botao manual Regras, Escape e foco.
- **UI-D06:** Primeira visita autenticada a Comunidade abre automaticamente;
  dispensa uma vez por conta sincroniza dispositivos, sem aceite juridico.
- **UI-D07:** Visitante no jogo tem Regras manual, sem abertura automatica.

</decisions>

## Open Decisions And Launch Gates

1. Escolher protocolo server-push, reconciliar RT-01/IDEN-04 em autorizacao
   documental separada para transporte (este bloco nao o decide), preservar origem,
   CSRF, sessao/TTL e revogacao. SSE e recomendacao, nao protocolo aprovado.
2. Aprovar 07-UI-SPEC e revisar mobile/desktop/teclado/foco. Detector instalado
   perde evidencia Angular no monorepo; block=false nao e dispensa.
3. Ratificar unidade Unicode/rate-limit, unidade de infracao, mute entre canais,
   cancelamento sem placar, compensacoes pendentes e marcos imutaveis produtores.
4. Revisao juridica e retencao por classe: 90/180 dias sao propostas; auditoria
   minima de sancao, logs, ledger, atleta sem conta e evidencias de direitos
   ainda sem prazos/regras. Fluxo para titular sem conta e autoridade de resposta.
5. Identidade institucional legitima, dominio/email/nuvem e identificadores/
   excecoes do checklist publico, sem vinculo oficial UFU.
6. Agenda/consistencia/destino/chaves/criptografia/acesso/expurgo de backups;
   perfil 50 usuarios com duracao/mix/SLO/taxa erro, scanner/Actions/pins/custos.
   Metas aprovadas nao sao evidencias de resultado.
7. Aprovacao dos planos revisados, digests SQL/API/inventario gerado e testes
   em recursos isolados antes de implementacao; aprovacao Git sempre separada.

## Required Requirement Coverage

CHAT-01..06, RT-01, INTE-05, PRIV-03..05, OPS-04, TEST-03, TEST-05, UX-01/02:
16 requisitos atribuidos a fase. TEST-04 conserva gate pre-lancamento.
Global/novas decisoes ampliam cobertura planejada; nao criar ou marcar requisitos
concluidos por inferencia. Reconciliacao documental autorizada em 2026-10-04:
CHAT-01 explicita leituras distintas e escrita ATIVO; CHAT-05 reserva sancao
global a ADMINISTRADOR_GERAL. Global moderado removido de Out of Scope.
IDs e checkboxes pendentes preservados; RT-01/STOMP nao modificado.

## Reconciliacao visual e preferencia proposta

07-08 declara HTML real do header e seu teste (15 paths); RouterLink ja existe
no .ts, que permanece fora do escopo. UI-D01..07 constam deste contexto e dos
planos/tests relevantes. 07-33..35 acrescentam proposta de persistencia/API/UI.
Proposta: modulo chat, V47 apos V46, registro minimo unico por conta e API
privada boolean-only. Nao usar preferencia de mute como dispensa de regras.
Nao e arquitetura aprovada: primeiro checkpoint de 33 compara alternativas e
exige ratificar storage, FK, privacidade/retencao e falhas de sincronizacao.
Falha de GET/POST e atualizacao de regras sao propostas, nao escolhas implicitas.
35 depende de 23 para serializar chat-panel; 31 depende de 35.
35 planos/102 tasks/17 ondas, 0 executados. Numeracao 33..35 e aditiva:
executar DAG/ondas, nao ordem numerica. UI-SPEC continua draft/checker pendente;
aprovacao de reconciliacao nao aprova arquitetura, execucao ou Git.

## Deferred Ideas

Mensagens diretas, escrita anonima, HTML rico, anexos, typing indicators, recibos
de leitura, recompensas, moeda/premio em grupo e fila offline duravel nao fazem
parte da fase. Chat GLOBAL deixou de ser adiado por ampliacao expressa de escopo.

## Existing Code Insights

- `backend/src/main/java/br/com/betufu/chat/package-info.java`: modulo ja
  declarado, ainda sem implementacao de chat.
- `identity/OperationalRole.java`: MODERADOR exige scopeRef; autoridade global
  ADMINISTRADOR_GERAL nao recebe escopo. Nao conceder acesso financeiro ao moderador.
- `verification/EnrollmentEligibility.java`: estados SEM_VINCULO, ATIVO,
  LIBERADO; projetar somente elegibilidade necessaria por porta de chat.
- `settlement/SettlementReadPort.java`: fatos atuais e progresso, sem horario
  publico de conclusao definitiva. Nao usar acceptedAt como horario de liquidacao.
- `.github/workflows/quality.yml`: jobs atuais de ESLint e Spotless apenas;
  prontidao exige ampliar evidencias, nao presumir pipeline completo.
- Fase 6: validacao passed; concorrencia limitada nao certifica carga de
  lancamento, RTO/RPO, auditoria WCAG completa ou revisao juridica.


## Preflight

- Date: 2026-10-04; checkout direto `master`; HEAD `21edd3b9d58d`.
- Worktree unico: `C:/Users/giova/Desktop/BetUFU`.
- Baseline: docs/development.md e docs/local-seed.md modificados pelo responsavel;
  docs/manual-testing.md nao rastreado. Nenhum arquivo esta no stage.
- SHA-256 development: DEAF6470ACF46C158D53EC66784916DD021CD8464336FF583EE780E0F9898FE8.
- SHA-256 local-seed: EF1DA4213B92E1DB908CCF370390C24E412C0891E3EF7F8D17C5D2092A925FCC.
- SHA-256 manual-testing: D4E1DF426A38B6C680A0A864997F01D5692B687308FD944F8EE6161491F3307F.
- RepoWise consultado antes das buscas nativas; STATE/ROADMAP sao documentos
  de alta frequencia de alteracao com um unico mantenedor: revisar diff e
  contadores, preservar snapshots historicos e nao sobrescrever o arquivo.
