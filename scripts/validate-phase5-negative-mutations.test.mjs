import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import {
  MUTATIONS,
  certifyRestoredBuild,
  defaultProjectRoot,
  replaceUnique,
  runCommand,
  runCertifiedMutationSuite,
  runMutationCase,
  runMutationSuite,
  sha256,
} from './validate-phase5-negative-mutations.mjs';

async function withFixture(action) {
  const root = await mkdtemp(join(tmpdir(), 'betufu-phase5-mutation-'));
  const path = join(root, 'fixture.txt');
  await writeFile(path, 'safe\n', 'utf8');
  try {
    await action(root, path);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

function fixtureMutation() {
  return {
    id: 'TEST',
    path: 'fixture.txt',
    mutate: (source) => source.replace('safe', 'mutated'),
    command: 'fake-command',
    expectedOutput: /EXPECTED/u,
    timeoutMs: 100,
  };
}

test('aceita somente vermelho discriminante e restaura os bytes', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    const result = await runMutationCase(
      root,
      fixtureMutation(),
      async () => ({ code: 1, stdout: 'EXPECTED', stderr: '' }),
    );
    assert.equal(result.status, 'expected-red');
    assert.equal(sha256(await readFile(path)), before);
  });
});

test('rejeita mutacao que permanece verde e restaura os bytes', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    await assert.rejects(
      runMutationCase(
        root,
        fixtureMutation(),
        async () => ({ code: 0, stdout: '', stderr: '' }),
      ),
      /MUTATION_GREEN/u,
    );
    assert.equal(sha256(await readFile(path)), before);
  });
});

test('rejeita assinatura vermelha incorreta e ancora ambigua', async () => {
  await withFixture(async (root, path) => {
    await assert.rejects(
      runMutationCase(
        root,
        fixtureMutation(),
        async () => ({ code: 1, stdout: 'OTHER', stderr: '' }),
      ),
      /MUTATION_SIGNATURE/u,
    );
    assert.throws(() => replaceUnique('x x', 'x', 'y', 'TEST'), /expected one anchor/u);
    assert.equal((await readFile(path, 'utf8')), 'safe\n');
  });
});

test('timeout encerra toda a arvore antes que um descendente escreva', async () => {
  await withFixture(async (root) => {
    const helper = join(root, 'spawn-delayed-writer.mjs');
    const marker = join(root, 'late-write.txt');
    await writeFile(
      helper,
      `import { spawn } from 'node:child_process';
const marker = process.argv[2];
const delayed = \`setTimeout(() => require('node:fs').writeFileSync(\${JSON.stringify(marker)}, 'late'), 600)\`;
spawn(process.execPath, ['-e', delayed], { stdio: 'ignore' });
setInterval(() => {}, 1000);
`,
      'utf8',
    );

    const result = await runCommand(
      `"${process.execPath}" "${helper}" "${marker}"`,
      root,
      100,
    );

    assert.equal(result.timedOut, true);
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 900));
    await assert.rejects(readFile(marker, 'utf8'), { code: 'ENOENT' });
  });
});

test('timeout nunca e aceito como vermelho discriminante', async () => {
  await withFixture(async (root, path) => {
    await assert.rejects(
      runMutationCase(
        root,
        fixtureMutation(),
        async () => ({
          code: 1,
          stdout: 'EXPECTED',
          stderr: '',
          timedOut: true,
        }),
      ),
      /MUTATION_TIMEOUT/u,
    );
    assert.equal(await readFile(path, 'utf8'), 'safe\n');
  });
});

test('recusa suite vazia', async () => {
  await assert.rejects(runMutationSuite(defaultProjectRoot, []), /Nenhuma mutacao/u);
});

test('certifica build verde depois de restaurar fonte e bytecode', async () => {
  const result = await certifyRestoredBuild(
    defaultProjectRoot,
    async () => ({ code: 0, stdout: 'BUILD SUCCESS', stderr: '' }),
  );
  assert.equal(result.status, 'restored-green');
});

test('rejeita bytecode restaurado que nao volta ao verde', async () => {
  await assert.rejects(
    certifyRestoredBuild(
      defaultProjectRoot,
      async () => ({ code: 1, stdout: 'BUILD FAILURE', stderr: '' }),
    ),
    /RESTORED_BUILD_FAILED/u,
  );
});

test('restaura o build mesmo quando uma mutacao falha', async () => {
  await withFixture(async (root, path) => {
    let restoreCalls = 0;
    await assert.rejects(
      runCertifiedMutationSuite(root, [fixtureMutation()], {
        runner: async () => ({ code: 0, stdout: 'BUILD SUCCESS', stderr: '' }),
        restoreRunner: async () => {
          restoreCalls += 1;
          return { code: 0, stdout: 'BUILD SUCCESS', stderr: '' };
        },
      }),
      /MUTATION_GREEN/u,
    );
    assert.equal(restoreCalls, 1);
    assert.equal(await readFile(path, 'utf8'), 'safe\n');
  });
});

test('catalogo possui metadados unicos e todas as ancoras alteram fontes reais', async () => {
  assert.equal(MUTATIONS.length, 7);
  const ids = new Set();
  for (const mutation of MUTATIONS) {
    assert.ok(mutation.threatId);
    assert.ok(!ids.has(mutation.id), `id duplicado: ${mutation.id}`);
    ids.add(mutation.id);
    const original = await readFile(join(defaultProjectRoot, mutation.path), 'utf8');
    assert.notEqual(mutation.mutate(original), original, mutation.id);
    assert.ok(mutation.expectedOutput instanceof RegExp);
    assert.equal(
      mutation.reportPath,
      'backend/target/surefire-reports/TEST-br.com.betufu.phase5.Phase5CertificationIT.xml',
    );
    assert.ok(mutation.expectedReport instanceof RegExp);
    assert.ok(mutation.timeoutMs > 0);
  }
});
