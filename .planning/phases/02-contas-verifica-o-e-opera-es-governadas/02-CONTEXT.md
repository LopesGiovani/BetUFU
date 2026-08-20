# Fase 2: Contas, Verificação e Operações Governadas — Contexto

**Coletado em:** 2026-08-20
**Status:** Pronta para planejamento

<domain>
## Limite da fase

Esta fase entrega três coisas, e somente elas:

1. **A conta BETUFU de ponta a ponta** — criar pelo fluxo hospedado do Keycloak, confirmar e-mail, entrar em visitas futuras, recuperar acesso, sair e manter um perfil público, sem que a BETUFU receba senha.
2. **O registro voluntário da matrícula UFU** — autodeclarado, representado por HMAC, com unicidade garantida e sem conservar o número original de forma consultável.
3. **Os papéis operacionais governados** — conceder, revogar, delimitar escopo, bloquear autoconcessão e auditar.

Ela **não** entrega: verificação documental por comprovante (adiada para a v2 nesta discussão), esportes, equipes e elencos (Fase 3), odds (Fase 4), créditos e apostas (Fase 5), liquidação e ranking (Fase 6), chat e moderação real (Fase 7).

**Redução de escopo decidida nesta discussão.** A Fase 2 passou de 27 para 18 requisitos. `VERI-01` a `VERI-05`, `VERI-08`, `VERI-12` e `OPER-05` foram movidos para a v2 de `REQUIREMENTS.md`; `PRIV-02` foi reatribuído à Fase 3, onde o comprovante de atleta (`SPRT-04`) o mantém vivo no MVP.

</domain>

<decisions>
## Decisões de implementação

**Numeração:** a série continua a do `01-CONTEXT.md`, que terminou em `D-24`, para que nenhuma referência `D-NN` fique ambígua no projeto.

### Escopo da verificação UFU

- **D-25:** A verificação documental do vínculo UFU sai do MVP. No MVP a pessoa **autodeclara** a matrícula e a plataforma verifica somente a **unicidade** por HMAC; não há envio de comprovante, revisor, estados de análise nem exclusão de arquivo. — **Reversibility:** costly — reintroduzir exige reverter a emenda em `ROADMAP.md`, `REQUIREMENTS.md` e `PROJECT.md`, planejar uma fase própria e criar armazenamento privado, papel de revisor e trilha de acesso ao arquivo.
- **D-26:** A matrícula registrada **não expira**. Sem verificação documental não existe evento que justifique revalidação periódica, e o estado `expirado` não é implementado. — **Reversibility:** costly — introduzir validade depois exige coluna nova e uma decisão sobre qual data atribuir a todos os registros já existentes.
- **D-27:** O risco de alguém registrar matrícula alheia foi apresentado ao responsável e **aceito explicitamente**, inclusive a ausência de meio de disputa: como o número original não é conservado, a pessoa legítima e um impostor produzem o mesmo HMAC e nenhum dado armazenado distingue os dois. A única saída é a correção administrativa da `D-32`.

### Matrícula: formato, normalização e duplicidade

- **D-28:** A matrícula da UFU é **alfanumérica** — mistura dígitos e letras, tipicamente codificando ano de ingresso e curso. Descartar não-dígitos destruiria informação significativa e é proibido.
- **D-29:** A normalização, aplicada **antes** de qualquer cálculo criptográfico, é: recortar espaços das pontas, converter letras para maiúscula e descartar separadores não alfanuméricos (hífen, ponto, barra, espaço interno). O resultado normalizado é então validado contra um padrão de matrícula UFU, e entrada malformada é recusada como erro de formulário **antes** de gerar HMAC. — **Reversibility:** **one-way** — alterar a regra de normalização depois muda o HMAC de todo registro existente, e como `VERI-06` proíbe conservar o número original, **não há como recalcular**. Uma mudança de normalização invalida a base inteira de vínculos.
- **D-30:** Colisão de matrícula produz **recusa explícita** — a mensagem diz claramente que aquela matrícula já pertence a outra conta — combinada com **limite de tentativas** por conta e por origem. Toda tentativa recusada é auditada (`AUDT-01`).
- **D-31:** O oráculo de enumeração criado pela recusa explícita é mitigado, não eliminado: cada conta registra no máximo uma matrícula, então sondar em massa exige criar muitas contas com e-mail confirmado. O limite de tentativas é a segunda barreira.
- **D-32:** A matrícula é **imutável para o usuário**. Somente o administrador geral a corrige, com motivo obrigatório e trilha completa; a correção libera a matrícula antiga e registra a nova. Este é o único caminho para erro de digitação, matrícula reemitida e registro indevido por terceiro. — **Reversibility:** reversible.

### Chave HMAC: guarda, versionamento e rotação

- **D-33:** A chave vive em **variável de ambiente `BETUFU_*`**, fora do PostgreSQL e fora do Git, no mesmo padrão que a Fase 1 já usa para `BETUFU_S3_*`. A aplicação **falha fechada** se a variável estiver ausente — não sobe com chave vazia nem com valor padrão. Um dump do banco sozinho não revela nenhuma matrícula.
- **D-34:** A versão da chave fica numa **coluna dedicada** ao lado do HMAC, com restrição de valor válido, indexável junto do HMAC e declarada em migration Flyway, no mesmo estilo de constraints explícitas de `V1` e `V2`. — **Reversibility:** costly — migration.
- **D-35:** Rotação planejada **mantém as chaves antigas ativas somente para comparação**: escritas novas usam a chave corrente, e a checagem de unicidade calcula o candidato sob **todas as versões ativas** e compara com cada uma. Sem isso, `VERI-07` cairia silenciosamente no dia da rotação, porque a mesma matrícula sob `v1` e `v2` produz valores diferentes. — **Reversibility:** **one-way** na direção oposta — descartar uma chave antiga é irreversível e apaga a capacidade de detectar duplicidade contra os registros daquela versão.
- **D-36:** Custo aceito e registrado: rotação assim **reduz o uso da chave, não a exposição** — as chaves antigas continuam existindo e continuam sendo material sensível.
- **D-37:** Comprometimento da chave é tratado como incidente de privacidade, não de higiene. A resposta é **rotacionar, abrir janela de reinformação sob a nova versão e expurgar os HMACs da versão comprometida**. Sem o expurgo, a chave vazada continua útil para quem obtiver um dump depois. Na Fase 2 isto entra como **procedimento escrito**; a ferramenta de expurgo só se constrói se for preciso usar.

### Cadastro, confirmação de e-mail e recuperação

- **D-38:** `verifyEmail: true` no realm. O Keycloak impõe `VERIFY_EMAIL` como *required action* e o login **não conclui** até a confirmação. A BETUFU nunca recebe ID token de conta não confirmada, então não existe estado intermediário no backend nem checagem a esquecer em endpoint novo.
- **D-39:** O perfil BETUFU é **obrigatório antes de navegar**: no primeiro acesso a pessoa escolhe o apelido antes de seguir. Isso estabelece o invariante *toda conta tem apelido* (`apelido NOT NULL`) desde a primeira linha do banco, eliminando o estado nulo em toda superfície pública futura. — **Reversibility:** costly — relaxar depois exige tornar a coluna anulável e revisar toda superfície que hoje assume o invariante.
- **D-40:** Redefinição de senha **encerra todas as sessões BETUFU** daquela identidade, via gatilho `SECURITY`. Sem isso, quem invadiu manteria acesso renovável indefinidamente, bastando uma requisição válida a cada 8 horas — a troca de credencial no Keycloak não derruba, por si só, a sessão BFF já emitida. Esta decisão dá ao `SessionInvalidationListener` do 01-07 o **primeiro consumidor em produção**. O mecanismo de integração fica em aberto; *back-channel logout* do OIDC é o candidato padrão, a confirmar na pesquisa.
- **D-41:** O identificador de login é o **e-mail**. O apelido público permanece separado e **nunca** serve como identificador de acesso — se servisse, todo atacante já saberia metade da credencial de qualquer pessoa, porque `PROF-02` torna o apelido público. O vínculo estável continua sendo `(iss, sub)`, então trocar de e-mail no futuro não quebra identidade nem histórico.

### Perfil público e apelido

- **D-42:** O apelido **não é único**. A identidade real é o UUID público da conta. Consequência apresentada e **aceita**: homônimos legítimos aparecem no ranking e nos grupos, e passar-se por outra pessoa não exige esforço.
- **D-43:** As listas exibem **somente o apelido**; a distinção entre homônimos vive no perfil, onde aparece o identificador público. Consequência aceita: a ambiguidade permanece exatamente nas superfícies onde ela pesa — lista de ranking, grupos e, na Fase 7, o chat.
- **D-44:** O apelido aceita **ASCII estrito, 3 a 20 caracteres**: `A-Z`, `a-z`, dígitos e os separadores `_` e `-`. Sem espaço, acento, símbolo, caractere de controle ou largura zero. Dado que o apelido não é único e não há discriminador visível, esta é a **única barreira restante** contra o sósia por homoglifo — o `а` cirílico é visualmente idêntico ao `a` latino.
- **D-45:** *Equipe favorita* é modelada na Fase 2 como **referência opcional nula**, com a interface omitindo o seletor até haver equipes. `PROF-01` fica entregue em duas etapas: apelido na Fase 2, equipe favorita habilitada na Fase 3 sem migration nova.

### Papéis operacionais

- **D-46:** A Fase 2 modela os **três** papéis: administrador geral, administrador de competição e moderador. A máquina de conceder, revogar, delimitar e auditar nasce completa; as capacidades ligam nas fases que as criam (competição na 3, moderação na 7). O revisor de matrícula **não** é modelado — foi para a v2. Papéis continuam autoritativos na BETUFU/PostgreSQL, nunca no Keycloak (`D-20`).
- **D-47:** O primeiro administrador geral nasce por **semente de configuração de ambiente** (variável `BETUFU_*`), aplicada na inicialização de forma **idempotente** e auditada. Não existe endpoint privilegiado de bootstrap para ser abusado, a identidade real nunca entra no Git (respeita `PRIV-03`) e cada ambiente semeia o seu. Rodar de novo não duplica nem reescreve.
- **D-48:** Duas guardas explícitas sobre papéis próprios: **(a)** concessão cujo alvo seja o próprio ator é bloqueada e auditada — fecha o desvio de `OPER-03`, em que o administrador geral se concederia administrador de competição para contornar a proibição de apostar; **(b)** revogar o próprio papel de administrador geral é recusado quando ele for o **último** — impede travamento irreversível da governança. Cada guarda deve virar um teste que falha se a guarda sumir.
- **D-49:** Concessão ou revogação de papel **encerra todas as sessões da identidade afetada**, via gatilho `ADMINISTRATIVE`. Cumpre `IDEN-05` ao pé da letra e é impossível de burlar: o Spring Security guarda as autoridades no `SecurityContext` dentro da sessão (Redis, 01-06), e mexer no papel no PostgreSQL não altera essa cópia. Esta decisão dá ao `SessionInvalidationListener` o **segundo consumidor**. Custo aceito: a pessoa é desconectada mesmo quando o papel alterado não se relacionava com a atividade em curso.

### MFA

- **D-50:** **MFA é opcional em todo o MVP.** O Keycloak continua oferecendo-o a qualquer conta, inclusive administrativa, e quem quiser ativa por conta própria — mas **nenhum comando da plataforma é condicionado a ele**, não há step-up e não há exigência de recência. `IDEN-05` e `ODDS-12` foram emendados, e a regra transversal de `PROJECT.md` deixou de exigir MFA para contas administrativas. `D-07` da Fase 1 permanece literalmente satisfeita: não há reconfirmação por operação sensível.
- **D-51:** Risco apresentado e **aceito explicitamente**: a governança da plataforma passa a depender apenas de senha. As guardas de duas pessoas distintas — não autoconcessão (`D-48`), segundo administrador no override de odds (`ODDS-12`), confirmação de resultado por administrador distinto — permanecem e são a mitigação real.
- **D-52:** O campo `mfaAuthenticated` **permanece ausente** do contrato de sessão. A ausência deliberada documentada em `SessionController.java:100` e travada por `SessionControllerIT.java:215` é mantida, e a pendência aberta no `STATE.md` fecha como decisão: não se publica um sinalizador que não governa autorização nenhuma, porque isso convida o frontend a construir interface sobre uma garantia inexistente.

### Discrição dos agentes posteriores

Nestes pontos o responsável não fixou regra; decida de forma conservadora e registre a suposição:

- **Frequência de troca do apelido** — livre, com intervalo mínimo, ou definitiva. Considere que o ranking `RANK` é vitalício e que, sem unicidade e sem discriminador visível, esta regra também é o que impede assumir o apelido de outra pessoa logo após uma discussão.
- **Padrão exato da matrícula UFU** — a expressão regular de validação da `D-29` depende do formato real, que o responsável confirmou ser alfanumérico mas não especificou. **Confirmar na pesquisa antes de fixar**, e preferir um padrão que não rejeite matrículas de pós-graduação ou de alunos antigos.
- **Algoritmo e tamanho da chave HMAC**, e o formato de armazenamento do digest.
- **Valores numéricos do limite de tentativas** da `D-30` e da janela de contagem.
- **Como a semente da `D-47` identifica a pessoa** — por e-mail ou pelo par `(iss, sub)` — e o que acontece se a identidade semeada ainda não tiver feito o primeiro login.
- **Mecanismo de integração do gatilho `SECURITY`** da `D-40`.
- **Códigos de erro estáveis e textos**, respeitando `D-14` a `D-17` da Fase 1 e o `ApiErrorCode` já existente.
- **Conteúdo do painel operacional** (`OPER-06`) — a fase tem `UI hint: yes`, então isto pertence ao `UI-SPEC.md`.

</decisions>

<canonical_refs>
## Referências canônicas

**Agentes posteriores devem ler estes documentos antes de pesquisar, planejar ou implementar.**

### Produto, escopo e requisitos

- `.planning/PROJECT.md` — produto, arquitetura aprovada, restrições de privacidade e integridade, e a exigência de aprovação explícita para decisões e operações Git. **Emendado em 2026-08-19 e 2026-08-20** por esta discussão.
- `.planning/ROADMAP.md` — seção "Phase 2", com objetivo, dependências, requisitos e critérios de sucesso já reduzidos ao escopo real.
- `.planning/REQUIREMENTS.md` — consultar `AUTH-02`, `AUTH-03`, `AUTH-04`, `PROF-01`, `PROF-02`, `VERI-06`, `VERI-07`, `VERI-09`, `VERI-11`, `OPER-01`, `OPER-02`, `OPER-03`, `OPER-04`, `OPER-06`, `IDEN-05`, `PRIV-01`, `AUDT-01`, `AUDT-02`. Os textos de `VERI-07`, `VERI-09`, `IDEN-05` e `ODDS-12` **foram emendados** nesta discussão.

### Decisões herdadas que continuam vinculantes

- `.planning/phases/01-funda-o-segura-da-plataforma/01-CONTEXT.md` — em especial `D-05` a `D-09` (sessão de 8 horas ociosas, sem prazo absoluto, logout só do dispositivo atual), `D-14` a `D-17` (erros e formulários), `D-18` (S3 desacoplado por `BETUFU_S3_*`) e `D-20` (só `TECHNICAL` no Keycloak; papéis de domínio na BETUFU/PostgreSQL).
- `.planning/STATE.md` — seção *Contexto acumulado*, com as decisões de segurança do 01-07, 01-22 e 01-26 que restringem a superfície HTTP de sessão e a fiação OIDC.

### Regras de colaboração

- `AGENTS.md` — participação obrigatória do responsável, aprovação separada para stage, commit e push, e modo pedagógico.
- `CLAUDE.md` — padrões de prova: mutação discriminante, evidência empírica de framework, e a prova agregada obrigatória nas **duas** suítes (`node scripts/maven.mjs -f backend/pom.xml verify` **e** `node --test scripts/*.test.mjs`).

Não existem ADRs ou especificações externas adicionais para esta fase.

</canonical_refs>

<code_context>
## Informações sobre o código existente

### Recursos reutilizáveis

- **`br.com.betufu.identity.internal.*`** — `LocalIdentity`, `LocalIdentityRepository`, `LocalIdentityService`, `OidcClaimsProjection` e a migration `V1__create_local_identity.sql` já implementam a identidade estável `(issuer, subject)` com `UNIQUE`, `COLLATE "C"` e criação idempotente por `ON CONFLICT DO NOTHING`. **A conta BETUFU e o perfil se penduram nesta tabela** — não recriar identidade.
- **`br.com.betufu.platform.session.*`** — contrato de sessão, política de 8 horas, logout do dispositivo atual e `SessionInvalidationListener`. O listener existe desde o 01-07 **sem consumidor em produção**; `D-40` e `D-49` são os dois primeiros consumidores.
- **`br.com.betufu.platform.api.ApiErrorCode` e `ProblemDetailsHandler`** — códigos estáveis e RFC 9457 já em produção. Erros novos desta fase entram neste enum, não em estruturas paralelas.
- **`br.com.betufu.platform.observability.LogSanitizer`** e a família `Sanitizing*` — já existem para impedir vazamento em log. `PRIV-01` proíbe matrícula e HMAC em log, URL e telemetria; **verificar se o sanitizador cobre os novos campos** em vez de assumir.
- **`br.com.betufu.verification` e `br.com.betufu.identity`** — módulos Spring Modulith já declarados por `package-info.java`, prontos para receber o código desta fase.
- **Frontend** — `frontend/src/app/core/session/session.store.ts`, o layout autenticado e o cliente TypeScript gerado em `frontend/src/app/api/generated/**` (árvore *machine-owned*, 20 paths, digest governado).

### Padrões já estabelecidos

- **Contrato OpenAPI junto do código, nunca depois.** `backend/openapi/betufu.yaml` cobre hoje 4 paths. O `STATE.md` registra que adiar o contrato custou 4 dos 7 arquivos novos do cliente regenerado no 01-26 — o adiamento tem custo medido.
- **Migrations Flyway com constraints explícitas** — `V1` e `V2` declaram `CHECK` de vazio, de tamanho em octetos e de ordem temporal. As tabelas desta fase devem seguir o mesmo rigor.
- **Validadores Node fail-closed** com regressões negativas em `scripts/*.test.mjs`; a suíte Node está em 481/481.
- **`core.autocrlf=true`** deixa mutação multilinha sobre fonte Java inerte em checkout Windows. Toda mutação multilinha nova deve usar o helper `replaceAcrossLines`, que casa `\r?\n`. **Esta discussão tropeçou exatamente nisso** ao emendar os documentos de planejamento.

### Pontos de integração

- **`infra/keycloak/realm-betufu.json` muda nesta fase.** Hoje `registrationAllowed: false` e `verifyEmail: false`; `AUTH-02` e `AUTH-03` exigem os dois em `true`. E `scripts/validate-keycloak-realm.mjs:91` e `:93` **pinam esses dois valores exatos** — realm e validador mudam no mesmo bloco, ou a suíte Node quebra. `resetPasswordAllowed: true` e o SMTP apontando para o Mailpit já estão prontos e atendem `AUTH-04`.
- **Novas migrations `V3+`** para perfil, matrícula (HMAC + `hmac_version`) e papéis.
- **`backend/openapi/betufu.yaml`** ganha os endpoints de perfil, matrícula e administração de papéis, com regeneração do cliente TypeScript e atualização do digest governado da árvore gerada.
- **`SecurityConfiguration` e `SessionSecurityConfiguration`** recebem as regras de autorização por papel; hoje só existe `ROLE_TECHNICAL` por allowlist fechada.

</code_context>

<specifics>
## Ideias específicas

- Descrever a entrega como **"registro da matrícula UFU"**, nunca como "verificação" — o MVP não verifica titularidade, e usar a palavra antiga induziria usuário e agentes a supor uma garantia que não existe.
- A recusa por matrícula já registrada deve ser **explícita e compreensível**, no tom aprovado em `D-14`: dizer o que aconteceu, indicar a próxima ação possível e oferecer um código copiável.
- O padrão "duas pessoas distintas" já usado no projeto (override de odds, confirmação de resultado) é a referência mental para as guardas de papéis da `D-48`.

</specifics>

<deferred>
## Ideias adiadas

- **Verificação documental do vínculo UFU** — envio de comprovante, validação de tipo real e ameaças, acesso temporário auditado do revisor, decisão com motivo e validade, estados de análise, exclusão do arquivo e bloqueio de autorrevisão. Movida para a v2 de `REQUIREMENTS.md` em 2026-08-19, como seção própria *"Verificação documental do vínculo UFU"* (`VERI-01` a `VERI-05`, `VERI-08`, `VERI-12`, `OPER-05`).
- **Obrigatoriedade de MFA** — reavaliação futura, conforme `D-50`. Enquanto isso, `PASS-01` (MFA e passkeys para usuários comuns) permanece na v2.
- **Step-up de MFA por operação sensível** — adiado desde o `01-CONTEXT.md` e agora sem consumidor, já que o MFA deixou de condicionar comandos.
- **Discriminador visível para homônimos** — recusado em `D-43`; se a ambiguidade no ranking ou no chat se mostrar um problema real de produto, é a primeira coisa a reconsiderar.
- **Prazo de validade do vínculo UFU** — `D-26`; volta a fazer sentido apenas se a verificação documental for reintroduzida.

</deferred>

---

*Fase: 2-Contas, Verificação e Operações Governadas*
*Contexto coletado em: 2026-08-20*
