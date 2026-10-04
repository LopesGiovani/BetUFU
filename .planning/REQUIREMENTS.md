# Requirements: BETUFU

**Defined:** 2026-08-01
**Core Value:** Tornar os jogos universitários da UFU mais envolventes por meio de uma competição social de palpites, justa, transparente e sem dinheiro real.

## v1 Requirements

Requisitos do primeiro lançamento, centrado em uma edição piloto das Olimpíadas UFU, modalidades de confronto direto e créditos exclusivamente virtuais.

### Acesso e Perfis

- [x] **AUTH-01**: Visitante pode consultar competições, agenda, partidas, equipes e resultados públicos sem criar conta.
- [x] **AUTH-02**: Pessoa pode criar uma conta pelo fluxo hospedado do Keycloak com e-mail e senha e completar o perfil BETUFU, sem que a aplicação receba ou persista a senha.
- [x] **AUTH-03**: Usuário pode confirmar o e-mail, entrar, permanecer autenticado entre acessos e encerrar a sessão.
- [x] **AUTH-04**: Usuário pode recuperar o acesso por um fluxo seguro de redefinição de senha.
- [x] **PROF-01**: Usuário pode editar o apelido dentro das regras públicas de perfil; a referência opcional reservada para favorito é completada pela Fase 3 em `SPRT-15`.
- [x] **PROF-02**: Perfil público básico exibe somente apelido, posição permitida e conquistas, sem revelar e-mail, matrícula, HMAC, comprovante ou identidade civil; favorito e extensão esportiva pertencem a `SPRT-15` e `SPRT-16`.

### Matrícula UFU

- [x] **VERI-06**: A matrícula normalizada gera um HMAC único para impedir duplicidade, sem armazenar o número original de forma consultável.
- [x] **VERI-07**: Uma matrícula pode estar associada a somente uma conta BETUFU; conflito ou colisão é bloqueado e auditado, sem criar segunda associação.
- [x] **VERI-09**: Somente conta com matrícula UFU registrada e única pode apostar em eventos internos e integrar o ranking UFU.
- [x] **VERI-10**: Revalidar o vínculo não concede novamente os créditos iniciais já concedidos à mesma conta elegível.
- [x] **VERI-11**: Nenhuma etapa de cadastro ou verificação exige login, senha, conta ou SSO oficial da UFU.

### Competições, Equipes e Elencos

- [x] **SPRT-01**: Administrador autorizado pode cadastrar esporte, competição, edição, categoria, fase e local reutilizável com espaço interno opcional dentro do escopo atribuído; a Olimpíada UFU 2026 é a primeira edição real de um catálogo genérico.
- [x] **SPRT-02**: Administrador da competição pode cadastrar organizações esportivas, equipes por edição, atletas e vínculos de elenco por modalidade, categoria e período, individualmente ou por CSV oficial validado de forma atômica.
- [x] **SPRT-03**: Um atleta pode existir no catálogo sem possuir conta BETUFU.
- [x] **SPRT-04**: Para cadastrar um atleta, o administrador informa a matrícula somente como entrada transitória; o servidor normaliza, calcula HMAC versionado e descarta o valor bruto. A coincidência não comprova vínculo oficial, titularidade ou legitimidade esportiva.
- [x] **SPRT-05**: O cadastro do atleta pode guardar o mesmo HMAC versionado usado na verificação da conta, sem expor nem conservar o número consultável e sem receber comprovante documental no MVP.
- [x] **SPRT-06**: Quando uma conta verificada produzir HMAC igual ao de um atleta ainda sem usuário, o sistema propõe vínculo único e auditável entre os registros.
- [x] **SPRT-07**: HMAC já ligado a outra conta, múltiplos candidatos ou divergência de dados bloqueiam o vínculo automático e abrem revisão manual.
- [x] **SPRT-08**: A pessoa recebe a proposta de vínculo, precisa aceitá-la explicitamente e pode rejeitar ou contestar uma associação de atleta incorreta; foto e campos pessoais do atleta só são liberados após o aceite.
- [x] **SPRT-09**: Elenco nasce como rascunho, é publicado manualmente antes da abertura do primeiro mercado afetado e não possui escalação por partida no MVP; alteração ou vínculo tardio exige motivo e auditoria.
- [x] **SPRT-10**: Administrador pode cadastrar evento com participantes, horário canônico, fuso, fase, fonte e local opcionalmente detalhado por espaço interno.
- [x] **SPRT-11**: Administrador pode manter evento e resultado como rascunho, publicar, suspender, adiar, cancelar, informar e corrigir com fonte, motivo, versões e auditoria; o mesmo administrador pode confirmar no MVP ou pedir revisão opcional.
- [x] **SPRT-12**: Pessoa pode navegar primeiro pela agenda e filtrar por data, esporte, competição/edição, categoria, fase e estado, abrindo a página completa do evento.
- [x] **SPRT-13**: Pessoa pode abrir páginas de organização, equipe e atleta com equipes da edição, elenco público autorizado, próximos eventos, resultados e estatísticas genéricas confiáveis; campos pessoais opcionais são controlados pelo atleta vinculado.
- [x] **SPRT-14**: O piloto opera detalhadamente eventos de confronto direto confiáveis, incluindo chaveamento derivado das partidas; outras modalidades podem aparecer no catálogo e agenda básica, e classificações de grupos/pontos corridos são transcritas da fonte oficial sem motor automático de desempate.
- [x] **SPRT-15**: Usuário pode escolher como favorito uma organização esportiva permanente, que agrega suas equipes de diferentes modalidades e edições; a Fase 3 reconcilia a referência técnica de equipe criada na Fase 2 sem perder o perfil existente.
- [x] **SPRT-16**: Após aceitar o vínculo, somente o atleta controla foto, curso, biografia e redes sociais opcionais; mídia e links recebem validação segura, campos removíveis não incluem dados privados e estatísticas públicas permanecem genéricas no MVP.

### Papéis, Operação e Integridade

- [x] **OPER-01**: Administrador geral pode conceder e revogar separadamente os papéis de moderador e administrador de competição, sempre com escopo e auditoria. O papel de revisor de matrícula saiu do MVP junto com a verificação documental (ver `OPER-05` na v2).
- [x] **OPER-02**: Administrador de competição visualiza e opera somente competições atribuídas, salvo permissão superior explicitamente auditada.
- [x] **OPER-03**: Administrador geral gerencia papéis e configurações da plataforma e não pode registrar apostas em nenhum evento.
- [x] **OPER-04**: Moderador pode atuar nos chats e denúncias sem receber acesso a comprovantes, saldos ou funções esportivas que não lhe pertençam.
- [x] **OPER-06**: Painel operacional apresenta pendências, falhas e estados necessários para manter a edição piloto.
- [x] **INTE-01**: No MVP, vínculo vigente de atleta não bloqueia apostas, inclusive em evento ou equipe de que participe; a relação atleta–equipe–evento permanece autoritativa para permitir reavaliação futura da política.
- [x] **INTE-02**: Administrador de competição não pode apostar em nenhum evento da competição atribuída, ainda que não tenha criado a partida específica.
- [x] **INTE-03**: Restrições vigentes de verificação, papel, escopo, estado do mercado e conflitos administrativos são validadas no servidor em toda tentativa de aposta; vínculo de atleta é permitido pela política do MVP.
- [x] **INTE-04**: Negação de elegibilidade informa motivo adequado ao usuário sem revelar dados privados ou regras exploráveis.
- [ ] **INTE-05**: Administrador autorizado pode suspender conta, partida, mercado ou canal sem apagar o histórico necessário à auditoria.

### Créditos Virtuais

- [x] **CRED-01**: Todo estudante que se torna elegível recebe exatamente a mesma concessão inicial de créditos, com valor configurável ainda a definir e lançamento auditável.
- [x] **CRED-02**: A concessão inicial ocorre no máximo uma vez por conta elegível, independentemente de expiração e revalidação posteriores.
- [x] **CRED-03**: O saldo persiste entre edições e competições e não sofre reset automático no MVP.
- [x] **CRED-04**: Créditos não possuem símbolo nem valor monetário e não podem ser comprados, vendidos, transferidos, sacados, convertidos ou trocados por prêmio econômico.
- [x] **CRED-05**: O MVP não oferece missões, recargas, bônus recorrentes, palpite de recuperação ou outra fonte de créditos além da concessão inicial, ganhos de apostas e correções legítimas.
- [x] **CRED-06**: Usuário com saldo abaixo do mínimo configurado, inclusive saldo zero, fica impedido de apostar e pode permanecer assim por tempo indeterminado no MVP.
- [x] **CRED-07**: Usuário pode consultar saldo e histórico explicável de concessões, débitos, pagamentos, devoluções, reversões e correções.
- [x] **CRED-08**: O sistema rejeita aposta acima do saldo disponível e nunca permite saldo negativo, inclusive sob requisições concorrentes e retries.
- [x] **CRED-09**: Ajuste administrativo serve somente para correção compensatória, exige permissão, motivo e fluxo de aprovação ainda a definir e não pode funcionar como reposição discricionária de saldo.
- [x] **CRED-10**: Valor mínimo, valor máximo, precisão decimal, fórmula de pagamento, inclusão ou não da stake no retorno e arredondamento são regras determinísticas e versionadas que serão definidas antes da fase de implementação.

### Mercados e Odds

- [x] **ODDS-01**: Administrador pode criar mercados simples pré-jogo usando templates compatíveis com confronto direto e empate quando aplicável.
- [x] **ODDS-02**: O sistema calcula automaticamente probabilidades e odds por modalidade e categoria usando baseline Elo versionado.
- [x] **ODDS-03**: O cálculo pondera resultados recentes, aplica regressão entre edições e limita a influência de confrontos diretos com amostra pequena.
- [x] **ODDS-04**: Partida cancelada não altera o modelo; W.O. segue tratamento versionado separado e explicável.
- [x] **ODDS-05**: O volume e a distribuição das apostas dos usuários não alteram probabilidades nem odds no MVP.
- [x] **ODDS-06**: A margem é configurável, começa em 5% e é aplicada e arredondada de maneira versionada e reproduzível.
- [x] **ODDS-07**: Dados insuficientes produzem estimativa neutra com indicador de baixa confiança, nunca uma odd manual rotineira.
- [x] **ODDS-08**: Cada cálculo registra algoritmo, versão, entradas, probabilidades sem margem, margem, odds publicadas, confiança, arredondamento e horário.
- [x] **ODDS-09**: Pessoa pode consultar odd atual, histórico de versões e explicação legível da metodologia e do nível de confiança.
- [x] **ODDS-10**: Administrador tenta corrigir a fonte e recalcular antes de solicitar qualquer override; se a distorção persistir, precisa suspender o mercado.
- [x] **ODDS-11**: Override excepcional exige proposta com motivo e evidência, comparação entre odd do modelo e proposta, probabilidades e delta visível.
- [x] **ODDS-12**: Override exige aprovação por segundo administrador distinto e cria nova versão imutável e auditada. MFA é opcional no MVP e não condiciona a aprovação.
- [x] **ODDS-13**: Override pode ocorrer durante suspensão administrativa, mas não após o encerramento definitivo do mercado por prazo ou estado CLOSED/LOCKED, e nunca altera a odd de aposta já aceita.
- [x] **ODDS-14**: Mudança de versão antes do envio invalida o bilhete aberto e exige reconfirmação explícita do usuário.
- [x] **ODDS-15**: Somente resultado confirmado e vigente alimenta o rating; correção confirmada recompõe ratings e odds futuras de forma versionada sem alterar odds já aceitas.

### Apostas Pré-jogo

- [x] **PRED-01**: Usuário verificado e elegível pode escolher um resultado de mercado e informar créditos dentro dos limites configurados antes do fechamento.
- [x] **PRED-02**: Cada usuário pode ter no máximo uma seleção aceita por mercado e não pode apostar em resultados opostos do mesmo mercado.
- [x] **PRED-03**: Bilhete mostra partida, seleção, créditos, versão da odd, versão da regra do mercado, retorno virtual potencial e horário de fechamento.
- [x] **PRED-04**: No envio, o servidor revalida vínculo UFU, conflitos, mercado, prazo, versão da odd, limites e saldo.
- [x] **PRED-05**: Aceitação registra aposta, débito no ledger e referências imutáveis às versões da odd e da regra do mercado em uma única operação atômica e idempotente.
- [x] **PRED-06**: Aposta aceita não pode ser editada nem cancelada pelo usuário e não é modificada por versões futuras da odd.
- [x] **PRED-07**: Seleções individuais e distribuições agregadas permanecem ocultas de outros usuários até o fechamento do mercado.
- [x] **PRED-08**: O servidor fecha o mercado pelo horário canônico configurado antes da partida e rejeita qualquer tentativa posterior.
- [x] **PRED-09**: Usuário pode consultar histórico com seleção, créditos, odd aceita, retorno potencial e estado pendente, ganho, perdido, devolvido ou anulado.

### Resultados e Liquidação

- [x] **SETL-01**: A liquidação consome exatamente uma versão vigente do resultado esportivo já publicada e confirmada na Fase 3, preservando sua fonte e identificador; não cria um segundo cadastro de resultado.
- [x] **SETL-02**: A autorização para liquidar valida novamente permissão, estado e versão do resultado; no MVP, o mesmo administrador que registrou ou confirmou o resultado esportivo pode autorizar essa versão, com atores persistidos separadamente para futura política de duas pessoas.
- [x] **SETL-03**: Nenhuma aposta é liquidada antes de existir versão esportiva confirmada e autorização de liquidação válida e auditada; segundo administrador distinto não é obrigatório no MVP.
- [x] **SETL-04**: Após confirmação, a liquidação aplica a regra versionada do mercado a todas as apostas elegíveis de forma idempotente.
- [x] **SETL-05**: Partida adiada suspende mercado e liquidação até remarcação ou cancelamento auditável.
- [x] **SETL-06**: Partida cancelada ou mercado anulado devolve integralmente os créditos uma única vez e não registra acerto nem erro.
- [x] **SETL-07**: Repetir fechamento, confirmação, liquidação, anulação, devolução ou retry não duplica débito nem pagamento.
- [x] **SETL-08**: Correção, cancelamento ou anulação posterior nasce como nova versão no domínio esportivo; somente depois de publicada e confirmada pode autorizar reversão, devolução ou reliquidação auditada. O MVP permite o mesmo administrador e preserva atores separados para futura segregação de função.
- [x] **SETL-09**: Usuário pode ver resultado utilizado, regra, cálculo do retorno e lançamentos que alteraram seu saldo.
- [x] **SETL-10**: Execução de liquidação, devolução ou correção possui estado e progresso duráveis, propriedade temporária de processamento e unidades idempotentes, permitindo diagnóstico e retomada sem apresentar execução parcial como concluída.
- [x] **SETL-11**: Correção confirmada recompõe, de forma idempotente, ledger, ranking, conquistas e dados futuros do modelo, preservando todas as versões anteriores.

### Ranking UFU, Grupos e Conquistas

- [x] **RANK-01**: Pessoa pode consultar um único ranking principal UFU, alimentado apenas por apostas feitas enquanto a verificação era válida e mantido de forma vitalícia no MVP.
- [x] **RANK-02**: O ranking ordena primeiro pelo total de apostas corretas definitivamente liquidadas.
- [x] **RANK-03**: O primeiro desempate é a maior taxa de acerto, calculada como acertos divididos por apostas definitivamente liquidadas com resultado decisivo.
- [x] **RANK-04**: Apostas anuladas, canceladas ou devolvidas ficam fora do total de acertos, do total de erros e do denominador da taxa.
- [x] **RANK-05**: Persistindo empate após a taxa de acerto, os usuários compartilham a mesma posição.
- [x] **RANK-06**: Cada mercado pode gerar no máximo um acerto por usuário, independentemente dos créditos usados ou da odd aceita.
- [x] **RANK-07**: Ranking é derivado das liquidações e pode ser reconstruído sem alterar apostas, odds ou ledger.
- [x] **RANK-08**: Expiração da verificação bloqueia novas apostas sem apagar estatísticas legítimas; a visibilidade no ranking durante a expiração seguirá política ainda a definir.
- [x] **POOL-01**: Usuário cadastrado pode criar grupo privado para disputar com amigos; somente apostas elegíveis geram estatísticas.
- [x] **POOL-02**: Usuário pode entrar por convite ou código válido e sair quando a política permitir.
- [x] **POOL-03**: Criador pode editar nome, convidar ou remover integrantes e encerrar o grupo sem criar moeda, prêmio ou regra financeira própria.
- [x] **POOL-04**: Grupo exibe um recorte de seus integrantes usando as mesmas métricas, período vitalício e desempates do ranking UFU.
- [x] **ACHV-01**: Usuário pode obter conquistas por sequência de acertos, previsão de azarão, desempenho em rodada e apoio à organização esportiva favorita.
- [x] **ACHV-02**: Conquistas derivam de liquidações e odds aceitas e não são concedidas duas vezes quando o processamento é repetido.
- [x] **ACHV-03**: Perfil apresenta conquistas com nome, descrição, regra e edição em que foram obtidas.

### Chat e Moderação

- [ ] **CHAT-01**: Partida publicada oferece leitura pública; a sala global exige sessão para leitura, inclusive para matrícula inativa. Publicar texto em ambos os canais exige usuário autenticado com matrícula ATIVO.
- [ ] **CHAT-02**: Mensagens persistidas aparecem em atualização próxima do tempo real e o histórico é carregado por páginas.
- [ ] **CHAT-03**: Usuário pode denunciar mensagem específica informando um motivo.
- [ ] **CHAT-04**: Usuário pode silenciar outro participante sem notificá-lo nem alterar a visão dos demais.
- [ ] **CHAT-05**: Moderador pode revisar denúncias, remover mensagens e escalar casos somente no escopo autorizado; apenas ADMINISTRADOR_GERAL aplica suspensões globais de escrita no chat (24h, 7 dias ou banimento de chat), com motivo e auditoria, sem alterar saldo, ranking ou histórico.
- [ ] **CHAT-06**: Mensagem moderada preserva metadados internos necessários à auditoria sem continuar visível ao público.

### Arquitetura, Segurança e Qualidade

- [x] **ARCH-01**: O produto usa monorepo com `backend/`, `frontend/`, `infra/` e `docs/`, frontend Angular 22/TypeScript 6.0.x e backend Java 25/Spring Boot 4.1/Maven.
- [x] **ARCH-02**: O backend é um monólito modular Spring Modulith 2.1 com limites explícitos entre identidade, verificação, esportes, odds, apostas, ledger, liquidação, ranking, chat e auditoria.
- [x] **ARCH-03**: Testes automatizados impedem dependências não autorizadas entre módulos; microsserviços, Kafka e Kubernetes não integram o MVP.
- [x] **IDEN-01**: Keycloak administra autenticação, credenciais, recuperação, verificação de e-mail, sessões e MFA; a BETUFU administra perfil, papéis de domínio, vínculo UFU e elegibilidade.
- [x] **IDEN-02**: A aplicação correlaciona o par estável `(iss, sub)` do Keycloak ao usuário BETUFU e não usa e-mail como identificador imutável.
- [x] **IDEN-03**: Spring atua como BFF OIDC, mantém tokens e sessão no servidor e nunca exige que o frontend armazene access ou refresh token.
- [x] **IDEN-04**: Contrato de sessão BFF define TTL ocioso e absoluto, regeneração, logout e revogação, state, nonce, PKCE, política SameSite e CSRF inclusive no STOMP CONNECT.
- [x] **IDEN-05**: Comando administrativo sensível exige papel vigente e fica auditado; concessão ou revogação de papel invalida as sessões da identidade afetada. MFA é opcional no MVP e não condiciona nenhum comando.
- [x] **DATA-01**: PostgreSQL 18 é a fonte oficial de saldo, elegibilidade, odds, aposta, resultado, liquidação, ranking e chat.
- [x] **DATA-02**: Toda alteração de schema ocorre por migration Flyway versionada e validada.
- [x] **DATA-03**: Comando crítico valida e bloqueia mercado, versão da odd, elegibilidade, conflito e saldo autoritativos dentro da mesma transação PostgreSQL.
- [x] **DATA-04**: Nenhuma alteração de saldo ocorre sem lançamento correspondente no ledger, e registros históricos não podem ser sobrescritos ou apagados.
- [x] **DATA-05**: Constraints e chaves de negócio impedem concessão inicial, seleção por usuário/mercado, lançamento de ledger, pagamento, devolução e liquidação duplicados, inclusive sob concorrência e retries.
- [x] **DATA-06**: Redis permanece auxiliar; a plataforma possui contrato S3 desacoplado para objetos que precisem de armazenamento, e BETUFU e Keycloak usam bancos e usuários separados. Comprovantes documentais, se reintroduzidos na v2, exigem uso privado conforme `PRIV-02`.
- [x] **PRIV-01**: Matrículas, HMACs, documentos, tokens, chaves e dados privados não aparecem em URLs, conteúdo público, telemetria ou logs operacionais comuns.
- [ ] **PRIV-03**: Checklist de publicação verifica páginas, builds, imagens, documentos, metadados e histórico destinado a repositório público contra identificadores pessoais definidos e bloqueia exposição conhecida do fundador.
- [ ] **PRIV-04**: Domínio, infraestrutura, e-mails e presença pública usam identidade institucional legítima, mantendo dados verdadeiros onde contratos e lei exigirem.
- [ ] **PRIV-05**: Lançamento público depende de revisão jurídica e de fluxos testáveis para aviso, acesso, correção, oposição, exclusão ou pseudonimização e retenção por classe de dado, incluindo atleta sem conta, logs, ledger, auditoria e backups.
- [x] **API-01**: O backend publica REST JSON versionado em `/api/v1`, contrato OpenAPI e erros compatíveis com Problem Details.
- [x] **API-02**: O frontend consome cliente TypeScript gerado do OpenAPI e o pipeline detecta divergência de contrato.
- [x] **API-03**: Comando crítico usa idempotência persistente por ator, tipo e chave, vincula fingerprint do payload, repete a resposta original e rejeita reutilização concorrente com payload diferente.
- [ ] **RT-01**: WebSocket/STOMP respeita sessão, origem e autorização de domínio, e mensagens do chat são persistidas no PostgreSQL.
- [x] **AUDT-01**: Evento auditável registra ator, papel, alvo, ação, referências aos estados anterior e novo — incluindo o vínculo e a versão da chave quando aplicável, sem armazenar o HMAC da matrícula —, motivo, horário, correlation ID e chave de idempotência quando aplicável. No MVP, a trilha não registra MFA nem evidência documental, pois o MFA não governa autorização e a verificação documental não faz parte do escopo.
- [x] **AUDT-02**: Trilha de auditoria possui acesso restrito, proteção contra alteração e retenção definida sem armazenar segredos ou dados pessoais desnecessários.
- [x] **OPS-01**: Desenvolvimento oferece dois modos reproduzíveis. No modo diário padrão, Docker Compose executa PostgreSQL, Redis, Keycloak, SeaweedFS como implementação S3 exclusivamente local e serviço de e-mail, enquanto Spring Boot e Angular rodam pelo terminal ou pela IDE. No perfil completo de validação, Spring também roda em contêiner e o Angular compilado é servido por Nginx. Ambos usam wrappers, versões de runtime e imagens aprovados e fixados antes da implementação ou implantação correspondente.
- [x] **OPS-02**: Desenvolvimento, homologação e produção possuem configurações separadas, e segredos nunca são versionados.
- [x] **OPS-03**: Aplicação fornece health checks, logs estruturados, correlation ID e métricas sem dados sensíveis.
- [x] **DIAG-01**: O diagnóstico do backend publica somente os estados `UP`, `DEGRADED` e `DOWN`, em paridade com o contrato público, preservando agregação, probes, componentes, ordem, autorização, profiles e rota existentes.
- [ ] **OPS-04**: Backups de dados BETUFU, Keycloak e objetos privados são automáticos, com RPO e RTO definidos, e a restauração é testada antes do lançamento.
- [x] **TEST-01**: Regras de domínio possuem testes JUnit e transações integradas usam PostgreSQL e dependências reais via Testcontainers.
- [x] **TEST-02**: Spring Modulith Test e ArchUnit verificam limites modulares e dependências permitidas.
- [ ] **TEST-03**: Contrato OpenAPI, componentes Angular e jornadas completas críticas possuem testes automatizados, incluindo Playwright.
- [x] **TEST-04**: Testes concorrentes cobrem aposta, saldo, liquidação e devolução; perfil de carga, SLO e taxa máxima de erro são definidos e verificados antes do lançamento.
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
- **PRIV-02**: Comprovantes de conta ou de atleta, caso sejam reintroduzidos, ficam criptografados em armazenamento privado, usam acesso temporário autorizado e são excluídos após a decisão final segundo política que também cobre versões e backups.

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
| Escrita anônima ou chat sem moderação | Escrita exige sessão e matrícula ATIVO; a sala global moderada está incluída no piloto. Leitura pública de chat por partida permanece permitida. |
| Login, senha ou SSO oficial da UFU | A BETUFU possui identidade própria e não presume integração institucional. |
| Branding ou comunicação que indiquem vínculo oficial com a UFU | A plataforma é independente e deve evitar confusão institucional. |
| Anonimato absoluto do operador perante provedores ou autoridades | Não é tecnicamente nem juridicamente garantível; a proteção acordada é contra exposição pública pessoal. |
| Microsserviços, Kafka ou Kubernetes no MVP | Acrescentariam operação distribuída sem necessidade comprovada. |

## Traceability

Mapa de planejamento aprovado. Cada requisito v1 mapeia para exatamente uma fase do roadmap aprovado em 2026-08-01.

| Requirement | Phase | Status |
|-------------|-------|--------|
| ARCH-01 | Phase 1 | Complete |
| ARCH-02 | Phase 1 | Complete |
| ARCH-03 | Phase 1 | Complete |
| IDEN-01 | Phase 1 | Complete |
| IDEN-02 | Phase 1 | Complete |
| IDEN-03 | Phase 1 | Complete |
| IDEN-04 | Phase 1 | Complete |
| DATA-01 | Phase 1 | Complete |
| DATA-02 | Phase 1 | Complete |
| DATA-06 | Phase 1 | Complete |
| API-01 | Phase 1 | Complete |
| API-02 | Phase 1 | Complete |
| OPS-01 | Phase 1 | Complete |
| OPS-02 | Phase 1 | Complete |
| OPS-03 | Phase 1 | Complete |
| TEST-01 | Phase 1 | Complete |
| TEST-02 | Phase 1 | Complete |
| AUTH-02 | Phase 2 | Complete |
| AUTH-03 | Phase 2 | Complete |
| AUTH-04 | Phase 2 | Complete |
| PROF-01 | Phase 2 | Complete |
| PROF-02 | Phase 2 | Complete |
| VERI-06 | Phase 2 | Complete |
| VERI-07 | Phase 2 | Complete |
| VERI-09 | Phase 2 | Complete |
| VERI-11 | Phase 2 | Complete |
| OPER-01 | Phase 2 | Complete |
| OPER-02 | Phase 2 | Complete |
| OPER-03 | Phase 2 | Complete |
| OPER-04 | Phase 2 | Complete |
| OPER-06 | Phase 2 | Complete |
| IDEN-05 | Phase 2 | Complete |
| PRIV-01 | Phase 2 | Complete |
| AUDT-01 | Phase 2 | Complete |
| AUDT-02 | Phase 2 | Complete |
| AUTH-01 | Phase 3 | Complete |
| SPRT-01 | Phase 3 | Complete |
| SPRT-02 | Phase 3 | Complete |
| SPRT-03 | Phase 3 | Complete |
| SPRT-04 | Phase 3 | Complete |
| SPRT-05 | Phase 3 | Complete |
| SPRT-06 | Phase 3 | Complete |
| SPRT-07 | Phase 3 | Complete |
| SPRT-08 | Phase 3 | Complete |
| SPRT-09 | Phase 3 | Complete |
| SPRT-10 | Phase 3 | Complete |
| SPRT-11 | Phase 3 | Complete |
| SPRT-12 | Phase 3 | Complete |
| SPRT-13 | Phase 3 | Complete |
| SPRT-14 | Phase 3 | Complete |
| SPRT-15 | Phase 3 | Complete |
| SPRT-16 | Phase 3 | Complete |
| DIAG-01 | Phase 03.1 | Complete |
| ODDS-01 | Phase 4 | Complete |
| ODDS-02 | Phase 4 | Complete |
| ODDS-03 | Phase 4 | Complete |
| ODDS-04 | Phase 4 | Complete |
| ODDS-05 | Phase 4 | Complete |
| ODDS-06 | Phase 4 | Complete |
| ODDS-07 | Phase 4 | Complete |
| ODDS-08 | Phase 4 | Complete |
| ODDS-09 | Phase 4 | Complete |
| ODDS-10 | Phase 4 | Complete |
| ODDS-11 | Phase 4 | Complete |
| ODDS-12 | Phase 4 | Complete |
| ODDS-13 | Phase 4 | Complete |
| VERI-10 | Phase 5 | Complete |
| CRED-01 | Phase 5 | Complete |
| CRED-02 | Phase 5 | Complete |
| CRED-03 | Phase 5 | Complete |
| CRED-04 | Phase 5 | Complete |
| CRED-05 | Phase 5 | Complete |
| CRED-06 | Phase 5 | Complete |
| CRED-08 | Phase 5 | Complete |
| CRED-10 | Phase 5 | Complete |
| ODDS-14 | Phase 5 | Complete |
| PRED-01 | Phase 5 | Complete |
| PRED-02 | Phase 5 | Complete |
| PRED-03 | Phase 5 | Complete |
| PRED-04 | Phase 5 | Complete |
| PRED-05 | Phase 5 | Complete |
| PRED-06 | Phase 5 | Complete |
| PRED-07 | Phase 5 | Complete |
| PRED-08 | Phase 5 | Complete |
| INTE-01 | Phase 5 | Complete |
| INTE-02 | Phase 5 | Complete |
| INTE-03 | Phase 5 | Complete |
| INTE-04 | Phase 5 | Complete |
| DATA-03 | Phase 5 | Complete |
| DATA-04 | Phase 5 | Complete |
| DATA-05 | Phase 5 | Complete |
| API-03 | Phase 5 | Complete |
| CRED-07 | Phase 6 | Complete |
| CRED-09 | Phase 6 | Complete |
| PRED-09 | Phase 6 | Complete |
| ODDS-15 | Phase 6 | Complete |
| SETL-01 | Phase 6 | Complete |
| SETL-02 | Phase 6 | Complete |
| SETL-03 | Phase 6 | Complete |
| SETL-04 | Phase 6 | Complete |
| SETL-05 | Phase 6 | Complete |
| SETL-06 | Phase 6 | Complete |
| SETL-07 | Phase 6 | Complete |
| SETL-08 | Phase 6 | Complete |
| SETL-09 | Phase 6 | Complete |
| SETL-10 | Phase 6 | Complete |
| SETL-11 | Phase 6 | Complete |
| RANK-01 | Phase 6 | Complete |
| RANK-02 | Phase 6 | Complete |
| RANK-03 | Phase 6 | Complete |
| RANK-04 | Phase 6 | Complete |
| RANK-05 | Phase 6 | Complete |
| RANK-06 | Phase 6 | Complete |
| RANK-07 | Phase 6 | Complete |
| RANK-08 | Phase 6 | Complete |
| POOL-01 | Phase 6 | Complete |
| POOL-02 | Phase 6 | Complete |
| POOL-03 | Phase 6 | Complete |
| POOL-04 | Phase 6 | Complete |
| ACHV-01 | Phase 6 | Complete |
| ACHV-02 | Phase 6 | Complete |
| ACHV-03 | Phase 6 | Complete |
| TEST-04 | Phase 6 | Complete (Phase 6); launch gate pending |
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

### Reconciliação da Fase 6 — 2026-10-04

Os 31 requisitos atribuídos à Fase 6 foram reconciliados com a aprovação do
responsável e com o índice de evidências de
`phases/06-resultados-confirmados-rankings-e-grupos/06-VALIDATION.md` e a
certificação `06-VERIFICATION.md` (`status: passed`), formalizadas em
`47a3041`. Esta atualização altera o tracking, não as regras dos requisitos.

**Ressalva de TEST-04:** o marcador `[x]` registra a entrega aprovada da
concorrência de aposta, saldo, liquidação e devolução na Fase 6. Não certifica
perfil de carga, SLO ou taxa máxima de erro: essa parte continua obrigatória e
pendente antes do lançamento público, conforme a validação da fase. Por isso,
a tabela mantém o gate de lançamento explicitamente pendente; sua definição e
verificação devem ser discutidas na prontidão do piloto, sem remover a obrigação
nem declarar um benchmark inexistente.

**Coverage:**

- v1 requirements: 139 total
- Mapped to phases: 139
- Unmapped: 0 ✓

---
*Requisitos definidos em: 2026-08-01*
*Última atualização: 2026-10-04 após reconciliar os 31 requisitos entregues pela Fase 6; 139/139 requisitos v1 permanecem mapeados, com o gate pré-lançamento de TEST-04 explicitamente pendente*
