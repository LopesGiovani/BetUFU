#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
export const defaultProjectRoot = resolve(scriptsDirectory, '..');
export const CANDIDATE_RELATIVE_PATH = 'scripts/odds-roster-binding-candidate.json';

export class RosterBindingValidationError extends Error {
  constructor(rule, message) {
    super(`${rule}: ${message}`);
    this.name = 'RosterBindingValidationError';
    this.rule = rule;
  }
}

function fail(rule, message) {
  throw new RosterBindingValidationError(rule, message);
}

export function readCandidateFile(projectRoot = defaultProjectRoot) {
  return JSON.parse(requireText(projectRoot));
}

function requireText(projectRoot) {
  return readFileSync(resolve(projectRoot, CANDIDATE_RELATIVE_PATH), 'utf8');
}

export function validateCandidate(candidate, options = {}) {
  if (!candidate || typeof candidate !== 'object') fail('ROOT', 'candidate must be an object');
  const keys = Object.keys(candidate).sort().join('|');
  const expected = ['approval', 'atomicity', 'binding', 'correction', 'decisionStatus', 'freeze', 'kind', 'migration', 'phase', 'plan', 'publicBoundary', 'runtimeConsumable', 'schemaVersion', 'scope'].sort().join('|');
  if (keys !== expected) fail('ROOT_KEYS', 'unexpected top-level field');
  if (candidate.runtimeConsumable !== false) fail('RUNTIME_CONSUMABLE', 'candidate cannot be runtime input');
  if (candidate.decisionStatus !== 'approved_human_checkpoint_04-19-T1') fail('DECISION_STATUS', 'checkpoint must be approved');
  if (candidate.approval?.signal !== 'aprovar roster 04-19') fail('APPROVAL_SIGNAL', 'human approval signal is missing');
  if (candidate.approval?.filename !== 'backend/src/main/resources/db/migration/V28__bind_event_roster_publication.sql') fail('MIGRATION_FILENAME', 'literal migration filename mismatch');
  if (candidate.binding?.eventFormat !== 'HEAD_TO_HEAD' || candidate.binding?.participants?.join('|') !== '1|2') fail('BINDING_SHAPE', 'only two-slot H2H binding is supported');
  if (candidate.binding?.referenceFields?.join('|') !== 'publicationId|versionNo') fail('BINDING_REFERENCE', 'publicationId and versionNo are required');
  if (candidate.binding?.latestImplicitSelection !== false) fail('NO_LATEST_IMPLICIT', 'latest publication selection is forbidden');
  if (candidate.binding?.snapshotImmutable !== true || candidate.freeze?.capturedValuesAreImmutable !== true) fail('SNAPSHOT_IMMUTABLE', 'frozen references must be immutable');
  if (candidate.binding?.knownAtRequired !== true) fail('KNOWN_AT_REQUIRED', 'knownAt is required');
  if (candidate.binding?.statusRequired !== 'PUBLICADO') fail('PUBLICATION_STATUS', 'only published rosters are eligible');
  if (candidate.binding?.piiInOddsContract !== false || candidate.publicBoundary?.oddsImportsSportsInternal !== false) fail('NO_PII_OR_INTERNAL', 'public boundary leaked PII or internals');
  if (candidate.atomicity?.requiresRowLock !== true || candidate.atomicity?.compareAndSet !== true || candidate.atomicity?.noCheckThenAct !== true) fail('ATOMICITY', 'lock and compare-and-set are required');
  if (candidate.atomicity?.failureMode !== 'fail_closed') fail('FAIL_CLOSED', 'race must fail closed');
  if (candidate.freeze?.correctionCreatesNewPublication !== true || candidate.freeze?.existingMarketIsNotRewritten !== true) fail('CORRECTION_POLICY', 'corrections must preserve frozen markets');
  if (candidate.scope?.draftPublicationForbidden !== true || candidate.scope?.stalePublicationForbidden !== true || candidate.scope?.crossTeamBindingForbidden !== true) fail('SCOPE_GUARDS', 'draft/stale/cross-team references must be rejected');
  if (candidate.migration?.forwardOnly !== true || candidate.migration?.mayEditAppliedMigrations !== false) fail('MIGRATION_POLICY', 'migration must be additive');
  if (options.requireApproval && candidate.approval?.approvedBy !== 'Giovani') fail('APPROVAL_PENDING', 'human approval token is required');
  return Object.freeze({ valid: true, checks: 23, approvalStatus: candidate.decisionStatus });
}

export function validateCandidateFile(projectRoot = defaultProjectRoot, options = {}) {
  let candidate;
  try {
    candidate = JSON.parse(requireText(projectRoot));
  } catch {
    fail('CANDIDATE_JSON', 'invalid candidate JSON');
  }
  return validateCandidate(candidate, options);
}

export function parseArguments(args) {
  if (args.length === 1 && args[0] === '--check') return { requireApproval: false };
  if (args.length === 1 && args[0] === '--require-approval') return { requireApproval: true };
  fail('CLI_ARGUMENTS', 'usage: --check or --require-approval');
}

export function runCli(args = process.argv.slice(2), projectRoot = defaultProjectRoot) {
  try {
    const evidence = validateCandidateFile(projectRoot, parseArguments(args));
    console.log(`[validate-odds-roster-binding] OK: ${evidence.checks} verificacoes; T1=${evidence.approvalStatus}.`);
    return 0;
  } catch (error) {
    console.error(`[validate-odds-roster-binding] ERRO: ${error instanceof Error ? error.message : String(error)}`);
    return 1;
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) process.exitCode = runCli();
