---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
status: researched-updated-after-discussion
created: 2026-10-04
baseline: 21edd3b9d58d
research_scope: repository-and-installed-configuration
---

# Phase 7 Research

## Summary

Pesquisa local para planejar comunidade moderada e prontidao do piloto. As
decisoes do responsavel estao em 07-CONTEXT.md. Recomendacoes deste documento
nao autorizam implementacao nem substituem escolhas de produto pendentes.

RepoWise foi usado antes das buscas nativas pelo orquestrador e pelo pesquisador
GSD em contexto separado. HEAD confirmado: 21edd3b, master. Nao foram executados
testes materiais, chamadas de negocio, migrations, Docker ou operacoes Git.
Nao houve consulta web; comportamento de APIs novas deve ser confirmado durante
a implementacao com a versao instalada e testes discriminantes.

O pesquisador retornou evidencias inline depois de uma espera limitada pelo
orquestrador; este documento foi consolidado pelo orquestrador. Nao houve
research-plan/cache ou parecer independente dos futuros planos.

## Standard Stack And Existing Foundations

| Area | Evidence | Planning implication |
|---|---|---|
| Java/Spring | backend/pom.xml: Java 25, Boot 4.1.0, Modulith 2.1.0 | Usar MVC/JDBC e testes PostgreSQL reais existentes; sem novo broker |
| Angular | frontend/package.json: Angular 22.1.7, TypeScript 6.0.3, RxJS 7.8.2 | Standalone, zoneless, Signals/stores e adaptadores de API |
| Testing | Vitest 4.1.11 e Playwright 1.62.1; infraestrutura Maven/Testcontainers | Nao instalar outra stack de testes |
| Chat | backend/src/main/java/br/com/betufu/chat/package-info.java | Modulo ja declarado; mensagens/servicos ainda precisam ser criados |
| Enrollment | verification/EnrollmentEligibility.java | SEM_VINCULO, ATIVO, LIBERADO; porta de chat deve projetar somente permissao de escrita |
| Roles | identity/OperationalRole.java | MODERADOR tem escopo obrigatorio; revalidar concessao em todo comando |
| Settlement | settlement/SettlementReadPort.java | Progresso COMPLETE nao inclui horario publico de conclusao |
| Audit | Trilha existente e padroes append-only das fases anteriores | Reutilizar contrato publico de auditoria; nunca abrir evidencia ao DTO publico |
| CI | .github/workflows/quality.yml | ESLint e Spotless apenas; nao considerar gates agregados ja entregues |

Node/npm do PATH observado pelo pesquisador divergem dos pins da CI e do
packageManager. Nao atualizar dependencias para resolver isso: usar wrappers e
ambiente aprovado. Comandos reais do frontend sao generate:api e
generate:api:check, nao openapi:generate.

## Architecture Patterns And Integration Points

1. Contratos de dados que o chat precisa pertencem ao consumidor `chat`.
   Identity, verification, sports e settlement implementam essas portas em seus
   proprios adaptadores internos. Nenhum import interno atravessa modulo.
   Analogos: verification/internal/RankingEnrollmentAdapter.java e
   sports/internal/settlement/SportsResultForSettlementAdapter.java.
2. Dados publicos devem limitar-se a mensagem de texto segura, apelido permitido,
   IDs publicos opacos, horario e marcador de moderacao. Nao transportar email,
   HMAC, status privado de matricula, identidade de denunciante, motivo interno
   detalhado ou conteudo original removido. GET deve montar uma projecao segura,
   nao serializar a entidade persistida.
3. Uma porta de elegibilidade nao retorna a entidade de matricula; um booleano
   ou estado publico suficiente evita acoplamento e exposicao. Roles de moderador
   nao concedem permissao para apostar nem acesso a saldo.
4. SportsEventService ja registra suspensao versionada de partida; OddsMarketService
   ja registra transicoes. O kill-switch deve compor capacidades existentes,
   nao criar um segundo estado esportivo conflitante ou apagar fatos.
5. backend/openapi/betufu.yaml governa REST; candidate/digest/inventario devem
   ser revisados antes da geracao oficial do cliente Angular. Nao editar
   frontend/src/app/api/generated manualmente. Problem Details sanitizado e
   guardas existentes continuam aplicaveis.

## Transport Recommendation And Trade-offs

Recomendacao inicial de polling SUPERADA pela escolha D-27: aviso do servidor
quase imediato. Propor SSE usando MVC/BFF existentes para entrega unidirecional
e REST para comandos, sem novo broker. Essa recomendacao nao aprova protocolo:
RT-01 ainda exige WebSocket/STOMP. Comparar SSE/STOMP e ratificar/reconciliar
RT-01 e garantias IDEN-04 antes de implementar. Nao manter dois transportes
de negocio em paralelo. Conexoes persistentes exigem reconexao/backpressure,
origem/autorizacao, expiracao e revogacao reais e capacidade medida.

Consulta REST paginada/cursor continua para recuperacao inicial/reconexao, nao
para substituir o push escolhido por ciclos periodicos. Nao criar conexoes
duplicadas ao navegar; liberar stream no logout/destruicao/troca de conta.
SLO de latencia ainda nao aprovado; tres segundos limita escrita, nao leitura.

Nao buscar apenas mensagens de ID maior: moderacao de uma mensagem antiga e
mudanca do estado do canal precisam chegar a quem ja a carregou. Usar um cursor
de alteracoes/projecao capaz de substituir a mensagem pelo marcador publico.
Testar paginacao concorrente, reconexao, refresh, deduplicacao e mensagem removida
sem reaparecimento em cache.

RT-01 exige WebSocket/STOMP hoje. A permissao do responsavel para SSE/polling
requer reconciliacao documental explicita antes de declarar RT-01 concluido.
IDEN-04 contem STOMP CONNECT: aplicar garantias equivalentes de sessao/origem e
CSRF em comandos, sem copiar uma protecao de protocolo que nao sera usado.
Na pesquisa inicial REQUIREMENTS.md nao foi alterado. A reconciliacao posterior
alterou somente CHAT-01/05 e Out of Scope; RT-01 e IDEN-04 continuam intactos.

platform/session/ValidActivityFilter.java tem allowlist restrita. Polling de
fundo nao deve prolongar indefinidamente a sessao. Testar expiracao real no store,
logout e revogacao de papel sem injetar autenticacao no navegador.

## Channel Lifecycle And Append-only Facts

Abertura deriva de publicacao da partida, inclusive partidas ja publicadas na
implantacao; rascunhos nao geram canais publicos. A consulta pode recuperar
projecoes perdidas, mas nao inventar fato esportivo nem expor rascunho.

SettlementReadPort.CaseProgress nao possui completedAt. SettlementService
termina o processamento depois de percorrer os palpites; zero palpites tambem
chega a COMPLETE. SettlementRepository.complete grava updated_at quando
completed_units = total_units, mas isso nao e um fato imutavel publico de
liquidacao definitiva. Nao usar acceptedAt, scheduledAt ou polling time como
substituto desse marco.

Propor porta consumer-owned para o marco de conclusao por evento/outcome vigente,
com ID e horario autoritativos. Precisam ser decididos: evento sem mercado,
sem palpites, mercados multiplos, cancelamento, correcoes pendentes e falhas
parciais. PENDING/RUNNING/FAILED nao iniciam o prazo de fechamento.

O fato de fechamento permanente deve sobreviver a retries, correcao esportiva,
republicacao, reconstrucoes e restauracao. Uma reliquidacao posterior nao pode
reabrir canal encerrado. Clock injetavel e checagem autoritativa a cada escrita
evitam depender da pontualidade de um job.

Migrations V39+ devem reservar numeros durante a decomposicao, sem alterar V1-V38.
Separar fatos append-only de mensagens/denuncias/acoes e projecoes reconstruiveis
de canal, sancao ou limiter. Grants e triggers PostgreSQL devem impedir UPDATE/
DELETE dos fatos pelo usuario de runtime. Nao confundir append-only com retencao
infinita; destino juridico de originais e backups continua pendente.

## Writing, Spam And Moderation Safety

- Regra ATIVO e autenticacao sao revalidadas no servidor dentro da tentativa de
  escrita. Visitante pode ler sem receber privilegio de escrita. Administrador
  nao ganha matricula por possuir papel.
- Texto apenas: limite autoritativo de 280 caracteres, definicao Unicode
  consistente entre Java e Angular, rejeicao de vazio/controles indevidos e
  renderizacao por interpolacao/textContent, nunca innerHTML confiado. Sem nova
  biblioteca de sanitizacao por inferencia.
- Limite por usuario de tres segundos precisa ser global entre canais e resistir
  a chamadas concorrentes. Propor estado auxiliar transacional por usuario;
  idempotencia vincula ator/chave/payload e nao conta retry como segunda mensagem.
  Redis pode auxiliar, nao substituir integridade de fatos PostgreSQL.
- Denuncias usam enum fechado e referencia a mensagem real. Idempotencia,
  autorizacao e acesso restrito evitam duplicidade e exposicao do denunciante.
- Mute e imediato/pessoal e salvo na conta, sincronizado (D-21). Servidor retorna
  apenas preferencias do titular; outros leitores nao mudam. Limpar caches
  privados no logout. Escopo entre canais e estrategia de conflito/falha precisam
  de contrato; nao guardar IDs privados em storage do navegador por inferencia.
- Remocao grava acao imutavel e marcador publico, mantendo original restrito.
  Cursor/estado de cache deve invalidar a visao anterior. Original nao deve
  reaparecer via paginas antigas, stream/polling ou erros.
- Suspensoes 24h/7dias/ban globais sao exclusivas de ADMINISTRADOR_GERAL;
  moderador remove/analisa no canal autorizado. Sistema sugere proximo nivel,
  admin confirma; caso duplicado/anulado nao avanca, usuario pede revisao na conta.
  Desbanimento e unidade de caso precisam de contrato sem redecidir D-17..20.
  Nenhuma delas altera creditos, ranking ou historico de palpites.
- Kill-switch de conta/partida/mercado/canal precisa de alvo, motivo, ator,
  idempotencia e retomada auditavel. Bloquear comandos novos sem desfazer
  liquidacoes ou apagar fatos; efeito exato nos comandos em curso e checkpoint.

## Pilot Readiness Is Not Just Functional Testing

OPS-04 cobre bancos BETUFU e Keycloak e objetos privados. infra/compose.yaml e
infra/postgres/init-databases.sh mostram separacao de bancos/usuarios e S3 local
SeaweedFS. Copiar somente o banco BETUFU nao comprova recuperacao de identidades
e objetos. Restaurar somente em recursos nomeados/isolados, com manifesto,
hashes, versoes, integridade e evidencias do tempo de recuperacao/perda de dados.
Nao restaurar sobre volume do responsavel nem usar prune/reset amplo.

Antes de operacao publica, aprovar metas RTO (tempo para recuperar) e RPO (perda
maxima de dados), agenda/retencao, criptografia/chaves, destino, acesso e
procedimento de restauracao. Teste local prova mecanismo, nao cumprimento de
metas de nuvem ainda inexistentes. Segredos de Keycloak/backups nao entram em logs.

PRIV-03 exige checklist de publicacao incluindo builds, imagens, documentos e
historico publico; identificadores a bloquear e excecoes legitimas precisam ser
aprovados. PRIV-04 exige identidade institucional legitima, dominio/email/infra
definidos sem sugerir vinculo oficial UFU ou ocultar dados legalmente exigidos.
PRIV-05 exige revisao juridica e fluxos de direitos/retencao por classe, incluindo
atleta sem conta, chat original moderado, ledger, auditoria, logs e backups.
Esses gates nao podem ser substituidos por mocks, checkbox ou texto generico.

TEST-04 conserva um gate de carga/SLO/taxa maxima de erro. Corridas PostgreSQL da
Fase 6 provam integridade limitada, nao capacidade do piloto. Testes de carga
precisam de perfil e ambiente aprovados; nao atacar servicos externos.

CI deve executar testes Java/Node/Angular, migrations em PostgreSQL, Modulith,
OpenAPI anti-drift, build, privacidade e scan de seguranca com severidade
bloqueante aprovada. Actions, ferramentas externas e dependencias novas
necessitam aprovacao especifica; seguranca fail-closed nao pode ser um job
continue-on-error. CI green nao equivale a liberacao juridica do piloto.

## Validation Architecture

Infraestrutura existente: JUnit/Testcontainers/PostgreSQL, Modulith/ArchUnit,
Vitest/Angular, Node tests e Playwright real com runner isolado da Fase 6.
Novos testes devem falhar no comportamento inseguro, nao somente procurar strings.

| Behavior / requirement | Planned discriminating evidence |
|---|---|
| CHAT-01/02, RT-01 | API real/public read/ATIVO write, historico paginado, cursor de remocao, expiracao/logout/origem, transporte ratificado |
| CHAT-03/04 | Motivo fora do enum rejeitado; mute imediato nao afeta segunda sessao; privacidade do denunciante |
| CHAT-05/06 | Moderador fora do escopo negado; original preservado mas ausente em DTO/cursor; 24h/7d/ban com clock controlado |
| INTE-05 | Pausa/retry/retomada por alvo; comando concorrente nao ultrapassa bloqueio; ledger/odds/palpites historicos intactos |
| OPS-04 | Restauracao real isolada de bancos/objetos, hashes/constraints/Flyway/contagens e metas aprovadas medidas |
| PRIV-03/04/05 | Fixtures negativas de PII/publicacao, evidencias juridicas/operacionais e fluxos de direitos exercitados |
| TEST-03/05, UX-01/02 | Playwright sem mocks de negocio, desktop/mobile, teclado/foco, loading/empty/error/readonly/suspension; CI negativa e agregado |
| TEST-04 launch gate | Perfil de carga e SLO/taxa erro aprovados, medidos em ambiente isolado |

Comandos futuros existentes, nao executados nesta pesquisa:

    node scripts/maven.mjs -f backend/pom.xml verify
    node --test scripts/*.test.mjs
    npm --prefix frontend test -- --watch=false
    npm --prefix frontend run generate:api:check
    npm --prefix frontend run lint
    npm --prefix frontend run build:production
    git diff --check

Nomes/classes/scripts focais serao definidos nos PLANs; nao executar -Dtest para
classes inexistentes nem atribuir passed a validacao draft. Capturar totais dos
XML Maven para evitar duplicacao do resumo pelo wrapper. A infraestrutura real
da Fase 6 serve de analogia, nao permite mutar volumes ou apostas do responsavel.

## Proposed Decomposition Areas (Not Execution Plans)

Proposta inicial tinha dez planos/44 tasks e seis allowlists acima de 15 paths.
Responsavel escolheu subdivisao, nao excecao, e ampliou para global, mute
sincronizado, revisao de sancao e area Privacidade. Nova numeracao/subdivisao
fica no 07-DECOMPOSITION.md; mapa antigo nao autoriza executar 07-02..07-10.
Cada nova fatia inclui candidatos/gerados no limite de 15 paths, teste
discriminante e contratos publicos. Foundations tecnicas explicitas sao
necessarias onde schema/API/geracao caberiam mal em um tracer unico; nao alegar
uma jornada Angular certificada por teste HTTP. Contrato UI continua gate.

## Discussion Delta And Source Grounding

- Global exige sessao em toda leitura/stream e cache privado; partida conserva
  leitura publica. PUBLIC endpoint nao pode aceitar global via tipo/ID alternativo.
- Escrita com fila volatil usa chave idempotente criada no comando original,
  sequenciamento/backoff respeitando 3s, revalidacao em cada tentativa e purge no
  logout/troca de conta. ACK perdido devolve recibo anterior, nao nova mensagem.
  Pending nao deve desaparecer por falso sucesso; erro terminal nao auto-reenvia.
- Marco depende de todos mercados/palpites e outcome vigente; sem palpites usar
  resultado confirmado. Correcao so reconta se concluir antes de fechar. Nao usar
  updated_at mutavel como unico fato; job atrasado nao permite escrita vencida.
- Kill-switch adotado: admin apenas; chat readonly; mercado/partida negam novos
  palpites; conta nega mensagens/palpites mas conserva consulta/revisao. Nao
  instalar bloqueio generico em sessao, settlement ou rights por inferencia.
- RPO <=3600s, RTO <=14400s, backup 30d, carga de referencia 50 simultaneos e
  zero vulnerabilidades CONFIRMADAS abertas. Prazos comuns 90d/evidencias 180d
  sao propostas juridicas; auditoria minima da sancao/retencao legal continua gate.
- Restaurar snapshot anterior a uma exclusao exige aplicar registro independente
  e vigente de supressoes ANTES de servir dados. Reaplicar o registro velho que
  veio no mesmo snapshot nao prova privacidade. Chaves/destino ainda pendentes.
- frontend/scripts/generate-api.mjs possui allowlists PUBLIC_GENERATOR_API_TAGS,
  PUBLIC_GENERATOR_MODELS e APPROVED_GENERATED_MANIFEST; cada incremento de
  contrato precisa prever script/testes na allowlist, nao so betufu.yaml.
  executeGenerateApi suporta mode=candidate e candidateDirectory; nao inventar
  renderizacao em memoria. Candidate usa configuracao/inputSpec aprovados:
  preparacao de API candidata sem substituir contrato governado exige mecanismo
  comprovado no gerador, ou bloco explicito para o adicionar antes da primeira API.
  Inventario exato e hashes devem anteceder publicacao/generated oficial.
- Nenhum arquivo SQL, API, gerado ou runner criado por esta revisao documental.

## Decision Coverage And Contract Conflicts

D-01..40 e UI-D01..07 em CONTEXT sao fonte das escolhas; ligar comportamento
a testes. Na reconciliacao documental posterior, CHAT-01/05 e Out of Scope
foram atualizados para D-01/15/17, sem marcar entrega. RT-01/STOMP permanece
pendente de decisao e documentacao separadas, nao resolvido aqui.

Preferencia de regras nao existia nos 32 planos iniciais. Proposta adicional
07-33..35 cobre backend/API/UI real. Guardar dispensa somente no navegador nao
satisfaz dois dispositivos; propor registro minimo por conta no modulo chat
evita confundir com mute ou consentimento legal. Identity e alternativa possivel;
storage/modulo/FK/retencao so serao escolhidos no primeiro checkpoint de 33.
V47 depende de V46 em 27; API 34 depende de 25 para serializar YAML/gerador;
UI 35 depende de 23 para serializar chat-panel. Nenhuma arquitetura implementada.

## Blocking Decisions For Subsequent Planning/Execution

1. Contrato visual de chat/moderacao/pausas e jornadas mobile/desktop (gate UI).
2. RT-01 e selecao server-push sem enfraquecer IDEN-04; preferencia de regras/falhas/retencao em 33.
3. Timestamp/porta do marco, cancelamento sem placar e compensacao pendente.
4. Unidade de caso, desbanimento, alcance/conflito de mute sincronizado.
5. Backup de tres substratos, scheduler/chaves/destino, carga/SLO/erro/provedores;
   RPO/RTO, 50 simultaneos e bloqueio de toda vulnerabilidade ja escolhidos.
6. Revisao juridica/retencao/direitos e identidade institucional legitima.

Essas pendencias nao anulam as decisoes consolidadas. Devem virar checkpoints
explicitos e impedem declarar prontidao publica sem evidencia.
