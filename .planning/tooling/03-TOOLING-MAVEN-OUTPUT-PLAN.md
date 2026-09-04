---
plan_id: 03-TOOLING-MAVEN-OUTPUT
type: execute
status: complete
completed: 2026-09-04
depends_on: [03-25]
worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-03-gap-exec
branch: worktree-agent-03-gap-exec
base_sha: 8a85cec64c7ca8be815892240c60c48b52c3327a
current_head_at_planning: 40f90057f4dd33aea2e56b9ae205e358b83150fc
files_modified:
  - scripts/maven.mjs
  - scripts/lib/maven-output.mjs
  - scripts/maven-output.test.mjs
  - docs/development.md
autonomous: false
no_dependencies: true
no_generated_code: true
no_openapi_change: true
estimate: { tasks: 5, confidence: medium }
must_haves:
  truths:
    - "O Maven continua recebendo exatamente os mesmos argumentos e executando o mesmo wrapper por plataforma."
    - "Uma execucao bem-sucedida mostra somente um resumo curto e, quando disponivel, os totais de testes."
    - "Uma execucao com falha mostra no maximo dez linhas relevantes e o caminho do log completo."
    - "O codigo de saida do Maven e os sinais SIGINT/SIGTERM continuam sendo propagados."
    - "A coleta e limitada em memoria e nao despeja o log completo no terminal."
    - "A mudanca nao altera Java, Spring, banco, OpenAPI, cliente gerado, dependencias, CI ou hooks."
  artifacts:
    - {path: scripts/maven.mjs, provides: "entry point Maven com saida resumida"}
    - {path: scripts/lib/maven-output.mjs, provides: "coleta, contagem e selecao de linhas sem estado global"}
    - {path: scripts/maven-output.test.mjs, provides: "provas deterministicas do contrato de saida"}
    - {path: docs/development.md, provides: "orientacao sobre resumo e log de falha"}
  key_links:
    - {from: scripts/maven.mjs, to: scripts/lib/maven-output.mjs, via: "coleta bounded e formatacao do resumo"}
    - {from: scripts/maven-output.test.mjs, to: scripts/lib/maven-output.mjs, via: "testes Node nativos"}
    - {from: docs/development.md, to: scripts/maven.mjs, via: "comando canonico documentado"}
---

# Plano auxiliar de tooling: saida resumida do Maven

## Objetivo

Reduzir a poluicao do terminal causada por `stdio: 'inherit'` em
`scripts/maven.mjs`, sem mudar o processo Maven que roda o projeto. O wrapper
continuara sendo o comando canonico local, mas sua saida sera capturada em um
arquivo temporario e resumida para o operador.

Este e um plano auxiliar de tooling. Ele nao cria um novo gap esportivo e nao
altera a contagem da roadmap da Fase 3.

## Contrato observavel

### Sucesso

Imprimir:

```text
[maven] BUILD SUCCESS
[maven] Tests run: N, Failures: 0, Errors: 0, Skipped: 0
```

A segunda linha so aparece quando uma ou mais linhas Surefire/Failsafe com
`Tests run` puderem ser interpretadas. Goals como `spotless:check`, que nao
executam testes, mostram apenas `BUILD SUCCESS`.

### Falha

Imprimir `BUILD FAILURE`, ate dez linhas nao vazias selecionadas do erro e o
caminho do log completo mantido no diretorio temporario do sistema operacional.
As linhas preferidas sao `BUILD FAILURE`, `[ERROR]`, `Failed to execute goal`,
`There are test failures`, `Caused by:` e resumos de testes com falhas/erros.
Se nenhuma linha preferida existir, usar as ultimas dez linhas nao vazias.

O resumo nunca deve imprimir o conteudo integral do log. O arquivo temporario
de sucesso deve ser removido depois que o resumo for emitido; o arquivo de
falha deve permanecer para diagnostico local.

## Abordagem tecnica

O processo filho continuara usando `shell: false`, a mesma resolucao do
`ComSpec` no Windows e a mesma lista allowlisted de argumentos. Apenas o
stdout e o stderr passarao a ser enviados para um coletor pequeno e para um
arquivo temporario, enquanto o stdin continuara herdado para nao quebrar
comandos que precisem de entrada.

O coletor deve:

- aceitar chunks parciais e completar linhas somente quando encontrar newline;
- manter apenas estado limitado para contagem e para as dez linhas de falha;
- somar linhas `Tests run: ..., Failures: ..., Errors: ..., Skipped: ...` de
  Surefire/Failsafe, sem inventar zero quando nao houver evidencia;
- remover sequencias ANSI antes de selecionar linhas para o terminal;
- fechar o arquivo antes de imprimir o resultado final;
- deixar a decisao de sucesso/falha baseada no exit code do processo, nao em
  texto que o Maven imprimiu.

Nao sera criada dependencia nova. Nenhum arquivo Java, contrato OpenAPI,
cliente gerado, migration, CI ou hook entra neste plano. O CI que chama
`./mvnw` diretamente continuara com seu comportamento atual.

## Tasks sequenciais

<tasks>
<task type="auto" tdd="true">
  <name>Fixar o coletor puro e o contrato de resumo</name>
  <files>scripts/lib/maven-output.mjs; scripts/maven-output.test.mjs</files>
  <read_first>scripts/maven.mjs; scripts/reset-local.test.mjs; docs/development.md</read_first>
  <action>Criar um modulo sem efeitos colaterais para receber chunks, gravar o log completo por meio do callback fornecido, acumular somente estado bounded, somar contadores de testes, limpar ANSI e selecionar no maximo dez linhas de falha. Expor tambem a conversao do encerramento do processo para exit code para que essa regra seja testavel sem iniciar Maven. Nao assumir que ausencia de uma linha de testes significa zero testes.</action>
  <verify><automated>node --test scripts/maven-output.test.mjs</automated></verify>
  <acceptance_criteria>Chunks divididos em qualquer fronteira produzem as mesmas linhas e contagens; entradas sem testes nao recebem contagem inventada; a selecao nunca passa de dez linhas.</acceptance_criteria>
  <done>Coletor deterministico coberto por testes unitarios Node.</done>
</task>

<task type="auto" tdd="true">
  <name>Conectar captura ao wrapper Maven sem alterar sua autoridade</name>
  <files>scripts/maven.mjs; scripts/lib/maven-output.mjs</files>
  <read_first>scripts/maven.mjs; scripts/lib/maven-output.mjs; .githooks/pre-commit; README.md</read_first>
  <action>Substituir somente o encaminhamento direto de stdout/stderr por captura em arquivo temporario, mantendo stdin herdado, cwd, shell:false, windowsHide, ComSpec, validacao de argumentos e propagacao de SIGINT/SIGTERM. Aguardar o flush do arquivo antes do resumo; em exit code zero imprimir BUILD SUCCESS e os totais detectados, remover o temporario e sair com zero; em exit code nao zero imprimir BUILD FAILURE, ate dez linhas e o caminho do arquivo, mantendo o exit code original. Erro de spawn deve continuar usando a falha do wrapper e nao pode vazar log integral.</action>
  <verify><automated>node --check scripts/maven.mjs</automated></verify>
  <acceptance_criteria>O entry point continua portatil no Windows/Unix, nao usa shell para montar argumentos e nao muda o codigo de saida observado pelo chamador.</acceptance_criteria>
  <done>Wrapper usa output masking com log completo somente para diagnostico de falha.</done>
</task>

<task type="auto" tdd="true">
  <name>Provar falha, sucesso e limites do wrapper</name>
  <files>scripts/maven-output.test.mjs; scripts/maven.mjs</files>
  <read_first>scripts/maven.mjs; scripts/lib/maven-output.mjs; scripts/maven-output.test.mjs</read_first>
  <action>Completar as provas do coletor e do contrato de encerramento: sucesso sem testes, sucesso com varias linhas Surefire/Failsafe, falha com mais de dez candidatos, fallback sem marcadores, chunks stdout/stderr separados, ANSI, exit codes numericos e SIGINT/SIGTERM mapeados. O teste nao deve depender de Testcontainers, rede, Docker ou suite Maven completa.</action>
  <verify><automated>node --test scripts/maven-output.test.mjs</automated></verify>
  <acceptance_criteria>As provas falham quando a antiga politica de despejar tudo ou de perder o exit code e reintroduzida.</acceptance_criteria>
  <done>Contrato de output masking possui evidencia discriminante e local.</done>
</task>

<task type="auto">
  <name>Documentar o diagnostico local sem ampliar escopo</name>
  <files>docs/development.md</files>
  <read_first>docs/development.md; README.md; scripts/maven.mjs</read_first>
  <action>Adicionar uma nota curta explicando que o comando canonico mostra resumo em sucesso e preserva o caminho do log quando falha. Nao alterar comandos existentes, CI, hooks ou instrucoes de infraestrutura.</action>
  <verify><automated>node --check scripts/maven.mjs</automated></verify>
  <acceptance_criteria>A documentacao orienta como localizar o log de uma falha e continua coerente com o comando ja publicado.</acceptance_criteria>
  <done>Operador consegue entender a nova saida sem conhecer a implementacao.</done>
</task>

<task type="auto">
  <name>Executar a certificacao focal e agregada</name>
  <files>scripts/maven.mjs; scripts/lib/maven-output.mjs; scripts/maven-output.test.mjs; docs/development.md</files>
  <read_first>scripts/maven.mjs; scripts/lib/maven-output.mjs; scripts/maven-output.test.mjs; docs/development.md</read_first>
  <action>Reconciliar o status com a allowlist e executar somente os comandos literais abaixo. Registrar as contagens reais e confirmar que nenhuma linha integral de log foi emitida pelo wrapper. Se qualquer arquivo fora da allowlist aparecer, ou se a suite revelar regressao fora deste tooling, parar antes de corrigir.</action>
  <verify><automated>node --check scripts/maven.mjs</automated><automated>node --test scripts/maven-output.test.mjs</automated><automated>node scripts/maven.mjs -f backend/pom.xml -DskipTests validate</automated><automated>node scripts/maven.mjs -f backend/pom.xml verify</automated><automated>node --test scripts/*.test.mjs</automated></verify>
  <acceptance_criteria>Focais e agregados passam, o output do Maven permanece curto, e o diff final contem somente os quatro paths allowlisted.</acceptance_criteria>
  <done>Snapshot pronto para revisao somente leitura; stage e commits continuam separados.</done>
</task>
</tasks>

## Verificacoes e governanca

- Antes da primeira edicao de codigo, confirmar novamente branch, HEAD, status
  limpo e os quatro paths allowlisted.
- Depois de cada task, executar apenas o `<verify>` literal daquela task e
  interpretar o resultado antes de continuar.
- O orquestrador deve repetir independentemente a certificacao focal e as duas
  suites agregadas: `node scripts/maven.mjs -f backend/pom.xml verify` e
  `node --test scripts/*.test.mjs`.
- Nao executar `--auto`, `--all`, `--force`, Docker, Testcontainers,
  instalacao, rede, geracao de cliente, stage, commit, push, merge, rebase ou
  cleanup por inferencia.
- Qualquer necessidade de mudar uma API, dependencia, infraestrutura, contrato,
  CI, hook ou path fora da allowlist bloqueia o plano e exige nova aprovacao.
- Apos as verificacoes, congelar status, HEAD, paths e hashes para revisao
  somente leitura. Qualquer correcao invalida o snapshot anterior.

## Resultado esperado

O desenvolvedor continua usando exatamente `node scripts/maven.mjs ...`, mas o
terminal recebe duas linhas em sucesso e no maximo onze linhas curtas em falha
(resumo, ate dez linhas e caminho do log). O Maven, o Java/Spring e as regras
de negocio permanecem inalterados.
