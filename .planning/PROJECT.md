# BETUFU

## What This Is

A BETUFU é uma aplicação web responsiva, independente e sem vínculo oficial com a Universidade Federal de Uberlândia. Ela transforma uma edição das Olimpíadas UFU em uma experiência social de palpites esportivos com créditos exclusivamente virtuais: visitantes acompanham competições e equipes, usuários conversam e formam grupos, e estudantes com vínculo UFU verificado podem apostar e disputar o ranking universitário.

O MVP será operado por administradores com escopos e conflitos de interesse controlados. Equipes, atletas, elencos, partidas e resultados são cadastrados pela operação; odds pré-jogo são calculadas automaticamente e só admitem override excepcional, com mercado suspenso, justificativa, dupla aprovação e auditoria.

## Core Value

Tornar os jogos universitários da UFU mais envolventes por meio de uma competição social de palpites, justa, transparente e sem dinheiro real.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Permitir navegação pública por competições, agenda, partidas, equipes e resultados.
- [ ] Oferecer contas BETUFU com autenticação gerenciada por Keycloak, sem login ou SSO da UFU.
- [ ] Manter perfis públicos por apelido, equipe favorita, conquistas e posição permitida, sem expor matrícula, documentos ou identidade privada.
- [ ] Permitir verificação opcional do vínculo UFU por comprovante, com revisão manual, acesso temporário e auditado e exclusão do arquivo após a decisão final.
- [ ] Representar a matrícula por HMAC normalizado e único, sem conservar o número original consultável, impedindo mais de uma conta por matrícula.
- [ ] Restringir apostas em eventos internos e participação no ranking a estudantes com verificação UFU válida.
- [ ] Usar uma edição das Olimpíadas UFU como piloto, começando pelas modalidades de confronto direto que possuam tabelas e resultados confiáveis.
- [ ] Modelar competições, edições, modalidades, categorias, equipes, atletas, elencos, fases, partidas e mercados de forma extensível.
- [ ] Fazer o administrador da competição cadastrar equipes e elencos no escopo que administra.
- [ ] Permitir que um atleta exista sem conta BETUFU e seja vinculado posteriormente a uma conta verificada quando os HMACs de matrícula coincidirem, com unicidade, auditoria e possibilidade de contestação.
- [ ] Exibir páginas de equipes com modalidade, elenco público autorizado, desempenho recente, resultados e próximas partidas.
- [ ] Calcular odds pré-jogo automaticamente com baseline Elo por modalidade e categoria, influência de recência, regressão entre edições, peso limitado de confrontos diretos e margem configurável.
- [ ] Exibir metodologia, confiança, versões e mudanças das odds, preservando em cada aposta a versão aceita.
- [ ] Admitir override de odds somente como exceção: mercado suspenso, motivo e evidência obrigatórios, proposta e aprovação por administradores distintos, MFA, comparação com o modelo e nova versão imutável.
- [ ] Conceder a todo estudante verificado a mesma quantidade inicial de créditos, com valor configurável ainda a definir e sem concessão duplicada por revalidação.
- [ ] Manter o saldo entre edições, sem reset automático no MVP; saldo insuficiente ou zerado pode impedir novas apostas por tempo indeterminado.
- [ ] Operar créditos sem compra, venda, transferência, saque, conversão, prêmio financeiro, recarga discricionária ou mecânica recorrente de recuperação no MVP.
- [ ] Registrar toda movimentação em ledger imutável e manter aposta, débito e snapshot da odd na mesma transação.
- [ ] Oferecer apostas simples antes da partida, com bilhete, créditos usados, retorno virtual potencial, histórico e liquidação idempotente.
- [ ] Limitar cada usuário a uma única seleção aceita por mercado e ocultar palpites de outros usuários até o fechamento.
- [ ] Exigir que um administrador registre qualquer versão inicial ou corretiva do resultado e outro administrador distinto a confirme antes da liquidação, reversão ou reliquidação.
- [ ] Tratar adiamento, cancelamento, anulação e correção por suspensão, devolução ou lançamentos compensatórios, sem apagar histórico.
- [ ] Manter um único ranking principal UFU, vitalício, ordenado por total de acertos e, no primeiro desempate, por taxa de acerto.
- [ ] Usar a mesma métrica do ranking UFU nas visões de grupos privados; empate remanescente compartilha a mesma posição.
- [ ] Contabilizar no máximo um acerto por mercado, independentemente do valor apostado ou da odd; palpites anulados ou devolvidos não contam como acerto nem erro.
- [ ] Disponibilizar grupos privados, chat persistido por partida, denúncia, silenciamento e moderação.
- [ ] Oferecer conquistas por sequências de acertos, previsão de azarão, desempenho em rodada e apoio a uma equipe.
- [ ] Impedir atletas de apostar em partidas da própria equipe, administradores de competição de apostar no próprio escopo e administradores gerais de apostar em qualquer evento.
- [ ] Manter trilha de auditoria para verificações, vínculos de atletas, elencos, odds, resultados, liquidações, permissões e moderação.
- [ ] Funcionar bem em navegadores de celular e desktop.

### Out of Scope

- Dinheiro real, depósitos, saques, conversão de créditos ou prêmios econômicos — mudariam materialmente o risco e exigiriam outro projeto e análise jurídica própria.
- Compra, venda ou transferência de créditos — criariam valor paralelo, fraude e desigualdade.
- Missões, atividades remuneradas em créditos e recargas — ideias não aprovadas para o MVP; o desenho específico do “palpite de recuperação” foi rejeitado.
- Reset automático de saldo por edição — o MVP preserva o saldo, sujeito a revisão futura.
- Apostas ao vivo e apostas múltiplas — exigem operação e regras incompatíveis com o piloto.
- Mercados criados por usuários — abririam espaço para manipulação e liquidação inconsistente.
- Edição manual rotineira ou override unilateral de odds — odds são automáticas e a exceção exige controle por duas pessoas.
- Notificações de partidas, liquidações, chat e ranking — adiadas para depois do MVP.
- Janela formal de contestação de resultados — adiada para depois do MVP.
- Mercados de provas com vários competidores, como atletismo e natação — adiados até validar os confrontos diretos.
- Aplicativos nativos para Android e iOS — a primeira versão será web responsiva.
- Login, SSO, integração, vínculo, endosso ou representação oficial da UFU — nada disso é presumido no MVP.

## Context

- O público principal são estudantes da UFU que acompanham e torcem por equipes e atléticas nos jogos universitários.
- O cadastro de conta BETUFU é aberto, mas somente estudantes verificados podem apostar nos eventos UFU. Não há uma conta UFU usada para entrar na plataforma.
- A matrícula é opcional para quem apenas navega ou usa recursos sociais, nunca aparece publicamente e não permanece armazenada de forma consultável após a verificação.
- O piloto concentra calendário, comunidade e operação em uma edição específica das Olimpíadas UFU; as modalidades exatas dependem das tabelas confiáveis disponíveis.
- Atleta e usuário BETUFU são entidades distintas. O administrador comprova o vínculo esportivo do atleta mesmo que ele não tenha conta; se a pessoa criar uma conta depois, a coincidência de HMAC permite o vínculo auditável.
- Equipes e elencos ficam sob responsabilidade do administrador da competição. O elenco deve ser travado antes da abertura do mercado; mudanças posteriores são auditadas e podem exigir revisão das apostas afetadas.
- O volume apostado pelos usuários não altera as odds no MVP, evitando manipulação em uma comunidade pequena.
- A margem inicial das odds é configurável e começa em 5%; alterações futuras não mudam odds já aceitas.
- Todo estudante verificado recebe a mesma concessão inicial, mas o valor, os limites por aposta e a política numérica ainda serão definidos antes da implementação correspondente.
- O ranking principal já é o ranking UFU porque somente apostas feitas com verificação válida geram estatísticas. Ele é vitalício no MVP; grupos mostram uma disputa filtrada entre seus integrantes usando a mesma métrica.
- Usuário sem saldo pode permanecer impedido de apostar. Essa limitação é conscientemente aceita para o MVP e pode ser revista depois.
- A identidade pessoal do fundador não será publicada. A operação usará uma identidade institucional legítima, sem prometer anonimato perante provedores, contratos ou autoridades.
- Antes do lançamento público serão necessárias revisão jurídica, política de privacidade, base legal, retenção, direitos dos titulares e comunicação clara de independência institucional.

## Architecture

### Frontend

- Angular 22 e TypeScript 6.0.x.
- Componentes standalone, Angular Material/CDK com identidade visual própria, Signals para estado local e derivado e RxJS para fluxos assíncronos e tempo real.
- Reactive Forms tipados, Angular Router com carregamento por rota e cliente TypeScript gerado pelo OpenAPI.
- Sem NgRx, Nx ou microfrontends inicialmente.

### Backend

- Java 25 LTS, Spring Boot 4.1, Maven e monólito modular com Spring Modulith 2.1.
- Módulos previstos: identidade/perfil, verificação, esportes/competições, odds, apostas, ledger, liquidação, rankings/conquistas, chat/moderação e auditoria.
- Limites modulares protegidos por Spring Modulith Test e ArchUnit.
- Sem microsserviços, Kafka ou Kubernetes no MVP.

### Identidade e segurança

- Keycloak administra login, credenciais, recuperação de acesso, verificação de e-mail, sessões e MFA; a BETUFU administra perfil, verificação UFU, papéis de domínio, elegibilidade e conflitos de interesse.
- Spring Security aplica autorização. O identificador estável é o par `(iss, sub)` emitido pelo Keycloak, nunca o e-mail.
- A aplicação web usa BFF: Spring realiza OIDC e mantém tokens e sessão no servidor; o navegador recebe apenas cookie `HttpOnly`, `Secure` e `SameSite` compatível.
- Operações mutáveis e o canal em tempo real são protegidos contra CSRF, origem indevida e acesso sem autorização.
- MFA é obrigatório para contas administrativas.

### Dados e integrações

- PostgreSQL 18 é a fonte oficial, com Flyway, Spring Data JPA para operações comuns e SQL/locks explícitos nas transações críticas.
- Ledger, versões de odds, odds aceitas e auditoria são imutáveis; correções usam novos registros e lançamentos compensatórios.
- Redis é auxiliar para sessões, rate limiting e suporte ao tempo real, nunca fonte de saldo, elegibilidade, resultado ou liquidação.
- Comprovantes temporários ficam em armazenamento S3 compatível, privado e criptografado.
- BETUFU e Keycloak usam bancos e usuários separados, ainda que compartilhem inicialmente o mesmo servidor PostgreSQL.

### API, tempo real e operação

- REST JSON versionado em `/api/v1`, contrato OpenAPI e erros por Problem Details.
- Idempotency key em apostas, liquidações, devoluções, correções e outros comandos críticos.
- WebSocket/STOMP para chat e atualizações; mensagens persistidas no PostgreSQL.
- Monorepo com `backend/`, `frontend/`, `infra/` e `docs/`; Docker Compose no desenvolvimento.
- PostgreSQL, Redis, Keycloak, MinIO e e-mail de desenvolvimento compõem o ambiente local.
- O desenvolvimento local possui dois modos suportados. No modo diário padrão, as dependências de infraestrutura rodam em contêineres e Spring Boot e Angular rodam pelo terminal ou pela IDE. No perfil completo de validação, Spring também roda em contêiner e o frontend Angular compilado é servido por um contêiner Nginx.
- Os dois modos usam os mesmos contratos e configurações-base, com wrappers, versões de runtime e imagens de contêiner fixados.
- Ambientes de desenvolvimento, homologação e produção; PostgreSQL e S3 gerenciados são preferíveis em produção se o orçamento permitir.
- Logs estruturados, correlation ID, Spring Actuator, métricas, backups com restauração testada e segredos fora do repositório.

### Qualidade

- JUnit, Testcontainers, Spring Modulith Test, ArchUnit, testes de contrato OpenAPI, testes de componentes Angular e Playwright.
- Testes de concorrência cobrem aposta, saldo, devolução e liquidação; testes de carga antecedem o lançamento.
- O pipeline bloqueia entrega quando falham testes, migrations, contrato, limites modulares ou verificações de segurança.

## Constraints

- **Economia virtual**: nenhum crédito possui valor monetário ou pode ser comprado, vendido, transferido, convertido, sacado ou trocado por prêmio.
- **Independência institucional**: a BETUFU não representa a UFU; textos, marcas e identidade não podem sugerir vínculo oficial.
- **Privacidade**: matrícula e comprovante são dados identificadores; coleta mínima, impressão HMAC versionada e protegida por segredo, armazenamento temporário privado e exclusão após decisão são obrigatórios.
- **Integridade**: uma conta por matrícula, uma seleção por mercado, bloqueio de conflitos, dupla confirmação de resultado e ledger imutável.
- **Stack aprovada**: Angular/TypeScript no frontend e Java/Spring no backend; qualquer troca relevante volta para decisão explícita.
- **Plataforma**: web responsiva primeiro, sem aplicativos nativos no MVP.
- **Fonte de dados**: calendários, elencos e resultados dependem de fontes confiáveis e operação administrativa; integração oficial não é presumida.
- **Processo**: nenhuma decisão arquitetural, documento de planejamento ou commit deve ser tratado como aprovado sem revisão explícita do usuário.

## Open Decisions

- Quantidade inicial exata de créditos.
- Limites mínimo e máximo por aposta, casas decimais, fórmula de pagamento, inclusão ou não da stake no retorno e política de arredondamento.
- Prazo e regra exatos de validade da verificação UFU.
- Visibilidade e posição no ranking enquanto a verificação estiver expirada.
- Tratamento de matrícula reemitida ou de uma mesma pessoa com mais de uma matrícula legítima.
- Política para correção de resultado quando créditos pagos incorretamente já tiverem sido gastos, sem apagar o ledger nem permitir saldo negativo.
- Controle de aprovação para ajuste compensatório direto no ledger; dupla aprovação é a recomendação de segurança, ainda não uma decisão.
- Destino de aposta aceita quando vínculo de atleta, alteração de elenco ou papel administrativo posterior revelar conflito de interesse.
- Parâmetros numéricos do Elo, tratamento probabilístico de empate e fixtures de referência por modalidade.
- Protocolo de normalização, versionamento, rotação e resposta a comprometimento da chave HMAC.
- Indicadores e resposta operacional caso a margem e a ausência de reposição reduzam excessivamente a oferta total de créditos.
- Modalidades, categorias e tabelas exatas da edição piloto.
- Provedor de nuvem e plataforma de CI/CD.

Essas decisões devem permanecer configuráveis ou ser fechadas antes da fase que as implementar. Elas não autorizam novas mecânicas de obtenção de créditos.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Usar exclusivamente créditos virtuais | Validar diversão e integridade sem movimentação financeira | ✓ Aprovado para o MVP |
| Manter cadastro aberto e restringir apostas UFU a estudantes verificados | Permitir descoberta pública e reduzir participação indevida | ✓ Aprovado |
| Autenticar contas BETUFU com Keycloak, sem conta ou SSO UFU | Separar identidade da plataforma de uma integração institucional inexistente | ✓ Aprovado |
| Verificar vínculo por comprovante privado, exclusão posterior e HMAC da matrícula | Minimizar dados e impedir duplicidade sem conservar o número consultável | ✓ Aprovado |
| Usar uma edição das Olimpíadas UFU como piloto | Concentrar calendário, equipes e comunidade em um evento observável | ✓ Aprovado |
| Começar por confrontos diretos | Reduzir a complexidade inicial de mercados e liquidação | ✓ Aprovado |
| Usar Angular 22 e TypeScript 6 no frontend | Construir uma aplicação web tipada e estruturada | ✓ Aprovado |
| Usar Java 25, Spring Boot 4.1 e Spring Modulith 2.1 | Começar com backend robusto e modular sem distribuir prematuramente | ✓ Aprovado |
| Usar BFF com Keycloak e sessão de servidor | Evitar tokens no navegador e centralizar controles de segurança | ✓ Aprovado |
| Usar PostgreSQL 18 como fonte oficial e ledger imutável | Preservar consistência, auditoria e correções reproduzíveis | ✓ Aprovado |
| Calcular odds automaticamente com baseline Elo | Evitar edição arbitrária e produzir um baseline explicável | ✓ Aprovado |
| Começar com margem de odds configurável em 5% | Aplicar um parâmetro inicial explícito sem impedir ajuste futuro | ✓ Aprovado para o MVP |
| Permitir override excepcional de odds com dupla aprovação | Corrigir distorções graves sem transformar intervenção manual em rotina | ✓ Aprovado |
| Preservar a odd aceita e publicar histórico de versões | Garantir transparência e tratamento uniforme | ✓ Aprovado |
| Exigir duas pessoas distintas para confirmar resultados | Reduzir erro e manipulação antes da liquidação | ✓ Aprovado |
| Cadastrar equipes e elencos pelo administrador da competição | Manter responsabilidade operacional e escopo auditável | ✓ Aprovado |
| Permitir atleta sem conta e vínculo posterior por HMAC | Não obrigar cadastro e ainda aplicar conflitos quando houver associação | ✓ Aprovado |
| Conceder o mesmo saldo inicial a todo estudante verificado | Garantir ponto de partida uniforme | ✓ Aprovado; valor pendente |
| Persistir saldo sem reset automático | Manter continuidade entre edições no MVP | ✓ Aprovado; revisar futuramente |
| Aceitar bloqueio por saldo zerado, sem recuperação no MVP | Evitar criar uma economia adicional antes de validá-la | ✓ Aprovado; revisar futuramente |
| Usar ranking UFU vitalício por total de acertos | Valorizar conhecimento esportivo sem multiplicar acertos por stake ou odd | ✓ Aprovado |
| Usar taxa de acerto como primeiro desempate e posição compartilhada depois | Diferenciar consistência sem inventar critérios adicionais | ✓ Aprovado |
| Incluir páginas de equipes, grupos, chat, moderação e conquistas no MVP | Fazer da BETUFU uma experiência social universitária | ✓ Aprovado |
| Adiar notificações e contestação formal de resultados | Manter o MVP concentrado no ciclo principal | ✓ Aprovado |
| Não expor publicamente a identidade pessoal do fundador | Separar presença pública pessoal da operação institucional legítima | ✓ Aprovado, sem promessa de irrastrabilidade |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `$gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `$gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-08-01 after consolidation of approved product, economy and architecture decisions*
