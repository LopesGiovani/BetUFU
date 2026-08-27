# Fase 3: Experiência Esportiva do Piloto — Contexto

**Coletado em:** 2026-08-26
**Status:** Pronta para planejamento

<domain>
## Limite da fase

Esta fase entrega a base esportiva pública e administrativa da BETUFU: catálogo genérico de esportes, competições e edições; organizações esportivas, equipes, atletas e elencos; agenda, locais, eventos, resultados públicos, chaveamentos e classificações transcritas de fonte oficial. A primeira edição real será a Olimpíada Universitária UFU 2026, mas o domínio não pode ser específico desse evento — ele deve comportar outras competições ao longo do ano.

Ela também entrega o vínculo proposto entre conta e atleta pela coincidência do HMAC versionado da matrícula, sem conservar a matrícula e sem alegar validação oficial da UFU.

Ela **não** entrega: odds ou mercados (Fase 4), créditos e apostas (Fase 5), liquidação e ranking (Fase 6), chat (Fase 7), motores completos para provas com vários competidores, estatísticas específicas por modalidade, escalação por partida, importação XLSX, validação documental de atleta ou integração automática com uma fonte oficial.

</domain>

<decisions>
## Decisões de implementação

**Numeração:** esta série continua o `02-CONTEXT.md`, que terminou em `D-52`.

### Catálogo esportivo genérico

- **D-53:** O domínio segue a hierarquia `Esporte → Competição → Edição → Categoria → Fase → Evento`. A Olimpíada UFU é uma competição, 2026 é uma edição e os documentos de 2025 servem somente como referência de domínio. — **Reversibility:** costly — fixar competição ou edição diretamente nos eventos exigiria migrar dados e contratos públicos quando a segunda competição fosse cadastrada.
- **D-54:** A compatibilidade operacional pertence ao **formato do evento**, não a uma lista rígida de modalidades. O MVP detalha eventos `HEAD_TO_HEAD`; modalidades com provas coletivas ou vários competidores entram no catálogo e na agenda básica, mas sem baterias, raias, marcas, pódios ou regras próprias.
- **D-55:** Todas as modalidades conhecidas podem ser catalogadas desde o início. A adequação de cada uma ao modelo deverá ser reavaliada antes de ganhar operação detalhada; a extensão futura para formatos multi-competidor deve ser possível, mas não justifica criar classes vazias ou estruturas especulativas agora.

### Organizações, equipes e favorito

- **D-56:** A atlética, delegação ou organização esportiva é permanente entre edições. Cada edição cria suas próprias equipes por modalidade/categoria e seus próprios elencos, preservando o histórico de 2026, 2027 e anos seguintes.
- **D-57:** O favorito do usuário aponta para a organização esportiva inteira, não para uma equipe de uma modalidade. A página da organização agrega equipes, elencos públicos autorizados, próximos eventos e resultados. Esta decisão substitui a semântica “equipe favorita” reservada pela `D-45`; o atual nome técnico `favoriteTeamId` deve ser reconciliado pelo planejamento sem manter um contrato público enganoso. — **Reversibility:** costly — a referência já existe no perfil e sua mudança pode exigir migration e regeneração de contrato.
- **D-58:** Um mesmo atleta possui um registro esportivo global e pode participar de vários elencos, modalidades e edições. O sistema não duplica a identidade esportiva para cada equipe.

### Administração escopada e publicação

- **D-59:** O administrador geral cria competições/edições e atribui `ADMINISTRADOR_COMPETICAO`. Vários administradores podem compartilhar o mesmo UUID de competição em `scopeRef`; cada um opera somente seu escopo e não pode conceder papéis.
- **D-60:** Dados esportivos nascem em `RASCUNHO`, passam por pré-visualização e só ficam públicos após ação explícita. Correção posterior exige fonte ou motivo e preserva ator, horário e versões. Operações concorrentes não podem sobrescrever silenciosamente o trabalho de outro administrador.
- **D-61:** Uma única pessoa autorizada pode publicar no MVP; não existe dupla aprovação obrigatória para cadastro esportivo comum.

### Agenda, locais e navegação pública

- **D-62:** A entrada pública é orientada por agenda: `Hoje`, próximos eventos e filtros por esporte, competição/edição, categoria, fase e estado. Cada item deixa clara a competição a que pertence; páginas de competição, organização, equipe, atleta e evento complementam a navegação.
- **D-63:** Local é reutilizável e pode conter espaços internos, como `Centro Esportivo UFU → Ginásio 05`. O espaço interno, endereço, mapa e observações são opcionais; o evento pode permanecer com `LOCAL_A_DEFINIR`.

### Chaveamento e classificação

- **D-64:** O chaveamento oficial é transcrito manualmente pelo administrador; a BETUFU não sorteia confrontos no MVP. Ele é derivado dos eventos ligados, admite rodadas, `bye`, disputa de terceiro lugar e participante definido pelo vencedor de evento anterior, sem manter uma cópia editável divergente.
- **D-65:** Fases de grupos e pontos corridos exibem jogos e resultados, mas a tabela/classificação é transcrita do boletim oficial. Critérios automáticos de pontuação e desempate ficam para motores futuros por modalidade/regulamento.

### Fontes, resultados e liquidação futura

- **D-66:** Resultado público é registrado com fonte oficial. O boletim oficial mais recente prevalece somente quando declara correção ou substituição; fontes anteriores permanecem no histórico. Informação verbal ou mensagem informal pode gerar pendência, mas não confirma o placar.
- **D-67:** O administrador pode confirmar sozinho quando estiver seguro ou marcar `AGUARDANDO_REVISAO` para ajuda opcional de outro administrador. O resultado confirmado atualiza agenda e chaveamento; a Fase 3 não movimenta créditos.
- **D-68:** A Fase 6 consome a versão esportiva já confirmada; não recria nem reconfirma o cadastro do placar. No MVP, quem registrou ou confirmou o resultado pode também autorizar aquela versão para liquidação. Os atores dessas ações devem permanecer separados no histórico para tornar a política de duas pessoas (*maker-checker*) uma evolução futura, obrigatoriamente reavaliada antes de prêmio real, crescimento operacional relevante ou parceria institucional. Correção, reversão e reliquidação nunca apagam versões anteriores. Esta decisão substitui somente a exigência anterior de segundo confirmador para **resultados**; o segundo administrador no override excepcional de odds continua obrigatório. — **Reversibility:** costly — exigir pessoas distintas depois altera autorização e fluxos da liquidação, mas não deve exigir remodelar o histórico se os atores já forem persistidos separadamente.

### Atletas, matrícula e vínculo com conta

- **D-69:** Atleta pode existir sem conta BETUFU. Ao cadastrá-lo, o administrador informa a matrícula apenas como entrada transitória; o backend normaliza, calcula o mesmo HMAC versionado da Fase 2 e descarta o valor bruto, sem resposta, log, URL ou persistência consultável.
- **D-70:** Matrícula coincidente prova apenas que os dois cadastros informaram o mesmo valor; não prova vínculo ativo com a UFU, titularidade ou legitimidade esportiva. O MVP não recebe comprovante, não usa armazenamento documental e não apresenta selo `VERIFICADO_PELA_UFU`.
- **D-71:** Correspondência única entre HMAC de conta e de atleta cria uma **proposta**, nunca vínculo silencioso. A pessoa precisa aceitar ou rejeitar; ambiguidade, HMAC já ligado ou múltiplos candidatos bloqueiam a associação e exigem tratamento administrativo auditado.

### Elencos e importação

- **D-72:** Elenco pertence à equipe daquela edição. Nasce como rascunho, é publicado manualmente e, depois disso, qualquer alteração exige motivo e auditoria. A futura abertura de mercado deve depender de elenco publicado.
- **D-73:** O MVP não cadastra titulares nem reservas por partida. Escalações ficam como evolução futura; o modelo atual não deve transformar `TITULAR` ou `RESERVA` em propriedade permanente do atleta.
- **D-74:** O administrador pode cadastrar individualmente ou importar CSV UTF-8 usando um modelo fixo da BETUFU. A importação valida todas as linhas e é atômica: havendo um erro, salva zero; estando válida, cria tudo em rascunho. Equipes, modalidades e categorias referenciadas precisam existir e não são criadas implicitamente.
- **D-75:** A entrada tabular usa uma linha lógica comum com nome esportivo, matrícula transitória, organização/equipe, modalidade, categoria e período de vínculo. O parser deve ficar separado da validação/importação para permitir um parser XLSX futuro sem duplicar regras, HMAC, transação ou auditoria.

### Perfil público do atleta

- **D-76:** Depois de aceitar o vínculo, somente o próprio atleta controla foto, curso, biografia e links de redes sociais, todos opcionais e removíveis. Administrador não publica foto pessoal em nome dele; atleta sem conta usa avatar padrão.
- **D-77:** Equipes, modalidades, participações e resultados vêm dos registros esportivos oficiais. No MVP, estatísticas públicas são genéricas e confiáveis — participações, vitórias, derrotas e resultados disponíveis —; gols, cartões, sets, tempos e métricas específicas ficam para evolução por modalidade.
- **D-78:** Matrícula, HMAC, e-mail, identificadores internos e dados de autenticação nunca fazem parte do perfil público. Biografia não aceita HTML executável e links sociais precisam de validação segura.

### Política de apostas do MVP

- **D-79:** O vínculo de atleta **não bloqueia apostas no MVP**: a pessoa pode apostar em qualquer mercado, inclusive em evento ou equipe de que participa e tanto a favor quanto contra. O risco de informação interna e influência sobre créditos/ranking virtuais foi apresentado e aceito para evitar complexidade prematura.
- **D-80:** A relação atleta–equipe–evento deve permanecer modelada para permitir uma política futura sem reconstruir o domínio. A regra será reavaliada obrigatoriamente antes de prêmio real, crescimento relevante ou mudança de enquadramento. Administradores gerais continuam proibidos de apostar em qualquer evento e administradores de competição continuam proibidos no próprio escopo.

### Discrição dos agentes posteriores

Pesquisador e planejador podem decidir, de forma conservadora e registrada:

- nomes técnicos, pacotes e limites internos do novo módulo esportivo, preservando Spring Modulith;
- paginação, ordenação estável, códigos de erro e textos finais;
- limites de tamanho/formato para foto, biografia e CSV, e processamento seguro da imagem;
- estratégia de cache e atualização da agenda pública;
- mecanismo de concorrência otimista e formato físico das versões/fontes;
- desenho visual exato, estados vazios e responsividade, respeitando o contrato de UI que será produzido antes da implementação frontend.

</decisions>

<canonical_refs>
## Referências canônicas

**Agentes posteriores devem ler estas referências antes de planejar ou implementar.**

### Produto, requisitos e fases

- `.planning/PROJECT.md` — valor central, limites do MVP, privacidade, independência institucional e políticas reconciliadas nesta discussão.
- `.planning/REQUIREMENTS.md` — `AUTH-01`, `PROF-01`, `PROF-02`, `SPRT-01` a `SPRT-16`, `OPER-01` a `OPER-03`, `INTE-01` a `INTE-04`, `PRIV-01`, `AUDT-01` e `AUDT-02`.
- `.planning/ROADMAP.md` — seção da Fase 3 e limites das Fases 4, 5 e 6.
- `.planning/STATE.md` — posição atual e decisões acumuladas do projeto.

### Decisões herdadas

- `.planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-CONTEXT.md` — principalmente `D-25`, `D-28` a `D-37`, `D-45` a `D-49` e `D-51`; esta fase substitui a semântica de favorito da `D-45` por `D-57` e somente os demais trechos explicitamente indicados em `D-68`, `D-70` e `D-79`.
- `AGENTS.md` — aprovações, modo pedagógico e orquestração obrigatória.
- `CLAUDE.md` — padrão de prova, mutações discriminantes e suíte agregada.

### Referência esportiva — não representa contrato com a UFU

- `https://www.proae.ufu.br/acontece/2025/07/olimpiada-universitaria-ufu-2025` — exemplo real de calendário, boletins, programas, elencos e chaveamentos; serve somente como referência para o produto genérico e para preparar a edição 2026.
- `https://www.proae.ufu.br/sites/proae.ufu.br/files/media/arquivo/regulamento_geral.pdf` — regulamento oficial de 2025 usado para entender modalidades e organização; não deve ser codificado como regra permanente nem interpretado como parceria institucional.

`arquivo.txt` é uma cópia local fornecida pelo responsável para leitura durante a discussão. Permanece fora do Git e não substitui as fontes oficiais acima.

</canonical_refs>

<code_context>
## Informações sobre o código existente

### Recursos reutilizáveis

- `br.com.betufu.verification.internal.MatriculaNormalizer`, `MatriculaHmac` e `MatriculaHmacKeyProperties` já implementam normalização e HMAC versionado. O módulo esportivo deve reutilizar o **contrato/comportamento**, sem criar uma segunda regra e sem importar tipos `internal` atravessando fronteiras.
- `br.com.betufu.platform.identity.OperationalIdentityLookup` e os papéis com `scopeRef` já projetam identidades e autoridades escopadas sem criar ciclo `platform ↔ identity`.
- `br.com.betufu.audit.AuditRecorder` é a porta existente para ações auditáveis.
- `BetufuProfile.favoriteTeamId` já reserva a referência de favorito, mas o nome e o contrato ainda refletem “equipe”; a Fase 3 deve reconciliá-los com “organização esportiva”.
- A plataforma possui configuração e *probe* de um contrato S3 desacoplado, mas ainda não possui cliente de objetos, upload, transformação, entrega ou deleção. A foto opcional exige que a Fase 3 planeje essa capacidade de mídia com processamento seguro; comprovante documental continua fora do MVP.

### Padrões estabelecidos

- Spring Modulith e ArchUnit impedem ciclos entre módulos; contratos do consumidor pertencem ao módulo consumidor quando necessário.
- Migrations Flyway usam constraints explícitas, chaves de negócio, versão e testes sobre PostgreSQL real.
- REST `/api/v1`, OpenAPI e cliente Angular gerado evoluem juntos; a árvore gerada não é editada manualmente e seu inventário/digest passa por checkpoint humano.
- Comandos mutáveis usam autorização no servidor, idempotência quando necessária, auditoria e testes de concorrência/mutação discriminante.
- Angular 22 zoneless usa standalone components, Signals, Reactive Forms tipados e navegação governada por capacidades do BFF.

### Pontos de integração

- perfil/identidade: favorito da organização, aceite do vínculo e campos opcionais controlados pelo atleta;
- verificação: cálculo e comparação do HMAC sem expor matrícula;
- identidade operacional: `ADMINISTRADOR_COMPETICAO` escopado pelo UUID da competição;
- auditoria: publicação, correção, mudança de elenco, resultado e fonte;
- odds/apostas/liquidação: somente contratos futuros; a Fase 3 não cria mercado nem movimenta crédito.

</code_context>

<specifics>
## Ideias específicas

- A navegação deve lembrar plataformas esportivas com vários campeonatos, como a separação visual entre Brasileirão e Libertadores, e não um site fixo “Olimpíada 2025”.
- A primeira operação real será preparada para a edição 2026.
- A organização “Atlética Engenharia”, por exemplo, pode reunir futsal, vôlei e basquete, enquanto cada equipe/elenco pertence à edição.
- O CSV oficial da BETUFU deve abrir normalmente em Excel e Google Sheets; XLSX pode ser acrescentado depois por outro parser.
- O chaveamento deve ser legível pelo visitante e derivado das partidas, preservando a fonte oficial e suas correções.

</specifics>

<deferred>
## Ideias adiadas

- política que bloqueie ou limite apostas de atletas em eventos próprios;
- confirmação financeira obrigatória por uma segunda pessoa distinta;
- motores completos para provas com vários competidores;
- estatísticas específicas por modalidade;
- titulares e reservas por partida;
- importação XLSX;
- cálculo automático de classificação e desempate por regulamento/modalidade;
- integração automática com fonte oficial;
- verificação documental de atleta e armazenamento privado de comprovantes, se um caso futuro realmente justificar a coleta.

</deferred>

---

*Fase: 3-experiência-esportiva-do-piloto*
*Contexto coletado: 2026-08-26*
