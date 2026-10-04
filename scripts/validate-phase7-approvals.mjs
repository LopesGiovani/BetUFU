#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { lstat, readFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const defaultProjectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const phaseDirectory = '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto';
const stages = ['prepare-candidates', 'implementation'];
const aggregateCommands = [
  'node scripts/maven.mjs -f backend/pom.xml verify',
  'node --test scripts/*.test.mjs',
];
const digestPattern = /^[a-f0-9]{64}$/i;
const headPattern = /^[a-f0-9]{40}$/;

export class Phase7ApprovalError extends Error {
  constructor(rule) {
    super(`BLOCKED: ${rule}`);
    this.name = 'Phase7ApprovalError';
    this.rule = rule;
  }
}

function requireCondition(condition, rule) {
  if (!condition) throw new Phase7ApprovalError(rule);
}

function nonempty(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

async function safePath(root, path) {
  requireCondition(nonempty(path) && !/[:\\\x00-\x1f]/.test(path), 'UNSAFE_PATH');
  const parts = path.split('/');
  requireCondition(
    parts.every((part) => part && part !== '.' && part !== '..'),
    'UNSAFE_PATH',
  );
  let current = root;
  for (const part of parts) {
    current = join(current, part);
    try {
      requireCondition(!(await lstat(current)).isSymbolicLink(), 'UNSAFE_PATH');
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
  }
  return current;
}

async function read(root, path, rule) {
  const absolute = await safePath(root, path);
  try {
    requireCondition((await lstat(absolute)).isFile(), rule);
    return await readFile(absolute);
  } catch (error) {
    if (error instanceof Phase7ApprovalError) throw error;
    throw new Phase7ApprovalError(rule);
  }
}

function parseJson(bytes, rule) {
  try {
    const value = JSON.parse(bytes.toString('utf8'));
    requireCondition(value && typeof value === 'object' && !Array.isArray(value), rule);
    return value;
  } catch {
    throw new Phase7ApprovalError(rule);
  }
}

function digest(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

function assertDigest(bytes, expected) {
  requireCondition(typeof expected === 'string' && digestPattern.test(expected), 'DIGEST_MISMATCH');
  requireCondition(digest(bytes) === expected.toLowerCase(), 'DIGEST_MISMATCH');
}

function sameSet(actual, expected, rule) {
  requireCondition(Array.isArray(actual) && actual.every(nonempty), rule);
  requireCondition(new Set(actual).size === actual.length, rule);
  requireCondition(
    actual.length === expected.length && expected.every((item) => actual.includes(item)),
    rule,
  );
}

// Read the narrow PLAN format used by Phase 7; ambiguous/incomplete plans fail closed.
export function phase7PlanContract(source, plan, stage) {
  const frontmatter = source.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  requireCondition(frontmatter, 'PLAN_CONTRACT');
  requireCondition(
    new RegExp(`^plan: ['"]?${plan.slice(3)}['"]?\\s*$`, 'm').test(frontmatter[1]),
    'PLAN_CONTRACT',
  );
  const allowlist = frontmatter[1].match(/^files_modified:\r?\n((?:[ \t]+- [^\r\n]+\r?\n)+)/m);
  requireCondition(allowlist, 'PLAN_CONTRACT');
  const allPaths = [...allowlist[1].matchAll(/^[ \t]+- ([^\r\n]+)/gm)].map((match) =>
    match[1].trim(),
  );
  requireCondition(
    allPaths.length <= 15 && new Set(allPaths).size === allPaths.length,
    'PLAN_CONTRACT',
  );
  const paths = [`docs/phase7-approvals/${plan}.json`];
  const commands = [];
  for (const match of source.matchAll(/<task\s+([^>]+)>([\s\S]*?)<\/task>/g)) {
    if (!/type="(?:auto|tracer)"/.test(match[1])) continue;
    const body = match[2];
    const automated = [...body.matchAll(/<automated>([\s\S]*?)<\/automated>/g)].map((entry) =>
      entry[1].trim(),
    );
    const declaredStages = automated.flatMap((command) =>
      [...command.matchAll(/--stage\s+(prepare-candidates|implementation)\b/g)].map(
        (entry) => entry[1],
      ),
    );
    if (declaredStages.length && !declaredStages.includes(stage)) continue;
    if (!declaredStages.length && stage !== 'implementation') continue;
    const files = body.match(/<files>([\s\S]*?)<\/files>/);
    requireCondition(files && automated.length > 0, 'PLAN_CONTRACT');
    paths.push(...files[1].split(',').map((path) => path.trim()));
    commands.push(...automated);
  }
  requireCondition(
    commands.length > 0 && paths.every((path) => allPaths.includes(path)),
    'PLAN_CONTRACT',
  );
  if (stage === 'implementation') commands.push(...aggregateCommands);
  return {
    paths: [...new Set(paths)],
    commands: [...new Set(commands)],
    candidates: allPaths.filter((path) => path.includes(`/${plan}-candidate/`)),
  };
}

async function verifyHashes(root, hashes, rule, allowEmpty = true) {
  requireCondition(hashes && typeof hashes === 'object' && !Array.isArray(hashes), rule);
  const entries = Object.entries(hashes);
  requireCondition(allowEmpty || entries.length > 0, rule);
  for (const [path, expected] of entries) assertDigest(await read(root, path, rule), expected);
}

export async function validatePhase7Approvals({
  projectRoot = defaultProjectRoot,
  plan,
  stage,
  currentHead,
} = {}) {
  requireCondition(/^07-(?:0[1-9]|[12][0-9]|3[0-5])$/.test(plan ?? ''), 'PLAN');
  requireCondition(stages.includes(stage), 'STAGE');
  const root = resolve(projectRoot);
  const approval = parseJson(
    await read(root, `docs/phase7-approvals/${plan}.json`, 'APPROVAL_FILE'),
    'APPROVAL_FILE',
  );
  requireCondition(approval.schema_version === 1, 'APPROVAL_SCHEMA');
  requireCondition(approval.plan === plan, 'APPROVAL_PLAN');
  requireCondition(approval.status === 'approved', 'APPROVAL_STATUS');
  requireCondition(approval.authorized_stage === stage, 'APPROVAL_STAGE');
  const human = approval.human_approval;
  requireCondition(
    approval.fixture !== true &&
      human?.source === 'owner' &&
      human.status === 'approved' &&
      nonempty(human.reference) &&
      nonempty(human.statement) &&
      Array.isArray(human.plans) &&
      human.plans.includes(plan),
    'HUMAN_APPROVAL',
  );
  let liveHead = currentHead;
  if (liveHead === undefined) {
    try {
      liveHead = execFileSync('git', ['rev-parse', 'HEAD'], {
        cwd: root,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
      }).trim();
    } catch {
      throw new Phase7ApprovalError('APPROVAL_HEAD');
    }
  }
  requireCondition(headPattern.test(liveHead ?? '') && approval.head === liveHead, 'APPROVAL_HEAD');
  const planBytes = await read(root, `${phaseDirectory}/${plan}-PLAN.md`, 'PLAN_FILE');
  assertDigest(planBytes, approval.plan_sha256);
  const contract = phase7PlanContract(planBytes.toString('utf8'), plan, stage);
  sameSet(approval.scope?.paths, contract.paths, 'APPROVAL_PATHS');
  sameSet(approval.scope?.commands, contract.commands, 'APPROVAL_COMMANDS');
  for (const path of contract.paths) await safePath(root, path);
  await verifyHashes(root, approval.baseline_hashes, 'BASELINE_HASHES', false);
  await verifyHashes(root, approval.candidate_hashes, 'CANDIDATE_HASHES');
  if (stage === 'implementation')
    sameSet(Object.keys(approval.candidate_hashes), contract.candidates, 'CANDIDATE_HASHES');
  const decisionsBytes = await read(root, 'docs/phase7-decisions.json', 'DECISIONS_FILE');
  assertDigest(decisionsBytes, approval.decisions_sha256);
  const decisions = parseJson(decisionsBytes, 'DECISIONS_FILE');
  requireCondition(
    decisions.schema_version === 1 &&
      Object.hasOwn(decisions.plan_gates ?? {}, plan) &&
      Array.isArray(decisions.plan_gates[plan]),
    'GATE_REGISTRY',
  );
  for (const gateId of decisions.plan_gates[plan]) {
    const gate = decisions.gates?.[gateId];
    requireCondition(gate?.status === 'approved', 'GATE_STATUS');
    requireCondition(
      gate.source === 'owner' && gate.fixture !== true && nonempty(gate.human_reference),
      'GATE_EVIDENCE',
    );
    await verifyHashes(root, gate.evidence_hashes, 'GATE_EVIDENCE', false);
  }
  return {
    status: 'approved',
    plan,
    stage,
    head: liveHead,
    paths: contract.paths,
    commands: contract.commands,
  };
}

function parseArguments(args) {
  requireCondition(args.length === 4, 'CLI_ARGUMENTS');
  const parsed = {};
  for (let i = 0; i < args.length; i += 2) {
    const flag = args[i];
    requireCondition(
      ['--plan', '--stage'].includes(flag) && !Object.hasOwn(parsed, flag.slice(2)),
      'CLI_ARGUMENTS',
    );
    parsed[flag.slice(2)] = args[i + 1];
  }
  return parsed;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const result = await validatePhase7Approvals(parseArguments(process.argv.slice(2)));
    console.log(`APPROVED: ${result.plan} ${result.stage} ${result.head}`);
  } catch (error) {
    console.error(
      error instanceof Phase7ApprovalError ? error.message : 'BLOCKED: VALIDATION_ERROR',
    );
    process.exitCode = 1;
  }
}
