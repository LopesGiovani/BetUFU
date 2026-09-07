#!/usr/bin/env node

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  realpathSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import {
  dirname,
  isAbsolute,
  join,
  relative,
  resolve,
} from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
export const defaultProjectRoot = resolve(scriptsDirectory, '..');
export const CANDIDATE_RELATIVE_PATH = 'scripts/odds-state-machine-fixtures.json';
export const PENDING_APPROVAL = 'pending_human_checkpoint_04-02-T2';
export const PENDING_DECISION_STATUS = 'candidate_pending_human_checkpoint_04-02-T2';
export const APPROVED_DECISION_STATUS = 'approved_human_checkpoint_04-02-T2';
export const APPROVED_BY = 'LopesGiovani';

const PURPOSE = 'Candidato documental para contrato publico sports -> odds, taxonomia e maquina de estados antes de implementacao Java ou schema.';
const APPROVAL_REFERENCE_PATTERN = /^04-02\/T2@\d{4}-\d{2}-\d{2}: owner message '.*'$/u;

const ROOT_KEYS = Object.freeze([
  'schemaVersion', 'phase', 'plan', 'kind', 'runtimeConsumable', 'purpose',
  'decisionStatus', 'decisionBasis', 'boundaryContract', 'taxonomy',
  'marketStateMachine', 'governanceAndAuthority', 'mappings', 'fixtures',
  'approval',
]);

const TAXONOMY_CLASSES = Object.freeze([
  'SPORTING_RESULT',
  'ADMINISTRATIVE_WALKOVER',
  'ABANDONED_NO_VALID_RESULT',
  'CANCELLED',
  'POSTPONED',
]);

const OUTCOME_CLASSES = Object.freeze([
  'SPORTING_RESULT',
  'ADMINISTRATIVE_WALKOVER',
  'ABANDONED_NO_VALID_RESULT',
]);

const LIFECYCLE_CLASSES = Object.freeze(['CANCELLED', 'POSTPONED']);

const RESULT_STATUSES = Object.freeze(['CONFIRMADO', 'CORRIGIDO']);
const NON_RESULT_STATUSES = Object.freeze(['INFORMADO', 'AGUARDANDO_REVISAO']);

const MARKET_STATES = Object.freeze([
  'DRAFT', 'OPEN', 'SUSPENDED', 'CLOSED', 'LOCKED', 'CANCELLED',
]);

const OPERATIONAL_ROLES = Object.freeze([
  'ADMINISTRADOR_GERAL', 'ADMINISTRADOR_COMPETICAO', 'MODERADOR',
]);

const ODDS_CAPABILITIES = Object.freeze([
  'VIEW_ODDS_MARKET',
  'MANAGE_ODDS_MARKET',
  'PROPOSE_ODDS_OVERRIDE',
  'APPROVE_ODDS_OVERRIDE',
]);

const FORBIDDEN_SCORE_FIELDS = Object.freeze([
  'score', 'scores', 'homeScore', 'awayScore', 'home_score', 'away_score',
  'sourceHomeScore', 'sourceAwayScore', 'modelHomeScore', 'modelAwayScore',
  'homeScoreForModel', 'awayScoreForModel',
]);

export class OddsContractCandidateValidationError extends Error {
  constructor(rule, message) {
    super(`[${rule}] ${message}`);
    this.name = 'OddsContractCandidateValidationError';
    this.rule = rule;
  }
}

function fail(rule, message) {
  throw new OddsContractCandidateValidationError(rule, message);
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function isSha256(value) {
  return typeof value === 'string' && /^[a-f0-9]{64}$/iu.test(value);
}

function isCanonicalUtcInstant(value) {
  if (typeof value !== 'string') return false;
  const parsed = new Date(value);
  return !Number.isNaN(parsed.valueOf()) && parsed.toISOString() === value;
}

function sameSet(actual, expected) {
  return Array.isArray(actual)
    && actual.length === expected.length
    && new Set(actual).size === actual.length
    && [...actual].sort().every((item, index) => item === [...expected].sort()[index]);
}

function sameArray(actual, expected) {
  return Array.isArray(actual)
    && actual.length === expected.length
    && actual.every((item, index) => item === expected[index]);
}

function exactKeys(value, expected) {
  return isRecord(value) && sameSet(Object.keys(value), expected);
}

function makeContext() {
  let checks = 0;
  return {
    get checks() {
      return checks;
    },
    require(condition, rule, message) {
      if (!condition) fail(rule, message);
      checks += 1;
    },
    record(value, rule) {
      this.require(isRecord(value), rule, 'objeto obrigatorio');
      return value;
    },
    keys(value, expected, rule) {
      this.require(exactKeys(value, expected), rule, 'chaves obrigatorias divergentes');
    },
    string(value, rule) {
      this.require(isNonEmptyString(value), rule, 'texto obrigatorio');
    },
    boolean(value, expected, rule) {
      this.require(value === expected, rule, `valor esperado: ${String(expected)}`);
    },
    array(value, rule) {
      this.require(Array.isArray(value), rule, 'array obrigatorio');
    },
    arraySet(value, expected, rule) {
      this.require(sameSet(value, expected), rule, 'conjunto esperado divergente');
    },
    arrayExact(value, expected, rule) {
      this.require(sameArray(value, expected), rule, 'ordem/valores esperados divergentes');
    },
  };
}

function validateRoot(ctx, candidate) {
  const root = ctx.record(candidate, 'ROOT_SHAPE');
  ctx.keys(root, ROOT_KEYS, 'ROOT_KEYS');
  ctx.require(root.schemaVersion === '04-02.odds-contract-fixtures.v1', 'SCHEMA_VERSION', 'schemaVersion inesperado');
  ctx.require(root.phase === '04-mercados-pr-jogo-e-odds-transparentes', 'PHASE', 'fase inesperada');
  ctx.require(root.plan === '04-02', 'PLAN', 'plano inesperado');
  ctx.require(root.kind === 'odds_contract_and_state_machine_candidate', 'KIND', 'kind inesperado');
  ctx.boolean(root.runtimeConsumable, false, 'RUNTIME_CONSUMABLE');
  ctx.require(root.purpose === PURPOSE, 'PURPOSE', 'purpose deve preservar a fronteira documental nao-runtime');
  ctx.require(
    root.decisionStatus === PENDING_DECISION_STATUS || root.decisionStatus === APPROVED_DECISION_STATUS,
    'DECISION_STATUS',
    'decisionStatus deve permanecer pendente ou aprovado pelo checkpoint 04-02-T2',
  );
  return root;
}

function validateDecisionBasis(ctx, value) {
  const basis = ctx.record(value, 'DECISION_BASIS_SHAPE');
  ctx.keys(basis, ['optionsCompared', 'selectedOption', 'selectedOptionKey', 'selectedOptionDescription', 'modularBoundary'], 'DECISION_BASIS_KEYS');
  const options = ctx.record(basis.optionsCompared, 'OPTIONS_COMPARED_SHAPE');
  ctx.keys(options, ['A', 'B', 'C'], 'OPTIONS_COMPARED_KEYS');

  const optA = ctx.record(options.A, 'OPTION_A_SHAPE');
  ctx.require(optA.key === 'approved_synchronous_read_port', 'OPTION_A_KEY');
  ctx.require(optA.disposition === 'APPROVED_FOR_IMPLEMENTATION', 'OPTION_A_DISPOSITION');

  const optB = ctx.record(options.B, 'OPTION_B_SHAPE');
  ctx.require(optB.key === 'opaque_after_commit_application_event', 'OPTION_B_KEY');
  ctx.require(optB.disposition === 'REJECTED_FOR_MVP', 'OPTION_B_DISPOSITION');

  const optC = ctx.record(options.C, 'OPTION_C_SHAPE');
  ctx.require(optC.key === 'versioned_odds_projection', 'OPTION_C_KEY');
  ctx.require(optC.disposition === 'REJECTED_FOR_MVP', 'OPTION_C_DISPOSITION');

  ctx.require(basis.selectedOption === 'A', 'SELECTED_OPTION', 'opcao A deve ser a selecionada');
  ctx.require(basis.selectedOptionKey === 'approved_synchronous_read_port', 'SELECTED_OPTION_KEY');
  ctx.require(
    basis.selectedOptionDescription === 'Contrato pertencente a sports, produtor em sports.internal e consumidor odds sem imports internos.',
    'SELECTED_OPTION_DESCRIPTION',
  );

  const boundary = ctx.record(basis.modularBoundary, 'MODULAR_BOUNDARY_SHAPE');
  ctx.keys(boundary, ['publicPortClass', 'publicModels', 'oddsPackage', 'sportsInternalPackage', 'forbiddenImports', 'forbiddenReverseDependency'], 'MODULAR_BOUNDARY_KEYS');
  ctx.require(boundary.publicPortClass === 'br.com.betufu.sports.SportsResultForOddsPort', 'BOUNDARY_PORT_CLASS');
  ctx.arraySet(boundary.publicModels, [
    'br.com.betufu.sports.SportsEventForOdds',
    'br.com.betufu.sports.ConfirmedSportsResultForOdds',
    'br.com.betufu.sports.SportsResultKindForOdds',
  ], 'BOUNDARY_PUBLIC_MODELS');
  ctx.require(boundary.oddsPackage === 'br.com.betufu.odds', 'BOUNDARY_ODDS_PACKAGE');
  ctx.require(boundary.sportsInternalPackage === 'br.com.betufu.sports.internal', 'BOUNDARY_SPORTS_INTERNAL_PACKAGE');
  ctx.arraySet(boundary.forbiddenImports, ['br.com.betufu.sports.internal..'], 'BOUNDARY_FORBIDDEN_IMPORTS');
  ctx.require(boundary.forbiddenReverseDependency === 'sports cannot depend on odds', 'BOUNDARY_REVERSE_DEPENDENCY');
}

function validateBoundaryContract(ctx, value) {
  const boundary = ctx.record(value, 'BOUNDARY_CONTRACT_SHAPE');
  ctx.keys(boundary, ['port', 'models'], 'BOUNDARY_CONTRACT_KEYS');

  const port = ctx.record(boundary.port, 'PORT_SHAPE');
  ctx.keys(port, ['name', 'package', 'methods'], 'PORT_KEYS');
  ctx.require(port.name === 'SportsResultForOddsPort', 'PORT_NAME');
  ctx.require(port.package === 'br.com.betufu.sports', 'PORT_PACKAGE');
  ctx.array(port.methods, 'PORT_METHODS');
  ctx.require(port.methods.length === 3, 'PORT_METHODS_COUNT');
  const methodNames = port.methods.map((m) => m.name);
  ctx.arraySet(methodNames, ['getEventForOdds', 'getConfirmedResultForOdds', 'findAdmissibleResultsAsOf'], 'PORT_METHOD_NAMES');

  const models = ctx.record(boundary.models, 'MODELS_SHAPE');
  ctx.keys(models, ['SportsEventForOdds', 'ConfirmedSportsResultForOdds', 'SportsResultKindForOdds'], 'MODELS_KEYS');

  const eventModel = ctx.record(models.SportsEventForOdds, 'EVENT_MODEL_SHAPE');
  ctx.keys(eventModel, ['package', 'fields', 'allowedFormats', 'rejectedFormats', 'slotStructure', 'slotCount', 'requiresConfirmedVersion'], 'EVENT_MODEL_KEYS');
  ctx.require(eventModel.package === 'br.com.betufu.sports', 'EVENT_MODEL_PACKAGE');
  ctx.arraySet(eventModel.fields, [
    'eventId', 'competitionId', 'editionId', 'sportId', 'categoryId',
    'scheduledAt', 'zoneId', 'format', 'slots',
  ], 'EVENT_MODEL_FIELDS');
  ctx.arrayExact(eventModel.allowedFormats, ['HEAD_TO_HEAD'], 'EVENT_MODEL_ALLOWED_FORMATS');
  ctx.arrayExact(eventModel.rejectedFormats, ['GENERIC'], 'EVENT_MODEL_REJECTED_FORMATS');
  ctx.arraySet(eventModel.slotStructure, ['slotNo', 'displayLabel', 'teamId', 'teamName'], 'EVENT_MODEL_SLOT_STRUCTURE');
  ctx.require(eventModel.slotCount === 2, 'EVENT_MODEL_SLOT_COUNT');
  ctx.boolean(eventModel.requiresConfirmedVersion, false, 'EVENT_MODEL_NO_CONFIRMED_VERSION_REQUIRED');

  const resultModel = ctx.record(models.ConfirmedSportsResultForOdds, 'RESULT_MODEL_SHAPE');
  ctx.keys(resultModel, ['package', 'fields', 'admissibleStatuses', 'inadmissibleStatuses', 'scorePolicy'], 'RESULT_MODEL_KEYS');
  ctx.require(resultModel.package === 'br.com.betufu.sports', 'RESULT_MODEL_PACKAGE');
  ctx.arraySet(resultModel.fields, [
    'versionId', 'eventId', 'competitionId', 'editionId', 'versionNo',
    'status', 'resultKind', 'scores', 'confirmedAt', 'knownAt',
    'sportTime', 'supersedesVersionId',
  ], 'RESULT_MODEL_FIELDS');
  ctx.arraySet(resultModel.admissibleStatuses, RESULT_STATUSES, 'RESULT_MODEL_ADMISSIBLE_STATUSES');
  ctx.arraySet(resultModel.inadmissibleStatuses, NON_RESULT_STATUSES, 'RESULT_MODEL_INADMISSIBLE_STATUSES');

  const scorePolicy = ctx.record(resultModel.scorePolicy, 'RESULT_SCORE_POLICY_SHAPE');
  ctx.keys(scorePolicy, ['source', 'requiredFor', 'forbiddenFor'], 'RESULT_SCORE_POLICY_KEYS');
  ctx.require(scorePolicy.source === 'sports_result_version_slot (V16)', 'RESULT_SCORE_POLICY_SOURCE');
  ctx.arrayExact(scorePolicy.requiredFor, ['SPORTING_RESULT'], 'RESULT_SCORE_POLICY_REQUIRED');
  ctx.arraySet(scorePolicy.forbiddenFor, [
    'ADMINISTRATIVE_WALKOVER', 'ABANDONED_NO_VALID_RESULT', 'CANCELLED', 'POSTPONED',
  ], 'RESULT_SCORE_POLICY_FORBIDDEN');

  const kindModel = ctx.record(models.SportsResultKindForOdds, 'KIND_MODEL_SHAPE');
  ctx.keys(kindModel, ['package', 'values'], 'KIND_MODEL_KEYS');
  ctx.require(kindModel.package === 'br.com.betufu.sports', 'KIND_MODEL_PACKAGE');
  ctx.arraySet(kindModel.values, TAXONOMY_CLASSES, 'KIND_MODEL_VALUES');
}

function validateTaxonomy(ctx, value) {
  const taxonomy = ctx.record(value, 'TAXONOMY_SHAPE');
  ctx.keys(taxonomy, ['classes', 'outcomeClasses', 'lifecycleClasses', 'definitions', 'resultVersionStatuses', 'separationRules'], 'TAXONOMY_KEYS');
  ctx.arraySet(taxonomy.classes, TAXONOMY_CLASSES, 'TAXONOMY_CLASSES');
  ctx.arraySet(taxonomy.outcomeClasses, OUTCOME_CLASSES, 'TAXONOMY_OUTCOME_CLASSES');
  ctx.arraySet(taxonomy.lifecycleClasses, LIFECYCLE_CLASSES, 'TAXONOMY_LIFECYCLE_CLASSES');

  const defs = ctx.record(taxonomy.definitions, 'TAXONOMY_DEFINITIONS_SHAPE');
  ctx.keys(defs, TAXONOMY_CLASSES, 'TAXONOMY_DEFINITIONS_KEYS');

  const sporting = ctx.record(defs.SPORTING_RESULT, 'SPORTING_DEF_SHAPE');
  ctx.keys(sporting, ['meaning', 'feedsElo', 'feedsDavidson', 'hasScore', 'drawEligibility', 'marketOutcome', 'settlementEffect'], 'SPORTING_DEF_KEYS');
  ctx.boolean(sporting.feedsElo, true, 'SPORTING_FEEDS_ELO');
  ctx.boolean(sporting.feedsDavidson, true, 'SPORTING_FEEDS_DAVIDSON');
  ctx.boolean(sporting.hasScore, true, 'SPORTING_HAS_SCORE');
  ctx.require(sporting.marketOutcome === 'VALID_SPORTING_OUTCOME', 'SPORTING_MARKET_OUTCOME');
  ctx.require(sporting.settlementEffect === 'NORMAL_SETTLEMENT', 'SPORTING_SETTLEMENT_EFFECT');

  const walkover = ctx.record(defs.ADMINISTRATIVE_WALKOVER, 'WALKOVER_DEF_SHAPE');
  ctx.keys(walkover, ['meaning', 'feedsElo', 'feedsDavidson', 'hasScore', 'winnerAllowed', 'winnerPolicy', 'marketOutcome', 'settlementEffect'], 'WALKOVER_DEF_KEYS');
  ctx.boolean(walkover.feedsElo, false, 'WALKOVER_FEEDS_ELO');
  ctx.boolean(walkover.feedsDavidson, false, 'WALKOVER_FEEDS_DAVIDSON');
  ctx.boolean(walkover.hasScore, false, 'WALKOVER_HAS_SCORE');
  ctx.boolean(walkover.winnerAllowed, true, 'WALKOVER_WINNER_ALLOWED');
  ctx.require(walkover.marketOutcome === 'ADMINISTRATIVE_WALKOVER', 'WALKOVER_MARKET_OUTCOME');
  ctx.require(walkover.settlementEffect === 'VOID_REFUND', 'WALKOVER_SETTLEMENT_EFFECT');

  const abandoned = ctx.record(defs.ABANDONED_NO_VALID_RESULT, 'ABANDONED_DEF_SHAPE');
  ctx.keys(abandoned, ['meaning', 'feedsElo', 'feedsDavidson', 'hasScore', 'winnerAllowed', 'marketOutcome', 'settlementEffect'], 'ABANDONED_DEF_KEYS');
  ctx.boolean(abandoned.feedsElo, false, 'ABANDONED_FEEDS_ELO');
  ctx.boolean(abandoned.feedsDavidson, false, 'ABANDONED_FEEDS_DAVIDSON');
  ctx.boolean(abandoned.hasScore, false, 'ABANDONED_HAS_SCORE');
  ctx.boolean(abandoned.winnerAllowed, false, 'ABANDONED_WINNER_FORBIDDEN');
  ctx.require(abandoned.marketOutcome === 'ABANDONED', 'ABANDONED_MARKET_OUTCOME');
  ctx.require(abandoned.settlementEffect === 'VOID_REFUND', 'ABANDONED_SETTLEMENT_EFFECT');

  for (const [kind, expectedOutcome, expectedSettlement] of [
    ['CANCELLED', 'CANCELLED', 'VOID_REFUND'],
    ['POSTPONED', 'POSTPONED', 'SUSPEND_AND_VERSION_ON_RESCHEDULE'],
  ]) {
    const item = ctx.record(defs[kind], `${kind}_DEF_SHAPE`);
    ctx.keys(item, ['meaning', 'feedsElo', 'feedsDavidson', 'hasScore', 'winnerAllowed', 'marketOutcome', 'settlementEffect'], `${kind}_DEF_KEYS`);
    ctx.boolean(item.feedsElo, false, `${kind}_FEEDS_ELO`);
    ctx.boolean(item.feedsDavidson, false, `${kind}_FEEDS_DAVIDSON`);
    ctx.boolean(item.hasScore, false, `${kind}_HAS_SCORE`);
    ctx.boolean(item.winnerAllowed, false, `${kind}_WINNER_FORBIDDEN`);
    ctx.require(item.marketOutcome === expectedOutcome, `${kind}_MARKET_OUTCOME`);
    ctx.require(item.settlementEffect === expectedSettlement, `${kind}_SETTLEMENT_EFFECT`);
  }

  const statuses = ctx.record(taxonomy.resultVersionStatuses, 'TAXONOMY_STATUSES_SHAPE');
  ctx.keys(statuses, ['admissibleAtReplay', 'notAdmissibleAtReplay'], 'TAXONOMY_STATUSES_KEYS');
  ctx.arraySet(statuses.admissibleAtReplay, RESULT_STATUSES, 'TAXONOMY_STATUSES_ADMISSIBLE');
  ctx.arraySet(statuses.notAdmissibleAtReplay, NON_RESULT_STATUSES, 'TAXONOMY_STATUSES_NOT_ADMISSIBLE');

  ctx.arraySet(taxonomy.separationRules, [
    'CANCELLED e POSTPONED sao classes de lifecycle V24 e nao devem carregar payload de placar.',
    'ADMINISTRATIVE_WALKOVER e ABANDONED_NO_VALID_RESULT nao carregam placar esportivo nem sintetico.',
    'Empate e uma politica regulamentar restrita exclusivamente a SPORTING_RESULT.',
    'Vencedor administrativo no W.O. nao produz alteracao no rating Elo ou Davidson.',
  ], 'TAXONOMY_SEPARATION_RULES');
}

function validateMarketStateMachine(ctx, value) {
  const machine = ctx.record(value, 'MARKET_STATE_MACHINE_SHAPE');
  ctx.keys(machine, ['states', 'stateDescriptions', 'initialState', 'terminalStates', 'transitions', 'forbiddenTransitions', 'postponementPolicy', 'overridePolicy'], 'MARKET_STATE_MACHINE_KEYS');
  ctx.arraySet(machine.states, MARKET_STATES, 'MARKET_STATES');

  const descriptions = ctx.record(machine.stateDescriptions, 'STATE_DESCRIPTIONS_SHAPE');
  ctx.keys(descriptions, MARKET_STATES, 'STATE_DESCRIPTIONS_KEYS');

  ctx.require(machine.initialState === 'DRAFT', 'INITIAL_STATE');
  ctx.arraySet(machine.terminalStates, ['LOCKED', 'CANCELLED'], 'TERMINAL_STATES');

  ctx.array(machine.transitions, 'TRANSITIONS_ARRAY');
  for (const [index, trans] of machine.transitions.entries()) {
    const item = ctx.record(trans, `TRANSITION_${index}_SHAPE`);
    ctx.keys(item, ['from', 'to', 'action', 'requiredCapability', 'description'], `TRANSITION_${index}_KEYS`);
    ctx.require(MARKET_STATES.includes(item.from), `TRANSITION_${index}_FROM_STATE`);
    ctx.require(MARKET_STATES.includes(item.to), `TRANSITION_${index}_TO_STATE`);
    ctx.require(ODDS_CAPABILITIES.includes(item.requiredCapability), `TRANSITION_${index}_CAPABILITY`);
  }

  // Verify transition pairs
  const transitionPairs = machine.transitions.map((t) => `${t.from}->${t.to}`);
  const expectedPairs = [
    'DRAFT->OPEN',
    'DRAFT->CANCELLED',
    'OPEN->SUSPENDED',
    'OPEN->CLOSED',
    'OPEN->CANCELLED',
    'SUSPENDED->OPEN',
    'SUSPENDED->CLOSED',
    'SUSPENDED->CANCELLED',
    'CLOSED->LOCKED',
    'CLOSED->SUSPENDED',
    'CLOSED->CANCELLED',
    'LOCKED->CANCELLED',
  ];
  ctx.arraySet(transitionPairs, expectedPairs, 'TRANSITIONS_EXACT_PAIRS');

  ctx.array(machine.forbiddenTransitions, 'FORBIDDEN_TRANSITIONS_ARRAY');
  const forbiddenPairs = machine.forbiddenTransitions.map((t) => `${t.from}->${t.to}`);
  for (const pair of ['CLOSED->OPEN', 'LOCKED->OPEN', 'LOCKED->DRAFT', 'CANCELLED->OPEN', 'CANCELLED->LOCKED', 'DRAFT->LOCKED']) {
    ctx.require(forbiddenPairs.includes(pair), `FORBIDDEN_TRANSITIONS_CONTAINS_${pair.replace('->', '_TO_')}`);
  }

  const postponement = ctx.record(machine.postponementPolicy, 'POSTPONEMENT_POLICY_SHAPE');
  ctx.keys(postponement, ['d111Compliance', 'onPostpone', 'onReschedule', 'preservesPriorVersion'], 'POSTPONEMENT_POLICY_KEYS');
  ctx.boolean(postponement.d111Compliance, true, 'POSTPONEMENT_D111');
  ctx.require(postponement.onPostpone === 'SUSPENDED', 'POSTPONEMENT_ON_POSTPONE');
  ctx.boolean(postponement.preservesPriorVersion, true, 'POSTPONEMENT_PRESERVES_PRIOR');

  const override = ctx.record(machine.overridePolicy, 'OVERRIDE_POLICY_SHAPE');
  ctx.keys(override, ['d116Compliance', 'd117Compliance', 'allowedStates', 'forbiddenStates', 'distinctActorsRequired', 'odds10ProofRequired', 'createsNewPriceVersion', 'neverRewritesAcceptedOdds'], 'OVERRIDE_POLICY_KEYS');
  ctx.boolean(override.d116Compliance, true, 'OVERRIDE_D116');
  ctx.boolean(override.d117Compliance, true, 'OVERRIDE_D117');
  ctx.arrayExact(override.allowedStates, ['SUSPENDED'], 'OVERRIDE_ALLOWED_STATES');
  ctx.arraySet(override.forbiddenStates, ['DRAFT', 'OPEN', 'CLOSED', 'LOCKED', 'CANCELLED'], 'OVERRIDE_FORBIDDEN_STATES');
  ctx.boolean(override.distinctActorsRequired, true, 'OVERRIDE_DISTINCT_ACTORS');
  ctx.boolean(override.odds10ProofRequired, true, 'OVERRIDE_ODDS10_PROOF');
  ctx.boolean(override.createsNewPriceVersion, true, 'OVERRIDE_NEW_VERSION');
  ctx.boolean(override.neverRewritesAcceptedOdds, true, 'OVERRIDE_NEVER_REWRITES');
}

function validateGovernanceAndAuthority(ctx, value) {
  const gov = ctx.record(value, 'GOVERNANCE_SHAPE');
  ctx.keys(gov, ['roles', 'capabilities', 'roleCapabilityMatrix', 'scopeDerivationRule', 'odds10PreOverrideProof'], 'GOVERNANCE_KEYS');
  ctx.arraySet(gov.roles, OPERATIONAL_ROLES, 'GOVERNANCE_ROLES');
  ctx.arraySet(gov.capabilities, ODDS_CAPABILITIES, 'GOVERNANCE_CAPABILITIES');

  const matrix = ctx.record(gov.roleCapabilityMatrix, 'ROLE_CAPABILITY_MATRIX_SHAPE');
  ctx.keys(matrix, OPERATIONAL_ROLES, 'ROLE_CAPABILITY_MATRIX_KEYS');
  ctx.arraySet(matrix.ADMINISTRADOR_GERAL, ODDS_CAPABILITIES, 'ADMINISTRADOR_GERAL_CAPABILITIES');
  ctx.arraySet(matrix.ADMINISTRADOR_COMPETICAO, ODDS_CAPABILITIES, 'ADMINISTRADOR_COMPETICAO_CAPABILITIES');
  ctx.arraySet(matrix.MODERADOR, ['VIEW_ODDS_MARKET'], 'MODERADOR_CAPABILITIES');

  ctx.require(
    gov.scopeDerivationRule === 'Derivado exclusivamente da entidade sports_event.competition_id no banco; payload do cliente nunca decide autoridade.',
    'SCOPE_DERIVATION_RULE',
  );

  const odds10 = ctx.record(gov.odds10PreOverrideProof, 'ODDS10_PROOF_SHAPE');
  ctx.keys(odds10, ['required', 'fields', 'rule'], 'ODDS10_PROOF_KEYS');
  ctx.boolean(odds10.required, true, 'ODDS10_PROOF_REQUIRED');
  ctx.arraySet(odds10.fields, [
    'attemptTimestamp', 'sourceCorrectionReference', 'recalculationAttempted', 'residualDistortionReason',
  ], 'ODDS10_PROOF_FIELDS');
  ctx.require(
    odds10.rule === 'Administrador deve corrigir fonte oficial ou tentar recalculacao antes de propor override; proposta rejeitada se faltar prova.',
    'ODDS10_PROOF_RULE',
  );
}

function validateMappings(ctx, value) {
  const mappings = ctx.record(value, 'MAPPINGS_SHAPE');
  ctx.keys(mappings, ['sportsToMarketMapping'], 'MAPPINGS_KEYS');
  ctx.array(mappings.sportsToMarketMapping, 'SPORTS_TO_MARKET_MAPPING');
  ctx.require(mappings.sportsToMarketMapping.length === 5, 'MAPPINGS_COUNT');
  for (const [index, item] of mappings.sportsToMarketMapping.entries()) {
    ctx.keys(item, ['sportsFact', 'hasScore', 'marketState', 'marketOutcome', 'feedsElo', 'settlement'], `MAPPING_${index}_KEYS`);
  }
}

function validateFixtures(ctx, value) {
  ctx.array(value, 'FIXTURES_ARRAY');
  ctx.require(value.length >= 8, 'FIXTURES_COUNT');
  const fixtureIds = value.map((f) => f.id);
  const requiredFixtureIds = [
    'fixture_generic_format_rejected',
    'fixture_head_to_head_valid',
    'fixture_sporting_result_h2h_score',
    'fixture_sporting_result_draw_regulation',
    'fixture_administrative_walkover',
    'fixture_abandoned_no_score',
    'fixture_cancelled_lifecycle',
    'fixture_postponed_and_rescheduled',
    'fixture_override_suspended_maker_checker',
    'fixture_unlisted_transitions',
  ];
  for (const reqId of requiredFixtureIds) {
    ctx.require(fixtureIds.includes(reqId), `FIXTURE_PRESENCE_${reqId}`);
  }
}

function validateApproval(ctx, candidate, requireApproval) {
  const approval = ctx.record(candidate.approval, 'APPROVAL_SHAPE');
  ctx.keys(approval, ['kind', 'status', 'approvedAt', 'approvedBy', 'digest', 'reviewReference', 'reviewedArtifactHashes'], 'APPROVAL_KEYS');
  ctx.require(approval.kind === 'human_checkpoint_token', 'APPROVAL_KIND');
  ctx.require(approval.status === PENDING_APPROVAL || approval.status === 'approved', 'APPROVAL_STATUS');

  const pending = approval.status === PENDING_APPROVAL;
  ctx.require(
    (candidate.decisionStatus === PENDING_DECISION_STATUS && pending)
      || (candidate.decisionStatus === APPROVED_DECISION_STATUS && !pending),
    'APPROVAL_STATE',
    'decisionStatus e approval.status devem permanecer coerentes',
  );

  if (pending) {
    for (const key of ['approvedAt', 'approvedBy', 'digest', 'reviewReference', 'reviewedArtifactHashes']) {
      ctx.require(approval[key] === null, `APPROVAL_PENDING_${key.toUpperCase()}`, 'campo deve permanecer nulo antes do gate');
    }
    if (requireApproval) fail('APPROVAL_PENDING', 'checkpoint humano 04-02/T2 ainda nao foi registrado');
    return;
  }

  ctx.require(isCanonicalUtcInstant(approval.approvedAt), 'APPROVAL_APPROVED_AT', 'timestamp UTC canonico obrigatorio');
  ctx.require(approval.approvedBy === APPROVED_BY, 'APPROVAL_APPROVED_BY', 'identidade do responsavel divergente');
  ctx.require(isSha256(approval.digest), 'APPROVAL_DIGEST');
  ctx.require(APPROVAL_REFERENCE_PATTERN.test(approval.reviewReference), 'APPROVAL_REVIEW_REFERENCE', 'referencia do checkpoint divergente');
  ctx.require(isRecord(approval.reviewedArtifactHashes), 'APPROVAL_REVIEWED_HASHES');
  ctx.keys(approval.reviewedArtifactHashes, ['candidateSnapshot'], 'APPROVAL_REVIEWED_HASHES_KEYS');
  ctx.require(approval.reviewedArtifactHashes.candidateSnapshot === approval.digest, 'APPROVAL_DIGEST_BINDING');
}

export function validateCandidate(candidate, options = {}) {
  const requireApproval = options.requireApproval === true;
  const ctx = makeContext();
  const root = validateRoot(ctx, candidate);
  validateDecisionBasis(ctx, root.decisionBasis);
  validateBoundaryContract(ctx, root.boundaryContract);
  validateTaxonomy(ctx, root.taxonomy);
  validateMarketStateMachine(ctx, root.marketStateMachine);
  validateGovernanceAndAuthority(ctx, root.governanceAndAuthority);
  validateMappings(ctx, root.mappings);
  validateFixtures(ctx, root.fixtures);
  validateApproval(ctx, root, requireApproval);
  return Object.freeze({
    valid: true,
    checks: ctx.checks,
    approvalStatus: root.approval.status,
    decisionStatus: root.decisionStatus,
  });
}

function assertInside(root, target, rule) {
  const relativePath = relative(root, target);
  if (relativePath === '' || isAbsolute(relativePath) || relativePath.startsWith('..')) {
    fail(rule, 'caminho fora da raiz autorizada');
  }
}

export function resolveCandidatePath(projectRoot = defaultProjectRoot) {
  if (typeof projectRoot !== 'string' || projectRoot.length === 0) {
    fail('CANDIDATE_ROOT', 'raiz do projeto invalida');
  }
  const root = resolve(projectRoot);
  let rootReal;
  try {
    rootReal = realpathSync(root);
    if (!statSync(rootReal).isDirectory()) fail('CANDIDATE_ROOT', 'raiz nao e diretorio');
  } catch (error) {
    if (error instanceof OddsContractCandidateValidationError) throw error;
    fail('CANDIDATE_ROOT', 'raiz do projeto nao existe');
  }
  const expected = resolve(rootReal, CANDIDATE_RELATIVE_PATH);
  assertInside(rootReal, expected, 'CANDIDATE_PATH');
  let candidateReal;
  try {
    candidateReal = realpathSync(expected);
    if (!statSync(candidateReal).isFile()) fail('CANDIDATE_PATH', 'candidato nao e arquivo');
  } catch (error) {
    if (error instanceof OddsContractCandidateValidationError) throw error;
    fail('CANDIDATE_PATH', 'candidato ausente');
  }
  assertInside(rootReal, candidateReal, 'CANDIDATE_SYMLINK');
  return candidateReal;
}

export function readCandidateFile(projectRoot = defaultProjectRoot) {
  const candidatePath = resolveCandidatePath(projectRoot);
  let source;
  try {
    source = readFileSync(candidatePath, 'utf8');
  } catch {
    fail('CANDIDATE_READ', 'nao foi possivel ler o candidato');
  }
  try {
    return JSON.parse(source);
  } catch {
    fail('CANDIDATE_JSON', 'JSON invalido');
  }
}

function readCandidateSource(projectRoot = defaultProjectRoot) {
  const candidatePath = resolveCandidatePath(projectRoot);
  try {
    return readFileSync(candidatePath, 'utf8');
  } catch {
    fail('CANDIDATE_READ', 'nao foi possivel ler o candidato');
  }
}

function normalizeSnapshotLineEndings(source) {
  const crlfCount = (source.match(/\r\n/gu) || []).length;
  const bareCrCount = (source.match(/\r(?!\n)/gu) || []).length;
  const bareLfCount = (source.match(/(?<!\r)\n/gu) || []).length;
  if (bareCrCount > 0 || (crlfCount > 0 && bareLfCount > 0)) {
    fail('CANDIDATE_LINE_ENDINGS', 'finais de linha mistos nao sao aceitos');
  }
  return source.replace(/\r\n?/gu, '\n');
}

function pendingSnapshotSource(source) {
  const normalizedSource = normalizeSnapshotLineEndings(source);
  if (!normalizedSource.endsWith('\n') || normalizedSource.endsWith('\n\n')) {
    fail('CANDIDATE_TRAILING_WHITESPACE', 'o candidato deve terminar com uma unica quebra de linha LF');
  }
  const newline = '\n';
  const marker = '  "approval": {';
  const approvalStart = normalizedSource.lastIndexOf(marker);
  if (approvalStart < 0 || normalizedSource.indexOf(marker) !== approvalStart) {
    fail('APPROVAL_SNAPSHOT_SHAPE', 'bloco approval nao e unico ou nao possui formato canonico');
  }
  const approvedStatus = '  "decisionStatus": "approved_human_checkpoint_04-02-T2",';
  const pendingStatus = '  "decisionStatus": "candidate_pending_human_checkpoint_04-02-T2",';
  const prefix = normalizedSource.slice(0, approvalStart);
  if (prefix.indexOf(approvedStatus) < 0 || prefix.indexOf(approvedStatus) !== prefix.lastIndexOf(approvedStatus)) {
    fail('APPROVAL_SNAPSHOT_STATUS', 'decisionStatus aprovado nao possui ocorrencia canonica');
  }
  if (normalizedSource.at(-2) !== '}') {
    fail('CANDIDATE_TRAILING_WHITESPACE', 'o candidato deve ter somente uma quebra de linha apos o JSON');
  }
  const normalizedPrefix = prefix.replace(approvedStatus, pendingStatus);
  const pendingApproval = [
    '  "approval": {',
    '    "kind": "human_checkpoint_token",',
    '    "status": "pending_human_checkpoint_04-02-T2",',
    '    "approvedAt": null,',
    '    "approvedBy": null,',
    '    "digest": null,',
    '    "reviewReference": null,',
    '    "reviewedArtifactHashes": null',
    '  }',
    '}',
  ].join(newline);
  return `${normalizedPrefix}${pendingApproval}${newline}`;
}

export function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

export function approvalSnapshotDigest(source) {
  return sha256(pendingSnapshotSource(source)).toUpperCase();
}

function validateApprovalSnapshotDigest(candidate, source) {
  if (candidate.approval.status !== 'approved') return;
  const observedDigest = approvalSnapshotDigest(source);
  if (observedDigest.toUpperCase() !== candidate.approval.digest.toUpperCase()) {
    fail('APPROVAL_DIGEST_MISMATCH', 'digest aprovado nao corresponde ao snapshot pre-aprovacao do candidato');
  }
}

export function validateCandidateFile(projectRoot = defaultProjectRoot, options = {}) {
  const source = readCandidateSource(projectRoot);
  let candidate;
  try {
    candidate = JSON.parse(source);
  } catch {
    fail('CANDIDATE_JSON', 'JSON invalido');
  }
  const evidence = validateCandidate(candidate, options);
  validateApprovalSnapshotDigest(candidate, source);
  return Object.freeze({
    ...evidence,
    candidateSnapshotDigest: candidate.approval.status === 'approved'
      ? candidate.approval.digest
      : null,
  });
}

export function parseArguments(argumentsList) {
  if (!Array.isArray(argumentsList)) fail('CLI_ARGUMENTS', 'argumentos invalidos');
  if (argumentsList.length === 1 && argumentsList[0] === '--check') {
    return Object.freeze({ requireApproval: false });
  }
  if (argumentsList.length === 1 && argumentsList[0] === '--require-approval') {
    return Object.freeze({ requireApproval: true });
  }
  fail('CLI_ARGUMENTS', 'uso: node scripts/odds-contract.test.mjs --check | --require-approval');
}

export function sanitizeError(error) {
  if (error instanceof OddsContractCandidateValidationError) return error.message;
  return 'INTERNAL_ERROR: falha interna durante a validacao.';
}

export function runCli(argumentsList = process.argv.slice(2), projectRoot = defaultProjectRoot) {
  let options;
  try {
    options = parseArguments(argumentsList);
    const evidence = validateCandidateFile(projectRoot, options);
    console.log(`[odds-contract] OK: ${evidence.checks} verificacoes; T2=${evidence.approvalStatus}.`);
    return 0;
  } catch (error) {
    const exitCode = error instanceof OddsContractCandidateValidationError
      && error.rule === 'APPROVAL_PENDING'
      ? 1
      : error instanceof OddsContractCandidateValidationError
        && error.rule === 'CLI_ARGUMENTS'
        ? 2
        : 1;
    console.error(`[odds-contract] ERRO: ${sanitizeError(error)}`);
    return exitCode;
  }
}

// ---------------------------------------------------------------------------
// Test Suite (runs under `node --test`)
// ---------------------------------------------------------------------------

const scriptPath = fileURLToPath(import.meta.url);
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

function approvedCandidateClone(source) {
  const candidate = candidateClone();
  const rawSource = source ?? readFileSync(join(defaultProjectRoot, CANDIDATE_RELATIVE_PATH), 'utf8');
  const normalized = normalizeSnapshotLineEndings(rawSource);
  candidate.decisionStatus = APPROVED_DECISION_STATUS;
  const approvedMarker = 'approved_human_checkpoint_04-02-T2';
  const pendingSnapshot = normalized.includes(approvedMarker) ? pendingSnapshotSource(normalized) : normalized;
  const digest = sha256(pendingSnapshot).toUpperCase();
  candidate.approval = {
    kind: 'human_checkpoint_token',
    status: 'approved',
    approvedAt: '2026-09-07T16:00:00.000Z',
    approvedBy: APPROVED_BY,
    digest,
    reviewReference: "04-02/T2@2026-09-07: owner message 'aprovar contrato e state machine 04-02'",
    reviewedArtifactHashes: {
      candidateSnapshot: digest,
    },
  };
  return candidate;
}

function assertMutationApplied(original, mutated, label) {
  assert.notDeepEqual(mutated, original, `${label}: mutation did not change the candidate`);
}

function assertRule(mutator, expectedRule, label = expectedRule) {
  const original = pendingCandidateClone();
  const mutated = structuredClone(original);
  mutator(mutated);
  assertMutationApplied(original, mutated, label);
  assert.throws(
    () => validateCandidate(mutated),
    (error) => error instanceof OddsContractCandidateValidationError
      && error.rule === expectedRule,
    `${label}: expected rule ${expectedRule}`,
  );
}

test('candidato baseline em estado pendente passa na validacao estrutural', () => {
  const pending = pendingCandidateClone();
  const evidence = validateCandidate(pending, { requireApproval: false });
  assert.equal(evidence.valid, true);
  assert.equal(evidence.approvalStatus, PENDING_APPROVAL);
  assert.equal(evidence.decisionStatus, PENDING_DECISION_STATUS);
  assert.equal(pending.runtimeConsumable, false);
  assert.ok(evidence.checks > 50);
});

test('candidato aprovado com token humano valido passa em requireApproval', () => {
  const approved = approvedCandidateClone();
  const evidence = validateCandidate(approved, { requireApproval: true });
  assert.equal(evidence.valid, true);
  assert.equal(evidence.approvalStatus, 'approved');
  assert.equal(evidence.decisionStatus, APPROVED_DECISION_STATUS);
});

test('candidato pendente falha quando requireApproval e true', () => {
  assert.throws(
    () => validateCandidate(pendingCandidateClone(), { requireApproval: true }),
    (error) => error instanceof OddsContractCandidateValidationError
      && error.rule === 'APPROVAL_PENDING',
  );
});

test('rejeita candidato se runtimeConsumable for true', () => {
  assertRule((c) => { c.runtimeConsumable = true; }, 'RUNTIME_CONSUMABLE');
});

test('rejeita purpose alterado que tente liberar producao', () => {
  assertRule((c) => { c.purpose = 'runtime production contract'; }, 'PURPOSE');
});

test('rejeita opcao de integracao diferente da opcao A aprovada', () => {
  assertRule((c) => { c.decisionBasis.selectedOption = 'B'; }, 'SELECTED_OPTION');
  assertRule((c) => { c.decisionBasis.selectedOptionKey = 'opaque_after_commit_application_event'; }, 'SELECTED_OPTION_KEY');
});

test('rejeita violacao de fronteira modular com imports de sports.internal', () => {
  assertRule((c) => { c.decisionBasis.modularBoundary.forbiddenImports = []; }, 'BOUNDARY_FORBIDDEN_IMPORTS');
  assertRule((c) => { c.decisionBasis.modularBoundary.publicPortClass = 'br.com.betufu.sports.internal.Port'; }, 'BOUNDARY_PORT_CLASS');
});

test('rejeita contrato com metodos ausentes ou divergentes', () => {
  assertRule((c) => { c.boundaryContract.port.methods.pop(); }, 'PORT_METHODS_COUNT');
});

test('rejeita formato GENERIC como formato permitido no modelo de evento', () => {
  assertRule((c) => { c.boundaryContract.models.SportsEventForOdds.allowedFormats = ['GENERIC']; }, 'EVENT_MODEL_ALLOWED_FORMATS');
  assertRule((c) => { c.boundaryContract.models.SportsEventForOdds.slotCount = 3; }, 'EVENT_MODEL_SLOT_COUNT');
});

test('rejeita taxonomia sem uma das 5 classes obrigatorias', () => {
  assertRule((c) => { c.taxonomy.classes = ['SPORTING_RESULT']; }, 'TAXONOMY_CLASSES');
});

test('rejeita score em fatos administrativos (W.O., abandono, cancelamento, adiamento)', () => {
  assertRule((c) => { c.taxonomy.definitions.ADMINISTRATIVE_WALKOVER.hasScore = true; }, 'WALKOVER_HAS_SCORE');
  assertRule((c) => { c.taxonomy.definitions.ABANDONED_NO_VALID_RESULT.hasScore = true; }, 'ABANDONED_HAS_SCORE');
  assertRule((c) => { c.taxonomy.definitions.CANCELLED.hasScore = true; }, 'CANCELLED_HAS_SCORE');
  assertRule((c) => { c.taxonomy.definitions.POSTPONED.hasScore = true; }, 'POSTPONED_HAS_SCORE');
});

test('rejeita alimentacao de Elo/Davidson para W.O., abandono, cancelamento ou adiamento', () => {
  assertRule((c) => { c.taxonomy.definitions.ADMINISTRATIVE_WALKOVER.feedsElo = true; }, 'WALKOVER_FEEDS_ELO');
  assertRule((c) => { c.taxonomy.definitions.ADMINISTRATIVE_WALKOVER.feedsDavidson = true; }, 'WALKOVER_FEEDS_DAVIDSON');
  assertRule((c) => { c.taxonomy.definitions.ABANDONED_NO_VALID_RESULT.feedsElo = true; }, 'ABANDONED_FEEDS_ELO');
  assertRule((c) => { c.taxonomy.definitions.CANCELLED.feedsElo = true; }, 'CANCELLED_FEEDS_ELO');
  assertRule((c) => { c.taxonomy.definitions.POSTPONED.feedsElo = true; }, 'POSTPONED_FEEDS_ELO');
});

test('rejeita vencedor em abandono ou cancelamento', () => {
  assertRule((c) => { c.taxonomy.definitions.ABANDONED_NO_VALID_RESULT.winnerAllowed = true; }, 'ABANDONED_WINNER_FORBIDDEN');
  assertRule((c) => { c.taxonomy.definitions.CANCELLED.winnerAllowed = true; }, 'CANCELLED_WINNER_FORBIDDEN');
});

test('rejeita maquina de estados sem os 6 estados obrigatorios', () => {
  assertRule((c) => { c.marketStateMachine.states.pop(); }, 'MARKET_STATES');
});

test('rejeita maquina de estados com estado inicial diferente de DRAFT', () => {
  assertRule((c) => { c.marketStateMachine.initialState = 'OPEN'; }, 'INITIAL_STATE');
});

test('rejeita exclusao de transicoes obrigatorias da tabela', () => {
  assertRule((c) => { c.marketStateMachine.transitions.pop(); }, 'TRANSITIONS_EXACT_PAIRS');
});

test('rejeita politica de override que permita alteracao em mercado nao suspenso', () => {
  assertRule((c) => { c.marketStateMachine.overridePolicy.allowedStates = ['OPEN']; }, 'OVERRIDE_ALLOWED_STATES');
  assertRule((c) => { c.marketStateMachine.overridePolicy.distinctActorsRequired = false; }, 'OVERRIDE_DISTINCT_ACTORS');
  assertRule((c) => { c.marketStateMachine.overridePolicy.odds10ProofRequired = false; }, 'OVERRIDE_ODDS10_PROOF');
});

test('rejeita roles ou capabilities divergentes da governanca', () => {
  assertRule((c) => { c.governanceAndAuthority.roles.pop(); }, 'GOVERNANCE_ROLES');
  assertRule((c) => { c.governanceAndAuthority.capabilities.pop(); }, 'GOVERNANCE_CAPABILITIES');
  assertRule((c) => { c.governanceAndAuthority.roleCapabilityMatrix.MODERADOR = ['MANAGE_ODDS_MARKET']; }, 'MODERADOR_CAPABILITIES');
});

test('rejeita regra de escopo baseada em payload do cliente', () => {
  assertRule((c) => { c.governanceAndAuthority.scopeDerivationRule = 'client trust'; }, 'SCOPE_DERIVATION_RULE');
});

test('rejeita comprovacao ODDS-10 sem campos obrigatorios', () => {
  assertRule((c) => { c.governanceAndAuthority.odds10PreOverrideProof.fields = []; }, 'ODDS10_PROOF_FIELDS');
  assertRule((c) => { c.governanceAndAuthority.odds10PreOverrideProof.required = false; }, 'ODDS10_PROOF_REQUIRED');
});

test('rejeita fixtures sem casos obrigatorios', () => {
  assertRule((c) => { c.fixtures = []; }, 'FIXTURES_COUNT');
  assertRule((c) => { c.fixtures = c.fixtures.filter((f) => f.id !== 'fixture_generic_format_rejected'); }, 'FIXTURE_PRESENCE_fixture_generic_format_rejected');
  assertRule((c) => { c.fixtures = c.fixtures.filter((f) => f.id !== 'fixture_unlisted_transitions'); }, 'FIXTURE_PRESENCE_fixture_unlisted_transitions');
});

test('parser aceita somente os modos literais e falha fechado', () => {
  assert.deepEqual(parseArguments(['--check']), { requireApproval: false });
  assert.deepEqual(parseArguments(['--require-approval']), { requireApproval: true });
  assert.throws(() => parseArguments([]), (error) => error.rule === 'CLI_ARGUMENTS');
  assert.throws(() => parseArguments(['--unknown']), (error) => error.rule === 'CLI_ARGUMENTS');
  assert.throws(() => parseArguments(['--check', '--require-approval']), (error) => error.rule === 'CLI_ARGUMENTS');
});

function runCliProcess(...args) {
  const env = { ...process.env };
  delete env.NODE_TEST_CONTEXT;
  return spawnSync(process.execPath, [scriptPath, ...args], {
    cwd: defaultProjectRoot,
    encoding: 'utf8',
    shell: false,
    windowsHide: true,
    env,
  });
}

test('CLI --check retorna zero no fixture do disco', () => {
  const result = runCliProcess('--check');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /T2=(pending_human_checkpoint_04-02-T2|approved)/u);
});

test('CLI --require-approval respeita o status atual do fixture', () => {
  const result = runCliProcess('--require-approval');
  if (baseline.approval.status === 'approved') {
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /T2=approved/u);
  } else {
    assert.equal(result.status, 1);
    assert.match(result.stderr, /APPROVAL_PENDING/u);
  }
});

test('CLI sem flag ou com flag desconhecida retorna dois', () => {
  const noFlag = runCliProcess();
  assert.equal(noFlag.status, 2);
  assert.match(noFlag.stderr, /CLI_ARGUMENTS/u);

  const unknown = runCliProcess('--invalid');
  assert.equal(unknown.status, 2);
  assert.match(unknown.stderr, /CLI_ARGUMENTS/u);
});

// Invoked as standalone script (outside node test runner)
const isNodeTestRunner = process.execArgv.includes('--test') || Boolean(process.env.NODE_TEST_CONTEXT);
const invokedDirectly = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (invokedDirectly && !isNodeTestRunner) {
  process.exit(runCli());
}
