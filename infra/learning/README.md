# BETUFU: primeiro ambiente privado de aprendizado

Esta configuração prepara a BETUFU para uma única EC2, com acesso pelo túnel
SSH. Não autoriza lançamento público. O Compose de desenvolvimento, suas
credenciais e seus volumes permanecem separados.

**Estado:** primeiro deploy privado em execução. O responsável apresentou os
sete serviços `healthy` e confirmou acesso pelo túnel, cadastro e login. Upload,
recuperação de senha, invalidação de sessões e E2E completos ficaram para um
bloco posterior, por sua decisão. Isso não é certificação E2E nem liberação pública.

## Registro da implantação

- Instância: `i-0097430f1b99081fd`, região `sa-east-1`.
- Último IP público confirmado: `54.207.66.110`; conferir novamente no console
  após parar/iniciar a EC2.
- Diretório no servidor: `/home/ubuntu/betufu-learning/infra/learning`.
- Configuração implantada: commit `42bdb3f36ae0132960adca4bd6d136c324eb5d8e`.
- Pacote: `BetUFU-learning-42bdb3f.tar`, 368.640 bytes, 16 arquivos.
- SHA-256 conferido na origem e na EC2:
  `433f8d743a34413d41f37863ede59cd1df9192f7b75582d44430d304f0d6d77a`.
- Função IAM vinculada: `betufu-learning-ecr-pull`, com autenticação ECR em
  São Paulo e download restrito aos três repositórios da aplicação. A identidade
  dessa função foi confirmada pelo responsável dentro da EC2.
- AWS CLI instalada na EC2 pelo Snap: `2.35.21`, com atualizações automáticas.
- Rede Compose conferida: `172.18.0.0/16`, compatível com os proxies confiáveis.
- Credenciais novas geradas na EC2; `.env` pertence a `ubuntu`, modo `600`.
  Este registro não contém seus valores.

Imagens da aplicação confirmadas no `compose ps -a` fornecido pelo responsável:

| Componente | Digest do índice no ECR |
| --- | --- |
| backend | `sha256:8a7e896cb31f98efa34bf10bd58ebd1bf555bad93e174d96f5564d9a9b6b00a6` |
| frontend | `sha256:062deb50a76e2ca7c645d29feb2997e5c0abc18123cc03f0a002c3118bb66197` |
| keycloak | `sha256:4ae99a0b041905dbce17c7866bfcfbb25319b70e5801580af8c00701d88d3dc2` |

Os quatro serviços auxiliares também usam os digests registrados no Compose
desse commit. As verificações de saúde passaram para os sete serviços; as portas
publicadas observadas foram somente `127.0.0.1:4200`, `:8081` e `:8025`.

Certificados do backend e SeaweedFS válidos até **31/01/2027 UTC**; CA até
**02/11/2027 UTC**. Planejar renovação antes dessas datas em um bloco próprio.
Ambos os truststores importaram a CA com a impressão digital SHA-256
`CB:E2:72:54:30:4E:16:83:F1:F9:93:40:DA:6F:00:05:84:4C:40:10:F4:02:2C:E7:AF:92:C8:16:9E:9D:A3:C2`.

As seções de provisionamento abaixo são referência para uma instalação nova,
não passos para repetir neste ambiente. Para o ambiente existente, use
[parar](#encerrar-sem-apagar-dados) e [retomar](#retomar-o-ambiente-existente).

## O que cada serviço faz

| Serviço | Função | Porta no host EC2 |
| --- | --- | --- |
| frontend | Nginx serve o Angular e encaminha chamadas ao Spring | `127.0.0.1:4200` |
| backend | API Spring, sessões, autorização e regras da aplicação | Nenhuma |
| keycloak | Cadastro, login, verificação de e-mail e recuperação de senha | `127.0.0.1:8081` |
| postgres | Dados da aplicação e do Keycloak em bancos separados | Nenhuma |
| redis | Sessões e estruturas temporárias | Nenhuma |
| seaweedfs | Arquivos privados pela interface compatível com S3 | Nenhuma |
| mailpit | Caixa de e-mail de teste, sem entrega externa configurada | `127.0.0.1:8025` |

`127.0.0.1` limita o acesso à própria EC2. O túnel SSH leva essas portas ao seu
computador; o grupo de segurança continua permitindo somente SSH do seu IP.
Não publique portas adicionais no firewall.

As três imagens da aplicação apontam para digests fixos do ECR. PostgreSQL,
Redis, SeaweedFS e Mailpit reutilizam as versões fixadas no Compose existente.
Os limites de memória somam 6.272 MiB, deixando espaço dos 8 GB para o Ubuntu e
Docker; são limites, não uma prova de capacidade. Logs têm rotação de 10 MB,
três arquivos por serviço. Uso de memória e disco precisa ser observado após
a inicialização.

## Validação local aprovada

Na raiz do repositório, este comando verifica a estrutura do Compose usando
somente valores fictícios; não inicia serviços nem baixa imagens:

```powershell
docker compose --env-file infra/learning/.env.example --file infra/learning/compose.yaml config --quiet
```

Saída vazia com código zero significa configuração estruturalmente válida.
Isso não valida certificados, Nginx em execução, credenciais ou jornadas da
aplicação. Nunca use `.env.example` para iniciar os serviços.

## Referência de provisionamento: acesso e pacote

1. Confirmar no console o estado e o IP público atual da instância
   `i-0097430f1b99081fd`, em `sa-east-1`. O IP pode mudar após parar/iniciar.
2. Verificar se a EC2 tem AWS CLI. Instalação, se necessária, exige seu bloco
   aprovado. Preparar uma função IAM da EC2 com acesso somente de leitura às
   imagens dos três repositórios ECR. Não copiar credenciais root para a EC2.
   O perfil local `betufu-deploy` foi identificado como root; a autorização
   anterior para ECR não libera alterações gerais de infraestrutura.
3. Após revisão e aprovações separadas de stage e commit, empacotar somente os
   caminhos abaixo a partir do commit aprovado, usando
   `git -c core.autocrlf=false archive`:

   - `infra/learning/compose.yaml`, `nginx.conf`, `.env.example` e `README.md`;
   - `infra/postgres/init-databases.sh`;
   - `infra/keycloak/realm-betufu.json`;
   - `infra/keycloak/themes/betufu/`.

   Preservar a hierarquia `infra/`, pois os mounts usam caminhos relativos.
   A opção temporária `core.autocrlf=false` evita a conversão para CRLF do
   Windows sem alterar a configuração do Git. Conferir o conteúdo do pacote
   contra os blobs do commit, incluindo LF no script do PostgreSQL.
   Não enviar o repositório inteiro, `.env`, volumes, bancos, arquivos de
   usuários, JARs ou caches locais. As imagens vêm do ECR.
4. Transferir o pacote por SCP e conferir seu SHA-256 na origem e na EC2.
   Autenticar o Docker no ECR com as credenciais temporárias da função IAM e
   baixar as imagens fixadas. Esses comandos serão apresentados em seu bloco.

## Referência de provisionamento: credenciais e TLS na EC2

TLS é a conexão criptografada usada aqui entre Nginx e backend, Keycloak e
backend, e backend e armazenamento. É necessário também porque a aplicação
exige HTTPS para armazenamento e para o callback de recuperação de senha.
O navegador acessa `localhost` através do SSH; não precisa instalar essa CA.

Preparar somente na EC2, sem imprimir segredos no terminal ou na conversa:

- Recusar sobrescrita se `.env` ou `secrets/` já existirem. Usar `umask 077`,
  `.env` com permissão `600` e diretório `secrets/` com `700`.
- Gerar cada segredo de forma independente com `openssl rand`. Usar hex para
  senhas que também entram em opções Java; as duas chaves HMAC precisam de
  pelo menos 32 bytes aleatórios codificados em Base64. HMAC autentica mensagens
  entre componentes; não é uma senha de pessoa.
- Preencher as 14 variáveis do exemplo sem reutilizar valores locais. O usuário
  de migração e o usuário de execução do banco precisam de senhas distintas.
  Não regenerar chaves/senhas ao reiniciar um ambiente que já contém dados.
- Criar uma CA privada e certificados de servidor para `backend` e `seaweedfs`.
  Cada certificado precisa de SAN para o nome do respectivo serviço, `localhost`
  e IP `127.0.0.1`, além de uso de servidor TLS. SAN identifica os nomes que
  podem ser verificados por clientes e pelas sondas de saúde.
- Criar `backend.p12` no formato PKCS12, contendo chave privada do backend,
  certificado e cadeia da CA, protegido por `BETUFU_TLS_KEYSTORE_PASSWORD`.
- Criar truststores separados a partir de uma **cópia dos certificados públicos
  padrão de cada imagem Java**. No backend, a origem é
  `$JAVA_HOME/lib/security/cacerts`; no Keycloak implantado, `JAVA_HOME` está
  vazio e a origem confirmada é `/etc/pki/java/cacerts`. Usar `cp -L` e dar modo
  `600` à cópia antes de alterá-la: a origem do Keycloak gerou uma cópia `400`,
  e o `keytool` falhou ao tentar escrever até essa permissão ser corrigida.
  Importar a CA privada com `keytool`; não substituir a lista inteira pela CA privada,
  pois isso quebraria chamadas HTTPS a serviços públicos.
- Usar o `keytool` das próprias imagens aprovadas de backend e Keycloak,
  preservando a origem de cada truststore. Alterar a senha padrão da cópia
  (`changeit`) para `BETUFU_TLS_TRUSTSTORE_PASSWORD`. As opções
  `-storepass:env` e `-new:env` evitam colocar senhas literais nos comandos.
  Isso não exige instalar outro Java ou adicionar uma imagem auxiliar.

Arquivos esperados, relativos a `infra/learning/`:

```text
.env
secrets/
  ca.crt
  ca.key
  backend.p12
  seaweedfs.crt
  seaweedfs.key
  truststores/
    backend-truststore.jks
    keycloak-truststore.jks
```

`ca.key` fica somente no host, com permissão `600`; nunca é montada nos
containers. Os arquivos de execução são montados individualmente, somente
para leitura. Backend (UID 10001), Keycloak (UID 1000) e Nginx (UID 101)
precisam conseguir lê-los. Arquivos montados podem ter permissão `644`, com
o diretório pai no host protegido por `700`; não abrir a pasta inteira para
outros usuários. Conferir os UIDs nas imagens antes da preparação real.
O acesso administrativo ao Docker permite ler esses segredos.

## Referência de provisionamento: inicializar e conferir

Antes de iniciar, conferir caminhos, permissões, validade e nomes dos
certificados, login ECR e variáveis obrigatórias sem mostrar seus valores.
Confirmar que a rede bridge do Docker usa endereços `172.16.0.0/12`: a
expressão de proxies confiáveis no Compose foi preparada para esse intervalo.
Se a rede diferir, revisar a configuração antes de iniciar; nunca deixar a
lista de proxies vazia nem aceitar cabeçalhos de qualquer origem.

Na primeira inicialização, o script existente cria bancos separados e os
papéis de execução/migração. O Flyway aplica as migrations existentes ao banco
novo. Não alterar migrations nem conectar volumes antigos a este projeto.
O prefixo `betufu-learning` cria quatro volumes próprios, preservados entre
reinícios. O script de inicialização só roda com o diretório de dados vazio.

O realm importado chama-se `betufu-local`, pois esse nome já faz parte da
configuração compartilhada. `learning-admin` administra o Keycloak; `technical`
é a conta técnica usada pelo backend. Nenhuma delas deve ser promovida
automaticamente a administrador da BETUFU. A escolha do UUID para o primeiro
administrador da aplicação permanece pendente de um bloco específico; o seed
fica pendente enquanto esse bootstrap não for configurado.

Após iniciar no bloco aprovado, conferir saúde, logs sanitizados, memória e
disco. Se houver falha, diagnosticar o serviço específico antes de repetir a
inicialização. Não contornar erro TLS com validação de certificado desativada.

## Acesso pelo túnel SSH e E2E pendentes

Depois de os serviços estarem saudáveis, usar o IP atual da EC2 no comando
abaixo, em outro PowerShell. O marcador deve ser substituído antes de executar:

```powershell
ssh -i 'C:\Users\giova\Desktop\betufu-learning-key.pem' -N -o ExitOnForwardFailure=yes -L 127.0.0.1:4200:127.0.0.1:4200 -L 127.0.0.1:8081:127.0.0.1:8081 -L 127.0.0.1:8025:127.0.0.1:8025 ubuntu@IP_PUBLICO_ATUAL
```

`-N` mantém apenas o túnel; `-L` encaminha cada porta e
`ExitOnForwardFailure` encerra se alguma porta local já estiver ocupada.
Manter o terminal aberto durante o acesso:

- Aplicação: `http://localhost:4200`.
- Keycloak: `http://localhost:8081`.
- E-mails de teste no Mailpit: `http://localhost:8025`.

Usar `localhost`, pois os redirecionamentos OIDC foram configurados com esse
nome. O backend mantém o cookie Secure; confirmar o comportamento no navegador
usado para este acesso local. Não acessar a aplicação pelo IP público da EC2.

As confirmações manuais de cadastro e login encerraram a checagem inicial do
deploy. A suíte E2E completa não foi executada nesta EC2; o responsável a adiou
para um bloco próprio. Nesse bloco, usar contas e arquivos fictícios para cobrir
também upload, leitura autorizada, recuperação de senha e invalidação das
sessões antigas. Nginx aceita requisições de até 6 MB; o Spring mantém o
limite de 5 MB por arquivo. O listener do Keycloak assina o callback HTTPS de
recuperação usando a mesma chave v1 configurada no backend. Mailpit captura os
links de teste sem configurar entrega de e-mail para destinatários externos.
Somente essas provas em execução confirmarão o funcionamento integrado.

## Encerrar sem apagar dados

Na sessão SSH da EC2, este comando para os serviços e preserva os volumes.
Os caminhos absolutos permitem executá-lo mesmo estando na pasta pessoal `~`:

```bash
sudo docker compose \
  --env-file /home/ubuntu/betufu-learning/infra/learning/.env \
  --file /home/ubuntu/betufu-learning/infra/learning/compose.yaml \
  stop
```

Nunca usar `down -v` neste ambiente com dados a preservar. Manter o `.env`, os
certificados e as chaves junto dos dados; não regenerá-los em cada retomada.
Parar preserva o disco existente, mas não cria um backup independente.

Antes de sair, no console EC2: selecione a instância → **Estado da instância →
Parar instância**. Isso preserva o disco; não escolha **Encerrar**.
Fechar o SSH sozinho mantém a EC2 ligada e consumindo créditos. O disco EBS
continua existindo e pode ter custo mesmo com a instância parada.

## Retomar o ambiente existente

1. No console EC2 em São Paulo, selecionar a mesma instância e escolher
   **Iniciar instância**. Conferir o IP público atual.
2. No PowerShell do Windows, conectar por SSH usando o IP atual:

   ```powershell
   ssh -i 'C:\Users\giova\Desktop\betufu-learning-key.pem' ubuntu@IP_PUBLICO_ATUAL
   ```

3. Na EC2, iniciar os serviços com os mesmos arquivos e volumes:

   ```bash
   sudo docker compose \
     --env-file /home/ubuntu/betufu-learning/infra/learning/.env \
     --file /home/ubuntu/betufu-learning/infra/learning/compose.yaml \
     up -d
   ```

   O Compose atual não define reinício automático; iniciar a EC2 não substitui
   este passo. Com as imagens já presentes, não é necessário baixá-las novamente.
   A retomada reutiliza os dados existentes, sem repetir a criação de senhas.

4. Conferir os serviços; a inicialização pode levar alguns minutos:

   ```bash
   sudo docker compose \
     --env-file /home/ubuntu/betufu-learning/infra/learning/.env \
     --file /home/ubuntu/betufu-learning/infra/learning/compose.yaml \
     ps -a
   ```

5. Quando estiverem saudáveis, abrir outro PowerShell e executar o comando do
   [túnel SSH](#acesso-pelo-túnel-ssh-e-e2e-pendentes), usando o IP atual.
   Manter esse terminal aberto e acessar `http://localhost:4200`.

Este roteiro registra comandos para o responsável executar; o fechamento
documental não parou nem reiniciou a EC2. O pacote original contém o README do
commit `42bdb3f`; atualizações locais deste arquivo não são transferidas
automaticamente para o servidor.
