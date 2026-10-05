# Roadmap: BETUFU

## Visão geral

Roadmap aprovado para o MVP da BETUFU. Ele entrega um piloto seguro e operado de forma independente para palpites em confrontos diretos das Olimpíadas UFU: primeiro estabelece a plataforma protegida e a participação verificada; depois oferece dados esportivos confiáveis e odds transparentes; em seguida habilita palpites com créditos virtuais, transforma resultados em uma competição social justa e, por fim, conclui uma comunidade segura, responsiva e pronta para lançamento. O usuário aprovou esta estrutura de sete fases em 2026-08-01.

## Fases

**Numeração das fases:**

- Fases inteiras representam o trabalho planejado do MVP.
- Fases decimais ficam reservadas para inserções urgentes após o planejamento.

- [x] **Phase 1: Fundação Segura da Plataforma** - Estabelecer a base modular, observável e segura da qual todas as capacidades do MVP dependem.
- [x] **Phase 2: Contas, Verificação e Operações Governadas** - Permitir identidades BETUFU protegidas e oferecer controles estritamente delimitados aos operadores autorizados.
- [x] **Phase 3: Experiência Esportiva do Piloto** - Publicar e operar um catálogo genérico de competições e edições, começando pela Olimpíada Universitária UFU 2026 e detalhando inicialmente confrontos diretos.
- [x] **Phase 03.1: Convergência do Contrato de Diagnóstico** - Alinhar o diagnóstico do backend ao vocabulário público `UP`, `DEGRADED` e `DOWN` sem alterar suas fronteiras.
- [x] **Phase 4: Mercados Pré-jogo e Odds Transparentes** - Produzir odds automáticas explicáveis, com substituições excepcionais submetidas a controles rigorosos.
- [x] **Phase 5: Créditos Virtuais e Palpites Protegidos** - Permitir que participantes elegíveis registrem palpites pré-jogo seguros e atômicos usando créditos virtuais.
- [x] **Phase 05.1: Convergência das Jornadas do Usuário** - Conectar sessão, conta, evento, mercado e palpite em jornadas descobríveis, coerentes e verificadas no navegador real.
- [x] **Phase 6: Resultados Confirmados, Rankings e Grupos** - Transformar resultados confirmados em liquidações corretas, classificação vitalícia e conquistas.
- [ ] **Phase 7: Comunidade Moderada e Prontidão do Piloto** - Entregar chat seguro por partida e uma experiência responsiva, privada e operacionalmente pronta para o piloto público.

## Detalhes das fases

### Phase 1: Fundação Segura da Plataforma

**Goal**: Operadores conseguem executar e evoluir uma plataforma BETUFU segura e modular, com sessão de navegador protegida, base de dados confiável e integração orientada por contratos.
**Mode:** mvp
**Depends on**: Nenhuma (primeira fase)
**Requirements**: ARCH-01, ARCH-02, ARCH-03, IDEN-01, IDEN-02, IDEN-03, IDEN-04, DATA-01, DATA-02, DATA-06, API-01, API-02, OPS-01, OPS-02, OPS-03, TEST-01, TEST-02
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um operador consegue iniciar o ambiente local com versões fixadas e consultar saúde, logs estruturados, métricas e dados de correlação sem expor segredos.
  2. A aplicação persiste os dados autoritativos por meio de migrações versionadas e rejeita dependências proibidas entre módulos nas verificações automatizadas.
  3. O navegador se comunica por uma API versionada e um cliente gerado, enquanto sessões BFF apoiadas pelo Keycloak mantêm tokens fora do armazenamento do navegador.
  4. Criação, renovação e encerramento de sessão, além das tentativas de conexão em tempo real, aplicam as proteções acordadas de OIDC, cookies, origem e CSRF.

**Plans**: 31/31 plans executed
Plans:
**Wave 1**

- [x] 01-01-PLAN.md — Validar a legitimidade da cadeia de suprimentos e os runtimes obrigatórios.

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md — Fixar Maven Wrapper, POM e launcher portátil da stack backend.

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 01-17-PLAN.md — Criar o scaffold Spring Boot/Modulith e a fronteira BFF OIDC.

**Wave 4** *(blocked on Wave 3 completion)*

- [x] 01-18-PLAN.md — Separar os profiles dev/homolog/prod sem segredos.

**Wave 5** *(blocked on Wave 4 completion)*

- [x] 01-03-PLAN.md — Subir a infraestrutura local real e configurar o realm e o tema do Keycloak.

**Wave 6** *(blocked on Wave 5 completion)*

- [x] 01-04-PLAN.md — Criar o workspace Angular 22/TypeScript 6 reproduzível.

**Wave 7** *(blocked on Wave 6 completion)*

- [x] 01-19-PLAN.md — Criar o host Angular e o roteamento build-specific.

**Wave 8** *(blocked on Wave 7 completion)*

- [x] 01-20-PLAN.md — Gerar o cliente OpenAPI inicial de forma transacional, recuperável e determinística.

**Wave 9** *(blocked on Wave 8 completion)*

- [x] 01-05-PLAN.md — Persistir o vínculo estável `(iss, sub)` no backend com PostgreSQL real.

**Wave 10** *(concluída; Gate 01-29 aprovado)*

- [x] 01-29-PLAN.md — Conectar o runtime local ao PostgreSQL e materializar o bootstrap XSRF SPA.

**Wave 11** *(concluída; Plano 01-06 encerrado com provas agregadas aprovadas)*

- [x] 01-06-PLAN.md — Implementar a sessão ociosa PT8H apoiada pelo Redis e a atividade válida.

**Wave 12** *(concluída; Planos 01-07 e 01-21 integrados com provas aprovadas)*

- [x] 01-07-PLAN.md — Revogar refresh token por OIDC e manter UserInfo opt-out.
- [x] 01-21-PLAN.md — Provar o tracer Angular somente em dev/homolog/harness e sua ausência production, após 01-29 e 01-06, com checkpoint humano próprio para Playwright.

**Wave 13** *(concluída; Plano 01-22 executado, revisado externamente e corrigido)*

- [x] 01-22-PLAN.md — Publicar o estado seguro da sessão e logout do dispositivo atual. O escopo
      OpenAPI foi retirado durante a execução e atribuído ao 01-26, Task 2.

**Wave 14** *(concluída; Plano 01-08 executado, revisado externamente com PASS e provas aprovadas)*

- [x] 01-08-PLAN.md — Proteger HTTP e STOMP CONNECT com CSRF, sessão e origem. O escopo de rota de proxy e E2E browser foi formalmente adiado para a Phase 7 (CHAT-01/CHAT-02).

**Wave 15** *(concluída; Plano 01-09 executado e revisado com PASS)*

- [x] 01-09-PLAN.md — Declarar os dez módulos Spring Modulith sem antecipar negócio futuro.

**Wave 16** *(concluída; Plano 01-23 executado, revisado com PASS e integrado)*

- [x] 01-23-PLAN.md — Impor gates Modulith/ArchUnit sobre os dez módulos.

**Wave 17** *(concluída; Plano 01-10 executado, revisado com PASS e integrado)*

- [x] 01-10-PLAN.md — Tornar Flyway, JPA e PostgreSQL autoritativos e delimitar Redis/S3.

**Wave 18** *(concluída; Plano 01-11 executado e validado com perfis daily/full)*

- [x] 01-11-PLAN.md — Entregar os perfis daily/full e o orquestrador cross-platform.

**Wave 19** *(concluída; Plano 01-24 executado, revisado em 4 rodadas e aprovado com PASS)*

- [x] 01-24-PLAN.md — Implementar o reset local confirmado e estritamente allowlisted.
      A allowlist do plano foi ampliada no fechamento para `STATE.md` e `ROADMAP.md`,
      com ratificação do responsável.

**Wave 20** *(concluída; Planos 01-30, 01-31 e 01-12 executados, revisados com PASS)*

- [x] 01-30-PLAN.md — Reconciliar o contrato do validador de realm com o artefato canônico.
      Plano criado durante a Wave 20 para corrigir drift herdado do 01-29, que deixava
      `validate-keycloak-realm.mjs` vermelho contra um artefato commitado e bloqueava o
      gate agregado do 01-16.

- [x] 01-31-PLAN.md — Fechar a última regressão negativa inerte da suíte Node.
      Criado durante a Wave 20 para eliminar o ruído de `core.autocrlf=true` antes do
      gate agregado do 01-16. Suíte Node passou a 481/481.

- [x] 01-12-PLAN.md — Padronizar Problem Details, correlação e logs estruturados.

**Wave 21** *(concluída; Plano 01-25 executado, revisado com PASS e integrado)*

- [x] 01-25-PLAN.md — Expor health e métricas operacionais mínimos e seguros.

**Wave 22** *(concluída; Plano 01-13 executado e testado com 167 testes Java)*

- [x] 01-13-PLAN.md — Modelar a projeção diagnóstica e seus probes tolerantes a falha.

**Wave 23** *(concluída; Plano 01-26 executado e revisado em 2 rodadas)*

- [x] 01-26-PLAN.md — Expor o diagnóstico não-prod e regenerar o cliente OpenAPI final.
      A allowlist foi ampliada três vezes com ratificação prévia: path `platform.diagnostic`,
      `scripts/validate-phase1.mjs` para o modo `--contract`, e os dois validadores cujos guards
      pinavam artefato em vez de propriedade.

**Wave 24** *(concluída; Plano 01-14 executado e testado com fontes locais)*

- [x] 01-14-PLAN.md — Implementar tokens, tema e fontes/ícones auto-hospedados.

**Wave 25** *(concluída; Plano 01-27 executado e testado)*

- [x] 01-27-PLAN.md — Montar a estrutura inicial autenticada e seus testes focais.

**Wave 26** *(concluída; Plano 01-15 executado e verificado com as 8 garantias de UI)*

- [x] 01-15-PLAN.md — Entregar adapter/store e a página diagnóstica com oito garantias de UI.

**Wave 27** *(concluída; Plano 01-28 executado e testado com ausência total em production comprovada)*

- [x] 01-28-PLAN.md — Selar rotas técnicas e provar ausência de diagnóstico/tracer em production.

**Wave 28** *(concluída; Plano 01-16 executado — Gate Integral Nyquist aprovado e Fase 1 concluída)*

- [x] 01-16-PLAN.md — Executar o gate Nyquist integral e a aprovação humana final.

**UI hint**: yes

### Phase 2: Contas, Verificação e Operações Governadas

**Goal**: Pessoas conseguem administrar com segurança contas BETUFU e o registro opcional da matrícula UFU, enquanto operadores atuam apenas nos escopos e dados privados para os quais possuem autorização.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: AUTH-02, AUTH-03, AUTH-04, PROF-01, PROF-02, VERI-06, VERI-07, VERI-09, VERI-11, OPER-01, OPER-02, OPER-03, OPER-04, OPER-06, IDEN-05, PRIV-01, AUDT-01, AUDT-02
**Escopo reduzido em 2026-08-19 e reconciliado em 2026-08-26**: a verificação documental do vínculo UFU (`VERI-01` a `VERI-05`, `VERI-08`, `VERI-12`, `OPER-05`, `PRIV-02`) foi adiada para a v2. A Fase 3 também não recebe comprovante: usa matrícula transitória somente para calcular HMAC e não apresenta a coincidência como prova de titularidade ou vínculo oficial.
**Decisões reconciliadas**: normalização e formato foram aprovados e implementados no `02-06-SUMMARY.md`; persistência versionada, busca sob versões ativas e limites foram provados no `02-07-SUMMARY.md`; rotação concorrente está no `02-16-SUMMARY.md`; e comprometimento/expurgo D-37 está governado por `02-CHAVE-HMAC-PROCEDIMENTO.md` e pela trilha minimizada dos `02-04-SUMMARY.md`/`02-21-SUMMARY.md`. Permanece aberta somente a política de produto para matrículas legitimamente reemitidas ou múltiplas, que não pode ser inferida do mecanismo criptográfico. A regra de validade deixou de ser pendência: sem verificação documental, a matrícula registrada não expira no MVP.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Uma pessoa consegue criar uma conta BETUFU, verificar o e-mail, entrar novamente em visitas futuras, recuperar o acesso, sair e atualizar um perfil público sem que a BETUFU receba sua senha.
  2. Uma pessoa consegue registrar voluntariamente sua matrícula UFU, recebe recusa imediata e compreensível quando aquela matrícula já pertence a outra conta e consegue consultar o próprio estado de vínculo.
  3. O número da matrícula nunca fica armazenado de forma consultável: o HMAC é a única representação persistida, e nem ele nem o número aparecem em resposta pública, URL, telemetria ou log.
  4. A plataforma usa um HMAC não público da matrícula para impedir vínculos duplicados e comunica decisões de elegibilidade sem expor informações privadas.
  5. Um administrador geral consegue conceder, revogar e delimitar papéis operacionais; cada papel enxerga somente seu trabalho autorizado, e ações sensíveis ficam auditáveis. MFA é opcional no MVP e não condiciona nenhum comando.

**Plans**: 32/32 plans executed

- [x] 02-01-PLAN.md
- [x] 02-02-PLAN.md
- [x] 02-03-PLAN.md
- [x] 02-04-PLAN.md
- [x] 02-05-PLAN.md
- [x] 02-06-PLAN.md
- [x] 02-07-PLAN.md
- [x] 02-08-PLAN.md
- [x] 02-09-PLAN.md
- [x] 02-10-PLAN.md
- [x] 02-11-PLAN.md
- [x] 02-12-PLAN.md
- [x] 02-13-PLAN.md
- [x] 02-14-PLAN.md
- [x] 02-15-PLAN.md
- [x] 02-16-PLAN.md
- [x] 02-17-PLAN.md
- [x] 02-18-PLAN.md
- [x] 02-19-PLAN.md
- [x] 02-20-PLAN.md
- [x] 02-21-PLAN.md
- [x] 02-22-PLAN.md
- [x] 02-23-PLAN.md
- [x] 02-24-PLAN.md
- [x] 02-25-PLAN.md
- [x] 02-26-PLAN.md
- [x] 02-27-PLAN.md
- [x] 02-28-PLAN.md
- [x] 02-29-PLAN.md
- [x] 02-30-PLAN.md
- [x] 02-31-PLAN.md
- [x] 02-32-PLAN.md

- **Wave 1** *(concluída: 02-01, 02-02 e 02-03 implementados, verificados e revisados)*: 02-01, 02-02, 02-03
- **Wave 2** *(concluída: 02-04, 02-06 e 02-14 implementados, verificados, revisados e integrados)*: 02-04, 02-06, 02-14
- **Wave 3** *(concluída no worktree: 02-05, 02-09 e 02-21 implementados, verificados e formalizados; 02-21 aguarda integração Git)*: 02-05, 02-09, 02-21
- **Wave 4** *(concluída: 02-11, 02-15 e 02-22 implementados, verificados e formalizados)*: 02-11, 02-15, 02-22
- **Wave 5** *(concluída: 02-07 implementado, verificado, revisado e formalizado)*: 02-07
- **Wave 6** *(concluída: 02-08, 02-16 e 02-24 decididos ou implementados, verificados, revisados e formalizados; núcleo de origem Keycloak D-40 permanece sem ativação de realm/compose)*: 02-08, 02-16, 02-24
- **Wave 7** *(concluída em 2026-08-23 com PASS externo; 02-28 e 02-30 dependem de 02-24; hardening da origem e núcleo/tracer hermético do destino, em arquivos disjuntos e sem ativação)*: 02-23, 02-28, 02-30
- **Wave 8** *(concluída em 2026-08-23 com PASS externo: authorities/seed, hardening e operação do inbox; nenhuma ativação produtiva)*: 02-17, 02-31
- **Wave 9** *(concluída em 2026-08-24: painel operacional e rollout D-40 verificados, revisados e integrados após o gate humano literal)*: 02-10, 02-29
- **Wave 10** *(concluída em 2026-08-25 com contrato, cliente gerado e S4 verificados; revisão final P0=P1=P2=0)*: 02-18
- **Wave 11** *(concluída em 2026-08-25: correção D-32 transacional, autorização exclusiva do Administrador Geral e revisão final P0=P1=P2=0)*: 02-12
- **Wave 12** *(concluída em 2026-08-25: S3 e contrato/cliente da correção verificados; inventário final 47, digest aprovado e revisão P0=P1=P2=0)*: 02-13, 02-25
- **Wave 13** *(concluida em 2026-08-25: S5 global governada, papeis escopados deferidos, navegador real e revisao final P0=P1=P2=0)*: 02-19
- **Wave 14** *(concluída em 2026-08-26: S6 e contrato/digest de capacidades implementados, verificados em árvore combinada e revisados com P0=P1=P2=0)*: 02-20, 02-32
- **Wave 15** *(concluída em 2026-08-26: guard global, navegação S7 por capacidades, ciclo de vida de sessão, suítes completas e revisão final P0=P1=P2=0)*: 02-26
- **Wave 16** *(concluída em 2026-08-26: checkpoint humano S1-S7 aprovado sobre snapshot combinado congelado, com desktop/320 px, teclado, foco, privacidade, capabilities fail-closed e zero divergência no manifesto)*: 02-27

**UI hint**: yes

### Phase 3: Experiência Esportiva do Piloto

**Goal**: Visitantes conseguem acompanhar um catálogo confiável e genérico de competições e edições esportivas, começando pela Olimpíada Universitária UFU 2026, enquanto administradores mantêm seus dados somente nos escopos atribuídos.
**Mode:** mvp
**Depends on**: Phase 1, Phase 2
**Requirements**: AUTH-01, SPRT-01, SPRT-02, SPRT-03, SPRT-04, SPRT-05, SPRT-06, SPRT-07, SPRT-08, SPRT-09, SPRT-10, SPRT-11, SPRT-12, SPRT-13, SPRT-14, SPRT-15, SPRT-16
**Decisões encerradas em 2026-08-26**: o produto é genérico para várias competições; todas as modalidades podem entrar no catálogo, mas somente eventos de confronto direto recebem operação detalhada no MVP. Não há comprovante documental de atleta, escalação por partida, estatística específica por modalidade nem bloqueio de aposta por vínculo de atleta. O contexto completo e as evoluções obrigatórias estão em `03-CONTEXT.md`.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um visitante sem conta consegue navegar primeiro pela agenda de várias competições/edições, aplicar filtros e abrir evento com estado, horário, local opcional, participantes e fonte.
  2. Um ou mais administradores de competição conseguem operar somente o escopo atribuído e manter rascunhos/publicações de organizações, equipes por edição, atletas, elencos e eventos com versões, motivo e auditoria.
  3. Um visitante consegue abrir páginas de organização, equipe e atleta com dados autorizados, próximos eventos, resultados e estatísticas genéricas; favorito aponta para a organização esportiva inteira.
  4. Um atleta pode existir sem conta; matrícula transitória produz HMAC sem comprovante nem persistência bruta, e correspondência única gera proposta explícita, aceitável, rejeitável e contestável.
  5. Elencos são publicados manualmente; confrontos diretos exibem chaveamento derivado das partidas, enquanto outras modalidades e classificações manuais preservam fonte oficial sem simular regras não implementadas.

**Plans**: 27/27 plans executed; final post-gap certification and human snapshot approval completed on 2026-09-12

Plans:
**Wave 1**

- [x] 03-01-PLAN.md — aprovar ER/DDL, identidade de versão e migração preservadora

**Wave 2** *(concluída em 2026-08-27: implementação, revisão, integração linear e limpeza exata)*

- [x] 03-02-PLAN.md — provar migration, autorização e API pública do tracer

**Wave 3** *(concluída documentalmente em 2026-08-28: variante V, A2, B2 e C2 aprovados; nenhum requisito de produto concluído)*

- [x] 03-18-PLAN.md — reconciliar atomicamente contexto visual, UI-SPEC e planos incompletos; fechar B2/C2 com pareceres frescos

**Wave 4** *(concluída na worktree em 2026-08-29: implementação, suítes completas, aprovação visual e auditoria final verdes; Git permanece separado)*

- [x] 03-03-PLAN.md — entregar tracer vertical com listagem pública real mínima, contrato e navegador

**Wave 5** *(concluida na worktree; integracao Git permanece separada)*

- [x] 03-04-PLAN.md ? expandir workspace/catalogo O1 e locais reutilizaveis

**Wave 6** *(concluida na worktree; integracao Git permanece separada)*

- [x] 03-05-PLAN.md ? criar organizacoes/equipes, reconciliar favorito e ampliar perfil/rotas/guard

**Wave 7** *(concluida, integrada, enviada e limpa em 2026-08-30)*

- [x] 03-06-PLAN.md — criar atleta global e seam HMAC transitório

**Wave 8** *(concluida na worktree em 2026-08-30; integracao Git permanece separada)*

- [x] 03-07-PLAN.md — entregar propostas e revisão de vínculo

**Wave 9** *(blocked on Wave 8 completion)*

- [x] 03-08-PLAN.md — publicar elencos versionados

**Wave 10** *(blocked on Wave 9 completion)*

- [x] 03-09-PLAN.md — importar CSV/XLSX atomicamente

**Wave 11** *(blocked on Wave 10 completion)*

- [x] 03-10-PLAN.md — versionar eventos, resultados e fontes

**Wave 12** *(concluida, integrada, enviada e limpa em 2026-08-31)*

- [x] 03-11-PLAN.md — completar agenda e páginas públicas

**Wave 13** *(concluida, integrada, enviada e limpa em 2026-08-31)*

- [x] 03-12-PLAN.md — derivar chaveamento dos eventos

**Wave 14** *(concluida, integrada, enviada e limpa em 2026-08-31)*

- [x] 03-13-PLAN.md — publicar classificação manual versionada

**Wave 15** *(concluida, integrada, enviada e limpa em 2026-08-31)*

- [x] 03-14-PLAN.md — entregar perfil esportivo textual/social owner-only

**Wave 16** *(concluida, integrada, enviada e limpa em 2026-09-01)*

- [x] 03-15-PLAN.md — processar e armazenar foto com segurança

**Wave 17** *(concluída, integrada, enviada e limpa em 2026-09-01)*

- [x] 03-16-PLAN.md — consolidar capabilities e workspace O1–O8

**Wave 18** *(concluída, integrada, enviada e limpa em 2026-09-02)*

- [x] 03-19-PLAN.md — convergir perfil incompleto, perfil e matrícula para o Console pessoal sem alterar lógica

**Wave 19** *(concluida, integrada, enviada e limpa)*

- [x] 03-20-PLAN.md — convergir operacao legada e aposentar o AuthenticatedLayoutComponent com prova equivalente; implementacao, verificacoes e checkpoint humano concluidos

**Wave 20** *(concluida, integrada, enviada e limpa em 2026-09-02)*

- [x] 03-17-PLAN.md — certificar arquitetura, E2E, privacidade e validação; implementação, verificações e checkpoint humano concluídos

**Wave 21** *(gap closure; depends on 03-17; integrated and cleaned locally; push not performed)*

- [x] 03-21-PLAN.md — substituir relação singular por associação N:N de competições e modalidades; implementação, provas e integração local concluídas

**Wave 22** *(gap closure; depends on 03-21; integrated and cleaned locally; push not performed)*

- [x] 03-22-PLAN.md — aceitar formato GENERIC sem simular regras esportivas ausentes; implementação, verificações, candidato e integração local concluídos

**Wave 23** *(gap closure; depends on 03-22; integrated and cleaned locally; push not performed)*

- [x] 03-23-PLAN.md — derivar autorização por competição para organizações, atletas e elencos; decisão A1, implementação, provas e integração local concluídas

**Wave 24** *(gap closure; depends on 03-23; integrated and cleaned locally; push not performed)*

- [x] 03-24-PLAN.md — endurecer CSV/XLSX e bloquear alteração direta de roster publicado; implementação, verificações e integração local concluídas

**Wave 25** *(gap closure; depends on 03-24; integrated and cleaned locally; push not performed)*

- [x] 03-25-PLAN.md — persistir histórico append-only de lifecycle e consulta escopada; implementação, verificações e documentação concluídas localmente

**Wave 26** *(gap closure; depends on 03-25; integrated and cleaned locally; push not performed)*

- [x] 03-26-PLAN.md — tornar proposta owner navegável e corrigir deep links da agenda; implementação, focais e Playwright `4/4` concluídos localmente

**Wave 27** *(gap closure; depends on 03-26; final snapshot approved on 2026-09-12; Git formalization pending)*

- [x] 03-27-PLAN.md — recertificar a Fase 3 após o fechamento dos gaps; suites, contratos, dois browsers e snapshot humano aprovados localmente

**UI hint**: yes

### Phase 03.1: Convergência do Contrato de Diagnóstico (INSERTED)

**Goal:** O backend de diagnostico emite exatamente `UP`, `DEGRADED` e `DOWN`, em paridade com o contrato publico, sem alterar agregacao, probes, autorizacao, profiles, rotas ou consumidores.
**Requirements**: DIAG-01
**Depends on:** Phase 3
**Plans:** 1 plan

Plans:

- [x] 03.1-01-PLAN.md — convergir o enum, consumidores Java e provas JSON/HTTP com rebuild limpo; implementacao, verificacoes e checkpoint humano concluidos

### Phase 4: Mercados Pré-jogo e Odds Transparentes

**Goal**: Pessoas conseguem consultar odds pré-jogo explicáveis para o piloto, e administradores governam os mercados sem manipulação manual rotineira de preços.
**Mode:** mvp
**Depends on**: Phase 3 and Phase 03.1
**Requirements**: ODDS-01, ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13
**Decisão pendente**: A calibração/dataset, a autoridade de fonte, a estratégia física de envelope/head e FKs, o backfill de legado, a temporalidade/regulamento, o workflow administrativo, a propagação/recuperação, a autorização/auditoria, o binding/freeze/correção de roster, o inventário OpenAPI/cliente e a UI-SPEC exigem gates humanos próprios. Os parâmetros numéricos do Elo, o tratamento da probabilidade de empate e os confrontos de referência continuam em aberto até a calibração aprovada.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um administrador autorizado consegue criar somente mercados pré-jogo compatíveis com confrontos diretos, e o sistema gera probabilidades e odds versionadas com base em Elo, separadas por esporte e categoria.
  2. Um visitante consegue consultar a odd atual, seu histórico de versões, metodologia legível, dados de entrada e indicador de confiança; dados escassos produzem uma estimativa neutra explicitamente marcada como baixa confiança, não odds manuais rotineiras.
  3. Preços publicados aplicam a margem configurável de forma reproduzível e não são influenciados pelo volume de palpites dos usuários; cancelamento e W.O. seguem tratamento explicável e versionado.
  4. Um administrador precisa suspender o mercado e tentar corrigir a fonte ou recalcular antes de propor uma substituição, apresentando evidências e uma comparação visível com o modelo.
  5. Um segundo administrador, distinto, precisa aprovar a substituição excepcional, produzindo uma nova versão imutável e auditada que não altera odds já aceitas nem mercados definitivamente encerrados.

**Plans**: 21/21 registros de plano / 46 tasks do escopo original concluídos; Waves 0–17 certificadas e os planos posteriores de tooling/redirect reconciliados; UAT humano `04-08/T3` aprovado

- [x] 04-01-PLAN.md
- [x] 04-02-PLAN.md
- [x] 04-03-PLAN.md
- [x] 04-04-PLAN.md
- [x] 04-05-PLAN.md
- [x] 04-06-PLAN.md
- [x] 04-07-PLAN.md
- [x] 04-08-PLAN.md
- [x] 04-09-PLAN.md
- [x] 04-10-PLAN.md
- [x] 04-11-PLAN.md
- [x] 04-12-PLAN.md
- [x] 04-13-PLAN.md
- [x] 04-14-PLAN.md
- [x] 04-15-PLAN.md
- [x] 04-16-PLAN.md
- [x] 04-17-PLAN.md
- [x] 04-18-PLAN.md
- [x] 04-19-PLAN.md
- [x] 04-20-PLAN.md
- [x] 04-21-PLAN.md

**Planos e ondas**:

- [x] **Wave 0**: 04-01 — calibração, amostra, parâmetros e backtest.
- [x] **Wave 1**: 04-15 — candidato de autoridade da fonte e políticas temporais/legado.
- [x] **Wave 2**: 04-02 — fronteira pública `sports` → `odds`, taxonomia e estados.
- [x] **Wave 3**: 04-03 — candidato de schema; 04-12 — oráculos golden independentes.
- [x] **Wave 4**: 04-09 — persistência odds e lineage.
- [x] **Wave 5**: 04-18 — governança, autorização server-side e auditoria.
- [x] **Wave 6**: 04-16 — autoridade de outcomes, head e compatibilidade V16/V24.
- [x] **Wave 7**: 04-17 — produtor real e projeção temporal.
- [x] **Wave 8**: 04-04 — kernel e cálculo.
- [x] **Wave 9**: 04-10 — replay, correções e mutações negativas.
- [x] **Wave 10**: 04-19 — binding, freeze e correção de roster.
- [x] **Wave 11**: 04-05 — criação, publicação e lifecycle do mercado.
- [x] **Wave 12**: 04-06 — override maker-checker e critério de correção prévia.
- [x] **Wave 13**: 04-07 — candidato OpenAPI e digest governado.
- [x] **Wave 14**: 04-11 — cliente gerado a partir do digest aprovado.
- [x] **Wave 15**: 04-13 — superfície pública de consulta e explicação.
- [x] **Wave 16**: 04-14 — superfície operacional e jornadas de browser.
- [x] **Wave 17**: 04-08 — certificação focal, mutações, snapshot e UAT preparado.
- [x] **Manutenção pós-fechamento**: 04-20 — seeder local daily/full; 04-21 — redirect pós-login administrativo.

Os planos estão descritos em `.planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-*-PLAN.md` e a matriz de evidências em `04-VALIDATION.md`. Esta decomposição é planejamento, não autorização de código, migration, dependência, infraestrutura ou Git.
**UI hint**: yes

### Phase 5: Créditos Virtuais e Palpites Protegidos

**Goal**: Pessoas elegíveis conseguem registrar um palpite pré-jogo transparente usando somente créditos virtuais, com saldo, elegibilidade e conflitos verificados de forma atômica.
**Mode:** mvp
**Depends on**: Phase 2, Phase 4
**Requirements**: VERI-10, CRED-01, CRED-02, CRED-03, CRED-04, CRED-05, CRED-06, CRED-08, CRED-10, ODDS-14, PRED-01, PRED-02, PRED-03, PRED-04, PRED-05, PRED-06, PRED-07, PRED-08, INTE-01, INTE-02, INTE-03, INTE-04, DATA-03, DATA-04, DATA-05, API-03
**Decisões ratificadas em `05-CONTEXT.md`**: concessão inicial única de 1.000,00 créditos; stake mínimo de 10,00 com all-in permitido até o saldo; escala de duas casas; retorno `stake × odd` incluindo o stake; arredondamento `HALF_UP` em escala 2; conflito administrativo tardio tratado por anulação e lançamento compensatório imutável quando o conflito já era vigente no aceite. Vínculo de atleta não bloqueia aposta no MVP, conforme `03-CONTEXT.md`.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um aluno recém-elegível recebe a mesma concessão única e auditável de créditos virtuais, nunca a recebe novamente por revalidação e não consegue comprar, vender, transferir, sacar ou repor créditos por mecanismos não aprovados.
  2. Um usuário verificado, elegível e sem conflitos consegue selecionar um resultado do mercado antes do horário canônico de fechamento e visualizar um bilhete com seleção, valor, versões da odd aceita e das regras do mercado, retorno virtual potencial e horário de encerramento.
  3. O servidor verifica novamente, de forma atômica, verificação, papéis, escopos, conflitos administrativos, estado do mercado, prazo, versão da odd, limites configurados e saldo; tentativas inválidas ou concorrentes são rejeitadas sem saldo negativo, seleção duplicada ou efeitos duplicados no livro-razão. O vínculo de atleta é permitido pela política do MVP.
  4. Um palpite enviado é imutável, seu débito e seus registros aceitos são gravados juntos, e palpites individuais ou agregados permanecem ocultos de outros usuários até o fechamento do mercado.
  5. Quando o preço muda antes do envio, o bilhete aberto é invalidado e o usuário precisa reconfirmar explicitamente a versão atualizada.

**Plans**: 8 plans complete

- [x] 05-01-PLAN.md — Materializar ledger virtual imutável e concessão inicial idempotente.
- [x] 05-02-PLAN.md — Registrar palpite pré-jogo com snapshot de odds, elegibilidade e débito atômico.
- [x] 05-03-PLAN.md — Publicar contrato REST BFF e gerar cliente Angular anti-drift.
- [x] 05-04-PLAN.md — Integrar bilhete próprio à página pública de odds conforme `05-UI-SPEC.md`.
- [x] 05-05-PLAN.md — Certificar concorrência, mutações negativas, privacidade, browsers e snapshot UAT.
- [x] 05-06-PLAN.md — Preservar vetores completos de odds e resolver o snapshot autoritativo pela linhagem.
- [x] 05-07-PLAN.md — Corrigir concessão retroativa, idempotência canônica e aceite atômico do palpite.
- [x] 05-08-PLAN.md — Reconciliar o seed local com publicações de odds completas e concessão inicial idempotente.
**UI hint**: yes

Phase 5 completion update (2026-09-14): all seven original plans are complete. The final
05-05 snapshot passed automated certification and independent read-only review;
the responsible developer approved human checkpoint `05-05/T3`. A auditoria
manual posterior encontrou lacunas de integração entre capacidades já
implementadas; por decisão do responsável, a Fase 05.1 precede a Fase 6. The
additional local-seed reconciliation, 05-08, was completed on 2026-09-15.

### Phase 05.1: Convergência das Jornadas do Usuário (INSERTED)

**Goal**: Visitantes, participantes e operadores percorrem as capacidades entregues nas Fases 1–5 por caminhos visíveis e coerentes, preservando contexto de autenticação, privacidade, autorização e estado após navegação e refresh.
**Mode:** mvp
**Depends on**: Phase 1, Phase 2, Phase 3, Phase 4, Phase 5
**Requirements**: AUTH-01, AUTH-03, PROF-01, VERI-09, SPRT-12, IDEN-03, IDEN-04, CRED-03, ODDS-09, ODDS-13, PRED-01, PRED-03, PRED-04, PRED-08, API-03, TEST-02
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um visitante parte da agenda, abre um evento publicado e encontra um CTA de odds somente quando o evento possui um mercado público consultável, sem conhecer UUIDs nem criar dependência cíclica entre `sports` e `odds`.
  2. Ao iniciar o login de um contexto público ou protegido, o sistema preserva e restaura apenas uma URL interna validada, com consumo único e sem permitir open redirect, rota técnica ou vazamento de token.
  3. O shell da Arena representa os estados visitante, carregando, autenticado e expirado de forma coerente, oferecendo “Minha Conta”, saldo próprio e logout quando aplicável, inclusive em 320 px e por teclado.
  4. Perfil e matrícula exibem o mesmo estado real do vínculo institucional sem expor matrícula, HMAC ou detalhes internos entre módulos.
  5. O bilhete bloqueia interação quando o prazo canônico já venceu e prioriza corretamente os estados aberto, suspenso e encerrado; o servidor permanece a autoridade final para rejeitar o palpite.
  6. Rotas protegidas tratam sessão anônima/expirada de forma compreensível e preservam retorno seguro, enquanto URLs inexistentes terminam em uma página 404 acessível sem capturar callbacks técnicos.
  7. Playwright comprova a jornada integrada real, sem interceptar APIs de negócio, cobrindo agenda → evento → mercado → login → retorno → palpite, refresh, duplicidade, logout, 401/404, deadline, navegação do browser, sessões separadas e largura móvel.

**Plans**: 7/7 plans complete

- [x] 05.1-01-PLAN.md — Publicar a referência evento→mercado sem ciclo modular e exibir o CTA de odds no evento.
- [x] 05.1-02-PLAN.md — Preservar e consumir com segurança o destino contextual durante o login OIDC.
- [x] 05.1-03-PLAN.md — Tornar o shell da Arena consciente de sessão, saldo próprio e logout.
- [x] 05.1-04-PLAN.md — Substituir o estado estático de matrícula do perfil por consulta real e privada.
- [x] 05.1-05-PLAN.md — Bloquear o bilhete após o deadline e ordenar corretamente os estados de disponibilidade.
- [x] 05.1-06-PLAN.md — Tratar acesso anônimo/expirado a rotas protegidas e oferecer wildcard 404 acessível.
- [x] 05.1-07-PLAN.md — Certificar as jornadas integradas com Playwright real e gates agregados.

**Ondas concluídas**:

- [x] **Wave 1**: 05.1-01, 05.1-02 e 05.1-04.
- [x] **Wave 2**: 05.1-03, 05.1-05 e 05.1-06.
- [x] **Wave 3**: 05.1-07 e certificação final da fase.

Completion update (2026-09-17): all seven plans are complete. Real Playwright
journeys passed 12/12 across Chromium and Firefox at desktop and 320 px, and
the aggregate repository gates passed at Maven 714/714, Angular 424/424 and
Node 757/757. Phase 6 remains subject to its own interactive planning and
approval cycle; no push was performed.
**UI hint**: yes

### Phase 6: Resultados Confirmados, Rankings e Grupos

**Goal**: Resultados esportivos confirmados se transformam em liquidações explicáveis e recuperáveis e em uma competição universitária vitalícia e justa entre participantes verificados.
**Mode:** mvp
**Depends on**: Phase 05.1
**Requirements**: CRED-07, CRED-09, PRED-09, ODDS-15, SETL-01, SETL-02, SETL-03, SETL-04, SETL-05, SETL-06, SETL-07, SETL-08, SETL-09, SETL-10, SETL-11, RANK-01, RANK-02, RANK-03, RANK-04, RANK-05, RANK-06, RANK-07, RANK-08, POOL-01, POOL-02, POOL-03, POOL-04, ACHV-01, ACHV-02, ACHV-03, TEST-04
**Decisões fechadas em `06-DISCUSS-CHECKPOINT.json`**: recuperação FIFO de pagamentos incorretos, bloqueio apenas de novas apostas enquanto houver pendência, maker-checker para ajustes administrativos, motivos fechados, visibilidade sanitizada e preservação/recomposição do ranking durante matrícula expirada ou correções de liquidação.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. A liquidação consome a versão esportiva vigente já publicada e confirmada na Fase 3; um administrador autoriza a versão exata e, no MVP, pode ser a mesma pessoa que registrou ou confirmou o resultado, com atores persistidos separadamente para futura política de duas pessoas.
  2. Usuários conseguem consultar o estado pendente, ganho, perdido, reembolsado ou anulado de cada palpite, juntamente com resultado, regra, cálculo e movimentações de saldo aplicados.
  3. Adiamento, cancelamento, anulação e correção suspendem ou compensam o trabalho afetado sem apagar o histórico; novas tentativas e recuperações não duplicam débitos, reembolsos, pagamentos, conquistas ou efeitos de liquidação.
  4. Uma pessoa consegue visualizar um único ranking vitalício da UFU, ordenado primeiro por palpites definitivamente corretos e depois pela taxa de acerto, com posições compartilhadas nos empates restantes e sem multiplicação dos acertos por créditos ou odds.
  5. Usuários cadastrados conseguem administrar grupos privados que exibem as mesmas métricas do ranking vitalício e conquistar feitos concedidos uma única vez, com regras e edição conquistada exibidas no perfil.

**Plans**: 8/8 plans complete in 6 waves (`06-01` through `06-08`)
**Planning artifacts**: `06-CONTEXT.md`, `06-RESEARCH.md`, `06-DISCUSSION-LOG.md`, `06-UI-SPEC.md`
**Planning review**: structural allowlists reconciled on 2026-10-01; revised UI contract accepted by owner after 6/6 inline review and independently APPROVED by the UI checker (5 PASS, 1 non-blocking FLAG). The approved 06-07 generated-client inventory/digest passed official generation and anti-drift.
**Execution progress**: `06-01` and `06-02` completed and verified on 2026-10-01; `06-03` through `06-06` completed on 2026-10-02. V33 provides the settlement foundation, V34 the first idempotent pass, V35 audited credit corrections and recovery, V36 the reconstructible lifetime ranking, V37 private groups, and V38 derived, idempotent and revocable achievements. `06-07` completed on 2026-10-03 (`27224b5`): HTTP/OpenAPI/generated client and Angular journeys. `06-08` completed on 2026-10-03 (`0a5f025`): concurrency, temporal replay, privacy, approved group-form fix, isolated real E2E and final aggregate certification. All eight plans are complete; phase verification is passed.
**Execution order**:

  - Wave 1: `06-01` - fundacao de contratos e schema de liquidacao (complete).
  - Wave 2: `06-02` - motor de liquidacao idempotente e retomavel (complete; correcao financeira posterior no 06-03).
  - Wave 3: `06-03` correcoes de ledger (complete); `06-04` ranking reconstruivel (complete).
  - Wave 4: `06-05` grupos privados (complete); `06-06` conquistas derivadas (complete).
  - Wave 5: `06-07` contratos HTTP, cliente Angular e UI integrada (complete).
  - Wave 6: `06-08` concorrencia, Playwright e certificacao agregada (complete).

**06-08 certification (2026-10-03)**: approved inline remediation corrected
native group submission and provisioned strictly owned isolated real test data.
Final clean Maven verify passed 361 Surefire and 613 Failsafe XML cases,
Angular 455/455, Node 812/812, focal guards 47/47 and real Chrome 16/16 at
desktop/320px without skips/retries. Generator/lint/build/Spotless/source and
diff gates passed. Owner services, volumes, two predictions and documentation
were preserved; no push occurred. See `06-VALIDATION.md` and
`06-VERIFICATION.md` (passed) for requirement evidence, frozen hashes and
limits: bounded races are not launch-load SLOs, forced store expiry is not an
eight-hour wait, and keyboard/mobile checks are not full WCAG certification.
REQUIREMENTS.md tracking cells remain outside this documentary allowlist and
need scoped reconciliation; Phase 7 is pending, not automatically started.
**UI hint**: yes

### Phase 7: Comunidade Moderada e Prontidão do Piloto

**Goal**: O piloto oferece chat por partida e sala global, com conversas seguras e responsivas, moderação auditável e operação recuperável sem comprometer privacidade ou integridade financeira.
**Mode:** mvp
**Depends on**: Phase 2, Phase 3, Phase 6
**Requirements**: CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, CHAT-06, RT-01, INTE-05, PRIV-03, PRIV-04, PRIV-05, OPS-04, TEST-03, TEST-05, UX-01, UX-02
**Frontend scope**: Entrada Comunidade no topo da Arena, chat no evento, denúncias/mute, console de moderação e pausas, revisão de sanções e Privacidade na conta. Angular Signals, sessão BFF, OpenAPI gerado e visual existente preservado; contrato UI ainda pendente.
**Decisões consolidadas**: D-01..D-40 em 07-CONTEXT.md; global incluído expressamente no piloto. Esta atualização aprova somente a consolidação documental.
**Decisões pendentes**: Protocolo server-push e reconciliação RT-01/STOMP; aprovação do UI-SPEC draft e arquitetura/falhas da preferência de regras; bordas de lifecycle/mute/limiter; políticas jurídicas, identidade institucional legítima, backup/chaves/destino e carga/SLO/erro. Nenhum provedor ou dependência selecionado por inferência.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Partidas publicadas têm leitura pública; global exige sessão inclusive para leitura, mas permite inativos. Ambos exigem ATIVO para escrever, texto até 280 caracteres e uma mensagem por usuário a cada três segundos. Partida fecha permanentemente duas horas após marco definitivo aprovado; correção concluída antes de fechar reconta o prazo, nunca reabre canal expirado.
  2. Denúncia privada com catálogo fechado e mute imediato sincronizado preservam a experiência de outros leitores. Moderador remove no escopo; somente administrador geral aplica sanção de escrita em todos os chats, com progressão e revisão auditáveis. Texto original restrito não aparece em API/stream público.
  3. Somente administrador geral pausa/retoma conta, partida, mercado ou canal; nega novos comandos definidos sem apagar histórico ou interromper liquidação/devolução/correção. Conta pausada conserva consultas, revisão e direitos.
  4. Jornadas descobríveis global/evento/conta/operação funcionam em desktop/mobile/teclado com foco e estados compreensíveis. Fila offline é volátil por aba, retry idempotente revalida autorização e logout/troca de persona elimina pendentes.
  5. Lançamento depende de política jurídica/retencão/direitos, identidade legítima, ausência de vulnerabilidades confirmadas, CI/contrato/arquitetura e restore utilizável de BETUFU/Keycloak/objetos. Metas RPO <=1h/RTO <=4h e referência de 50 simultâneos precisam evidência real; supressões posteriores ao snapshot devem ser reaplicadas antes de servir.

**Plans**: 35 planos em 17 ondas, 102 tasks; 15 executados (07-01/02/03/04/28/05/06/07/08/09/10/11/13/14/16). Waves 1-7 completas; Wave 8 inicia com07-17. 07-16: V43, focal8/8, Node936/936 e Maven1062/1062 (XML) verificados. V39-V43 preservadas; legado pre-V41 e gates juridicos/institucionais/publicos pendentes. Fase nao certificada; ver SUMMARYs.

**Wave 1**
- [x] 07-01-PLAN.md — Governanca de blocos e registro de gates.

**Wave 2 — Próxima; exige autorização e gates dos consumidores**
- [x] 07-02-PLAN.md — Preparacao juridica e institucional segregada.
- [x] 07-03-PLAN.md — Pipeline real para fonte OpenAPI candidata.
- [x] 07-04-PLAN.md — Canais global e por partida: schema V39.
- [x] 07-28-PLAN.md — Backups automaticos completos com RPO1h.

**Wave 3 — Bloqueada pelas dependências explícitas dos planos**
- [x] 07-05-PLAN.md — Portas publicas e leitura HTTP segura.

**Wave 4 — Bloqueada pelas dependências explícitas dos planos**
- [x] 07-06-PLAN.md — Contrato gerado de leitura dos dois canais.
- [x] 07-07-PLAN.md — Server push seguro e cursor de mudancas.
- [x] 07-09-PLAN.md — Escrita ATIVO, limite e idempotencia: V40/backend.

**Wave 5 - Concluida**
- [x] 07-08-PLAN.md — Comunidade Arena e evento: tracer real de leitura.
- [x] 07-10-PLAN.md — Marco definitivo e encerramento permanente: V41.

**Wave 6 - Concluida**
- [x] 07-11-PLAN.md — Contrato oficial de envio e recibo.
- [x] 07-13-PLAN.md — Denuncia e mute sincronizado: V42/backend.

**Wave 7 - Concluida**
- [x] 07-14-PLAN.md — Contrato de denuncias e mute de conta.
- [x] 07-16-PLAN.md — Moderacao scoped e evidencia restrita: V43.

**Wave 8 - Em execucao autorizada; 07-17 e 07-20**
- [ ] 07-17-PLAN.md — Sancoes globais e revisao na conta: backend.
- [ ] 07-20-PLAN.md — Pausa e retomada admin-only: V44/backend.

**Wave 9 — Bloqueada pelas dependências explícitas dos planos**
- [ ] 07-18-PLAN.md — Contrato de moderacao e revisao privada.
- [ ] 07-21-PLAN.md — Guards atomicos de chat e novos palpites.
- [ ] 07-24-PLAN.md — Direitos do titular e resposta operacional: V45/backend.

**Wave 10 — Bloqueada pelas dependências explícitas dos planos**
- [ ] 07-12-PLAN.md — Composer e fila volatil: tracer real de envio.
- [ ] 07-22-PLAN.md — Contrato oficial de pausas e estados.
- [ ] 07-27-PLAN.md — Retencao segregada, exports e supressoes: V46.

**Wave 11 — Bloqueada pelas dependências explícitas dos planos**
- [ ] 07-15-PLAN.md — Denunciar e silenciar: tracer multi-dispositivo.
- [ ] 07-25-PLAN.md — Contrato oficial de Privacidade da conta.
- [ ] 07-29-PLAN.md — Restore real isolado e supressao antes de servir.
- [ ] 07-33-PLAN.md — Proposta de preferência de regras por conta: backend e V47 após V46.

**Wave 12 — Bloqueada pelas dependências explícitas dos planos**
- [ ] 07-19-PLAN.md — Console e punicoes da conta: tracer integrado.
- [ ] 07-34-PLAN.md — Contrato privado gerado da preferência de regras, após 07-25/33.

**Wave 13 — Bloqueada pelas dependências explícitas dos planos**
- [ ] 07-23-PLAN.md — Pausas operacionais e estados: tracer real.

**Wave 14 — Bloqueada pelas dependências explícitas dos planos**
- [ ] 07-26-PLAN.md — Privacidade na conta: tracer titular/operacao.
- [ ] 07-35-PLAN.md — Popup de regras acessível e jornada real entre contas/dispositivos.

**Wave 15 — Bloqueada pelas dependências explícitas dos planos**
- [ ] 07-30-PLAN.md — CI, publicacao e carga de referencia50.

**Wave 16 — Bloqueada pelas dependências explícitas dos planos**
- [ ] 07-31-PLAN.md — Jornadas reais integradas e regressao do piloto.

**Wave 17 — Bloqueada pelas dependências explícitas dos planos**
- [ ] 07-32-PLAN.md — Certificacao, snapshot e parecer do responsavel.

**Cross-cutting constraints:**
- Briefing/aprovação por bloco e candidatos/hashes antes de SQL/API governados; execução e Git separados, sem push.
- Allowlists com até 15 paths incluindo candidatos/metadados/gerados. Inventário real extra interrompe publicação e exige revisão/subdivisão.
- Portas públicas consumer-owned/adaptadores produtores, sem imports internos cruzados; schemas append-only candidatos V39..V47 não alteram V1..V38 nem fatos financeiros.
- UI-SPEC existe como draft, com aprovação/checker pendentes; server-push não escolhe SSE implicitamente. CHAT-01/05 e Out of Scope reconciliados, sem marcar entrega.
- Sanções/pausas não geram punição financeira; DTOs/marcadores/cursor/stream respeitam autorização e privacidade.
- Backup/restore/carga/E2E somente em alvos isolados aprovados; preservar documentos, apostas, volumes e serviços do responsável.
- Ondas serializam gerador/contrato/rotas/shell compartilhados. Preferência inline; tabela não autoriza worktrees/agentes ou paralelismo.

**Planning review anterior — 2026-10-04 (32 planos, escopo delimitado):** Subdivisão documental substitui o blocker de tamanho da proposta inicial: 32 allowlists com no máximo 15 paths; DAG local sem predecessor inválido ou colisão na mesma onda. Mapa de validação atualizado para 92 tasks. Revisão independente delimitada encontrou 0 BLOCKERs e 2 WARNINGs: ordem Flyway e ligação do validador. Ajustados por dependências monotônicas V39..V46 e chamadas bloqueantes por etapa; rechecagem independente confirmou os dois alertas resolvidos. Não há PASS global; estrutura 32/32 e referências 16/16 requisitos e 40/40 decisões são provas documentais, não testes de negócio. Verificações anteriores de 10/10 e 14 decisões são históricas. Fase 7 não implementada ou liberada para execução.
**Reconciliation — 2026-10-04:** 07-08 inclui HTML/teste reais do header (15 paths). Acrescentadas propostas 07-33/34/35 (10/14/9 paths) nas ondas 11/12/14, sem renumerar 01..32. V47 depende de V46; API 34 serializa contrato/gerador após 25; UI 35 serializa chat-panel após 23. 31 exige 35 antes da certificação. Preferência no módulo chat e política de falhas são propostas para aprovação no primeiro checkpoint de 33, não decisões já autorizadas. Fechar regras não significa aceite jurídico. UI-D01..07 registradas no contexto; requisitos não marcados concluídos. Revisão atual inline, não independente; nenhuma implementação, Git ou liberação do piloto.
**UI hint**: yes

## Progress

**Ordem de execução:** Phase 1 → Phase 2 → Phase 3 → Phase 03.1 → Phase 4 → Phase 5 → Phase 05.1 → Phase 6 → Phase 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Fundação Segura da Plataforma | 31/31 | Complete | 2026-08-19 |
| 2. Contas, Verificação e Operações Governadas | 32/32 | Complete | 2026-08-26 |
| 3. Experiência Esportiva do Piloto | 27/27 | Complete; final post-gap snapshot approved | 2026-09-12 |
| 03.1. Convergência do Contrato de Diagnóstico | 1/1 | Complete | 2026-09-03 |
| 4. Mercados Pré-jogo e Odds Transparentes | 21/21 | Complete    | 2026-09-12 |
| 5. Créditos Virtuais e Palpites Protegidos | 8/8 | Complete; final snapshot approved and local seed reconciled | 2026-09-15 |
| 05.1. Convergência das Jornadas do Usuário | 7/7 | Complete; integrated real-browser verification passed | 2026-09-17 |
| 6. Resultados Confirmados, Rankings e Grupos | 8/8 | Complete; isolated real E2E and aggregate verification passed | 2026-10-03 |
| 7. Comunidade Moderada e Prontidao do Piloto | 15/35 | Wave 8 next; 07-17 | - |
