# Auditoria das Jornadas Reais do Usuário — Fases 1 a 5

**Data:** 2026-09-15  
**Escopo:** Fases 1, 2, 3, 03.1, 4 e 5  
**Modo:** auditoria/UAT somente leitura, sem correções  
**Resultado:** jornadas essenciais não convergem; recomenda-se uma fase corretiva antes da Fase 6

## 1. Limites e governança

Esta auditoria seguiu `AGENTS.md`, as guardrails da BETUFU e a autorização específica do responsável. O bloco autorizado incluiu navegação real no navegador, login/logout, sessões isoladas, refresh, voltar/avançar, viewport móvel, teclado, inspeção de console/rede e a criação deste relatório.

Não foram autorizados nem executados:

- novo palpite ou tentativa de duplicação;
- alteração de perfil, matrícula, papéis, odds ou dados esportivos;
- seed, rebuild, reset, instalação ou atualização de dependência;
- alteração de código, ROADMAP ou planos;
- stage, commit, push ou qualquer ação externa.

O teste de envio foi interrompido antes do clique. Preencher seleção e stake alterou apenas o estado local do formulário Angular.

## 2. Preflight confirmado ao vivo

| Item | Estado observado |
|---|---|
| Branch | `master` |
| HEAD | `c4cd2f2a105331fadcba443dff57d5322e12bf45` |
| Relação com remoto | `master` igual a `origin/master` (`0/0`), diferente do snapshot anterior que indicava dois commits locais |
| Alterações do responsável | `M docs/development.md`, `M docs/local-seed.md`, `?? docs/manual-testing.md` |
| Serviços BETUFU | sete containers `healthy`: frontend, backend, Keycloak, PostgreSQL, Redis, Mailpit e SeaweedFS |
| Flyway | V32, conforme ambiente vigente e documentação da Fase 5 |
| Massa esportiva | 671 eventos, 4 mercados, 5 publicações |
| Créditos | duas concessões iniciais; aluno com `950.00`, atleta com `1000.00` |
| Palpites | exatamente um registro `PENDING`, seleção `AWAY`, stake `50.00`, odd `2.12`, retorno `106.00`, lucro `56.00` |
| Integridade de duplicação | constraint `uq_prediction_identity_market (identity_id, market_id)` presente |
| Navegadores temporários | sessões `visitor`, `athlete`, `admin`, `maker`, `checker` e `moderator` encerradas ao fim do UAT |

A consulta final foi executada dentro de `BEGIN READ ONLY`. Ela confirmou um único palpite e um único débito, portanto o UAT não alterou saldo nem histórico de palpites.

## 3. Parecer executivo

As funcionalidades implementadas até a Fase 5 existem e, em grande parte, funcionam quando abertas por URL conhecida. Entretanto, o produto ainda não oferece uma jornada integrada que permita a um usuário comum partir da Arena, entender sua sessão, encontrar a conta, descobrir um mercado e chegar ao palpite sem conhecer UUIDs.

O problema central não é a ausência das páginas; é a ausência de conexões confiáveis entre elas:

1. a Arena não reage à sessão autenticada;
2. o login não preserva a URL de origem;
3. o evento público não expõe nem recebe referência ao mercado;
4. a conta e o console operacional não possuem entrada global descobrível;
5. perfil e matrícula consultam fontes diferentes e exibem estados contraditórios;
6. o frontend habilita o formulário depois do deadline quando o estado persistido ainda é `OPEN`;
7. rotas protegidas anônimas e rotas inexistentes podem resultar em página vazia;
8. a cobertura E2E usa majoritariamente APIs mockadas e deep links, ocultando os gaps acima.

**Conclusão:** a Fase 5 está tecnicamente certificada nos contratos isolados, mas a jornada real “Arena → evento → mercado → login → retorno → palpite” não está utilizável. O gap é transversal às Fases 1–5 e deve ser tratado antes de adicionar liquidação, ranking e grupos na Fase 6.

## 4. Por que testes isolados não garantiram a jornada

Um teste de componente responde perguntas locais, como “o bilhete calcula lucro?” ou “a tela de matrícula renderiza `ATIVO`?”. Um teste de rota responde “o Angular aceita `/odds/mercados/:marketId`?”. Nenhum deles, isoladamente, prova que o usuário consegue descobrir o `marketId`, que o shell público percebe a sessão, que o login retorna à mesma URL ou que duas telas usam a mesma fonte de verdade.

Na BETUFU, esse desencaixe é visível na automação existente:

- `sports-pilot.spec.ts` inicia algumas páginas públicas por deep link e intercepta as APIs com `page.route`;
- `odds-pilot.spec.ts` abre diretamente `/odds/mercados/${MARKET_ID}` com mercado mockado;
- `prediction-pilot.spec.ts` também começa pelo UUID do mercado e mocka sessão, matrícula, saldo e palpite;
- o `playwright.config.ts` padrão seleciona somente `walking-skeleton.spec.ts`;
- a página do evento recebe `eventId`, mas o contrato `PublicEventDetail` não contém `marketId`;
- o shell da Arena é estático e sempre renderiza “Entrar”, enquanto o bilhete carrega `SessionStore` separadamente.

Assim, cada ilha passa em seu próprio teste, mas a travessia entre as ilhas não é exercitada. A unidade correta de validação para esse risco é uma jornada E2E com backend, Keycloak, banco e frontend reais, começando por uma URL conhecida pelo usuário.

## 5. Grafo observado das jornadas

```text
Arena /
  └─ agenda → /eventos/:eventId
                  └─ voltar para agenda
                  X  sem link ou marketId para /odds/mercados/:marketId

/odds/mercados/:marketId  (somente por UUID conhecido)
  ├─ visitante → Keycloak → /
  │                         X perdeu o mercado de origem
  ├─ aluno elegível → bilhete / comprovante
  └─ conta sem matrícula → /conta/matricula

/conta/perfil  (somente por URL conhecida ou por CTA contextual)
  ├─ /conta/matricula
  ├─ /conta/vinculo-atleta
  ├─ /conta/perfil/esportivo
  └─ logout

/operacao/*  (somente por URL conhecida)
  └─ perfis seed incompletos → /conta/perfil/completar
```

## 6. Matriz de personas e jornadas

Legenda: **Crítica** impede a proposta central; **Alta** bloqueia ou contradiz uma jornada essencial; **Média** degrada compreensão, confiança ou operação; **Baixa** é acabamento; **BLOCKED** indica que a massa/autorização do UAT não permitiu concluir sem mutação.

| Persona | Ponto inicial | Objetivo e passos esperados | Rotas percorridas | Resultado observado | Resultado esperado | Severidade | Evidência | Requisito/fase | Cobertura automatizada existente | E2E ausente | Proposta sem implementação |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Visitante | `/` | Abrir agenda, escolher evento e descobrir mercado disponível | `/` → `/eventos/:eventId` | Evento abriu, mas só ofereceu voltar à agenda; mercado existente no banco não foi anunciado | Evento deve mostrar CTA de odds quando houver mercado público e usar linguagem adequada quando não houver | **Crítica** | Evento das 18h possuía mercado `5601e9f5-...`; UI sem link; `PublicEventDetail` não tem `marketId` | AUTH-01, SPRT-12, ODDS-09; Fases 3–4 | Agenda→evento mockada em `sports-pilot.spec.ts`; mercado por deep link em `odds-pilot.spec.ts` | Arena→evento real→mercado real | Expor referência/estado resumido de mercado no contrato público ou oferecer endpoint de descoberta por `eventId`; renderizar CTA no evento |
| Visitante | `/` | Navegar por competição, equipe, organização e atleta sem UUID manual | `/` → evento; demais páginas testadas por deep link | Competição/equipe/organização/atleta funcionam isoladamente; Arena só lista eventos e o evento não liga para as entidades | A navegação pública deve formar uma malha bidirecional compreensível | **Alta** | Deep links funcionaram; equipe e organização possuem links internos, mas o ponto inicial e o evento não os expõem | AUTH-01, SPRT-12, SPRT-15, SPRT-16; Fase 3 | Teste mockado abre competição e depois usa `page.goto` para organização/atleta | Jornada integral sem `page.goto` intermediário | Tornar nomes de competição e participantes links semânticos; incluir breadcrumbs/retorno contextual |
| Visitante | Mercado por deep link | Iniciar login no contexto e retornar ao mesmo mercado | `/odds/mercados/5601...` → Keycloak → `/` | Login funcionou, mas descartou o contexto | Retornar ao mercado original após callback, com URL validada e same-origin | **Alta** | URL final `/`; nenhum palpite enviado | AUTH-03, IDEN-03, IDEN-04, PRED-01; Fases 1–2–5 | Walking skeleton espera retorno fixo à raiz; prediction E2E mocka sessão | Login contextual real de rota pública | Persistir destino seguro no início do login e consumi-lo uma vez no callback |
| Aluno regular | `/` após login | Identificar sessão e encontrar “Minha conta” | `/` | `/api/v1/session` informou `authenticated: true`, mas o cabeçalho continuou “Entrar”; não havia conta nem logout | Shell público deve refletir sessão e oferecer conta/logout | **Alta** | `arena-shell.component.html` contém links estáticos “Entrar”; componente não injeta `SessionStore` | AUTH-03; Fase 2 | `ConsoleShell` tem testes de sessão; Arena mockada espera nenhuma chamada de sessão | Shell público autenticado com backend real | Tornar Arena session-aware e reutilizar um estado de sessão único entre shells e bilhete |
| Aluno regular | `/conta/perfil` por deep link | Consultar perfil e matrícula consistentes | `/conta/perfil` ↔ `/conta/matricula`; refresh e back/forward | Perfil: “Nenhuma matrícula registrada”; matrícula: “Matrícula registrada” | Todas as telas devem derivar o mesmo vínculo autoritativo | **Alta** | `ProfileController.DEFAULT_MATRICULA_STATUS = "NAO_REGISTRADA"`; endpoint de matrícula consulta vínculo real | VERI-09, PROF-01; Fase 2 | Componentes testam seus contratos separadamente; mock do console mantém status sincronizado | Integração real perfil↔matrícula | Remover constante e projetar status pelo domínio de matrícula, preservando fronteira de módulos via porta pública |
| Aluno regular | Evento conhecido | Ver saldo e chegar naturalmente ao mercado | `/` → `/eventos/:eventId`; mercado apenas por deep link | Saldo `950.00` apareceu no mercado; descoberta falhou antes disso | Mercado deve ser alcançável do evento e saldo deve aparecer somente após autenticação/elegibilidade | **Crítica** | Banco e bilhete reais; ausência de link no evento | CRED-03, PRED-01, PRED-03; Fase 5 | Bilhete mockado cobre saldo; nenhum fluxo home→mercado | Home→evento→mercado→saldo real | Conectar evento/mercado e adicionar E2E com massa determinística |
| Aluno regular | Mercado `OPEN` por deep link | Escolher seleção/stake e conferir cálculos antes do prazo | `/odds/mercados/5601...` | Às ~20h, fechamento exibido como 17h30; mercado seguia “Aberto”; seleção HOME + 10 habilitou envio e calculou retorno 19/lucro 9 | UI deve considerar deadline canônico e bloquear imediatamente, além da rejeição autoritativa do servidor | **Alta** | Somente GETs e estado local; `availableState` verifica `state`, não `closeDeadline` | PRED-01, PRED-04, PRED-08, DATA-03; Fase 5 | Backend cobre rejeição por prazo; frontend mocka snapshots válidos | Relógio real/fixture expirada no navegador | Derivar disponibilidade de `state + closeDeadline`, com relógio testável; manter servidor como autoridade final |
| Aluno regular | Mercado do palpite existente | Recarregar e recuperar comprovante | `/odds/mercados/c1577f92-...` + refresh | Comprovante reapareceu com AWAY, 50, 2.12, retorno 106, lucro 56; formulário de novo envio não apareceu | Mesmo comportamento | **Passa** | UI real e banco com um palpite `PENDING` | PRED-02, PRED-03, PRED-05, PRED-06; Fase 5 | `prediction-pilot.spec.ts` cobre submit/reload mockado | Repetição no ambiente integrado sem criar novo palpite | Manter; adicionar E2E integrado com fixture preexistente e leitura somente |
| Aluno regular | Mercado já apostado | Não duplicar palpite | `/odds/mercados/c157...` | UI não ofereceu segundo envio; tentativa de POST não foi autorizada | Bloqueio também deve permanecer no servidor sob retry/concorrência | **Passa / não mutado** | Constraint única e testes de concorrência; nenhum POST no UAT | PRED-02, DATA-05; Fase 5 | Backend/Testcontainers cobre duplicação e concorrência | E2E integrado de erro duplicado controlado em banco descartável | Manter regra; testar somente em ambiente efêmero autorizado |
| Aluno regular | Dentro da conta | Sair e confirmar limpeza da sessão | menu da conta → logout Keycloak → `/` | Logout funcionou; `/api/v1/session` passou a anônimo | Mesmo comportamento, com acesso descobrível desde Arena | **Passa com gap de descoberta** | Sessão real encerrada | AUTH-03, IDEN-04; Fases 1–2 | Testes de logout e sessão existentes | Arena autenticada→logout | Expor menu de sessão no shell público |
| Atleta | Conta por deep link | Encontrar vínculo e perfil esportivo; abrir perfil público | `/conta/perfil` → `/conta/perfil/esportivo` → `/atletas/:athleteId` | Fluxo interno funcionou e perfil público abriu; entrada inicial da conta continuou órfã | Conta deve ser descobrível globalmente | **Alta** para descoberta; **Passa** após entrada | Link esportivo presente dentro da conta | SPRT-16, PROF-02; Fases 2–3 | Specs de perfil esportivo e público; owner flow mockado | Arena→conta→perfil esportivo real | Corrigir shell global; preservar links atuais |
| Atleta | Perfil público | Conferir fronteira público/privado | `/atletas/:athleteId` | Dados esportivos públicos sem matrícula/e-mail; foto ausente gerou 404 no console | Nenhuma PII e fallback de mídia sem erro ruidoso | **Média** | DOM sem PII; request de foto 404 | PROF-02, SPRT-16, PRIV-01; Fases 2–3 | Testes de privacy e componentes | Mídia ausente em navegador real | Usar placeholder/fallback e tratar ausência esperada sem console error |
| Administrador geral | `/` após login | Encontrar console, painel, papéis, matrícula e esportes | `/` e deep links `/operacao/*` | Arena sem entrada operacional; usuário seed foi redirecionado ao onboarding `/conta/perfil/completar` | Operador com perfil completo deve descobrir apenas destinos permitidos por capability | **Alta** para descoberta; **BLOCKED** para telas | Sessão admin separada; gate de perfil impediu acesso | OPER-01..04, IDEN-05; Fases 2–4 | Console/capabilities testados com mocks; deep links operacionais | Login real→menu→cada destino com persona pronta | Completar massa seed de operadores em bloco futuro autorizado; adicionar launcher de operação no shell autenticado |
| Administrador geral | Mercado público | Confirmar que papel não concede elegibilidade de aposta | `/odds/mercados/:marketId` | UI mostrou “Vínculo necessário” para admin sem matrícula | Papel administrativo não substitui matrícula | **Passa** | Sessão admin e endpoint de matrícula reais | VERI-09, INTE-03; Fases 2–5 | Backend cobre elegibilidade | Persona admin integrada no E2E | Manter separação; cobrir regressão E2E |
| Maker de odds | Login e operação | Descobrir operação, abrir mercado autorizado e propor override | `/` e `/operacao/odds/mercados/:marketId` | Sem entrada global; onboarding bloqueou UI. GET de capabilities confirmou permissão; proposta pendente não era recuperável no payload consultado | Maker completo deve navegar ao mercado, ver ação permitida e não aprovar a própria proposta | **BLOCKED — massa/onboarding**; gap de descoberta **Alta** | Sessão isolada; capabilities por GET; sem mutação | ODDS-10, ODDS-11, OPER-02; Fase 4 | Backend e `odds-pilot` mockado cobrem ações | Duas sessões reais, proposta persistida e segregação | Preparar personas completas/fixture descartável e E2E de dois atores; oferecer descoberta por lista/evento, não UUID |
| Checker de odds | Login e operação | Abrir proposta de outro ator e aprovar | `/` e `/operacao/odds/mercados/:marketId` | Mesmo bloqueio de onboarding; capability existe, mas UI não pôde recuperar override pendente pelo payload disponível | Checker deve encontrar fila/proposta e aprovar somente proposta alheia | **BLOCKED — massa/contrato UI** | Sessão checker separada; GET sem override pendente | ODDS-11, ODDS-12, ODDS-13; Fase 4 | Backend cobre distinct actor; UI mockada | Fluxo maker→checker real | Expor consulta segura da proposta/fila pendente e criar jornada de duas sessões |
| Moderador | Login | Encontrar apenas superfícies compatíveis com papel | `/` e deep links | Onboarding bloqueou reprodução; capabilities retornaram `VIEW_OPERATIONAL_PANEL` e `VIEW_ODDS_MARKET` | Navegação deve refletir exatamente capabilities vigentes | **BLOCKED — massa/onboarding** | Sessão separada e GET de capabilities | OPER-04, IDEN-05; Fase 2 | Guards/menu com mocks | Persona moderador real pronta | Completar fixture e validar ausência de ações não permitidas |
| Visitante | Deep link protegido | Receber orientação de login | `/conta/perfil` sem sessão | URL permaneceu; body vazio; três `GET /api/v1/profile/me` retornaram 401 | Redirecionar para login ou renderizar estado 401 com retorno contextual | **Alta** | Navegador e rede reais | AUTH-03, IDEN-04; Fases 1–2 | Stores possuem mensagens 401 isoladas | Rota protegida anônima real | Adicionar guard de autenticação/estado explícito antes de carregar a página |
| Qualquer | URL inválida | Receber 404 compreensível e retorno seguro | `/rota-inexistente` | Página vazia e `NG04002` no console | Página 404 com link para Arena, sem exceção não tratada | **Alta** | Console real | UX-01 futuro, mas robustez básica das Fases 1–5 | Testes de rotas só verificam rotas conhecidas | Wildcard real em desktop/mobile | Adicionar rota `**` e página not-found acessível; não confundir com funcionalidade futura |
| Visitante | Mercado suspenso/bloqueado por deep link | Entender que login não altera estado do mercado | `/odds/mercados/:marketId` | Como visitante, viu “Entre para palpitar” mesmo em `SUSPENDED`/`LOCKED` | Mensagem primária deve refletir indisponibilidade do mercado; login é secundário ou oculto | **Média/Alta** | Estado público real + bilhete prioriza `visitor` antes de avaliar mercado | ODDS-09, ODDS-13, PRED-01; Fases 4–5 | Componentes cobrem estados autenticados | Visitante em todos os estados | Compor estado por prioridade: mercado fechado/suspenso antes de elegibilidade/sessão |
| Aluno/atleta | Perfil | Carregar apenas dados permitidos | `/conta/perfil` | Perfil comum chamou `/api/v1/operations/sports/organizations`, recebeu 403 e exibiu indisponibilidade | Perfil não operacional deve usar catálogo público ou não fazer chamada privilegiada | **Média** | Network/console real | PROF-01, OPER-02, PRIV-01; Fases 2–3 | Adapter/componentes mockam resposta | Perfil real sem papel operacional | Trocar dependência por endpoint público mínimo para favorito ou carregar sob capability |
| Todas | 320×800 e teclado | Navegar sem overflow, com skip link e foco visível | Arena e conta | Sem overflow horizontal nos caminhos testados; skip link primeiro; ordem de Tab coerente | Mesmo comportamento em todas as jornadas | **Passa parcial** | Viewport e teclado reais | UX-01 pertence à Fase 7; acessibilidade transversal | Vários specs de componente e E2E mockado | Mercado/operacional/erros completos em mobile | Preservar padrões; incluir axe/teclado nos novos E2Es críticos |

## 7. Matriz de estados transversais

| Estado | Situação | Observação |
|---|---|---|
| Visitante | Testado | Arena, evento, mercado, deep link protegido e 404 |
| Autenticado | Testado | Aluno, atleta, admin, maker, checker e moderador em sessões separadas |
| Sessão expirada | Parcial | Respostas 401 foram observadas em rota protegida; expiração temporal durante uma jornada não foi forçada |
| Matrícula ausente | Testado | Admin no mercado recebeu “Vínculo necessário” |
| Matrícula ativa | Testado | Aluno/atleta; divergência perfil vs matrícula confirmada |
| Saldo insuficiente | Não reproduzido ao vivo | Cobertura isolada existe; massa não fornecia persona autorizada sem saldo e não houve mutação |
| Mercado aberto | Testado | Incluiu caso inconsistente de deadline já vencido |
| Mercado suspenso | Testado como visitante | CTA de login enganosa |
| Mercado encerrado/bloqueado | Testado como visitante | CTA de login enganosa; não houve envio |
| Rota inexistente | Testado | Página vazia + `NG04002` |
| Refresh | Testado | Evento, conta, matrícula e comprovante |
| Voltar/avançar | Testado | Contradição de matrícula persistiu |
| Logout | Testado | Sessão limpa e retorno à Arena |
| Duas personas separadas | Testado | Maker/checker e demais papéis em contextos isolados; operação ficou bloqueada pelo onboarding |
| Largura móvel | Testado parcialmente | Arena e conta sem overflow a 320 px |
| Teclado e foco | Testado parcialmente | Skip link e ordem de Tab coerentes nos caminhos alcançáveis |
| Erros sem PII | Testado parcialmente | Não foi observada PII; faltam cenários operacionais desbloqueados e sessão expirada durante ação |

## 8. Achados por categoria e causa

### 8.1 Navegação e shell

1. **Mercado público órfão — Crítica.** A rota existe, mas a Arena e o evento não fornecem `marketId`. O contrato do evento contém `eventId` e metadados esportivos, sem ligação para odds.
2. **Conta e operação órfãs — Alta.** A Arena só renderiza “Competições” e “Entrar”; não há estado autenticado, conta, logout ou operação.
3. **Login sem retorno contextual — Alta.** O fluxo OIDC retorna à raiz, mesmo iniciado em mercado específico.
4. **Entidades públicas parcialmente desconectadas — Alta.** Páginas isoladas funcionam, mas o usuário não forma o caminho completo desde a Arena.

### 8.2 Estado incoerente

1. **Matrícula contraditória — Alta.** `ProfileController` injeta `NAO_REGISTRADA` como constante, enquanto `/api/v1/enrollment/me` consulta o vínculo real.
2. **Shell e bilhete discordam sobre autenticação — Alta.** O bilhete usa `SessionStore`; o shell da Arena é estático.
3. **Deadline vencido com formulário habilitado — Alta.** O estado do bilhete considera apenas `OddsMarketState`, não a comparação temporal de `closeDeadline`.

### 8.3 Contrato backend/frontend

1. O evento público não expõe descoberta de mercado.
2. A UI operacional não recebeu no payload consultado dados suficientes para recuperar a proposta pendente do seed.
3. O perfil comum depende de endpoint operacional para organizações, gerando 403 esperado mas desnecessário.

### 8.4 Autorização e privacidade

1. **Positivo:** admin sem matrícula não foi considerado elegível para apostar.
2. **Positivo:** perfil público do atleta não expôs e-mail, matrícula ou identidade civil.
3. **Não concluído:** segregação maker/checker não pôde ser reproduzida pela UI porque os perfis seed foram bloqueados pelo onboarding. Não há evidência para classificar isso como defeito de autorização.

### 8.5 Artefato de build

`frontend/Dockerfile` copia `frontend/dist` e não executa `ng build`. Portanto, reconstruir a imagem sem compilar antes pode servir bundle antigo. O comentário “builder multi-stage” não corresponde a um estágio de build Angular real.

**Classificação:** Alta para confiabilidade do ambiente de validação.  
**Proposta:** ou construir o Angular hermeticamente dentro da imagem com versões/lockfile aprovados, ou tornar o wrapper `dev.mjs up full` responsável por build obrigatório, digest e falha fechada quando `dist` estiver stale. A escolha tem impacto de infraestrutura e precisa de decisão específica.

### 8.6 Massa de teste e seeder

O seed deriva `marketId` de `market:${index}`, enquanto `eventId` deriva da origem da partida. Alternar perfis com primeiras partidas diferentes reutiliza o mesmo `marketId` para outro evento, causando colisão de `pk_odds_market`. Reexecutar o mesmo perfil mantém a associação e funciona.

**Classificação:** Média/Alta para reprodutibilidade local.  
**Proposta:** derivar o mercado de uma chave estável do evento + tipo, ou tornar a convergência capaz de detectar e reconciliar perfil anterior. A correção deve preservar idempotência e não editar migrations aprovadas sem necessidade.

### 8.7 Acessibilidade e tratamento de erro

1. Skip link, Tab e reflow móvel passaram nos caminhos alcançados.
2. Rota inexistente e deep link protegido anônimo falham com página vazia.
3. Foto opcional ausente gera 404 ruidoso em vez de fallback.
4. Estados de mercado para visitante priorizam autenticação e podem comunicar ação impossível.

### 8.8 Funcionalidades futuras

Não foram classificados como defeito: liquidação, estorno, compensação, histórico completo, ranking, grupos, conquistas e comunidade. O comprovante atual usa estado “Pendente”, coerente com a Fase 5. Não foi observada promessa explícita de pagamento ou ranking já disponível.

## 9. Cobertura automatizada e lacunas

| Risco | Cobertura atual | Lacuna relevante |
|---|---|---|
| Sessão BFF/OIDC e ausência de token no browser | Forte em backend e walking skeleton real | Retorno contextual e shell público autenticado |
| Agenda e páginas esportivas | Componentes e `sports-pilot` com mocks | Navegação completa por links reais, sem deep links artificiais |
| Odds públicas | Backend forte; página e `odds-pilot` por UUID mockado | Descoberta evento→mercado e estados reais |
| Palpite | Backend/Testcontainers forte; Playwright 14/14 com APIs mockadas | Jornada real desde Arena, Keycloak e banco; deadline expirado na UI |
| Perfil/matrícula | Componentes/stores separados | Consistência cross-endpoint com dados reais |
| Operação/capabilities | Backend e componentes com mocks | Personas seed completas, entrada global e duas sessões maker/checker |
| Rotas inválidas/protegidas | Quase ausente | 404, 401, retorno ao login e retomada do contexto |
| Mobile/teclado | Cobertura parcial em specs e UAT | Jornada crítica completa e estados de erro |
| Docker full | Healthcheck e documentação | Garantia de que a imagem contém o bundle produzido do source atual |
| Seeder | Testes do SQL do mesmo perfil | Alternância `daily`↔`full` no mesmo volume |

O fato de a Fase 5 registrar Playwright `14/14` não contradiz o achado: esses testes validam o bilhete e seus estados com interceptações controladas, não a descoberta do mercado nem a integração do shell/autenticação.

## 10. Recomendação de fase corretiva

Recomenda-se inserir, mediante aprovação separada, uma fase **05.1 — Convergência das Jornadas do Usuário** antes da Fase 6. Não houve alteração no ROADMAP durante esta auditoria.

### Por que antes da Fase 6

- A Fase 6 adicionará histórico, liquidação, ranking e grupos sobre uma navegação que ainda não conduz ao palpite.
- Corrigir depois aumenta o número de shells, CTAs e contratos dependentes do mesmo problema.
- A divergência de matrícula e o deadline visual afetam confiança e podem confundir validação de resultados futuros.
- Uma base E2E integrada agora reduz regressões nas jornadas mais valiosas do produto.

### Fatias corretivas candidatas

| Fatia | Benefício | Dependências/risco | Trade-off de separação |
|---|---|---|---|
| Shell, sessão e navegação da conta | Corrige estado autenticado, conta, logout e operação | OIDC, return URL e menu por capability | Deve vir cedo porque desbloqueia quase todas as personas |
| Evento → mercado → palpite | Entrega a jornada central | Mudança de contrato OpenAPI e decisão de cardinalidade evento/mercado | Pode ser separada do shell, mas precisa do retorno contextual para fechar a jornada autenticada |
| Consistência de matrícula | Remove contradição de domínio | Fronteira Spring Modulith entre identity e enrollment | Pequena em superfície, mas arquiteturalmente sensível |
| Deadline e precedência de estados | Evita CTA/formulário impossível | Relógio testável e regra de apresentação | Deve manter servidor como autoridade e não duplicar regra de negócio de forma divergente |
| Docker build | Garante bundle atual | Tempo de build, cache, supply chain e imagem Node aprovada | Pode ser plano independente de infraestrutura |
| Idempotência entre perfis do seeder | Restaura reprodutibilidade | Chaves determinísticas e dados já existentes | Deve ter teste de alternância em volume descartável |
| Playwright de jornadas reais | Prova integração | Massa estável, múltiplas sessões e custo de execução | Deve acompanhar cada fatia, não ser deixado apenas para o final |
| Documentação manual | Alinha procedimento ao produto | Depende das decisões anteriores | Última fatia, após comportamento estabilizado |

### Decisões que exigem aprovação específica

1. Contrato de descoberta: incluir `marketId` no evento, lista resumida de mercados ou endpoint por `eventId`.
2. Estratégia de retorno OIDC e validação contra open redirect.
3. Fonte de verdade da matrícula através da fronteira Modulith.
4. Build Angular dentro do Docker versus build obrigatório no wrapper.
5. Política de seed ao alternar perfis no mesmo volume.
6. Massa E2E para operadores: completar perfis seed ou criar fixture efêmera dedicada.

## 11. Evidências e comandos executados

Principais comandos somente leitura:

```powershell
git status --short --branch
git rev-parse HEAD
git rev-list --left-right --count origin/master...master
docker ps --format "table {{.Names}}\t{{.Status}}"
docker inspect infra-backend-1 ...
docker exec ... psql ... "BEGIN READ ONLY; SELECT ...; COMMIT;"
rg --files ...
rg -n ...
Get-Content ...
playwright-cli list
playwright-cli -s=<sessao> close
```

Observações da ferramenta:

- `playwright-cli` informou diferença entre a skill local 0.1.18 e a ferramenta 0.1.20;
- nenhuma atualização foi executada;
- a tentativa de listar requests de várias sessões acionou uma assertion interna `UV_HANDLE_CLOSING` no Windows;
- a falha foi apenas da ferramenta de inspeção e não alterou a aplicação;
- as sessões foram encerradas individualmente com sucesso;
- a prova autoritativa de não mutação veio do PostgreSQL: um palpite, um débito e saldos inalterados.

## 12. Critério de saída desta auditoria

O diagnóstico está pronto para revisão do responsável. Nenhuma correção ou decomposição definitiva em planos deve começar sem nova aprovação explícita. A autorização deste UAT termina com a entrega deste artefato.
