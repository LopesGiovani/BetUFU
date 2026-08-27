# BETUFU

## What This Is

A BETUFU é uma aplicação web responsiva, independente e sem vínculo oficial com a Universidade Federal de Uberlândia. Ela transforma uma edição das Olimpíadas UFU em uma experiência social de palpites esportivos com créditos exclusivamente virtuais: visitantes acompanham competições e equipes, usuários conversam e formam grupos, e estudantes que registram sua matrícula UFU podem apostar e disputar o ranking universitário.

O MVP será operado por administradores com escopos e conflitos de interesse controlados. Equipes, atletas, elencos, partidas e resultados são cadastrados pela operação; odds pré-jogo são calculadas automaticamente e só admitem override excepcional, com mercado suspenso, justificativa, dupla aprovação e auditoria.

## Core Value

Tornar os jogos universitários da UFU mais envolventes por meio de uma competição social de palpites, justa, transparente e sem dinheiro real.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Permitir navegação pública por competições, agenda, partidas, equipes e resultados.
- [ ] Oferecer contas BETUFU com autenticação gerenciada por Keycloak, sem login ou SSO da UFU.
- [ ] Manter perfis públicos por apelido, organização esportiva favorita, conquistas e posição permitida; atleta vinculado pode publicar foto, curso, biografia e redes sociais opcionais sem expor matrícula, documentos ou identidade privada.
- [ ] Permitir o registro voluntário e opcional da matrícula UFU, sem exigir comprovante no MVP; a verificação documental com revisão manual foi adiada para a v2.
- [ ] Representar a matrícula por HMAC normalizado e único, sem conservar o número original consultável, impedindo mais de uma conta por matrícula.
- [ ] Restringir apostas em eventos internos e participação no ranking a contas com matrícula UFU registrada e única.
- [ ] Usar a Olimpíada Universitária UFU 2026 como primeira edição real de uma plataforma genérica para várias competições ao longo do ano, começando pela operação detalhada de confrontos diretos com dados confiáveis.
- [ ] Modelar esportes, competições, edições, categorias, fases, organizações esportivas, equipes por edição, atletas, elencos, locais, eventos e mercados de forma extensível.
- [ ] Fazer um ou mais administradores da competição cadastrarem e publicarem dados esportivos somente no escopo atribuído, com fonte, histórico e auditoria.
- [ ] Permitir que um atleta exista sem conta BETUFU e seja vinculado posteriormente a uma conta verificada quando os HMACs de matrícula coincidirem, com unicidade, auditoria e possibilidade de contestação.
- [ ] Exibir páginas de organização, equipe e atleta com dados esportivos públicos autorizados, desempenho genérico confiável, resultados e próximos eventos.
- [ ] Calcular odds pré-jogo automaticamente com baseline Elo por modalidade e categoria, influência de recência, regressão entre edições, peso limitado de confrontos diretos e margem configurável.
- [ ] Exibir metodologia, confiança, versões e mudanças das odds, preservando em cada aposta a versão aceita.
- [ ] Admitir override de odds somente como exceção: mercado suspenso, motivo e evidência obrigatórios, proposta e aprovação por administradores distintos, comparação com o modelo e nova versão imutável.
- [ ] Conceder a todo estudante verificado a mesma quantidade inicial de créditos, com valor configurável ainda a definir e sem concessão duplicada por revalidação.
- [ ] Manter o saldo entre edições, sem reset automático no MVP; saldo insuficiente ou zerado pode impedir novas apostas por tempo indeterminado.
- [ ] Operar créditos sem compra, venda, transferência, saque, conversão, prêmio financeiro, recarga discricionária ou mecânica recorrente de recuperação no MVP.
- [ ] Registrar toda movimentação em ledger imutável e manter aposta, débito e snapshot da odd na mesma transação.
- [ ] Oferecer apostas simples antes da partida, com bilhete, créditos usados, retorno virtual potencial, histórico e liquidação idempotente.
- [ ] Limitar cada usuário a uma única seleção aceita por mercado e ocultar palpites de outros usuários até o fechamento.
- [ ] Exigir fonte oficial, versões e auditoria para resultado inicial ou corretivo; no MVP, a mesma pessoa autorizada pode registrar e confirmar antes da liquidação, reversão ou reliquidação, preservando atores separados no modelo para futura política de duas pessoas.
- [ ] Tratar adiamento, cancelamento, anulação e correção por suspensão, devolução ou lançamentos compensatórios, sem apagar histórico.
- [ ] Manter um único ranking principal UFU, vitalício, ordenado por total de acertos e, no primeiro desempate, por taxa de acerto.
- [ ] Usar a mesma métrica do ranking UFU nas visões de grupos privados; empate remanescente compartilha a mesma posição.
- [ ] Contabilizar no máximo um acerto por mercado, independentemente do valor apostado ou da odd; palpites anulados ou devolvidos não contam como acerto nem erro.
- [ ] Disponibilizar grupos privados, chat persistido por partida, denúncia, silenciamento e moderação.
- [ ] Oferecer conquistas por sequências de acertos, previsão de azarão, desempenho em rodada e apoio à organização esportiva favorita.
- [ ] No MVP, permitir que atletas apostem inclusive em eventos próprios; administradores de competição continuam proibidos de apostar no próprio escopo e administradores gerais em qualquer evento. A política de atleta deve ser reavaliada antes de prêmio real, escala relevante ou parceria institucional.
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
- Verificação documental do vínculo UFU — envio de comprovante, revisão manual, acesso temporário auditado e exclusão do arquivo foram adiados para a v2 em 2026-08-19; no MVP a matrícula é autodeclarada e apenas a unicidade é verificada.
- Notificações de partidas, liquidações, chat e ranking — adiadas para depois do MVP.
- Janela formal de contestação de resultados — adiada para depois do MVP.
- Operação detalhada e mercados de provas com vários competidores, como atletismo e natação — o catálogo e a agenda básica podem exibi-los, mas regras de baterias, marcas, pódio e liquidação ficam adiadas.
- Aplicativos nativos para Android e iOS — a primeira versão será web responsiva.
- Login, SSO, integração, vínculo, endosso ou representação oficial da UFU — nada disso é presumido no MVP.

## Context

- O público principal são estudantes da UFU que acompanham e torcem por equipes e atléticas nos jogos universitários.
- O cadastro de conta BETUFU é aberto, mas somente contas com matrícula UFU registrada podem apostar nos eventos UFU. No MVP esse registro é autodeclarado: a plataforma verifica a unicidade da matrícula, não a sua titularidade. Não há uma conta UFU usada para entrar na plataforma.
- A matrícula é opcional para quem apenas navega ou usa recursos sociais, nunca aparece publicamente e não permanece armazenada de forma consultável após o registro.
- A primeira operação real será a Olimpíada Universitária UFU 2026, mas o domínio e a navegação são genéricos para várias competições e edições ao longo do ano; documentos de 2025 servem apenas como referência.
- Atleta e usuário BETUFU são entidades distintas. O administrador cadastra o atleta mesmo sem conta e informa a matrícula somente para cálculo transitório do HMAC; isso não comprova vínculo com a UFU nem titularidade. Se a pessoa criar uma conta depois, a coincidência de HMAC gera uma proposta auditável que ela aceita ou rejeita.
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

- Keycloak administra login, credenciais, recuperação de acesso, verificação de e-mail, sessões e MFA; nesta fase, o único papel BETUFU provisionado no realm é `TECHNICAL`. A BETUFU/PostgreSQL administra perfil, verificação UFU, papéis administrativos e de domínio, elegibilidade e conflitos de interesse.
- Spring Security aplica autorização. O identificador estável é o par `(iss, sub)` emitido pelo Keycloak, nunca o e-mail.
- A aplicação web usa BFF: Spring realiza OIDC e mantém tokens e sessão no servidor; o navegador recebe apenas cookie `HttpOnly`, `Secure` e `SameSite` compatível.
- Operações mutáveis e o canal em tempo real são protegidos contra CSRF, origem indevida e acesso sem autorização.
- MFA é opcional no MVP: o Keycloak o oferece a qualquer conta, inclusive administrativa, mas nenhum comando da plataforma é condicionado a ele. A obrigatoriedade fica para reavaliação futura.

### Dados e integrações

- PostgreSQL 18 é a fonte oficial, com Flyway, Spring Data JPA para operações comuns e SQL/locks explícitos nas transações críticas.
- Ledger, versões de odds, odds aceitas e auditoria são imutáveis; correções usam novos registros e lançamentos compensatórios.
- Redis é auxiliar para sessões, rate limiting e suporte ao tempo real, nunca fonte de saldo, elegibilidade, resultado ou liquidação.
- Fotos opcionais de atletas usam armazenamento de objetos com processamento seguro e política pública de mídia. O MVP não coleta comprovante de conta nem de atleta; caso a verificação documental retorne na v2, seus arquivos exigirão armazenamento privado e criptografado. O backend mantém configuração genérica `BETUFU_S3_*`, incluindo `BETUFU_S3_ENDPOINT`, sem acoplamento ao serviço local.
- BETUFU e Keycloak usam bancos e usuários separados, ainda que compartilhem inicialmente o mesmo servidor PostgreSQL.

### API, tempo real e operação

- REST JSON versionado em `/api/v1`, contrato OpenAPI e erros por Problem Details.
- Idempotency key em apostas, liquidações, devoluções, correções e outros comandos críticos.
- WebSocket/STOMP para chat e atualizações; mensagens persistidas no PostgreSQL.
- Monorepo com `backend/`, `frontend/`, `infra/` e `docs/`; Docker Compose no desenvolvimento.
- PostgreSQL, Redis, Keycloak, SeaweedFS e e-mail de desenvolvimento compõem o ambiente local. SeaweedFS é somente a implementação S3 gratuita do MVP local, publica apenas a porta S3 8333 e usa volume Docker persistente.
- O desenvolvimento local possui dois modos suportados. No modo diário padrão, as dependências de infraestrutura rodam em contêineres e Spring Boot e Angular rodam pelo terminal ou pela IDE. No perfil completo de validação, Spring também roda em contêiner e o frontend Angular compilado é servido por um contêiner Nginx.
- Os dois modos usam os mesmos contratos e configurações-base, com wrappers, versões de runtime e imagens de contêiner fixados.
- Ambientes de desenvolvimento, homologação e produção; PostgreSQL gerenciado é preferível em produção se o orçamento permitir, enquanto a solução S3 de produção permanece deliberadamente não escolhida.
- Logs estruturados, correlation ID, Spring Actuator, métricas, backups com restauração testada e segredos fora do repositório.

### Qualidade

- JUnit, Testcontainers, Spring Modulith Test, ArchUnit, testes de contrato OpenAPI, testes de componentes Angular e Playwright.
- Testes de concorrência cobrem aposta, saldo, devolução e liquidação; testes de carga antecedem o lançamento.
- O pipeline bloqueia entrega quando falham testes, migrations, contrato, limites modulares ou verificações de segurança.

## Constraints

- **Economia virtual**: nenhum crédito possui valor monetário ou pode ser comprado, vendido, transferido, convertido, sacado ou trocado por prêmio.
- **Independência institucional**: a BETUFU não representa a UFU; textos, marcas e identidade não podem sugerir vínculo oficial.
- **Privacidade**: matrícula é dado identificador; coleta transitória mínima e impressão HMAC versionada protegida por segredo são obrigatórias. O MVP não recebe comprovante documental de conta nem de atleta e nunca apresenta a coincidência de HMAC como verificação oficial da UFU.
- **Integridade**: uma conta por matrícula, uma seleção por mercado, restrições administrativas, fonte/versionamento de resultado e ledger imutável. No MVP, resultado não exige confirmador distinto; a separação de atores é preservada para futura política de duas pessoas.
- **Stack aprovada**: Angular/TypeScript no frontend e Java/Spring no backend; qualquer troca relevante volta para decisão explícita.
- **Plataforma**: web responsiva primeiro, sem aplicativos nativos no MVP.
- **Fonte de dados**: calendários, elencos e resultados dependem de fontes confiáveis e operação administrativa; integração oficial não é presumida.
- **Processo colaborativo obrigatório**: o responsável participa ativamente do desenvolvimento. Antes de cada plano, onda ou bloco material, o GSD deve explicar objetivo, abordagem, arquivos e comandos previstos, impactos, riscos e resultado esperado. Decisões relevantes, instalações, scaffolds, alterações de código, banco ou infraestrutura e ações externas exigem aprovação explícita e específica. Stage, commit e push exigem aprovação separada. Cada aprovação vale somente para o escopo apresentado e não autoriza automaticamente o próximo passo. A execução deve usar o modo interativo e pausar para revisão entre blocos.

## Open Decisions

- Quantidade inicial exata de créditos.
- Limites mínimo e máximo por aposta, casas decimais, fórmula de pagamento, inclusão ou não da stake no retorno e política de arredondamento.
- Tratamento de matrícula reemitida ou de uma mesma pessoa com mais de uma matrícula legítima.
- Política para correção de resultado quando créditos pagos incorretamente já tiverem sido gastos, sem apagar o ledger nem permitir saldo negativo.
- Controle de aprovação para ajuste compensatório direto no ledger; dupla aprovação é a recomendação de segurança, ainda não uma decisão.
- Política futura para conflito esportivo de atleta — o MVP permite aposta em evento próprio, mas preserva a relação atleta–equipe–evento para reavaliação antes de prêmio real, escala relevante ou parceria institucional.
- Parâmetros numéricos do Elo, tratamento probabilístico de empate e fixtures de referência por modalidade.
- Protocolo de normalização, versionamento, rotação e resposta a comprometimento da chave HMAC — passou a ser o único mecanismo de integridade da matrícula no MVP.
- Indicadores e resposta operacional caso a margem e a ausência de reposição reduzam excessivamente a oferta total de créditos.
- Regras detalhadas, estatísticas e critérios automáticos de classificação/desempate para modalidades além do confronto direto do MVP.
- Momento de exigir pessoas distintas para confirmar resultado e autorizar liquidação, reversão ou reliquidação.
- Provedor de nuvem e plataforma de CI/CD.
- Solução S3 compatível para homologação e produção; SeaweedFS foi aprovado somente para o ambiente local do MVP.

Essas decisões devem permanecer configuráveis ou ser fechadas antes da fase que as implementar. Elas não autorizam novas mecânicas de obtenção de créditos.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Usar exclusivamente créditos virtuais | Validar diversão e integridade sem movimentação financeira | ✓ Aprovado para o MVP |
| Manter cadastro aberto e restringir apostas UFU a contas com matrícula registrada | Permitir descoberta pública e reduzir participação indevida | ✓ Aprovado |
| Autenticar contas BETUFU com Keycloak, sem conta ou SSO UFU | Separar identidade da plataforma de uma integração institucional inexistente | ✓ Aprovado |
| Verificar vínculo por comprovante privado, exclusão posterior e HMAC da matrícula | Minimizar dados e impedir duplicidade sem conservar o número consultável | ⚠ Superada: o comprovante de conta foi adiado em 2026-08-19 e o de atleta em 2026-08-26; o HMAC permanece sem representar prova oficial |
| Registrar a matrícula UFU por autodeclaração no MVP, sem comprovante | Reduzir o escopo do MVP mantendo a unicidade por HMAC, que impede duas contas com a mesma matrícula | ✓ Aprovado em 2026-08-19; o risco de alguém registrar matrícula alheia foi apresentado e aceito explicitamente, inclusive a ausência de meio de disputa, porque o número original não é conservado |
| Não aplicar prazo de validade à matrícula registrada no MVP | Sem verificação documental não existe evento que justifique revalidação periódica | ✓ Aprovado em 2026-08-19 |
| Tornar o MFA opcional em todo o MVP, inclusive para contas administrativas | Reduzir o escopo do MVP; a dupla aprovação do override de odds permanece como controle de integridade | ✓ Aprovado em 2026-08-20; substitui a regra anterior de MFA obrigatório e alcança IDEN-05 e ODDS-12. A antiga dupla confirmação de resultado foi substituída em 2026-08-26 |
| Usar a Olimpíada Universitária UFU 2026 como primeira edição de um catálogo genérico | Validar a operação em evento observável sem fixar o produto a uma única competição | ✓ Aprovado em 2026-08-26; documentos de 2025 são somente referência |
| Começar por confrontos diretos | Reduzir a complexidade inicial de mercados e liquidação | ✓ Aprovado |
| Usar Angular 22 e TypeScript 6 no frontend | Construir uma aplicação web tipada e estruturada | ✓ Aprovado |
| Usar Java 25, Spring Boot 4.1 e Spring Modulith 2.1 | Começar com backend robusto e modular sem distribuir prematuramente | ✓ Aprovado |
| Usar BFF com Keycloak e sessão de servidor | Evitar tokens no navegador e centralizar controles de segurança | ✓ Aprovado |
| Manter somente `TECHNICAL` como papel BETUFU no Keycloak nesta fase | Separar autenticação e acesso técnico dos papéis administrativos e de domínio persistidos pela aplicação | ✓ Aprovado; papéis de domínio ficam na BETUFU/PostgreSQL |
| Usar PostgreSQL 18 como fonte oficial e ledger imutável | Preservar consistência, auditoria e correções reproduzíveis | ✓ Aprovado |
| Usar SeaweedFS como serviço S3 gratuito somente no ambiente local do MVP | Oferecer armazenamento local reproduzível sem escolher o serviço de produção | ✓ Aprovado para local; produção pendente |
| Integrar objetos por contrato S3 e configuração `BETUFU_S3_*` | Manter o backend desacoplado da implementação local e permitir decisão posterior de produção | ✓ Aprovado |
| Calcular odds automaticamente com baseline Elo | Evitar edição arbitrária e produzir um baseline explicável | ✓ Aprovado |
| Começar com margem de odds configurável em 5% | Aplicar um parâmetro inicial explícito sem impedir ajuste futuro | ✓ Aprovado para o MVP |
| Permitir override excepcional de odds com dupla aprovação | Corrigir distorções graves sem transformar intervenção manual em rotina | ✓ Aprovado |
| Preservar a odd aceita e publicar histórico de versões | Garantir transparência e tratamento uniforme | ✓ Aprovado |
| Permitir que a mesma pessoa registre e confirme resultado no MVP | Reduzir operação inicial preservando fonte, versões e atores separados | ✓ Aprovado em 2026-08-26; duas pessoas tornam-se reavaliação obrigatória antes de prêmio real, escala relevante ou parceria institucional |
| Cadastrar equipes e elencos pelo administrador da competição | Manter responsabilidade operacional e escopo auditável | ✓ Aprovado |
| Permitir atleta sem conta e vínculo posterior por HMAC | Não obrigar cadastro e permitir proposta auditável sem apresentar a coincidência como prova oficial | ✓ Aprovado; vínculo de atleta não bloqueia aposta no MVP e a política será reavaliada futuramente |
| Conceder o mesmo saldo inicial a todo estudante verificado | Garantir ponto de partida uniforme | ✓ Aprovado; valor pendente |
| Persistir saldo sem reset automático | Manter continuidade entre edições no MVP | ✓ Aprovado; revisar futuramente |
| Aceitar bloqueio por saldo zerado, sem recuperação no MVP | Evitar criar uma economia adicional antes de validá-la | ✓ Aprovado; revisar futuramente |
| Usar ranking UFU vitalício por total de acertos | Valorizar conhecimento esportivo sem multiplicar acertos por stake ou odd | ✓ Aprovado |
| Usar taxa de acerto como primeiro desempate e posição compartilhada depois | Diferenciar consistência sem inventar critérios adicionais | ✓ Aprovado |
| Incluir páginas de equipes, grupos, chat, moderação e conquistas no MVP | Fazer da BETUFU uma experiência social universitária | ✓ Aprovado |
| Adiar notificações e contestação formal de resultados | Manter o MVP concentrado no ciclo principal | ✓ Aprovado |
| Não expor publicamente a identidade pessoal do fundador | Separar presença pública pessoal da operação institucional legítima | ✓ Aprovado, sem promessa de irrastrabilidade |
| Tornar obrigatória a participação ativa do responsável em todo o desenvolvimento | Impedir decisões, implementação e operações Git silenciosas e manter cada aprovação limitada ao escopo explicado | ✓ Aprovado; executar por onda em modo interativo |

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
*Last updated: 2026-08-26 after reconciling Phase 3 sports, athlete and result policies*
