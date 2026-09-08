import assert from 'node:assert/strict';
import test from 'node:test';
import {
  defaultProjectRoot,
  parseArguments,
  readCandidateFile,
  validateCandidate,
  validateCandidateFile,
} from './validate-odds-roster-binding.mjs';

const baseline = readCandidateFile(defaultProjectRoot);

test('candidato aprovado valida e permanece fora do runtime', () => {
  const result = validateCandidateFile(defaultProjectRoot, { requireApproval: true });
  assert.equal(result.valid, true);
  assert.equal(baseline.runtimeConsumable, false);
  assert.equal(baseline.binding.referenceFields[0], 'publicationId');
  assert.equal(baseline.binding.referenceFields[1], 'versionNo');
});

test('correcao preserva snapshot e nao reescreve mercado', () => {
  assert.equal(baseline.freeze.correctionCreatesNewPublication, true);
  assert.equal(baseline.freeze.existingMarketIsNotRewritten, true);
  assert.equal(baseline.freeze.oldSnapshotRemainsReadable, true);
});

test('corrida abertura x correcao falha fechado', () => {
  assert.equal(baseline.atomicity.noCheckThenAct, true);
  assert.equal(baseline.atomicity.failureMode, 'fail_closed');
  assert.equal(baseline.atomicity.compareAndSet, true);
});

test('rejeita selecao latest implicita, PII e corrida sem lock', () => {
  const latest = structuredClone(baseline);
  latest.binding.latestImplicitSelection = true;
  assert.throws(() => validateCandidate(latest), /NO_LATEST_IMPLICIT/u);
  const pii = structuredClone(baseline);
  pii.binding.piiInOddsContract = true;
  assert.throws(() => validateCandidate(pii), /NO_PII_OR_INTERNAL/u);
  const race = structuredClone(baseline);
  race.atomicity.requiresRowLock = false;
  assert.throws(() => validateCandidate(race), /ATOMICITY/u);
});

test('parser aceita somente os modos literais', () => {
  assert.deepEqual(parseArguments(['--check']), { requireApproval: false });
  assert.deepEqual(parseArguments(['--require-approval']), { requireApproval: true });
  assert.throws(() => parseArguments([]), /CLI_ARGUMENTS/u);
});
