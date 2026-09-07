#!/usr/bin/env node

import assert from 'node:assert/strict';
import { test } from 'node:test';

import {
  PROJECT_ROOT,
  assertPathWithinRoot,
  assertPathWithinOrEqualRoot,
  assertPathsWithinAllowlist,
  buildAgyArgs,
  buildExecutorPrompt,
  buildWaveDryRun,
  discoverPlans,
  executeTask,
  loadSkillManifest,
  loadWorkerSkillGuidance,
  parseAgyOutput,
  parseArguments,
  parsePlan,
  parsePorcelainPaths,
  sanitizeAgyDiagnostic,
  selectPlanRoot,
  selectSkillRouting,
  sha256Hex,
  validateInteractiveConfig,
  validateSkillManifest,
  validateWorkerTests,
} from './agy-wave.mjs';

import { resolve } from 'node:path';

const plansPromise = discoverPlans(PROJECT_ROOT, 2);
const skillManifestPromise = loadSkillManifest(PROJECT_ROOT);
const WORKSPACE_ROOT = resolve(PROJECT_ROOT, '..');
const WORKTREE = PROJECT_ROOT;
const COMMON_GIT_DIRECTORY = resolve(PROJECT_ROOT, '.git');
const BASE = 'a'.repeat(40);
const BRANCH = 'worktree-agent-02-01';
const RUN_ID = 'run-test-02-01';
const APPROVAL_REF = 'chat-approval-02-01';
const POSTGRES_TEST = 'backend/src/test/java/br/com/betufu/platform/persistence/PostgresSemanticsIT.java';
const REDIS_SUPPORT = 'backend/src/test/java/br/com/betufu/platform/session/RedisContainerSupport.java';
const MEASUREMENTS = '.planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-MEASUREMENTS.md';
const FROZEN_PLAN = '.planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-15-PLAN.md';
const HASH_A = 'A'.repeat(64);
const HASH_B = 'B'.repeat(64);
const HASH_C = 'C'.repeat(64);

function processResult(stdout = '') {
  return {
    exitCode: 0,
    signal: null,
    stdout,
    stderr: '',
    spawnError: null,
    overflow: false,
    timedOut: false,
  };
}

function workerPayload(overrides = {}) {
  return {
    status: 'COMPLETED',
    plan_id: '02-01',
    task_id: '02-01-T1',
    summary: 'Implementação concluída.',
    files_changed: [POSTGRES_TEST],
    tests_executed: [],
    tests_requested: [
      'node scripts/maven.mjs -f backend/pom.xml verify -Dit.test=PostgresSemanticsIT',
    ],
    decisions_required: [],
    blockers: [],
    ...overrides,
  };
}

function fakeProcesses(options = {}) {
  const calls = [];
  let statusCalls = 0;
  let headCalls = 0;
  const initialStatus = options.initialStatus ?? '';
  const finalStatus = options.finalStatus
    ?? `?? ${POSTGRES_TEST}\0`;
  const agyStdout = options.agyStdout
    ?? JSON.stringify({ status: 'SUCCESS', result: JSON.stringify(workerPayload()) });

  const runProcess = async (command, args) => {
    calls.push({ command, args });
    if (command === 'agy') {
      return {
        ...processResult(agyStdout),
        exitCode: options.agyExitCode ?? 0,
        stderr: options.agyStderr ?? '',
      };
    }
    assert.equal(command, 'git');
    const gitArgs = args.slice(2);
    const operation = gitArgs.join(' ');
    if (operation === 'rev-parse --show-toplevel') return processResult(WORKTREE);
    if (operation === 'rev-parse --git-common-dir') return processResult(COMMON_GIT_DIRECTORY);
    if (operation === 'rev-parse HEAD') {
      headCalls += 1;
      return processResult(headCalls === 1 ? (options.initialHead ?? BASE) : (options.finalHead ?? BASE));
    }
    if (operation === 'symbolic-ref --short HEAD') return processResult(options.branch ?? BRANCH);
    if (operation === 'status --porcelain=v1 -z --untracked-files=all') {
      statusCalls += 1;
      return processResult(statusCalls === 1 ? initialStatus : finalStatus);
    }
    throw new Error(`git simulado recebeu operação inesperada: ${operation}`);
  };
  const hashCalls = new Map();
  const hashPath = async (_worktree, path) => {
    const count = hashCalls.get(path) ?? 0;
    hashCalls.set(path, count + 1);
    const initial = options.initialHashes?.[path] ?? HASH_A;
    return count === 0 ? initial : (options.finalHashes?.[path] ?? initial);
  };
  return { calls, runProcess, hashPath, hashCalls };
}

async function baseExecutionOptions(taskId = '02-01-T1', overrides = {}) {
  return {
    phase: 2,
    wave: 1,
    taskId,
    worktree: WORKTREE,
    workspaceRoot: WORKSPACE_ROOT,
    expectedBase: BASE,
    expectedBranch: BRANCH,
    runId: RUN_ID,
    approvalReference: APPROVAL_REF,
    timeoutMs: 60_000,
    plans: await plansPromise,
    projectRoot: PROJECT_ROOT,
    contextRoot: PROJECT_ROOT,
    satisfiedPlans: [],
    satisfiedTasks: [],
    evidence: [],
    ...overrides,
  };
}

async function continuationExecutionOptions(overrides = {}) {
  const initialStatus = `?? ${MEASUREMENTS}\0?? ${POSTGRES_TEST}\0`;
  return {
    initialStatus,
    options: await baseExecutionOptions('02-01-T1', {
      mode: 'continue-task',
      continuation: {
        expectedStatusSha256: sha256Hex(initialStatus),
        baselineFiles: [
          { path: MEASUREMENTS, sha256: HASH_A },
          { path: POSTGRES_TEST, sha256: HASH_B },
        ],
        correctionFiles: [POSTGRES_TEST],
        feedback: 'Ajuste somente o oráculo da exceção observada.',
        ...overrides,
      },
    }),
  };
}

test('descobre os 32 planos da fase 02 e preserva a ordem', async () => {
  const plans = await plansPromise;

  assert.equal(plans.length, 32);
  assert.deepEqual(plans.slice(0, 3).map((plan) => plan.id), ['02-01', '02-02', '02-03']);
  assert.deepEqual(plans.slice(-2).map((plan) => plan.id), ['02-31', '02-32']);
  assert.ok(plans.every((plan) => plan.autonomous === false));
});

test('sentinela de ausência de arquivos só é aceita em checkpoint', async () => {
  const checkpointPlan = (await plansPromise).find((plan) => plan.id === '02-27');
  assert.deepEqual(checkpointPlan.tasks[0].files, []);

  const executablePlan = `---
phase: fixture
plan: 99
wave: 99
depends_on: []
files_modified: []
autonomous: false
requirements: []
---
<task type="auto">
  <name>Task executável inválida</name>
  <files>None — checkpoint evidence only; no production or test file is owned.</files>
</task>`;

  assert.throws(
    () => parsePlan(executablePlan, resolve(PROJECT_ROOT, '02-99-PLAN.md'), 2, PROJECT_ROOT),
    /fora da allowlist/u,
  );
});

test('parsePlan aceita arquivos separados por ponto e vírgula e vírgula', () => {
  const mixedPlan = `---
phase: fixture
plan: "99"
wave: 1
depends_on: []
files_modified:
  - scripts/one.mjs
  - scripts/two.mjs
  - scripts/three.mjs
autonomous: false
requirements: [TEST-01]
---
<tasks>
<task type="auto">
<name>fixture task</name>
<files>scripts/one.mjs; scripts/two.mjs, scripts/three.mjs</files>
<precondition></precondition>
<verify><automated>node --test scripts/fixture.test.mjs</automated></verify>
</task>
</tasks>`;

  const plan = parsePlan(mixedPlan, resolve(PROJECT_ROOT, '99-99-PLAN.md'), 99, PROJECT_ROOT);
  assert.deepEqual(plan.tasks[0].files, [
    'scripts/one.mjs',
    'scripts/two.mjs',
    'scripts/three.mjs',
  ]);
});

test('dry-run da onda 1 libera 02-01 e 02-02, mas bloqueia 02-03 no gate A-7', async () => {
  const result = buildWaveDryRun(await plansPromise, 2, 1, await skillManifestPromise);

  assert.deepEqual(result.initialDispatch.map((item) => item.task), ['02-01-T1', '02-02-T1']);
  assert.deepEqual(result.blockedDispatch, [{
    plan: '02-03',
    task: '02-03-T1',
    waitsForTasks: ['02-01-T1'],
    waitsForEvidence: ['A-7'],
  }]);
  assert.deepEqual(result.overlap, []);
  assert.deepEqual(result.concurrency, { codingProcesses: 3, verificationProcesses: 1 });
  assert.ok(Object.values(result.safety).every((value) => value === false));
  assert.deepEqual(result.initialDispatch[0].skillRouting.bundles, ['backend-api']);
  assert.ok(result.initialDispatch[0].skillRouting.workerSkills.includes('betufu-wave-guardrails'));
  assert.ok(result.initialDispatch[0].skillRouting.reviewers.includes('verification'));
});

test('manifesto seleciona bundles por paths e palavras sem carregar skill externa no worker', async () => {
  const manifest = await skillManifestPromise;
  assert.equal(validateSkillManifest(manifest), true);
  const plan = {
    id: '03-15',
    filePath: '.planning/phases/03/03-15-PLAN.md',
    requirements: ['SPRT-16'],
  };
  const task = {
    id: '03-15-T5',
    type: 'auto',
    name: 'Entregar componente owner-only com Signal store',
    precondition: '',
    files: [
      'frontend/src/app/features/sports/athlete-profile/athlete-profile.store.ts',
      'frontend/src/app/features/sports/athlete-profile/athlete-photo-field.component.ts',
    ],
    verificationCommands: ['npm --prefix frontend run test -- --watch=false'],
  };
  const routing = selectSkillRouting(plan, task, manifest);

  assert.deepEqual(routing.bundles, ['security', 'frontend-angular']);
  assert.deepEqual(routing.workerSkills, [
    'betufu-wave-guardrails',
    'angular-signals-patterns',
    'frontend-accessibility-wcag',
  ]);
  assert.ok(routing.controlSkills.includes('gsd-secure-phase'));
  assert.ok(routing.controlSkills.includes('penetration-testing-with-strix'));
  assert.ok(!routing.workerSkills.includes('penetration-testing-with-strix'));
  assert.ok(routing.reviewers.includes('security'));
  assert.ok(routing.reviewers.includes('ui'));
});

test('manifesto roteia tarefas de verificacao para verification-review', async () => {
  const manifest = await skillManifestPromise;
  const plan = {
    id: '99-01',
    filePath: '.planning/phases/99/99-01-PLAN.md',
    requirements: [],
  };
  const task = {
    id: '99-01-T1',
    type: 'auto',
    name: 'Auditar snapshot congelado e verificar integracao',
    precondition: '',
    files: ['.agents/agy-review.mjs'],
    verificationCommands: [],
  };
  const routing = selectSkillRouting(plan, task, manifest);

  assert.deepEqual(routing.bundles, ['verification-review']);
  assert.ok(routing.reviewers.includes('verification'));
  assert.ok(routing.reviewers.includes('security'));
  assert.ok(routing.reviewers.includes('ui'));
  assert.ok(routing.gates.includes('aggregate-backend'));
});

test('manifesto falha fechado para bundle ou skill local invalida', async () => {
  const manifest = structuredClone(await skillManifestPromise);
  manifest.routing[0].bundle = 'inexistente';
  assert.throws(() => validateSkillManifest(manifest), /bundle inexistente/u);

  const missing = structuredClone(await skillManifestPromise);
  missing.bundles.planning.worker_skills = ['skill-inexistente'];
  missing.selection.always_worker_skills = ['skill-inexistente'];
  const plans = await plansPromise;
  const plan = plans.find((candidate) => candidate.id === '02-01');
  const routing = selectSkillRouting(plan, plan.tasks[0], missing);
  assert.deepEqual(routing.workerSkills, ['skill-inexistente', 'betufu-wave-guardrails']);
  await assert.rejects(
    loadWorkerSkillGuidance(PROJECT_ROOT, routing),
    /worker skill local ausente/u,
  );
});

test('comando Antigravity fixa modelo, sandbox, modo e schema sem bypass perigoso', () => {
  const args = buildAgyArgs('C:\\worktree', 'prompt seguro');

  assert.deepEqual(args.slice(0, 6), [
    '--add-dir', 'C:\\worktree',
    '--model', 'gemini-3.7-flash-high',
    '--mode', 'accept-edits',
  ]);
  assert.ok(args.includes('--sandbox'));
  assert.ok(args.includes('--json-schema'));
  assert.ok(!args.includes('--dangerously-skip-permissions'));
  assert.ok(!args.includes('--disable-slash-commands'));
});

test('prompt declara modo executor e proíbe terminal, Git, rede, dependências e expansão de escopo', async () => {
  const plan = (await plansPromise).find((candidate) => candidate.id === '02-01');
  const prompt = buildExecutorPrompt(plan, plan.tasks[0], 'CONTRATO_LOCAL_COMPLETO');

  assert.match(prompt, /^Modo EXECUTOR\./u);
  assert.match(prompt, /Não use terminal, shell, PowerShell, cmd/u);
  assert.match(prompt, /nem mesmo para ler, listar ou buscar arquivos/u);
  assert.match(prompt, /somente as ferramentas nativas do Antigravity/u);
  assert.match(prompt, /Não execute comandos Git/u);
  assert.match(prompt, /não instale dependências/u);
  assert.match(prompt, /não use rede/u);
  assert.match(prompt, /Nunca inclua segredo/u);
  assert.match(prompt, /BLOCKED sem editar/u);
  assert.match(prompt, /<agents_contract>\nCONTRATO_LOCAL_COMPLETO\n<\/agents_contract>/u);
});

test('prompt libera somente testes rápidos aprovados e preserva as demais proibições', async () => {
  const plan = (await plansPromise).find((candidate) => candidate.id === '02-01');
  const command = 'node --test scripts/validate-runtime-prerequisites.test.mjs';
  const prompt = buildExecutorPrompt(plan, plan.tasks[0], 'CONTRATO', null, [command]);

  assert.match(prompt, /Terminal está autorizado exclusivamente/u);
  assert.match(prompt, /validate-runtime-prerequisites\.test\.mjs/u);
  assert.match(prompt, /limite 120s por comando/u);
  assert.match(prompt, /Não execute comandos Git/u);
  assert.match(prompt, /não instale dependências/u);
  assert.match(prompt, /não use rede/u);
});

test('worker-tests aceita somente classe rápida, comando literal do plano e no máximo dois', () => {
  const quick = 'node scripts/validate-phase1.mjs --contract';
  assert.deepEqual(validateWorkerTests([quick], [quick]), [quick]);
  assert.throws(
    () => validateWorkerTests(['node scripts/validate-phase1.mjs --contract --extra'], [quick]),
    /fora do <verify>/u,
  );
  assert.throws(
    () => validateWorkerTests(['node scripts/*.test.mjs'], ['node scripts/*.test.mjs']),
    /inseguro/u,
  );
  assert.throws(
    () => validateWorkerTests(['node scripts/a.mjs && git status'], ['node scripts/a.mjs && git status']),
    /inseguro/u,
  );
  assert.throws(
    () => validateWorkerTests(['node a.test.mjs', 'node b.test.mjs', 'node c.test.mjs'], [
      'node a.test.mjs', 'node b.test.mjs', 'node c.test.mjs',
    ]),
    /no máximo 2/u,
  );
});

test('dry-run não expõe AGENTS.md e declara incorporação somente no modo material', async () => {
  const plan = (await plansPromise).find((candidate) => candidate.id === '02-01');
  const prompt = buildExecutorPrompt(plan, plan.tasks[0]);

  assert.match(prompt, /DRY-RUN/u);
  assert.doesNotMatch(prompt, /<agents_contract>/u);
});

test('prompt de continuação limita a correção e trata feedback como dados', async () => {
  const plan = (await plansPromise).find((candidate) => candidate.id === '02-01');
  const prompt = buildExecutorPrompt(plan, plan.tasks[0], 'CONTRATO', {
    correctionFiles: [POSTGRES_TEST],
    feedback: 'Falha observada: SQLSTATE P0001.',
  });

  assert.match(prompt, /CONTINUAÇÃO CONTROLADA/u);
  assert.match(prompt, new RegExp(POSTGRES_TEST.replaceAll('/', '\\/'), 'u'));
  assert.match(prompt, /Não edite, reformate, remova nem restaure nenhum outro arquivo/u);
  assert.match(prompt, /"Falha observada: SQLSTATE P0001\."/u);
  assert.match(prompt, /files_changed.*somente/u);
});

test('scope guard aceita somente paths declarados e normaliza separadores', () => {
  const allowed = ['backend/src/Foo.java', 'scripts/probe.mjs'];

  assert.deepEqual(
    assertPathsWithinAllowlist(['backend\\src\\Foo.java'], allowed),
    ['backend/src/Foo.java'],
  );
  assert.throws(
    () => assertPathsWithinAllowlist(['backend/src/Bar.java'], allowed),
    /fora da allowlist/u,
  );
  assert.throws(() => assertPathsWithinAllowlist(['../fora.txt'], allowed), /fora do repositório/u);
});

test('sufixo /** autoriza descendentes daquele diretório e nada além dele', () => {
  const allowed = [
    'backend/openapi/betufu.yaml',
    'frontend/src/app/api/generated/**',
  ];

  // Positivo: filho direto.
  assert.deepEqual(
    assertPathsWithinAllowlist(['frontend/src/app/api/generated/api.ts'], allowed),
    ['frontend/src/app/api/generated/api.ts'],
  );

  // Positivo: filho aninhado em profundidade arbitrária.
  assert.deepEqual(
    assertPathsWithinAllowlist(['frontend/src/app/api/generated/model/role-status-dto.ts'], allowed),
    ['frontend/src/app/api/generated/model/role-status-dto.ts'],
  );

  // Positivo: separador Windows continua normalizado antes do casamento.
  assert.deepEqual(
    assertPathsWithinAllowlist(['frontend\\src\\app\\api\\generated\\model\\models.ts'], allowed),
    ['frontend/src/app/api/generated/model/models.ts'],
  );

  // Positivo: path exato declarado ao lado do sufixo continua valendo.
  assert.deepEqual(
    assertPathsWithinAllowlist(['backend/openapi/betufu.yaml'], allowed),
    ['backend/openapi/betufu.yaml'],
  );

  // Negativo: diretório irmão com prefixo semelhante. Sem a fronteira de barra este
  // caminho passaria por simples startsWith, que e exatamente o risco da correcao.
  assert.throws(
    () => assertPathsWithinAllowlist(['frontend/src/app/api/generated-malicioso/arquivo.ts'], allowed),
    /fora da allowlist/u,
  );

  // Negativo: o proprio diretorio, sem descendente, nao e um arquivo autorizado.
  assert.throws(
    () => assertPathsWithinAllowlist(['frontend/src/app/api/generated/'], allowed),
    /fora da allowlist/u,
  );

  // Negativo: irmao fora da arvore coberta.
  assert.throws(
    () => assertPathsWithinAllowlist(['frontend/src/app/api/handwritten/api.ts'], allowed),
    /fora da allowlist/u,
  );

  // Negativo: path fora do repositório continua recusado antes de qualquer casamento.
  assert.throws(
    () => assertPathsWithinAllowlist(['../fora.ts'], allowed),
    /fora do repositório/u,
  );
});

test('allowlist recusa curinga que não seja o sufixo /** exato', () => {
  assert.throws(
    () => assertPathsWithinAllowlist(['frontend/a.ts'], ['frontend/*.ts']),
    /somente caminho exato ou sufixo/u,
  );
  assert.throws(
    () => assertPathsWithinAllowlist(['frontend/a.ts'], ['frontend/**/model/*.ts']),
    /somente caminho exato ou sufixo/u,
  );
  assert.throws(
    () => assertPathsWithinAllowlist(['frontend/a.ts'], ['frontend/{a,b}/x.ts']),
    /somente caminho exato ou sufixo/u,
  );
  assert.throws(
    () => assertPathsWithinAllowlist(['frontend/a.ts'], ['frontend/a?.ts']),
    /somente caminho exato ou sufixo/u,
  );
});

test('worktree deve ser descendente estrita da raiz aprovada', () => {
  assert.equal(assertPathWithinRoot(WORKTREE, WORKSPACE_ROOT), WORKTREE);
  assert.throws(() => assertPathWithinRoot(WORKSPACE_ROOT, WORKSPACE_ROOT), /fora da raiz/u);
  assert.throws(
    () => assertPathWithinRoot(resolve(WORKSPACE_ROOT, '..', 'vizinho'), WORKSPACE_ROOT),
    /fora da raiz/u,
  );
});

test('parser porcelain inclui origem e destino de rename', () => {
  assert.deepEqual(
    parsePorcelainPaths('R  novo.txt\0antigo.txt\0?? criado.txt\0'),
    ['novo.txt', 'antigo.txt', 'criado.txt'],
  );
});

test('resposta Antigravity exige envelope e payload estruturados', () => {
  const parsed = parseAgyOutput(
    JSON.stringify({ status: 'SUCCESS', result: JSON.stringify(workerPayload()) }),
    '02-01',
    '02-01-T1',
  );
  assert.equal(parsed.status, 'COMPLETED');
  const structured = parseAgyOutput(
    JSON.stringify({ status: 'SUCCESS', structured_output: workerPayload() }),
    '02-01',
    '02-01-T1',
  );
  assert.equal(structured.status, 'COMPLETED');
  assert.throws(() => parseAgyOutput('não-json', '02-01', '02-01-T1'), /JSON inválido/u);
  assert.throws(
    () => parseAgyOutput(JSON.stringify(workerPayload({ task_id: '02-01-T2' })), '02-01', '02-01-T1'),
    /resposta pertence/u,
  );
});

test('parser aceita e descarta somente metadados conhecidos do Antigravity', () => {
  const parsed = parseAgyOutput(
    JSON.stringify({
      status: 'SUCCESS',
      result: JSON.stringify(workerPayload({
        toolAction: { name: 'replace', content: 'não persistir' },
        toolSummary: 'conteúdo de telemetria que não deve chegar ao registro',
      })),
    }),
    '02-01',
    '02-01-T1',
  );

  assert.equal(parsed.status, 'COMPLETED');
  assert.equal(Object.hasOwn(parsed, 'toolAction'), false);
  assert.equal(Object.hasOwn(parsed, 'toolSummary'), false);
  assert.doesNotMatch(JSON.stringify(parsed), /não persistir|telemetria/u);
});

test('parser continua bloqueando metadado desconhecido', () => {
  assert.throws(
    () => parseAgyOutput(
      JSON.stringify(workerPayload({ unexpectedMetadata: 'bloquear' })),
      '02-01',
      '02-01-T1',
    ),
    /campos extras: unexpectedMetadata/u,
  );
});

test('execução simulada valida Git, escopo, resultado e grava somente registro sanitizado', async () => {
  const fake = fakeProcesses();
  let written = null;
  const result = await executeTask(await baseExecutionOptions(), {
    runProcess: fake.runProcess,
    writeRecord: async (commonDirectory, record) => {
      written = { commonDirectory, record };
      return resolve(commonDirectory, 'result.json');
    },
  });

  assert.equal(result.status, 'COMPLETED');
  assert.equal(written.commonDirectory, COMMON_GIT_DIRECTORY);
  assert.deepEqual(written.record.filesChanged, workerPayload().files_changed);
  assert.equal(fake.calls.filter((call) => call.command === 'agy').length, 1);
  const agyCall = fake.calls.find((call) => call.command === 'agy');
  assert.ok(agyCall.args.includes('--sandbox'));
  assert.ok(!agyCall.args.includes('--dangerously-skip-permissions'));
});

test('execução registra worker-test rápido aprovado e concluído', async () => {
  const command = 'node scripts/validate-phase1.mjs --contract';
  const sourcePlans = await plansPromise;
  const plans = sourcePlans.map((plan) => plan.id !== '02-01'
    ? plan
    : {
        ...plan,
        tasks: plan.tasks.map((task, index) => index === 0
          ? { ...task, verificationCommands: [command] }
          : task),
      });
  const fake = fakeProcesses({
    agyStdout: JSON.stringify({
      status: 'SUCCESS',
      result: JSON.stringify(workerPayload({
        tests_executed: [command],
        tests_requested: [],
      })),
    }),
  });
  let written = null;
  const result = await executeTask(await baseExecutionOptions('02-01-T1', {
    plans,
    workerTests: [command],
  }), {
    runProcess: fake.runProcess,
    writeRecord: async (_commonDirectory, record) => {
      written = record;
      return 'result.json';
    },
  });

  assert.deepEqual(result.testsExecuted, [command]);
  assert.deepEqual(written.testsExecuted, [command]);
  const prompt = fake.calls.find((call) => call.command === 'agy').args.at(-1);
  assert.match(prompt, /validate-phase1\.mjs --contract/u);
});

test('execução bloqueia teste declarado como executado sem autorização', async () => {
  const command = workerPayload().tests_requested[0];
  const fake = fakeProcesses({
    agyStdout: JSON.stringify({
      status: 'SUCCESS',
      result: JSON.stringify(workerPayload({ tests_executed: [command] })),
    }),
  });

  await assert.rejects(
    executeTask(await baseExecutionOptions(), {
      runProcess: fake.runProcess,
      writeRecord: async () => 'result.json',
    }),
    /tests_executed diverge da autorização/u,
  );
});

test('continuação aceita somente snapshot exato e registra apenas o delta desta chamada', async () => {
  const continuation = await continuationExecutionOptions();
  const fake = fakeProcesses({
    initialStatus: continuation.initialStatus,
    finalStatus: continuation.initialStatus,
    initialHashes: { [MEASUREMENTS]: HASH_A, [POSTGRES_TEST]: HASH_B },
    finalHashes: { [MEASUREMENTS]: HASH_A, [POSTGRES_TEST]: HASH_C },
  });
  let written = null;
  const result = await executeTask(continuation.options, {
    runProcess: fake.runProcess,
    hashPath: fake.hashPath,
    writeRecord: async (commonDirectory, record) => {
      written = { commonDirectory, record };
      return resolve(commonDirectory, 'continuation-result.json');
    },
  });

  assert.equal(result.mode, 'continuation');
  assert.deepEqual(result.filesChanged, [POSTGRES_TEST]);
  assert.deepEqual(result.worktreeFiles, [MEASUREMENTS, POSTGRES_TEST].sort());
  assert.deepEqual(written.record.snapshot.correctionFiles, [POSTGRES_TEST]);
  assert.equal(written.record.snapshot.initialStatusSha256, sha256Hex(continuation.initialStatus));
  const agyCall = fake.calls.find((call) => call.command === 'agy');
  const prompt = agyCall.args.at(-1);
  assert.match(prompt, /CONTINUAÇÃO CONTROLADA/u);
  assert.match(prompt, /oráculo da exceção observada/u);
});

test('continuação aceita arquivo sujo fora da task somente quando declarado e congelado', async () => {
  const initialStatus = ` M ${FROZEN_PLAN}\0?? ${POSTGRES_TEST}\0`;
  const options = await baseExecutionOptions('02-01-T1', {
    mode: 'continue-task',
    continuation: {
      expectedStatusSha256: sha256Hex(initialStatus),
      baselineFiles: [
        { path: FROZEN_PLAN, sha256: HASH_A },
        { path: POSTGRES_TEST, sha256: HASH_B },
      ],
      correctionFiles: [POSTGRES_TEST],
      frozenFiles: [FROZEN_PLAN],
      feedback: 'Implemente somente o teste autorizado.',
    },
  });
  const fake = fakeProcesses({
    initialStatus,
    finalStatus: initialStatus,
    initialHashes: { [FROZEN_PLAN]: HASH_A, [POSTGRES_TEST]: HASH_B },
    finalHashes: { [FROZEN_PLAN]: HASH_A, [POSTGRES_TEST]: HASH_C },
  });
  let written = null;

  const result = await executeTask(options, {
    runProcess: fake.runProcess,
    hashPath: fake.hashPath,
    writeRecord: async (_commonDirectory, record) => {
      written = record;
      return 'continuation-frozen-result.json';
    },
  });

  assert.deepEqual(result.worktreeFiles, [FROZEN_PLAN, POSTGRES_TEST].sort());
  assert.deepEqual(written.snapshot.frozenFiles, [FROZEN_PLAN]);
  const prompt = fake.calls.find((call) => call.command === 'agy').args.at(-1);
  assert.match(prompt, /Arquivos congelados/u);
  assert.match(prompt, new RegExp(FROZEN_PLAN.replaceAll('/', '\\/'), 'u'));
});

test('continuação bloqueia alteração posterior em frozen-file', async () => {
  const initialStatus = ` M ${FROZEN_PLAN}\0?? ${POSTGRES_TEST}\0`;
  const options = await baseExecutionOptions('02-01-T1', {
    mode: 'continue-task',
    continuation: {
      expectedStatusSha256: sha256Hex(initialStatus),
      baselineFiles: [
        { path: FROZEN_PLAN, sha256: HASH_A },
        { path: POSTGRES_TEST, sha256: HASH_B },
      ],
      correctionFiles: [POSTGRES_TEST],
      frozenFiles: [FROZEN_PLAN],
      feedback: 'Implemente somente o teste autorizado.',
    },
  });
  const fake = fakeProcesses({
    initialStatus,
    finalStatus: initialStatus,
    initialHashes: { [FROZEN_PLAN]: HASH_A, [POSTGRES_TEST]: HASH_B },
    finalHashes: { [FROZEN_PLAN]: HASH_C, [POSTGRES_TEST]: HASH_C },
  });

  await assert.rejects(
    executeTask(options, {
      runProcess: fake.runProcess,
      hashPath: fake.hashPath,
      writeRecord: async () => 'não-deveria-gravar',
    }),
    /baseline bloqueada.*02-15-PLAN\.md/u,
  );
});

test('continuação exige frozen-file já sujo e separado dos arquivos de correção', async () => {
  const cleanFrozen = await continuationExecutionOptions({ frozenFiles: [FROZEN_PLAN] });
  const cleanFake = fakeProcesses({
    initialStatus: cleanFrozen.initialStatus,
    initialHashes: { [MEASUREMENTS]: HASH_A, [POSTGRES_TEST]: HASH_B },
  });
  await assert.rejects(
    executeTask(cleanFrozen.options, {
      runProcess: cleanFake.runProcess,
      hashPath: cleanFake.hashPath,
    }),
    /frozen-file deve estar previamente modificado.*02-15-PLAN\.md/u,
  );

  const overlap = await continuationExecutionOptions({ frozenFiles: [POSTGRES_TEST] });
  const overlapFake = fakeProcesses({ initialStatus: overlap.initialStatus });
  await assert.rejects(
    executeTask(overlap.options, {
      runProcess: overlapFake.runProcess,
      hashPath: overlapFake.hashPath,
    }),
    /frozen-file não pode ser correction-file.*PostgresSemanticsIT\.java/u,
  );
});

test('continuação bloqueia drift de status ou hash antes de chamar Antigravity', async () => {
  const continuation = await continuationExecutionOptions();
  const statusDrift = fakeProcesses({
    initialStatus: `?? ${POSTGRES_TEST}\0`,
  });
  await assert.rejects(
    executeTask(continuation.options, {
      runProcess: statusDrift.runProcess,
      hashPath: statusDrift.hashPath,
    }),
    /git status divergiu/u,
  );
  assert.equal(statusDrift.calls.filter((call) => call.command === 'agy').length, 0);

  const hashDrift = fakeProcesses({
    initialStatus: continuation.initialStatus,
    initialHashes: { [MEASUREMENTS]: HASH_C, [POSTGRES_TEST]: HASH_B },
  });
  await assert.rejects(
    executeTask(continuation.options, {
      runProcess: hashDrift.runProcess,
      hashPath: hashDrift.hashPath,
    }),
    /SHA-256 divergente.*02-MEASUREMENTS\.md/u,
  );
  assert.equal(hashDrift.calls.filter((call) => call.command === 'agy').length, 0);
});

test('continuação bloqueia mudança em arquivo preexistente fora da correção', async () => {
  const continuation = await continuationExecutionOptions();
  const fake = fakeProcesses({
    initialStatus: continuation.initialStatus,
    finalStatus: continuation.initialStatus,
    initialHashes: { [MEASUREMENTS]: HASH_A, [POSTGRES_TEST]: HASH_B },
    finalHashes: { [MEASUREMENTS]: HASH_C, [POSTGRES_TEST]: HASH_C },
  });

  await assert.rejects(
    executeTask(continuation.options, {
      runProcess: fake.runProcess,
      hashPath: fake.hashPath,
      writeRecord: async () => 'não-deveria-gravar',
    }),
    /baseline bloqueada.*02-MEASUREMENTS\.md/u,
  );
  assert.equal(fake.calls.filter((call) => call.command === 'agy').length, 1);
});

test('árvore inicialmente suja bloqueia antes de chamar Antigravity', async () => {
  const fake = fakeProcesses({ initialStatus: '?? fora.txt\0' });
  await assert.rejects(
    executeTask(await baseExecutionOptions(), { runProcess: fake.runProcess }),
    /deve estar limpa/u,
  );
  assert.equal(fake.calls.filter((call) => call.command === 'agy').length, 0);
});

test('alteração fora da task bloqueia mesmo que o worker declare sucesso', async () => {
  const fake = fakeProcesses({ finalStatus: '?? pom.xml\0' });
  await assert.rejects(
    executeTask(await baseExecutionOptions(), {
      runProcess: fake.runProcess,
      writeRecord: async () => 'não-deveria-gravar',
    }),
    /fora da allowlist/u,
  );
});

test('processo Antigravity com erro ainda passa pela auditoria de escopo', async () => {
  const fake = fakeProcesses({ finalStatus: '?? pom.xml\0' });
  const originalRun = fake.runProcess;
  fake.runProcess = async (command, args) => {
    const result = await originalRun(command, args);
    return command === 'agy' ? { ...result, exitCode: 7 } : result;
  };

  await assert.rejects(
    executeTask(await baseExecutionOptions(), { runProcess: fake.runProcess }),
    /fora da allowlist/u,
  );
  assert.equal(fake.calls.filter((call) => call.command === 'agy').length, 1);
  assert.ok(fake.calls.some((call) => call.command === 'git'
    && call.args.includes('--untracked-files=all')));
});

test('status ERROR grava diagnóstico sanitizado sem payload, mensagem ou segredo', async () => {
  const continuation = await continuationExecutionOptions();
  const secret = 'SEGREDO_NAO_PERSISTIR';
  const agyStdout = JSON.stringify({
    status: 'ERROR',
    error: { code: 'PROTOCOL_FAILURE', type: 'ENVELOPE', message: secret },
    response: { detail: secret },
  });
  const fake = fakeProcesses({
    initialStatus: continuation.initialStatus,
    finalStatus: continuation.initialStatus,
    initialHashes: { [MEASUREMENTS]: HASH_A, [POSTGRES_TEST]: HASH_B },
    finalHashes: { [MEASUREMENTS]: HASH_A, [POSTGRES_TEST]: HASH_C },
    agyStdout,
    agyStderr: secret,
  });
  let written = null;

  await assert.rejects(
    executeTask(continuation.options, {
      runProcess: fake.runProcess,
      hashPath: fake.hashPath,
      writeRecord: async (_commonDirectory, record) => {
        written = record;
        return resolve(COMMON_GIT_DIRECTORY, 'diagnostic.json');
      },
    }),
    /status ERROR.*diagnóstico sanitizado/u,
  );

  assert.equal(written.status, 'ERROR');
  assert.equal(written.diagnostic.envelopeStatus, 'ERROR');
  assert.equal(written.diagnostic.errorCode, 'PROTOCOL_FAILURE');
  assert.equal(written.diagnostic.errorType, 'ENVELOPE');
  assert.deepEqual(written.invocationFiles, [POSTGRES_TEST]);
  const serialized = JSON.stringify(written);
  assert.doesNotMatch(serialized, new RegExp(secret, 'u'));
  assert.doesNotMatch(serialized, /detail/u);
  assert.ok(written.diagnostic.stdoutBytes > 0);
  assert.ok(written.diagnostic.stderrBytes > 0);
});

test('sanitizador guarda somente metadados estruturais do envelope', () => {
  const diagnostic = sanitizeAgyDiagnostic({
    ...processResult(JSON.stringify({
      status: 'ERROR',
      error: { code: 'SAFE_CODE', message: 'senha=nao-registrar' },
      output: 'conteudo-nao-registrar',
    })),
    stderr: 'tambem-nao-registrar',
  });

  assert.equal(diagnostic.envelopeStatus, 'ERROR');
  assert.equal(diagnostic.errorCode, 'SAFE_CODE');
  assert.deepEqual(diagnostic.envelopeKeys, ['error', 'output', 'status']);
  assert.doesNotMatch(JSON.stringify(diagnostic), /nao-registrar/u);
});

test('sanitizador classifica falha conhecida de permissão sem guardar mensagem', () => {
  const diagnostic = sanitizeAgyDiagnostic({
    ...processResult(JSON.stringify({
      status: 'ERROR',
      error: 'permission check failed para Get-Location com detalhe privado',
    })),
  });

  assert.equal(diagnostic.errorCode, 'PERMISSION_CHECK_FAILED');
  assert.doesNotMatch(JSON.stringify(diagnostic), /Get-Location|detalhe privado/u);
});

test('drift de branch, HEAD ou JSON bloqueia o resultado', async () => {
  const wrongBranch = fakeProcesses({ branch: 'branch-errada' });
  await assert.rejects(
    executeTask(await baseExecutionOptions(), { runProcess: wrongBranch.runProcess }),
    /branch divergente/u,
  );

  const changedHead = fakeProcesses({ finalHead: 'b'.repeat(40) });
  await assert.rejects(
    executeTask(await baseExecutionOptions(), { runProcess: changedHead.runProcess }),
    /alterou HEAD/u,
  );

  const invalidJson = fakeProcesses({ finalStatus: '', agyStdout: '{inválido' });
  await assert.rejects(
    executeTask(await baseExecutionOptions(), {
      runProcess: invalidJson.runProcess,
      writeRecord: async () => 'result.json',
    }),
    /JSON inválido/u,
  );
});

test('contextRoot deve ficar dentro da worktree e pode ser a propria raiz', async () => {
  assert.equal(assertPathWithinOrEqualRoot(WORKTREE, WORKTREE, 'contextRoot'), WORKTREE);
  assert.equal(
    assertPathWithinOrEqualRoot(resolve(WORKTREE, '.agents'), WORKTREE, 'contextRoot'),
    resolve(WORKTREE, '.agents'),
  );
  assert.throws(
    () => assertPathWithinOrEqualRoot(resolve(WORKSPACE_ROOT, 'outra-worktree'), WORKTREE, 'contextRoot'),
    /contextRoot fora da raiz/u,
  );

  const fake = fakeProcesses();
  await assert.rejects(
    executeTask(await baseExecutionOptions('02-01-T1', {
      contextRoot: resolve(WORKSPACE_ROOT, 'outra-worktree'),
    }), { runProcess: fake.runProcess }),
    /contextRoot fora da raiz/u,
  );
  assert.equal(fake.calls.length, 0);
});

test('prompt incorpora somente guidance local e explicita gates e skills de controle', async () => {
  const plan = (await plansPromise).find((candidate) => candidate.id === '02-01');
  const prompt = buildExecutorPrompt(plan, plan.tasks[0], 'CONTRATO', null, [], {
    bundles: ['backend-api'],
    gates: ['scope', 'focal-tests'],
    workerSkills: ['betufu-wave-guardrails'],
    controlSkills: ['change-review'],
    guidance: [{
      skill: 'betufu-wave-guardrails',
      path: '.agents/skills/betufu-wave-guardrails/SKILL.md',
      content: 'GUIDANCE_LOCAL',
    }],
  });

  assert.match(prompt, /Bundles selecionados deterministicamente: backend-api/u);
  assert.match(prompt, /Gates .*scope, focal-tests/u);
  assert.match(prompt, /<worker_skill name="betufu-wave-guardrails"/u);
  assert.match(prompt, /GUIDANCE_LOCAL/u);
  assert.doesNotMatch(prompt, /conteúdo.*change-review/iu);
});

test('checkpoint e pré-condição parcial nunca são enviados automaticamente', async () => {
  await assert.rejects(executeTask(await baseExecutionOptions('02-01-T3')), /checkpoint humano/u);

  const phaseGate = await baseExecutionOptions('02-03-T1');
  await assert.rejects(executeTask(phaseGate), /02-01-T1.*A-7/u);
});

test('config guard exige execução interativa sem avanço automático', () => {
  const valid = {
    mode: 'interactive',
    workflow: { auto_advance: false },
    manager: { flags: { execute: '--interactive' } },
    agent_skills: {
      manifest: '.agents/skill-bundles.json',
      selection: 'deterministic',
      always_worker_skills: ['betufu-wave-guardrails'],
      worker_content: 'local-only',
      missing_worker_skill: 'fail',
      external_skills: 'control-plane-only',
    },
  };

  assert.equal(validateInteractiveConfig(valid), true);
  assert.throws(
    () => validateInteractiveConfig({ ...valid, workflow: { auto_advance: true } }),
    /auto_advance/u,
  );
  assert.throws(
    () => validateInteractiveConfig({ ...valid, manager: { flags: { execute: '--interactive --all' } } }),
    /flags proibidas/u,
  );
});

test('CLI exige exatamente um modo e argumentos completos para execução material', () => {
  assert.deepEqual(
    parseArguments(['--phase', '2', '--wave', '1', '--dry-run']),
    { mode: 'dry-run', phase: 2, wave: 1 },
  );
  assert.throws(() => parseArguments(['--phase', '2', '--wave', '1']), /exatamente um modo/u);
  assert.throws(
    () => parseArguments(['--phase', '2', '--wave', '1', '--dry-run', '--force']),
    /desconhecido ou sem valor/u,
  );
  assert.throws(
    () => parseArguments(['--phase', '2', '--wave', '1', '--execute-task']),
    /argumentos obrigatórios ausentes/u,
  );
  assert.throws(
    () => parseArguments([
      '--phase', '2', '--wave', '1', '--dry-run',
      '--worker-test', 'node scripts/validate-phase1.mjs --contract',
    ]),
    /dry-run.*execução material/u,
  );

  const material = parseArguments([
    '--phase', '2', '--wave', '1', '--execute-task',
    '--task', '02-01-T1', '--worktree', WORKTREE, '--workspace-root', WORKSPACE_ROOT,
    '--expected-base', BASE, '--expected-branch', BRANCH,
    '--run-id', RUN_ID, '--approval-ref', APPROVAL_REF,
    '--worker-test', 'node scripts/validate-phase1.mjs --contract',
  ]);
  assert.deepEqual(material.workerTests, ['node scripts/validate-phase1.mjs --contract']);
});

test('execução material lê planos do worktree e dry-run mantém o checkout de controle', () => {
  assert.equal(selectPlanRoot({ mode: 'dry-run' }, PROJECT_ROOT), PROJECT_ROOT);
  assert.equal(
    selectPlanRoot({ mode: 'continue-task', worktree: WORKTREE }, PROJECT_ROOT),
    WORKTREE,
  );
  assert.equal(
    selectPlanRoot({ mode: 'execute-task', worktree: WORKTREE }, PROJECT_ROOT),
    WORKTREE,
  );
});

test('CLI de continuação exige snapshot, baseline, correção e feedback explícitos', () => {
  const statusHash = 'D'.repeat(64);
  const common = [
    '--phase', '2', '--wave', '1', '--continue-task',
    '--task', '02-01-T1',
    '--worktree', WORKTREE,
    '--workspace-root', WORKSPACE_ROOT,
    '--expected-base', BASE,
    '--expected-branch', BRANCH,
    '--run-id', RUN_ID,
    '--approval-ref', APPROVAL_REF,
  ];
  const parsed = parseArguments([
    ...common,
    '--expected-status-sha256', statusHash,
    '--baseline-file', `${MEASUREMENTS}=${HASH_A}`,
    '--baseline-file', `${POSTGRES_TEST}=${HASH_B}`,
    '--correction-file', POSTGRES_TEST,
    '--frozen-file', FROZEN_PLAN,
    '--feedback', 'Corrigir somente o oráculo.',
  ]);

  assert.equal(parsed.mode, 'continue-task');
  assert.equal(parsed.continuation.expectedStatusSha256, statusHash);
  assert.deepEqual(parsed.continuation.baselineFiles, [
    { path: MEASUREMENTS, sha256: HASH_A },
    { path: POSTGRES_TEST, sha256: HASH_B },
  ]);
  assert.deepEqual(parsed.continuation.correctionFiles, [POSTGRES_TEST]);
  assert.deepEqual(parsed.continuation.frozenFiles, [FROZEN_PLAN]);
  assert.equal(parsed.continuation.feedback, 'Corrigir somente o oráculo.');

  assert.throws(
    () => parseArguments(common),
    /argumentos de continuação ausentes/u,
  );
  assert.throws(
    () => parseArguments([
      '--phase', '2', '--wave', '1', '--execute-task',
      '--task', '02-01-T1', '--worktree', WORKTREE, '--workspace-root', WORKSPACE_ROOT,
      '--expected-base', BASE, '--expected-branch', BRANCH,
      '--run-id', RUN_ID, '--approval-ref', APPROVAL_REF,
      '--feedback', 'indevido',
    ]),
    /não aceita argumentos de continuação/u,
  );
});

test('CLI aceita próxima task somente com baseline aceita completa', () => {
  const statusHash = 'E'.repeat(64);
  const parsed = parseArguments([
    '--phase', '2', '--wave', '1', '--next-task',
    '--task', '02-01-T2', '--worktree', WORKTREE, '--workspace-root', WORKSPACE_ROOT,
    '--expected-base', BASE, '--expected-branch', BRANCH,
    '--run-id', RUN_ID, '--approval-ref', APPROVAL_REF,
    '--accepted-status-sha256', statusHash,
    '--accepted-baseline-file', `${MEASUREMENTS}=${HASH_A}`,
  ]);

  assert.equal(parsed.mode, 'next-task');
  assert.deepEqual(parsed.acceptedBaseline, {
    expectedStatusSha256: statusHash,
    baselineFiles: [{ path: MEASUREMENTS, sha256: HASH_A }],
  });
  assert.throws(
    () => parseArguments([
      '--phase', '2', '--wave', '1', '--next-task',
      '--task', '02-01-T2', '--worktree', WORKTREE, '--workspace-root', WORKSPACE_ROOT,
      '--expected-base', BASE, '--expected-branch', BRANCH,
      '--run-id', RUN_ID, '--approval-ref', APPROVAL_REF,
    ]),
    /argumentos de próxima task ausentes/u,
  );
  assert.throws(
    () => parseArguments([
      '--phase', '2', '--wave', '1', '--next-task',
      '--task', '02-01-T2', '--worktree', WORKTREE, '--workspace-root', WORKSPACE_ROOT,
      '--expected-base', BASE, '--expected-branch', BRANCH,
      '--run-id', RUN_ID, '--approval-ref', APPROVAL_REF,
      '--accepted-status-sha256', statusHash,
      '--accepted-baseline-file', `${MEASUREMENTS}=${HASH_A}`,
      '--feedback', 'atalho indevido',
    ]),
    /não aceita argumentos de continuação/u,
  );
});

test('branches master e main são rejeitadas antes do dispatch', async () => {
  for (const branch of ['master', 'main']) {
    const fake = fakeProcesses({ branch });
    await assert.rejects(
      executeTask(await baseExecutionOptions('02-01-T1', { expectedBranch: branch }), {
        runProcess: fake.runProcess,
      }),
      /master e main são proibidas/u,
    );
    assert.equal(fake.calls.filter((call) => call.command === 'agy').length, 0);
  }
});

test('registro de execução inclui run_id, aprovação e duração', async () => {
  let written = null;
  const times = [new Date('2026-09-07T10:00:00.000Z'), new Date('2026-09-07T10:00:01.250Z')];
  const fake = fakeProcesses();
  const result = await executeTask(await baseExecutionOptions(), {
    runProcess: fake.runProcess,
    now: () => times.shift(),
    writeRecord: async (_commonDirectory, record) => {
      written = record;
      return 'execution-record.json';
    },
  });

  assert.equal(result.execution.run_id, RUN_ID);
  assert.equal(result.execution.approval_reference, APPROVAL_REF);
  assert.equal(result.execution.started_at, '2026-09-07T10:00:00.000Z');
  assert.equal(result.execution.finished_at, '2026-09-07T10:00:01.250Z');
  assert.equal(result.execution.duration_ms, 1250);
  assert.deepEqual(written.execution, result.execution);
});

test('próxima task preserva baseline aceita e congela arquivos herdados', async () => {
  const initialStatus = `?? ${MEASUREMENTS}\0?? ${POSTGRES_TEST}\0`;
  const options = await baseExecutionOptions('02-01-T2', {
    satisfiedTasks: ['02-01-T1'],
    acceptedBaseline: {
      expectedStatusSha256: sha256Hex(initialStatus),
      baselineFiles: [
        { path: MEASUREMENTS, sha256: HASH_A },
        { path: POSTGRES_TEST, sha256: HASH_B },
      ],
    },
  });
  const fake = fakeProcesses({
    initialStatus,
    finalStatus: `?? ${MEASUREMENTS}\0?? ${POSTGRES_TEST}\0`,
    initialHashes: { [MEASUREMENTS]: HASH_A, [POSTGRES_TEST]: HASH_B },
    finalHashes: { [MEASUREMENTS]: HASH_C, [POSTGRES_TEST]: HASH_B },
    agyStdout: JSON.stringify({
      status: 'SUCCESS',
      result: JSON.stringify(workerPayload({
        task_id: '02-01-T2',
        files_changed: [MEASUREMENTS],
        tests_requested: [],
      })),
    }),
  });
  let written = null;
  const result = await executeTask(options, {
    runProcess: fake.runProcess,
    hashPath: fake.hashPath,
    writeRecord: async (_commonDirectory, record) => {
      written = record;
      return 'next-task-record.json';
    },
  });

  assert.equal(result.mode, 'next-task');
  assert.deepEqual(result.filesChanged, [MEASUREMENTS]);
  assert.deepEqual(written.snapshot.frozenFiles, [POSTGRES_TEST]);
  assert.deepEqual(written.snapshot.mutableBaselineFiles, [MEASUREMENTS]);
});

test('próxima task rejeita baseline com arquivo que não pertence às tasks anteriores', async () => {
  const initialStatus = `?? ${REDIS_SUPPORT}\0`;
  const options = await baseExecutionOptions('02-01-T2', {
    satisfiedTasks: ['02-01-T1'],
    acceptedBaseline: {
      expectedStatusSha256: sha256Hex(initialStatus),
      baselineFiles: [{ path: REDIS_SUPPORT, sha256: HASH_A }],
    },
  });
  const fake = fakeProcesses({
    initialStatus,
    initialHashes: { [REDIS_SUPPORT]: HASH_A },
  });

  await assert.rejects(
    executeTask(options, { runProcess: fake.runProcess, hashPath: fake.hashPath }),
    /fora da allowlist.*RedisContainerSupport\.java/u,
  );
  assert.equal(fake.calls.filter((call) => call.command === 'agy').length, 0);
});
