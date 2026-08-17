#!/usr/bin/env node

/**
 * scripts/reset-local.test.mjs
 *
 * Prova de escopo do reset local, sobre a função PURA de seleção.
 *
 * Por que não há teste que remova recurso de verdade: o `<action>` do Plano
 * 01-24 pede para "testar recurso externo homônimo", e a leitura literal disso
 * seria criar um recurso homônimo no daemon e rodar o reset em cima. Se a
 * guarda tiver o defeito que o teste procura, o próprio teste causa a perda que
 * deveria detectar. A prova recai sobre a decisão — que é onde mora o risco de
 * apagar a mais — com inventários em memória, incluindo vizinhos hostis.
 *
 * O que estes testes NÃO cobrem, declarado: que o `docker volume rm` apagou o
 * volume certo e só ele. Isso exigiria remover um volume real e não há forma
 * segura. Essa lacuna é coberta pelo dry-run real na máquina do responsável,
 * antes de qualquer `--apply`.
 */

import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  CONFIRMATION_PHRASE,
  LOCAL_SERVICES,
  LOCAL_VOLUMES,
  isExactConfirmation,
  normalizeConfigPath,
  selectRemovable,
} from './lib/local-resources.mjs';
import { classifyRemoval, detectNonLocalEnvironment, runReset } from './reset-local.mjs';

/** Nomes selecionados, para asserções legíveis. */
const names = (items) => items.map((item) => item.name);

const CHECKOUT = 'C:\\Users\\giova\\Desktop\\BetUFU\\infra\\compose.yaml';
const OTHER_CHECKOUT = 'D:\\outro-projeto\\infra\\compose.yaml';
const WIN = { composeFile: CHECKOUT, platform: 'win32' };

function container(service, overrides = {}) {
  return {
    name: `infra-${service}-1`,
    labels: {
      'com.docker.compose.service': service,
      'com.docker.compose.project': 'infra',
      'com.docker.compose.oneoff': 'False',
      'com.docker.compose.project.config_files': CHECKOUT,
      ...overrides,
    },
  };
}

function volume(name, overrides = {}) {
  return {
    name,
    labels: {
      'com.docker.compose.project': 'infra',
      'com.docker.compose.volume': name,
      ...overrides,
    },
  };
}

/** Inventário realista: a composição BETUFU cercada de recursos de terceiro. */
function fullInventory() {
  return {
    containers: [
      ...LOCAL_SERVICES.map((service) => container(service)),
      // vizinhos
      { name: 'meu-nginx', labels: {} },
      { name: 'outro-postgres', labels: { 'com.docker.compose.service': 'postgres' } },
      container('postgres', { 'com.docker.compose.project.config_files': OTHER_CHECKOUT }),
    ],
    volumes: [
      ...LOCAL_VOLUMES.map((name) => volume(name)),
      { name: 'pgdata', labels: {} },
      { name: 'meus-dados-importantes', labels: {} },
    ],
  };
}

test('baseline: seleciona exatamente os 7 containers e 4 volumes da BETUFU', () => {
  const { remove } = selectRemovable(fullInventory(), WIN);

  assert.deepEqual(
    names(remove.containers).sort(),
    LOCAL_SERVICES.map((s) => `infra-${s}-1`).sort(),
  );
  assert.deepEqual(names(remove.volumes).sort(), [...LOCAL_VOLUMES].sort());
});

test('baseline: nenhum recurso vizinho entra na seleção', () => {
  const { remove } = selectRemovable(fullInventory(), WIN);

  for (const hostile of ['meu-nginx', 'outro-postgres']) {
    assert.ok(!names(remove.containers).includes(hostile), `${hostile} não pode ser selecionado`);
  }
  for (const hostile of ['pgdata', 'meus-dados-importantes']) {
    assert.ok(!names(remove.volumes).includes(hostile), `${hostile} não pode ser selecionado`);
  }
});

// --- M1: igualdade de nome, não prefixo ------------------------------------

test('M1: volume com o prefixo BETUFU mas nome diferente é recusado', () => {
  const { remove, ignored } = selectRemovable(
    { volumes: [volume('betufu-local-postgres-data-backup')] },
    WIN,
  );

  assert.deepEqual(names(remove.volumes), []);
  assert.match(ignored[0].reason, /allowlist literal/u);
});

// --- M2: âncora de checkout em containers ----------------------------------

test('M2: container de outro checkout, com projeto infra idêntico, é recusado', () => {
  const { remove, ignored } = selectRemovable(
    { containers: [container('postgres', { 'com.docker.compose.project.config_files': OTHER_CHECKOUT })] },
    WIN,
  );

  assert.deepEqual(names(remove.containers), []);
  assert.match(ignored[0].reason, /outro checkout/u);
});

test('M2: container sem o label config_files é recusado', () => {
  const labels = container('redis').labels;
  delete labels['com.docker.compose.project.config_files'];

  const { remove, ignored } = selectRemovable(
    { containers: [{ name: 'infra-redis-1', labels }] },
    WIN,
  );

  assert.deepEqual(names(remove.containers), []);
  assert.match(ignored[0].reason, /não ancorável/u);
});

// --- M2b: volumes não têm config_files, então o nome carrega a guarda -------

test('M2b: volume com projeto infra mas nome fora da allowlist é recusado', () => {
  const { remove, ignored } = selectRemovable({ volumes: [volume('pgdata')] }, WIN);

  assert.deepEqual(names(remove.volumes), []);
  assert.match(ignored[0].reason, /allowlist literal/u);
});

test('M2b: volume da allowlist com projeto divergente é recusado', () => {
  const { remove, ignored } = selectRemovable(
    { volumes: [volume('betufu-local-redis-data', { 'com.docker.compose.project': 'outro' })] },
    WIN,
  );

  assert.deepEqual(names(remove.volumes), []);
  assert.match(ignored[0].reason, /projeto Compose/u);
});

// --- M3: conjunção, não disjunção ------------------------------------------

test('M3: satisfazer apenas uma condição não basta', () => {
  const { remove } = selectRemovable(
    {
      containers: [{ name: 'qualquer', labels: { 'com.docker.compose.project': 'infra' } }],
      volumes: [{ name: 'betufu-local-mailpit-data', labels: {} }],
    },
    WIN,
  );

  assert.deepEqual(names(remove.containers), []);
  assert.deepEqual(names(remove.volumes), []);
});

// --- M4: serviço fora do conjunto conhecido --------------------------------

test('M4: serviço desconhecido no mesmo projeto e checkout é recusado', () => {
  const { remove, ignored } = selectRemovable(
    {
      containers: [{
        name: 'infra-grafana-1',
        labels: {
          'com.docker.compose.service': 'grafana',
          'com.docker.compose.project': 'infra',
          'com.docker.compose.oneoff': 'False',
          'com.docker.compose.project.config_files': CHECKOUT,
        },
      }],
    },
    WIN,
  );

  assert.deepEqual(names(remove.containers), []);
  assert.match(ignored[0].reason, /fora da allowlist/u);
});

// --- M4b: containers one-off -----------------------------------------------

test('M4b: container one-off do mesmo serviço é recusado', () => {
  const { remove, ignored } = selectRemovable(
    { containers: [container('postgres', { 'com.docker.compose.oneoff': 'True' })] },
    WIN,
  );

  assert.deepEqual(names(remove.containers), []);
  assert.match(ignored[0].reason, /one-off/u);
});

// --- M9: normalização de separador, nas duas direções ----------------------

test('M9: mesmo checkout com barras normais casa em win32', () => {
  const { remove } = selectRemovable(
    {
      containers: [container('keycloak', {
        'com.docker.compose.project.config_files':
          'C:/Users/giova/Desktop/BetUFU/infra/compose.yaml',
      })],
    },
    WIN,
  );

  assert.deepEqual(names(remove.containers), ['infra-keycloak-1']);
});

test('M9: normalização não pode casar checkouts diferentes', () => {
  const { remove } = selectRemovable(
    {
      containers: [container('keycloak', {
        'com.docker.compose.project.config_files':
          'C:/Users/giova/Desktop/BetUFU-copia/infra/compose.yaml',
      })],
    },
    WIN,
  );

  assert.deepEqual(names(remove.containers), []);
});

test('M9: case-folding vale em win32 e não vale em linux', () => {
  const upper = '/home/dev/BetUFU/infra/compose.yaml';
  const lower = '/home/dev/betufu/infra/compose.yaml';

  assert.equal(normalizeConfigPath(upper, 'win32'), normalizeConfigPath(lower, 'win32'));
  assert.notEqual(normalizeConfigPath(upper, 'linux'), normalizeConfigPath(lower, 'linux'));
});

// --- M5: confirmação literal -----------------------------------------------

test('M5: só a frase exata confirma', () => {
  assert.ok(isExactConfirmation(CONFIRMATION_PHRASE));
  assert.ok(isExactConfirmation(`${CONFIRMATION_PHRASE}\n`));
  assert.ok(isExactConfirmation(`${CONFIRMATION_PHRASE}\r\n`));
});

test('M5: variações de caixa, espaço e conteúdo são recusadas', () => {
  for (const wrong of [
    'apagar dados locais betufu',
    ' APAGAR DADOS LOCAIS BETUFU',
    'APAGAR DADOS LOCAIS BETUFU ',
    'APAGAR  DADOS LOCAIS BETUFU',
    'APAGAR DADOS LOCAIS',
    'sim',
    '',
  ]) {
    assert.ok(!isExactConfirmation(wrong), `"${wrong}" não pode confirmar`);
  }
});

// --- Convergência e fail-closed -------------------------------------------

test('idempotência: inventário sem recursos BETUFU seleciona conjunto vazio', () => {
  const { remove } = selectRemovable(
    {
      containers: [{ name: 'meu-nginx', labels: {} }],
      volumes: [{ name: 'pgdata', labels: {} }],
    },
    WIN,
  );

  assert.deepEqual(names(remove.containers), []);
  assert.deepEqual(names(remove.volumes), []);
});

test('inventário vazio seleciona conjunto vazio', () => {
  const { remove, ignored } = selectRemovable({}, WIN);

  assert.deepEqual(names(remove.containers), []);
  assert.deepEqual(names(remove.volumes), []);
  assert.deepEqual(ignored, []);
});

test('fail-closed: sem âncora de checkout a seleção nem sequer roda', () => {
  assert.throws(
    () => selectRemovable(fullInventory(), { composeFile: '', platform: 'win32' }),
    /composeFile/u,
  );
});

test('todo recurso recusado carrega um motivo legível', () => {
  const { ignored } = selectRemovable(fullInventory(), WIN);

  assert.ok(ignored.length > 0);
  for (const item of ignored) {
    assert.ok(typeof item.reason === 'string' && item.reason.length > 0);
    assert.ok(item.kind === 'container' || item.kind === 'volume');
  }
});

// ===========================================================================
// Camada do CLI — runReset com dependências injetadas
//
// Esta camada não tinha teste na primeira entrega, e foi exatamente onde o
// defeito de lock do bootstrap se escondeu: `--apply --bootstrap` disparava
// `dev.mjs up` ainda segurando `.betufu-dev.lock`, e o subprocesso morria
// contra o lock do próprio pai, de forma determinística.
//
// Nada aqui toca o daemon: as remoções são registradas, não executadas.
// ===========================================================================

/** Constrói dependências falsas e um diário de eventos ordenado. */
function fakeDeps(overrides = {}) {
  const events = [];
  const deps = {
    acquireLock: async () => {
      events.push('lock:acquire');
      return {
        nonce: 'fake-nonce',
        release: async () => { events.push('lock:release'); },
      };
    },
    collectInventory: async () => {
      events.push('inventory');
      return {
        inventory: fullInventory(),
        totals: { containers: 10, volumes: 6 },
      };
    },
    readConfirmation: async () => {
      events.push('confirm:read');
      return CONFIRMATION_PHRASE;
    },
    removeContainer: async (id) => { events.push(`rm:container:${id}`); return 'removed'; },
    removeVolume: async (name) => { events.push(`rm:volume:${name}`); return 'removed'; },
    runBootstrap: async () => { events.push('bootstrap'); return 0; },
    log: () => {},
    errorLog: () => {},
    ...overrides,
  };
  return { deps, events };
}

const BASE = { platform: 'win32', composeFile: CHECKOUT, stdinIsTTY: true, env: {} };

test('CLI: dry-run é o padrão e nunca remove', async () => {
  const { deps, events } = fakeDeps();
  const code = await runReset({ ...BASE, argv: [], deps });

  assert.equal(code, 0);
  assert.ok(!events.some((e) => e.startsWith('rm:')), 'nenhuma remoção pode ocorrer');
  assert.ok(!events.includes('confirm:read'), 'dry-run não pede confirmação');
});

test('CLI: --bootstrap sem --apply é recusado', async () => {
  const { deps, events } = fakeDeps();
  const code = await runReset({ ...BASE, argv: ['--bootstrap'], deps });

  assert.equal(code, 1);
  assert.deepEqual(events, []);
});

test('CLI: argumento desconhecido é recusado antes de qualquer ação', async () => {
  const { deps, events } = fakeDeps();
  const code = await runReset({ ...BASE, argv: ['--force'], deps });

  assert.equal(code, 1);
  assert.deepEqual(events, []);
});

test('CLI: CI definido recusa --apply sem tocar no lock', async () => {
  const { deps, events } = fakeDeps();
  const code = await runReset({ ...BASE, argv: ['--apply'], env: { CI: 'true' }, deps });

  assert.equal(code, 1);
  assert.deepEqual(events, []);
});

test('CLI: homolog e prod recusam --apply', async () => {
  for (const env of [
    { SPRING_PROFILES_ACTIVE: 'prod' },
    { SPRING_PROFILES_ACTIVE: 'homolog' },
    { NODE_ENV: 'production' },
    { BETUFU_ENV: 'homolog' },
  ]) {
    const { deps, events } = fakeDeps();
    const code = await runReset({ ...BASE, argv: ['--apply'], env, deps });

    assert.equal(code, 1, `${JSON.stringify(env)} deve recusar`);
    assert.deepEqual(events, [], `${JSON.stringify(env)} não pode agir`);
  }
});

test('CLI: --apply exige terminal interativo', async () => {
  const { deps, events } = fakeDeps();
  const code = await runReset({ ...BASE, argv: ['--apply'], stdinIsTTY: false, deps });

  assert.equal(code, 1);
  assert.deepEqual(events, []);
});

test('CLI: confirmação incorreta encerra non-zero sem remover', async () => {
  const { deps, events } = fakeDeps({ readConfirmation: async () => 'apagar dados locais betufu' });
  const code = await runReset({ ...BASE, argv: ['--apply'], deps });

  assert.equal(code, 1);
  assert.ok(!events.some((e) => e.startsWith('rm:')), 'nada pode ser removido');
  assert.ok(events.includes('lock:release'), 'o lock precisa ser liberado mesmo na recusa');
});

test('CLI: confirmação correta remove containers antes de volumes', async () => {
  const { deps, events } = fakeDeps();
  const code = await runReset({ ...BASE, argv: ['--apply'], deps });

  assert.equal(code, 0);
  const removals = events.filter((e) => e.startsWith('rm:'));
  const lastContainer = removals.findLastIndex((e) => e.startsWith('rm:container:'));
  const firstVolume = removals.findIndex((e) => e.startsWith('rm:volume:'));

  assert.equal(removals.filter((e) => e.startsWith('rm:container:')).length, 7);
  assert.equal(removals.filter((e) => e.startsWith('rm:volume:')).length, 4);
  assert.ok(lastContainer < firstVolume, 'um volume em uso não pode ser removido antes do container');
});

// --- Regressão do P1 -------------------------------------------------------

test('P1: o bootstrap roda DEPOIS de o lock ser liberado', async () => {
  const { deps, events } = fakeDeps();
  const code = await runReset({ ...BASE, argv: ['--apply', '--bootstrap'], deps });

  assert.equal(code, 0);
  const release = events.indexOf('lock:release');
  const bootstrap = events.indexOf('bootstrap');

  assert.ok(release !== -1 && bootstrap !== -1, 'ambos os eventos precisam ocorrer');
  assert.ok(
    release < bootstrap,
    'dev.mjs up adquire o MESMO .betufu-dev.lock; subir dentro do lock trava sempre',
  );
});

test('P1: exit code do bootstrap é propagado', async () => {
  const { deps } = fakeDeps({ runBootstrap: async () => 7 });
  const code = await runReset({ ...BASE, argv: ['--apply', '--bootstrap'], deps });

  assert.equal(code, 7);
});

test('P3a: ambiente já limpo ainda executa o bootstrap pedido', async () => {
  const { deps, events } = fakeDeps({
    collectInventory: async () => ({
      inventory: { containers: [{ name: 'meu-nginx', labels: {} }], volumes: [] },
      totals: { containers: 1, volumes: 0 },
    }),
  });
  const code = await runReset({ ...BASE, argv: ['--apply', '--bootstrap'], deps });

  assert.equal(code, 0);
  assert.ok(!events.includes('confirm:read'), 'sem nada a remover, não se pede confirmação');
  assert.ok(events.includes('bootstrap'), 'o bootstrap pedido não pode ser engolido');
});

test('falha parcial encerra non-zero e não sobe o ambiente', async () => {
  const { deps, events } = fakeDeps({
    removeVolume: async (name) => {
      events.push(`rm:volume:${name}`);
      return 'failed';
    },
  });
  const code = await runReset({ ...BASE, argv: ['--apply', '--bootstrap'], deps });

  assert.equal(code, 1);
  assert.ok(!events.includes('bootstrap'), 'não se sobe ambiente sobre remoção parcial');
});

test('detectNonLocalEnvironment aceita ambiente local e recusa o resto', () => {
  assert.equal(detectNonLocalEnvironment({}), null);
  assert.equal(detectNonLocalEnvironment({ NODE_ENV: 'development' }), null);
  assert.equal(detectNonLocalEnvironment({ SPRING_PROFILES_ACTIVE: 'dev' }), null);
  assert.equal(detectNonLocalEnvironment({ CI: '' }), null);
  assert.equal(detectNonLocalEnvironment({ CI: '1' }), 'CI');
  assert.equal(detectNonLocalEnvironment({ SPRING_PROFILES_ACTIVE: 'prod' }), 'SPRING_PROFILES_ACTIVE');
});

// --- P3 da rodada 2 --------------------------------------------------------

test('P3-2: recurso já ausente não conta como falha nem suprime o bootstrap', async () => {
  const { deps, events } = fakeDeps({
    removeVolume: async (name) => { events.push(`rm:volume:${name}`); return 'absent'; },
  });
  const code = await runReset({ ...BASE, argv: ['--apply', '--bootstrap'], deps });

  assert.equal(code, 0, 'convergência benigna não pode virar exit 1');
  assert.ok(events.includes('bootstrap'), 'o bootstrap não pode ser suprimido por um recurso já ausente');
});

test('P3-3: marcadores de ambiente usam o mesmo critério', () => {
  // A assimetria anterior deixava `staging-production` passar em NODE_ENV
  // e recusava o mesmo valor em BETUFU_ENV.
  for (const name of ['NODE_ENV', 'BETUFU_ENV', 'SPRING_PROFILES_ACTIVE']) {
    assert.equal(detectNonLocalEnvironment({ [name]: 'staging-production' }), name);
    assert.equal(detectNonLocalEnvironment({ [name]: 'meu-homolog' }), name);
    assert.equal(detectNonLocalEnvironment({ [name]: 'development' }), null);
  }
});

test('P3-4: falha ao adquirir o lock encerra non-zero sem agir', async () => {
  const { deps, events } = fakeDeps({
    acquireLock: async () => { throw new Error('Outra instância está rodando'); },
  });
  const code = await runReset({ ...BASE, argv: ['--apply'], deps });

  assert.equal(code, 1);
  assert.deepEqual(events, [], 'sem lock, nada pode ser inventariado nem removido');
});

test('P3-6: classifyRemoval separa sucesso, ausência e falha real', () => {
  assert.equal(classifyRemoval({ exitCode: 0, stderr: '' }), 'removed');

  // Formas reais do Docker para recurso inexistente, em inglês e português.
  for (const stderr of [
    'Error response from daemon: No such container: infra-postgres-1',
    'Error: No such volume: betufu-local-redis-data',
    'Error response from daemon: get betufu-local-x: not found',
    'Erro: volume nao encontrado',
  ]) {
    assert.equal(classifyRemoval({ exitCode: 1, stderr }), 'absent', stderr);
  }

  // Qualquer outra falha é falha: a direção desconhecida tem de ser a segura.
  for (const stderr of [
    'Error response from daemon: volume is in use',
    'permission denied',
    '',
  ]) {
    assert.equal(classifyRemoval({ exitCode: 1, stderr }), 'failed', stderr);
  }
});
