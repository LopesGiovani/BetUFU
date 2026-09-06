import { createHash } from 'node:crypto';
import { readFileSync, realpathSync } from 'node:fs';
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual } from 'node:util';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const projectRootReal = realpathSync(projectRoot);
const paths = {
  manifest: 'scripts/odds-calibration-manifest.json',
  fixtures: 'scripts/odds-calibration-fixtures.json',
  backtestReport: 'scripts/odds-backtest-report.json',
  quarantine: 'scripts/odds-unresolved-quarantine.json',
  validator: 'scripts/validate-odds-calibration.mjs',
};

// These local evidence checks never execute pricing, network access, or the extractor.
const snapshot = {
  path: 'scripts/data/laufu/laufu_partidas_2026.json',
  bytes: 987642,
  sha256: 'A4593833F5C9E6060DC26956DC6782F077EDD047546DAF1A44911AFEE023AB92',
};
const capturedAt = '2026-09-06T02:32:52.781Z';
const pendingApproval = 'pending_human_checkpoint_04-01-T2';
const approvedStatus = 'approved';
const approvalTokenKind = 'human_checkpoint_token';
const fixtureCases = [
  ['regular-confirmed-result', 'regular_sport_result', false, '445146'],
  ['administrative-wo', 'administrative_wo_without_sport_score', false, '445159'],
  ['numeric-wo', 'numeric_wo_contaminant', false, '445159'],
  ['abandonment-no-valid-result', 'abandonment_without_valid_result', true],
  ['cancelled-event', 'cancelled', true],
  ['postponed-rescheduled-event', 'postponed_or_rescheduled', true],
  ['corrected-confirmed-result', 'corrected_result_without_verified_lineage', true],
];
const riskIds = [
  'ROBOTS_NOT_EXECUTED', 'EDITION_HARDCODED', 'CANCELADA_UNCLASSIFIED',
  'SLUG_FALLBACK_SILENT', 'NUMERIC_WO_SCORE', 'MATCH_HOST_HARDCODED',
  'CACHE_WITHOUT_TTL', 'PAST_DATE_INFERRED_ADIADA', 'NON_DRY_RUN_WRITES_NETWORK',
];
const quarantineReasons = [
  'FALLBACK_SLUG_ID', 'MISSING_SPORT_TIME', 'NUMERIC_WO_SCORE',
  'NO_KNOWN_AT', 'NO_CONFIRMED_VERSION_CHAIN', 'SOURCE_STATUS_NOT_AUTHORITATIVE',
];
const scoreFields = [
  'placar_mandante_regulamentar', 'placar_visitante_regulamentar',
  'placar_mandante_final', 'placar_visitante_final',
];
const teamFields = [
  ['time_mandante_id', 'time_mandante_nome'],
  ['time_visitante_id', 'time_visitante_nome'],
];

// Only fixed evidence paths may be read; JSON values cannot select files.
const allowedEvidencePaths = new Set([
  ...Object.values(paths),
  snapshot.path,
  ...fixtureCases.map(([id]) => fixturePath(id)),
]);
const rawArtifactPrefix = 'scripts/data/';
const rawDataRootReal = realpathSync(resolve(projectRoot, rawArtifactPrefix));

function expect(condition, rule) {
  if (!condition) throw new Error(rule);
}

function equal(actual, expected, rule) {
  expect(isDeepStrictEqual(actual, expected), rule);
}

function sameSet(actual, expected, rule) {
  expect(Array.isArray(actual), rule);
  equal([...actual].sort(), [...expected].sort(), rule);
}

function nonempty(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function numericId(value) {
  return typeof value === 'string' && /^\d+$/u.test(value);
}

function timestamp(value) {
  return typeof value === 'string'
    && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/u.test(value)
    && Number.isFinite(Date.parse(value));
}

function finiteNumber(value) {
  return typeof value === 'number' && Number.isFinite(value);
}

function containsFiniteNumber(value) {
  if (finiteNumber(value)) return true;
  if (Array.isArray(value)) return value.some(containsFiniteNumber);
  return value !== null && typeof value === 'object'
    && Object.values(value).some(containsFiniteNumber);
}

function slugify(value) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/gu, '')
    .toLowerCase().replace(/[^a-z0-9]+/gu, '-').replace(/^-+|-+$/gu, '');
}

function fallbackFields(row) {
  return teamFields.filter(([id, name]) => nonempty(row[id])
    && !numericId(row[id]) && row[id] === slugify(row[name]))
    .map(([id]) => `${id}=${row[id]}`);
}

function numericWalkover(row) {
  return row.estado === 'WO' && scoreFields.every(field => Number.isInteger(row[field]));
}

function fixturePath(id) {
  return `backend/src/test/resources/odds/${id}.json`;
}

function validateApprovedEvidence(manifest, report, bundle) {
  const artifact = manifest.source?.rawArtifact;
  const rawDigest = createHash('sha256').update(bundle.rawBytes).digest('hex').toUpperCase();
  const hasCurrentSnapshotFingerprint = bundle.rawBytes.length === snapshot.bytes
    && rawDigest === snapshot.sha256;
  const isCurrentSnapshot = artifact?.path === snapshot.path || hasCurrentSnapshotFingerprint;
  if (isCurrentSnapshot) {
    if (artifact?.path === snapshot.path) {
      equal(artifact.bytes, snapshot.bytes,
        'a captura LAUFU atual nao pode ter tamanho adulterado');
      equal(typeof artifact.sha256 === 'string' ? artifact.sha256.toUpperCase() : artifact.sha256,
        snapshot.sha256, 'a captura LAUFU atual nao pode ter digest adulterado');
      equal(manifest.source.type, 'exploratory_snapshot',
        'a captura LAUFU atual nao pode ser reclassificada');
      equal(manifest.source.uri, 'https://laufu.jliga.club',
        'T2 fixa a origem exploratoria revisada');
      equal(manifest.source.edition, '2026',
        'T2 fixa a edicao exploratoria revisada');
      equal(manifest.scope.sourceAuthority, 'exploratory',
        'T2 fixa autoridade exploratoria');
      equal(manifest.scope.calibrationEligible, false,
        'T2 nunca libera a captura LAUFU para calibracao');
      equal(manifest.scope.productionEligible, false,
        'T2 nunca libera a captura LAUFU para producao');
      equal(manifest.parameters?.['D-120']?.laufuIsNotProductionAuthority, true,
        'T2 preserva D-120 para a captura LAUFU');
      equal(report.dataset.sourceSha256, snapshot.sha256,
        'T2 fixa o digest da captura revisada');
    }
    throw new Error('T2 bloqueado: snapshot exploratorio LAUFU nao e elegivel para calibracao');
  }

  equal(manifest.approval.status, approvedStatus, 'T2 exige status approved no manifesto');
  expect(timestamp(manifest.approval.approvedAt), 'T2 exige approvedAt em formato temporal');
  expect(timestamp(manifest.source.capturedAt), 'T2 exige capturedAt da fonte');
  expect(Date.parse(manifest.approval.approvedAt) >= Date.parse(manifest.source.capturedAt),
    'approvedAt nao pode anteceder a captura revisada');
  expect(nonempty(manifest.approval.approvedBy), 'T2 exige approvedBy');
  equal(manifest.approval.kind, approvalTokenKind,
    'T2 exige token humano de checkpoint, nao digest criptografico');
  expect(typeof manifest.approval.digest === 'string'
    && /^[0-9a-f]{64}$/iu.test(manifest.approval.digest),
  'T2 exige token hexadecimal de checkpoint com 64 caracteres');
  equal(report.approval.status, approvedStatus, 'T2 exige status approved no backtest');
  equal(report.approval.kind, approvalTokenKind,
    'backtest deve identificar o token humano de checkpoint');
  equal(report.approval.digest, manifest.approval.digest,
    'token de aprovacao deve coincidir entre manifesto e backtest');
  equal(report.approval.reviewReference, manifest.approval.reviewReference,
    'referencia da revisao deve coincidir entre manifesto e backtest');
  equal(report.approval.reviewedArtifactHashes, manifest.approval.reviewedArtifactHashes,
    'hashes revisados devem coincidir entre manifesto e backtest');

  expect(nonempty(manifest.scope.sourceAuthority)
    && manifest.scope.sourceAuthority !== 'unknown'
    && manifest.scope.sourceAuthority !== 'exploratory',
    'T2 exige autoridade de fonte explicitamente informada');
  expect(typeof manifest.scope.calibrationEligible === 'boolean',
    'T2 exige elegibilidade de calibracao explicita');
  expect(typeof manifest.scope.productionEligible === 'boolean',
    'T2 exige elegibilidade de producao explicita');
  equal(report.dataset.sourceAuthority, manifest.scope.sourceAuthority,
    'autoridade da fonte deve coincidir entre manifesto e backtest');
  equal(report.dataset.calibrationEligible, manifest.scope.calibrationEligible,
    'elegibilidade de calibracao deve coincidir entre manifesto e backtest');
  equal(report.dataset.productionEligible, manifest.scope.productionEligible,
    'elegibilidade de producao deve coincidir entre manifesto e backtest');
  equal(manifest.scope.productionEligible, false,
    'T2 de calibracao nunca concede elegibilidade de producao');
  equal(manifest.scope.calibrationEligible, true,
    'T2 exige dataset elegivel para calibracao');
  expect(nonempty(manifest.source.type) && manifest.source.type !== 'exploratory_snapshot',
    'T2 exige tipo de fonte nao exploratorio');
  expect(typeof manifest.source.uri === 'string' && /^https?:\/\//u.test(manifest.source.uri),
    'T2 exige URI de proveniencia https/http');
  expect(nonempty(manifest.source.edition), 'T2 exige edicao da fonte');
  equal(bundle.raw.metadados?.fonte, manifest.source.uri,
    'URI da fonte deve coincidir com os metadados brutos');
  equal(bundle.raw.metadados?.edicao, manifest.source.edition,
    'edicao da fonte deve coincidir com os metadados brutos');
  equal(bundle.raw.metadados?.extracao_timestamp, manifest.source.capturedAt,
    'capturedAt deve coincidir com os metadados brutos');
  validateTemporalCandidate(bundle, manifest, report);
  equal(manifest.scope.knownAtAvailable, true, 'T2 exige known_at historico');
  equal(manifest.scope.confirmedVersionChainAvailable, true,
    'T2 exige cadeia de versoes confirmadas');
  expect(nonempty(manifest.scope.backtestStatus)
    && manifest.scope.backtestStatus !== 'blocked_pending_verified_temporal_dataset',
  'T2 exige backtest temporal desbloqueado');
  expect(nonempty(manifest.temporalProtocol.trainEvaluationSplit)
    && !manifest.temporalProtocol.trainEvaluationSplit.startsWith('open_pending'),
  'T2 exige separacao treino-avaliacao definida');
  expect(nonempty(manifest.temporalProtocol.recencyFormula)
    && !manifest.temporalProtocol.recencyFormula.startsWith('open_pending'),
  'T2 exige formula de recencia definida');
  expect(nonempty(manifest.temporalProtocol.leakageControl),
    'T2 exige controle de leakage documentado');

  const parameterRules = [
    ['D-101', p => p.initialRating === 1500],
    ['D-102', p => p.provisionalK === 32
      && p.matureK === 16
      && Number.isInteger(p.experienceThreshold) && p.experienceThreshold > 0],
    ['D-103', p => p.chronologicalOrdering === true && nonempty(p.recencyFormula)
      && (p.monthlyHalfLife === undefined || p.monthlyHalfLife === null
        || (finiteNumber(p.monthlyHalfLife) && p.monthlyHalfLife > 0))],
    ['D-104', p => p.editionRegressionToInitial === 0.5],
    ['D-105', p => p.requiresConfirmedCurrentVersion === true],
    ['D-106', p => p.drawIsConditional === true
      && p.davidsonParameters !== null && typeof p.davidsonParameters === 'object'
      && !Array.isArray(p.davidsonParameters)
      && Object.keys(p.davidsonParameters).length > 0
      && containsFiniteNumber(p.davidsonParameters)],
    ['D-107', p => p.sharedConservativeDrawPrior === true
      && Number.isInteger(p.specializationThreshold) && p.specializationThreshold > 0],
    ['D-108', p => p.lowSampleEstimate === 'neutral'
      && p.confidenceBands !== null && typeof p.confidenceBands === 'object'
      && !Array.isArray(p.confidenceBands)
      && Object.keys(p.confidenceBands).length > 0
      && containsFiniteNumber(p.confidenceBands)],
    ['D-109', p => p.administrativeWalkoverExcluded === true],
    ['D-110', p => p.cancelledOrInvalidResultExcluded === true],
    ['D-111', p => p.postponementSuspendsAndRescheduleVersions === true],
    ['D-113', p => p.initialMargin === 0.05 && p.configurableAndVersioned === true],
    ['D-114', p => p.publishedDecimalPlaces === 2
      && nonempty(p.roundingMode) && p.publishedPriceImmutable === true],
    ['D-115', p => p.fairProbabilitySeparateFromPublishedPrice === true],
    ['D-120', p => p.laufuIsNotProductionAuthority === true],
  ];
  for (const [id, rule] of parameterRules) {
    const parameter = manifest.parameters?.[id];
    expect(parameter && typeof parameter === 'object', `T2 exige parametro: ${id}`);
    expect(!/open|pending|provisional|not_evaluated/iu.test(JSON.stringify(parameter.status ?? '')),
      `T2 rejeita parametro ainda aberto: ${id}`);
    expect(rule(parameter), `T2 rejeita parametro invalido: ${id}`);
  }

  expect(['completed', 'evaluated'].includes(report.status),
    'T2 exige status completed ou evaluated no backtest');
  expect(nonempty(report.evidenceStatus)
    && !/insufficient|not_run|blocked/iu.test(report.evidenceStatus),
  'T2 exige evidencia temporal suficiente');
  expect(Number.isInteger(report.dataset.rowsEligibleAfterLineageChecks)
    && report.dataset.rowsEligibleAfterLineageChecks > 0,
    'T2 exige linhas elegiveis apos linhagem');
  expect(Number.isInteger(report.dataset.trainingRows) && report.dataset.trainingRows > 0
    && Number.isInteger(report.dataset.evaluationRows) && report.dataset.evaluationRows > 0,
    'T2 exige treino e avaliacao nao vazios');
  const metricNames = ['logLoss', 'brierScore', 'calibrationError', 'overroundInvariant', 'confidenceIntervals'];
  sameSet(Object.keys(report.metrics), metricNames, 'T2 exige metricas completas');
  for (const [name, value] of Object.entries(report.metrics)) {
    if (name === 'confidenceIntervals') {
      expect(value !== null && typeof value === 'object' && !Array.isArray(value),
        'T2 exige intervalos de confianca estruturados');
    } else {
      expect(finiteNumber(value) && value >= 0, `T2 exige metrica numerica nao negativa: ${name}`);
    }
  }
  const expectedAxes = ['K experience policy', 'recency', 'Davidson', 'confidence', 'margin', 'rounding'];
  expect(Array.isArray(report.sensitivityMatrix)
    && report.sensitivityMatrix.length === expectedAxes.length,
  'T2 exige os seis eixos de sensibilidade');
  sameSet(report.sensitivityMatrix.map(sensitivity => sensitivity.axis), expectedAxes,
    'T2 exige eixos de sensibilidade completos');
  for (const sensitivity of report.sensitivityMatrix) {
    expect(Array.isArray(sensitivity.scenarios) && sensitivity.scenarios.length > 0,
      `T2 exige cenarios de sensibilidade: ${sensitivity.axis}`);
    expect(['evaluated', 'completed', 'locked_input_evaluated'].includes(sensitivity.status),
      `T2 exige sensibilidade avaliada: ${sensitivity.axis}`);
  }

  expect(nonempty(manifest.approval.reviewReference),
    'T2 exige referencia externa da revisao humana');
  const reviewed = manifest.approval.reviewedArtifactHashes;
  expect(reviewed && typeof reviewed === 'object' && !Array.isArray(reviewed),
    'T2 exige hashes dos artefatos revisados');
  const reviewedKeys = ['backtestReport', 'fixtureRegistry', 'quarantine', 'rawArtifact', 'fixtureFiles'];
  sameSet(Object.keys(reviewed), reviewedKeys, 'T2 exige hashes completos dos artefatos');
  const actualHashes = {
    backtestReport: createHash('sha256').update(bundle.reportBytes).digest('hex'),
    fixtureRegistry: createHash('sha256').update(bundle.registryBytes).digest('hex'),
    quarantine: createHash('sha256').update(bundle.quarantineBytes).digest('hex'),
    rawArtifact: createHash('sha256').update(bundle.rawBytes).digest('hex'),
    fixtureFiles: Object.fromEntries([...bundle.fixtureBytes.entries()]
      .map(([id, bytes]) => [id, createHash('sha256').update(bytes).digest('hex')])),
  };
  for (const key of reviewedKeys) {
    if (key === 'fixtureFiles') {
      expect(reviewed[key] && typeof reviewed[key] === 'object' && !Array.isArray(reviewed[key]),
        'T2 exige SHA-256 revisado de cada fixture');
      sameSet(Object.keys(reviewed[key]), fixtureCases.map(([id]) => id),
        'T2 exige hashes das sete fixtures');
      for (const [id] of fixtureCases) {
        expect(typeof reviewed[key][id] === 'string' && /^[0-9a-f]{64}$/iu.test(reviewed[key][id]),
          `T2 exige SHA-256 revisado: fixture ${id}`);
        equal(reviewed[key][id].toUpperCase(), actualHashes[key][id].toUpperCase(),
          `SHA-256 revisado divergente: fixture ${id}`);
      }
    } else {
      expect(typeof reviewed[key] === 'string' && /^[0-9a-f]{64}$/iu.test(reviewed[key]),
        `T2 exige SHA-256 revisado: ${key}`);
      equal(reviewed[key].toUpperCase(), actualHashes[key].toUpperCase(),
        `SHA-256 revisado divergente: ${key}`);
    }
  }
}

// Read only fixed local paths; artifact links never select files or trigger requests.
function isVersionedRawArtifactPath(path) {
  const segments = typeof path === 'string' ? path.split('/') : [];
  return typeof path === 'string'
    && path.startsWith(rawArtifactPrefix)
    && path.endsWith('.json')
    && !path.includes('\\')
    && !path.includes('\0')
    && !isAbsolute(path)
    && segments.every(segment => segment.length > 0 && segment !== '.' && segment !== '..');
}

function readSafeBytes(path, { allowRawArtifact = false } = {}) {
  expect(typeof path === 'string' && path.length > 0,
    'caminho de evidencia obrigatorio');
  expect(!path.includes('\0') && !isAbsolute(path),
    `Caminho de evidencia absoluto ou invalido: ${path}`);
  expect(allowedEvidencePaths.has(path) || (allowRawArtifact && isVersionedRawArtifactPath(path)),
    `Caminho de evidencia fora da allowlist interna: ${path}`);
  const resolvedPath = resolve(projectRoot, path);
  let realPath;
  try {
    realPath = realpathSync(resolvedPath);
  } catch {
    throw new Error(`Arquivo local indisponivel: ${path}`);
  }
  const relativePath = relative(projectRootReal, realPath);
  expect(relativePath !== ''
    && !isAbsolute(relativePath)
    && !relativePath.startsWith(`..${sep}`)
  && relativePath !== '..',
  `Caminho de evidencia fora da worktree: ${path}`);
  if (allowRawArtifact) {
    const rawRelativePath = relative(rawDataRootReal, realPath);
    expect(rawRelativePath !== ''
      && !isAbsolute(rawRelativePath)
      && !rawRelativePath.startsWith(`..${sep}`)
      && rawRelativePath !== '..',
    `Caminho de fonte bruta fora de scripts/data: ${path}`);
  }
  return readFileSync(realPath);
}

function readBytes(path) {
  expect(typeof path === 'string' && allowedEvidencePaths.has(path),
    `Caminho de evidencia fora da allowlist: ${path}`);
  return readSafeBytes(path);
}

function readRawArtifactBytes(path) {
  expect(isVersionedRawArtifactPath(path), `Caminho de fonte bruta inseguro: ${path}`);
  return readSafeBytes(path, { allowRawArtifact: true });
}

function parseObject(bytes, path) {
  let value;
  try {
    value = JSON.parse(bytes.toString('utf8'));
  } catch {
    throw new Error(`JSON invalido: ${path}`);
  }
  expect(value !== null && typeof value === 'object' && !Array.isArray(value),
    `Objeto JSON obrigatorio: ${path}`);
  return value;
}

function loadBundle() {
  const manifest = parseObject(readBytes(paths.manifest), paths.manifest);
  const report = parseObject(readBytes(paths.backtestReport), paths.backtestReport);
  const registry = parseObject(readBytes(paths.fixtures), paths.fixtures);
  const quarantine = parseObject(readBytes(paths.quarantine), paths.quarantine);
  const rawPath = manifest.source?.rawArtifact?.path;
  const rawBytes = readRawArtifactBytes(rawPath);
  const raw = parseObject(rawBytes, rawPath);
  const fixtures = new Map();
  const fixtureBytes = new Map();
  for (const [id] of fixtureCases) {
    const path = fixturePath(id);
    const bytes = readBytes(path);
    fixtureBytes.set(id, bytes);
    fixtures.set(id, parseObject(bytes, path));
  }
  return {
    manifest,
    report,
    registry,
    quarantine,
    raw,
    rawBytes,
    reportBytes: readBytes(paths.backtestReport),
    registryBytes: readBytes(paths.fixtures),
    quarantineBytes: readBytes(paths.quarantine),
    fixtures,
    fixtureBytes,
  };
}

function validateSharedSchemas(bundle) {
  const { manifest, report, registry, quarantine } = bundle;
  equal(manifest.schemaVersion, '04-01.calibration-manifest.v2', 'schema do manifesto');
  equal(report.schemaVersion, '04-01.backtest-report.v2', 'schema do backtest');
  equal(registry.schemaVersion, '04-01.calibration-fixtures.v1', 'schema do inventario');
  equal(quarantine.schemaVersion, '04-01.unresolved-quarantine.v1', 'schema da quarentena');
  equal(manifest.phase, '04-mercados-pr-jogo-e-odds-transparentes', 'fase');
  equal(manifest.plan, '04-01', 'plano');
  const { manifest: unused, ...artifactPaths } = paths;
  equal(manifest.artifacts, artifactPaths, 'links do manifesto');
  for (const artifact of [report, registry, quarantine]) {
    equal(artifact.manifest, paths.manifest, 'link para manifesto');
  }
}

function validateSharedIntegrity(bundle) {
  const { manifest, report, registry, quarantine, raw, rawBytes } = bundle;
  const artifact = manifest.source?.rawArtifact;
  expect(artifact && typeof artifact === 'object', 'fonte bruta obrigatoria');
  expect(typeof artifact.path === 'string', 'caminho da fonte bruta obrigatorio');
  expect(Number.isInteger(artifact.bytes) && artifact.bytes > 0,
    'tamanho da fonte bruta obrigatorio');
  expect(typeof artifact.sha256 === 'string' && /^[0-9a-f]{64}$/iu.test(artifact.sha256),
    'SHA-256 da fonte bruta obrigatorio');
  equal(rawBytes.length, artifact.bytes, 'tamanho da fonte bruta');
  const digest = createHash('sha256').update(rawBytes).digest('hex').toUpperCase();
  equal(digest, artifact.sha256.toUpperCase(), 'SHA-256 da fonte bruta');
  expect(raw && typeof raw === 'object' && !Array.isArray(raw), 'objeto da fonte bruta');
  expect(Array.isArray(raw.partidas), 'partidas da fonte bruta');
  expect(raw.metadados && typeof raw.metadados === 'object', 'metadados da fonte bruta');
  expect(typeof report.dataset?.sourceSha256 === 'string', 'digest da fonte no backtest');
  expect(typeof registry.sourceSnapshotSha256 === 'string', 'digest da fonte no inventario');
  expect(typeof quarantine.sourceSnapshotSha256 === 'string', 'digest da fonte na quarentena');
  equal(report.dataset.sourceSha256.toUpperCase(), artifact.sha256.toUpperCase(),
    'digest da fonte no backtest');
  equal(registry.sourceSnapshotSha256.toUpperCase(), artifact.sha256.toUpperCase(),
    'digest da fonte no inventario');
  equal(quarantine.sourceSnapshotSha256.toUpperCase(), artifact.sha256.toUpperCase(),
    'digest da fonte na quarentena');
  expect(typeof manifest.scope?.eligibilitySubject === 'string',
    'sujeito da elegibilidade obrigatorio');
  expect(typeof manifest.scope?.calibrationEligible === 'boolean',
    'elegibilidade de calibracao obrigatoria');
  expect(typeof manifest.scope?.productionEligible === 'boolean',
    'elegibilidade de producao obrigatoria');
  equal(report.dataset.eligibilitySubject, manifest.scope.eligibilitySubject,
    'sujeito da elegibilidade no backtest');
  equal(report.dataset.calibrationEligible, manifest.scope.calibrationEligible,
    'elegibilidade de calibracao no backtest');
  equal(report.dataset.productionEligible, manifest.scope.productionEligible,
    'elegibilidade de producao no backtest');
}

function validateFixtureSemantics(bundle) {
  const { manifest, registry, fixtures, raw } = bundle;
  expect(Array.isArray(raw?.partidas), 'captura bruta obrigatoria para fixtures reais');
  const sourceById = new Map(raw.partidas.map(row => [row.origem_id, row]));
  expect(Array.isArray(registry.fixtures), 'inventario de fixtures obrigatorio');
  sameSet(registry.fixtures.map(entry => entry.id), fixtureCases.map(([id]) => id),
    'T2 exige exatamente os sete IDs de fixture');
  for (const [id, classification, synthetic, sourceId] of fixtureCases) {
    const entry = registry.fixtures.find(candidate => candidate.id === id);
    const fixture = fixtures.get(id);
    expect(entry && fixture, `fixture ausente: ${id}`);
    equal(entry.fixturePath, fixturePath(id), 'path associado ao ID da fixture');
    equal(entry.classification, classification, 'classificacao no inventario da fixture');
    equal(entry.syntheticContractFixture, synthetic, 'origem sintetica no inventario da fixture');
    equal(entry.sourceRecordId, sourceId, 'origem por fixture');
    equal(entry.eligibleForCalibration, false, 'inventario de fixture inelegivel');
    expect(nonempty(entry.exclusionReason), 'motivo de exclusao da fixture');
    equal(fixture.schemaVersion, '04-01.fixture.v1', 'schema da fixture');
    equal(fixture.fixtureId, id, 'ID da fixture');
    equal(fixture.classification, classification, 'classificacao da fixture');
    equal(fixture.syntheticContractFixture, synthetic, 'origem sintetica da fixture');
    equal(fixture.eligibleForCalibration, false, 'fixture nunca entra na calibracao');
    expect(nonempty(fixture.exclusionReason), 'fixture precisa explicar sua exclusao');

    const event = fixture.event;
    expect(event && typeof event === 'object' && !Array.isArray(event),
      `evento obrigatorio na fixture: ${id}`);
    expect(nonempty(event.competitionId) && nonempty(event.edition),
      `escopo esportivo incompleto na fixture: ${id}`);
    expect(timestamp(event.sportTime), `sportTime invalido na fixture: ${id}`);
    const result = fixture.result;
    expect(result && typeof result === 'object' && !Array.isArray(result)
      && nonempty(result.kind), `taxonomia ausente na fixture: ${id}`);
    for (const score of [result.homeScore, result.awayScore]) {
      expect(score === null || (Number.isInteger(score) && score >= 0),
        `placar invalido na fixture: ${id}`);
    }

    if (synthetic) {
      equal(fixture.source, {
        type: 'synthetic_contract_fixture',
        reason: fixture.source?.reason,
      }, 'fixture sintetica deve declarar somente sua origem e motivo');
      expect(nonempty(fixture.source.reason), `motivo sintetico ausente: ${id}`);
      equal(event.competitionId, 'fixture-competition', 'competicao sintetica');
      equal(event.edition, 'fixture', 'edicao sintetica');
      equal(event.homeId, 'fixture-home', 'mandante sintetico');
      equal(event.awayId, 'fixture-away', 'visitante sintetico');
    } else {
      const source = fixture.source;
      const sourceRow = sourceById.get(sourceId);
      expect(source && typeof source === 'object' && !Array.isArray(source),
        `proveniencia ausente na fixture real: ${id}`);
      expect(sourceRow && typeof sourceRow === 'object' && !Array.isArray(sourceRow),
        `registro de origem ausente na captura: ${id}`);
      expect(typeof source.uri === 'string' && /^https?:\/\//u.test(source.uri),
        `URI de proveniencia invalida: ${id}`);
      expect(numericId(source.recordId), `recordId invalido: ${id}`);
      equal(source.recordId, sourceId, `recordId da fixture diverge da origem: ${id}`);
      equal(source.uri, sourceRow.origem_url, `URI da fixture diverge da origem: ${id}`);
      equal(source.capturedAt, sourceRow.extracao_timestamp,
        `capturedAt da fixture diverge da origem: ${id}`);
      expect(timestamp(source.capturedAt), `capturedAt invalido: ${id}`);
      equal(source.snapshotSha256?.toUpperCase(), manifest.source.rawArtifact.sha256.toUpperCase(),
        `digest da fixture divergente: ${id}`);
      expect(nonempty(event.sport) && nonempty(event.category),
        `modalidade/categoria ausente na fixture real: ${id}`);
      for (const [fixtureField, sourceField] of [
        ['competitionId', 'campeonato_id'], ['edition', 'edicao'], ['sport', 'modalidade'],
        ['category', 'categoria'], ['sportTime', 'data_hora_iso'],
      ]) equal(fixture.event[fixtureField], sourceRow[sourceField],
        `escopo da fixture diverge da origem: ${id}`);
      for (const [idField, nameField] of [['homeId', 'homeName'], ['awayId', 'awayName']]) {
        expect(event[idField] === null || numericId(event[idField]),
          `identidade canonica invalida na fixture: ${id}`);
        expect(nonempty(event[nameField]), `nome observado ausente na fixture: ${id}`);
        const sourceIdField = idField === 'homeId' ? 'time_mandante_id' : 'time_visitante_id';
        const sourceNameField = nameField === 'homeName' ? 'time_mandante_nome' : 'time_visitante_nome';
        equal(event[idField], numericId(sourceRow[sourceIdField]) ? sourceRow[sourceIdField] : null,
          `identidade da fixture diverge da origem: ${id}`);
        equal(slugify(event[nameField]), slugify(sourceRow[sourceNameField]),
          `rotulo da fixture diverge da origem: ${id}`);
      }
    }

    if (id === 'regular-confirmed-result') {
      equal(result.kind, 'CONCLUIDA_UNVERIFIED', 'resultado regular sem confirmacao');
      expect(Number.isInteger(result.homeScore) && Number.isInteger(result.awayScore),
        'resultado regular precisa preservar placar observado');
      equal(result.confirmedVersionId, null, 'resultado regular sem versao confirmada');
      equal(result.knownAt, null, 'resultado regular sem known_at');
    } else if (id === 'administrative-wo') {
      equal(result.kind, 'ADMINISTRATIVE_WO', 'taxonomia de W.O. administrativo');
      equal(result.homeScore, null, 'W.O. administrativo sem placar mandante');
      equal(result.awayScore, null, 'W.O. administrativo sem placar visitante');
    } else if (id === 'numeric-wo') {
      equal(result.kind, 'ADMINISTRATIVE_WO', 'W.O. numerico continua administrativo');
      expect(Number.isInteger(result.sourceHomeScore) && Number.isInteger(result.sourceAwayScore),
        'W.O. numerico preserva campos fonte');
      equal(result.homeScoreForModel, null, 'W.O. numerico nao entra no modelo');
      equal(result.awayScoreForModel, null, 'W.O. numerico nao entra no modelo');
    } else if (id === 'abandonment-no-valid-result') {
      equal(result.kind, 'ABANDONED_NO_VALID_RESULT', 'taxonomia de abandono');
      equal(result.homeScore, null, 'abandono sem placar mandante');
      equal(result.awayScore, null, 'abandono sem placar visitante');
    } else if (id === 'cancelled-event') {
      equal(result.kind, 'CANCELLED', 'taxonomia de cancelamento');
      equal(result.homeScore, null, 'cancelamento sem placar mandante');
      equal(result.awayScore, null, 'cancelamento sem placar visitante');
    } else if (id === 'postponed-rescheduled-event') {
      equal(result.kind, 'POSTPONED', 'taxonomia de adiamento');
      expect(nonempty(result.previousMarketVersion)
        && nonempty(result.rescheduledMarketVersion), 'remarcacao precisa versionar mercado');
      equal(result.homeScore, null, 'adiamento sem placar mandante');
      equal(result.awayScore, null, 'adiamento sem placar visitante');
    } else if (id === 'corrected-confirmed-result') {
      equal(result.kind, 'CORRECTED_CONFIRMED_RESULT', 'taxonomia de correcao');
      expect(nonempty(result.supersedesVersionId)
        && nonempty(result.confirmedVersionId), 'correcao precisa de linhagem');
      expect(timestamp(result.knownAt) && Date.parse(result.knownAt) > Date.parse(event.sportTime),
        'correcao precisa known_at posterior ao evento');
    }
  }
}

function field(row, names) {
  for (const name of names) {
    if (Object.prototype.hasOwnProperty.call(row, name)) return row[name];
  }
  return undefined;
}

function validateTemporalCandidate(bundle, manifest, report) {
  const rows = bundle.raw.partidas;
  expect(Array.isArray(rows) && rows.length > 0,
    'T2 exige dataset temporal nao vazio');
  const byId = new Map();
  for (const row of rows) {
    expect(row && typeof row === 'object' && !Array.isArray(row),
      'T2 exige linhas de dataset estruturadas');
    const recordId = field(row, ['origem_id', 'sourceRecordId']);
    expect(numericId(recordId), 'T2 exige ID de registro numerico');
    expect(!byId.has(recordId), 'T2 rejeita IDs de registro duplicados');
    byId.set(recordId, row);
    const knownAt = field(row, ['known_at', 'knownAt']);
    expect(timestamp(knownAt), 'T2 exige known_at em cada linha');
    const confirmedVersionId = field(row, ['confirmed_version_id', 'confirmedVersionId']);
    expect(confirmedVersionId === null || nonempty(confirmedVersionId),
      'T2 exige confirmedVersionId explicito ou nulo');
  }

  const eligibleStatuses = new Set([
    'CONCLUIDA', 'CONFIRMADO', 'CORRIGIDA', 'CORRIGIDO',
    'SPORTING_RESULT', 'CONFIRMED', 'CORRECTED',
  ]);
  const eligible = rows.filter(row => {
    const status = field(row, ['estado', 'status', 'resultKind']);
    const homeId = field(row, ['time_mandante_id', 'homeId']);
    const awayId = field(row, ['time_visitante_id', 'awayId']);
    const sportTime = field(row, ['data_hora_iso', 'sportTime']);
    const confirmedVersionId = field(row, ['confirmed_version_id', 'confirmedVersionId']);
    const homeScore = field(row, [
      'placar_mandante_final', 'placar_mandante_regulamentar', 'homeScore',
    ]);
    const awayScore = field(row, [
      'placar_visitante_final', 'placar_visitante_regulamentar', 'awayScore',
    ]);
    const lineage = field(row, ['confirmed_version_chain', 'confirmedVersionChain', 'version_lineage']);
    if (!eligibleStatuses.has(status) || !numericId(homeId) || !numericId(awayId)
      || !timestamp(sportTime) || !nonempty(confirmedVersionId)
      || !Number.isInteger(homeScore) || homeScore < 0
      || !Number.isInteger(awayScore) || awayScore < 0) return false;
    expect(Array.isArray(lineage) && lineage.length > 0,
      'T2 exige cadeia de versoes confirmadas nas linhas elegiveis');
    return true;
  });
  const eligibleIds = eligible.map(row => field(row, ['origem_id', 'sourceRecordId']));
  const dataset = report.dataset;
  equal(dataset.rowsObserved, rows.length, 'T2 cruza rowsObserved com a fonte bruta');
  equal(dataset.rowsEligibleAfterLineageChecks, eligible.length,
    'T2 deriva linhas elegiveis da fonte bruta');
  expect(Array.isArray(dataset.eligibleRecordIds)
    && Array.isArray(dataset.trainingRecordIds)
    && Array.isArray(dataset.evaluationRecordIds),
  'T2 exige IDs explicitos do split temporal');
  const idLists = [dataset.eligibleRecordIds, dataset.trainingRecordIds, dataset.evaluationRecordIds];
  for (const ids of idLists) {
    expect(ids.every(numericId), 'T2 exige IDs numericos no split temporal');
    equal(new Set(ids).size, ids.length, 'T2 rejeita IDs duplicados no split temporal');
  }
  sameSet(dataset.eligibleRecordIds, eligibleIds,
    'IDs elegiveis declarados divergem da fonte');
  equal(dataset.trainingRows, dataset.trainingRecordIds.length,
    'contagem de treino diverge dos IDs');
  equal(dataset.evaluationRows, dataset.evaluationRecordIds.length,
    'contagem de avaliacao diverge dos IDs');
  expect(dataset.trainingRows > 0 && dataset.evaluationRows > 0,
    'T2 exige treino e avaliacao nao vazios');
  sameSet([...dataset.trainingRecordIds, ...dataset.evaluationRecordIds], eligibleIds,
    'split temporal nao cobre exatamente as linhas elegiveis');
  equal(new Set(dataset.trainingRecordIds).size + new Set(dataset.evaluationRecordIds).size,
    eligibleIds.length, 'treino e avaliacao nao podem se sobrepor');

  const split = manifest.temporalProtocol;
  expect(timestamp(split.trainCutoff) && timestamp(split.evaluationCutoff),
    'T2 exige cutoffs temporais explicitos');
  expect(Date.parse(split.trainCutoff) < Date.parse(split.evaluationCutoff),
    'cutoff de treino deve anteceder cutoff de avaliacao');
  for (const id of dataset.trainingRecordIds) {
    expect(Date.parse(field(byId.get(id), ['known_at', 'knownAt'])) <= Date.parse(split.trainCutoff),
      'linha de treino fora do cutoff');
  }
  for (const id of dataset.evaluationRecordIds) {
    const knownAt = Date.parse(field(byId.get(id), ['known_at', 'knownAt']));
    expect(knownAt > Date.parse(split.trainCutoff)
      && knownAt <= Date.parse(split.evaluationCutoff),
    'linha de avaliacao fora dos cutoffs');
  }
}

function validateApprovalMode() {
  let completed = 0;
  let current = 'leitura T2';
  const check = (name, action) => {
    current = name;
    action();
    completed += 1;
  };

  try {
    let bundle;
    check('JSONs locais do T2', () => {
      bundle = loadBundle();
    });

    check('schemas e links do T2', () => {
      validateSharedSchemas(bundle);
    });

    check('captura e digests do T2', () => {
      validateSharedIntegrity(bundle);
    });

    check('evidencia aprovada do T2', () => validateApprovedEvidence(
      bundle.manifest, bundle.report, bundle));

    check('fixtures e quarentena protegidas no T2', () => {
      const { manifest, registry, quarantine, fixtures } = bundle;
      expect(manifest.classification?.syntheticFixturesNeverEnterBacktest === true,
        'T2 preserva exclusao de fixtures sinteticas');
      expect(manifest.parameters?.['D-109']?.administrativeWalkoverExcluded === true,
        'T2 preserva exclusao de W.O. administrativo');
      expect(manifest.parameters?.['D-110']?.cancelledOrInvalidResultExcluded === true,
        'T2 preserva exclusao de resultados invalidos');
      validateFixtureSemantics(bundle);
      expect(Array.isArray(quarantine.records), 'T2 exige registros de quarentena');
      for (const record of quarantine.records) {
        equal(record.calibrationEligible, false, 'quarentena do T2 permanece inelegivel');
      }
    });

    console.log(`Gate T2: ${completed}/${completed} verificacoes; 0 erros. Evidencia aprovada e completa.`);
  } catch (error) {
    console.error(`FALHA em ${current}: ${error instanceof TypeError ? 'estrutura obrigatoria ausente ou invalida' : error.message}`);
    console.error(`${completed} verificacoes concluidas; 1 erro; T2 bloqueado. Nenhuma aprovacao de produto ou precificacao.`);
    process.exitCode = 1;
  }
}

function validate({ requireApproval = false, silent = false } = {}) {
  if (requireApproval) {
    validateApprovalMode();
    return;
  }
  let completed = 0;
  let current = 'leitura local';
  const check = (name, action) => {
    current = name;
    action();
    completed += 1;
  };

  try {
    let manifest, report, registry, quarantine, raw, rawBytes;
    const fixtures = new Map();
    check('JSONs locais', () => {
      manifest = parseObject(readBytes(paths.manifest), paths.manifest);
      report = parseObject(readBytes(paths.backtestReport), paths.backtestReport);
      registry = parseObject(readBytes(paths.fixtures), paths.fixtures);
      quarantine = parseObject(readBytes(paths.quarantine), paths.quarantine);
      rawBytes = readBytes(snapshot.path);
      raw = parseObject(rawBytes, snapshot.path);
      for (const [id] of fixtureCases) {
        fixtures.set(id, parseObject(readBytes(fixturePath(id)), fixturePath(id)));
      }
    });

    check('schemas e links', () => {
      equal(manifest.schemaVersion, '04-01.calibration-manifest.v2', 'schema do manifesto');
      equal(report.schemaVersion, '04-01.backtest-report.v2', 'schema do backtest');
      equal(registry.schemaVersion, '04-01.calibration-fixtures.v1', 'schema do inventario');
      equal(quarantine.schemaVersion, '04-01.unresolved-quarantine.v1', 'schema da quarentena');
      equal(manifest.phase, '04-mercados-pr-jogo-e-odds-transparentes', 'fase');
      equal(manifest.plan, '04-01', 'plano');
      const { manifest: unused, ...artifactPaths } = paths;
      equal(manifest.artifacts, artifactPaths, 'links do manifesto');
      for (const artifact of [report, registry, quarantine]) {
        equal(artifact.manifest, paths.manifest, 'link para manifesto');
      }
    });

    check('integridade e proveniencia da captura', () => {
      equal(manifest.source.rawArtifact, snapshot, 'captura congelada do T1');
      equal(rawBytes.length, snapshot.bytes, 'tamanho em bytes da captura');
      equal(createHash('sha256').update(rawBytes).digest('hex').toUpperCase(),
        snapshot.sha256, 'SHA-256 dos bytes capturados');
      equal(manifest.source.type, 'exploratory_snapshot', 'fonte apenas exploratoria');
      equal(manifest.source.uri, 'https://laufu.jliga.club', 'fonte da captura');
      equal(manifest.source.edition, '2026', 'edicao da captura');
      equal(manifest.source.capturedAt, capturedAt, 'instante da captura');
      equal(manifest.scope.sourceAuthority, 'exploratory', 'autoridade exploratoria da fonte');
      equal(manifest.scope.eligibilitySubject, 'dataset_snapshot',
        'elegibilidade do manifesto se aplica ao dataset');
      equal(manifest.scope.calibrationEligible, false, 'captura inelegivel para calibracao');
      equal(manifest.scope.productionEligible, false, 'captura inelegivel para producao');
      equal(raw.metadados.fonte, manifest.source.uri, 'proveniencia nos metadados');
      equal(raw.metadados.edicao, manifest.source.edition, 'edicao nos metadados');
      equal(raw.metadados.extracao_timestamp, capturedAt, 'instante nos metadados');
      equal(registry.sourceSnapshotSha256, snapshot.sha256, 'digest do inventario');
      equal(quarantine.sourceSnapshotSha256, snapshot.sha256, 'digest da quarentena');
      equal(report.dataset.sourceSha256, snapshot.sha256, 'digest do backtest');
      equal(report.dataset.sourceAuthority, manifest.scope.sourceAuthority,
        'autoridade da fonte no backtest');
      equal(report.dataset.eligibilitySubject, 'dataset_snapshot',
        'elegibilidade do backtest se aplica ao dataset');
      equal(report.dataset.calibrationEligible, manifest.scope.calibrationEligible,
        'elegibilidade de calibracao no backtest');
      equal(report.dataset.productionEligible, manifest.scope.productionEligible,
        'elegibilidade de producao no backtest');
    });

    const rows = raw.partidas;
    check('estrutura e limites da fonte', () => {
      expect(Array.isArray(rows) && rows.length > 0, 'partidas obrigatorias');
      expect(Array.isArray(raw.campeonatos), 'campeonatos obrigatorios');
      equal(raw.metadados.total_partidas, rows.length, 'total de partidas da fonte');
      equal(raw.metadados.total_campeonatos, raw.campeonatos.length, 'total de campeonatos');
      equal(new Set(rows.map(row => row.origem_id)).size, rows.length, 'IDs de partida unicos');
      for (const row of rows) {
        expect(numericId(row.origem_id), 'ID de partida numerico');
        expect(['CONCLUIDA', 'AGENDADA', 'ADIADA', 'WO'].includes(row.estado), 'estado observado');
        equal(row.edicao, '2026', 'edicao por partida');
        equal(row.extracao_timestamp, capturedAt, 'captura por partida');
        expect(row.data_hora_iso === null || timestamp(row.data_hora_iso), 'instante esportivo ou null');
        for (const [id, name] of teamFields) {
          expect(nonempty(row[id]) && nonempty(row[name]), 'identidade observada');
          expect(numericId(row[id]) || row[id] === slugify(row[name]), 'fallback conhecido da captura');
        }
        // Capture time is not evidence of when a result became historically known.
        for (const key of ['known_at', 'knownAt', 'confirmedVersionId', 'confirmed_version_id']) {
          expect(row[key] === undefined || row[key] === null, 'fonte sem cronologia ou versao verificada');
        }
      }
    });

    const statusCounts = {};
    for (const row of rows) statusCounts[row.estado] = (statusCounts[row.estado] ?? 0) + 1;
    const unresolved = rows.filter(row => fallbackFields(row).length > 0);
    const candidates = rows.filter(row => row.estado === 'CONCLUIDA'
      && teamFields.every(([id]) => numericId(row[id])));
    const missingTime = rows.filter(row => row.data_hora_iso === null).length;
    const numericWo = rows.filter(numericWalkover).length;
    const sourceById = new Map(rows.map(row => [row.origem_id, row]));

    check('contagens recalculadas', () => {
      equal(statusCounts, { CONCLUIDA: 333, AGENDADA: 291, ADIADA: 1, WO: 46 }, 'estados da captura');
      const quality = manifest.dataQuality;
      equal(quality.rowsObserved, rows.length, 'linhas observadas');
      equal(quality.statusCounts, statusCounts, 'contagens por estado');
      equal(quality.rowsWithSportTime, rows.length - missingTime, 'linhas com instante');
      equal(quality.rowsMissingSportTime, missingTime, 'linhas sem instante');
      equal(quality.fallbackIdentityRows, unresolved.length, 'fallbacks de identidade');
      equal(quality.numericWalkoverRows, numericWo, 'W.O. numericos');
      equal(numericWo, statusCounts.WO, 'todo W.O. desta captura possui placar numerico');
      expect(nonempty(quality.fallbackDetectionRule), 'regra de fallback documentada');
      expect(nonempty(quality.candidateRegularRule), 'candidato nao equivale a resultado verificado');
      equal(candidates.length, statusCounts.CONCLUIDA
        - unresolved.filter(row => row.estado === 'CONCLUIDA').length, 'aritmetica das candidatas');
    });

    check('elegibilidade bloqueada', () => {
      equal(manifest.scope, {
        eligibilitySubject: 'dataset_snapshot',
        sourceAuthority: 'exploratory',
        calibrationEligible: false,
        productionEligible: false,
        knownAtAvailable: false,
        confirmedVersionChainAvailable: false,
        backtestStatus: 'blocked_pending_verified_temporal_dataset',
        candidateRegularRowsObserved: candidates.length,
        completedRowsObserved: statusCounts.CONCLUIDA,
        validSportResultRowsObserved: 0,
        rowsExcludedBeforeCalibration: rows.length,
      }, 'candidatas nao sao resultados elegiveis');
    });

    check('protocolo temporal em aberto', () => {
      equal(manifest.temporalProtocol, {
        ordering: 'canonical sport time ascending; ties require stable source id',
        trainEvaluationSplit: 'open_pending_dataset_review',
        recencyFormula: 'open_pending_temporal_backtest',
        leakageControl: 'known_at and corrected-version lineage are required before approval',
        snapshotCaptureIsNotKnownAt: true,
      }, 'captura unica nao substitui known_at nem separacao temporal');
    });

    check('decisoes fixas e parametros abertos', () => {
      equal(manifest.parameters, {
        'D-101': { initialRating: 1500, status: 'locked' },
        'D-102': { provisionalK: 32, matureK: 16, experienceThreshold: null, status: 'provisional_open_threshold' },
        'D-103': { chronologicalOrdering: true, monthlyHalfLife: null, status: 'locked_order_open_recency' },
        'D-104': { editionRegressionToInitial: 0.5, status: 'locked' },
        'D-105': { requiresConfirmedCurrentVersion: true, status: 'locked' },
        'D-106': { drawIsConditional: true, davidsonParameters: null, status: 'locked_mode_open_parameters' },
        'D-107': { sharedConservativeDrawPrior: true, specializationThreshold: null, status: 'locked_direction_open_threshold' },
        'D-108': { lowSampleEstimate: 'neutral', confidenceBands: null, status: 'locked_behavior_open_bands' },
        'D-109': { administrativeWalkoverExcluded: true, status: 'locked' },
        'D-110': { cancelledOrInvalidResultExcluded: true, status: 'locked' },
        'D-111': { postponementSuspendsAndRescheduleVersions: true, status: 'locked' },
        'D-113': { initialMargin: 0.05, configurableAndVersioned: true, status: 'locked' },
        'D-114': { publishedDecimalPlaces: 2, roundingMode: null, publishedPriceImmutable: true, status: 'locked_open_rounding' },
        'D-115': { fairProbabilitySeparateFromPublishedPrice: true, status: 'locked' },
        'D-120': { laufuIsNotProductionAuthority: true, status: 'locked' },
      }, 'nenhum parametro aberto pode ser escolhido pelo T1');
    });

    check('politica de exclusao', () => {
      equal(manifest.classification.actualIncludedRows, 0,
        'nenhuma linha da captura foi incluida na calibracao');
      equal(manifest.classification.includedClassIsAnAllowlistOnly, true,
        'classe permitida nao equivale a linhas efetivamente incluidas');
      sameSet(manifest.classification.includedInCalibration,
        ['regular_sport_result_with_verified_lineage'], 'somente resultado com linhagem verificada');
      sameSet(manifest.classification.excluded, [
        'administrative_wo', 'numeric_wo', 'abandonment_without_valid_result', 'cancelled',
        'postponed_or_rescheduled_without_lineage', 'correction_without_version_chain',
        'unresolved_identity', 'missing_sport_time',
      ], 'exclusoes obrigatorias');
      equal(manifest.classification.syntheticFixturesNeverEnterBacktest, true, 'fixtures nunca sao amostra');
    });

    check('backtest nao executado', () => {
      equal(report.status, 'not_run', 'backtest continua nao executado');
      equal(report.evidenceStatus, 'insufficient_for_temporal_backtest', 'evidencia insuficiente');
      expect(nonempty(report.blockingReason) && nonempty(report.decision), 'bloqueio e decisoes documentados');
      equal(report.dataset, {
        eligibilitySubject: 'dataset_snapshot',
        sourceAuthority: 'exploratory',
        calibrationEligible: false,
        productionEligible: false,
        rowsObserved: rows.length, candidateRegularRows: candidates.length,
        rowsEligibleAfterLineageChecks: 0, trainingRows: 0, evaluationRows: 0,
        sourceSha256: snapshot.sha256,
      }, 'nenhuma linha de treino ou avaliacao');
      equal(report.metrics, {
        logLoss: null, brierScore: null, calibrationError: null,
        overroundInvariant: null, confidenceIntervals: null,
      }, 'metricas nao podem ser fabricadas');
    });

    check('sensibilidades ainda nao avaliadas', () => {
      equal(report.sensitivityMatrix, [
        { axis: 'K experience policy', scenarios: ['provisional_K_32', 'mature_K_16'], experienceThresholdGames: null, status: 'proposed_not_evaluated' },
        { axis: 'recency', scenarios: ['chronological_no_half_life', 'formula_to_be_approved'], status: 'proposed_not_evaluated' },
        { axis: 'Davidson', scenarios: ['shared_conservative_prior', 'specialized_prior'], status: 'proposed_not_evaluated' },
        { axis: 'confidence', scenarios: ['neutral_low_sample', 'band_definition_open'], status: 'proposed_not_evaluated' },
        { axis: 'margin', scenarios: [0.05], status: 'locked_input_not_evaluated' },
        { axis: 'rounding', scenarios: ['two_decimal_places_mode_open'], status: 'proposed_not_evaluated' },
      ], 'K e uma politica por experiencia; demais eixos nao foram avaliados');
    });

    check('nove riscos do extrator', () => {
      expect(Array.isArray(report.extractorRisks), 'inventario de riscos');
      sameSet(report.extractorRisks.map(risk => risk.id), riskIds, 'riscos completos e sem duplicatas');
      for (const risk of report.extractorRisks) {
        equal(risk.status, 'observed', 'risco observado nao equivale a corrigido');
        expect(nonempty(risk.detail), 'detalhe do risco obrigatorio');
      }
    });

    check('quarentena completa das identidades', () => {
      expect(Array.isArray(quarantine.records), 'registros da quarentena');
      sameSet(quarantine.records.map(record => record.sourceRecordId),
        unresolved.map(row => row.origem_id), 'inventario completo sem duplicatas');
      for (const record of quarantine.records) {
        const row = sourceById.get(record.sourceRecordId);
        equal(record.state, row.estado, 'estado do registro em quarentena');
        sameSet(record.fallbackFields, fallbackFields(row), 'campos fallback derivados da fonte');
        const reasons = ['FALLBACK_SLUG_ID', 'NO_KNOWN_AT', 'NO_CONFIRMED_VERSION_CHAIN'];
        if (numericWalkover(row)) reasons.push('NUMERIC_WO_SCORE');
        // Missing sport time is a global exclusion count, not an identity record reason.
        sameSet(record.reasonCodes, reasons, 'motivos por registro');
        equal(record.calibrationEligible, false, 'quarentena nao pode ser liberada');
      }
    });

    check('resumo e politica da quarentena', () => {
      equal(quarantine.summary, {
        totalRows: rows.length,
        unresolvedIdentityRows: unresolved.length,
        unresolvedCompletedRows: unresolved.filter(row => row.estado === 'CONCLUIDA').length,
        unresolvedWoRows: unresolved.filter(row => row.estado === 'WO').length,
        missingSportTimeRows: missingTime, numericWoRows: numericWo,
        explicitCancelledRows: rows.filter(row => row.estado === 'CANCELADA').length,
        explicitAbandonmentRows: rows.filter(row => row.estado === 'ABANDONADA').length,
      }, 'resumo derivado da fonte; identidade e W.O. sao contagens distintas');
      sameSet(quarantine.reasonCodes, quarantineReasons, 'motivos de exclusao');
      sameSet(quarantine.releaseRequirements, [
        'canonical_identity', 'known_at', 'confirmed_version_chain', 'authoritative_result_kind', 'independent_review',
      ], 'liberacao depende de evidencia e revisao');
      expect(nonempty(quarantine.policy), 'politica de quarentena documentada');
      equal(quarantine.sampleRecords, [
        { sourceRecordId: '445159', reasonCodes: ['NUMERIC_WO_SCORE', 'FALLBACK_SLUG_ID'], calibrationEligible: false },
        { sourceRecordId: '445146', reasonCodes: ['NO_CONFIRMED_VERSION_CHAIN'], calibrationEligible: false },
      ], 'amostras da quarentena permanecem inelegiveis');
    });

    check('inventario das sete fixtures', () => {
      expect(Array.isArray(registry.fixtures), 'inventario de fixtures');
      sameSet(registry.fixtures.map(entry => entry.id), fixtureCases.map(([id]) => id), 'sete IDs unicos');
      sameSet(registry.fixtures.map(entry => entry.fixturePath),
        fixtureCases.map(([id]) => fixturePath(id)), 'sete paths locais unicos');
      sameSet(registry.fixtures.map(entry => entry.classification),
        fixtureCases.map(([, classification]) => classification), 'sete classificacoes distintas');
      for (const [id, classification, synthetic, sourceId] of fixtureCases) {
        const entry = registry.fixtures.find(item => item.id === id);
        const fixture = fixtures.get(id);
        equal(entry.fixturePath, fixturePath(id), 'path associado ao ID');
        equal(entry.classification, classification, 'classificacao no inventario');
        equal(entry.syntheticContractFixture, synthetic, 'origem sintetica no inventario');
        equal(entry.sourceRecordId, sourceId, 'origem por fixture, sem inventar fonte sintetica');
        equal(entry.eligibleForCalibration, false, 'inventario inelegivel');
        expect(nonempty(entry.exclusionReason), 'motivo de exclusao da fixture');
        equal(fixture.schemaVersion, '04-01.fixture.v1', 'schema da fixture');
        equal(fixture.fixtureId, id, 'ID da fixture');
        equal(fixture.classification, classification, 'classificacao da fixture');
        equal(fixture.syntheticContractFixture, synthetic, 'origem sintetica da fixture');
        equal(fixture.eligibleForCalibration, false, 'fixture nunca e evidencia de calibracao');
      }
    });

    check('proveniencia das fixtures reais', () => {
      for (const [id, , synthetic, sourceId] of fixtureCases) {
        if (synthetic) continue;
        const fixture = fixtures.get(id);
        const row = sourceById.get(sourceId);
        expect(row !== undefined, 'partida da fixture existe na captura');
        equal(fixture.source, {
          uri: row.origem_url, recordId: sourceId, capturedAt, snapshotSha256: snapshot.sha256,
        }, 'proveniencia real e digest');
        for (const [field, sourceField] of [
          ['competitionId', 'campeonato_id'], ['edition', 'edicao'], ['sport', 'modalidade'],
          ['category', 'categoria'], ['sportTime', 'data_hora_iso'],
        ]) equal(fixture.event[field], row[sourceField], 'escopo esportivo da fixture real');
        for (const [side, idField, nameField] of [
          ['home', 'time_mandante_id', 'time_mandante_nome'],
          ['away', 'time_visitante_id', 'time_visitante_nome'],
        ]) {
          equal(fixture.event[`${side}Id`], numericId(row[idField]) ? row[idField] : null,
            'fallback nao pode virar identidade canonica');
          expect(nonempty(fixture.event[`${side}Name`]), 'nome observado da equipe');
          // Accent folding checks labels only; it never resolves canonical identity.
          equal(slugify(fixture.event[`${side}Name`]), slugify(row[nameField]), 'rotulo textual da equipe');
        }
        equal(fixture.result.confirmedVersionId, null, 'fonte real nao tem versao confirmada');
        equal(fixture.result.knownAt, null, 'capturedAt nao e knownAt');
      }
    });

    check('resultado regular nao verificado', () => {
      const row = sourceById.get('445146');
      equal(row.estado, 'CONCLUIDA', 'estado observado regular');
      equal(fixtures.get('regular-confirmed-result').result, {
        kind: 'CONCLUIDA_UNVERIFIED', homeScore: row.placar_mandante_regulamentar,
        awayScore: row.placar_visitante_regulamentar, confirmedVersionId: null, knownAt: null,
      }, 'placar observado nao comprova confirmacao');
    });

    check('W.O. administrativo e contaminante numerico', () => {
      const row = sourceById.get('445159');
      equal(row.estado, 'WO', 'fonte do W.O.');
      equal(fixtures.get('administrative-wo').result, {
        kind: 'ADMINISTRATIVE_WO', homeScore: null, awayScore: null, confirmedVersionId: null, knownAt: null,
      }, 'W.O. administrativo nao tem placar esportivo');
      equal(fixtures.get('numeric-wo').result, {
        kind: 'ADMINISTRATIVE_WO', sourceHomeScore: row.placar_mandante_regulamentar,
        sourceAwayScore: row.placar_visitante_regulamentar, homeScoreForModel: null,
        awayScoreForModel: null, confirmedVersionId: null, knownAt: null,
      }, 'placar numerico da fonte nunca e placar para o modelo');
    });

    check('limites das quatro fixtures sinteticas', () => {
      for (const [id, , synthetic] of fixtureCases) {
        if (!synthetic) continue;
        const fixture = fixtures.get(id);
        equal(fixture.source.type, 'synthetic_contract_fixture', 'fonte sintetica explicita');
        expect(nonempty(fixture.source.reason), 'motivo da fixture sintetica');
        sameSet(Object.keys(fixture.source), ['type', 'reason'], 'fixture sintetica nao alega captura real');
        equal(fixture.event.competitionId, 'fixture-competition', 'competicao sintetica');
        equal(fixture.event.edition, 'fixture', 'edicao sintetica');
        equal(fixture.event.homeId, 'fixture-home', 'mandante sintetico');
        equal(fixture.event.awayId, 'fixture-away', 'visitante sintetico');
        expect(timestamp(fixture.event.sportTime), 'instante sintetico explicito');
      }
      for (const [id, kind] of [
        ['abandonment-no-valid-result', 'ABANDONED_NO_VALID_RESULT'], ['cancelled-event', 'CANCELLED'],
      ]) {
        equal(fixtures.get(id).result, {
          kind, homeScore: null, awayScore: null, confirmedVersionId: null, knownAt: null,
        }, 'cancelamento ou abandono sem resultado valido');
      }
      equal(fixtures.get('postponed-rescheduled-event').result, {
        kind: 'POSTPONED', previousMarketVersion: 'market-v1', rescheduledMarketVersion: 'market-v2',
        homeScore: null, awayScore: null, confirmedVersionId: null, knownAt: null,
      }, 'remarcacao exemplifica nova versao, nunca resultado esportivo');
      const correction = fixtures.get('corrected-confirmed-result');
      equal(correction.result, {
        kind: 'CORRECTED_CONFIRMED_RESULT', supersedesVersionId: 'result-v1',
        confirmedVersionId: 'result-v2', knownAt: '2026-01-05T12:00:00-03:00', homeScore: 1, awayScore: 0,
      }, 'correcao sintetica exemplifica linhagem, mas nao verifica a fonte');
      expect(Date.parse(correction.result.knownAt) > Date.parse(correction.event.sportTime),
        'correcao sintetica posterior ao evento');
    });

    check('checkpoint T2 ainda pendente', () => {
      equal(manifest.approval, {
        kind: approvalTokenKind,
        status: pendingApproval, approvedAt: null, approvedBy: null, digest: null,
        reviewReference: null, reviewedArtifactHashes: null,
      }, 'T1 nao registra aprovacao humana');
      equal(report.approval, {
        kind: approvalTokenKind, status: pendingApproval, digest: null,
        reviewReference: null, reviewedArtifactHashes: null,
      }, 'backtest nao aprovado');
    });

    if (!silent) {
      console.log(`Consistencia dos artefatos: ${completed}/${completed} verificacoes; 0 erros.`);
      console.log(`Captura: ${rows.length} partidas; ${candidates.length} candidatas; 0 elegiveis; ${unresolved.length} identidades em quarentena; ${numericWo} W.O. numericos excluidos; ${fixtures.size} fixtures inelegiveis.`);
      console.log('Artefatos consistentes; calibracao, backtest e T2 continuam bloqueados. Nenhuma aprovacao estatistica ou de produto.');
    }
    return true;
  } catch (error) {
    if (!silent) {
      console.error(`FALHA em ${current}: ${error instanceof TypeError ? 'estrutura obrigatoria ausente ou invalida' : error.message}`);
      console.error(`${completed} verificacoes concluidas; 1 erro; demais verificacoes nao executadas. Calibracao, backtest e T2 bloqueados.`);
      process.exitCode = 1;
    }
    return false;
  }
}

const args = process.argv.slice(2);
const checkOnly = args.length === 1 && args[0] === '--check';
const checkWithApproval = args.length === 2 && args[0] === '--check' && args[1] === '--require-approval';
if (!checkOnly && !checkWithApproval) {
  console.error('Uso deste T1: node scripts/validate-odds-calibration.mjs --check');
  console.error('Uso deste T2: node scripts/validate-odds-calibration.mjs --check --require-approval');
  console.error('Flags desconhecidas, duplicadas ou fora de ordem falham fechadas. Aprovacao de calibracao/T2 continua bloqueada.');
  process.exitCode = 2;
} else {
  validate({ requireApproval: checkWithApproval });
}
