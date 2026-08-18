# Roadmap: BETUFU

## Visão geral

Roadmap aprovado para o MVP da BETUFU. Ele entrega um piloto seguro e operado de forma independente para palpites em confrontos diretos das Olimpíadas UFU: primeiro estabelece a plataforma protegida e a participação verificada; depois oferece dados esportivos confiáveis e odds transparentes; em seguida habilita palpites com créditos virtuais, transforma resultados em uma competição social justa e, por fim, conclui uma comunidade segura, responsiva e pronta para lançamento. O usuário aprovou esta estrutura de sete fases em 2026-08-01.

## Fases

**Numeração das fases:**

- Fases inteiras representam o trabalho planejado do MVP.
- Fases decimais ficam reservadas para inserções urgentes após o planejamento.

- [ ] **Phase 1: Fundação Segura da Plataforma** - Estabelecer a base modular, observável e segura da qual todas as capacidades do MVP dependem.
- [ ] **Phase 2: Contas, Verificação e Operações Governadas** - Permitir identidades BETUFU protegidas e oferecer controles estritamente delimitados aos operadores autorizados.
- [ ] **Phase 3: Experiência Esportiva do Piloto** - Publicar e operar dados confiáveis de confrontos diretos, equipes e elencos das Olimpíadas UFU.
- [ ] **Phase 4: Mercados Pré-jogo e Odds Transparentes** - Produzir odds automáticas explicáveis, com substituições excepcionais submetidas a controles rigorosos.
- [ ] **Phase 5: Créditos Virtuais e Palpites Protegidos** - Permitir que participantes elegíveis registrem palpites pré-jogo seguros e atômicos usando créditos virtuais.
- [ ] **Phase 6: Resultados Confirmados, Rankings e Grupos** - Transformar resultados confirmados em liquidações corretas, classificação vitalícia e conquistas.
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

**Plans**: 25/31 plans executed
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

**Wave 24** *(liberada; Wave 23 concluída)*

- [ ] 01-14-PLAN.md — Implementar tokens, tema e fontes/ícones auto-hospedados.

**Wave 25** *(blocked on Wave 24 completion)*

- [ ] 01-27-PLAN.md — Montar a estrutura inicial autenticada e seus testes focais.

**Wave 26** *(blocked on Wave 25 completion)*

- [ ] 01-15-PLAN.md — Entregar adapter/store e a página diagnóstica com oito garantias de UI.

**Wave 27** *(blocked on Wave 26 completion)*

- [ ] 01-28-PLAN.md — Selar rotas técnicas e provar ausência de diagnóstico/tracer em production.

**Wave 28** *(blocked on Wave 27 completion)*

- [ ] 01-16-PLAN.md — Executar o gate Nyquist integral e a aprovação humana final.

**UI hint**: yes

### Phase 2: Contas, Verificação e Operações Governadas

**Goal**: Pessoas conseguem administrar com segurança contas BETUFU e a verificação UFU opcional, enquanto operadores atuam apenas nos escopos e dados privados para os quais possuem autorização.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: AUTH-02, AUTH-03, AUTH-04, PROF-01, PROF-02, VERI-01, VERI-02, VERI-03, VERI-04, VERI-05, VERI-06, VERI-07, VERI-08, VERI-09, VERI-11, VERI-12, OPER-01, OPER-02, OPER-03, OPER-04, OPER-05, OPER-06, IDEN-05, PRIV-01, PRIV-02, AUDT-01, AUDT-02
**Decisão pendente**: A regra de validade da verificação, o tratamento de matrículas reemitidas ou múltiplas e o protocolo de normalização, rotação e comprometimento do HMAC continuam em aberto; esta fase deve submetê-los a uma decisão explícita ou configuração antes que a implementação dependa deles.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Uma pessoa consegue criar uma conta BETUFU, verificar o e-mail, entrar novamente em visitas futuras, recuperar o acesso, sair e atualizar um perfil público sem que a BETUFU receba sua senha.
  2. Uma pessoa consegue enviar voluntariamente um documento permitido, acompanhar os estados pendente, aprovado, rejeitado e expirado e reenviar quando a política permitir.
  3. Revisores autorizados acessam documentos privados apenas temporariamente, decidem com motivo e validade registrados e acompanham a exclusão do arquivo processado; a autorrevisão ou revisão em conflito é bloqueada.
  4. A plataforma usa um HMAC não público da matrícula para impedir identidades verificadas duplicadas e comunica decisões de elegibilidade sem expor informações privadas.
  5. Um administrador geral consegue conceder, revogar e delimitar papéis operacionais; cada papel enxerga somente seu trabalho autorizado, e ações sensíveis exigem MFA e ficam auditáveis.

**Plans**: TBD
**UI hint**: yes

### Phase 3: Experiência Esportiva do Piloto

**Goal**: Visitantes conseguem acompanhar um piloto confiável de confrontos diretos das Olimpíadas UFU, enquanto administradores de competição mantêm seus dados esportivos dentro de escopos responsabilizáveis.
**Mode:** mvp
**Depends on**: Phase 1, Phase 2
**Requirements**: AUTH-01, SPRT-01, SPRT-02, SPRT-03, SPRT-04, SPRT-05, SPRT-06, SPRT-07, SPRT-08, SPRT-09, SPRT-10, SPRT-11, SPRT-12, SPRT-13, SPRT-14
**Decisão pendente**: As modalidades, categorias e tabelas autoritativas exatas do piloto continuam em aberto; somente partidas confiáveis em formato de confronto direto podem ser lançadas.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um visitante consegue navegar por competições públicas, filtrar a programação, abrir uma partida completa e consultar estado, proveniência e horários de um confronto publicado sem possuir conta.
  2. Um administrador de competição consegue manter apenas edição, etapas, equipes, atletas, elencos e partidas da competição atribuída, com motivos auditáveis para publicação, suspensão, adiamento e cancelamento.
  3. Um visitante consegue abrir a página de uma equipe e consultar elenco público autorizado, esporte, desempenho recente, resultados e próximas partidas.
  4. Um atleta pode existir sem conta BETUFU; uma correspondência HMAC posterior é proposta de modo único, revisável e contestável, sem revelar a matrícula.
  5. Elencos ficam bloqueados antes da abertura dos mercados afetados, e o piloto lançado exclui formatos não suportados com vários competidores e confrontos sem fonte confiável.

**Plans**: TBD
**UI hint**: yes

### Phase 4: Mercados Pré-jogo e Odds Transparentes

**Goal**: Pessoas conseguem consultar odds pré-jogo explicáveis para o piloto, e administradores governam os mercados sem manipulação manual rotineira de preços.
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: ODDS-01, ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13
**Decisão pendente**: Os parâmetros numéricos do Elo, o tratamento da probabilidade de empate e os confrontos de referência continuam em aberto e devem ser especificados ou configurados antes da implementação dos preços automáticos.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um administrador autorizado consegue criar somente mercados pré-jogo compatíveis com confrontos diretos, e o sistema gera probabilidades e odds versionadas com base em Elo, separadas por esporte e categoria.
  2. Um visitante consegue consultar a odd atual, seu histórico de versões, metodologia legível, dados de entrada e indicador de confiança; dados escassos produzem uma estimativa neutra explicitamente marcada como baixa confiança, não odds manuais rotineiras.
  3. Preços publicados aplicam a margem configurável de forma reproduzível e não são influenciados pelo volume de palpites dos usuários; cancelamento e W.O. seguem tratamento explicável e versionado.
  4. Um administrador precisa suspender o mercado e tentar corrigir a fonte ou recalcular antes de propor uma substituição, apresentando evidências e uma comparação visível com o modelo.
  5. Um segundo administrador, distinto e autenticado com MFA, precisa aprovar a substituição excepcional, produzindo uma nova versão imutável e auditada que não altera odds já aceitas nem mercados definitivamente encerrados.

**Plans**: TBD
**UI hint**: yes

### Phase 5: Créditos Virtuais e Palpites Protegidos

**Goal**: Pessoas elegíveis conseguem registrar um palpite pré-jogo transparente usando somente créditos virtuais, com saldo, elegibilidade e conflitos verificados de forma atômica.
**Mode:** mvp
**Depends on**: Phase 2, Phase 4
**Requirements**: VERI-10, CRED-01, CRED-02, CRED-03, CRED-04, CRED-05, CRED-06, CRED-08, CRED-10, ODDS-14, PRED-01, PRED-02, PRED-03, PRED-04, PRED-05, PRED-06, PRED-07, PRED-08, INTE-01, INTE-02, INTE-03, INTE-04, DATA-03, DATA-04, DATA-05, API-03
**Decisão pendente**: Quantidade inicial de créditos; limites e precisão da aposta; fórmula de pagamento; inclusão ou não da aposta no retorno; arredondamento; e política para conflito identificado tardiamente continuam em aberto. Esta fase não pode escolhê-los implicitamente.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um aluno recém-elegível recebe a mesma concessão única e auditável de créditos virtuais, nunca a recebe novamente por revalidação e não consegue comprar, vender, transferir, sacar ou repor créditos por mecanismos não aprovados.
  2. Um usuário verificado, elegível e sem conflitos consegue selecionar um resultado do mercado antes do horário canônico de fechamento e visualizar um bilhete com seleção, valor, versões da odd aceita e das regras do mercado, retorno virtual potencial e horário de encerramento.
  3. O servidor verifica novamente, de forma atômica, verificação, papéis, conflitos como atleta, estado do mercado, prazo, versão da odd, limites configurados e saldo; tentativas inválidas ou concorrentes são rejeitadas sem saldo negativo, seleção duplicada ou efeitos duplicados no livro-razão.
  4. Um palpite enviado é imutável, seu débito e seus registros aceitos são gravados juntos, e palpites individuais ou agregados permanecem ocultos de outros usuários até o fechamento do mercado.
  5. Quando o preço muda antes do envio, o bilhete aberto é invalidado e o usuário precisa reconfirmar explicitamente a versão atualizada.

**Plans**: TBD
**UI hint**: yes

### Phase 6: Resultados Confirmados, Rankings e Grupos

**Goal**: Resultados esportivos confirmados se transformam em liquidações explicáveis e recuperáveis e em uma competição universitária vitalícia e justa entre participantes verificados.
**Mode:** mvp
**Depends on**: Phase 5
**Requirements**: CRED-07, CRED-09, PRED-09, ODDS-15, SETL-01, SETL-02, SETL-03, SETL-04, SETL-05, SETL-06, SETL-07, SETL-08, SETL-09, SETL-10, SETL-11, RANK-01, RANK-02, RANK-03, RANK-04, RANK-05, RANK-06, RANK-07, RANK-08, POOL-01, POOL-02, POOL-03, POOL-04, ACHV-01, ACHV-02, ACHV-03, TEST-04
**Decisão pendente**: O tratamento de correções após o gasto de créditos pagos incorretamente, a aprovação de ajustes compensatórios diretos no livro-razão e a visibilidade no ranking durante uma verificação expirada continuam em aberto e exigem uma política explícita antes da implementação dos fluxos correspondentes.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um administrador de competição consegue enviar um resultado provisório com evidências, mas um segundo administrador autorizado e distinto precisa confirmá-lo antes de qualquer liquidação.
  2. Usuários conseguem consultar o estado pendente, ganho, perdido, reembolsado ou anulado de cada palpite, juntamente com resultado, regra, cálculo e movimentações de saldo aplicados.
  3. Adiamento, cancelamento, anulação e correção suspendem ou compensam o trabalho afetado sem apagar o histórico; novas tentativas e recuperações não duplicam débitos, reembolsos, pagamentos, conquistas ou efeitos de liquidação.
  4. Uma pessoa consegue visualizar um único ranking vitalício da UFU, ordenado primeiro por palpites definitivamente corretos e depois pela taxa de acerto, com posições compartilhadas nos empates restantes e sem multiplicação dos acertos por créditos ou odds.
  5. Usuários cadastrados conseguem administrar grupos privados que exibem as mesmas métricas do ranking vitalício e conquistar feitos concedidos uma única vez, com regras e edição conquistada exibidas no perfil.

**Plans**: TBD
**UI hint**: yes

### Phase 7: Comunidade Moderada e Prontidão do Piloto

**Goal**: O piloto oferece conversas seguras e responsivas sobre as partidas e está pronto para operar publicamente sem comprometer privacidade, responsabilização ou capacidade de recuperação.
**Mode:** mvp
**Depends on**: Phase 2, Phase 3, Phase 6
**Requirements**: CHAT-01, CHAT-02, CHAT-03, CHAT-04, CHAT-05, CHAT-06, RT-01, INTE-05, PRIV-03, PRIV-04, PRIV-05, OPS-04, TEST-03, TEST-05, UX-01, UX-02
**Decisão pendente**: A escolha dos provedores de nuvem e CI e os fluxos legais, de privacidade, retenção e direitos dos titulares precisam ser encerrados explicitamente antes do lançamento público; este roadmap não os seleciona.
**Success Criteria** (o que deve ser VERDADEIRO):

  1. Um usuário autenticado consegue ler e publicar mensagens de texto persistidas em um canal de partida autorizado, receber atualizações quase em tempo real e carregar o histórico mais antigo por páginas.
  2. Um usuário consegue denunciar uma mensagem ou silenciar outro participante sem notificá-lo, enquanto um moderador no escopo correto consegue remover conteúdo ou suspender e banir um participante com motivo auditável e evidência interna preservada.
  3. Um operador autorizado consegue suspender uma conta, confronto, mercado ou canal sem apagar o histórico necessário.
  4. Fluxos públicos, autenticados, de palpites, histórico, ranking, administração e chat permanecem utilizáveis em dispositivos móveis e computadores e comunicam estados de carregamento, vazio, erro, suspensão e confirmação sem depender apenas de cores.
  5. Verificações de lançamento impedem exposição conhecida de dados pessoais, usam a identidade institucional operacional aprovada, comprovam backups por restauração e bloqueiam a entrega quando falham os gates de qualidade, migração, contrato, arquitetura ou segurança.

**Plans**: TBD
**UI hint**: yes

## Progress

**Ordem de execução:** Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Fundação Segura da Plataforma | 17/29 | In Progress | - |
| 2. Contas, Verificação e Operações Governadas | 0/TBD | Pending | - |
| 3. Experiência Esportiva do Piloto | 0/TBD | Pending | - |
| 4. Mercados Pré-jogo e Odds Transparentes | 0/TBD | Pending | - |
| 5. Créditos Virtuais e Palpites Protegidos | 0/TBD | Pending | - |
| 6. Resultados Confirmados, Rankings e Grupos | 0/TBD | Pending | - |
| 7. Comunidade Moderada e Prontidão do Piloto | 0/TBD | Pending | - |
