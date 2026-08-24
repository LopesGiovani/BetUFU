# Procedimento Operacional do Realm Keycloak Local

Este documento descreve o funcionamento, a persistência e o procedimento operacional de manutenção do realm do Keycloak no ambiente de desenvolvimento local do Betufu.

---

## 1. Como o Keycloak Local Funciona e Persiste Dados

No arquivo `infra/compose.yaml`, o serviço do Keycloak (versão 26.7.1) é iniciado com o argumento `start-dev --import-realm` e configurado com `KC_DB=postgres`.

### O Mecanismo de Importação vs. Banco de Dados

- **Primeira Inicialização (Banco Ausente/Vazio):** Quando o container sobe e o banco de dados PostgreSQL ainda não possui o realm cadastrado, o parâmetro `--import-realm` lê o arquivo JSON (`infra/keycloak/realm-betufu.json`) e cria o realm `betufu-local` diretamente no PostgreSQL.
- **Inicializações Seguintes (Banco Já Populado):** Como `KC_DB=postgres` mantém o estado persistido no volume Docker `betufu-local-postgres-data`, o Keycloak **não sobrescreve** o realm persistido com o conteúdo do arquivo JSON.
- **Conceito Fundamental:** Editar o arquivo `infra/keycloak/realm-betufu.json` **não altera** o realm que já foi gravado no banco de dados. Para uma atualização pontual dos listeners de eventos, use a reconciliação não destrutiva da Seção 12. Recriar o volume do PostgreSQL só é necessário quando o objetivo explícito for um rebootstrap completo do realm; não é o procedimento normal de rollout do listener.

---

## 2. Pré-condições e Alerta Destrutivo

> [!CAUTION]
> **AVISO DE IMPACTO DESTRUTIVO TOTAL**
> O reset do ambiente local apaga **todos os dados** do ambiente de desenvolvimento. Bancos de dados, contas de usuários, mídias enviadas, sessões ativas e mensagens de teste serão permanentemente perdidos.

### Pré-condições

- Confirmar que nenhum dado local relevante precisa ser mantido antes de prosseguir.
- Estar na raiz do **checkout que criou os containers Compose**, com o Node.js e Docker em execução.
- Confirmar que esse mesmo checkout já contém as mudanças revisadas em `infra/keycloak/realm-betufu.json`. Não aplique o reset enquanto a alteração existir somente num worktree isolado.

---

## 3. Passo Seguro: Simulação (Dry-Run)

Antes de qualquer ação destrutiva, execute a simulação do script para verificar quais recursos seriam afetados sem realizar qualquer deleção:

```bash
node scripts/reset-local.mjs
```

### O que conferir

- Verifique a lista de containers e volumes exibida no terminal.
- Certifique-se de que apenas os recursos locais da BETUFU estão sendo listados para remoção.
- Interrompa se algum container `infra-*` aparecer como preservado com a justificativa `config_files aponta para outro checkout`. Isso significa que o comando foi executado a partir da cópia errada do repositório.
- Confirme que o script executa sem erros de parâmetros ou sintaxe.

### Medição de 2026-08-20

- A partir do worktree `02-02`, o dry-run selecionou os quatro volumes, mas recusou os sete containers porque eles pertenciam ao checkout principal. Essa combinação é **inválida para `--apply`**.
- A partir do checkout proprietário, o dry-run selecionou os sete containers BETUFU e os quatro volumes allowlisted, preservando o volume de outro projeto. Essa é a seleção coerente esperada para o ambiente medido.
- Nas duas execuções, a saída terminou com `DRY-RUN: nada foi removido`.

---

## 4. Checkpoint de Segurança

> [!IMPORTANT]
> **CHECKPOINT OBRIGATÓRIO: PARAR E OBTER AUTORIZAÇÃO**
> **NÃO** execute comandos com a flag `--apply` de forma automática.
> É obrigatório parar e obter autorização destrutiva separada e explícita antes de qualquer execução com `--apply`. A autorização não corrige um checkout errado: o dry-run também precisa passar pelo gate de propriedade descrito acima.

---

## 5. Procedimento de Reset e Bootstrap (Documentação)

> [!NOTE]
> **Atenção:** Este comando está documentado exclusivamente para referência operacional. **Não execute este procedimento automaticamente.**

Depois que as mudanças estiverem no checkout proprietário, o dry-run estiver coerente e houver autorização explícita, o comando para reset e recriação do ambiente é:

```bash
node scripts/reset-local.mjs --apply --bootstrap
```

- **Confirmação Literal Requerida:** O script solicitará a digitação exata da confirmação:

  ```text
  APAGAR DADOS LOCAIS BETUFU
  ```

### Segurança por Allowlist

O script remove os recursos **item a item por allowlist**, utilizando apenas os nomes exatos dos volumes e containers do projeto. Ele **nunca** executa comandos genéricos ou perigosos como `docker system prune` ou `docker compose down -v`, preservando outros projetos e dados da máquina host.

---

## 6. Impactos Exatos nos Volumes Locais

A execução do reset remove pontualmente os seguintes volumes:

- **`betufu-local-postgres-data`**: Bancos de dados locais do backend e do Keycloak, apagando o realm persistido, tabelas e todas as contas de usuários.
- **`betufu-local-redis-data`**: Sessões de autenticação e dados de cache em memória.
- **`betufu-local-seaweed-data`**: Objetos, anexos e mídias armazenadas no SeaweedFS local.
- **`betufu-local-mailpit-data`**: Mensagens de e-mail e links/tokens de verificação capturados no Mailpit.

---

## 7. Verificação do Realm Pós-Bootstrap

Após o término do bootstrap, acesse o Keycloak em execução e confira se o realm ativo possui:

1. `registrationAllowed = true` (auto-cadastro liberado)
2. `verifyEmail = true` (verificação de e-mail ativada)
3. Usuário técnico com `emailVerified = true`

---

## 8. Validação de Tema e Fluxo de Cadastro

Com o ambiente restaurado, execute o smoke HTTP do tema:

```bash
node scripts/validate-keycloak-theme.mjs
```

Esse comando confirma que o realm local expõe o link e a página de cadastro, aplica o tema BETUFU e não pede senha no formulário inicial. Ele não percorre sozinho o e-mail de confirmação nem a criação posterior da senha.

Para confirmar o fluxo no estado persistido que acabou de ser importado, faça um cadastro local e observe a sequência completa:

1. Forneça o e-mail no formulário inicial, sem campo de senha.
2. Abra a mensagem recebida no Mailpit e confirme o endereço.
3. Crie a senha somente na página aberta depois da confirmação.

A regressão automatizada dessa sequência pertence ao teste de integração Java `SelfRegistrationKeycloakIT`, que inicia Keycloak e Mailpit isolados. Isso separa duas provas: o smoke verifica **o ambiente local em execução**; o teste Java verifica **o comportamento completo do arquivo de realm**.

---

## 9. Contexto OQ-10 (Post-Logout Redirect URIs)

- **Situação no Arquivo:** O arquivo `infra/keycloak/realm-betufu.json` já contém a diretiva `post.logout.redirect.uris=http://localhost:4200/` configurada para o client `betufu-bff`.
- **Descompasso de Documentação:** O arquivo `.planning/STATE.md` encontra-se desatualizado quanto ao conteúdo do JSON.
- **Importante:** Não se deve afirmar que o comportamento em runtime/logout já foi confirmado.
- **Ação Planejada:** A confirmação em runtime do fluxo de logout e a consequente atualização do `.planning/STATE.md` devem ser realizadas na etapa de fechamento da fase.

---

## 10. Diagnóstico: Cadastro Ausente ou Inconsistente

Se o cadastro de usuários não estiver disponível após o bootstrap:

1. Verifique se o volume `betufu-local-postgres-data` foi de fato recriado do zero.
2. Inspecione os logs do container do Keycloak para confirmar que o realm `betufu-local` foi importado com sucesso na inicialização.
3. **Atenção:** **Nunca** sugira ou utilize comandos `prune` do Docker para resolver problemas de importação.

---

## 11. Checklist Operacional

- [ ] Executar simulação segura: `node scripts/reset-local.mjs`
- [ ] Conferir os recursos identificados na saída do dry-run
- [ ] Confirmar que nenhum container `infra-*` foi recusado por pertencer a outro checkout
- [ ] Confirmar que o checkout proprietário já contém o realm revisado
- [ ] Parar no checkpoint e solicitar autorização destrutiva
- [ ] Executar (quando autorizado): `node scripts/reset-local.mjs --apply --bootstrap`
- [ ] Inserir confirmação literal: `APAGAR DADOS LOCAIS BETUFU`
- [ ] Validar no Keycloak: `registrationAllowed=true`, `verifyEmail=true` e usuário técnico `emailVerified=true`
- [ ] Executar o smoke HTTP do tema: `node scripts/validate-keycloak-theme.mjs`
- [ ] Fazer um cadastro local e confirmar no Mailpit a ordem e-mail → confirmação → criação de senha
- [ ] Confirmar que a regressão Java `SelfRegistrationKeycloakIT` permanece verde
- [ ] Programar teste de runtime de logout e atualização do `.planning/STATE.md` para o fechamento da fase

---

## 12. Reconciliação Não Destrutiva dos Event Listeners

O realm persistido precisa receber o listener mesmo quando `--import-realm` é ignorado porque o banco já contém `betufu-local`. O script `scripts/reconcile-keycloak-event-listeners.mjs` usa a Admin API do Keycloak e segue o ciclo seguro **GET completo → união estável → PUT completo → GET de confirmação**. Ele preserva todos os campos e listeners existentes, acrescenta somente `jboss-logging` e `betufu-password-event-listener`, e não altera `eventsEnabled`.

### 12.1 Credenciais e dry-run

Forneça `KC_BOOTSTRAP_ADMIN_USERNAME` e `KC_BOOTSTRAP_ADMIN_PASSWORD` exclusivamente pelo ambiente protegido do processo (secret manager, variável protegida do job ou terminal local controlado). Não coloque usuário, senha ou token na linha de comando, em arquivo versionado ou em log.

Depois que o healthcheck do novo Keycloak estiver `healthy`, execute primeiro o dry-run:

```bash
node scripts/reconcile-keycloak-event-listeners.mjs --base-url http://127.0.0.1:8081 --realm betufu-local
```

Saída esperada quando falta algum listener:

```text
[reconcile-keycloak-event-listeners] DRY_RUN realm=betufu-local action=ADD_REQUIRED_LISTENERS
```

O dry-run não executa `PUT`. Falha HTTP, timeout ou JSON inválido encerra o comando com código diferente de zero e mantém o rollout bloqueado.

### 12.2 Aplicação e prova de idempotência

Ainda com o Keycloak saudável e **antes de liberar qualquer tráfego**, aplique a reconciliação:

```bash
node scripts/reconcile-keycloak-event-listeners.mjs --apply --base-url http://127.0.0.1:8081 --realm betufu-local
```

Na primeira mudança, a saída esperada é:

```text
[reconcile-keycloak-event-listeners] APPLIED realm=betufu-local
```

Repita imediatamente o mesmo comando. A segunda execução não pode emitir outro `PUT` e deve responder:

```text
[reconcile-keycloak-event-listeners] UNCHANGED realm=betufu-local
```

Somente depois desse `UNCHANGED` o tráfego pode ser liberado. Se a leitura pós-escrita divergir, o script tenta restaurar o documento anterior e termina em erro; o operador deve manter o tráfego bloqueado, guardar a saída não sensível e escalar o incidente.

### 12.3 Checklist pós-health do rollout D-40

- [ ] Confirmar o serviço Keycloak como `healthy`.
- [ ] Injetar `KC_BOOTSTRAP_ADMIN_USERNAME` e `KC_BOOTSTRAP_ADMIN_PASSWORD` apenas no ambiente protegido.
- [ ] Executar o dry-run e revisar `DRY_RUN` ou `UNCHANGED`.
- [ ] Executar `--apply` e exigir `APPLIED` ou `UNCHANGED`.
- [ ] Repetir `--apply` e exigir exatamente `UNCHANGED`.
- [ ] Confirmar pela Admin API que listeners anteriores continuam presentes e `eventsEnabled` não mudou.
- [ ] Remover as credenciais administrativas do ambiente do processo.
- [ ] Só então liberar tráfego para o Keycloak atualizado.
