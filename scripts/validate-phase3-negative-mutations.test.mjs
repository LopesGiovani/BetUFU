import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import { runMutationCase, sha256 } from './validate-phase3-negative-mutations.mjs';

async function withFixture(run) {
  const root = await mkdtemp(join(tmpdir(), 'betufu-phase3-mutation-'));
  const path = join(root, 'fixture.txt');
  await writeFile(path, 'safe\n', 'utf8');
  try {
    await run(root, path);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

function mutation() {
  return {
    id: 'TEST',
    path: 'fixture.txt',
    mutate: (source) => source.replace('safe', 'mutated'),
    command: 'fake',
    timeoutMs: 10,
    expectedOutput: /EXPECTED/u,
  };
}

async function assertRestored(path, expectedHash) {
  const bytes = await readFile(path);
  assert.equal(bytes.toString('utf8'), 'safe\n');
  assert.equal(sha256(bytes), expectedHash);
}

test('falha se a mutacao permanece verde e restaura os bytes', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    await assert.rejects(
      runMutationCase(root, mutation(), { runner: async () => ({ code: 0, stdout: '', stderr: '' }) }),
      /MUTATION_GREEN/u,
    );
    await assertRestored(path, before);
  });
});

test('aceita somente vermelho com a assinatura esperada', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    const evidence = await runMutationCase(root, mutation(), {
      runner: async () => ({ code: 1, stdout: 'EXPECTED', stderr: '' }),
    });
    assert.equal(evidence.status, 'expected-red');
    await assertRestored(path, before);
  });
});

test('rejeita vermelho com assinatura divergente e restaura', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    await assert.rejects(
      runMutationCase(root, mutation(), { runner: async () => ({ code: 1, stdout: 'OTHER', stderr: '' }) }),
      /MUTATION_SIGNATURE/u,
    );
    await assertRestored(path, before);
  });
});

test('rejeita timeout e restaura', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    await assert.rejects(
      runMutationCase(root, mutation(), { runner: async () => ({ timedOut: true, code: null, stdout: '', stderr: '' }) }),
      /MUTATION_TIMEOUT/u,
    );
    await assertRestored(path, before);
  });
});

test('restaura mesmo quando o executor falha', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    await assert.rejects(
      runMutationCase(root, mutation(), {
        runner: async () => {
          throw new Error('runner exploded');
        },
      }),
      /runner exploded/u,
    );
    await assertRestored(path, before);
  });
});

test('recusa mutacao com ancora ambigua', async () => {
  await withFixture(async (root, path) => {
    const ambiguous = {
      ...mutation(),
      mutate: () => {
        throw new Error('[TEST] expected one anchor, found 2');
      },
    };
    await assert.rejects(
      runMutationCase(root, ambiguous, { runner: async () => ({ code: 1, stdout: 'EXPECTED', stderr: '' }) }),
      /expected one anchor/u,
    );
    assert.equal((await readFile(path)).toString('utf8'), 'safe\n');
  });
});
