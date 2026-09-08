import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

import {
  MUTATIONS,
  defaultProjectRoot,
  mutateAlterEditionRegression,
  mutateAlterKFactorThreshold,
  mutateAlterMarginRounding,
  mutateIncludeNumericWalkover,
  mutateInsertBettingVolume,
  replaceUniqueRegex,
  runMutationCase,
  runMutationSuite,
  sha256,
} from './validate-odds-negative-mutations.mjs';

async function withFixture(run) {
  const root = await mkdtemp(join(tmpdir(), 'betufu-odds-mutation-'));
  const path = join(root, 'fixture.txt');
  await writeFile(path, 'safe\n', 'utf8');
  try {
    await run(root, path);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

function dummyMutation() {
  return {
    id: 'TEST',
    name: 'cenario_teste',
    threatId: 'T-04-TEST',
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
      runMutationCase(root, dummyMutation(), {
        runner: async () => ({ code: 0, stdout: '', stderr: '' }),
      }),
      /MUTATION_GREEN/u,
    );
    await assertRestored(path, before);
  });
});

test('aceita somente vermelho com a assinatura esperada', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    const evidence = await runMutationCase(root, dummyMutation(), {
      runner: async () => ({ code: 1, stdout: 'EXPECTED', stderr: '' }),
    });
    assert.equal(evidence.status, 'expected-red');
    assert.equal(evidence.id, 'TEST');
    assert.equal(evidence.name, 'cenario_teste');
    await assertRestored(path, before);
  });
});

test('rejeita vermelho com assinatura divergente e restaura', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    await assert.rejects(
      runMutationCase(root, dummyMutation(), {
        runner: async () => ({ code: 1, stdout: 'OTHER', stderr: '' }),
      }),
      /MUTATION_SIGNATURE/u,
    );
    await assertRestored(path, before);
  });
});

test('rejeita timeout e restaura', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    await assert.rejects(
      runMutationCase(root, dummyMutation(), {
        runner: async () => ({ timedOut: true, code: null, stdout: '', stderr: '' }),
      }),
      /MUTATION_TIMEOUT/u,
    );
    await assertRestored(path, before);
  });
});

test('restaura mesmo quando o executor falha', async () => {
  await withFixture(async (root, path) => {
    const before = sha256(await readFile(path));
    await assert.rejects(
      runMutationCase(root, dummyMutation(), {
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
      ...dummyMutation(),
      mutate: () => {
        throw new Error('[TEST] expected one anchor, found 2');
      },
    };
    await assert.rejects(
      runMutationCase(root, ambiguous, {
        runner: async () => ({ code: 1, stdout: 'EXPECTED', stderr: '' }),
      }),
      /expected one anchor/u,
    );
    assert.equal((await readFile(path)).toString('utf8'), 'safe\n');
  });
});

test('runMutationSuite falha quando lista de mutacoes e vazia', async () => {
  await assert.rejects(
    runMutationSuite(defaultProjectRoot, []),
    /Nenhuma mutacao definida no harness/u,
  );
});

test('runMutationSuite falha quando nenhum sinal vermelho e executado com sucesso', async () => {
  await withFixture(async (root, path) => {
    const failingMutation = {
      ...dummyMutation(),
      expectedOutput: /NEVER_MATCH/u,
    };
    await assert.rejects(
      runMutationSuite(root, [failingMutation], {
        runner: async () => ({ code: 1, stdout: 'UNEXPECTED', stderr: '' }),
      }),
      /(?:Harness falhou|Nenhum sinal vermelho valido)/u,
    );
  });
});

test('runMutationSuite executa com sucesso todos os cenarios com runner mockado', async () => {
  await withFixture(async (root, path) => {
    const mockMutation = dummyMutation();
    const results = await runMutationSuite(root, [mockMutation], {
      runner: async () => ({ code: 1, stdout: 'EXPECTED', stderr: '' }),
    });
    assert.equal(results.length, 1);
    assert.equal(results[0].status, 'expected-red');
  });
});

test('cenario nomeado M_WO_NUMERIC: detecta inclusao indevida de W.O. numerico no filtro', async () => {
  const item = MUTATIONS.find((m) => m.id === 'M_WO_NUMERIC');
  assert.ok(item, 'M_WO_NUMERIC deve existir no catalogo de mutacoes');
  assert.equal(item.name, 'inclusao_wo_numerico');
  assert.equal(item.threatId, 'T-04-TAXONOMY');
  assert.equal(item.path, 'backend/src/main/java/br/com/betufu/odds/internal/model/OddsInputFilter.java');

  const filePath = join(defaultProjectRoot, item.path);
  const original = await readFile(filePath, 'utf8');
  const mutated = mutateIncludeNumericWalkover(original);
  assert.notEqual(mutated, original);
  assert.ok(mutated.includes('ADMINISTRATIVE_WALKOVER'));
});

test('cenario nomeado M_VOLUME: detecta insercao de volume de apostas no kernel', async () => {
  const item = MUTATIONS.find((m) => m.id === 'M_VOLUME');
  assert.ok(item, 'M_VOLUME deve existir no catalogo de mutacoes');
  assert.equal(item.name, 'insercao_volume_apostas');
  assert.equal(item.threatId, 'T-04-VOLUME');
  assert.equal(item.path, 'backend/src/main/java/br/com/betufu/odds/internal/model/OddsPricingKernel.java');

  const filePath = join(defaultProjectRoot, item.path);
  const original = await readFile(filePath, 'utf8');
  const mutated = mutateInsertBettingVolume(original);
  assert.notEqual(mutated, original);
  assert.ok(mutated.includes('volume\\":100000'));
});

test('cenario nomeado M_K_FACTOR: detecta alteracao indevida do limiar K do Elo', async () => {
  const item = MUTATIONS.find((m) => m.id === 'M_K_FACTOR');
  assert.ok(item, 'M_K_FACTOR deve existir no catalogo de mutacoes');
  assert.equal(item.name, 'alteracao_limiar_k');
  assert.equal(item.threatId, 'T-04-CALIBRATION');
  assert.equal(item.path, 'backend/src/main/java/br/com/betufu/odds/internal/model/EloRatingEngine.java');

  const filePath = join(defaultProjectRoot, item.path);
  const original = await readFile(filePath, 'utf8');
  const mutated = mutateAlterKFactorThreshold(original);
  assert.notEqual(mutated, original);
  assert.ok(mutated.includes('BigDecimal("16")'));
});

test('cenario nomeado M_REGRESSION: detecta alteracao da regressao de edicao no Elo', async () => {
  const item = MUTATIONS.find((m) => m.id === 'M_REGRESSION');
  assert.ok(item, 'M_REGRESSION deve existir no catalogo de mutacoes');
  assert.equal(item.name, 'alteracao_regressao_edicao');
  assert.equal(item.threatId, 'T-04-CALIBRATION');
  assert.equal(item.path, 'backend/src/main/java/br/com/betufu/odds/internal/model/EloRatingEngine.java');

  const filePath = join(defaultProjectRoot, item.path);
  const original = await readFile(filePath, 'utf8');
  const mutated = mutateAlterEditionRegression(original);
  assert.notEqual(mutated, original);
  assert.ok(mutated.includes('BigDecimal("0.0")'));
});

test('cenario nomeado M_ROUNDING: detecta alteracao das casas decimais / arredondamento da margem', async () => {
  const item = MUTATIONS.find((m) => m.id === 'M_ROUNDING');
  assert.ok(item, 'M_ROUNDING deve existir no catalogo de mutacoes');
  assert.equal(item.name, 'alteracao_arredondamento_margem');
  assert.equal(item.threatId, 'T-04-CALIBRATION');
  assert.equal(item.path, 'backend/src/main/java/br/com/betufu/odds/internal/model/MarginCalculator.java');

  const filePath = join(defaultProjectRoot, item.path);
  const original = await readFile(filePath, 'utf8');
  const mutated = mutateAlterMarginRounding(original);
  assert.notEqual(mutated, original);
  assert.ok(mutated.includes('DECIMAL_PLACES = 4'));
});

test('todas as mutacoes declaradas possuem ancora unica, threatId, metadados validos e alteram o codigo real', async () => {
  assert.ok(MUTATIONS.length >= 5, 'espera-se pelo menos 5 mutacoes de modelo');
  const ids = new Set();
  const names = new Set();

  for (const item of MUTATIONS) {
    assert.ok(item.id, 'mutacao deve ter id');
    assert.ok(item.name, `[${item.id}] mutacao deve ter nome descritivo`);
    assert.ok(item.threatId, `[${item.id}] mutacao deve ter threatId vinculado`);
    assert.ok(!ids.has(item.id), `[${item.id}] id duplicado`);
    assert.ok(!names.has(item.name), `[${item.name}] name duplicado`);
    ids.add(item.id);
    names.add(item.name);

    const filePath = join(defaultProjectRoot, item.path);
    const original = await readFile(filePath, 'utf8');
    const mutated = item.mutate(original);
    assert.notEqual(mutated, original, `[${item.id}] mutacao deve modificar o arquivo`);
    assert.ok(item.expectedOutput instanceof RegExp, `[${item.id}] expectedOutput deve ser RegExp`);
    assert.ok(item.timeoutMs > 0, `[${item.id}] timeoutMs deve ser positivo`);
    assert.ok(item.command.length > 0, `[${item.id}] command deve ser preenchido`);
  }
});
