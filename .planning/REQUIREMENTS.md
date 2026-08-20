# Requirements: BETUFU

**Defined:** 2026-08-01
**Core Value:** Tornar os jogos universitários da UFU mais envolventes por meio de uma competição social de palpites, justa, transparente e sem dinheiro real.

## v1 Requirements

Requisitos do primeiro lançamento, centrado em uma edição piloto das Olimpíadas UFU, modalidades de confronto direto e créditos exclusivamente virtuais.

### Acesso e Perfis

- [ ] **AUTH-01**: Visitante pode consultar competições, agenda, partidas, equipes e resultados públicos sem criar conta.
- [ ] **AUTH-02**: Pessoa pode criar uma conta pelo fluxo hospedado do Keycloak com e-mail e senha e completar o perfil BETUFU, sem que a aplicação receba ou persista a senha.
- [ ] **AUTH-03**: Usuário pode confirmar o e-mail, entrar, permanecer autenticado entre acessos e encerrar a sessão.
- [ ] **AUTH-04**: Usuário pode recuperar o acesso por um fluxo seguro de redefinição de senha.
- [ ] **PROF-01**: Usuário pode editar apelido e equipe favorita dentro das regras públicas de perfil.
- [ ] **PROF-02**: Perfil público exibe somente apelido, equipe favorita, posição permitida e conquistas, sem revelar e-mail, matrícula, HMAC, comprovante ou identidade civil.

### Matrícula UFU

- [ ] **VERI-06**: A matrícula normalizada gera um HMAC único para impedir duplicidade, sem armazenar o número original de forma consultável.
- [ ] **VERI-07**: Uma matrícula pode estar associada a somente uma conta BETUFU; conflito ou colisão é bloqueado e auditado, sem criar segunda associação.
- [ ] **VERI-09**: Somente conta com matrícula UFU registrada e única pode apostar em eventos internos e integrar o ranking UFU.
- [ ] **VERI-10**: Revalidar o vínculo não concede novamente os créditos iniciais já concedidos à mesma conta elegível.
- [ ] **VERI-11**: Nenhuma etapa de cadastro ou verificação exige login, senha, conta ou SSO oficial da UFU.

### Competições, Equipes e Elencos

- [ ] **SPRT-01**: Administrador da competição pode cadastrar competição, edição, modalidade, categoria, fase e local dentro do escopo atribuído.
- [ ] **SPRT-02**: Administrador da competição pode cadastrar equipes, atletas e vínculos de elenco por edição, modalidade e período.
- [ ] **SPRT-03**: Um atleta pode existir no catálogo sem possuir conta BETUFU.
- [ ] **SPRT-04**: Para incluir um atleta no elenco, o administrador apresenta comprovante aceito ou fonte confiável; o número da matrícula isolado não é prova suficiente.
- [ ] **SPRT-05**: O vínculo comprovado do atleta pode guardar o mesmo HMAC de matrícula usado na verificação, sem expor nem conservar o número consultável.
- [ ] **SPRT-06**: Quando uma conta verificada produzir HMAC igual ao de um atleta ainda sem usuário, o sistema propõe vínculo único e auditável entre os registros.
- [ ] **SPRT-07**: HMAC já ligado a outra conta, múltiplos candidatos ou divergência de dados bloqueiam o vínculo automático e abrem revisão manual.
- [ ] **SPRT-08**: A pessoa vinculada é notificada e pode contestar uma associação de atleta incorreta.
- [ ] **SPRT-09**: Elenco fica bloqueado antes da abertura do primeiro mercado afetado; alteração ou vínculo tardio exige motivo, auditoria e análise das apostas impactadas conforme política de conflito ainda a definir.
- [ ] **SPRT-10**: Administrador pode cadastrar uma partida com participantes, horário canônico, fuso, local, fase e proveniência dos dados.
- [ ] **SPRT-11**: Administrador pode manter a partida como rascunho, publicá-la, suspendê-la, adiá-la ou cancelá-la com motivo auditável.
- [ ] **SPRT-12**: Pessoa pode filtrar a agenda por data, modalidade, categoria, fase e estado e abrir a página completa de uma partida.
- [ ] **SPRT-13**: Pessoa pode abrir página de equipe com modalidade, elenco público autorizado, próximos jogos, resultados e desempenho recente.
- [ ] **SPRT-14**: O piloto libera somente confrontos diretos com ou sem empate cuja tabela, horário, participantes e regra de resultado sejam confiáveis.

### Papéis, Operação e Integridade

- [ ] **OPER-01**: Administrador geral pode conceder e revogar separadamente os papéis de moderador, revisor de matrícula e administrador de competição, sempre com escopo e auditoria.
- [ ] **OPER-02**: Administrador de competição visualiza e opera somente competições atribuídas, salvo permissão superior explicitamente auditada.
- [ ] **OPER-03**: Administrador geral gerencia papéis e configurações da plataforma e não pode registrar apostas em nenhum evento.
- [ ] **OPER-04**: Moderador pode atuar nos chats e denúncias sem receber acesso a comprovantes, saldos ou funções esportivas que não lhe pertençam.
- [ ] **OPER-06**: Painel operacional apresenta pendências, falhas e estados necessários para manter a edição piloto.
- [ ] **INTE-01**: Atleta com vínculo vigente não pode apostar em mercado relacionado a uma partida da própria equipe.
- [ ] **INTE-02**: Administrador de competição não pode apostar em nenhum evento da competição atribuída, ainda que não tenha criado a partida específica.
- [ ] **INTE-03**: Restrições de verificação, papel, escopo, atleta e conflito são validadas no servidor em toda tentativa de aposta.
- [ ] **INTE-04**: Negação de elegibilidade informa motivo adequado ao usuário sem revelar dados privados ou regras exploráveis.
- [ ] **INTE-05**: Administrador autorizado pode suspender conta, partida, mercado ou canal sem apagar o histórico necessário à auditoria.

### Créditos Virtuais

- [ ] **CRED-01**: Todo estudante que se torna elegível recebe exatamente a mesma concessão inicial de créditos, com valor configurável ainda a definir e lançamento auditável.
- [ ] **CRED-02**: A concessão inicial ocorre no máximo uma vez por conta elegível, independentemente de expiração e revalidação posteriores.
- [ ] **CRED-03**: O saldo persiste entre edições e competições e não sofre reset automático no MVP.
- [ ] **CRED-04**: Créditos não possuem símbolo nem valor monetário e não podem ser comprados, vendidos, transferidos, sacados, convertidos ou trocados por prêmio econômico.
- [ ] **CRED-05**: O MVP não oferece missões, recargas, bônus recorrentes, palpite de recuperação ou outra fonte de créditos além da concessão inicial, ganhos de apostas e correções legítimas.
- [ ] **CRED-06**: Usuário com saldo abaixo do mínimo configurado, inclusive saldo zero, fica impedido de apostar e pode permanecer assim por tempo indeterminado no MVP.
- [ ] **CRED-07**: Usuário pode consultar saldo e histórico explicável de concessões, débitos, pagamentos, devoluções, reversões e correções.
- [ ] **CRED-08**: O sistema rejeita aposta acima do saldo disponível e nunca permite saldo negativo, inclusive sob requisições concorrentes e retries.
- [ ] **CRED-09**: Ajuste administrativo serve somente para correção compensatória, exige permissão, motivo e fluxo de aprovação ainda a definir e não pode funcionar como reposição discricionária de saldo.
- [ ] **CRED-10**: Valor mínimo, valor máximo, precisão decimal, fórmula de pagamento, inclusão ou não da stake no retorno e arredondamento são regras determinísticas e versionadas que serão definidas antes da fase de implementação.

### Mercados e Odds

- [ ] **ODDS-01**: Administrador pode criar mercados simples pré-jogo usando templates compatíveis com confronto direto e empate quando aplicável.
- [ ] **ODDS-02**: O sistema calcula automaticamente probabilidades e odds por modalidade e categoria usando baseline Elo versionado.
- [ ] **ODDS-03**: O cálculo pondera resultados recentes, aplica regressão entre edições e limita a influência de confrontos diretos com amostra pequena.
- [ ] **ODDS-04**: Partida cancelada não altera o modelo; W.O. segue tratamento versionado separado e explicável.
- [ ] **ODDS-05**: O volume e a distribuição das apostas dos usuários não alteram probabilidades nem odds no MVP.
- [ ] **ODDS-06**: A margem é configurável, começa em 5% e é aplicada e arredondada de maneira versionada e reproduzível.
- [ ] **ODDS-07**: Dados insuficientes produzem estimativa neutra com indicador de baixa confiança, nunca uma odd manual rotineira.
- [ ] **ODDS-08**: Cada cálculo registra algoritmo, versão, entradas, probabilidades sem margem, margem, odds publicadas, confiança, arredondamento e horário.
- [ ] **ODDS-09**: Pessoa pode consultar odd atual, histórico de versões e explicação legível da metodologia e do nível de confiança.
- [ ] **ODDS-10**: Administrador tenta corrigir a fonte e recalcular antes de solicitar qualquer override; se a distorção persistir, precisa suspender o mercado.
- [ ] **ODDS-11**: Override excepcional exige proposta com motivo e evidência, comparação entre odd do modelo e proposta, probabilidades e delta visível.
- [ ] **ODDS-12**: Override exige aprovação por segundo administrador distinto e cria nova versão imutável e auditada. MFA é opcional no MVP e não condiciona a aprovação.
- [ ] **ODDS-13**: Override pode ocorrer durante suspensão administrativa, mas não após o encerramento definitivo do mercado por prazo ou estado CLOSED/LOCKED, e nunca altera a odd de aposta já aceita.
- [ ] **ODDS-14**: Mudança de versão antes do envio invalida o bilhete aberto e exige reconfirmação explícita do usuário.
- [ ] **ODDS-15**: Somente resultado confirmado e vigente alimenta o rating; correção confirmada recompõe ratings e odds futuras de forma versionada sem alterar odds já aceitas.

### Apostas Pré-jogo

- [ ] **PRED-01**: Usuário verificado e elegível pode escolher um resultado de mercado e informar créditos dentro dos limites configurados antes do fechamento.
- [ ] **PRED-02**: Cada usuário pode ter no máximo uma seleção aceita por mercado e não pode apostar em resultados opostos do mesmo mercado.
- [ ] **PRED-03**: Bilhete mostra partida, seleção, créditos, versão da odd, versão da regra do mercado, retorno virtual potencial e horário de fechamento.
- [ ] **PRED-04**: No envio, o servidor revalida vínculo UFU, conflitos, mercado, prazo, versão da odd, limites e saldo.
- [ ] **PRED-05**: Aceitação registra aposta, débito no ledger e referências imutáveis às versões da odd e da regra do mercado em uma única operação atômica e idempotente.
- [ ] **PRED-06**: Aposta aceita não pode ser editada nem cancelada pelo usuário e não é modificada por versões futuras da odd.
- [ ] **PRED-07**: Seleções individuais e distribuições agregadas permanecem ocultas de outros usuários até o fechamento do mercado.
- [ ] **PRED-08**: O servidor fecha o mercado pelo horário canônico configurado antes da partida e rejeita qualquer tentativa posterior.
- [ ] **PRED-09**: Usuário pode consultar histórico com seleção, créditos, odd aceita, retorno potencial e estado pendente, ganho, perdido, devolvido ou anulado.

### Resultados e Liquidação

- [ ] **SETL-01**: Administrador da competição pode registrar resultado provisório com fonte e evidência operacional mínima.
- [ ] **SETL-02**: Um segundo administrador autorizado e necessariamente distinto confirma ou rejeita o resultado provisório.
- [ ] **SETL-03**: Nenhuma aposta é liquidada antes da confirmação válida pelo segundo administrador.
- [ ] **SETL-04**: Após confirmação, a liquidação aplica a regra versionada do mercado a todas as apostas elegíveis de forma idempotente.
- [ ] **SETL-05**: Partida adiada suspende mercado e liquidação até remarcação ou cancelamento auditável.
- [ ] **SETL-06**: Partida cancelada ou mercado anulado devolve integralmente os créditos uma única vez e não registra acerto nem erro.
- [ ] **SETL-07**: Repetir fechamento, confirmação, liquidação, anulação, devolução ou retry não duplica débito nem pagamento.
- [ ] **SETL-08**: Correção, cancelamento ou anulação posterior cria nova versão proposta e exige confirmação por segundo administrador distinto antes de qualquer reversão, devolução ou reliquidação.
- [ ] **SETL-09**: Usuário pode ver resultado utilizado, regra, cálculo do retorno e lançamentos que alteraram seu saldo.
- [ ] **SETL-10**: Execução de liquidação, devolução ou correção possui estado e progresso duráveis, propriedade temporária de processamento e unidades idempotentes, permitindo diagnóstico e retomada sem apresentar execução parcial como concluída.
- [ ] **SETL-11**: Correção confirmada recompõe, de forma idempotente, ledger, ranking, conquistas e dados futuros do modelo, preservando todas as versões anteriores.

### Ranking UFU, Grupos e Conquistas

- [ ] **RANK-01**: Pessoa pode consultar um único ranking principal UFU, alimentado apenas por apostas feitas enquanto a verificação era válida e mantido de forma vitalícia no MVP.
- [ ] **RANK-02**: O ranking ordena primeiro pelo total de apostas corretas definitivamente liquidadas.
- [ ] **RANK-03**: O primeiro desempate é a maior taxa de acerto, calculada como acertos divididos por apostas definitivamente liquidadas com resultado decisivo.
- [ ] **RANK-04**: Apostas anuladas, canceladas ou devolvidas ficam fora do total de acertos, do total de erros e do denominador da taxa.
- [ ] **RANK-05**: Persistindo empate após a taxa de acerto, os usuários compartilham a mesma posição.
- [ ] **RANK-06**: Cada mercado pode gerar no máximo um acerto por usuário, independentemente dos créditos usados ou da odd aceita.
- [ ] **RANK-07**: Ranking é derivado das liquidações e pode ser reconstruído sem alterar apostas, odds ou ledger.
- [ ] **RANK-08**: Expiração da verificação bloqueia novas apostas sem apagar estatísticas legítimas; a visibilidade no ranking durante a expiração seguirá política ainda a definir.
- [ ] **POOL-01**: Usuário cadastrado pode criar grupo privado para disputar com amigos; somente apostas elegíveis geram estatísticas.
- [ ] **POOL-02**: Usuário pode entrar por convite ou código válido e sair quando a política permitir.
- [ ] **POOL-03**: Criador pode editar nome, convidar ou remover integrantes e encerrar o grupo sem criar moeda, prêmio ou regra financeira própria.
- [ ] **POOL-04**: Grupo exibe um recorte de seus integrantes usando as mesmas métricas, período vitalício e desempates do ranking UFU.
- [ ] **ACHV-01**: Usuário pode obter conquistas por sequência de acertos, previsão de azarão, desempenho em rodada e apoio à equipe favorita.
- [ ] **ACHV-02**: Conquistas derivam de liquidações e odds aceitas e não são concedidas duas vezes quando o processamento é repetido.
- [ ] **ACHV-03**: Perfil apresenta conquistas com nome, descrição, regra e edição em que foram obtidas.

### Chat e Moderação

- [ ] **CHAT-01**: Usuário autenticado pode ler e publicar mensagens de texto no canal disponível de uma partida.
- [ ] **CHAT-02**: Mensagens persistidas aparecem em atualização próxima do tempo real e o histórico é carregado por páginas.
- [ ] **CHAT-03**: Usuário pode denunciar mensagem específica informando um motivo.
- [ ] **CHAT-04**: Usuário pode silenciar outro participante sem notificá-lo nem alterar a visão dos demais.
- [ ] **CHAT-05**: Moderador pode remover mensagem, suspender participante ou bani-lo conforme escopo e motivo auditável.
- [ ] **CHAT-06**: Mensagem moderada preserva metadados internos necessários à auditoria sem continuar visível ao público.

### Arquitetura, Segurança e Qualidade

- [ ] **ARCH-01**: O produto usa monorepo com `backend/`, `frontend/`, `infra/` e `docs/`, frontend Angular 22/TypeScript 6.0.x e backend Java 25/Spring Boot 4.1/Maven.
- [ ] **ARCH-02**: O backend é um monólito modular Spring Modulith 2.1 com limites explícitos entre identidade, verificação, esportes, odds, apostas, ledger, liquidação, ranking, chat e auditoria.
- [ ] **ARCH-03**: Testes automatizados impedem dependências não autorizadas entre módulos; microsserviços, Kafka e Kubernetes não integram o MVP.
- [ ] **IDEN-01**: Keycloak administra autenticação, credenciais, recuperação, verificação de e-mail, sessões e MFA; a BETUFU administra perfil, papéis de domínio, vínculo UFU e elegibilidade.
- [ ] **IDEN-02**: A aplicação correlaciona o par estável `(iss, sub)` do Keycloak ao usuário BETUFU e não usa e-mail como identificador imutável.
- [ ] **IDEN-03**: Spring atua como BFF OIDC, mantém tokens e sessão no servidor e nunca exige que o frontend armazene access ou refresh token.
- [ ] **IDEN-04**: Contrato de sessão BFF define TTL ocioso e absoluto, regeneração, logout e revogação, state, nonce, PKCE, política SameSite e CSRF inclusive no STOMP CONNECT.
- [ ] **IDEN-05**: Comando administrativo sensível exige papel vigente e fica auditado; concessão ou revogação de papel invalida as sessões da identidade afetada. MFA é opcional no MVP e não condiciona nenhum comando.
- [ ] **DATA-01**: PostgreSQL 18 é a fonte oficial de saldo, elegibilidade, odds, aposta, resultado, liquidação, ranking e chat.
- [ ] **DATA-02**: Toda alteração de schema ocorre por migration Flyway versionada e validada.
- [ ] **DATA-03**: Comando crítico valida e bloqueia mercado, versão da odd, elegibilidade, conflito e saldo autoritativos dentro da mesma transação PostgreSQL.
- [ ] **DATA-04**: Nenhuma alteração de saldo ocorre sem lançamento correspondente no ledger, e registros históricos não podem ser sobrescritos ou apagados.
- [ ] **DATA-05**: Constraints e chaves de negócio impedem concessão inicial, seleção por usuário/mercado, lançamento de ledger, pagamento, devolução e liquidação duplicados, inclusive sob concorrência e retries.
- [ ] **DATA-06**: Redis permanece auxiliar; comprovantes usam armazenamento S3 privado; BETUFU e Keycloak usam bancos e usuários separados.
- [ ] **PRIV-01**: Matrículas, HMACs, documentos, tokens, chaves e dados privados não aparecem em URLs, conteúdo público, telemetria ou logs operacionais comuns.
- [ ] **PRIV-02**: Comprovantes de conta e de atleta ficam criptografados em armazenamento privado, usam acesso temporário autorizado e são excluídos após a decisão final segundo política que também cobre versões e backups.
- [ ] **PRIV-03**: Checklist de publicação verifica páginas, builds, imagens, documentos, metadados e histórico destinado a repositório público contra identificadores pessoais definidos e bloqueia exposição conhecida do fundador.
- [ ] **PRIV-04**: Domínio, infraestrutura, e-mails e presença pública usam identidade institucional legítima, mantendo dados verdadeiros onde contratos e lei exigirem.
- [ ] **PRIV-05**: Lançamento público depende de revisão jurídica e de fluxos testáveis para aviso, acesso, correção, oposição, exclusão ou pseudonimização e retenção por classe de dado, incluindo atleta sem conta, logs, ledger, auditoria e backups.
- [ ] **API-01**: O backend publica REST JSON versionado em `/api/v1`, contrato OpenAPI e erros compatíveis com Problem Details.
- [ ] **API-02**: O frontend consome cliente TypeScript gerado do OpenAPI e o pipeline detecta divergência de contrato.
- [ ] **API-03**: Comando crítico usa idempotência persistente por ator, tipo e chave, vincula fingerprint do payload, repete a resposta original e rejeita reutilização concorrente com payload diferente.
- [ ] **RT-01**: WebSocket/STOMP respeita sessão, origem e autorização de domínio, e mensagens do chat são persistidas no PostgreSQL.
- [ ] **AUDT-01**: Evento auditável registra ator, papel e MFA, alvo, ação, versão anterior e nova, motivo, evidência, horário, correlation ID e chave de idempotência quando aplicável.
- [ ] **AUDT-02**: Trilha de auditoria possui acesso restrito, proteção contra alteração e retenção definida sem armazenar segredos ou dados pessoais desnecessários.
- [ ] **OPS-01**: Desenvolvimento oferece dois modos reproduzíveis. No modo diário padrão, Docker Compose executa PostgreSQL, Redis, Keycloak, SeaweedFS como implementação S3 exclusivamente local e serviço de e-mail, enquanto Spring Boot e Angular rodam pelo terminal ou pela IDE. No perfil completo de validação, Spring também roda em contêiner e o Angular compilado é servido por Nginx. Ambos usam wrappers, versões de runtime e imagens aprovados e fixados antes da implementação ou implantação correspondente.
- [ ] **OPS-02**: Desenvolvimento, homologação e produção possuem configurações separadas, e segredos nunca são versionados.
- [ ] **OPS-03**: Aplicação fornece health checks, logs estruturados, correlation ID e métricas sem dados sensíveis.
- [ ] **OPS-04**: Backups de dados BETUFU, Keycloak e objetos privados são automáticos, com RPO e RTO definidos, e a restauração é testada antes do lançamento.
- [ ] **TEST-01**: Regras de domínio possuem testes JUnit e transações integradas usam PostgreSQL e dependências reais via Testcontainers.
- [ ] **TEST-02**: Spring Modulith Test e ArchUnit verificam limites modulares e dependências permitidas.
- [ ] **TEST-03**: Contrato OpenAPI, componentes Angular e jornadas completas críticas possuem testes automatizados, incluindo Playwright.
- [ ] **TEST-04**: Testes concorrentes cobrem aposta, saldo, liquidação e devolução; perfil de carga, SLO e taxa máxima de erro são definidos e verificados antes do lançamento.
- [ ] **TEST-05**: Pipeline bloqueia entrega com falha em testes, migrations, contrato, arquitetura ou achado de segurança acima da severidade definida.
- [ ] **UX-01**: Fluxos públicos, autenticação, aposta, histórico, ranking, administração e chat funcionam em navegadores móveis e desktop.
- [ ] **UX-02**: Interface apresenta estados de carregamento, vazio, erro, suspensão e confirmação sem depender apenas de cor.

## v2 Requirements

Requisitos reconhecidos, mas deliberadamente fora do primeiro roadmap.

### Verificação documental do vínculo UFU

Adiados do MVP em 2026-08-19. No MVP a matrícula é autodeclarada e a plataforma verifica somente a unicidade por HMAC (`VERI-06`, `VERI-07`), sem comprovante, sem revisor e sem prazo de validade.

- **VERI-01**: Usuário pode solicitar voluntariamente a verificação UFU enviando um comprovante de matrícula aceito pela política vigente.
- **VERI-02**: O envio valida tamanho, extensão, tipo real de conteúdo e ameaças antes de armazenar o comprovante em área privada.
- **VERI-03**: Somente revisor de matrícula autorizado obtém acesso temporário ao comprovante, e cada acesso fica auditado.
- **VERI-04**: Revisor pode aprovar ou rejeitar a solicitação com motivo, fonte da decisão, validade e datas registradas.
- **VERI-05**: Usuário pode acompanhar os estados pendente, aprovado, rejeitado e expirado e reenviar documentação quando a política permitir.
- **VERI-08**: O comprovante e o número de matrícula temporariamente processado são excluídos após a decisão final conforme prazo operacional definido, com sucesso ou falha da exclusão rastreável.
- **VERI-12**: Revisor não pode decidir a própria solicitação nem solicitação em que possua conflito registrado; a tentativa é bloqueada e auditada.
- **OPER-05**: Revisor de matrícula pode analisar comprovantes sem receber permissão para alterar odds, resultados ou créditos.

### Engajamento posterior

- **NOTF-01**: Usuário recebe notificações de início próximo, liquidação, vitória, resposta no chat e mudança de posição.
- **DSPT-01**: Administradores usam uma janela formal de contestação e correção antes da liquidação definitiva.
- **CREX-01**: Produto pode testar missões finitas ou atividades para obter créditos somente após nova decisão econômica e controles contra exploração.

### Expansão esportiva

- **MULT-01**: Usuário pode apostar em mercados para provas com vários competidores, com regras de pódio, empate, desistência e anulação.
- **MODL-01**: Modalidades com dados adequados podem adotar modelos estatísticos próprios no lugar do baseline Elo.
- **SRC-01**: Operador pode integrar fontes oficiais após acordo, proveniência, segurança e nível de serviço definidos.

### Plataforma

- **NATV-01**: Usuário pode acessar aplicativos nativos quando uso recorrente justificar novas superfícies.
- **PASS-01**: Usuário comum pode usar MFA ou passkeys quando o produto priorizar autenticação reforçada além dos administradores.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Dinheiro real, depósitos, saques, conversão ou prêmio econômico | Mudariam materialmente o risco, a operação e o enquadramento; exigiriam outro projeto e análise jurídica própria. |
| Compra, venda ou transferência de créditos | Criariam valor paralelo, fraude e desigualdade. |
| Palpite de recuperação | A mecânica foi explicitamente rejeitada. |
| Missões ou fontes extras de créditos no MVP | Permanecem apenas como ideia futura, sem autorização para implementação. |
| Reset automático de saldo no MVP | O saldo será persistente por enquanto. |
| Apostas ao vivo | Exigiriam feed de baixa latência e operação contínua incompatíveis com o piloto. |
| Apostas múltiplas ou acumuladas | Multiplicariam regras de cancelamento e aproximariam o produto de um sportsbook tradicional. |
| Mercados criados por usuários | Abririam espaço para manipulação e liquidação inconsistente. |
| Edição manual rotineira ou override unilateral de odds | Odds são automáticas; exceção exige mercado suspenso, justificativa e duas pessoas distintas. |
| Alteração retroativa da odd aceita | Destruiria reprodutibilidade e tratamento uniforme das apostas. |
| Chat anônimo, global ou sem moderação | Aumentaria abuso e removeria o contexto da partida. |
| Login, senha ou SSO oficial da UFU | A BETUFU possui identidade própria e não presume integração institucional. |
| Branding ou comunicação que indiquem vínculo oficial com a UFU | A plataforma é independente e deve evitar confusão institucional. |
| Anonimato absoluto do operador perante provedores ou autoridades | Não é tecnicamente nem juridicamente garantível; a proteção acordada é contra exposição pública pessoal. |
| Microsserviços, Kafka ou Kubernetes no MVP | Acrescentariam operação distribuída sem necessidade comprovada. |

## Traceability

Mapa de planejamento aprovado. Cada requisito v1 mapeia para exatamente uma fase do roadmap aprovado em 2026-08-01.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ARCH-01 | Phase 1 | Pending |
| ARCH-02 | Phase 1 | Pending |
| ARCH-03 | Phase 1 | Pending |
| IDEN-01 | Phase 1 | Pending |
| IDEN-02 | Phase 1 | Pending |
| IDEN-03 | Phase 1 | Pending |
| IDEN-04 | Phase 1 | Pending |
| DATA-01 | Phase 1 | Pending |
| DATA-02 | Phase 1 | Pending |
| DATA-06 | Phase 1 | Pending |
| API-01 | Phase 1 | Pending |
| API-02 | Phase 1 | Pending |
| OPS-01 | Phase 1 | Pending |
| OPS-02 | Phase 1 | Pending |
| OPS-03 | Phase 1 | Pending |
| TEST-01 | Phase 1 | Pending |
| TEST-02 | Phase 1 | Pending |
| AUTH-02 | Phase 2 | Pending |
| AUTH-03 | Phase 2 | Pending |
| AUTH-04 | Phase 2 | Pending |
| PROF-01 | Phase 2 | Pending |
| PROF-02 | Phase 2 | Pending |
| VERI-06 | Phase 2 | Pending |
| VERI-07 | Phase 2 | Pending |
| VERI-09 | Phase 2 | Pending |
| VERI-11 | Phase 2 | Pending |
| OPER-01 | Phase 2 | Pending |
| OPER-02 | Phase 2 | Pending |
| OPER-03 | Phase 2 | Pending |
| OPER-04 | Phase 2 | Pending |
| OPER-06 | Phase 2 | Pending |
| IDEN-05 | Phase 2 | Pending |
| PRIV-01 | Phase 2 | Pending |
| AUDT-01 | Phase 2 | Pending |
| AUDT-02 | Phase 2 | Pending |
| AUTH-01 | Phase 3 | Pending |
| SPRT-01 | Phase 3 | Pending |
| SPRT-02 | Phase 3 | Pending |
| SPRT-03 | Phase 3 | Pending |
| SPRT-04 | Phase 3 | Pending |
| SPRT-05 | Phase 3 | Pending |
| SPRT-06 | Phase 3 | Pending |
| SPRT-07 | Phase 3 | Pending |
| SPRT-08 | Phase 3 | Pending |
| SPRT-09 | Phase 3 | Pending |
| SPRT-10 | Phase 3 | Pending |
| SPRT-11 | Phase 3 | Pending |
| SPRT-12 | Phase 3 | Pending |
| SPRT-13 | Phase 3 | Pending |
| SPRT-14 | Phase 3 | Pending |
| PRIV-02 | Phase 3 | Pending |
| ODDS-01 | Phase 4 | Pending |
| ODDS-02 | Phase 4 | Pending |
| ODDS-03 | Phase 4 | Pending |
| ODDS-04 | Phase 4 | Pending |
| ODDS-05 | Phase 4 | Pending |
| ODDS-06 | Phase 4 | Pending |
| ODDS-07 | Phase 4 | Pending |
| ODDS-08 | Phase 4 | Pending |
| ODDS-09 | Phase 4 | Pending |
| ODDS-10 | Phase 4 | Pending |
| ODDS-11 | Phase 4 | Pending |
| ODDS-12 | Phase 4 | Pending |
| ODDS-13 | Phase 4 | Pending |
| VERI-10 | Phase 5 | Pending |
| CRED-01 | Phase 5 | Pending |
| CRED-02 | Phase 5 | Pending |
| CRED-03 | Phase 5 | Pending |
| CRED-04 | Phase 5 | Pending |
| CRED-05 | Phase 5 | Pending |
| CRED-06 | Phase 5 | Pending |
| CRED-08 | Phase 5 | Pending |
| CRED-10 | Phase 5 | Pending |
| ODDS-14 | Phase 5 | Pending |
| PRED-01 | Phase 5 | Pending |
| PRED-02 | Phase 5 | Pending |
| PRED-03 | Phase 5 | Pending |
| PRED-04 | Phase 5 | Pending |
| PRED-05 | Phase 5 | Pending |
| PRED-06 | Phase 5 | Pending |
| PRED-07 | Phase 5 | Pending |
| PRED-08 | Phase 5 | Pending |
| INTE-01 | Phase 5 | Pending |
| INTE-02 | Phase 5 | Pending |
| INTE-03 | Phase 5 | Pending |
| INTE-04 | Phase 5 | Pending |
| DATA-03 | Phase 5 | Pending |
| DATA-04 | Phase 5 | Pending |
| DATA-05 | Phase 5 | Pending |
| API-03 | Phase 5 | Pending |
| CRED-07 | Phase 6 | Pending |
| CRED-09 | Phase 6 | Pending |
| PRED-09 | Phase 6 | Pending |
| ODDS-15 | Phase 6 | Pending |
| SETL-01 | Phase 6 | Pending |
| SETL-02 | Phase 6 | Pending |
| SETL-03 | Phase 6 | Pending |
| SETL-04 | Phase 6 | Pending |
| SETL-05 | Phase 6 | Pending |
| SETL-06 | Phase 6 | Pending |
| SETL-07 | Phase 6 | Pending |
| SETL-08 | Phase 6 | Pending |
| SETL-09 | Phase 6 | Pending |
| SETL-10 | Phase 6 | Pending |
| SETL-11 | Phase 6 | Pending |
| RANK-01 | Phase 6 | Pending |
| RANK-02 | Phase 6 | Pending |
| RANK-03 | Phase 6 | Pending |
| RANK-04 | Phase 6 | Pending |
| RANK-05 | Phase 6 | Pending |
| RANK-06 | Phase 6 | Pending |
| RANK-07 | Phase 6 | Pending |
| RANK-08 | Phase 6 | Pending |
| POOL-01 | Phase 6 | Pending |
| POOL-02 | Phase 6 | Pending |
| POOL-03 | Phase 6 | Pending |
| POOL-04 | Phase 6 | Pending |
| ACHV-01 | Phase 6 | Pending |
| ACHV-02 | Phase 6 | Pending |
| ACHV-03 | Phase 6 | Pending |
| TEST-04 | Phase 6 | Pending |
| CHAT-01 | Phase 7 | Pending |
| CHAT-02 | Phase 7 | Pending |
| CHAT-03 | Phase 7 | Pending |
| CHAT-04 | Phase 7 | Pending |
| CHAT-05 | Phase 7 | Pending |
| CHAT-06 | Phase 7 | Pending |
| RT-01 | Phase 7 | Pending |
| INTE-05 | Phase 7 | Pending |
| PRIV-03 | Phase 7 | Pending |
| PRIV-04 | Phase 7 | Pending |
| PRIV-05 | Phase 7 | Pending |
| OPS-04 | Phase 7 | Pending |
| TEST-03 | Phase 7 | Pending |
| TEST-05 | Phase 7 | Pending |
| UX-01 | Phase 7 | Pending |
| UX-02 | Phase 7 | Pending |

**Coverage:**
- v1 requirements: 137 total
- Mapped to phases: 137
- Unmapped: 0 ✓

---
*Requisitos definidos em: 2026-08-01*
*Última atualização: 2026-08-20 após tornar o MFA opcional em todo o MVP (IDEN-05 e ODDS-12)*
