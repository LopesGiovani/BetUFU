import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdir, mkdtemp, readFile, rm, writeFile, copyFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';

import { validatePhase7Approvals } from './validate-phase7-approvals.mjs';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const phase = '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto';
const head = 'a'.repeat(40);
const hash = (value) => createHash('sha256').update(value).digest('hex');
const focal = 'node --test scripts/example.test.mjs';
const aggregate = [
  'node scripts/maven.mjs -f backend/pom.xml verify',
  'node --test scripts/*.test.mjs',
];

async function fixture(t, { plan = '07-01', gate = false, candidates = false } = {}) {
  const root = await mkdtemp(join(tmpdir(), 'betufu-approval-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  async function put(path, value) {
    await mkdir(dirname(join(root, path)), { recursive: true });
    await writeFile(join(root, path), typeof value === 'string' ? value : JSON.stringify(value));
  }
  const approvalPath = `docs/phase7-approvals/${plan}.json`;
  const candidate = `${phase}/${plan}-candidate/schema.sql`;
  const paths = [approvalPath, 'scripts/example.mjs', ...(candidates ? [candidate] : [])];
  const commands = [focal, ...aggregate];
  const planSource = `---\nplan: '${plan.slice(3)}'\nfiles_modified:\n${paths.map((p) => `  - ${p}`).join('\n')}\nautonomous: false\n---\n<task type="auto"><files>${paths.slice(1).join(', ')}</files><verify><automated>${focal}</automated></verify></task>\n`;
  const decisions = {
    schema_version: 1,
    plan_gates: { [plan]: gate ? ['ui'] : [] },
    gates: {
      ui: { status: 'pending', source: 'owner', human_reference: null, evidence_hashes: {} },
    },
  };
  const approval = {
    schema_version: 1,
    plan,
    status: 'approved',
    authorized_stage: 'implementation',
    head,
    plan_sha256: hash(planSource),
    decisions_sha256: hash(JSON.stringify(decisions)),
    human_approval: {
      source: 'owner',
      status: 'approved',
      reference: 'conversation:wave1',
      statement: 'Execute this plan',
      plans: [plan],
    },
    scope: { paths, commands },
    baseline_hashes: { 'docs/protected.md': hash('preserved') },
    candidate_hashes: candidates ? { [candidate]: hash('candidate SQL') } : {},
  };
  await put(`${phase}/${plan}-PLAN.md`, planSource);
  await put('docs/phase7-decisions.json', decisions);
  await put('docs/protected.md', 'preserved');
  if (candidates) await put(candidate, 'candidate SQL');
  await put(approvalPath, approval);
  return {
    root,
    plan,
    approval,
    decisions,
    put,
    approvalPath,
    candidate,
    run: (overrides = {}) =>
      validatePhase7Approvals({
        projectRoot: root,
        plan,
        stage: 'implementation',
        currentHead: head,
        ...overrides,
      }),
    save: () => put(approvalPath, approval),
    saveDecisions: async () => {
      await put('docs/phase7-decisions.json', decisions);
      approval.decisions_sha256 = hash(JSON.stringify(decisions));
      await put(approvalPath, approval);
    },
  };
}

test('approved independent foundation passes with unrelated UI gate pending', async (t) => {
  const f = await fixture(t);
  assert.equal((await f.run()).status, 'approved');
});

for (const status of ['pending', 'revoked', 'rejected', 'passed', null]) {
  test(`approval ${status} blocks execution`, async (t) => {
    const f = await fixture(t);
    f.approval.status = status;
    await f.save();
    await assert.rejects(f.run(), { rule: 'APPROVAL_STATUS' });
  });
}

test('missing real approval blocks', async (t) => {
  const f = await fixture(t);
  await rm(join(f.root, f.approvalPath));
  await assert.rejects(f.run(), { rule: 'APPROVAL_FILE' });
});

test('wrong required stage and unknown stage block', async (t) => {
  const f = await fixture(t);
  await assert.rejects(f.run({ stage: 'prepare-candidates' }), { rule: 'APPROVAL_STAGE' });
  await assert.rejects(f.run({ stage: 'deploy' }), { rule: 'STAGE' });
});

test('stale HEAD blocks', async (t) => {
  const f = await fixture(t);
  await assert.rejects(f.run({ currentHead: 'b'.repeat(40) }), { rule: 'APPROVAL_HEAD' });
});

test('another plan approval cannot be reused', async (t) => {
  const f = await fixture(t);
  f.approval.plan = '07-02';
  await f.save();
  await assert.rejects(f.run(), { rule: 'APPROVAL_PLAN' });
});

for (const field of ['reference', 'statement']) {
  test(`human ${field} is mandatory`, async (t) => {
    const f = await fixture(t);
    f.approval.human_approval[field] = '';
    await f.save();
    await assert.rejects(f.run(), { rule: 'HUMAN_APPROVAL' });
  });
}

test('human scope and revocation are checked independently', async (t) => {
  const f = await fixture(t);
  f.approval.human_approval.plans = ['07-02'];
  await f.save();
  await assert.rejects(f.run(), { rule: 'HUMAN_APPROVAL' });
  f.approval.human_approval.plans = ['07-01'];
  f.approval.human_approval.status = 'revoked';
  await f.save();
  await assert.rejects(f.run(), { rule: 'HUMAN_APPROVAL' });
});

for (const target of ['approval', 'human']) {
  test(`synthetic ${target} never authorizes a real consumer`, async (t) => {
    const f = await fixture(t);
    if (target === 'human') f.approval.human_approval.source = 'fixture';
    else f.approval.fixture = true;
    await f.save();
    await assert.rejects(f.run(), { rule: 'HUMAN_APPROVAL' });
  });
}

for (const change of ['extra', 'missing', 'duplicate']) {
  test(`${change} allowlist path blocks`, async (t) => {
    const f = await fixture(t);
    if (change === 'extra') f.approval.scope.paths.push('backend/pom.xml');
    if (change === 'missing') f.approval.scope.paths.pop();
    if (change === 'duplicate') f.approval.scope.paths.push(f.approval.scope.paths[0]);
    await f.save();
    await assert.rejects(f.run(), { rule: 'APPROVAL_PATHS' });
  });
}

test('unapproved or missing command blocks', async (t) => {
  const f = await fixture(t);
  f.approval.scope.commands.push('git push');
  await f.save();
  await assert.rejects(f.run(), { rule: 'APPROVAL_COMMANDS' });
  f.approval.scope.commands = [focal];
  await f.save();
  await assert.rejects(f.run(), { rule: 'APPROVAL_COMMANDS' });
});

for (const target of ['plan', 'decisions', 'baseline', 'candidate']) {
  test(`altered ${target} digest blocks`, async (t) => {
    const f = await fixture(t, { candidates: true });
    const path = {
      plan: `${phase}/07-01-PLAN.md`,
      decisions: 'docs/phase7-decisions.json',
      baseline: 'docs/protected.md',
      candidate: f.candidate,
    }[target];
    await f.put(path, `${await readFile(join(f.root, path), 'utf8')} `);
    await assert.rejects(f.run(), { rule: 'DIGEST_MISMATCH' });
  });
}

test('candidate approval must cover all candidate files', async (t) => {
  const f = await fixture(t, { candidates: true });
  f.approval.candidate_hashes = {};
  await f.save();
  await assert.rejects(f.run(), { rule: 'CANDIDATE_HASHES' });
});

test('pending applicable gate blocks; approved gate requires human evidence', async (t) => {
  const f = await fixture(t, { gate: true });
  await assert.rejects(f.run(), { rule: 'GATE_STATUS' });
  f.decisions.gates.ui.status = 'approved';
  await f.saveDecisions();
  await assert.rejects(f.run(), { rule: 'GATE_EVIDENCE' });
  await f.put('docs/ui-evidence.md', 'reviewed UI');
  Object.assign(f.decisions.gates.ui, {
    human_reference: 'conversation:UI-review',
    evidence_hashes: { 'docs/ui-evidence.md': hash('reviewed UI') },
  });
  await f.saveDecisions();
  assert.equal((await f.run()).status, 'approved');
  await f.put('docs/ui-evidence.md', 'changed UI');
  await assert.rejects(f.run(), { rule: 'DIGEST_MISMATCH' });
});

test('missing gate registry, missing gate or fixture gate blocks', async (t) => {
  const f = await fixture(t, { gate: true });
  delete f.decisions.plan_gates['07-01'];
  await f.saveDecisions();
  await assert.rejects(f.run(), { rule: 'GATE_REGISTRY' });
  f.decisions.plan_gates['07-01'] = ['absent'];
  await f.saveDecisions();
  await assert.rejects(f.run(), { rule: 'GATE_STATUS' });
  f.decisions.plan_gates['07-01'] = ['ui'];
  Object.assign(f.decisions.gates.ui, { status: 'approved', source: 'fixture' });
  await f.saveDecisions();
  await assert.rejects(f.run(), { rule: 'GATE_EVIDENCE' });
});

test('prepare-candidates has its own paths and commands, without implementation authority', async (t) => {
  const f = await fixture(t, { plan: '07-04', candidates: true });
  const source = `---\nplan: '04'\nfiles_modified:\n  - ${f.approvalPath}\n  - scripts/example.mjs\n  - ${f.candidate}\nautonomous: false\n---\n<task type="auto"><files>${f.candidate}</files><verify><automated>node scripts/validate-phase7-approvals.mjs --plan 07-04 --stage prepare-candidates; hash candidate</automated></verify></task>\n<task type="auto"><files>scripts/example.mjs</files><verify><automated>node scripts/validate-phase7-approvals.mjs --plan 07-04 --stage implementation; ${focal}</automated></verify></task>\n`;
  await f.put(`${phase}/07-04-PLAN.md`, source);
  Object.assign(f.approval, {
    authorized_stage: 'prepare-candidates',
    plan_sha256: hash(source),
    candidate_hashes: {},
  });
  f.approval.scope = {
    paths: [f.approvalPath, f.candidate],
    commands: [
      'node scripts/validate-phase7-approvals.mjs --plan 07-04 --stage prepare-candidates; hash candidate',
    ],
  };
  await f.save();
  assert.equal((await f.run({ stage: 'prepare-candidates' })).status, 'approved');
  await assert.rejects(f.run(), { rule: 'APPROVAL_STAGE' });
});

test('unsafe paths and malformed JSON fail closed without revealing contents', async (t) => {
  const f = await fixture(t);
  f.approval.baseline_hashes = { '../outside': hash('secret') };
  await f.save();
  await assert.rejects(f.run(), { rule: 'UNSAFE_PATH' });
  await f.put(f.approvalPath, '{sensitive private content');
  await assert.rejects(
    f.run(),
    (error) => error.rule === 'APPROVAL_FILE' && !error.message.includes('sensitive'),
  );
});

test('CLI missing validator or denied approval prevents downstream mutation', async (t) => {
  const f = await fixture(t);
  const consumer =
    'import { spawnSync } from "node:child_process"; import { writeFileSync } from "node:fs"; const r=spawnSync(process.execPath,["scripts/validate-phase7-approvals.mjs","--plan","07-01","--stage","implementation"],{stdio:"pipe"}); if(r.status!==0) process.exit(1); writeFileSync("mutated","unsafe");';
  await f.put('consumer.mjs', consumer);
  const run = () =>
    spawnSync(process.execPath, ['consumer.mjs'], { cwd: f.root, encoding: 'utf8' });
  assert.equal(run().status, 1);
  await assert.rejects(readFile(join(f.root, 'mutated')));
  await mkdir(join(f.root, 'scripts'), { recursive: true });
  await copyFile(
    join(projectRoot, 'scripts/validate-phase7-approvals.mjs'),
    join(f.root, 'scripts/validate-phase7-approvals.mjs'),
  );
  f.approval.status = 'pending';
  await f.save();
  assert.equal(run().status, 1);
  await assert.rejects(readFile(join(f.root, 'mutated')));
});

test('CLI rejects missing/duplicate/unknown flags and path traversal', () => {
  const script = join(projectRoot, 'scripts/validate-phase7-approvals.mjs');
  for (const args of [
    [],
    ['--plan', '07-01'],
    ['--plan', '../07-01', '--stage', 'implementation'],
    ['--plan', '07-01', '--stage', 'implementation', '--stage', 'implementation'],
    ['--plan', '07-01', '--stage', 'implementation', '--fixture'],
  ]) {
    const result = spawnSync(process.execPath, [script, ...args], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /BLOCKED/);
  }
});

test('pending-approval test discriminates a permissive validator mutation', async (t) => {
  const root = await mkdtemp(join(tmpdir(), 'betufu-approval-mutation-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  await mkdir(join(root, 'scripts'));
  const source = await readFile(join(projectRoot, 'scripts/validate-phase7-approvals.mjs'), 'utf8');
  const guard = "requireCondition(approval.status === 'approved', 'APPROVAL_STATUS');";
  assert.equal(source.split(guard).length, 2);
  await writeFile(join(root, 'scripts/validate-phase7-approvals.mjs'), source.replace(guard, ''));
  await copyFile(
    join(projectRoot, 'scripts/validate-phase7-approvals.test.mjs'),
    join(root, 'scripts/validate-phase7-approvals.test.mjs'),
  );
  const childEnvironment = { ...process.env };
  // Node's parent test context suppresses a nested test runner unless removed.
  delete childEnvironment.NODE_TEST_CONTEXT;
  const result = spawnSync(
    process.execPath,
    [
      '--test',
      '--test-name-pattern=^approval pending blocks execution$',
      'scripts/validate-phase7-approvals.test.mjs',
    ],
    { cwd: root, env: childEnvironment, encoding: 'utf8', timeout: 15_000 },
  );
  assert.equal(result.status, 1, result.stdout + result.stderr);
  assert.match(result.stdout + result.stderr, /Missing expected rejection/);
});
