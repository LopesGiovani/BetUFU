#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readFileSync, realpathSync, statSync } from 'node:fs';
import {
  dirname,
  isAbsolute,
  relative,
  resolve,
} from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptsDirectory = dirname(fileURLToPath(import.meta.url));
export const defaultProjectRoot = resolve(scriptsDirectory, '..');
export const CANDIDATE_RELATIVE_PATH = 'scripts/sports-odds-source-candidate.json';
export const PENDING_APPROVAL = 'pending_human_checkpoint_04-15-T2';
export const PENDING_DECISION_STATUS = 'candidate_pending_human_checkpoint_04-15-T2';
export const APPROVED_DECISION_STATUS = 'approved_human_checkpoint_04-15-T2';
export const APPROVED_BY = 'LopesGiovani';

const PURPOSE = 'Candidato documental para a autoridade de outcomes, a taxonomia e o replay temporal antes de schema ou runtime.';
const REPLAY_ALGORITHM = 'select the admissible fact in each supersession chain at the cutoff, then order sporting facts canonically';
const APPROVAL_REFERENCE_PATTERN = /^04-15\/T2@\d{4}-\d{2}-\d{2}: owner message 'aprovar source (?:04-15|14-15)'(?:(?: normalized to plan 04-15)|(?:; typo interpreted as plan 04-15))?$/u;

const ROOT_KEYS = Object.freeze([
  'schemaVersion', 'phase', 'plan', 'kind', 'runtimeConsumable', 'purpose',
  'decisionStatus', 'decisionBasis', 'authority', 'taxonomy', 'envelope',
  'payloads', 'head', 'temporalProtocol', 'replay', 'eloPolicy', 'legacy',
  'compatibility', 'administrativeWorkflow', 'regulation', 'propagation',
  'recovery', 'roster', 'constraints', 'migrationCandidates', 'openQuestions',
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
const LIFECYCLE_STATUSES = Object.freeze([
  'RASCUNHO', 'PUBLICADO', 'SUSPENSO', 'ADIADO', 'CANCELADO',
]);
const ADMIN_SCORE_FIELDS = Object.freeze([
  'homeScore',
  'awayScore',
  'score',
  'scores',
  'home_score',
  'away_score',
  'sourceHomeScore',
  'sourceAwayScore',
  'modelHomeScore',
  'modelAwayScore',
  'scoreForModel',
  'homeScoreForModel',
  'awayScoreForModel',
  'confirmedVersionId',
]);
const MIGRATION_FILENAMES = Object.freeze([
  'VNN_MARKETS__create_odds_market_lineage.sql',
  'VNN_GOV__extend_odds_governance_audit.sql',
  'VNN_SOURCE__create_sports_outcome_authority.sql',
  'VNN_ROSTER__bind_event_roster_publication.sql',
]);

const EXPECTED = Object.freeze({
  decisionBasisKeys: [
    'selectedOption', 'selectedOptionDescription', 'alreadyChosen',
    'ratifiedAtCheckpoint', 'pendingRatification',
  ],
  authorityKeys: [
    'sourceOfTruth', 'ownerModule', 'consumerModule', 'eventHeadCardinality',
    'factsStorage', 'supersession', 'headRole', 'headAndFactAtomicity',
    'lifecycleOwner', 'oddsOwnsCopyOfScore', 'secondAuthority',
  ],
  taxonomyKeys: [
    'classes', 'outcomeClasses', 'lifecycleClasses', 'definitions',
    'resultVersionStatuses', 'lifecycleV24Statuses', 'separationRules',
  ],
  envelopeKeys: [
    'physicalName', 'unknownFields', 'payloadDiscriminator',
    'exclusivePayloads', 'requiredIdentity', 'requiredLineage',
    'requiredSource', 'eventScopeIsComposite', 'candidateNamesRemainOpen',
  ],
  sportingPayloadKeys: [
    'requiredFields', 'v16Reference', 'scorePolicy', 'regulationGate',
    'correction',
  ],
  v16ReferenceKeys: [
    'table', 'versionIdField', 'exactReference', 'statusAllowlist',
    'scoreTable', 'slotCount', 'slotNumbers', 'readOriginalScores',
    'copyScoresIntoAuthority', 'synthesizeScores',
  ],
  scorePolicyKeys: [
    'source', 'originalOnly', 'nonNegative', 'exactlyTwoForHeadToHead',
    'administrativeScoreForbidden',
  ],
  administrativePayloadKeys: [
    'requiredFields', 'forbiddenFields', 'winner', 'feedsElo',
    'feedsDavidson', 'marketEffect',
  ],
  lifecyclePayloadKeys: ['payloadAllowed', 'lifecycleTable', 'lifecycleStatus'],
  headKeys: [
    'onePerEvent', 'oneCurrentHeadWhenFactExists', 'uniqueKeyCandidate',
    'mutableProjectionOnly', 'historyIsAppendOnly', 'updatedTogetherWithFact',
    'compareAndSetRequired', 'casInputCandidate', 'supersessionChain',
    'headCannotRewriteHistory',
    'headCannotBeUsedForReplay', 'maxVersionNoCannotBeUsedForReplay',
    'futureEventNeedsNoHead',
  ],
  temporalKeys: [
    'fields', 'cutoffRule', 'ordering', 'tieBreak', 'correctionAfterCutoff',
    'snapshotCaptureIsNotKnownAt', 'missingTimeDisposition', 'deterministic',
  ],
  sportTimeFieldKeys: [
    'meaning', 'candidateSource', 'timezoneSource', 'factField',
    'editionProvenance', 'snapshotAtFactCreation', 'currentEventLookupForbidden',
    'required',
  ],
  temporalFieldKeys: ['meaning', 'candidateSource', 'timezoneSource', 'required'],
  knownAtFieldKeys: ['meaning', 'source', 'required', 'distinctFromCapture'],
  confirmedAtFieldKeys: [
    'meaning', 'source', 'requiredForSportingResult', 'distinctFromKnownAt',
  ],
  replayKeys: [
    'historyRequired', 'algorithm', 'steps', 'forbiddenShortcuts',
    'leakageControl', 'reproducibility',
  ],
  eloKeys: [
    'initialRating', 'ratingScope', 'globalRating', 'headToHeadContext', 'experiencePolicy',
    'editionRegression', 'chronologicalProcessing', 'recency', 'draw',
    'lowSample', 'eligibleOutcome', 'forbiddenInputs', 'margin', 'publication',
  ],
  experienceKeys: [
    'provisionalK', 'matureK', 'experienceThreshold', 'thresholdIsNotImplicit',
  ],
  headToHeadKeys: ['contextualEvidenceAllowed', 'automaticBonus', 'promotionRequires'],
  regressionKeys: ['fractionTowardInitial', 'initialRating', 'versioned'],
  recencyKeys: ['formula', 'monthlyHalfLife', 'arbitraryHalfLifeForbidden'],
  drawKeys: [
    'conditionalOnRegulation', 'twoOutcomeWhenDrawImpossible',
    'threeOutcomeWhenRegulationAllows', 'davidson', 'sharedConservativePrior',
    'specializationThreshold',
  ],
  davidsonKeys: ['enabledOnlyForThreeOutcomeBranch', 'parameters'],
  lowSampleKeys: ['estimate', 'publicConfidenceLabel', 'confidenceBands'],
  eligibleOutcomeKeys: [
    'kinds', 'v16Statuses', 'administrativeWalkover', 'abandonment',
    'cancelled', 'postponed', 'futureEventNeedsNoOutcome',
  ],
  marginKeys: ['initial', 'configurable', 'versioned', 'formula'],
  publicationKeys: [
    'fairProbabilitySeparate', 'publishedPriceImmutable', 'decimalPlaces',
    'roundingMode',
  ],
  legacyKeys: [
    'backfillMode', 'authoritativeEvidence', 'forbiddenEvidence',
    'numericWalkoverDisposition', 'unresolvedDisposition', 'requiredBeforeClassification',
    'classificationStates', 'noSilentClassification', 'quarantineReleaseRequires',
  ],
  compatibilityKeys: ['confirmedVersionId', 'v16', 'v24', 'crossKindCorrection'],
  compatibilityPointerKeys: [
    'currentField', 'role', 'secondAuthority', 'sportingResultOnly',
    'readersToReconcile', 'writersToReconcile',
    'administrativeCorrectionPolicy', 'reconciliationRequiredBeforeRuntime',
  ],
  compatibilityV16Keys: [
    'table', 'scoresRemainOwnedBySports', 'rowsRemainImmutable',
    'mustRemainByteIdentical',
  ],
  compatibilityV24Keys: [
    'table', 'lifecycleRemainsSeparate', 'rowsRemainImmutable',
    'mustRemainByteIdentical',
  ],
  crossKindKeys: [
    'createsNewFact', 'linksSupersededFact', 'atomicHeadTransition',
    'oldFactRemainsReadable', 'acceptedOddsAreNotRewritten', 'policy',
  ],
  workflowKeys: [
    'serverSideOnly', 'operations', 'scopeDerivedFromPersistedEvent',
    'sourceReferenceRequired', 'correctionReasonRequired', 'bulkImport',
    'newUi', 'clientAuthority', 'transaction', 'auditRequired', 'idempotency',
    'rolesAndCapabilities', 'administrativePayloadHasNoScore',
  ],
  regulationKeys: [
    'versioned', 'source', 'drawEligibility', 'missingRegulation',
    'noImplicitDefault',
  ],
  propagationKeys: [
    'strategy', 'candidateStrategies', 'mustPreserve',
    'correctionTriggersNewLineage', 'noUnapprovedSynchronousAssumption',
  ],
  recoveryKeys: [
    'appendOnly', 'replayFromHistory', 'headRebuild', 'partialFailure',
    'manualRepair', 'deleteHistory', 'useCurrentHeadAsHistory',
  ],
  rosterKeys: [
    'marketOpenRequiresPublishedRoster', 'requiredStatus', 'bindingMode',
    'perParticipantPublicationId', 'perParticipantVersionNo',
    'perParticipantKnownAt', 'correctionEffect', 'freezeAndAtomicity',
    'latestImplicitSelection', 'lineupPerMatch', 'piiInOddsContract',
    'correctionCreatesNewPublication',
  ],
  constraintKeys: [
    'database', 'checkCannotProveCrossTableCoherence', 'candidateForeignKeys',
    'candidateUniqueConstraints', 'deferredConstraintTriggers',
    'databaseTriggers', 'constraintStatus', 'noCheckAcrossTablesPretended',
  ],
  migrationKeys: [
    'status', 'numbersFixed', 'doesNotInferFromV24', 'sequence',
    'ratifyLiteralFilenamesBeforeExecution',
  ],
  approvalKeys: [
    'kind', 'status', 'approvedAt', 'approvedBy', 'digest', 'reviewReference',
    'reviewedArtifactHashes',
  ],
});

export class SportsOddsSourceCandidateValidationError extends Error {
  constructor(rule, message) {
    super(`[${rule}] ${message}`);
    this.name = 'SportsOddsSourceCandidateValidationError';
    this.rule = rule;
  }
}

function fail(rule, message) {
  throw new SportsOddsSourceCandidateValidationError(rule, message);
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

function scoreLikeKey(key) {
  const normalized = key.replace(/[_-]/gu, '').toLowerCase();
  return normalized.includes('score') || normalized === 'scores'
    || normalized.includes('confirmedversionid');
}

function scanPayloadData(value, rule, path = 'payload') {
  if (Array.isArray(value)) {
    for (const item of value) scanPayloadData(item, rule, path);
    return;
  }
  if (!isRecord(value)) return;
  for (const [key, nested] of Object.entries(value)) {
    if (scoreLikeKey(key)) fail(rule, `${path}.${key} e proibido`);
    scanPayloadData(nested, rule, `${path}.${key}`);
  }
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
    number(value, rule) {
      this.require(typeof value === 'number' && Number.isFinite(value), rule, 'numero finito obrigatorio');
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
  ctx.require(root.schemaVersion === '04-15.source-authority-candidate.v1', 'SCHEMA_VERSION', 'schemaVersion inesperado');
  ctx.require(root.phase === '04-mercados-pr-jogo-e-odds-transparentes', 'PHASE', 'fase inesperada');
  ctx.require(root.plan === '04-15', 'PLAN', 'plano inesperado');
  ctx.require(root.kind === 'source_authority_candidate', 'KIND', 'kind inesperado');
  ctx.boolean(root.runtimeConsumable, false, 'RUNTIME_CONSUMABLE');
  ctx.require(root.purpose === PURPOSE, 'PURPOSE', 'purpose deve preservar a fronteira documental nao-runtime');
  ctx.require(
    root.decisionStatus === PENDING_DECISION_STATUS
      || root.decisionStatus === APPROVED_DECISION_STATUS,
    'DECISION_STATUS',
    'decisionStatus deve permanecer pendente ou aprovado pelo checkpoint 04-15/T2',
  );
  ctx.array(root.openQuestions, 'OPEN_QUESTIONS');
  ctx.arrayExact(root.openQuestions, [
    'qual envelope e quais nomes fisicos serao ratificados',
    'qual estrategia de propagacao sera adotada',
    'como confirmed_version_id sera projetado apos correcao cross-kind administrativa',
    'qual politica de recuperacao reconstruira o head',
    'qual publicationId/versionNo/known_at de roster sera congelado por participante',
    'qual efeito uma correcao de roster tera sobre mercado e odds',
    'qual threshold de experiencia troca K=32 por K=16',
    'quais parametros Davidson, bandas de confianca, recencia e arredondamento passarao no backtest',
  ], 'OPEN_QUESTIONS_CONTENT');
  return root;
}

function validateDecisionBasis(ctx, value) {
  const basis = ctx.record(value, 'DECISION_BASIS_SHAPE');
  ctx.keys(basis, EXPECTED.decisionBasisKeys, 'DECISION_BASIS_KEYS');
  ctx.require(basis.selectedOption === 'A', 'DECISION_OPTION', 'somente a opcao A foi escolhida');
  ctx.require(
    basis.selectedOptionDescription === 'Fatos administrativos separados e um unico head corrente por evento.',
    'DECISION_OPTION_DESCRIPTION',
  );
  ctx.arraySet(basis.alreadyChosen, [
    'uma autoridade de outcome por evento',
    'fatos append-only',
    'correcao cria nova linhagem',
    'lifecycle permanece separado no contrato V24',
  ], 'DECISION_ALREADY_CHOSEN');
  ctx.arraySet(basis.ratifiedAtCheckpoint, [
    'autoridade sports_outcome_authority com um head corrente por evento',
    'taxonomia de cinco classes com lifecycle V24 separado',
    'sport_time imutavel no fato e replay as-of por historia',
    'legado sem classificacao silenciosa e com quarentena',
    'workflow administrativo server-side sem placar',
    'correcao cross-kind como nova linhagem sem reescrever odds aceitas',
    'confronto direto apenas como evidencia contextual, sem bonus automatico',
  ], 'DECISION_RATIFIED_CHECKPOINT');
  ctx.arraySet(basis.pendingRatification, [
    'envelope e nomes fisicos',
    'constraints e triggers',
    'politica de legado',
    'workflow administrativo detalhado',
    'propagacao e recuperacao',
    'binding e atomicidade de roster',
    'ordem e nomes literais de migrations',
  ], 'DECISION_PENDING_RATIFICATION');
}

function validateAuthority(ctx, value) {
  const authority = ctx.record(value, 'AUTHORITY_SHAPE');
  ctx.keys(authority, EXPECTED.authorityKeys, 'AUTHORITY_KEYS');
  ctx.require(authority.sourceOfTruth === 'sports_outcome_authority', 'AUTHORITY_SOURCE', 'fonte candidata inesperada');
  ctx.require(authority.ownerModule === 'sports', 'AUTHORITY_OWNER', 'sports deve possuir a fonte');
  ctx.require(authority.consumerModule === 'odds', 'AUTHORITY_CONSUMER', 'odds deve ser consumidor');
  ctx.require(authority.eventHeadCardinality === 'at_most_one_current_head_per_event', 'HEAD_CARDINALITY', 'cardinalidade de head inesperada');
  ctx.require(authority.factsStorage === 'append_only', 'FACTS_APPEND_ONLY', 'fatos devem ser append-only');
  ctx.require(authority.supersession === 'new_fact_references_predecessor', 'SUPERSESSION', 'correcao deve criar linhagem');
  ctx.require(authority.headRole === 'current_read_projection_only', 'HEAD_ROLE', 'head nao pode ser segunda historia');
  ctx.require(authority.headAndFactAtomicity === 'required_transaction_and_compare_and_set', 'HEAD_ATOMICITY', 'CAS/transacao obrigatorios');
  ctx.require(authority.lifecycleOwner === 'sports_event_lifecycle_history_v24', 'LIFECYCLE_OWNER', 'V24 deve continuar proprietario do lifecycle');
  ctx.boolean(authority.oddsOwnsCopyOfScore, false, 'ODDS_SCORE_COPY');
  ctx.boolean(authority.secondAuthority, false, 'SECOND_AUTHORITY');
}

function validateTaxonomy(ctx, value) {
  const taxonomy = ctx.record(value, 'TAXONOMY_SHAPE');
  ctx.keys(taxonomy, EXPECTED.taxonomyKeys, 'TAXONOMY_KEYS');
  ctx.arraySet(taxonomy.classes, TAXONOMY_CLASSES, 'TAXONOMY_CLASSES');
  ctx.arraySet(taxonomy.outcomeClasses, OUTCOME_CLASSES, 'TAXONOMY_OUTCOME_PARTITION');
  ctx.arraySet(taxonomy.lifecycleClasses, LIFECYCLE_CLASSES, 'TAXONOMY_LIFECYCLE_PARTITION');
  ctx.require(
    taxonomy.outcomeClasses.every((kind) => !taxonomy.lifecycleClasses.includes(kind)),
    'TAXONOMY_DISJOINT',
    'outcome e lifecycle devem ser disjuntos',
  );
  const definitions = ctx.record(taxonomy.definitions, 'TAXONOMY_DEFINITIONS_SHAPE');
  ctx.keys(definitions, TAXONOMY_CLASSES, 'TAXONOMY_DEFINITION_CLASSES');
  const sporting = ctx.record(definitions.SPORTING_RESULT, 'SPORTING_DEFINITION_SHAPE');
  ctx.keys(sporting, ['meaning', 'feedsElo', 'hasSportsScore', 'requiresV16Reference'], 'SPORTING_DEFINITION_KEYS');
  ctx.require(sporting.meaning === 'Resultado esportivo confirmado e vigente na cadeia V16.', 'SPORTING_MEANING');
  ctx.boolean(sporting.feedsElo, true, 'SPORTING_FEEDS_ELO');
  ctx.boolean(sporting.hasSportsScore, true, 'SPORTING_HAS_SCORE');
  ctx.boolean(sporting.requiresV16Reference, true, 'SPORTING_V16_REQUIRED');

  for (const kind of ['ADMINISTRATIVE_WALKOVER', 'ABANDONED_NO_VALID_RESULT']) {
    const definition = ctx.record(definitions[kind], `${kind}_DEFINITION_SHAPE`);
    ctx.keys(definition, ['meaning', 'feedsElo', 'hasSportsScore', 'winnerMayBeRecorded'], `${kind}_DEFINITION_KEYS`);
    const expectedMeaning = kind === 'ADMINISTRATIVE_WALKOVER'
      ? 'Vitoria administrativa sem resultado esportivo.'
      : 'Abandono sem resultado esportivo valido.';
    ctx.require(definition.meaning === expectedMeaning, `${kind}_MEANING`);
    ctx.boolean(definition.feedsElo, false, `${kind}_FEEDS_ELO`);
    ctx.boolean(definition.hasSportsScore, false, `${kind}_HAS_SCORE`);
  }
  ctx.boolean(definitions.ADMINISTRATIVE_WALKOVER.winnerMayBeRecorded, true, 'ADMIN_WINNER_OPTIONAL');
  ctx.boolean(definitions.ABANDONED_NO_VALID_RESULT.winnerMayBeRecorded, false, 'ABANDONED_WINNER_FORBIDDEN');

  for (const [kind, status, rule] of [
    ['CANCELLED', 'CANCELADO', 'CANCELLED_DEFINITION'],
    ['POSTPONED', 'ADIADO', 'POSTPONED_DEFINITION'],
  ]) {
    const definition = ctx.record(definitions[kind], `${rule}_SHAPE`);
    ctx.keys(definition, ['meaning', 'feedsElo', 'hasOutcomePayload', 'lifecycleStatus'], `${rule}_KEYS`);
    const expectedMeaning = kind === 'CANCELLED'
      ? 'Fato de lifecycle V24 com status CANCELADO, nao payload de outcome.'
      : 'Fato de lifecycle V24 com status ADIADO, nao payload de outcome.';
    ctx.require(definition.meaning === expectedMeaning, `${rule}_MEANING`);
    ctx.boolean(definition.feedsElo, false, `${rule}_FEEDS_ELO`);
    ctx.boolean(definition.hasOutcomePayload, false, `${rule}_OUTCOME_PAYLOAD`);
    ctx.require(definition.lifecycleStatus === status, `${rule}_STATUS`, 'status V24 inesperado');
  }
  const statuses = ctx.record(taxonomy.resultVersionStatuses, 'RESULT_STATUS_SHAPE');
  ctx.keys(statuses, ['admissibleAtReplay', 'notAdmissibleAtReplay'], 'RESULT_STATUS_KEYS');
  ctx.arraySet(statuses.admissibleAtReplay, RESULT_STATUSES, 'RESULT_STATUS_ADMISSIBLE');
  ctx.arraySet(statuses.notAdmissibleAtReplay, NON_RESULT_STATUSES, 'RESULT_STATUS_NON_ADMISSIBLE');
  ctx.arraySet(taxonomy.lifecycleV24Statuses, LIFECYCLE_STATUSES, 'LIFECYCLE_STATUS_ALLOWLIST');
  ctx.arraySet(taxonomy.separationRules, [
    'CANCELLED e POSTPONED nao podem aparecer em payload de resultado.',
    'ADMINISTRATIVE_WALKOVER e ABANDONED_NO_VALID_RESULT nao carregam placar.',
    'placar numerico nao classifica W.O. ou abandono.',
  ], 'TAXONOMY_SEPARATION_RULES');
}

function validateEnvelope(ctx, value) {
  const envelope = ctx.record(value, 'ENVELOPE_SHAPE');
  ctx.keys(envelope, EXPECTED.envelopeKeys, 'ENVELOPE_KEYS');
  ctx.require(envelope.physicalName === 'pending_human_ratification', 'ENVELOPE_PHYSICAL_NAME', 'nome fisico continua candidato');
  ctx.require(envelope.unknownFields === 'reject', 'ENVELOPE_UNKNOWN_FIELDS', 'campos desconhecidos devem falhar');
  ctx.require(envelope.payloadDiscriminator === 'outcomeKind', 'ENVELOPE_DISCRIMINATOR', 'discriminador inesperado');
  ctx.boolean(envelope.exclusivePayloads, true, 'ENVELOPE_EXCLUSIVE_PAYLOADS');
  ctx.arraySet(envelope.requiredIdentity, ['factId', 'eventId', 'competitionId', 'editionId'], 'ENVELOPE_IDENTITY');
  ctx.arraySet(envelope.requiredLineage, ['versionNo', 'supersedesFactId', 'recordedAt', 'knownAt'], 'ENVELOPE_LINEAGE');
  ctx.arraySet(envelope.requiredSource, ['sourceReference', 'recordedByIdentityId'], 'ENVELOPE_SOURCE');
  ctx.boolean(envelope.eventScopeIsComposite, true, 'ENVELOPE_COMPOSITE_SCOPE');
  ctx.boolean(envelope.candidateNamesRemainOpen, true, 'ENVELOPE_NAMES_OPEN');
}

function validatePayloads(ctx, value) {
  const payloads = ctx.record(value, 'PAYLOADS_SHAPE');
  ctx.keys(payloads, TAXONOMY_CLASSES, 'PAYLOAD_CLASSES');
  const sporting = ctx.record(payloads.SPORTING_RESULT, 'SPORTING_PAYLOAD_SHAPE');
  ctx.keys(sporting, EXPECTED.sportingPayloadKeys, 'SPORTING_PAYLOAD_KEYS');
  ctx.arraySet(sporting.requiredFields, [
    'outcomeKind', 'confirmedVersionId', 'confirmedVersionStatus', 'sportTime',
    'knownAt', 'confirmedAt',
  ], 'SPORTING_REQUIRED_FIELDS');
  const v16 = ctx.record(sporting.v16Reference, 'V16_REFERENCE_SHAPE');
  ctx.keys(v16, EXPECTED.v16ReferenceKeys, 'V16_REFERENCE_KEYS');
  ctx.require(v16.table === 'sports_result_version', 'V16_TABLE');
  ctx.require(v16.versionIdField === 'confirmedVersionId', 'V16_ID_FIELD');
  ctx.boolean(v16.exactReference, true, 'V16_EXACT_REFERENCE');
  ctx.arraySet(v16.statusAllowlist, RESULT_STATUSES, 'V16_STATUS_ALLOWLIST');
  ctx.require(v16.scoreTable === 'sports_result_version_slot', 'V16_SCORE_TABLE');
  ctx.require(v16.slotCount === 2, 'V16_SLOT_COUNT');
  ctx.arrayExact(v16.slotNumbers, [1, 2], 'V16_SLOT_NUMBERS');
  ctx.boolean(v16.readOriginalScores, true, 'V16_READ_ORIGINAL_SCORES');
  ctx.boolean(v16.copyScoresIntoAuthority, false, 'V16_COPY_SCORES');
  ctx.boolean(v16.synthesizeScores, false, 'V16_SYNTHESIZE_SCORES');
  const scorePolicy = ctx.record(sporting.scorePolicy, 'SPORTING_SCORE_POLICY_SHAPE');
  ctx.keys(scorePolicy, EXPECTED.scorePolicyKeys, 'SPORTING_SCORE_POLICY_KEYS');
  ctx.require(scorePolicy.source === 'exact V16 slots', 'SPORTING_SCORE_SOURCE');
  ctx.boolean(scorePolicy.originalOnly, true, 'SPORTING_SCORE_ORIGINAL');
  ctx.boolean(scorePolicy.nonNegative, true, 'SPORTING_SCORE_NON_NEGATIVE');
  ctx.boolean(scorePolicy.exactlyTwoForHeadToHead, true, 'SPORTING_SCORE_TWO_SLOTS');
  ctx.boolean(scorePolicy.administrativeScoreForbidden, true, 'SPORTING_SCORE_ADMIN_FORBIDDEN');
  ctx.require(
    sporting.regulationGate === 'draw eligibility is read from the versioned regulation at the as-of cut',
    'SPORTING_REGULATION_GATE',
  );
  ctx.require(sporting.correction === 'new fact supersedes the prior sporting fact', 'SPORTING_CORRECTION');

  for (const kind of ['ADMINISTRATIVE_WALKOVER', 'ABANDONED_NO_VALID_RESULT']) {
    const payload = ctx.record(payloads[kind], `${kind}_PAYLOAD_SHAPE`);
    const payloadKeys = kind === 'ADMINISTRATIVE_WALKOVER'
      ? EXPECTED.administrativePayloadKeys
      : EXPECTED.administrativePayloadKeys.filter((key) => key !== 'winner');
    ctx.keys(payload, payloadKeys, `${kind}_PAYLOAD_KEYS`);
    const required = kind === 'ADMINISTRATIVE_WALKOVER'
      ? ['outcomeKind', 'administrativeReason', 'sportTime', 'knownAt']
      : ['outcomeKind', 'abandonmentReason', 'sportTime', 'knownAt'];
    ctx.arraySet(payload.requiredFields, required, `${kind}_REQUIRED_FIELDS`);
    ctx.arraySet(payload.forbiddenFields, ADMIN_SCORE_FIELDS, `${kind}_SCORE_FORBIDDEN_FIELDS`);
    if (kind === 'ADMINISTRATIVE_WALKOVER') {
      ctx.require(
        payload.winner === 'optional canonical participant, never a sports score',
        `${kind}_WINNER_POLICY`,
      );
    }
    ctx.boolean(payload.feedsElo, false, `${kind}_FEEDS_ELO`);
    ctx.boolean(payload.feedsDavidson, false, `${kind}_FEEDS_DAVIDSON`);
    ctx.require(
      payload.marketEffect === 'candidate for separate void/settlement handling',
      `${kind}_MARKET_EFFECT`,
    );
    scanPayloadData(payload, `${kind}_SCORE_FORBIDDEN`);
  }
  for (const [kind, status, rule] of [
    ['CANCELLED', 'CANCELADO', 'CANCELLED_PAYLOAD'],
    ['POSTPONED', 'ADIADO', 'POSTPONED_PAYLOAD'],
  ]) {
    const payload = ctx.record(payloads[kind], `${rule}_SHAPE`);
    ctx.keys(payload, EXPECTED.lifecyclePayloadKeys, `${rule}_KEYS`);
    ctx.boolean(payload.payloadAllowed, false, `${rule}_ALLOWED`);
    ctx.require(payload.lifecycleTable === 'sports_event_lifecycle_history', `${rule}_TABLE`, 'lifecycle deve ser V24');
    ctx.require(payload.lifecycleStatus === status, `${rule}_STATUS`, 'status lifecycle inesperado');
  }
}

function validateHead(ctx, value) {
  const head = ctx.record(value, 'HEAD_SHAPE');
  ctx.keys(head, EXPECTED.headKeys, 'HEAD_KEYS');
  ctx.boolean(head.onePerEvent, true, 'HEAD_ONE_PER_EVENT');
  ctx.boolean(head.oneCurrentHeadWhenFactExists, true, 'HEAD_REQUIRED_WHEN_FACT_EXISTS');
  ctx.arrayExact(head.uniqueKeyCandidate, ['eventId'], 'HEAD_UNIQUE_KEY');
  ctx.boolean(head.mutableProjectionOnly, true, 'HEAD_PROJECTION_ONLY');
  ctx.boolean(head.historyIsAppendOnly, true, 'HEAD_HISTORY_APPEND_ONLY');
  ctx.boolean(head.updatedTogetherWithFact, true, 'HEAD_ATOMIC_UPDATE');
  ctx.boolean(head.compareAndSetRequired, true, 'HEAD_CAS');
  ctx.require(head.casInputCandidate === 'event.lock_version', 'HEAD_CAS_INPUT');
  const chain = ctx.record(head.supersessionChain, 'HEAD_CHAIN_SHAPE');
  ctx.keys(chain, [
    'singleRootPerEvent', 'oneSuccessorPerPredecessor', 'acyclic',
    'newFactMustSupersedeCurrentHead',
  ], 'HEAD_CHAIN_KEYS');
  ctx.boolean(chain.singleRootPerEvent, true, 'HEAD_CHAIN_SINGLE_ROOT');
  ctx.boolean(chain.oneSuccessorPerPredecessor, true, 'HEAD_CHAIN_NO_FORK');
  ctx.boolean(chain.acyclic, true, 'HEAD_CHAIN_ACYCLIC');
  ctx.boolean(chain.newFactMustSupersedeCurrentHead, true, 'HEAD_CHAIN_CAS_PREDECESSOR');
  ctx.boolean(head.headCannotRewriteHistory, true, 'HEAD_REWRITE_HISTORY');
  ctx.boolean(head.headCannotBeUsedForReplay, true, 'HEAD_REPLAY_SOURCE');
  ctx.boolean(head.maxVersionNoCannotBeUsedForReplay, true, 'HEAD_MAX_VERSION_REPLAY');
  ctx.boolean(head.futureEventNeedsNoHead, true, 'HEAD_FUTURE_EXCEPTION');
}

function validateTemporal(ctx, value) {
  const temporal = ctx.record(value, 'TEMPORAL_SHAPE');
  ctx.keys(temporal, EXPECTED.temporalKeys, 'TEMPORAL_KEYS');
  const fields = ctx.record(temporal.fields, 'TEMPORAL_FIELDS_SHAPE');
  ctx.keys(fields, ['sport_time', 'known_at', 'confirmed_at'], 'TEMPORAL_FIELD_NAMES');
  const sportTime = ctx.record(fields.sport_time, 'SPORT_TIME_SHAPE');
  ctx.keys(sportTime, EXPECTED.sportTimeFieldKeys, 'SPORT_TIME_KEYS');
  ctx.require(sportTime.meaning === 'instante esportivo canonico do evento', 'SPORT_TIME_MEANING');
  ctx.require(
    sportTime.candidateSource === 'sports_event.scheduled_at capturado na insercao do fato',
    'SPORT_TIME_SOURCE',
  );
  ctx.require(
    sportTime.timezoneSource === 'sports_event.zone_id capturado na insercao do fato',
    'SPORT_TIME_ZONE',
  );
  ctx.require(sportTime.factField === 'outcome_fact.sport_time', 'SPORT_TIME_FACT_FIELD');
  ctx.require(
    sportTime.editionProvenance === 'outcome_fact.competition_id e outcome_fact.edition_id',
    'SPORT_TIME_EDITION_PROVENANCE',
  );
  ctx.boolean(sportTime.snapshotAtFactCreation, true, 'SPORT_TIME_SNAPSHOT');
  ctx.boolean(sportTime.currentEventLookupForbidden, true, 'SPORT_TIME_CURRENT_EVENT_FORBIDDEN');
  ctx.boolean(sportTime.required, true, 'SPORT_TIME_REQUIRED');
  const knownAt = ctx.record(fields.known_at, 'KNOWN_AT_SHAPE');
  ctx.keys(knownAt, EXPECTED.knownAtFieldKeys, 'KNOWN_AT_KEYS');
  ctx.require(knownAt.meaning === 'instante em que o fato ficou conhecido pelo sistema/modelo', 'KNOWN_AT_MEANING');
  ctx.require(knownAt.source === 'outcome fact audit timestamp', 'KNOWN_AT_SOURCE');
  ctx.boolean(knownAt.required, true, 'KNOWN_AT_REQUIRED');
  ctx.boolean(knownAt.distinctFromCapture, true, 'KNOWN_AT_CAPTURE_DISTINCT');
  const confirmedAt = ctx.record(fields.confirmed_at, 'CONFIRMED_AT_SHAPE');
  ctx.keys(confirmedAt, EXPECTED.confirmedAtFieldKeys, 'CONFIRMED_AT_KEYS');
  ctx.require(confirmedAt.meaning === 'instante de confirmacao exata da versao V16', 'CONFIRMED_AT_MEANING');
  ctx.require(confirmedAt.source === 'sports_result_version.confirmed_at', 'CONFIRMED_AT_SOURCE');
  ctx.boolean(confirmedAt.requiredForSportingResult, true, 'CONFIRMED_AT_REQUIRED');
  ctx.boolean(confirmedAt.distinctFromKnownAt, true, 'CONFIRMED_AT_KNOWN_DISTINCT');
  ctx.require(temporal.cutoffRule === 'known_at <= cutoff is required for an as-of replay', 'TEMPORAL_CUTOFF');
  ctx.arrayExact(temporal.ordering, ['sport_time ASC', 'known_at ASC', 'stableFactId ASC'], 'TEMPORAL_ORDER');
  ctx.require(temporal.tieBreak === 'stableFactId after sport_time and known_at', 'TEMPORAL_TIE_BREAK');
  ctx.require(
    temporal.correctionAfterCutoff === 'excluded from the earlier replay and retained for later cuts',
    'TEMPORAL_CORRECTION_CUTOFF',
  );
  ctx.boolean(temporal.snapshotCaptureIsNotKnownAt, true, 'TEMPORAL_CAPTURE_NOT_KNOWN');
  ctx.require(temporal.missingTimeDisposition === 'unresolved quarantine', 'TEMPORAL_MISSING_TIME');
  ctx.boolean(temporal.deterministic, true, 'TEMPORAL_DETERMINISTIC');
}

function validateReplay(ctx, value) {
  const replay = ctx.record(value, 'REPLAY_SHAPE');
  ctx.keys(replay, EXPECTED.replayKeys, 'REPLAY_KEYS');
  ctx.boolean(replay.historyRequired, true, 'REPLAY_HISTORY_REQUIRED');
  ctx.require(replay.algorithm === REPLAY_ALGORITHM, 'REPLAY_ALGORITHM', 'algoritmo temporal divergente');
  ctx.arrayExact(replay.steps, [
    'load append-only facts for the event',
    'discard facts with known_at after the cutoff',
    'resolve supersession chains without using the current head',
    'reject forked or cyclic supersession chains',
    'retain only CONFIRMADO or CORRIGIDO V16 references for SPORTING_RESULT',
    'apply regulation version valid at the cut',
    'sort by sport_time, known_at and stableFactId',
    'emit a replay digest and lineage',
  ], 'REPLAY_STEPS');
  ctx.arraySet(replay.forbiddenShortcuts, [
    'current head',
    'MAX(version_no)',
    'capture timestamp as known_at',
    'latest row without an as-of cutoff',
  ], 'REPLAY_FORBIDDEN_SHORTCUTS');
  ctx.require(
    replay.leakageControl === 'a correction known after the cutoff cannot alter a prior replay',
    'REPLAY_LEAKAGE_CONTROL',
  );
  ctx.require(
    replay.reproducibility === 'same facts, regulation versions and cutoff produce the same order',
    'REPLAY_REPRODUCIBILITY',
  );
}

function validateElo(ctx, value) {
  const elo = ctx.record(value, 'ELO_SHAPE');
  ctx.keys(elo, EXPECTED.eloKeys, 'ELO_KEYS');
  ctx.require(elo.initialRating === 1500, 'ELO_INITIAL_RATING');
  ctx.arrayExact(elo.ratingScope, ['sport', 'category'], 'ELO_SCOPE');
  ctx.boolean(elo.globalRating, false, 'ELO_GLOBAL_RATING');
  const headToHead = ctx.record(elo.headToHeadContext, 'ELO_H2H_SHAPE');
  ctx.keys(headToHead, EXPECTED.headToHeadKeys, 'ELO_H2H_KEYS');
  ctx.boolean(headToHead.contextualEvidenceAllowed, true, 'ELO_H2H_CONTEXT_ALLOWED');
  ctx.boolean(headToHead.automaticBonus, false, 'ELO_H2H_NO_AUTOMATIC_BONUS');
  ctx.require(
    headToHead.promotionRequires === 'several editions and backtest demonstrating real gain',
    'ELO_H2H_PROMOTION_GATE',
  );
  const experience = ctx.record(elo.experiencePolicy, 'ELO_EXPERIENCE_SHAPE');
  ctx.keys(experience, EXPECTED.experienceKeys, 'ELO_EXPERIENCE_KEYS');
  ctx.require(experience.provisionalK === 32, 'ELO_PROVISIONAL_K');
  ctx.require(experience.matureK === 16, 'ELO_MATURE_K');
  ctx.require(experience.experienceThreshold === 'pending_human_ratification_by_backtest', 'ELO_EXPERIENCE_PENDING');
  ctx.boolean(experience.thresholdIsNotImplicit, true, 'ELO_THRESHOLD_EXPLICIT');
  const regression = ctx.record(elo.editionRegression, 'ELO_REGRESSION_SHAPE');
  ctx.keys(regression, EXPECTED.regressionKeys, 'ELO_REGRESSION_KEYS');
  ctx.require(regression.fractionTowardInitial === 0.5, 'ELO_REGRESSION_FRACTION');
  ctx.require(regression.initialRating === 1500, 'ELO_REGRESSION_INITIAL');
  ctx.boolean(regression.versioned, true, 'ELO_REGRESSION_VERSIONED');
  ctx.boolean(elo.chronologicalProcessing, true, 'ELO_CHRONOLOGICAL');
  const recency = ctx.record(elo.recency, 'ELO_RECENCY_SHAPE');
  ctx.keys(recency, EXPECTED.recencyKeys, 'ELO_RECENCY_KEYS');
  ctx.require(recency.formula === 'pending_human_ratification_by_temporal_backtest', 'ELO_RECENCY_PENDING');
  ctx.require(recency.monthlyHalfLife === null, 'ELO_RECENCY_NUMERIC_PENDING');
  ctx.boolean(recency.arbitraryHalfLifeForbidden, true, 'ELO_RECENCY_ARBITRARY_FORBIDDEN');
  const draw = ctx.record(elo.draw, 'ELO_DRAW_SHAPE');
  ctx.keys(draw, EXPECTED.drawKeys, 'ELO_DRAW_KEYS');
  ctx.boolean(draw.conditionalOnRegulation, true, 'ELO_DRAW_REGULATION');
  ctx.boolean(draw.twoOutcomeWhenDrawImpossible, true, 'ELO_DRAW_TWO_OUTCOME');
  ctx.boolean(draw.threeOutcomeWhenRegulationAllows, true, 'ELO_DRAW_THREE_OUTCOME');
  const davidson = ctx.record(draw.davidson, 'ELO_DAVIDSON_SHAPE');
  ctx.keys(davidson, EXPECTED.davidsonKeys, 'ELO_DAVIDSON_KEYS');
  ctx.boolean(davidson.enabledOnlyForThreeOutcomeBranch, true, 'ELO_DAVIDSON_BRANCH');
  ctx.require(davidson.parameters === 'pending_human_ratification_by_backtest', 'ELO_DAVIDSON_PENDING');
  ctx.boolean(draw.sharedConservativePrior, true, 'ELO_SHARED_PRIOR');
  ctx.require(draw.specializationThreshold === 'pending_human_ratification', 'ELO_SPECIALIZATION_PENDING');
  const lowSample = ctx.record(elo.lowSample, 'ELO_LOW_SAMPLE_SHAPE');
  ctx.keys(lowSample, EXPECTED.lowSampleKeys, 'ELO_LOW_SAMPLE_KEYS');
  ctx.require(lowSample.estimate === 'neutral', 'ELO_LOW_SAMPLE_ESTIMATE');
  ctx.require(lowSample.publicConfidenceLabel === 'low_confidence', 'ELO_LOW_SAMPLE_LABEL');
  ctx.require(lowSample.confidenceBands === 'pending_human_ratification', 'ELO_CONFIDENCE_PENDING');
  const eligible = ctx.record(elo.eligibleOutcome, 'ELO_ELIGIBLE_SHAPE');
  ctx.keys(eligible, EXPECTED.eligibleOutcomeKeys, 'ELO_ELIGIBLE_KEYS');
  ctx.arrayExact(eligible.kinds, ['SPORTING_RESULT'], 'ELO_ELIGIBLE_KINDS');
  ctx.arraySet(eligible.v16Statuses, RESULT_STATUSES, 'ELO_ELIGIBLE_STATUSES');
  for (const [key, rule] of [
    ['administrativeWalkover', 'ELO_ADMIN_EXCLUDED'],
    ['abandonment', 'ELO_ABANDONMENT_EXCLUDED'],
    ['cancelled', 'ELO_CANCELLED_EXCLUDED'],
    ['postponed', 'ELO_POSTPONED_EXCLUDED'],
  ]) ctx.boolean(eligible[key], false, rule);
  ctx.boolean(eligible.futureEventNeedsNoOutcome, true, 'ELO_FUTURE_NO_OUTCOME');
  ctx.arraySet(elo.forbiddenInputs, [
    'betting_volume', 'betting_distribution', 'liquidity', 'exposure', 'user_behavior',
  ], 'ELO_FORBIDDEN_INPUTS');
  const margin = ctx.record(elo.margin, 'ELO_MARGIN_SHAPE');
  ctx.keys(margin, EXPECTED.marginKeys, 'ELO_MARGIN_KEYS');
  ctx.require(margin.initial === 0.05, 'ELO_MARGIN_INITIAL');
  ctx.boolean(margin.configurable, true, 'ELO_MARGIN_CONFIGURABLE');
  ctx.boolean(margin.versioned, true, 'ELO_MARGIN_VERSIONED');
  ctx.require(margin.formula === 'pending_human_ratification', 'ELO_MARGIN_PENDING');
  const publication = ctx.record(elo.publication, 'ELO_PUBLICATION_SHAPE');
  ctx.keys(publication, EXPECTED.publicationKeys, 'ELO_PUBLICATION_KEYS');
  ctx.boolean(publication.fairProbabilitySeparate, true, 'ELO_FAIR_PROBABILITY');
  ctx.boolean(publication.publishedPriceImmutable, true, 'ELO_PRICE_IMMUTABLE');
  ctx.require(publication.decimalPlaces === 2, 'ELO_DECIMAL_PLACES');
  ctx.require(publication.roundingMode === 'pending_human_ratification', 'ELO_ROUNDING_PENDING');
}

function validateLegacy(ctx, value) {
  const legacy = ctx.record(value, 'LEGACY_SHAPE');
  ctx.keys(legacy, EXPECTED.legacyKeys, 'LEGACY_KEYS');
  ctx.require(legacy.backfillMode === 'evidence_only', 'LEGACY_EVIDENCE_ONLY');
  ctx.arraySet(legacy.authoritativeEvidence, [
    'persisted canonical foreign key',
    'verified V16 confirmed-version chain',
    'authoritative source reference with audit trail',
    'versioned lifecycle evidence',
  ], 'LEGACY_AUTHORITATIVE_EVIDENCE');
  ctx.arraySet(legacy.forbiddenEvidence, [
    'numeric score alone', 'team name alone', 'team slug alone', 'row order',
    'latest version alone', 'capture timestamp as known_at',
  ], 'LEGACY_FORBIDDEN_EVIDENCE');
  ctx.require(
    legacy.numericWalkoverDisposition === 'quarantine with score-for-model null',
    'LEGACY_NUMERIC_WALKOVER',
  );
  ctx.require(legacy.unresolvedDisposition === 'quarantine', 'LEGACY_UNRESOLVED');
  ctx.arraySet(legacy.requiredBeforeClassification, [
    'canonical identity', 'sport_time', 'known_at',
    'confirmed V16 version chain', 'authoritative outcome kind',
  ], 'LEGACY_REQUIRED_FIELDS');
  ctx.arraySet(legacy.classificationStates, ['classified', 'unresolved', 'quarantined'], 'LEGACY_STATES');
  ctx.boolean(legacy.noSilentClassification, true, 'LEGACY_NO_SILENT_CLASSIFICATION');
  ctx.arraySet(legacy.quarantineReleaseRequires, [
    'canonical_identity', 'known_at', 'confirmed_version_chain',
    'authoritative_result_kind', 'independent_review',
  ], 'LEGACY_RELEASE_REQUIREMENTS');
}

function validateCompatibility(ctx, value) {
  const compatibility = ctx.record(value, 'COMPATIBILITY_SHAPE');
  ctx.keys(compatibility, EXPECTED.compatibilityKeys, 'COMPATIBILITY_KEYS');
  const pointer = ctx.record(compatibility.confirmedVersionId, 'COMPATIBILITY_POINTER_SHAPE');
  ctx.keys(pointer, EXPECTED.compatibilityPointerKeys, 'COMPATIBILITY_POINTER_KEYS');
  ctx.require(pointer.currentField === 'sports_event.confirmed_version_id', 'COMPATIBILITY_FIELD');
  ctx.require(pointer.role === 'temporary_compatibility_projection', 'COMPATIBILITY_ROLE');
  ctx.boolean(pointer.secondAuthority, false, 'COMPATIBILITY_SECOND_AUTHORITY');
  ctx.boolean(pointer.sportingResultOnly, true, 'COMPATIBILITY_SPORTING_ONLY');
  ctx.arrayExact(pointer.readersToReconcile, [
    'SportsResultRepository.findConfirmedResultVersion',
    'sports public result projection',
  ], 'COMPATIBILITY_READERS');
  ctx.arrayExact(pointer.writersToReconcile, [
    'SportsResultService.confirmResult',
    'SportsResultService.correctResult',
  ], 'COMPATIBILITY_WRITERS');
  ctx.require(
    pointer.administrativeCorrectionPolicy === 'pending_human_ratification: atomically clear or project non-sporting state before runtime',
    'COMPATIBILITY_ADMIN_PENDING',
  );
  ctx.boolean(pointer.reconciliationRequiredBeforeRuntime, true, 'COMPATIBILITY_RECONCILIATION');
  const v16 = ctx.record(compatibility.v16, 'COMPATIBILITY_V16_SHAPE');
  ctx.keys(v16, EXPECTED.compatibilityV16Keys, 'COMPATIBILITY_V16_KEYS');
  ctx.require(v16.table === 'sports_result_version', 'COMPATIBILITY_V16_TABLE');
  ctx.boolean(v16.scoresRemainOwnedBySports, true, 'COMPATIBILITY_V16_SCORES_OWNER');
  ctx.boolean(v16.rowsRemainImmutable, true, 'COMPATIBILITY_V16_IMMUTABLE');
  ctx.boolean(v16.mustRemainByteIdentical, true, 'COMPATIBILITY_V16_BYTE_IDENTITY');
  const v24 = ctx.record(compatibility.v24, 'COMPATIBILITY_V24_SHAPE');
  ctx.keys(v24, EXPECTED.compatibilityV24Keys, 'COMPATIBILITY_V24_KEYS');
  ctx.require(v24.table === 'sports_event_lifecycle_history', 'COMPATIBILITY_V24_TABLE');
  ctx.boolean(v24.lifecycleRemainsSeparate, true, 'COMPATIBILITY_V24_LIFECYCLE');
  ctx.boolean(v24.rowsRemainImmutable, true, 'COMPATIBILITY_V24_IMMUTABLE');
  ctx.boolean(v24.mustRemainByteIdentical, true, 'COMPATIBILITY_V24_BYTE_IDENTITY');
  const crossKind = ctx.record(compatibility.crossKindCorrection, 'COMPATIBILITY_CROSS_KIND_SHAPE');
  ctx.keys(crossKind, EXPECTED.crossKindKeys, 'COMPATIBILITY_CROSS_KIND_KEYS');
  for (const [key, rule] of [
    ['createsNewFact', 'COMPATIBILITY_NEW_FACT'],
    ['linksSupersededFact', 'COMPATIBILITY_LINKS_PREDECESSOR'],
    ['atomicHeadTransition', 'COMPATIBILITY_ATOMIC_HEAD'],
    ['oldFactRemainsReadable', 'COMPATIBILITY_OLD_FACT'],
    ['acceptedOddsAreNotRewritten', 'COMPATIBILITY_ODDS_IMMUTABLE'],
  ]) ctx.boolean(crossKind[key], true, rule);
  ctx.require(crossKind.policy === 'pending_human_ratification', 'COMPATIBILITY_CROSS_KIND_PENDING');
}

function validateWorkflow(ctx, value) {
  const workflow = ctx.record(value, 'WORKFLOW_SHAPE');
  ctx.keys(workflow, EXPECTED.workflowKeys, 'WORKFLOW_KEYS');
  ctx.boolean(workflow.serverSideOnly, true, 'WORKFLOW_SERVER_SIDE');
  ctx.arrayExact(workflow.operations, ['INFORM', 'CONFIRM', 'CORRECT'], 'WORKFLOW_OPERATIONS');
  ctx.boolean(workflow.scopeDerivedFromPersistedEvent, true, 'WORKFLOW_SCOPE');
  ctx.boolean(workflow.sourceReferenceRequired, true, 'WORKFLOW_SOURCE');
  ctx.boolean(workflow.correctionReasonRequired, true, 'WORKFLOW_CORRECTION_REASON');
  ctx.boolean(workflow.bulkImport, false, 'WORKFLOW_BULK_IMPORT');
  ctx.boolean(workflow.newUi, false, 'WORKFLOW_NEW_UI');
  ctx.boolean(workflow.clientAuthority, false, 'WORKFLOW_CLIENT_AUTHORITY');
  ctx.require(
    workflow.transaction === 'lock event, compare expected version, insert fact, transition head and audit atomically',
    'WORKFLOW_ATOMIC_TRANSACTION',
  );
  ctx.boolean(workflow.auditRequired, true, 'WORKFLOW_AUDIT');
  ctx.require(workflow.idempotency === 'pending_human_ratification', 'WORKFLOW_IDEMPOTENCY_PENDING');
  ctx.require(workflow.rolesAndCapabilities === 'pending_gate_04-18', 'WORKFLOW_ROLES_PENDING');
  ctx.require(workflow.rolesAndCapabilities === 'pending_gate_04-18', 'WORKFLOW_ROLES_GATE');
  ctx.boolean(workflow.administrativePayloadHasNoScore, true, 'WORKFLOW_ADMIN_NO_SCORE');
}

function validateRegulation(ctx, value) {
  const regulation = ctx.record(value, 'REGULATION_SHAPE');
  ctx.keys(regulation, EXPECTED.regulationKeys, 'REGULATION_KEYS');
  ctx.boolean(regulation.versioned, true, 'REGULATION_VERSIONED');
  ctx.require(regulation.source === 'authoritative competition or edition regulation', 'REGULATION_SOURCE');
  ctx.require(regulation.drawEligibility === 'regulation version valid for the event and as-of cut', 'REGULATION_DRAW_ELIGIBILITY');
  ctx.require(regulation.drawEligibility === 'regulation version valid for the event and as-of cut', 'REGULATION_AS_OF');
  ctx.require(regulation.missingRegulation === 'pending_human_ratification', 'REGULATION_MISSING_PENDING_VALUE');
  ctx.boolean(regulation.noImplicitDefault, true, 'REGULATION_NO_DEFAULT');
}

function validatePropagation(ctx, value) {
  const propagation = ctx.record(value, 'PROPAGATION_SHAPE');
  ctx.keys(propagation, EXPECTED.propagationKeys, 'PROPAGATION_KEYS');
  ctx.require(propagation.strategy === 'pending_human_ratification', 'PROPAGATION_STRATEGY_PENDING');
  ctx.arraySet(propagation.candidateStrategies, [
    'approved synchronous read port',
    'opaque after-commit application event',
    'versioned odds projection',
  ], 'PROPAGATION_CANDIDATES');
  ctx.arraySet(propagation.mustPreserve, [
    'sports owns the source',
    'odds cannot import sports.internal',
    'correction creates a new lineage',
    'accepted odds remain immutable',
  ], 'PROPAGATION_PRESERVATION');
  ctx.boolean(propagation.correctionTriggersNewLineage, true, 'PROPAGATION_NEW_LINEAGE');
  ctx.boolean(propagation.noUnapprovedSynchronousAssumption, true, 'PROPAGATION_NO_ASSUMPTION');
}

function validateRecovery(ctx, value) {
  const recovery = ctx.record(value, 'RECOVERY_SHAPE');
  ctx.keys(recovery, EXPECTED.recoveryKeys, 'RECOVERY_KEYS');
  ctx.boolean(recovery.appendOnly, true, 'RECOVERY_APPEND_ONLY');
  ctx.boolean(recovery.replayFromHistory, true, 'RECOVERY_HISTORY');
  ctx.require(recovery.headRebuild === 'pending_human_ratification', 'RECOVERY_HEAD_REBUILD_PENDING');
  ctx.require(
    recovery.partialFailure === 'transaction rollback plus an audited reconciliation path',
    'RECOVERY_PARTIAL_FAILURE',
  );
  ctx.require(recovery.manualRepair === 'new audited fact only', 'RECOVERY_MANUAL_REPAIR');
  ctx.boolean(recovery.deleteHistory, false, 'RECOVERY_DELETE_HISTORY');
  ctx.boolean(recovery.useCurrentHeadAsHistory, false, 'RECOVERY_HEAD_AS_HISTORY');
}

function validateRoster(ctx, value) {
  const roster = ctx.record(value, 'ROSTER_SHAPE');
  ctx.keys(roster, EXPECTED.rosterKeys, 'ROSTER_KEYS');
  ctx.boolean(roster.marketOpenRequiresPublishedRoster, true, 'ROSTER_MARKET_OPEN');
  ctx.require(roster.requiredStatus === 'PUBLICADO', 'ROSTER_STATUS');
  ctx.require(roster.bindingMode === 'candidate_only', 'ROSTER_BINDING_MODE');
  for (const [key, rule] of [
    ['perParticipantPublicationId', 'ROSTER_PUBLICATION_ID_PENDING'],
    ['perParticipantVersionNo', 'ROSTER_VERSION_PENDING'],
    ['perParticipantKnownAt', 'ROSTER_KNOWN_AT_PENDING'],
    ['correctionEffect', 'ROSTER_CORRECTION_PENDING'],
    ['freezeAndAtomicity', 'ROSTER_FREEZE_PENDING'],
  ]) {
    ctx.require(roster[key] === 'pending_gate_04-19-T1', rule);
  }
  ctx.boolean(roster.latestImplicitSelection, false, 'ROSTER_NO_LATEST');
  ctx.boolean(roster.lineupPerMatch, false, 'ROSTER_NO_LINEUP');
  ctx.boolean(roster.piiInOddsContract, false, 'ROSTER_NO_PII');
  ctx.boolean(roster.correctionCreatesNewPublication, true, 'ROSTER_NEW_PUBLICATION');
}

function validateConstraints(ctx, value) {
  const constraints = ctx.record(value, 'CONSTRAINTS_SHAPE');
  ctx.keys(constraints, EXPECTED.constraintKeys, 'CONSTRAINTS_KEYS');
  ctx.require(constraints.database === 'PostgreSQL', 'CONSTRAINTS_DATABASE');
  ctx.boolean(constraints.checkCannotProveCrossTableCoherence, true, 'CONSTRAINTS_CROSS_TABLE_CHECK');
  ctx.arraySet(constraints.candidateForeignKeys, [
    'fact(event_id, competition_id, edition_id) -> sports_event composite scope',
    'sporting fact(event_id, confirmed_version_id) -> sports_result_version exact event/version',
    'supersedes(event_id, supersedes_fact_id) -> prior fact of the same event',
    'head(event_id) -> sports_event exact event',
  ], 'CONSTRAINTS_FOREIGN_KEYS');
  ctx.arraySet(constraints.candidateUniqueConstraints, [
    'one head row per event',
    'one fact version number per event',
    'stable fact identifier per event',
    'one supersession root per event',
    'one successor per predecessor',
  ], 'CONSTRAINTS_UNIQUE_KEYS');
  ctx.arraySet(constraints.deferredConstraintTriggers, [
    'exactly one exclusive payload subtype per fact',
    'payload subtype belongs to the envelope event',
    'SPORTING_RESULT has exactly two H2H score references',
    'ADMINISTRATIVE_WALKOVER and ABANDONED_NO_VALID_RESULT have no score columns',
    'head transition and fact insertion are coherent at transaction end',
  ], 'CONSTRAINTS_DEFERRED_TRIGGERS');
  ctx.arraySet(constraints.databaseTriggers, [
    'append-only fact immutability',
    'head compare-and-set or lock-version protection',
    'no self-supersession',
    'no supersession forks or cycles',
  ], 'CONSTRAINTS_DATABASE_TRIGGERS');
  ctx.require(constraints.constraintStatus === 'candidate_pending_human_ratification', 'CONSTRAINTS_PENDING');
  ctx.boolean(constraints.noCheckAcrossTablesPretended, true, 'CONSTRAINTS_NO_FAKE_CHECK');
}

function validateMigrations(ctx, value) {
  const migrations = ctx.record(value, 'MIGRATIONS_SHAPE');
  ctx.keys(migrations, EXPECTED.migrationKeys, 'MIGRATIONS_KEYS');
  ctx.require(migrations.status === 'pending_human_ratification', 'MIGRATIONS_PENDING');
  ctx.boolean(migrations.numbersFixed, false, 'MIGRATIONS_NUMBERS_OPEN');
  ctx.boolean(migrations.doesNotInferFromV24, true, 'MIGRATIONS_NO_V24_INFERENCE');
  ctx.array(migrations.sequence, 'MIGRATIONS_SEQUENCE');
  ctx.require(migrations.sequence.length === MIGRATION_FILENAMES.length, 'MIGRATIONS_SEQUENCE_LENGTH', 'sequencia de migrations inesperada');
  migrations.sequence.forEach((entry, index) => {
    const item = ctx.record(entry, `MIGRATION_${index + 1}_SHAPE`);
    ctx.keys(item, ['order', 'filename', 'plan'], `MIGRATION_${index + 1}_KEYS`);
    ctx.require(item.order === index + 1, `MIGRATION_${index + 1}_ORDER`);
    ctx.require(item.filename === MIGRATION_FILENAMES[index], `MIGRATION_${index + 1}_FILENAME`);
    ctx.require(/^VNN_/u.test(item.filename) && !/^V\d+__/u.test(item.filename), `MIGRATION_${index + 1}_SYMBOLIC`);
    ctx.require(item.plan === ['04-09', '04-18', '04-16', '04-19'][index], `MIGRATION_${index + 1}_PLAN`);
  });
  ctx.boolean(migrations.ratifyLiteralFilenamesBeforeExecution, true, 'MIGRATIONS_LITERAL_RATIFICATION');
}

function validateApproval(ctx, candidate, requireApproval) {
  const approval = ctx.record(candidate.approval, 'APPROVAL_SHAPE');
  ctx.keys(approval, EXPECTED.approvalKeys, 'APPROVAL_KEYS');
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
    if (requireApproval) fail('APPROVAL_PENDING', 'checkpoint humano 04-15/T2 ainda nao foi registrado');
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
  validateAuthority(ctx, root.authority);
  validateTaxonomy(ctx, root.taxonomy);
  validateEnvelope(ctx, root.envelope);
  validatePayloads(ctx, root.payloads);
  validateHead(ctx, root.head);
  validateTemporal(ctx, root.temporalProtocol);
  validateReplay(ctx, root.replay);
  validateElo(ctx, root.eloPolicy);
  validateLegacy(ctx, root.legacy);
  validateCompatibility(ctx, root.compatibility);
  validateWorkflow(ctx, root.administrativeWorkflow);
  validateRegulation(ctx, root.regulation);
  validatePropagation(ctx, root.propagation);
  validateRecovery(ctx, root.recovery);
  validateRoster(ctx, root.roster);
  validateConstraints(ctx, root.constraints);
  validateMigrations(ctx, root.migrationCandidates);
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
    if (error instanceof SportsOddsSourceCandidateValidationError) throw error;
    fail('CANDIDATE_ROOT', 'raiz do projeto nao existe');
  }
  const expected = resolve(rootReal, CANDIDATE_RELATIVE_PATH);
  assertInside(rootReal, expected, 'CANDIDATE_PATH');
  let candidateReal;
  try {
    candidateReal = realpathSync(expected);
    if (!statSync(candidateReal).isFile()) fail('CANDIDATE_PATH', 'candidato nao e arquivo');
  } catch (error) {
    if (error instanceof SportsOddsSourceCandidateValidationError) throw error;
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
  const normalized = source.replace(/\r\n?/gu, '\n');
  return normalized;
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
  const approvedStatus = '  "decisionStatus": "approved_human_checkpoint_04-15-T2",';
  const pendingStatus = '  "decisionStatus": "candidate_pending_human_checkpoint_04-15-T2",';
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
    '    "status": "pending_human_checkpoint_04-15-T2",',
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

function validateApprovalSnapshotDigest(candidate, source) {
  if (candidate.approval.status !== 'approved') return;
  const observedDigest = sha256(pendingSnapshotSource(source));
  if (observedDigest.toUpperCase() !== candidate.approval.digest.toUpperCase()) {
    fail('APPROVAL_DIGEST_MISMATCH', 'digest aprovado nao corresponde ao snapshot pre-aprovacao do candidato');
  }
}

export function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

export function approvalSnapshotDigest(source) {
  return sha256(pendingSnapshotSource(source)).toUpperCase();
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
  fail('CLI_ARGUMENTS', 'uso: node scripts/validate-sports-odds-source-candidate.mjs --check | --require-approval');
}

export function sanitizeError(error) {
  if (error instanceof SportsOddsSourceCandidateValidationError) return error.message;
  return 'INTERNAL_ERROR: falha interna durante a validacao.';
}

export function runCli(argumentsList = process.argv.slice(2), projectRoot = defaultProjectRoot) {
  let options;
  try {
    options = parseArguments(argumentsList);
    const evidence = validateCandidateFile(projectRoot, options);
    console.log(`[validate-sports-odds-source-candidate] OK: ${evidence.checks} verificacoes; T2=${evidence.approvalStatus}.`);
    return 0;
  } catch (error) {
    const exitCode = error instanceof SportsOddsSourceCandidateValidationError
      && error.rule === 'APPROVAL_PENDING'
      ? 1
      : error instanceof SportsOddsSourceCandidateValidationError
        && error.rule === 'CLI_ARGUMENTS'
        ? 2
        : 1;
    console.error(`[validate-sports-odds-source-candidate] ERRO: ${sanitizeError(error)}`);
    return exitCode;
  }
}

const invokedAsScript = process.argv[1]
  && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invokedAsScript) process.exitCode = runCli();
