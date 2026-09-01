#!/usr/bin/env node

import assert from 'node:assert/strict';
import { test } from 'node:test';
import { resolve } from 'node:path';

import {
  PROJECT_ROOT,
  loadSkillManifest,
  validateSkillManifest,
} from './agy-wave.mjs';
import {
  AGGREGATE_COMMANDS,
  REVIEW_SCHEMA,
  buildReviewerArgs,
  buildReviewerPrompt,
  captureReviewSnapshot,
  evaluateReviewGates,
  invokeReviewer,
  parseReviewArguments,
  validateReviewerProfile,
  validateReviewerResult,
} from './agy-review.mjs';

const WORKSPACE_ROOT = resolve(PROJECT_ROOT, '..');
const BASE = 'a'.repeat(40);
const HEAD = 'b'.repeat(40);
const CHANGED = '.agents/agy-review.mjs';
const BACKEND_CHANGED = 'backend/src/main/java/br/com/betufu/Example.java';
const PLAN_BACKEND_CHANGED = 'backend/src/main/java/br/com/betufu/sports/internal/athleteprofile/AthletePublicProfileService.java';
const FRONTEND_CHANGED = 'frontend/src/app/example.component.ts';
const E2E_CHANGED = 'frontend/e2e/example.spec.ts';
const OPENAPI_CHANGED = 'backend/openapi/betufu.yaml';
const HASH = 'A'.repeat(64);

function processResult(stdout = '', exitCode = 0) {
  return {
    exitCode,
    signal: null,
    stdout,
    stderr: '',
    spawnError: null,
    overflow: false,
    timedOut: false,
  };
}

function fakeGit(options = {}) {
  const calls = [];
  const runProcess = async (command, args) => {
    calls.push({ command, args });
    assert.equal(command, 'git');
    const operation = args.slice(2).join(' ');
    if (operation === 'rev-parse --show-toplevel') return processResult(PROJECT_ROOT);
    if (operation === 'rev-parse --git-common-dir') return processResult('.git');
    if (operation === 'rev-parse HEAD') return processResult(options.head ?? HEAD);
    if (operation === 'symbolic-ref --short HEAD') return processResult(options.branch ?? 'worktree-process-improvements');
    if (operation === 'status --porcelain=v1 -z --untracked-files=all') {
      return processResult(options.status ?? '');
    }
    if (operation.startsWith('diff --name-only ')) {
      return processResult(options.changedOutput ?? `${CHANGED}\n`);
    }
    if (operation.startsWith('diff --check ')) {
      return processResult(options.diffCheck ?? '', options.diffCheckExitCode ?? 0);
    }
    if (operation.startsWith('merge-base --is-ancestor ')) {
      return processResult('', options.ancestorExitCode ?? 0);
    }
    throw new Error(`unexpected git operation: ${operation}`);
  };
  return { calls, runProcess };
}

async function manifest() {
  return loadSkillManifest(PROJECT_ROOT);
}

async function profile(reviewType = 'verification') {
  const value = (await manifest()).reviewer_profiles[reviewType];
  validateReviewerProfile(value, reviewType);
  return value;
}

function planFixture(filesModified = [CHANGED]) {
  return {
    filesModified,
    tasks: [{ verificationCommands: ['node --test .agents/agy-review.test.mjs'] }],
  };
}

function snapshotFixture(overrides = {}) {
  return {
    projectRoot: PROJECT_ROOT,
    worktree: PROJECT_ROOT,
    expectedBase: BASE,
    expectedHead: HEAD,
    head: HEAD,
    branch: 'worktree-process-improvements',
    statusPorcelain: '',
    dirtyFiles: [],
    changedFiles: [CHANGED],
    fileHashes: { [CHANGED]: HASH },
    diffCheck: '',
    diffCheckExitCode: 0,
    diffCheckResultSummary: '',
    baseIsAncestor: true,
    commonGitDirectory: resolve(PROJECT_ROOT, '.git'),
    ...overrides,
  };
}

test('perfis de revisao sao plan/read-only e exigem gates', async () => {
  const loaded = await manifest();
  assert.equal(validateSkillManifest(loaded), true);
  for (const [name, value] of Object.entries(loaded.reviewer_profiles)) {
    assert.equal(validateReviewerProfile(value, name), true);
    assert.throws(
      () => validateReviewerProfile({ ...value, readonly: false }, name),
      /plan\/read-only/u,
    );
    assert.throws(
      () => validateReviewerProfile({ ...value, required_gates: [] }, name),
      /sem gates obrigatorios/u,
    );
  }
});

test('manifesto falha cedo quando required_gates do perfil esta ausente', async () => {
  const loaded = structuredClone(await manifest());
  delete loaded.reviewer_profiles.security.required_gates;
  assert.throws(() => validateSkillManifest(loaded), /required_gates/u);
});

test('argumentos do revisor usam modo plan, sandbox e schema local', () => {
  const args = buildReviewerArgs(PROJECT_ROOT, 'PROMPT', 1_000);
  assert.equal(args[args.indexOf('--mode') + 1], 'plan');
  assert.ok(args.includes('--sandbox'));
  assert.equal(args[args.indexOf('--json-schema') + 1], REVIEW_SCHEMA);
  assert.ok(!args.includes('--yolo'));
});

test('snapshot limpo captura base, HEAD, diff e hashes', async () => {
  const fake = fakeGit();
  const snapshot = await captureReviewSnapshot({
    projectRoot: PROJECT_ROOT,
    worktree: PROJECT_ROOT,
    workspaceRoot: WORKSPACE_ROOT,
    expectedBase: BASE,
    expectedHead: HEAD,
  }, { runProcess: fake.runProcess });

  assert.deepEqual(snapshot.dirtyFiles, []);
  assert.deepEqual(snapshot.changedFiles, [CHANGED]);
  assert.equal(snapshot.head, HEAD);
  assert.equal(snapshot.baseIsAncestor, true);
  assert.match(snapshot.fileHashes[CHANGED], /^[0-9A-F]{64}$/u);
});

test('snapshot aceita separadores de caminho diferentes entre Git e Windows', async () => {
  const fake = fakeGit();
  const original = fake.runProcess;
  fake.runProcess = async (command, args) => {
    const result = await original(command, args);
    if (command === 'git' && args.slice(2).join(' ') === 'rev-parse --show-toplevel') {
      return { ...result, stdout: PROJECT_ROOT.replaceAll('\\', '/') };
    }
    return result;
  };
  const snapshot = await captureReviewSnapshot({
    projectRoot: PROJECT_ROOT,
    worktree: PROJECT_ROOT,
    workspaceRoot: WORKSPACE_ROOT,
    expectedBase: BASE,
    expectedHead: HEAD,
  }, { runProcess: fake.runProcess });
  assert.equal(snapshot.head, HEAD);
});

test('worktree suja bloqueia o gate worktree-clean', async () => {
  const fake = fakeGit({ status: ` M ${CHANGED}\0` });
  const snapshot = await captureReviewSnapshot({
    projectRoot: PROJECT_ROOT,
    worktree: PROJECT_ROOT,
    workspaceRoot: WORKSPACE_ROOT,
    expectedBase: BASE,
    expectedHead: HEAD,
  }, { runProcess: fake.runProcess });
  const gates = evaluateReviewGates({
    reviewType: 'verification',
    profile: await profile(),
    plan: planFixture(),
    snapshot,
    evidence: {},
  });
  assert.equal(gates.find((item) => item.id === 'worktree-clean').status, 'BLOCKED');
  assert.deepEqual(snapshot.dirtyFiles, [CHANGED]);
});

test('HEAD divergente ou base nao ancestral bloqueia o gate head', async () => {
  const snapshot = snapshotFixture({ head: 'c'.repeat(40), baseIsAncestor: false });
  const gates = evaluateReviewGates({
    reviewType: 'security',
    profile: await profile('security'),
    plan: planFixture(),
    snapshot,
    evidence: { securityReview: 'PASS' },
  });
  assert.equal(gates.find((item) => item.id === 'head').status, 'BLOCKED');
});

test('falha de diff-check bloqueia o gate e preserva a evidencia', async () => {
  const fake = fakeGit({ diffCheck: 'trailing whitespace', diffCheckExitCode: 2 });
  const snapshot = await captureReviewSnapshot({
    projectRoot: PROJECT_ROOT,
    worktree: PROJECT_ROOT,
    workspaceRoot: WORKSPACE_ROOT,
    expectedBase: BASE,
    expectedHead: HEAD,
  }, { runProcess: fake.runProcess });
  const gates = evaluateReviewGates({
    reviewType: 'security',
    profile: await profile('security'),
    plan: planFixture(),
    snapshot,
    evidence: { securityReview: 'PASS' },
  });
  const diffGate = gates.find((item) => item.id === 'diff-check');
  assert.equal(diffGate.status, 'BLOCKED');
  assert.match(diffGate.detail, /exit code 2/u);
});

test('alteracao fora da allowlist bloqueia o gate scope', async () => {
  const snapshot = snapshotFixture({ changedFiles: [BACKEND_CHANGED] });
  const gates = evaluateReviewGates({
    reviewType: 'verification',
    profile: await profile(),
    plan: planFixture(),
    snapshot,
    evidence: {},
  });
  assert.equal(gates.find((item) => item.id === 'scope').status, 'BLOCKED');
});

test('ausencia de evidencia de teste bloqueia o gate test-evidence', async () => {
  const gates = evaluateReviewGates({
    reviewType: 'verification',
    profile: await profile(),
    plan: planFixture(),
    snapshot: snapshotFixture(),
    evidence: {},
  });
  assert.equal(gates.find((item) => item.id === 'test-evidence').status, 'BLOCKED');
});

test('teste focal sem discriminates true nao certifica a mudanca', async () => {
  const gates = evaluateReviewGates({
    reviewType: 'verification',
    profile: await profile(),
    plan: planFixture(),
    snapshot: snapshotFixture(),
    evidence: {
      tests: [{
        command: 'node --test .agents/agy-review.test.mjs',
        status: 'PASS',
        kind: 'focal',
        discriminates: false,
      }],
    },
  });
  assert.equal(gates.find((item) => item.id === 'test-evidence').status, 'BLOCKED');
});

test('hashes OpenAPI divergentes bloqueiam o contrato', async () => {
  const gates = evaluateReviewGates({
    reviewType: 'security',
    profile: await profile('security'),
    plan: planFixture([OPENAPI_CHANGED]),
    snapshot: snapshotFixture({ changedFiles: [OPENAPI_CHANGED] }),
    evidence: {
      securityReview: 'PASS',
      openapi: {
        governedBefore: HASH,
        governedAfter: 'B'.repeat(64),
        digest: HASH,
      },
    },
  });
  assert.equal(gates.find((item) => item.id === 'openapi-contract').status, 'BLOCKED');
});

test('agregado backend ausente bloqueia a revisao de mudanca Java', async () => {
  const gates = evaluateReviewGates({
    reviewType: 'security',
    profile: await profile('security'),
    plan: planFixture([BACKEND_CHANGED]),
    snapshot: snapshotFixture({ changedFiles: [BACKEND_CHANGED] }),
    evidence: { securityReview: 'PASS' },
  });
  const aggregate = gates.find((item) => item.id === 'aggregate-backend');
  assert.equal(aggregate.status, 'BLOCKED');
  assert.match(aggregate.detail, /evidencia ausente/u);
});

test('mudanca Java exige agregados backend e Node', async () => {
  const gates = evaluateReviewGates({
    reviewType: 'security',
    profile: await profile('security'),
    plan: planFixture([BACKEND_CHANGED]),
    snapshot: snapshotFixture({ changedFiles: [BACKEND_CHANGED] }),
    evidence: {
      securityReview: 'PASS',
      aggregate: [{ command: AGGREGATE_COMMANDS.backend, status: 'PASS' }],
    },
  });
  assert.equal(gates.find((item) => item.id === 'aggregate-backend').status, 'PASS');
  assert.equal(gates.find((item) => item.id === 'aggregate-node').status, 'BLOCKED');
});

test('mudanca Angular exige backend, Node e Angular', async () => {
  const gates = evaluateReviewGates({
    reviewType: 'ui',
    profile: await profile('ui'),
    plan: planFixture([FRONTEND_CHANGED]),
    snapshot: snapshotFixture({ changedFiles: [FRONTEND_CHANGED] }),
    evidence: {
      uiReview: 'PASS',
      aggregate: [
        { command: AGGREGATE_COMMANDS.backend, status: 'PASS' },
        { command: AGGREGATE_COMMANDS.node, status: 'PASS' },
      ],
    },
  });
  assert.equal(gates.find((item) => item.id === 'aggregate-backend').status, 'PASS');
  assert.equal(gates.find((item) => item.id === 'aggregate-node').status, 'PASS');
  assert.equal(gates.find((item) => item.id === 'aggregate-angular').status, 'BLOCKED');
});

test('mudanca E2E exige Playwright alem dos agregados de aplicacao', async () => {
  const gates = evaluateReviewGates({
    reviewType: 'ui',
    profile: await profile('ui'),
    plan: planFixture([E2E_CHANGED]),
    snapshot: snapshotFixture({ changedFiles: [E2E_CHANGED] }),
    evidence: {
      uiReview: 'PASS',
      aggregate: [
        { command: AGGREGATE_COMMANDS.backend, status: 'PASS' },
        { command: AGGREGATE_COMMANDS.node, status: 'PASS' },
        { command: AGGREGATE_COMMANDS.angular, status: 'PASS' },
      ],
    },
  });
  assert.equal(gates.find((item) => item.id === 'aggregate-playwright').status, 'BLOCKED');
});

test('invokeReviewer nao chama agy quando um gate previo esta bloqueado', async () => {
  const fake = fakeGit({ changedOutput: `${PLAN_BACKEND_CHANGED}\n` });
  const result = await assert.rejects(
    invokeReviewer({
      allowExternalCall: true,
      reviewType: 'security',
      planId: '03-14',
      projectRoot: PROJECT_ROOT,
      worktree: PROJECT_ROOT,
      workspaceRoot: WORKSPACE_ROOT,
      expectedBase: BASE,
      expectedHead: HEAD,
      evidence: { securityReview: 'PASS' },
    }, { runProcess: fake.runProcess }),
    /gates previos bloqueados.*aggregate-backend/u,
  );
  assert.equal(result, undefined);
  assert.equal(fake.calls.filter((call) => call.command === 'agy').length, 0);
});

test('invokeReviewer recusa chamada externa sem aprovacao explicita', async () => {
  await assert.rejects(
    invokeReviewer({ allowExternalCall: false }),
    /allowExternalCall=true/u,
  );
});

test('resultado do revisor divergente do snapshot e rejeitado', async () => {
  const snapshot = snapshotFixture();
  assert.throws(
    () => validateReviewerResult({
      status: 'BLOCKED',
      reviewer: 'verification',
      plan_id: '03-15',
      base: BASE,
      head: HEAD,
      changed_files: ['outro-arquivo.txt'],
      gates: [{ id: 'scope', status: 'PASS', detail: 'ok' }],
      findings: [],
      summary: 'bloqueado',
    }, 'verification', '03-15', snapshot),
    /changed_files.*diverge/u,
  );
});

test('resultado PASS com gate bloqueado e rejeitado', async () => {
  const snapshot = snapshotFixture();
  assert.throws(
    () => validateReviewerResult({
      status: 'PASS',
      reviewer: 'verification',
      plan_id: '03-15',
      base: BASE,
      head: HEAD,
      changed_files: [CHANGED],
      gates: [{ id: 'scope', status: 'BLOCKED', detail: 'fora da allowlist' }],
      findings: [],
      summary: 'pass',
    }, 'verification', '03-15', snapshot),
    /gate bloqueado/u,
  );
});

test('resultado do revisor precisa manter paridade com gates coordenados', () => {
  const snapshot = snapshotFixture();
  const baseResult = {
    status: 'PASS',
    reviewer: 'verification',
    plan_id: '03-15',
    base: BASE,
    head: HEAD,
    changed_files: [CHANGED],
    gates: [{ id: 'scope', status: 'PASS', detail: 'ok' }],
    findings: [],
    summary: 'pass',
  };
  assert.throws(
    () => validateReviewerResult(baseResult, 'verification', '03-15', snapshot, [
      { id: 'scope', status: 'PASS', detail: 'ok' },
      { id: 'diff-check', status: 'PASS', detail: 'ok' },
    ]),
    /gates do revisor divergem/u,
  );
  assert.throws(
    () => validateReviewerResult({
      ...baseResult,
      gates: [
        { id: 'scope', status: 'PASS', detail: 'ok' },
        { id: 'unexpected', status: 'PASS', detail: 'nao coordenado' },
      ],
    }, 'verification', '03-15', snapshot, [
      { id: 'scope', status: 'PASS', detail: 'ok' },
    ]),
    /gates do revisor divergem/u,
  );
  assert.throws(
    () => validateReviewerResult({
      ...baseResult,
      gates: [
        { id: 'scope', status: 'PASS', detail: 'ok' },
        { id: 'scope', status: 'PASS', detail: 'duplicado' },
      ],
    }, 'verification', '03-15', snapshot),
    /gates duplicados/u,
  );
});

test('CLI do revisor separa prepare de invoke e exige aprovacao externa', () => {
  const common = [
    '--reviewer', 'verification', '--plan', '03-14', '--worktree', PROJECT_ROOT,
    '--workspace-root', WORKSPACE_ROOT, '--expected-base', BASE, '--expected-head', HEAD,
  ];
  assert.deepEqual(parseReviewArguments(['--prepare', ...common]), {
    mode: 'prepare',
    reviewType: 'verification',
    planId: '03-14',
    worktree: PROJECT_ROOT,
    workspaceRoot: WORKSPACE_ROOT,
    expectedBase: BASE,
    expectedHead: HEAD,
  });
  assert.throws(
    () => parseReviewArguments(['--invoke', ...common]),
    /--invoke exige --allow-external-call/u,
  );
  assert.throws(
    () => parseReviewArguments(['--prepare', '--allow-external-call', ...common]),
    /so pode ser usado com --invoke/u,
  );
});

test('prompt do revisor explicita modo read-only e snapshot congelado', async () => {
  const value = await profile('security');
  const prompt = buildReviewerPrompt(value, snapshotFixture(), 'security');
  assert.match(prompt, /SOMENTE LEITURA/u);
  assert.match(prompt, /nao edite arquivos/iu);
  assert.match(prompt, /<review_snapshot>/u);
});
