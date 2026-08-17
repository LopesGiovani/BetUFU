# Desenvolvimento local da BETUFU

Guia operacional do ambiente local: runtimes, origens, perfis, comandos e o reset
destrutivo. Todos os comandos são independentes de sistema operacional, per D-13:
nada de `bash -c`, encadeamento de shell ou wrapper específico de plataforma.

## Runtimes e origens

| Componente | Versão fixada | Origem |
|---|---|---|
| Java | 25 (Eclipse Adoptium) | wrapper Maven do repositório |
| Maven | 3.9.16 | `scripts/maven.mjs` |
| Node | conforme `package.json` do workspace | instalação local |
| Docker Compose | v2 (verificado em 5.3.1) | Docker Desktop / engine local |

As imagens de serviço são fixadas por digest SHA-256 em `infra/compose.yaml`.
Nenhum comando deste guia baixa imagem por tag móvel.

## Perfis

Definidos por `profiles:` em `infra/compose.yaml` e selecionados por
`--profile`. O nome do projeto Compose é `infra`, fixado em
`scripts/lib/compose.mjs`.

**`daily`** — 5 serviços. PostgreSQL, Redis, Keycloak, SeaweedFS (S3 local) e
Mailpit sobem em contêiner; Spring Boot e Angular rodam pelo terminal ou pela
IDE. É o modo de trabalho do dia a dia.

**`full`** — 7 serviços. Acrescenta o backend Spring em contêiner e o Angular
compilado servido por Nginx. É o modo de validação, mais lento porque constrói
as imagens `betufu/backend:local` e `betufu/frontend:local`.

## Comandos

```bash
node scripts/dev.mjs up daily
```

```bash
node scripts/dev.mjs up full
```

```bash
node scripts/dev.mjs status
```

```bash
node scripts/dev.mjs stop
```

`stop` para os contêineres e **preserva os volumes** — os dados continuam lá. É
a diferença central em relação ao reset, per D-12.

```bash
node scripts/dev.mjs logs postgres
```

A saída dos logs é sanitizada: valores de variáveis de ambiente nunca aparecem.

## Smokes

```bash
node scripts/dev.mjs verify daily
```

Sobe os 5 serviços e verifica idempotência, sobrevivência de volumes através de
um marcador no PostgreSQL, falha correta quando falta variável de ambiente,
bloqueio de concorrência pelo lockfile e tratamento de caminhos Unicode.

```bash
node scripts/dev.mjs verify full
```

Sobe os 7 serviços e verifica, além disso, o redirect OIDC 302 exato para o
Keycloak, o SPA fallback do Nginx, as regras de segurança do Nginx e a
idempotência da composição completa.

## Lock de concorrência

Todos os comandos que mexem na composição adquirem um lock exclusivo em
`.betufu-dev.lock`, na raiz do repositório. Isso impede que duas instâncias
operem ao mesmo tempo e deixem a composição pela metade. Se um processo morrer
sem liberar, o lock órfão é detectado pelo PID e removido automaticamente na
tentativa seguinte.

O reset usa **o mesmo** lock. Um reset não pode rodar concorrente a um `up`.

## Reset destrutivo

> **Este é o único comando que apaga dados.** Todo o resto cria.

```bash
node scripts/reset-local.mjs
```

Sem flags, o comando é **dry-run**: ele lista exatamente o que removeria e
encerra sem remover nada. Use esta forma sempre primeiro.

```bash
node scripts/reset-local.mjs --apply
```

Remove, mas só depois de você digitar a frase exata:

```
APAGAR DADOS LOCAIS BETUFU
```

A comparação é byte a byte. Caixa diferente, espaço a mais ou frase abreviada
são recusados, e nada é removido.

`--apply` só funciona em **terminal interativo**. Redirecionar a entrada
(`echo FRASE | node scripts/reset-local.mjs --apply`) é recusado: a confirmação
existe para colocar um humano na frente da decisão, e um pipe a contornaria.

`--apply` também é recusado quando o ambiente não parece local — `CI`,
`NODE_ENV=production`, ou `BETUFU_ENV`/`SPRING_PROFILES_ACTIVE` contendo
`homolog` ou `prod`. O reset é exclusivamente local, per D-12.

```bash
node scripts/reset-local.mjs --apply --bootstrap
```

Igual ao anterior e, ao final, sobe o perfil `daily`. O bootstrap é **opcional e
desligado por padrão** de propósito: encadear destruição com subida faz o
comando demorar minutos e falhar por motivos alheios à remoção, e aí fica
impossível saber se o problema foi o reset ou a subida.

O bootstrap roda **depois** de o lock ser liberado. Isso não é detalhe: ele
dispara `dev.mjs up daily`, cujo primeiro ato é adquirir o mesmo
`.betufu-dev.lock`. Subir ainda segurando o lock faria o subprocesso encontrar o
PID dono vivo e falhar com "Outra instância está rodando", sempre. Se a remoção
terminar em estado parcial, o bootstrap não roda.

### O que o reset remove

| Tipo | Recurso |
|---|---|
| container | `infra-postgres-1`, `infra-redis-1`, `infra-keycloak-1`, `infra-seaweedfs-1`, `infra-mailpit-1`, `infra-backend-1`, `infra-frontend-1` |
| volume | `betufu-local-postgres-data`, `betufu-local-redis-data`, `betufu-local-seaweed-data`, `betufu-local-mailpit-data` |

Um contêiner só é removido se satisfizer **quatro** condições ao mesmo tempo:
serviço na lista acima, projeto Compose `infra`, não ser um contêiner one-off, e
o label `com.docker.compose.project.config_files` apontar para o
`infra/compose.yaml` **deste** checkout. Essa última condição é o que impede o
reset de alcançar outra composição: `infra` é um nome de diretório banal, e sem
a âncora de caminho um projeto de terceiro com o mesmo nome casaria.

Um volume só é removido se o nome for **exatamente** um dos quatro, o projeto
for `infra` e o label de volume bater com o nome. Volumes não carregam o label
`config_files` — o nome literal é o discriminador.

### O que o reset nunca toca

- **Redes.** Nem `infra_default`. Não guardam dado, são recriadas pelo `up`, e
  não há critério forte para distingui-las.
- **Imagens.** Nem as fixadas por digest (recuperá-las exigiria rede), nem
  `betufu/backend:local` e `betufu/frontend:local`. Reset apaga dados, não
  artefatos de build.
- **`node_modules`**, navegadores do Playwright, `~/.m2`.
- **Qualquer recurso Docker** que não satisfaça todas as condições acima.
- **Volumes anônimos e dangling.** O reset nunca executa `docker system prune`,
  `volume prune`, `image prune` ou `docker compose down -v`. Prune é o oposto de
  uma allowlist: ele remove por ausência de critério, não por presença.

### Cuidado com múltiplos checkouts

Os volumes têm `name:` explícito em `infra/compose.yaml`, o que os torna nomes
**globais** no Docker, sem prefixo de projeto. Consequência: se você tiver dois
clones da BETUFU nesta máquina, os dois compartilham exatamente os mesmos
volumes. Um reset disparado de um clone apaga os dados que o outro usa, e nenhum
label distingue os dois — porque, do ponto de vista do Docker, não são recursos
diferentes.

Os contêineres, esses, são distinguíveis pela âncora `config_files`.

### Se o reset for interrompido

O comando remove um recurso por vez e reporta o que saiu e o que ficou, com exit
code diferente de zero. Basta reexecutar: a seleção é recalculada sobre o estado
atual e converge para o que restou. Rodar o reset num ambiente já limpo é uma
operação válida que não faz nada.

## Testes

```bash
node --test scripts/reset-local.test.mjs
```

A prova de escopo recai sobre a função pura de seleção em
`scripts/lib/local-resources.mjs`, alimentada por inventários em memória que
incluem vizinhos hostis — volumes com prefixo parecido, contêineres do mesmo
projeto em outro checkout, serviços desconhecidos. Nenhum teste cria ou remove
recurso real: um teste que criasse um recurso homônimo de verdade e rodasse o
reset em cima causaria justamente a perda que deveria detectar.

A segunda metade dos testes cobre o CLI, com as dependências injetadas por
`runReset`: dry-run como padrão, os gates de `--apply`, a recusa de ambiente não
local, a ordem containers→volumes, o caminho de falha parcial e a ordem entre
liberar o lock e subir o bootstrap. Também aqui nada toca o daemon — as remoções
são registradas num diário de eventos, não executadas.

O que os testes **não** cobrem, e é limite conhecido: que o `docker volume rm`
apagou o volume certo e só ele. Provar isso exigiria remover um volume real.
Essa lacuna é coberta pelo dry-run, que mostra a decisão concreta na sua máquina
antes de qualquer remoção.
