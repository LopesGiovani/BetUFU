import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import {
  APPROVED_DECISION_STATUS,
  CANDIDATE_RELATIVE_PATH,
  PENDING_APPROVAL,
  PENDING_DECISION_STATUS,
  SportsOddsSourceCandidateValidationError,
  approvalSnapshotDigest,
  defaultProjectRoot,
  parseArguments,
  readCandidateFile,
  resolveCandidatePath,
  validateCandidate,
  validateCandidateFile,
} from './validate-sports-odds-source-candidate.mjs';

const validatorPath = fileURLToPath(new URL('./validate-sports-odds-source-candidate.mjs', import.meta.url));
const baseline = readCandidateFile(defaultProjectRoot);

function candidateClone() {
  return structuredClone(baseline);
}

function pendingCandidateClone() {
  const candidate = candidateClone();
  candidate.decisionStatus = PENDING_DECISION_STATUS;
  candidate.approval = {
    kind: 'human_checkpoint_token',
    status: PENDING_APPROVAL,
    approvedAt: null,
    approvedBy: null,
    digest: null,
    reviewReference: null,
    reviewedArtifactHashes: null,
  };
  return candidate;
}

function withTemporaryCandidate(mutator, callback) {
  const root = mkdtempSync(join(tmpdir(), 'betufu-source-candidate-'));
  const scripts = join(root, 'scripts');
  mkdirSync(scripts);
  const sourcePath = join(scripts, 'sports-odds-source-candidate.json');
  const source = readFileSync(join(defaultProjectRoot, CANDIDATE_RELATIVE_PATH), 'utf8');
  writeFileSync(sourcePath, mutator(source), 'utf8');
  try {
    callback(root);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

function assertMutationApplied(original, mutated, label) {
  assert.notDeepEqual(mutated, original, `${label}: mutation did not change the candidate`);
}

function assertRule(mutator, expectedRule, label = expectedRule) {
  const mutated = candidateClone();
  mutator(mutated);
  assertMutationApplied(baseline, mutated, label);
  assert.throws(
    () => validateCandidate(mutated),
    (error) => error instanceof SportsOddsSourceCandidateValidationError
      && error.rule === expectedRule,
    `${label}: expected rule ${expectedRule}`,
  );
}

test('candidato aprovado passa sem liberar runtime', () => {
  const evidence = validateCandidate(baseline);
  assert.equal(evidence.valid, true);
  assert.equal(evidence.approvalStatus, 'approved');
  assert.equal(evidence.decisionStatus, APPROVED_DECISION_STATUS);
  assert.equal(baseline.runtimeConsumable, false);
  assert.match(baseline.approval.digest, /^[A-F0-9]{64}$/u);
  assert.equal(baseline.approval.reviewedArtifactHashes.candidateSnapshot, baseline.approval.digest);
  assert.ok(evidence.checks > 100);
});

test('arquivo e caminho ficam presos a scripts e a uma raiz existente', () => {
  const candidatePath = resolveCandidatePath(defaultProjectRoot);
  assert.equal(candidatePath, join(defaultProjectRoot, ...CANDIDATE_RELATIVE_PATH.split('/')));
  assert.doesNotThrow(() => validateCandidateFile(defaultProjectRoot));
});

test('rejeita o candidato se ele virar consumivel pelo runtime', () => {
  assertRule((candidate) => { candidate.runtimeConsumable = true; }, 'RUNTIME_CONSUMABLE');
});

test('rejeita purpose que tente promover o candidato a runtime', () => {
  assertRule((candidate) => { candidate.purpose = 'runtime production authority'; }, 'PURPOSE');
});

test('rejeita taxonomia com classe ausente', () => {
  assertRule((candidate) => { candidate.taxonomy.classes.pop(); }, 'TAXONOMY_CLASSES');
});

test('rejeita sobreposicao entre resultado e lifecycle', () => {
  assertRule((candidate) => { candidate.taxonomy.outcomeClasses.push('CANCELLED'); }, 'TAXONOMY_OUTCOME_PARTITION');
});

test('rejeita lifecycle como payload de outcome', () => {
  assertRule((candidate) => { candidate.payloads.CANCELLED.payloadAllowed = true; }, 'CANCELLED_PAYLOAD_ALLOWED');
});

test('rejeita status de lifecycle divergente do V24', () => {
  assertRule((candidate) => { candidate.taxonomy.definitions.POSTPONED.lifecycleStatus = 'CANCELADO'; }, 'POSTPONED_DEFINITION_STATUS');
});

test('rejeita fato administrativo se faltar qualquer alias de score proibido', () => {
  assertRule((candidate) => {
    candidate.payloads.ADMINISTRATIVE_WALKOVER.forbiddenFields = candidate.payloads.ADMINISTRATIVE_WALKOVER.forbiddenFields
      .filter((field) => field !== 'scores');
  }, 'ADMINISTRATIVE_WALKOVER_SCORE_FORBIDDEN_FIELDS');
});

test('rejeita score aninhado em payload administrativo', () => {
  const mutated = candidateClone();
  mutated.payloads.ADMINISTRATIVE_WALKOVER.example = { score: 1 };
  assertMutationApplied(baseline, mutated, 'ADMIN_SCORE_NESTED');
  assert.throws(
    () => validateCandidate(mutated),
    (error) => error.rule === 'ADMINISTRATIVE_WALKOVER_PAYLOAD_KEYS',
  );
});

test('rejeita referencia V16 que copie ou sintetize placar', () => {
  assertRule((candidate) => { candidate.payloads.SPORTING_RESULT.v16Reference.copyScoresIntoAuthority = true; }, 'V16_COPY_SCORES');
  assertRule((candidate) => { candidate.payloads.SPORTING_RESULT.v16Reference.synthesizeScores = true; }, 'V16_SYNTHESIZE_SCORES');
});

test('rejeita referencia V16 que perca um dos dois slots originais', () => {
  assertRule((candidate) => { candidate.payloads.SPORTING_RESULT.v16Reference.slotCount = 1; }, 'V16_SLOT_COUNT');
  assertRule((candidate) => { candidate.payloads.SPORTING_RESULT.v16Reference.slotNumbers = [1, 3]; }, 'V16_SLOT_NUMBERS');
});

test('rejeita head duplicavel ou sem obrigacao quando ha fato', () => {
  assertRule((candidate) => { candidate.head.onePerEvent = false; }, 'HEAD_ONE_PER_EVENT');
  assertRule((candidate) => { candidate.head.oneCurrentHeadWhenFactExists = false; }, 'HEAD_REQUIRED_WHEN_FACT_EXISTS');
});

test('rejeita replay sem historia append-only', () => {
  assertRule((candidate) => { candidate.replay.historyRequired = false; }, 'REPLAY_HISTORY_REQUIRED');
  assertRule((candidate) => {
    candidate.replay.forbiddenShortcuts = candidate.replay.forbiddenShortcuts.filter((item) => item !== 'MAX(version_no)');
  }, 'REPLAY_FORBIDDEN_SHORTCUTS');
  assertRule((candidate) => { candidate.replay.steps[2] = 'use current head'; }, 'REPLAY_STEPS');
  assertRule((candidate) => { candidate.replay.algorithm = 'ignore history and use current head at cutoff'; }, 'REPLAY_ALGORITHM');
  assertRule((candidate) => { candidate.replay.steps[3] = 'use current event scheduled_at during replay'; }, 'REPLAY_STEPS');
});

test('rejeita tempo sem known_at ou com ordenacao nao deterministica', () => {
  assertRule((candidate) => { delete candidate.temporalProtocol.fields.known_at; }, 'TEMPORAL_FIELD_NAMES');
  assertRule((candidate) => { candidate.temporalProtocol.ordering = ['known_at ASC']; }, 'TEMPORAL_ORDER');
  assertRule((candidate) => { candidate.temporalProtocol.fields.known_at.distinctFromCapture = false; }, 'KNOWN_AT_CAPTURE_DISTINCT');
  assertRule((candidate) => { candidate.temporalProtocol.fields.sport_time.snapshotAtFactCreation = false; }, 'SPORT_TIME_SNAPSHOT');
  assertRule((candidate) => { candidate.temporalProtocol.fields.sport_time.currentEventLookupForbidden = false; }, 'SPORT_TIME_CURRENT_EVENT_FORBIDDEN');
  assertRule((candidate) => { candidate.temporalProtocol.fields.sport_time.candidateSource = 'sports_event.scheduled_at at replay time'; }, 'SPORT_TIME_SOURCE');
});

test('rejeita parametros Elo/Davidson que parecem ratificados sem backtest', () => {
  assertRule((candidate) => { candidate.eloPolicy.recency.monthlyHalfLife = 6; }, 'ELO_RECENCY_NUMERIC_PENDING');
  assertRule((candidate) => { candidate.eloPolicy.draw.davidson.parameters = 'theta=0.1'; }, 'ELO_DAVIDSON_PENDING');
  assertRule((candidate) => { candidate.eloPolicy.publication.roundingMode = 'HALF_UP'; }, 'ELO_ROUNDING_PENDING');
  assertRule((candidate) => { candidate.eloPolicy.margin.formula = 'pending label; formula=0.99'; }, 'ELO_MARGIN_PENDING');
  assertRule((candidate) => { candidate.eloPolicy.headToHeadContext.automaticBonus = true; }, 'ELO_H2H_NO_AUTOMATIC_BONUS');
  assertRule((candidate) => { candidate.eloPolicy.headToHeadContext.promotionRequires = 'always'; }, 'ELO_H2H_PROMOTION_GATE');
});

test('rejeita cadeia de supersessao com topologia ambigua', () => {
  assertRule((candidate) => { candidate.head.supersessionChain.singleRootPerEvent = false; }, 'HEAD_CHAIN_SINGLE_ROOT');
  assertRule((candidate) => { candidate.head.supersessionChain.oneSuccessorPerPredecessor = false; }, 'HEAD_CHAIN_NO_FORK');
  assertRule((candidate) => { candidate.head.supersessionChain.acyclic = false; }, 'HEAD_CHAIN_ACYCLIC');
  assertRule((candidate) => { candidate.head.supersessionChain.newFactMustSupersedeCurrentHead = false; }, 'HEAD_CHAIN_CAS_PREDECESSOR');
});

test('rejeita entrada administrativa no Elo ou Davidson', () => {
  assertRule((candidate) => { candidate.eloPolicy.eligibleOutcome.administrativeWalkover = true; }, 'ELO_ADMIN_EXCLUDED');
  assertRule((candidate) => { candidate.payloads.ABANDONED_NO_VALID_RESULT.feedsDavidson = true; }, 'ABANDONED_NO_VALID_RESULT_FEEDS_DAVIDSON');
});

test('rejeita classificacao de legado baseada apenas em score', () => {
  assertRule((candidate) => {
    candidate.legacy.forbiddenEvidence = candidate.legacy.forbiddenEvidence.filter((item) => item !== 'numeric score alone');
  }, 'LEGACY_FORBIDDEN_EVIDENCE');
  assertRule((candidate) => { candidate.legacy.noSilentClassification = false; }, 'LEGACY_NO_SILENT_CLASSIFICATION');
  assertRule((candidate) => { candidate.legacy.unresolvedDisposition = 'classified'; }, 'LEGACY_UNRESOLVED');
});

test('rejeita confirmed_version_id como segunda autoridade', () => {
  assertRule((candidate) => { candidate.compatibility.confirmedVersionId.secondAuthority = true; }, 'COMPATIBILITY_SECOND_AUTHORITY');
  assertRule((candidate) => { candidate.compatibility.v16.mustRemainByteIdentical = false; }, 'COMPATIBILITY_V16_BYTE_IDENTITY');
  assertRule((candidate) => { candidate.compatibility.crossKindCorrection.acceptedOddsAreNotRewritten = false; }, 'COMPATIBILITY_ODDS_IMMUTABLE');
});

test('rejeita workflow administrativo fora do servidor ou sem auditoria atomica', () => {
  assertRule((candidate) => { candidate.administrativeWorkflow.clientAuthority = true; }, 'WORKFLOW_CLIENT_AUTHORITY');
  assertRule((candidate) => { candidate.administrativeWorkflow.bulkImport = true; }, 'WORKFLOW_BULK_IMPORT');
  assertRule((candidate) => { candidate.administrativeWorkflow.transaction = 'insert fact'; }, 'WORKFLOW_ATOMIC_TRANSACTION');
});

test('rejeita roster sem gate de 04-19 ou com selecao latest implicita', () => {
  assertRule((candidate) => { candidate.roster.perParticipantVersionNo = 'fixed-1'; }, 'ROSTER_VERSION_PENDING');
  assertRule((candidate) => { candidate.roster.latestImplicitSelection = true; }, 'ROSTER_NO_LATEST');
  assertRule((candidate) => { candidate.roster.piiInOddsContract = true; }, 'ROSTER_NO_PII');
});

test('rejeita migrations numeradas ou sem ratificacao literal', () => {
  assertRule((candidate) => { candidate.migrationCandidates.numbersFixed = true; }, 'MIGRATIONS_NUMBERS_OPEN');
  assertRule((candidate) => { candidate.migrationCandidates.sequence[0].filename = 'V27__create.sql'; }, 'MIGRATION_1_FILENAME');
  assertRule((candidate) => { candidate.migrationCandidates.ratifyLiteralFilenamesBeforeExecution = false; }, 'MIGRATIONS_LITERAL_RATIFICATION');
});

test('rejeita campo top-level desconhecido', () => {
  assertRule((candidate) => { candidate.unapproved = true; }, 'ROOT_KEYS');
});

test('aprovacao pendente passa no T1 mas bloqueia o modo T2', () => {
  const pending = pendingCandidateClone();
  assert.doesNotThrow(() => validateCandidate(pending, { requireApproval: false }));
  assert.throws(
    () => validateCandidate(pending, { requireApproval: true }),
    (error) => error.rule === 'APPROVAL_PENDING',
  );
});

test('aprovacao parcial nao e aceita como token humano', () => {
  const pending = pendingCandidateClone();
  pending.approval.status = 'approved';
  assertMutationApplied(pendingCandidateClone(), pending, 'PARTIAL_APPROVAL');
  assert.throws(
    () => validateCandidate(pending),
    (error) => error instanceof SportsOddsSourceCandidateValidationError
      && error.rule === 'APPROVAL_STATE',
  );
});

test('aprovacao exige identidade, referencia e digest vinculados', () => {
  assertRule((candidate) => { candidate.approval.approvedBy = 'attacker'; }, 'APPROVAL_APPROVED_BY');
  assertRule((candidate) => { candidate.approval.reviewReference = 'invented'; }, 'APPROVAL_REVIEW_REFERENCE');
  assertRule((candidate) => { candidate.approval.digest = '0'.repeat(64); }, 'APPROVAL_DIGEST_BINDING');
});

test('CLI T2 rejeita arquivo aprovado cujo snapshot foi alterado', () => {
  withTemporaryCandidate(
    (source) => source.replace('"schemaVersion":', '"schemaVersion" :'),
    (root) => assert.throws(
      () => validateCandidateFile(root, { requireApproval: true }),
      (error) => error instanceof SportsOddsSourceCandidateValidationError
        && error.rule === 'APPROVAL_DIGEST_MISMATCH',
    ),
  );
});

test('digest aprovado permanece estavel quando o checkout usa CRLF', () => {
  withTemporaryCandidate(
    (source) => source.replace(/\r?\n/gu, '\r\n'),
    (root) => assert.doesNotThrow(() => validateCandidateFile(root, { requireApproval: true })),
  );
});

test('digest rejeita whitespace extra apos o JSON', () => {
  withTemporaryCandidate(
    (source) => `${source}${source.includes('\r\n') ? '\r\n' : '\n'}`,
    (root) => assert.throws(
      () => validateCandidateFile(root, { requireApproval: true }),
      (error) => error instanceof SportsOddsSourceCandidateValidationError
        && error.rule === 'CANDIDATE_TRAILING_WHITESPACE',
    ),
  );
});

test('campos de politica nao aceitam texto contraditorio', () => {
  assertRule((candidate) => {
    candidate.administrativeWorkflow.transaction = 'do not lock; insert and skip audit';
  }, 'WORKFLOW_ATOMIC_TRANSACTION');
  assertRule((candidate) => {
    candidate.recovery.partialFailure = 'rollback is optional and audit may be skipped';
  }, 'RECOVERY_PARTIAL_FAILURE');
  assertRule((candidate) => {
    candidate.roster.perParticipantVersionNo = 'pending_gate_04-19-T1 but use latest';
  }, 'ROSTER_VERSION_PENDING');
  assertRule((candidate) => {
    candidate.payloads.SPORTING_RESULT.regulationGate = 'ignore regulation and use default';
  }, 'SPORTING_REGULATION_GATE');
});

test('approvalSnapshotDigest calcula o digest canonico do snapshot pendente', () => {
  const source = readFileSync(join(defaultProjectRoot, CANDIDATE_RELATIVE_PATH), 'utf8');
  assert.equal(approvalSnapshotDigest(source), baseline.approval.digest);
});

test('parser aceita somente os modos literais e falha fechado', () => {
  assert.deepEqual(parseArguments(['--check']), { requireApproval: false });
  assert.deepEqual(parseArguments(['--require-approval']), { requireApproval: true });
  assert.throws(() => parseArguments([]), (error) => error.rule === 'CLI_ARGUMENTS');
  assert.throws(() => parseArguments(['--unknown']), (error) => error.rule === 'CLI_ARGUMENTS');
  assert.throws(() => parseArguments(['--require-approval', '--check']), (error) => error.rule === 'CLI_ARGUMENTS');
});

function runCli(...args) {
  return spawnSync(process.execPath, [validatorPath, ...args], {
    cwd: defaultProjectRoot,
    encoding: 'utf8',
    shell: false,
    windowsHide: true,
  });
}

test('CLI T1 retorna zero e informa o checkpoint aprovado', () => {
  const result = runCli('--check');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /T2=approved/u);
});

test('CLI T2 retorna zero quando o token humano esta registrado', () => {
  const result = runCli('--require-approval');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /T2=approved/u);
});

test('CLI sem flag ou com flag desconhecida retorna dois', () => {
  const noFlag = runCli();
  assert.equal(noFlag.status, 2);
  assert.match(noFlag.stderr, /CLI_ARGUMENTS/u);
  const unknown = runCli('--unknown');
  assert.equal(unknown.status, 2);
  assert.match(unknown.stderr, /CLI_ARGUMENTS/u);
});
