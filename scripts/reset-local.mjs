#!/usr/bin/env node

/**
 * scripts/reset-local.mjs
 *
 * Reset destrutivo do ambiente local BETUFU, per D-12.
 *
 * Este é o único comando da fase que APAGA dados. Todo o resto cria. Por isso o
 * desenho é fail-closed em cada ponto de decisão:
 *
 * - dry-run é o PADRÃO; remover exige `--apply` explícito;
 * - a lista exata do que será removido é impressa ANTES do prompt, sempre;
 * - a confirmação é comparada byte a byte e exige terminal interativo;
 * - a seleção vem de `local-resources.mjs`, função pura e testada;
 * - a remoção é item a item, por ID imutável, com shell:false.
 *
 * NUNCA usa `docker compose down -v`, `volume prune`, `system prune` ou
 * qualquer varredura: prune é o oposto de allowlist. Um recurso só sai se
 * satisfizer todas as condições da tabela.
 *
 * O inventário é coletado SEM pré-filtro: todos os containers e volumes da
 * máquina entram, e a função pura rejeita os que não pertencem. Isso faz o
 * dry-run demonstrar concretamente que os vizinhos foram vistos e recusados.
 *
 * A lógica vive em `runReset`, que recebe suas dependências por injeção. Isso
 * permite provar dry-run, gates e ordem de remoção sem tocar o daemon — a
 * camada onde o defeito de lock do bootstrap passou despercebido na primeira
 * entrega, justamente por não ter teste.
 *
 * Uso:
 *   node scripts/reset-local.mjs                       dry-run (padrão)
 *   node scripts/reset-local.mjs --apply               remove após confirmação
 *   node scripts/reset-local.mjs --apply --bootstrap   remove e sobe daily
 */

import { createInterface } from 'node:readline';
import { realpath } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

import {
  acquireLock,
  resolveComposeFile,
  resolveProjectRoot,
  runCaptured,
  runInherited,
} from './lib/compose.mjs';
import {
  CONFIRMATION_PHRASE,
  LOCAL_SERVICES,
  LOCAL_VOLUMES,
  isExactConfirmation,
  selectRemovable,
} from './lib/local-resources.mjs';

const INSPECT_TIMEOUT_MS = 60_000;
const REMOVE_TIMEOUT_MS = 120_000;
/** Lotes no inspect: uma linha de comando com centenas de nomes estoura no Windows. */
const INSPECT_BATCH = 40;

/**
 * Variáveis que denunciam ambiente não-local. O reset é exclusivamente local,
 * per D-12, e um host de homologação ou produção nunca deve conseguir executá-lo.
 */
const NON_LOCAL_PATTERN = /(homolog|prod)/iu;

const NON_LOCAL_MARKERS = Object.freeze([
  { name: 'CI', matches: (value) => value !== '' },
  // Os três usam o MESMO padrão, sem âncora de início. Ancorar apenas um deles
  // criava assimetria: `NODE_ENV=staging-production` passava pelo portão
  // enquanto o mesmo valor em `BETUFU_ENV` era recusado.
  { name: 'NODE_ENV', matches: (value) => NON_LOCAL_PATTERN.test(value) },
  { name: 'BETUFU_ENV', matches: (value) => NON_LOCAL_PATTERN.test(value) },
  { name: 'SPRING_PROFILES_ACTIVE', matches: (value) => NON_LOCAL_PATTERN.test(value) },
]);

export function usageText() {
  return `
Uso: node scripts/reset-local.mjs [--apply] [--bootstrap]

  (sem flags)   Dry-run: lista o que seria removido e encerra sem remover.
  --apply       Remove, após confirmação literal em terminal interativo.
  --bootstrap   Somente com --apply: sobe o profile daily ao final.

Confirmação exigida por --apply (exata, sem espaços extras):
  ${CONFIRMATION_PHRASE}
`.trim();
}

/**
 * Detecta ambiente não-local. Devolve o nome da variável culpada, ou null.
 */
export function detectNonLocalEnvironment(env) {
  for (const marker of NON_LOCAL_MARKERS) {
    const value = env[marker.name];
    if (typeof value === 'string' && value !== '' && marker.matches(value)) {
      return marker.name;
    }
  }
  return null;
}

/**
 * Núcleo do comando, com dependências injetadas.
 *
 * @returns {Promise<number>} exit code
 */
export async function runReset(options) {
  const {
    argv = [],
    env = {},
    platform = process.platform,
    stdinIsTTY = false,
    composeFile,
    deps,
  } = options;

  const log = deps.log ?? (() => {});
  const errorLog = deps.errorLog ?? (() => {});

  const unknown = argv.filter((a) => a !== '--apply' && a !== '--bootstrap');
  if (unknown.length > 0) {
    errorLog(`argumento desconhecido: ${unknown.join(', ')}`);
    errorLog(usageText());
    return 1;
  }

  const apply = argv.includes('--apply');
  const bootstrap = argv.includes('--bootstrap');

  if (bootstrap && !apply) {
    errorLog('--bootstrap só faz sentido junto de --apply.');
    return 1;
  }

  if (apply) {
    const marker = detectNonLocalEnvironment(env);
    if (marker !== null) {
      errorLog(`${marker} indica ambiente não-local. O reset é exclusivamente local.`);
      return 1;
    }
    // Sem terminal interativo, `echo FRASE | node reset-local.mjs --apply`
    // destruiria dados sem nenhum humano presente.
    if (!stdinIsTTY) {
      errorLog('--apply exige terminal interativo. Entrada redirecionada foi recusada.');
      return 1;
    }
  }

  let lock;
  try {
    lock = await deps.acquireLock();
  } catch (error) {
    errorLog(error.message);
    return 1;
  }
  log(`Lock adquirido (nonce: ${lock.nonce}).`);

  let removalFailed = false;
  let exitCode = 0;

  try {
    log('Coletando inventário Docker (leitura, sem pré-filtro)...');
    const { inventory, totals } = await deps.collectInventory();
    log(`Examinados ${totals.containers} containers e ${totals.volumes} volumes da máquina.`);

    const selection = selectRemovable(inventory, { composeFile, platform });
    printPlan(log, selection, totals);
    printIgnoredSample(log, selection);

    const nothingToRemove =
      selection.remove.containers.length === 0 && selection.remove.volumes.length === 0;

    if (!apply) {
      log('DRY-RUN: nada foi removido. Use --apply para remover de fato.');
      log(`Serviços na allowlist: ${LOCAL_SERVICES.join(', ')}`);
      log(`Volumes na allowlist: ${LOCAL_VOLUMES.join(', ')}`);
      return 0;
    }

    if (nothingToRemove) {
      // Não retorna cedo: o bootstrap pedido continua devido, e sair aqui o
      // engoliria em silêncio.
      log('Nada a remover. O ambiente já está limpo (idempotente).');
    } else {
      log(`Para confirmar, digite exatamente: ${CONFIRMATION_PHRASE}`);
      const answer = await deps.readConfirmation();
      if (!isExactConfirmation(answer ?? '')) {
        errorLog('Confirmação incorreta. Nada foi removido.');
        return 1;
      }

      log('Confirmação aceita. Removendo...');
      const { removed, failures } = await removeSelected(deps, selection, log, errorLog);
      log(`Removidos: ${removed.length}. Falhas: ${failures.length}.`);

      if (failures.length > 0) {
        removalFailed = true;
        exitCode = 1;
        errorLog(`Estado parcial — não removidos: ${failures.join(', ')}.`);
        errorLog('Reexecute o comando; a seleção converge para o que restou.');
      }
    }
  } finally {
    await lock.release();
    log('Lock liberado.');
  }

  // O bootstrap roda FORA do lock, e só depois de liberado.
  //
  // Ele dispara `dev.mjs up daily`, cujo primeiro ato é adquirir o MESMO
  // lockfile (`.betufu-dev.lock`, fixado em compose.mjs). Enquanto o reset
  // segurasse o lock, o subprocesso encontraria o PID dono vivo e falharia com
  // "Outra instância está rodando" — de forma determinística, não intermitente.
  // Era esse o defeito que tornava `--apply --bootstrap` inalcançável.
  if (bootstrap && !removalFailed) {
    log('Subindo profile daily...');
    const bootstrapExit = await deps.runBootstrap();
    if (bootstrapExit !== 0) {
      errorLog(`bootstrap falhou (exit ${bootstrapExit}). A remoção, essa, foi concluída.`);
      return bootstrapExit;
    }
  } else if (bootstrap && removalFailed) {
    errorLog('bootstrap não executado: a remoção terminou em estado parcial.');
  }

  return exitCode;
}

// ---------------------------------------------------------------------------
// Apresentação
// ---------------------------------------------------------------------------

function printPlan(log, selection, totals) {
  const { containers, volumes } = selection.remove;

  log('');
  log('=== RECURSOS QUE SERÃO REMOVIDOS ===');
  if (containers.length === 0 && volumes.length === 0) {
    log('  (nenhum — nada a fazer)');
  }
  for (const item of containers) {
    log(`  container  ${item.name}`);
  }
  for (const item of volumes) {
    log(`  volume     ${item.name}   [APAGA DADOS]`);
  }

  log('');
  log('=== PRESERVADOS ===');
  log(`  ${totals.containers - containers.length} de ${totals.containers} containers da máquina`);
  log(`  ${totals.volumes - volumes.length} de ${totals.volumes} volumes da máquina`);
  log('  redes, imagens, node_modules, navegadores do Playwright e ~/.m2 não são tocados');
  log('');
}

function printIgnoredSample(log, selection, limit = 12) {
  const sample = selection.ignored.slice(0, limit);
  if (sample.length === 0) return;
  log('Amostra de recursos vizinhos examinados e recusados:');
  for (const item of sample) {
    log(`  ${item.kind.padEnd(9)} ${item.name} — ${item.reason}`);
  }
  if (selection.ignored.length > sample.length) {
    log(`  ... e mais ${selection.ignored.length - sample.length}`);
  }
  log('');
}

// ---------------------------------------------------------------------------
// Remoção — item a item, por ID imutável
// ---------------------------------------------------------------------------

/**
 * Cada remoção devolve um dos três status: `removed`, `absent` ou `failed`.
 *
 * `absent` existe porque um recurso que sumiu entre o inventário e a remoção
 * produz saída não-zero no Docker, embora o estado final seja exatamente o
 * desejado. Tratá-lo como falha suprimiria o bootstrap e devolveria exit 1 sobre
 * uma convergência benigna.
 */
async function removeSelected(deps, selection, log, errorLog) {
  const failures = [];
  const removed = [];

  const apply = async (kind, item, remover, identity) => {
    const status = await remover(identity);
    if (status === 'removed') {
      removed.push(`${kind} ${item.name}`);
      log(`removido: ${kind} ${item.name}`);
    } else if (status === 'absent') {
      log(`já ausente: ${kind} ${item.name}`);
    } else {
      failures.push(`${kind} ${item.name}`);
      errorLog(`falha ao remover ${kind} ${item.name}.`);
    }
  };

  // Containers primeiro: um volume em uso não pode ser removido.
  for (const item of selection.remove.containers) {
    // Pelo ID, não pelo nome: fecha a janela entre inventário e remoção, em que
    // um nome poderia passar a designar outro container.
    await apply('container', item, deps.removeContainer, item.id ?? item.name);
  }

  for (const item of selection.remove.volumes) {
    // Volumes não têm ID separado no Docker: o nome é a identidade.
    await apply('volume', item, deps.removeVolume, item.name);
  }

  return { removed, failures };
}

/**
 * Classifica a saída do docker rm; "não existe" é convergência, não falha.
 *
 * Exportada para teste: é a única lógica desta camada que toma decisão própria,
 * e é pura — três fixtures a cobrem sem tocar o daemon. Qualquer texto de erro
 * desconhecido cai em `failed`, que é a direção segura.
 */
export function classifyRemoval(result) {
  if (result.exitCode === 0) return 'removed';
  return /no such|not found|nao encontrad|não encontrad/iu.test(result.stderr) ? 'absent' : 'failed';
}

// ---------------------------------------------------------------------------
// Dependências reais
// ---------------------------------------------------------------------------

async function listNames(args) {
  const result = await runCaptured('docker', args, { timeoutMs: INSPECT_TIMEOUT_MS });
  if (result.exitCode !== 0) {
    throw new Error(`docker ${args.join(' ')} falhou (exit ${result.exitCode}).`);
  }
  return result.stdout
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line !== '');
}

async function inspectLabels(kind, names) {
  if (names.length === 0) return [];
  const isContainer = kind === 'container';
  const template = isContainer
    ? '{{.Id}}\t{{.Name}}\t{{json .Config.Labels}}'
    : '{{.Name}}\t{{.Name}}\t{{json .Labels}}';

  const entries = [];
  for (let offset = 0; offset < names.length; offset += INSPECT_BATCH) {
    const batch = names.slice(offset, offset + INSPECT_BATCH);
    const result = await runCaptured(
      'docker',
      [isContainer ? 'container' : 'volume', 'inspect', '--format', template, ...batch],
      { timeoutMs: INSPECT_TIMEOUT_MS },
    );

    // exitCode != 0 acontece quando algum nome sumiu entre o ls e o inspect
    // (corrida normal). As linhas válidas ainda vêm em stdout; o que não vier
    // simplesmente não entra no inventário, e não entrar significa não ser
    // removido — a direção segura.
    for (const line of result.stdout.split('\n')) {
      const trimmed = line.trim();
      if (trimmed === '') continue;
      const parts = trimmed.split('\t');
      if (parts.length < 3) continue;
      const id = parts[0];
      const rawName = parts[1];
      const name = rawName.startsWith('/') ? rawName.substring(1) : rawName;
      let labels = {};
      try {
        const parsed = JSON.parse(parts.slice(2).join('\t'));
        if (parsed !== null && typeof parsed === 'object') labels = parsed;
      } catch {
        // Labels ilegíveis => objeto vazio => reprovado pela função pura.
      }
      entries.push({ id, name, labels });
    }
  }
  return entries;
}

async function collectInventory() {
  const [containerNames, volumeNames] = await Promise.all([
    listNames(['ps', '-a', '--format', '{{.Names}}']),
    listNames(['volume', 'ls', '--format', '{{.Name}}']),
  ]);

  const [containers, volumes] = await Promise.all([
    inspectLabels('container', containerNames),
    inspectLabels('volume', volumeNames),
  ]);

  return {
    inventory: { containers, volumes },
    totals: { containers: containerNames.length, volumes: volumeNames.length },
  };
}

async function readConfirmationFromStdin() {
  const rl = createInterface({ input: process.stdin, terminal: false });
  try {
    for await (const line of rl) {
      return line;
    }
    return null;
  } finally {
    rl.close();
  }
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

async function main() {
  const composeFile = await realpath(resolveComposeFile()).catch(() => null);
  if (composeFile === null) {
    console.error(`[reset] ERRO: infra/compose.yaml não encontrado a partir de ${resolveProjectRoot()}.`);
    return 1;
  }

  return runReset({
    argv: process.argv.slice(2),
    env: process.env,
    platform: process.platform,
    stdinIsTTY: process.stdin.isTTY === true,
    composeFile,
    deps: {
      acquireLock,
      collectInventory,
      readConfirmation: readConfirmationFromStdin,
      removeContainer: async (id) =>
        classifyRemoval(await runCaptured('docker', ['rm', '--force', id], {
          timeoutMs: REMOVE_TIMEOUT_MS,
        })),
      removeVolume: async (name) =>
        classifyRemoval(await runCaptured('docker', ['volume', 'rm', name], {
          timeoutMs: REMOVE_TIMEOUT_MS,
        })),
      runBootstrap: () =>
        runInherited(process.execPath, [
          resolve(resolveProjectRoot(), 'scripts', 'dev.mjs'),
          'up',
          'daily',
        ]),
      log: (message) => console.log(`[reset] ${message}`),
      errorLog: (message) => console.error(`[reset] ERRO: ${message}`),
    },
  });
}

// Só executa quando invocado diretamente, para que o módulo seja importável.
//
// A comparação é por URL canônica, não por sufixo de caminho. Casar sufixo é
// case-sensitive, e `node Scripts\reset-local.mjs` num filesystem
// case-insensitive daria falso: `main()` nunca rodaria e o processo encerraria
// com 0 sem ter feito nada. Para um comando destrutivo, "não fez nada e
// reportou sucesso" é o modo de falha errado, mesmo sendo seguro na direção.
if (process.argv[1] !== undefined
    && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    process.exitCode = await main();
  } catch (error) {
    console.error(`[reset] ERRO: ${error instanceof Error ? error.message : String(error)}`);
    process.exitCode = 1;
  }
}
