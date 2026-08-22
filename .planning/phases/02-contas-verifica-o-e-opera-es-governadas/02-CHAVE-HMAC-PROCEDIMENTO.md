# Procedimento de Gestão, Rotação e Comprometimento da Chave HMAC

## 1. Visão Geral e Fundamentos Conceituais

### 1.1 O Que É e Por Que Usamos HMAC na BETUFU
Na BETUFU, o registro da matrícula universitária da UFU é autodeclarado e voluntário (requisitos `VERI-06`, `VERI-07`, `PRIV-01` e decisões `D-33` a `D-37`).
Para proteger a privacidade dos estudantes, **o número original da matrícula nunca é armazenado** no banco de dados, em arquivos ou em logs (`VERI-06`).

Em vez de guardar o número cru (isto é, a representação textual autodeclarada da matrícula), a aplicação processa o texto normalizado através de um **HMAC** (*Hash-based Message Authentication Code* — Código de Autenticação de Mensagem Baseado em Hash) com algoritmo **HMAC-SHA256**.
- **O que é um HMAC:** É uma função criptográfica que combina uma chave secreta com os dados de entrada (a matrícula normalizada) para gerar uma saída binária de tamanho fixo (o **digest** de 32 bytes / 256 bits).
- **Por que não um Hash simples (como SHA-256 puro)?** Um hash simples sem chave permitiria que qualquer pessoa de posse de uma cópia do banco de dados (dump SQL) realizasse um ataque de dicionário (*rainbow tables* ou força bruta), testando identificadores conhecidos até encontrar o mesmo hash. Com o HMAC, o cálculo depende de uma chave secreta mantida fora do banco de dados; enquanto a chave possuir alta entropia (mínimo de 256 bits aleatórios) e seu sigilo for rigorosamente preservado, um atacante de posse de um dump do banco não consegue recuperar o valor original nem pré-computar tabelas de enumeração offline.
- **Objetivo central:** Garantir a **unicidade de cadastro** (`VERI-07`: cada matrícula só pode estar vinculada a uma única conta na plataforma) sem expor nem conservar o número original da matrícula.

---

## 2. Estado do Snapshot Atual e Bloqueio Operacional

### 2.1 Suporte Exclusivo a V1 no Snapshot Atual
No estágio atual do repositório (Fase 2 / Plano 02-22):
- A aplicação possui configuração e carregamento estritos para a versão inicial `v1`, mapeada em `backend/src/main/resources/application.yaml`:
  - `betufu.verification.matricula-hmac.current-version: "${BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION}"`
  - `betufu.verification.matricula-hmac.keys.v1: "${BETUFU_MATRICULA_HMAC_KEY_V1}"`
- **Não há suporte em tempo de execução para `v2` no snapshot atual:** Definir uma variável de ambiente como `BETUFU_MATRICULA_HMAC_KEY_V2` neste momento **não** terá efeito na aplicação e não habilitará uma nova chave, pois o `application.yaml`, as configurações de infraestrutura e os scripts de validação (`scripts/validate-runtime-prerequisites.mjs`) validam exclusivamente `v1` e rejeitam propriedades não mapeadas.

### 2.2 Bloqueio Arquitetural: Plano 02-07 Não Executado
A tabela de vínculos no PostgreSQL (`public.ufu_enrollment`), a migration `V5`, a entidade JPA, o repositório e o serviço de verificação que consulta múltiplas versões ativas de chave pertencem ao **Plano 02-07** (Onda 5).

Qualquer execução prática de rotação em ambiente operacional permanece **estritamente bloqueada** até que:
1. O Plano 02-07 seja implementado, verificado e aprovado;
2. O arquivo `application.yaml` seja formalmente alterado para mapear a nova versão de chave sob o prefixo `keys`;
3. A infraestrutura e os validadores de ambiente sejam atualizados para a nova versão.

### 2.3 Bloqueio Operacional: Nenhum Gerenciador de Segredos Aprovado
Nenhum gerenciador de segredos externo (como HashiCorp Vault, AWS Secrets Manager ou equivalente) foi formalmente aprovado pelo responsável para o projeto até o momento. No MVP, a injeção do material de chave é realizada exclusivamente por variáveis de ambiente controladas no host de execução da aplicação.

---

## 3. Geração e Guarda do Material de Chave (D-33)

### 3.1 Especificação Criptográfica
- **Algoritmo:** HMAC com SHA-256 (`HmacSHA256`).
- **Comprimento Mínimo:** 32 bytes (256 bits) de entropia decodificada.
- **Codificação de Transporte:** Base64 padrão (RFC 4648), necessária para representação textual segura em variáveis de ambiente.

### 3.2 Geração Segura em Máquina Local
O material de chave deve ser gerado em ambiente seguro e isolado pelo responsável ou operador autorizado:
1. **Fonte de Alta Entropia:** Deve-se utilizar um gerador seguro de números aleatórios (CSPRNG — *Cryptographically Secure Pseudorandom Number Generator*), como `System.Security.Cryptography.RandomNumberGenerator` no PowerShell/.NET ou `SecureRandom` na JVM.
2. **Snippet de Geração Local em PowerShell:**
   Para desenvolvimento e testes em ambiente local, o comando abaixo aloca um array de 32 bytes (256 bits), preenche-o com bytes aleatórios seguros via `RandomNumberGenerator.Create()`, atribui a representação Base64 diretamente à variável de ambiente do processo atual, define a versão corrente, limpa o array da memória e descarta o gerador, sem nunca imprimir a chave no console nem gravá-la em disco:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:BETUFU_MATRICULA_HMAC_KEY_V1 = [Convert]::ToBase64String($bytes)
$env:BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION = "v1"
[Array]::Clear($bytes, 0, $bytes.Length)
$rng.Dispose()
```

3. **Proibições Rígidas de Operação:**
   - **Nunca passar o segredo como argumento de comando:** Argumentos de CLI ficam visíveis para outros usuários do sistema através da tabela de processos do sistema operacional e são gravados no histórico do shell.
   - **Nunca salvar a chave em arquivo desprotegido em disco:** Evita rastros não criptografados no sistema de arquivos.
   - **Nunca exibir o segredo em logs ou consoles compartilhados:** O snippet acima não utiliza `Write-Host` nem produz saída no terminal.
4. **Bloqueio de Produção:**
   - A injeção via variáveis no processo local destina-se unicamente ao desenvolvimento e testes locais.
   - O ambiente de produção permanece estritamente bloqueado até que um mecanismo formal de injeção e guarda de segredos seja aprovado pelo responsável.

### 3.3 Regras Rígidas de Guarda
1. **Nunca comitar no Git:** Nenhum arquivo rastreado pelo controle de versão pode conter chaves reais ou dados sensíveis.
2. **Nunca gravar a chave no PostgreSQL:** A chave de HMAC vive exclusivamente na memória volátil da aplicação Java e no ambiente do container. Uma cópia não autorizada do banco de dados (dump SQL) isolada não expõe a chave secreta.
3. **Injeção via Variáveis de Ambiente:** A chave é injetada via variáveis prefixadas com `BETUFU_`:
   - `BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION`: identificador textual da versão corrente (ex.: `v1`).
   - `BETUFU_MATRICULA_HMAC_KEY_V1`: valor da chave em Base64 para a versão `v1`.
4. **Comportamento Fail-Closed no Spring Boot:**
   - A classe `MatriculaHmacConfiguration` valida todas as propriedades de chave no momento da inicialização da aplicação (fase de *bootstrap*).
   - Se a variável da versão corrente estiver ausente, vazia, contiver Base64 malformado ou fornecer menos de 32 bytes decodificados, a aplicação encerra imediatamente o processo com exceção (`IllegalStateException`).
   - O sistema nunca utiliza chaves padrão (*fallback*) nem inicializa com configurações inseguras.

---

## 4. Rotação Planejada e Arquitetura Multiversão (D-34, D-35)

### 4.1 O Que É a Rotação Multiversão
Diferente de chaves de assinatura de token de sessão (onde a invalidação imediata da chave antiga apenas desloga usuários), a rotação do HMAC de matrícula precisa lidar com a persistência histórica de unicidade (`VERI-07`).

Quando uma nova versão de chave é promovida (exemplo: migração da versão `v1` para `v2`):
1. **Promoção da versão corrente:** A configuração `BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION` passa a apontar para a nova versão (`v2`).
2. **Novas escritas:** Todo novo cadastro de matrícula calcula o digest utilizando unicamente a chave corrente (`v2`) e persiste no banco de dados o digest binário e a versão numérica correspondente na coluna `hmac_version` (conforme modelado na migration `V5` do Plano 02-07, ex.: valor `2`).
3. **Versões antigas continuam ativas para leitura e comparação:** A chave da versão anterior (`v1`) permanece configurada no ambiente e carregada pelo Spring (`BETUFU_MATRICULA_HMAC_KEY_V1`).

### 4.2 Por Que as Versões Antigas Devem Permanecer Ativas
Se a chave antiga (`v1`) fosse descartada ou desativada no momento da rotação, ocorreria uma **falha silenciosa catastrófica de unicidade**:
- Uma dada matrícula já cadastrada no sistema sob a chave `v1` produziu o digest `D1`.
- Um segundo usuário tenta cadastrar exatamente a mesma matrícula após a rotação.
- Se o sistema calculasse o digest apenas com a nova chave `v2`, produziria um digest diferente `D2`.
- O banco de dados procuraria por `D2`, não encontraria nenhum registro conflitante, e aceitaria o cadastro.
- **Resultado:** Duas contas distintas teriam a mesma matrícula estudantil vinculada, violando silenciosamente a regra fundamental de negócio `VERI-07`.

**Como a verificação multi-versão resolve isso (Plano 02-07):**
No fluxo de registro, o serviço de verificação (`EnrollmentService`) calcula os digests candidatos sob **todas as versões de chave ativas** carregadas na aplicação (gerando `D_v1` e `D_v2`) e executa uma consulta por conjunto de digests no banco de dados. Se qualquer versão colidir com um registro ativo no banco, o cadastro é recusado com recusa explícita de duplicidade (`D-30`).

### 4.3 Mapeamento entre Versão Textual e Tipo Numérico no Banco (D-34)
- **Na aplicação Java e configuração Spring:** As versões são identificadas por strings no mapa de propriedades (ex.: `"v1"`, `"v2"`).
- **No PostgreSQL (Plano 02-07):** A coluna `hmac_version` na tabela `ufu_enrollment` é definida como tipo numérico (ex.: `smallint NOT NULL CHECK (hmac_version >= 1)`), indexável e com constraints explícitas. A camada de persistência faz o mapeamento determinístico entre a identificação textual da chave e o número da versão gravada na linha de vínculo.

---

## 5. Protocolo Operacional de Rotação em Produção

### 5.1 Checklist de Pré-Rotação
Antes de iniciar qualquer procedimento de rotação:
- [ ] O Plano 02-07 foi implementado, testado e está em execução em produção.
- [ ] O novo material de chave foi gerado com pelo menos 32 bytes de entropia decodificada em Base64.
- [ ] A alteração no `application.yaml` para adicionar a nova versão no mapa de chaves foi aprovada.
- [ ] Os scripts de validação de ambiente (`validate-runtime-prerequisites.mjs`) foram atualizados.
- [ ] A nova variável de ambiente (ex.: `BETUFU_MATRICULA_HMAC_KEY_V2`) foi configurada no ambiente do servidor.

### 5.2 Implantação em Duas Etapas (Two-Step Deployment)
Para garantir disponibilidade sem interrupção de serviço e validação contínua de integridade, a rotação divide-se em duas etapas:

1. **Etapa 1: Adição da Nova Chave como Passiva (Apenas Comparação):**
   - Atualizar a configuração para registrar a nova chave no mapa de chaves (`keys`), mantendo a versão corrente inalterada:
     `BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1`
   - Realizar o deploy da aplicação.
   - O Spring Boot inicializa, valida ambas as chaves (`v1` e `v2`) no bootstrap fail-closed e as carrega na memória.
   - Novas escritas continuam utilizando `v1`, mas a verificação multi-versão já está apta a checar candidatos sob ambas as chaves.
2. **Etapa 2: Promoção da Nova Versão a Corrente (Escrita Ativa):**
   - Atualizar a variável de ambiente `BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION` para `v2`.
   - Reiniciar a aplicação.
   - A partir deste momento, novas matrículas passam a ser gravadas com o digest de `v2` e versão numérica `2`. As buscas de unicidade continuam validando os registros legados sob `v1` e os novos sob `v2`.

### 5.3 Verificação de Provas sem Exposição de Segredos
A confirmação do sucesso da implantação deve ser realizada de forma totalmente indireta:
- **Healthcheck:** Verificar o endpoint `/actuator/health` e confirmar resposta HTTP 200 (`"status":"UP"`).
- **Logs de Inicialização:** Inspecionar os logs para assegurar que a aplicação subiu sem erros de configuração e sem lançar `IllegalStateException`.
- **Proibição Absoluta de Inspeção de Segredos:** É terminantemente proibido imprimir variáveis de ambiente em logs, executar comandos de terminal que despejem segredos em console ou incluir material de chave em relatórios ou tickets de mudança.

### 5.4 Critérios de Aborto e Rollback
- **Critérios de Aborto:**
  - Falha no bootstrap da JVM (erro de decodificação Base64 ou chave com menos de 32 bytes).
  - Versão corrente configurada ausente no mapa de chaves.
  - Endpoint de healthcheck indisponível ou respondendo com erro.
- **Rollback na Etapa 1:**
  - Reverter a configuração da aplicação para carregar exclusivamente a versão anterior.
  - Reiniciar e confirmar restauração do healthcheck HTTP 200.
  - Como nenhuma escrita foi feita sob a nova versão, há ausência de risco de inconsistência de digests sob a nova versão, preservando-se apenas os riscos operacionais normais de reinicialização e chaveamento de serviço.
- **Rollback na Etapa 2:**
  - Se nenhum registro novo tiver sido persistido com a nova versão: reverter `BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION` para `v1`.
  - Se novos registros já tiverem sido gravados sob a nova versão: a nova chave **não pode** ser removida da configuração passiva; o rollback deve apenas retornar a versão corrente para `v1` enquanto se investiga o ocorrido, preservando ambas as chaves ativas para busca multi-versão.

---

## 6. O Custo Aceito da Rotação (D-36)

Conforme registrado na decisão arquitetural `D-36`:

> **A rotação planejada de chaves reduz a taxa de uso futuro da chave antiga, mas NÃO reduz a sua superfície de exposição histórica.**

Enquanto existirem registros na tabela de vínculos associados à versão anterior (`v1`), a chave secreta `v1` continuará existindo no ambiente de produção e sendo carregada na memória da aplicação para conferência de duplicidade. Ela continua sendo um material criptográfico de alta sensibilidade e deve ser protegida com o mesmo rigor dispensado à chave corrente.

---

## 7. Irreversibilidade do Descarte de Chaves Antigas

O descarte de uma chave antiga é uma operação criptograficamente **irreversível**:
- Ao remover a chave `v1` da configuração, a aplicação perde definitivamente a capacidade de computar o HMAC correspondente para matrículas candidatas no padrão de `v1`.
- Consequentemente, o sistema perde para sempre a capacidade de detectar duplicidade contra cadastros históricos efetuados sob a chave `v1`.

Por este motivo:
- O descarte de qualquer chave antiga **nunca** é executado por rotinas automáticas de limpeza, tarefas agendadas (*cronjobs*) ou expiração temporal no banco de dados.
- Descartar uma chave antiga é uma decisão de governança exclusiva e manual do **responsável pelo projeto**, condicionada ao expurgo prévio ou migração de todos os vínculos daquela versão.

---

## 8. Procedimento de Resposta a Comprometimento da Chave (D-37)

Caso ocorra um vazamento, exposição não autorizada ou quebra de confidencialidade de uma chave HMAC, o evento é tratado como **incidente de privacidade de dados**, pois um atacante de posse da chave e de uma cópia do banco poderia tentar ataques de dicionário offline contra os digests dos estudantes.

### 8.1 Fluxo de Resposta ao Incidente
1. **Ativação Emergencial de Nova Versão:**
   - Gerar uma nova chave com alta entropia e configurá-la como versão corrente (`BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION`).
   - Bloquear imediatamente o uso da chave comprometida para qualquer nova gravação.
2. **Abertura de Janela de Reinformação:**
   - Notificar os usuários cujos vínculos foram gravados sob a versão de chave comprometida.
   - Estabelecer uma janela de tempo para que esses usuários acessem a plataforma e redigitem voluntariamente sua matrícula estudantil, permitindo o recálculo do digest sob a nova chave.
3. **Identificação e Isolamento:**
   - Mapear na tabela de vínculos todos os registros que permanecerem sob a versão de chave comprometida após o encerramento da janela.
4. **Expurgo dos Vínculos Comprometidos:**
   - Executar a exclusão controlada dos registros remanescentes sob a chave comprometida na tabela `ufu_enrollment`, eliminando os digests vulneráveis a ataques offline.
5. **Reconciliação e Auditoria:**
   - Reconciliar os totais de vínculos migrados, vínculos expurgados e contas pendentes, registrando o encerramento do incidente na governança do projeto.

### 8.2 Regras de Governança da Reinformação
- **Matrícula Imutável pelo Usuário (`D-32`):** A abertura da janela de reinformação **não permite que o usuário altere arbitrariamente seu número de matrícula**. A matrícula é imutável perante a conta do usuário; a reinformação consiste estritamente no reenvio do mesmo valor para cômputo do digest com a chave nova.
- **Fluxo Governado:** A reinformação exige uma interface administrativa e um fluxo governado de reconciliação que **não existem no repositório nesta fase**.

### 8.3 Status da Ferramenta de Expurgo
Conforme a decisão `D-37`, **a ferramenta de software para automação do expurgo não existe no repositório nesta fase**.
Construí-la preventivamente violaria o escopo do MVP. Caso venha a ser necessária no futuro, a ferramenta deverá ser construída sob especificação própria, contemplando:
- Execução administrativa autenticada e com segregação de papéis;
- Operação em transação atômica com controle de concorrência;
- Reconciliação com integridade referencial de contas;
- Emissão de evento de auditoria sem registro de dados sensíveis.

---

## 9. Relação do Expurgo com a Trilha de Auditoria

O expurgo de vínculos da versão de chave comprometida afeta exclusivamente a tabela de negócios (`ufu_enrollment`) e **não altera nem remove registros da trilha imutável `public.audit_event`**.

Conforme estabelecido na seção *Comprometimento da chave de matrícula (D-37)* do documento `.planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-AUDITORIA-PROCEDIMENTO.md`:
- A tabela de auditoria não armazena números de matrícula nem digests HMAC.
- As ações auditadas guardam apenas referências à linha de vínculo (`ufu_enrollment.id`) e à versão da chave (`hmac_version`), preservando a integridade histórica da trilha sem conservar dados sensíveis.

---

## 10. Padrão de Validação da Matrícula e Regra One-Way (D-28, D-29)

### 10.1 Padrão Canônico Aprovado
O padrão formal de validação da matrícula UFU foi definido no Plano 02-06 e aprovado pelo responsável em **2026-08-21**:
- **Expressão Regular:** `^[A-Z0-9]{5,20}$` (entre 5 e 20 caracteres alfanuméricos ASCII maiúsculos).

### 10.2 Ordem Canônica de Normalização (D-29)
Antes da geração do HMAC, a entrada do usuário passa rigorosamente pelos seguintes passos implementados em `MatriculaNormalizer`:
1. **Recorte de extremidades:** Remoção de espaços em branco no início e no fim da string (`trim()`).
2. **Conversão de caixa:** Conversão de todas as letras para maiúsculas utilizando localidade neutra (`toUpperCase(Locale.ROOT)`), impedindo variações de internacionalização (ex.: comportamento com caractere `i` no idioma turco).
3. **Remoção de separadores:** Remoção estritamente de hífen `-`, ponto `.`, barra `/` e espaços em branco conforme o padrão `Pattern.compile("[\\s\\-./]+")` (a normalização remove apenas esses separadores específicos, nunca quaisquer outros sinais de pontuação; caracteres fora do padrão aprovado provocam rejeição na etapa seguinte de validação).
4. **Validação do formato:** Verificação contra o padrão aprovado `^[A-Z0-9]{5,20}$`. Entradas fora do padrão são rejeitadas com erro de validação antes de qualquer processamento criptográfico.
5. **Cálculo do HMAC:** Somente entradas aprovadas na validação são encaminhadas ao `MatriculaHmac`.

### 10.3 Irreversibilidade da Normalização e do Padrão (Regra One-Way)
Como o requisito `VERI-06` proíbe o armazenamento da matrícula original:

> **Qualquer alteração futura na lógica de normalização ou na expressão regular de validação invalida irreversivelmente toda a base de matrículas cadastradas.**

Se a regra for alterada, uma matrícula já registrada gerará um texto normalizado diferente e, consequentemente, um digest diferente. Como a plataforma não guarda o número original para recalcular os registros existentes, torna-se matematicamente impossível reprocessar a base legada. Portanto, a regra de normalização é uma decisão permanente de via única (*one-way*).
