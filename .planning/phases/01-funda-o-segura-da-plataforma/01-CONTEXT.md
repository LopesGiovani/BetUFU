# Fase 1: Fundação Segura da Plataforma — Contexto

**Coletado em:** 2026-08-01
**Status:** Pronta para planejamento

<domain>
## Limite da fase

Esta fase entrega a primeira base executável da BETUFU: estrutura inicial da interface Angular, backend Java/Spring modular, sessão de navegador protegida por BFF e Keycloak, persistência e migrações confiáveis, contratos de API, ambiente local reproduzível, observabilidade e verificações arquiteturais automatizadas.

Ela não entrega os fluxos de negócio das fases seguintes, como verificação de matrícula, cadastro esportivo, odds, créditos, palpites, liquidação, rankings, grupos ou chat. A única interface funcional específica desta fase é a estrutura inicial da BETUFU acompanhada do diagnóstico técnico protegido definido abaixo.

</domain>

<decisions>
## Decisões de implementação

### Entrega visível da fase

- **D-01:** O resultado visível será a estrutura inicial da interface BETUFU em Angular, acompanhada de uma tela de diagnóstico protegida. Nos documentos e na comunicação com o usuário, usar “estrutura inicial da interface BETUFU” em vez do jargão “Shell Angular”.
- **D-02:** A tela de diagnóstico poderá ser aberta somente por uma conta técnica autenticada nos ambientes de desenvolvimento e homologação. A rota não deverá existir no build de produção.
- **D-03:** O diagnóstico exibirá, de forma resumida e segura, versão/build, ambiente e estado da API, sessão BFF, Keycloak, PostgreSQL, Redis e armazenamento. Cada componente será classificado como disponível, degradado ou indisponível, e a tela mostrará um correlation ID copiável.
- **D-04:** Quando uma dependência falhar, o diagnóstico continuará aberto em modo degradado, identificará o componente afetado e oferecerá nova tentativa. Não exibirá segredos, endereços internos ou stack traces.

### Comportamento da sessão

- **D-05:** Toda sessão expirará após 8 horas sem atividade. A mesma regra valerá para usuários comuns, administradores e contas técnicas, e cada atividade válida renovará o prazo de inatividade.
- **D-06:** Não haverá duração máxima absoluta no MVP. O contrato de sessão deverá representar explicitamente essa ausência, sem inventar outro limite durante o planejamento.
- **D-07:** O MFA feito no login administrativo permanecerá válido durante a sessão ativa; não haverá nova confirmação de MFA por operação sensível no MVP.
- **D-08:** Um usuário poderá manter sessões simultâneas e independentes em vários dispositivos. A inatividade e a expiração serão calculadas separadamente para cada sessão.
- **D-09:** A ação voluntária “Sair” encerrará somente a sessão do dispositivo atual. O MVP não terá uma opção para o usuário encerrar todas as suas sessões.

### Ambiente local

- **D-10:** O ambiente local terá dois modos suportados. O modo diário padrão será híbrido: PostgreSQL, Keycloak, Redis, MinIO e o serviço de e-mail de desenvolvimento rodarão em contêineres, enquanto Spring Boot e Angular rodarão pelo terminal ou pela IDE. Um perfil completo de validação também executará Spring em contêiner e servirá o Angular compilado por Nginx, permitindo verificar a composição reproduzível usada fora do ciclo rápido de desenvolvimento.
- **D-11:** A primeira inicialização será automática e reproduzível. Ela preparará banco e migrações, Keycloak com clientes e papéis, Redis, armazenamento, serviço de e-mail de desenvolvimento e uma conta técnica exclusivamente local. Nenhuma credencial real será versionada no Git; wrappers, runtimes e imagens terão versões fixadas.
- **D-12:** Parar e iniciar o ambiente preservará os dados locais. Haverá uma reinicialização separada e explícita que apagará somente os dados locais da BETUFU e recriará o ambiente, sempre após confirmação clara.
- **D-13:** Windows 11 com Docker Desktop/WSL2 e Linux serão as plataformas principais de desenvolvimento do MVP; Linux também atenderá à integração contínua. macOS será compatível quando possível, mas não constituirá requisito obrigatório. Os comandos principais deverão ser independentes do sistema operacional sempre que possível.

### Erros da aplicação

- **D-14:** Erros apresentados ao usuário usarão linguagem simples em português, explicarão o que não funcionou, indicarão a próxima ação possível e fornecerão um código de referência copiável. Detalhes técnicos, endereços internos e stack traces não aparecerão na interface.
- **D-15:** Erros de formulário preservarão os valores digitados, marcarão cada campo inválido com uma explicação específica e levarão o foco ao primeiro erro. Uma mensagem geral será usada somente quando o problema não pertencer a um campo.
- **D-16:** Apenas consultas que carregam informações poderão ser repetidas automaticamente. Ações que alteram dados nunca serão repetidas silenciosamente; durante o envio, o controle de ação ficará bloqueado e, diante de uma falha, o usuário receberá uma opção segura de tentar novamente.
- **D-17:** Uma falha isolada afetará somente a área correspondente da página, mantendo o restante e a navegação funcionais. Uma tela de erro completa será usada apenas quando a BETUFU não puder iniciar ou continuar com segurança.

### Discrição dos agentes posteriores

- Definir quais eventos técnicos contam como atividade válida para renovar o prazo ocioso, mantendo a regra aprovada de 8 horas.
- Definir regeneração de sessão e revogação administrativa ou de segurança exigidas por `IDEN-04`. Essas regras não foram decididas na discussão e não devem ser confundidas com o logout voluntário do dispositivo atual.
- Escolher nomes e composição dos comandos de inicialização e reinicialização local, preservando o comportamento aprovado e limitando qualquer remoção aos recursos locais da BETUFU.
- Definir códigos estáveis, estrutura completa de Problem Details, quantidade de novas tentativas automáticas e estratégia de espera, respeitando as regras de apresentação e repetição aprovadas.
- Definir detalhes visuais da estrutura inicial e dos estados de carregamento, vazio e sucesso que não contrariem as decisões registradas.

</decisions>

<canonical_refs>
## Referências canônicas

**Agentes posteriores devem ler estes documentos antes de pesquisar, planejar ou implementar.**

### Produto, arquitetura e limites

- `.planning/PROJECT.md` — define o produto, a arquitetura aprovada, as restrições de segurança e privacidade e a exigência de aprovação explícita para novas decisões arquiteturais e commits.
- `.planning/ROADMAP.md` — a seção “Phase 1: Fundação Segura da Plataforma” define objetivo, dependências, requisitos associados e critérios de sucesso desta fase.

### Requisitos rastreáveis

- `.planning/REQUIREMENTS.md` — consultar especificamente `ARCH-01`, `ARCH-02`, `ARCH-03`, `IDEN-01`, `IDEN-02`, `IDEN-03`, `IDEN-04`, `DATA-01`, `DATA-02`, `DATA-06`, `API-01`, `API-02`, `OPS-01`, `OPS-02`, `OPS-03`, `TEST-01` e `TEST-02`.

Não existem especificações ou ADRs externos adicionais para esta fase.

</canonical_refs>

<code_context>
## Informações sobre o código existente

### Recursos reutilizáveis

- Nenhum. O repositório contém somente documentos de planejamento; ainda não há código da aplicação, componentes, utilitários ou mapas de codebase.

### Padrões já estabelecidos

- A arquitetura aprovada em `.planning/PROJECT.md` determina monorepo com `backend/`, `frontend/`, `infra/` e `docs/`, Angular/TypeScript no frontend e Java/Spring Modulith no backend.
- PostgreSQL é a fonte oficial; Redis é auxiliar; Keycloak cuida da autenticação; o navegador usa uma sessão BFF sem armazenar tokens.
- REST `/api/v1`, OpenAPI, Problem Details, Flyway, Docker Compose, testes de módulos e verificações arquiteturais já são restrições do projeto, não escolhas a serem refeitas pelo planejamento.

### Pontos de integração

- Esta fase criará os primeiros pontos de integração entre Angular, BFF Spring, Keycloak, PostgreSQL, Redis e armazenamento S3 compatível.
- O contrato OpenAPI será a fronteira entre frontend e backend, e as migrações Flyway serão a fronteira versionada para o PostgreSQL.
- A tela de diagnóstico protegida será a prova integrada de que a fundação funciona nos ambientes autorizados.

</code_context>

<specifics>
## Ideias específicas

- A entrega deve ser descrita como “estrutura inicial da interface BETUFU”, evitando depender do termo “Shell Angular”.
- O diagnóstico deve usar os estados “disponível”, “degradado” e “indisponível” e continuar utilizável quando apenas uma dependência falhar.
- Exemplo do tom esperado para erro: “Não foi possível carregar os dados. Tente novamente. Código: ABC123.” O código real deve ser copiável e correlacionável com os registros técnicos.
- O fluxo local deve favorecer o trabalho diário em IDE/terminal e também oferecer um perfil completo em contêineres para validar Spring e o Angular servido por Nginx com a mesma base de dependências.

</specifics>

<deferred>
## Ideias adiadas

- Exigir nova confirmação de MFA antes de operações administrativas sensíveis — melhoria pós-MVP. No MVP, o MFA realizado no login permanece válido durante a sessão ativa.

</deferred>

---

*Fase: 1-Fundação Segura da Plataforma*
*Contexto coletado em: 2026-08-01*
