#!/usr/bin/env node

/**
 * Read-only review coordinator for frozen BETUFU snapshots.
 * It never mutates Git, files, dependencies, or external services.
 */

import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  assertPathWithinRoot,
  assertPathsWithinAllowlist,
  discoverPlans,
  loadSkillManifest,
  normalizeRepositoryPath,
  PROJECT_ROOT,
  runCaptured,
} from './agy-wave.mjs';

export const REVIEW_SCHEMA = resolve(
  PROJECT_ROOT,
  '.agents',
  'agy-review-result.schema.json',
);
export const REVIEW_TYPES = ['security', 'ui', 'verification'];
export const DEFAULT_REVIEW_TIMEOUT_MS = 20 * 60 * 1000;
export const AGGREGATE_COMMANDS = Object.freeze({
  backend: 'node scripts/maven.mjs -f backend/pom.xml verify',
  node: 'node --test scripts/*.test.mjs',
  angular: 'npm --prefix frontend run test -- --watch=false',
  playwright: 'npm --prefix frontend exec playwright test -- --config playwright.sports.config.ts',
});
const GIT_SHA = /^[0-9a-f]{40}(?:[0-9a-f]{24})?$/iu;
const SHA256 = /^[0-9a-f]{64}$/iu;

function fail(message) {
  throw new Error(message);
}

function unique(values) {
  return [...new Set(values)];
}

function sha256Hex(value) {
  return createHash('sha256').update(value).digest('hex').toUpperCase();
}

function safeGitResult(result, label) {
  if (result?.spawnError) fail(`${label}: processo nao iniciou`);
  if (result?.timedOut) fail(`${label}: timeout`);
  if (result?.overflow) fail(`${label}: saida excedeu o limite`);
  if (result?.exitCode !== 0) fail(`${label}: exit code ${String(result?.exitCode)}`);
  return String(result?.stdout ?? '');
}

async function gitOutput(runProcess, cwd, args) {
  return safeGitResult(
    await runProcess(
      'git',
      ['-C', cwd, ...args],
      { cwd, timeoutMs: 30_000, maxOutputBytes: 5 * 1024 * 1024 },
    ),
    `git ${args.join(' ')}`,
  );
}

function parseStatusPaths(raw) {
  return unique(String(raw).split('\0').filter(Boolean).map((entry) => {
    const value = entry.length >= 3 ? entry.slice(3) : entry;
    return normalizeRepositoryPath(value);
  }));
}

function parseNameOnly(raw) {
  return unique(String(raw).split(/\r?\n/u).map((line) => line.trim()).filter(Boolean)
    .map(normalizeRepositoryPath));
}

function validateReviewType(reviewType) {
  if (!REVIEW_TYPES.includes(reviewType)) fail(`reviewer invalido: ${String(reviewType)}`);
  return reviewType;
}

function validateSha(value, label, pattern = GIT_SHA) {
  if (typeof value !== 'string' || !pattern.test(value)) fail(`${label} invalido`);
  return value.toLowerCase();
}

function comparableAbsolutePath(value) {
  return resolve(value).toLowerCase();
}

async function readPlan(projectRoot, planId) {
  const match = String(planId ?? '').match(/^(\d{2})-\d{2}$/u);
  if (!match) fail(`plan_id invalido: ${String(planId)}`);
  const plans = await discoverPlans(projectRoot, Number.parseInt(match[1], 10));
  const plan = plans.find((candidate) => candidate.id === planId);
  if (!plan) fail(`plano nao encontrado: ${planId}`);
  return plan;
}

export function validateReviewerProfile(profile, reviewType) {
  validateReviewType(reviewType);
  if (!profile || profile.mode !== 'plan' || profile.readonly !== true) {
    fail(`perfil ${reviewType} deve ser plan/read-only`);
  }
  if (!Array.isArray(profile.skills) || profile.skills.length === 0) {
    fail(`perfil ${reviewType} sem skills`);
  }
  if (!Array.isArray(profile.focus) || profile.focus.length === 0) {
    fail(`perfil ${reviewType} sem foco`);
  }
  if (!Array.isArray(profile.required_gates) || profile.required_gates.length === 0) {
    fail(`perfil ${reviewType} sem gates obrigatorios`);
  }
  return true;
}

export async function captureReviewSnapshot(options, dependencies = {}) {
  const runProcess = dependencies.runProcess;
  if (typeof runProcess !== 'function') fail('captureReviewSnapshot exige runProcess');
  const projectRoot = resolve(options.projectRoot ?? PROJECT_ROOT);
  const worktree = assertPathWithinRoot(options.worktree, options.workspaceRoot);
  const expectedBase = validateSha(options.expectedBase, 'expected-base');
  const expectedHead = validateSha(options.expectedHead, 'expected-head');
  const topLevel = (await gitOutput(runProcess, worktree, ['rev-parse', '--show-toplevel'])).trim();
  if (comparableAbsolutePath(topLevel) !== comparableAbsolutePath(worktree)) {
    fail('worktree nao e a raiz informada');
  }
  const worktreeCommon = resolve(worktree, (await gitOutput(
    runProcess,
    worktree,
    ['rev-parse', '--git-common-dir'],
  )).trim());
  const projectCommon = resolve(projectRoot, (await gitOutput(
    runProcess,
    projectRoot,
    ['rev-parse', '--git-common-dir'],
  )).trim());
  if (comparableAbsolutePath(worktreeCommon) !== comparableAbsolutePath(projectCommon)) {
    fail('worktree pertence a outro repositorio');
  }

  const head = (await gitOutput(runProcess, worktree, ['rev-parse', 'HEAD'])).trim().toLowerCase();
  const branch = (await gitOutput(runProcess, worktree, ['symbolic-ref', '--short', 'HEAD'])).trim();
  const statusPorcelain = await gitOutput(
    runProcess,
    worktree,
    ['status', '--porcelain=v1', '-z', '--untracked-files=all'],
  );
  const changedFiles = parseNameOnly(await gitOutput(
    runProcess,
    worktree,
    ['diff', '--name-only', `${expectedBase}..${expectedHead}`],
  ));
  const diffCheckResult = await runProcess(
    'git',
    ['-C', worktree, 'diff', '--check', `${expectedBase}..${expectedHead}`],
    { cwd: worktree, timeoutMs: 30_000, maxOutputBytes: 5 * 1024 * 1024 },
  );
  const diffCheck = String(diffCheckResult?.stdout ?? '');
  const diffCheckExitCode = diffCheckResult?.exitCode;
  const diffCheckError = diffCheckResult?.spawnError ?? null;
  /*
   * diff --check reports whitespace defects through its exit code; keep them
   * in the snapshot so the reviewer can block without losing the evidence.
   */
  const diffCheckResultSummary = diffCheckError
    ? String(diffCheckError)
    : diffCheckExitCode === 0 ? diffCheck : `diff-check exit code ${String(diffCheckExitCode)}`;
  const ancestor = await runProcess(
    'git',
    ['-C', worktree, 'merge-base', '--is-ancestor', expectedBase, expectedHead],
    { cwd: worktree, timeoutMs: 30_000, maxOutputBytes: 1024 },
  );
  const fileHashes = {};
  for (const path of changedFiles) {
    try {
      fileHashes[path] = sha256Hex(await readFile(resolve(worktree, path)));
    } catch (error) {
      if (error?.code === 'ENOENT') fileHashes[path] = '0'.repeat(64);
      else throw error;
    }
  }

  return {
    projectRoot,
    worktree,
    expectedBase,
    expectedHead,
    head,
    branch,
    statusPorcelain,
    dirtyFiles: statusPorcelain.length > 0 ? parseStatusPaths(statusPorcelain) : [],
    changedFiles,
    fileHashes,
    diffCheck,
    diffCheckExitCode,
    diffCheckResultSummary,
    baseIsAncestor: ancestor?.exitCode === 0,
    commonGitDirectory: projectCommon,
  };
}

function gate(id, status, detail) {
  return { id, status, detail };
}

function evidenceList(evidence, field) {
  const value = evidence?.[field];
  return Array.isArray(value) ? value : [];
}

function testEvidenceGate(plan, evidence) {
  const tests = evidenceList(evidence, 'tests');
  if (tests.length === 0) return gate('test-evidence', 'BLOCKED', 'nenhuma evidencia de teste fornecida');
  const allowed = new Set(plan?.tasks.flatMap((task) => task.verificationCommands) ?? []);
  const invalid = tests.filter((item) => !item || typeof item.command !== 'string'
    || !allowed.has(item.command) || item.status !== 'PASS');
  if (invalid.length > 0) return gate('test-evidence', 'BLOCKED', 'teste ausente, fora do plano ou sem PASS');
  const nondiscriminating = tests.filter((item) => item.kind === 'focal' && item.discriminates !== true);
  if (nondiscriminating.length > 0) {
    return gate('test-evidence', 'BLOCKED', 'teste focal sem evidencia discriminante');
  }
  return gate('test-evidence', 'PASS', `${tests.length} teste(s) verificado(s)`);
}

function aggregateGate(id, command, evidence) {
  const item = evidenceList(evidence, 'aggregate').find((candidate) => candidate?.command === command);
  if (!item || item.status !== 'PASS') return gate(id, 'BLOCKED', `evidencia ausente para ${command}`);
  return gate(id, 'PASS', command);
}

function openApiGate(evidence) {
  const item = evidence?.openapi;
  if (!item || typeof item !== 'object') return gate('openapi-contract', 'BLOCKED', 'evidencia OpenAPI ausente');
  if (typeof item.governedBefore !== 'string' || !SHA256.test(item.governedBefore)
    || typeof item.governedAfter !== 'string' || !SHA256.test(item.governedAfter)
    || item.governedBefore !== item.governedAfter
    || typeof item.digest !== 'string' || !SHA256.test(item.digest)) {
    return gate('openapi-contract', 'BLOCKED', 'hashes governados ou digest invalidos');
  }
  return gate('openapi-contract', 'PASS', 'governed-before/after e digest conferidos');
}

export function evaluateReviewGates({ reviewType, profile, plan, snapshot, evidence = {}, executionResult = null }) {
  validateReviewerProfile(profile, reviewType);
  const gates = [
    gate('worktree-clean', snapshot.dirtyFiles.length === 0 ? 'PASS' : 'BLOCKED', snapshot.dirtyFiles.length === 0
      ? 'snapshot sem alteracoes locais'
      : `alteracoes locais: ${snapshot.dirtyFiles.join(', ')}`),
    gate('head', snapshot.head === snapshot.expectedHead && snapshot.baseIsAncestor ? 'PASS' : 'BLOCKED',
      snapshot.head === snapshot.expectedHead && snapshot.baseIsAncestor
        ? 'HEAD e ancestralidade conferidos'
        : 'HEAD ou ancestralidade divergente'),
    gate('diff-check', snapshot.diffCheckExitCode === 0 && snapshot.diffCheck.length === 0 ? 'PASS' : 'BLOCKED', snapshot.diffCheckExitCode === 0 && snapshot.diffCheck.length === 0
      ? 'diff sem erros de whitespace'
      : snapshot.diffCheckResultSummary),
  ];

  if (plan) {
    try {
      assertPathsWithinAllowlist(snapshot.changedFiles, plan.filesModified);
      gates.push(gate('scope', 'PASS', 'paths do diff dentro da allowlist do plano'));
    } catch (error) {
      gates.push(gate('scope', 'BLOCKED', error.message));
    }
  }
  if (profile.required_gates.includes('structured-result')) {
    const valid = executionResult && typeof executionResult === 'object'
      && Array.isArray(executionResult.files_changed)
      && JSON.stringify([...executionResult.files_changed].sort()) === JSON.stringify(snapshot.changedFiles.slice().sort());
    gates.push(gate('structured-result', valid ? 'PASS' : 'BLOCKED', valid
      ? 'resultado estruturado corresponde ao diff'
      : 'resultado estruturado ausente ou divergente'));
  }
  if (profile.required_gates.includes('test-evidence')) gates.push(testEvidenceGate(plan, evidence));
  if (profile.required_gates.includes('security-review')) {
    gates.push(gate('security-review', evidence.securityReview === 'PASS' ? 'PASS' : 'BLOCKED',
      evidence.securityReview === 'PASS' ? 'parecer de seguranca recebido' : 'parecer de seguranca ausente'));
  }
  if (profile.required_gates.includes('ui-review')) {
    gates.push(gate('ui-review', evidence.uiReview === 'PASS' ? 'PASS' : 'BLOCKED',
      evidence.uiReview === 'PASS' ? 'parecer de UI recebido' : 'parecer de UI ausente'));
  }
  const changed = snapshot.changedFiles;
  const hasBackend = changed.some((path) => path.startsWith('backend/'));
  const hasFrontend = changed.some((path) => path.startsWith('frontend/'));
  const hasNodeProcess = changed.some((path) => path.startsWith('scripts/') || path.startsWith('.agents/'));
  const hasBrowserTests = changed.some((path) => path.startsWith('frontend/e2e/')
    || path.startsWith('frontend/playwright/')
    || path.endsWith('playwright.config.ts'));

  // Application changes require both repository aggregate suites; frontend
  // changes additionally require Angular, and browser assets add Playwright.
  if (hasBackend || hasFrontend) {
    gates.push(aggregateGate('aggregate-backend', AGGREGATE_COMMANDS.backend, evidence));
    gates.push(aggregateGate('aggregate-node', AGGREGATE_COMMANDS.node, evidence));
  } else if (hasNodeProcess) {
    gates.push(aggregateGate('aggregate-node', AGGREGATE_COMMANDS.node, evidence));
  }
  if (hasFrontend) gates.push(aggregateGate('aggregate-angular', AGGREGATE_COMMANDS.angular, evidence));
  if (hasBrowserTests) gates.push(aggregateGate('aggregate-playwright', AGGREGATE_COMMANDS.playwright, evidence));
  if (changed.some((path) => path === 'backend/openapi/betufu.yaml' || path.startsWith('frontend/src/app/api/generated/'))) {
    gates.push(openApiGate(evidence));
  }
  return unique(gates.map((item) => JSON.stringify(item))).map((item) => JSON.parse(item));
}

export function buildReviewerPrompt(profile, snapshot, reviewType) {
  validateReviewerProfile(profile, reviewType);
  return [
    `MODO REVISOR SOMENTE LEITURA: ${reviewType}.`,
    'Analise exclusivamente o snapshot congelado abaixo.',
    'Nao edite arquivos, nao use Git mutavel, nao instale dependencias, nao use rede, Docker ou comandos externos.',
    'Retorne somente o objeto exigido pelo schema JSON do revisor.',
    'Classifique findings como P0/P1/P2/P3/INFO. P0-P2 bloqueiam o parecer.',
    `Foco deste perfil: ${profile.focus.join(', ')}.`,
    `Gates obrigatorios: ${profile.required_gates.join(', ')}.`,
    `<review_snapshot>${JSON.stringify(snapshot)}</review_snapshot>`,
  ].join('\n');
}

export function buildReviewerArgs(worktreePath, prompt, timeoutMs = DEFAULT_REVIEW_TIMEOUT_MS) {
  return [
    '--add-dir', worktreePath,
    '--model', 'gemini-3.7-flash-high',
    '--mode', 'plan',
    '--sandbox',
    '--output-format', 'json',
    '--json-schema', REVIEW_SCHEMA,
    '--print-timeout', `${Math.max(1, Math.round(timeoutMs / 1000))}s`,
    '-p', prompt,
  ];
}

export function validateReviewerResult(value, reviewType, planId, snapshot, expectedGates = null) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) fail('resultado do revisor nao e objeto');
  if (value.reviewer !== reviewType || value.plan_id !== planId) fail('resultado do revisor pertence a outro contexto');
  if (value.base !== snapshot.expectedBase || value.head !== snapshot.expectedHead) fail('resultado do revisor tem base/HEAD divergentes');
  if (!['PASS', 'BLOCKED', 'ERROR'].includes(value.status)) fail('status do revisor invalido');
  if (!Array.isArray(value.changed_files) || JSON.stringify(value.changed_files.slice().sort()) !== JSON.stringify(snapshot.changedFiles.slice().sort())) {
    fail('changed_files do revisor diverge do snapshot');
  }
  if (!Array.isArray(value.gates) || value.gates.length === 0 || !Array.isArray(value.findings)
    || typeof value.summary !== 'string' || !value.summary) {
    fail('resultado do revisor incompleto');
  }
  if (value.gates.some((item) => !item || typeof item.id !== 'string' || !item.id
    || !['PASS', 'BLOCKED', 'ERROR'].includes(item.status)
    || typeof item.detail !== 'string' || !item.detail)) {
    fail('gate do revisor invalido');
  }
  const gateIds = value.gates.map((item) => item.id);
  if (gateIds.length !== new Set(gateIds).size) fail('resultado possui gates duplicados');
  if (Array.isArray(expectedGates)) {
    const resultById = new Map(value.gates.map((item) => [item.id, item]));
    const missing = expectedGates.filter((item) => !resultById.has(item.id));
    const mismatched = expectedGates.filter((item) => resultById.get(item.id)?.status !== item.status);
    const expectedIds = new Set(expectedGates.map((item) => item.id));
    const extra = value.gates.filter((item) => !expectedIds.has(item.id));
    if (missing.length > 0 || mismatched.length > 0 || extra.length > 0) {
      fail('gates do revisor divergem dos gates coordenados');
    }
  }
  if (value.findings.some((finding) => !['P0', 'P1', 'P2', 'P3', 'INFO'].includes(finding?.severity))) {
    fail('finding com severidade invalida');
  }
  const blockedByGate = value.gates.some((item) => item.status !== 'PASS');
  const blockedByFinding = value.findings.some((finding) => ['P0', 'P1', 'P2'].includes(finding.severity));
  if (value.status === 'PASS' && (blockedByGate || blockedByFinding)) {
    fail('resultado PASS possui gate bloqueado ou finding P0-P2');
  }
  return value;
}

export async function prepareReview(options, dependencies = {}) {
  validateReviewType(options.reviewType);
  const manifest = options.skillManifest ?? await loadSkillManifest(options.projectRoot ?? PROJECT_ROOT);
  const profile = manifest.reviewer_profiles[options.reviewType];
  validateReviewerProfile(profile, options.reviewType);
  const plan = await readPlan(options.projectRoot ?? PROJECT_ROOT, options.planId);
  const snapshot = await captureReviewSnapshot(options, dependencies);
  const gates = evaluateReviewGates({
    reviewType: options.reviewType,
    profile,
    plan,
    snapshot,
    evidence: options.evidence,
    executionResult: options.executionResult,
  });
  const prompt = buildReviewerPrompt(profile, snapshot, options.reviewType);
  return {
    reviewType: options.reviewType,
    planId: options.planId,
    profile,
    plan,
    snapshot,
    gates,
    prompt,
    command: ['agy', ...buildReviewerArgs(options.worktree, prompt, options.timeoutMs)],
  };
}

export async function invokeReviewer(options, dependencies = {}) {
  if (options.allowExternalCall !== true) fail('invokeReviewer exige allowExternalCall=true');
  const runProcess = dependencies.runProcess;
  if (typeof runProcess !== 'function') fail('invokeReviewer exige runProcess');
  const prepared = await prepareReview(options, dependencies);
  const blockedGates = prepared.gates.filter((item) => item.status !== 'PASS');
  if (blockedGates.length > 0) {
    fail(`gates previos bloqueados: ${blockedGates.map((item) => item.id).join(', ')}`);
  }
  const result = await runProcess(
    'agy',
    buildReviewerArgs(options.worktree, prepared.prompt, options.timeoutMs),
    { cwd: options.worktree, timeoutMs: options.timeoutMs ?? DEFAULT_REVIEW_TIMEOUT_MS, maxOutputBytes: 5 * 1024 * 1024 },
  );
  const stdout = safeGitResult(result, 'agy reviewer');
  let parsed;
  try {
    const envelope = JSON.parse(stdout.trim());
    parsed = envelope?.structured_output ?? envelope?.result ?? envelope?.response ?? envelope?.output ?? envelope;
    if (typeof parsed === 'string') parsed = JSON.parse(parsed);
  } catch {
    fail('revisor devolveu JSON invalido');
  }
  const validated = validateReviewerResult(
    parsed,
    options.reviewType,
    options.planId,
    prepared.snapshot,
    prepared.gates,
  );
  return { ...prepared, result: validated };
}

function oneValue(argv, index, flag) {
  const value = argv[index + 1];
  if (!value || value.startsWith('--')) fail(`${flag} exige um valor`);
  return value;
}

export function parseReviewArguments(argv) {
  let mode = null;
  const parsed = { mode };
  const valueFlags = new Set([
    '--reviewer', '--plan', '--worktree', '--workspace-root', '--project-root',
    '--expected-base', '--expected-head', '--evidence-file', '--execution-result-file',
  ]);
  const keys = {
    '--reviewer': 'reviewType',
    '--plan': 'planId',
    '--worktree': 'worktree',
    '--workspace-root': 'workspaceRoot',
    '--project-root': 'projectRoot',
    '--expected-base': 'expectedBase',
    '--expected-head': 'expectedHead',
    '--evidence-file': 'evidenceFile',
    '--execution-result-file': 'executionResultFile',
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === '--prepare') {
      if (mode) fail('use exatamente um de --prepare ou --invoke');
      mode = 'prepare';
      parsed.mode = mode;
    } else if (arg === '--invoke') {
      if (mode) fail('use exatamente um de --prepare ou --invoke');
      mode = 'invoke';
      parsed.mode = mode;
    } else if (arg === '--allow-external-call') {
      parsed.allowExternalCall = true;
    } else if (valueFlags.has(arg)) {
      parsed[keys[arg]] = oneValue(argv, index, arg);
      index += 1;
    } else {
      fail(`argumento desconhecido: ${arg}`);
    }
  }
  if (!mode) fail('use exatamente um de --prepare ou --invoke');
  for (const field of ['reviewType', 'planId', 'worktree', 'workspaceRoot', 'expectedBase', 'expectedHead']) {
    if (!parsed[field]) fail(`argumento obrigatorio ausente: ${field}`);
  }
  if (mode === 'prepare' && parsed.allowExternalCall) {
    fail('--allow-external-call so pode ser usado com --invoke');
  }
  if (mode === 'invoke' && parsed.allowExternalCall !== true) {
    fail('--invoke exige --allow-external-call');
  }
  return parsed;
}

async function readJsonInput(filePath, label) {
  try {
    const value = JSON.parse(await readFile(resolve(filePath), 'utf8'));
    if (!value || typeof value !== 'object' || Array.isArray(value)) fail(`${label} deve ser um objeto JSON`);
    return value;
  } catch (error) {
    if (error instanceof Error && error.message.startsWith(label)) throw error;
    fail(`${label} invalido: ${error instanceof Error ? error.message : String(error)}`);
  }
}

export async function runReviewCli(argv, projectRoot = PROJECT_ROOT) {
  const parsed = parseReviewArguments(argv);
  const root = resolve(parsed.projectRoot ?? projectRoot);
  const evidence = parsed.evidenceFile ? await readJsonInput(parsed.evidenceFile, 'evidence-file') : {};
  const executionResult = parsed.executionResultFile
    ? await readJsonInput(parsed.executionResultFile, 'execution-result-file')
    : null;
  const options = {
    ...parsed,
    projectRoot: root,
    evidence,
    executionResult,
  };
  return parsed.mode === 'prepare'
    ? prepareReview(options, { runProcess: runCaptured })
    : invokeReviewer(options, { runProcess: runCaptured });
}

async function main() {
  try {
    const result = await runReviewCli(process.argv.slice(2));
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
  } catch (error) {
    process.stderr.write(`[agy-review] ERRO: ${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
  }
}

const invokedPath = process.argv[1] ? resolve(process.argv[1]) : '';
if (invokedPath === resolve(fileURLToPath(import.meta.url))) await main();
